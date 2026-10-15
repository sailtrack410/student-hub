import 'dart:convert';
import 'dart:io' show Platform;
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class ApiException implements Exception {
  final String code;
  final String message;

  const ApiException({required this.code, required this.message});

  @override
  String toString() => message;
}

class ApiClient {
  static const String _prefTokenKey = 'auth_token_key';

  static String get defaultBaseUrl {
    const envBase = String.fromEnvironment('API_BASE');
    if (envBase.isNotEmpty) {
      return envBase;
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
  final http.Client _client = http.Client();
  String? _token;

  ApiClient({String? baseUrl}) : baseUrl = baseUrl ?? defaultBaseUrl;

  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    _token = prefs.getString(_prefTokenKey);
  }

  String? get token => _token;
  bool get isAuthenticated => _token != null && _token!.isNotEmpty;

  Future<void> setToken(String token) async {
    _token = token;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_prefTokenKey, token);
  }

  Future<void> clearToken() async {
    _token = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_prefTokenKey);
  }

  Map<String, String> _buildHeaders([Map<String, String>? extra]) {
    final headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    };
    if (_token != null && _token!.isNotEmpty) {
      headers['Authorization'] = 'Bearer $_token';
    }
    if (extra != null) {
      headers.addAll(extra);
    }
    return headers;
  }

  Future<dynamic> get(String path, {Map<String, dynamic>? query}) async {
    try {
      final uri = Uri.parse('$baseUrl$path').replace(
        queryParameters: query?.map((k, v) => MapEntry(k, v.toString())),
      );
      final response = await _client.get(uri, headers: _buildHeaders());
      return _handleResponse(response);
    } catch (e) {
      _handleNetworkError(e);
    }
  }

  Future<dynamic> post(String path, {dynamic body}) async {
    try {
      final uri = Uri.parse('$baseUrl$path');
      final response = await _client.post(
        uri,
        headers: _buildHeaders(),
        body: body != null ? jsonEncode(body) : null,
      );
      return _handleResponse(response);
    } catch (e) {
      _handleNetworkError(e);
    }
  }

  dynamic _handleResponse(http.Response response) {
    if (response.statusCode == 401) {
      clearToken();
      throw const ApiException(
        code: 'UNAUTHORIZED',
        message: '登录状态已失效，请重新登录',
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
    throw ApiException(
      code: 'NETWORK_ERROR',
      message: '网络连接失败，请检查网络或后端服务 ($baseUrl)',
    );
  }
}
