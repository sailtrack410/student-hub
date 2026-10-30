import 'dart:async';

import 'dart:math';

import 'package:flutter/foundation.dart';

import '../api/ai_api.dart';
import '../api/api_client.dart';
import '../widgets/ai_sources_panel.dart';
import 'ai_chat_gateway.dart';

/// 消息条目模型
class AiChatMessage {
  final String id;
  final bool isUser;
  final String text;
  final String time;
  final List<AiChatAction>? actionButtons;
  final List<AiSource> sources;
  final bool isStreaming;
  final String? statusText;
  final AiChatRequest? retryRequest;
  final bool resendAsNew;

  const AiChatMessage({
    required this.id,
    required this.isUser,
    required this.text,
    required this.time,
    this.actionButtons,
    this.sources = const [],
    this.isStreaming = false,
    this.statusText,
    this.retryRequest,
    this.resendAsNew = false,
  });

  AiChatMessage copyWith({
    String? text,
    bool? isStreaming,
    List<AiChatAction>? actionButtons,
    List<AiSource>? sources,
    String? statusText,
    AiChatRequest? retryRequest,
  }) {
    return AiChatMessage(
      id: id,
      isUser: isUser,
      text: text ?? this.text,
      time: time,
      actionButtons: actionButtons ?? this.actionButtons,
      sources: sources ?? this.sources,
      isStreaming: isStreaming ?? this.isStreaming,
      statusText: statusText ?? this.statusText,
      retryRequest: retryRequest ?? this.retryRequest,
      resendAsNew: resendAsNew,
    );
  }
}

class AiChatRequest {
  AiChatRequest(
    this.query,
    this.displayText,
    List<Map<String, dynamic>> attachments,
  ) : attachments = List.unmodifiable(
        attachments.map((a) => Map<String, dynamic>.unmodifiable(a)),
      ),
      clientRequestId =
          '${DateTime.now().microsecondsSinceEpoch}-${List.generate(16, (_) => Random.secure().nextInt(256).toRadixString(16).padLeft(2, '0')).join()}';
  final String query;
  final String displayText;
  final List<Map<String, dynamic>> attachments;
  final String clientRequestId;
}

enum AiActionKind {
  schedule,
  grades,
  services,
  requestList,
  requestDetail,
  serviceApply,
  prompt,
  unsupported,
}

class AiChatAction {
  const AiChatAction(this.kind, this.label, {this.targetId});
  final AiActionKind kind;
  final String label;
  final String? targetId;

  factory AiChatAction.fromJson(Map data) {
    final kind = switch (data['type']?.toString().toUpperCase()) {
      'SCHEDULE' => AiActionKind.schedule,
      'GRADE' || 'GRADES' => AiActionKind.grades,
      'SERVICE_APPLY' => AiActionKind.serviceApply,
      'SERVICE_HALL' => AiActionKind.services,
      'REQUEST_LIST' => AiActionKind.requestList,
      'REQUEST_DETAIL' => AiActionKind.requestDetail,
      _ => AiActionKind.unsupported,
    };
    final url = Uri.tryParse(data['url']?.toString() ?? '');
    return AiChatAction(
      kind,
      data['label']?.toString() ?? '',
      targetId:
          data['itemId']?.toString() ??
          url?.queryParameters['requestId'] ??
          url?.queryParameters['itemId'],
    );
  }
}

/// 独立管理消息、会话代数、流式接收与同一问题的幂等请求 ID。
class AiChatController extends ChangeNotifier {
  AiChatController(this.gateway);
  final AiChatGateway gateway;
  bool isThinking = false;
  bool isSending = false;
  int generation = 0;
  String? _conversationId;
  bool _disposed = false;
  StreamIterator<AiStreamEvent>? _activeStream;
  void _update(VoidCallback action) {
    if (_disposed) return;
    action();
    notifyListeners();
  }

  final List<AiChatMessage> messages = [
    const AiChatMessage(
      id: 'msg_welcome',
      isUser: false,
      text: '同学你好！我是绵宝AI。\n我可以帮你查询课程安排、指引办事报修、答疑学籍考务或校园生活常识。有什么我可以帮你的吗？',
      time: '10:00',
      actionButtons: [
        AiChatAction(AiActionKind.prompt, '查明天下节课'),
        AiChatAction(AiActionKind.prompt, '宿舍报修流转'),
        AiChatAction(AiActionKind.prompt, '成绩与绩点'),
      ],
    ),
  ];

