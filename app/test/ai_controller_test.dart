import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/api/ai_api.dart';
import 'package:smartcampus_app/services/ai_chat_controller.dart';
import 'package:smartcampus_app/services/ai_chat_gateway.dart';

class Gateway extends AiChatGateway {
  Stream<AiStreamEvent> stream;
  var fallbacks = 0;
  Gateway(this.stream);
  @override
  Future<String?> createConversation() async => '1';
  @override
  Stream<AiStreamEvent> streamMessage({required String conversationId, required String content, String? clientRequestId, required List<Map<String,dynamic>> attachments}) => stream;
  @override
  Future<Map<String,dynamic>?> sendMessage({required String conversationId, required String content, String? clientRequestId, required List<Map<String,dynamic>> attachments}) async { fallbacks++; return null; }
}
void main() {
  test('reset cancels an idle stream and isolates its old result', () async {
    var cancelled = false;
    final stream = StreamController<AiStreamEvent>(onCancel: () { cancelled = true; });
    final controller = AiChatController(Gateway(stream.stream));
    final pending = controller.submit(AiChatRequest('old','old',[]));
    await Future<void>.delayed(Duration.zero);
    controller.reset();
    await pending;
    expect(cancelled, isTrue);
    expect(controller.messages, hasLength(1));
    controller.dispose();
    await stream.close();
  });
  for (final code in ['AI_409_03', 'AI_409_04', 'AI_409_01']) {
    test('structured $code never creates a fallback duplicate', () async {
      final gateway = Gateway(Stream.value(AiError('服务端状态', code: code)));
      final controller = AiChatController(gateway);
      await controller.submit(AiChatRequest('query','query',[]));
      expect(gateway.fallbacks, 0);
      expect(controller.messages.last.resendAsNew, code != 'AI_409_01');
      controller.dispose();
    });
  }
  test('actions decode typed targets rather than label substrings', () {
    final action = AiChatAction.fromJson({'type':'REQUEST_DETAIL','label':'打开','url':'/pages/service/request-detail?requestId=42'});
    expect(action.kind, AiActionKind.requestDetail);
    expect(action.targetId, '42');
    expect(AiChatAction.fromJson({'type':'UNKNOWN','label':'课表'}).kind, AiActionKind.unsupported);
  });
}
