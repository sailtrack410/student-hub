import 'dart:convert';

import 'api_client.dart';

class AiApi {
  static final ApiClient _client = ApiClient();

  static Future<String?> createConversation({String? title}) async {
    try {
      final res = await _client.post(
        '/api/app/ai/conversations',
        body: {'title': ?title},
      );
      if (res is Map<String, dynamic>) return res['conversationId']?.toString();
    } catch (_) {}
    return null;
  }

  static Future<Map<String, dynamic>?> sendMessage({
    required String conversationId,
    required String content,
    String? clientRequestId,
    List<Map<String, dynamic>> attachments = const [],
  }) async {
    // 模型整段生成 20~25 秒，放宽到 90 秒，避免默认 20 秒超时必现失败
    final res = await _client.post(
      '/api/app/ai/conversations/$conversationId/messages',
      requestTimeout: const Duration(seconds: 90),
      body: {
        'content': content,
        'clientRequestId': ?clientRequestId,
        if (attachments.isNotEmpty) 'attachments': attachments,
      },
    );
    if (res is Map<String, dynamic>) return res;
    return null;
  }

  static Stream<AiStreamEvent> streamMessage({
    required String conversationId,
    required String content,
    String? clientRequestId,
    List<Map<String, dynamic>> attachments = const [],
  }) async* {
    String event = '';
    final dataLines = <String>[];
    try {
      // 流式握手含模型首字等待（云端 DeepSeek-V3 实测 20~25 秒），放宽到 90 秒；
      // 首个 delta 之后由 ApiClient 的 30 秒事件空闲超时兜底。
      await for (final line in _client.streamSse(
        '/api/app/ai/conversations/$conversationId/chat-stream',
        timeout: const Duration(seconds: 90),
        body: {
          'content': content,
          'clientRequestId': ?clientRequestId,
          if (attachments.isNotEmpty) 'attachments': attachments,
        },
      )) {
        if (line.isEmpty) {
          if (dataLines.isNotEmpty) {
            final payload = dataLines.join('\n');
            if (event == 'delta') {
              yield AiDelta(payload);
            } else if (event == 'done') {
              final decoded = _decodeMap(payload);
              if (decoded != null) yield AiDone(decoded);
            } else if (event == 'error') {
              final data = _decodeMap(payload);
              yield AiError(
                data?['message']?.toString() ?? payload,
                code: data?['code']?.toString(),
              );
            }
          }
          event = '';
          dataLines.clear();
          continue;
        }
        if (line.startsWith('event:')) {
          event = line.substring(6).trim();
        } else if (line.startsWith('data:')) {
          dataLines.add(line.substring(5).trimLeft());
        }
      }
    } catch (e) {
      yield AiError(e.toString(), code: e is ApiException ? e.code : null);
    }
  }

  static Future<String?> transcribe(int fileId) async {
    try {
      final res = await _client.post(
        '/api/app/ai/speech/transcribe',
        body: {'fileId': fileId},
      );
      if (res is String && res.isNotEmpty) return res;
      if (res is Map<String, dynamic>) return res['text']?.toString();
    } catch (_) {}
    return null;
  }

  static Future<List<Map<String, String>>> fetchRecommendations() async {
    try {
      final res = await _client.get('/api/app/ai/recommendations');
      final list = res is List
          ? res
          : (res is Map<String, dynamic> && res['data'] is List
                ? res['data'] as List
                : const []);
      return list
          .whereType<Map>()
          .map((e) => e.map((k, v) => MapEntry(k.toString(), v.toString())))
          .toList();
    } catch (_) {
      return const [];
    }
  }

  static Map<String, dynamic>? _decodeMap(String payload) {
    try {
      final decoded = jsonDecode(payload);
      if (decoded is Map<String, dynamic>) return decoded;
    } catch (_) {}
    return null;
  }
}

sealed class AiStreamEvent {
  const AiStreamEvent();
}

class AiDelta extends AiStreamEvent {
  final String text;
  const AiDelta(this.text);
}

class AiDone extends AiStreamEvent {
  final Map<String, dynamic> data;
  const AiDone(this.data);
}

class AiError extends AiStreamEvent {
  final String message;
  final String? code;
  const AiError(this.message, {this.code});
}
