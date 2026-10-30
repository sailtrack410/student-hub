import 'dart:async';
import 'dart:convert';
import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';

import '../services/session_store.dart';

import 'sse_http_stub.dart'
    if (dart.library.html) 'sse_http_web.dart'
    if (dart.library.io) 'sse_http_io.dart';

enum ApiErrorKind {
  network,
  timeout,
  cancelled,
  unauthorized,
  server,
  business,
}

class RequestCancellation {
  final Completer<void> _signal = Completer<void>();
  Future<void> get whenCancelled => _signal.future;
  bool get isCancelled => _signal.isCompleted;
  void cancel() {
    if (!_signal.isCompleted) _signal.complete();
  }
}

class ApiException implements Exception {
  final String code;
  final String message;

  final ApiErrorKind kind;
  const ApiException({
    required this.code,
    required this.message,
    this.kind = ApiErrorKind.business,
  });

  @override
  String toString() => message;
}

class ApiClient {
  static String get defaultBaseUrl {
    const envBase = String.fromEnvironment('API_BASE');
    if (envBase.isNotEmpty) {
      return envBase;
    }
    if (kReleaseMode) {
      return 'https://mycc.sailtrack.cn';
    }
    if (kIsWeb) {
      return 'http://127.0.0.1:9000';
    }
    // Android 模拟器默认访问宿主机 10.0.2.2
    if (Platform.isAndroid) {
      return 'http://10.0.2.2:9000';
    }
    // iOS 模拟器或 macOS 桌面
    return 'http://127.0.0.1:9000';
  }

  final String baseUrl;
  final http.Client _client;
  final Duration requestTimeout;
  final Map<String, Future<dynamic>> _pendingGets = {};
  final Future<http.StreamedResponse> Function(http.BaseRequest) _sendSse;

  /// 全局 401 回调：由 main 注入，统一清会话并回登录页。
  static VoidCallback? onUnauthorized;
  ApiClient({
    String? baseUrl,
    this.requestTimeout = const Duration(seconds: 20),
    http.Client? client,
    Future<http.StreamedResponse> Function(http.BaseRequest)? sseSender,
  }) : baseUrl = baseUrl ?? defaultBaseUrl,
       _client = client ?? http.Client(),
       _sendSse = sseSender ?? (client == null ? sendSseRequest : client.send);

  Future<void> init() => SessionStore.init();

  String? get token => SessionStore.token;
  bool get isAuthenticated {
    final t = token;
    return t != null && t.isNotEmpty;
  }

  Future<void> setToken(String token) => SessionStore.setToken(token);

  Future<void> clearToken() async {
    await SessionStore.clearToken();
  }