  Future<void> submit(AiChatRequest request, {String? retryMessageId}) async {
    if (isSending) return;
    final activeGeneration = generation;
    bool isCurrent() => !_disposed && activeGeneration == generation;
    final responseId = 'response_${DateTime.now().microsecondsSinceEpoch}';
    _update(() {
      if (retryMessageId != null) {
        messages.removeWhere((message) => message.id == retryMessageId);
      } else {
        messages.add(
          AiChatMessage(
            id: 'user_${DateTime.now().microsecondsSinceEpoch}',
            isUser: true,
            text: request.displayText,
            time: _nowTime(),
          ),
        );
      }
      isThinking = true;
      isSending = true;
    });

    bool gotDelta = false;
    bool completed = false;
    bool degraded = false;
    Map<String, dynamic>? remote;
    String? errorCode;
    String? errorMessage;
    try {
      if (_conversationId == null) {
        final id = await gateway.createConversation().timeout(
          const Duration(seconds: 12),
        );
        if (!isCurrent()) return;
        _conversationId = id;
      }
      final conversationId = _conversationId;
      if (conversationId != null) {
        try {
          final iterator = StreamIterator(
            gateway
                .streamMessage(
                  conversationId: conversationId,
                  content: request.query,
                  attachments: request.attachments,
                  clientRequestId: request.clientRequestId,
                )
                .timeout(const Duration(seconds: 30)),
          );
          _activeStream = iterator;
          try {
            while (await iterator.moveNext()) {
              final event = iterator.current;
              if (!isCurrent()) return;
              if (event is AiDelta && event.text.isNotEmpty) {
                _update(() {
                  isThinking = false;
                  final index = messages.indexWhere((m) => m.id == responseId);
                  if (index < 0) {
                    messages.add(
                      AiChatMessage(
                        id: responseId,
                        isUser: false,
                        text: event.text,
                        time: _nowTime(),
                        isStreaming: true,
                      ),
                    );
                  } else {
                    messages[index] = messages[index].copyWith(
                      text: '${messages[index].text}${event.text}',
                    );
                  }
                  gotDelta = true;
                });
              } else if (event is AiDone) {
                remote = event.data;
                degraded = remote['degraded'] == true;
                completed =
                    !degraded &&
                    (gotDelta ||
                        (remote['content']?.toString().trim().isNotEmpty ??
                            false));
                break;
              } else if (event is AiError) {
                errorCode = event.code;
                errorMessage = event.message;
                break;
              }
            }
          } finally {
            if (identical(_activeStream, iterator)) _activeStream = null;
            await iterator.cancel();
          }
        } catch (_) {
          // 已接收片段在下方保留并标记中断；没有片段时尝试普通接口。
        }
        if (!isCurrent()) return;
        if (!gotDelta && !completed && !degraded && errorCode == null) {
          remote = await gateway
              .sendMessage(
                conversationId: conversationId,
                content: request.query,
                attachments: request.attachments,
                clientRequestId: request.clientRequestId,
              )
              .timeout(const Duration(seconds: 30));
          if (!isCurrent()) return;
          degraded = remote?['degraded'] == true;
          completed =
              !degraded &&
              (remote?['content']?.toString().trim().isNotEmpty ?? false);
        }
      }
    } on ApiException catch (e) {
      errorCode = e.code;
      errorMessage = e.message;
    } catch (_) {
      // 超时与传输异常统一进入可重试状态。
    }
    if (!isCurrent()) return;
    _update(() {
      isThinking = false;
      isSending = false;
      final content = remote?['content']?.toString() ?? '';
      final actions = (remote?['actions'] as List? ?? const [])
          .whereType<Map>()
          .map(AiChatAction.fromJson)
          .where(
            (a) => a.label.isNotEmpty && a.kind != AiActionKind.unsupported,
          )
          .toList();
      final index = messages.indexWhere((m) => m.id == responseId);
      final partial = index < 0 ? '' : messages[index].text;
      final terminal =
          degraded || errorCode == 'AI_409_03' || errorCode == 'AI_409_04';
      final response = AiChatMessage(
        id: responseId,
        isUser: false,
        text: (completed || degraded) && content.isNotEmpty
            ? content
            : gotDelta
            ? partial
            : (errorCode != null ? errorMessage : null) ?? '暂未取得 AI 回复，请检查网络后重试。课程和成绩请通过下方入口查询。',
        time: _nowTime(),
        statusText: degraded
            ? 'AI 服务暂时不可用，可作为新问题发送'
            : gotDelta && !completed
            ? '连接已中断，以上回复尚未完成'
            : errorCode == 'AI_409_01'
            ? '问题仍在处理中，稍后重试将取回同一回复'
            : terminal
            ? '本次请求已结束，可作为新问题发送'
            : null,
        retryRequest: completed ? null : request,
        resendAsNew: terminal,
        actionButtons: completed
            ? actions
            : const [
                AiChatAction(AiActionKind.schedule, '查看课表'),
                AiChatAction(AiActionKind.grades, '查看成绩'),
              ],
        sources: completed ? _parseSources(remote?['sources']) : const [],
      );
      if (index < 0) {
        messages.add(response);
      } else {
        messages[index] = response;
      }
    });
  }

  String _nowTime() =>
      '${DateTime.now().hour.toString().padLeft(2, '0')}:${DateTime.now().minute.toString().padLeft(2, '0')}';

  List<AiSource> _parseSources(dynamic raw) {
    if (raw is! List) return const [];
    final result = <AiSource>[];
    for (final item in raw) {
      if (item is! Map) continue;
      final pageRaw = item['page'];
      final page = pageRaw is num
          ? pageRaw.toInt()
          : int.tryParse(pageRaw?.toString() ?? '');
      final scoreRaw = item['score'];
      final score = scoreRaw is num
          ? scoreRaw.toDouble()
          : double.tryParse(scoreRaw?.toString() ?? '') ?? 0;
      result.add(
        AiSource(
          fileName: item['fileName']?.toString() ?? '未知文档',
          page: page,
          titlePath: item['titlePath']?.toString(),
          score: score,
          snippet: item['snippet']?.toString() ?? '',
        ),
      );
    }
    return result;
  }

  void reset() {
    generation++;
    unawaited(_activeStream?.cancel());
    _activeStream = null;
    _conversationId = null;
    isThinking = false;
    isSending = false;
    messages
      ..clear()
      ..add(
        const AiChatMessage(
          id: 'msg_welcome',
          isUser: false,
          text: '对话已重置。同学你好，随时向我提问校园生活与学业问题！',
          time: '刚刚',
        ),
      );
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    unawaited(_activeStream?.cancel());
    _activeStream = null;
    generation++;
    super.dispose();
  }
}