  Map<String, String> _buildHeaders([Map<String, String>? extra]) {
    final headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      if (!kIsWeb) 'x-client-type': 'app',
    };
    final activeToken = token;
    if (activeToken != null && activeToken.isNotEmpty) {
      headers['Authorization'] = 'Bearer $activeToken';
    }
    if (extra != null) {
      headers.addAll(extra);
    }
    return headers;
  }

  /// 相同账号/地址的并发只读请求合并，写操作始终由调用方显式提交。
  Future<dynamic> get(
    String path, {
    Map<String, dynamic>? query,
    RequestCancellation? cancellation,
  }) {
    final uri = Uri.parse(
      '$baseUrl$path',
    ).replace(queryParameters: query?.map((k, v) => MapEntry(k, v.toString())));
    if (cancellation != null) {
      return _request('GET', uri, cancellation: cancellation);
    }
    final key = '${token ?? ''}|$uri';
    final pending = _pendingGets[key];
    if (pending != null) return pending;
    final future = _request('GET', uri);
    _pendingGets[key] = future;
    future.then<void>(
      (_) {
        if (identical(_pendingGets[key], future)) _pendingGets.remove(key);
      },
      onError: (Object _, StackTrace _) {
        if (identical(_pendingGets[key], future)) _pendingGets.remove(key);
      },
    );
    return future;
  }

  Future<dynamic> post(
    String path, {
    dynamic body,
    RequestCancellation? cancellation,
    Duration? requestTimeout,
  }) => _request(
    'POST',
    Uri.parse('$baseUrl$path'),
    body: body,
    cancellation: cancellation,
    requestTimeout: requestTimeout,
  );
  Future<dynamic> put(
    String path, {
    dynamic body,
    RequestCancellation? cancellation,
  }) => _request(
    'PUT',
    Uri.parse('$baseUrl$path'),
    body: body,
    cancellation: cancellation,
  );
  Future<dynamic> delete(
    String path, {
    dynamic body,
    RequestCancellation? cancellation,
  }) => _request(
    'DELETE',
    Uri.parse('$baseUrl$path'),
    body: body,
    cancellation: cancellation,
  );

  Future<dynamic> _request(
    String method,
    Uri uri, {
    dynamic body,
    RequestCancellation? cancellation,
    Duration? requestTimeout,
  }) async {
    final abort = Completer<void>();
    cancellation?.whenCancelled.then((_) {
      if (!abort.isCompleted) abort.complete();
    });
    if (cancellation?.isCancelled == true) abort.complete();
    final request = http.AbortableRequest(
      method,
      uri,
      abortTrigger: abort.future,
    )..headers.addAll(_buildHeaders());
    if (body != null) request.body = jsonEncode(body);
    final requestToken = token;
    try {
      final response =
          await (() async => http.Response.fromStream(
            await _client.send(request),
          ))().timeout(
            requestTimeout ?? this.requestTimeout,
            onTimeout: () {
              if (!abort.isCompleted) abort.complete();
              throw TimeoutException('request timeout');
            },
          );
      return await _handleResponse(
        response,
        requestToken: requestToken,
        path: uri.path,
      );
    } catch (e) {
      _handleNetworkError(e);
    }
  }

  /// 订阅取消会中止底层连接；握手和事件空闲均有超时。
  ///
  /// [timeout] 覆盖握手超时（默认取全局 requestTimeout）；AI 流式握手要等服务端
  /// 出模型首字（云端 DeepSeek-V3 实测 20~25 秒），调用方需传更长时限。
  Stream<String> streamSse(String path, {dynamic body, Duration? timeout}) {
    final abort = Completer<void>();
    StreamSubscription<String>? subscription;
    var cancelled = false;
    late final StreamController<String> output;
    output = StreamController<String>(
      onListen: () async {
        final request =
            http.AbortableRequest(
                'POST',
                Uri.parse('$baseUrl$path'),
                abortTrigger: abort.future,
              )
              ..headers.addAll(_buildHeaders())
              ..headers['Accept'] = 'text/event-stream'
              ..body = body == null ? '' : jsonEncode(body);
        final requestToken = token;
        try {
          final response = await _sendSse(request).timeout(
            timeout ?? requestTimeout,
            onTimeout: () {
              if (!abort.isCompleted) abort.complete();
              throw TimeoutException('SSE handshake timeout');
            },
          );
          if (cancelled) {
            await response.stream.listen(null).cancel();
            return;
          }
          if (response.statusCode < 200 || response.statusCode >= 300) {
            final responseBody = await response.stream.bytesToString().timeout(
              requestTimeout,
            );
            await _handleResponse(
              http.Response(responseBody, response.statusCode),
              requestToken: requestToken,
              path: path,
            );
            throw ApiException(
              code: 'HTTP_${response.statusCode}',
              message: '流式请求失败',
            );
          }
          subscription = response.stream
              .transform(utf8.decoder)
              .transform(const LineSplitter())
              .timeout(const Duration(seconds: 30))
              .listen(
                output.add,
                onError: (Object e, StackTrace stack) {
                  if (!abort.isCompleted) abort.complete();
                  if (!output.isClosed) output.addError(e, stack);
                  unawaited(subscription?.cancel());
                  unawaited(output.close());
                },
                onDone: output.close,
              );
        } catch (e, stack) {
          if (!abort.isCompleted) abort.complete();
          if (!cancelled && !output.isClosed) {
            output.addError(e, stack);
            unawaited(output.close());
          }
        }
      },
      onCancel: () async {
        cancelled = true;
        if (!abort.isCompleted) abort.complete();
        await subscription?.cancel();
      },
      onPause: () => subscription?.pause(),
      onResume: () => subscription?.resume(),
    );
    return output.stream;
  }

  /// 上传图片（multipart/form-data，POST /api/app/files）
  Future<Map<String, dynamic>?> uploadImage(
    Uint8List bytes, {
    String filename = 'image.jpg',
  }) async {
    try {
      final uri = Uri.parse('$baseUrl/api/app/files');
      final request = http.MultipartRequest('POST', uri);
      final requestToken = token;
      final headers = _buildHeaders();
      headers.remove('Content-Type');
      request.headers.addAll(headers);
      final ext = filename.split('.').last.toLowerCase();
      final subtype = ext == 'png' ? 'png' : (ext == 'webp' ? 'webp' : 'jpeg');
      request.files.add(
        http.MultipartFile.fromBytes(
          'file',
          bytes,
          filename: filename,
          contentType: MediaType('image', subtype),
        ),
      );
      final streamed = await _client.send(request).timeout(requestTimeout);
      final response = await http.Response.fromStream(streamed)
          .timeout(requestTimeout);
      final data = await _handleResponse(
        response,
        requestToken: requestToken,
        path: uri.path,
      );
      return data is Map<String, dynamic> ? data : null;
    } catch (_) {
      return null;
    }
  }

  /// 上传音频 (POST /api/app/files/audio)
  Future<Map<String, dynamic>?> uploadAudio(
    Uint8List bytes, {
    String filename = 'voice.m4a',
  }) async {
    return _uploadMultipart('/api/app/files/audio', bytes, filename, 'audio');
  }

  /// 上传文档 (POST /api/app/files/document)
  Future<Map<String, dynamic>?> uploadDocument(
    Uint8List bytes, {
    required String filename,
  }) async {
    return _uploadMultipart(
      '/api/app/files/document',
      bytes,
      filename,
      'document',
    );
  }

  Future<Map<String, dynamic>?> _uploadMultipart(
    String path,
    Uint8List bytes,
    String filename,
    String kind,
  ) async {
    try {
      final uri = Uri.parse('$baseUrl$path');
      final request = http.MultipartRequest('POST', uri);
      final requestToken = token;
      final headers = _buildHeaders();
      headers.remove('Content-Type');
      request.headers.addAll(headers);
      final ext = filename.split('.').last.toLowerCase();
      final contentType = switch (kind) {
        'audio' => MediaType(
          'audio',
          ext == 'm4a' ? 'mp4' : (ext == 'mp3' ? 'mpeg' : 'aac'),
        ),
        'document' => switch (ext) {
          'pdf' => MediaType('application', 'pdf'),
          'docx' => MediaType(
            'application',
            'vnd.openxmlformats-officedocument.wordprocessingml.document',
          ),
          'md' => MediaType('text', 'markdown'),
          _ => MediaType('text', 'plain'),
        },
        _ => MediaType(
          'image',
          ext == 'png' ? 'png' : (ext == 'webp' ? 'webp' : 'jpeg'),
        ),
      };
      request.files.add(
        http.MultipartFile.fromBytes(
          'file',
          bytes,
          filename: filename,
          contentType: contentType,
        ),
      );
      final streamed = await _client.send(request).timeout(requestTimeout);
      final response = await http.Response.fromStream(streamed)
          .timeout(requestTimeout);
      final data = await _handleResponse(
        response,
        requestToken: requestToken,
        path: uri.path,
      );
      return data is Map<String, dynamic> ? data : null;
    } catch (_) {
      return null;
    }
  }

  /// 登录或教务认证失败不等于已有平台会话失效；这些接口由页面原地提示。
  bool _isAuthenticationEntry(String path) => const {
    '/api/common/auth/login',
    '/api/common/auth/captcha',
    '/api/common/auth/wechat-login',
    '/api/common/auth/qq-login',
    '/api/common/auth/dev-wechat-login',
    '/api/common/auth/email-code',
    '/api/common/auth/register',
    '/api/common/auth/password/reset',
    '/api/app/jw/auth',
  }.contains(path);

  Future<void> _handleUnauthorized(String? requestToken) async {
    // 未登录请求的 401 与旧账号的延迟响应都不改变当前会话。
    if (requestToken == null) return;
    final cleared = await SessionStore.clearToken(expectedToken: requestToken);
    if (cleared && token == null) onUnauthorized?.call();
  }

  Future<dynamic> _handleResponse(
    http.Response response, {
    required String? requestToken,
    required String path,
  }) async {
    if (response.statusCode == 401) {
      Map<String, dynamic>? decoded;
      try {
        final body = jsonDecode(utf8.decode(response.bodyBytes));
        if (body is Map<String, dynamic>) decoded = body;
      } catch (_) {}
      final code = decoded?['code']?.toString() ?? '';
      final isPlatformSessionExpiry =
          code == 'AUTH_401' && !_isAuthenticationEntry(path);
      if (!isPlatformSessionExpiry) {
        final error = ApiException(
          code: code.isEmpty ? 'HTTP_401' : code,
          message: decoded?['message']?.toString() ?? '身份认证失败，请重试',
          kind: ApiErrorKind.unauthorized,
        );
        throw error;
      }
      await _handleUnauthorized(requestToken);
      throw const ApiException(
        code: 'AUTH_401',
        message: '登录状态已失效，请重新登录',
        kind: ApiErrorKind.unauthorized,
      );
    }

    if (response.statusCode >= 500) {
      throw ApiException(
        code: 'HTTP_${response.statusCode}',
        message: '服务暂时不可用，请稍后重试',
        kind: ApiErrorKind.server,
      );
    }

    try {
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      if (decoded is Map<String, dynamic>) {
        final success = decoded['success'] == true;
        if (!success) {
          throw ApiException(
            code: decoded['code']?.toString() ?? 'ERROR',
            message: decoded['message']?.toString() ?? '请求失败',
          );
        }
        return decoded['data'];
      }
      return decoded;
    } on ApiException {
      rethrow;
    } catch (_) {
      if (response.statusCode >= 200 && response.statusCode < 300) {
        return response.body;
      }
      throw ApiException(
        code: 'HTTP_${response.statusCode}',
        message: '服务响应异常 (${response.statusCode})',
      );
    }
  }

  Never _handleNetworkError(dynamic error) {
    if (error is ApiException) {
      throw error;
    }
    if (error is TimeoutException) {
      throw const ApiException(
        code: 'TIMEOUT',
        message: '请求超时，请稍后重试',
        kind: ApiErrorKind.timeout,
      );
    }
    if (error is http.RequestAbortedException) {
      throw const ApiException(
        code: 'CANCELLED',
        message: '请求已取消',
        kind: ApiErrorKind.cancelled,
      );
    }
    throw ApiException(
      kind: ApiErrorKind.network,
      code: 'NETWORK_ERROR',
      message: '网络连接失败，请检查网络或后端服务 ($baseUrl)',
    );
  }
}
