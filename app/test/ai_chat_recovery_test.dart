import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/api/ai_api.dart';
import 'package:smartcampus_app/pages/assistant/ai_assistant_page.dart';
import 'package:smartcampus_app/pages/schedule/grade_query_page.dart';
import 'package:smartcampus_app/services/ai_chat_gateway.dart';

class _Gateway extends AiChatGateway {
  int created = 0;
  int streamed = 0;
  int sent = 0;
  final List<String> conversationIds = [];
  final List<String?> requestIds = [];
  final List<List<Map<String, dynamic>>> requestAttachments = [];
  Stream<AiStreamEvent> Function()? streamFactory;
  Future<AiPickedAttachment?> Function()? pickFactory;
  Future<Map<String, dynamic>?> Function(AiPickedAttachment)? uploadFactory;
  int uploaded = 0;
  Map<String, dynamic>? fallback;

  @override
  Future<String?> createConversation() async => 'conversation_${++created}';

  @override
  Future<List<Map<String, String>>> fetchRecommendations() async => [];

  @override
  Future<AiPickedAttachment?> pickDocumentAttachment() async =>
      pickFactory?.call();

  @override
  Future<Map<String, dynamic>?> uploadAttachment(
    AiPickedAttachment file,
  ) async {
    uploaded++;
    return uploadFactory?.call(file);
  }

  @override
  Stream<AiStreamEvent> streamMessage({
    required String conversationId,
    required String content,
    String? clientRequestId,
    required List<Map<String, dynamic>> attachments,
  }) {
    requestIds.add(clientRequestId);
    streamed++;
    conversationIds.add(conversationId);
    requestAttachments.add(attachments);
    return streamFactory?.call() ?? Stream.value(const AiError('offline'));
  }

  @override
  Future<Map<String, dynamic>?> sendMessage({
    required String conversationId,
    required String content,
    String? clientRequestId,
    required List<Map<String, dynamic>> attachments,
  }) async {
    requestIds.add(clientRequestId);
    sent++;
    return fallback;
  }
}

Future<void> _open(
  WidgetTester tester,
  _Gateway gateway, {
  double bottomInset = 0,
  double width = 390,
  double textScale = 1,
}) async {
  tester.view.physicalSize = Size(width, 844);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  await tester.pumpWidget(
    MaterialApp(
      home: MediaQuery(
        data: MediaQueryData(
          size: Size(width, 844),
          padding: EdgeInsets.only(bottom: bottomInset),
          viewPadding: EdgeInsets.only(bottom: bottomInset),
          textScaler: TextScaler.linear(textScale),
        ),
        child: AIAssistantPage(gateway: gateway),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _flush(WidgetTester tester) async {
  await tester.runAsync(
    () => Future<void>.delayed(const Duration(milliseconds: 10)),
  );
  await tester.pump();
}

Future<void> _send(WidgetTester tester, String question) async {
  await tester.enterText(find.byType(TextField), question);
  await tester.testTextInput.receiveAction(TextInputAction.send);
  await _flush(tester);
  await tester.pumpAndSettle();
}

Future<void> _chooseDocument(WidgetTester tester) async {
  await tester.tap(find.byTooltip('添加附件'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('文档'));
  await tester.pumpAndSettle();
}

Future<void> _newConversation(WidgetTester tester) async {
  await tester.tap(find.byTooltip('开始新对话'));
  await tester.pumpAndSettle();
  await tester.tap(find.widgetWithText(ElevatedButton, '开始新对话'));
  await tester.pumpAndSettle();
}

AiPickedAttachment _document([String name = '同名文档.pdf']) =>
    AiPickedAttachment(name: name, bytes: Uint8List.fromList([1, 2, 3]));

Finder _inputButton(String label) => find.byWidgetPredicate(
  (widget) => widget is IconButton && widget.tooltip == label,
);

void main() {
  testWidgets('AI dual failure offers retry without fabricated personal data', (
    tester,
  ) async {
    final gateway = _Gateway();
    await _open(tester, gateway);
    await _send(tester, '查询我的成绩');
    expect(find.textContaining('暂未取得 AI 回复'), findsOneWidget);
    expect(find.textContaining('3.78'), findsNothing);
    expect(find.textContaining('专业排名前'), findsNothing);
    expect(find.text('查看课表'), findsOneWidget);
    expect(find.text('查看成绩'), findsOneWidget);
    expect(gateway.sent, 1);
    expect(gateway.requestIds.toSet(), hasLength(1));
    expect(gateway.requestIds.first, isNotEmpty);

    gateway.fallback = {'content': '这是服务端的真实回复'};
    await tester.tap(find.text('重试此问题'));
    await _flush(tester);
    await tester.pumpAndSettle();
    expect(find.text('查询我的成绩'), findsOneWidget);
    expect(find.textContaining('这是服务端的真实回复'), findsOneWidget);
    expect(find.text('重试此问题'), findsNothing);
    expect(gateway.conversationIds, ['conversation_1', 'conversation_1']);
    expect(gateway.requestIds.toSet(), hasLength(1));
  });

  testWidgets(
    'AI interruption keeps partial text and blocks concurrent sends',
    (tester) async {
      final controller = StreamController<AiStreamEvent>();
      final gateway = _Gateway()..streamFactory = () => controller.stream;
      await _open(tester, gateway);
      await tester.enterText(find.byType(TextField), '第一个问题');
      await tester.testTextInput.receiveAction(TextInputAction.send);
      await tester.pump();
      controller.add(const AiDelta('已收到的片段'));
      await tester.pumpAndSettle();
      await _send(tester, '第二个问题');
      expect(gateway.streamed, 1);
      expect(find.text('第二个问题'), findsOneWidget); // 尚留在输入框，未发送为消息。

      controller.add(const AiError('connection lost'));
      await tester.runAsync(controller.close);
      await tester.pumpAndSettle();
      expect(find.textContaining('已收到的片段'), findsOneWidget);
      expect(find.text('连接已中断，以上回复尚未完成'), findsOneWidget);
      expect(find.text('重试此问题'), findsOneWidget);
      expect(gateway.sent, 0);
    },
  );

  testWidgets('new conversation ignores late old stream responses', (
    tester,
  ) async {
    final controller = StreamController<AiStreamEvent>();
    final gateway = _Gateway()..streamFactory = () => controller.stream;
    await _open(tester, gateway);
    await tester.enterText(find.byType(TextField), '旧问题');
    await tester.testTextInput.receiveAction(TextInputAction.send);
    await tester.pump();
    await tester.tap(find.byTooltip('开始新对话'));
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.textContaining('不会删除服务端历史记录'), findsOneWidget);
    await tester.tap(find.widgetWithText(ElevatedButton, '开始新对话'));
    await tester.pumpAndSettle();
    controller.add(const AiDelta('旧回复不应写入'));
    await tester.runAsync(controller.close);
    await tester.pumpAndSettle();
    expect(find.textContaining('旧回复不应写入'), findsNothing);

    gateway.streamFactory = () =>
        Stream.value(const AiDone({'content': '新会话回复'}));
    await _send(tester, '新问题');
    expect(gateway.conversationIds, ['conversation_1', 'conversation_2']);
    expect(find.textContaining('新会话回复'), findsOneWidget);
    expect(find.text('旧问题'), findsNothing);
  });

  testWidgets('AI failure grade action opens actual grade page', (
    tester,
  ) async {
    await _open(tester, _Gateway());
    await _send(tester, '成绩查询');
    await tester.tap(find.text('查看成绩'));
    await tester.pumpAndSettle();
    expect(find.byType(GradeQueryPage), findsOneWidget);
  });

  testWidgets('successful stream preserves citations and never falls back', (
    tester,
  ) async {
    final gateway = _Gateway()
      ..streamFactory = () => Stream.fromIterable([
        const AiDelta('正在回复'),
        const AiDone({
          'content': '实际回复',
          'sources': [
            {'fileName': '真实手册.pdf', 'snippet': '来源内容'},
          ],
        }),
      ]);
    await _open(tester, gateway);
    await _send(tester, '办理指南');
    expect(find.textContaining('实际回复'), findsOneWidget);
    expect(find.textContaining('真实手册.pdf'), findsWidgets);
    expect(find.text('重试此问题'), findsNothing);
    expect(gateway.sent, 0);
  });

  for (final transport in ['stream', 'done-only', 'POST']) {
    final streaming = transport != 'POST';
    testWidgets('degraded $transport stays retryable', (tester) async {
      const degraded = {'content': '服务端降级回复', 'degraded': true};
      final gateway = _Gateway()..fallback = degraded;
      gateway.streamFactory = () => streaming
          ? Stream.fromIterable([
              if (transport == 'stream') const AiDelta('服务端降级回复'),
              const AiDone(degraded),
            ])
          : Stream.value(const AiError('stream unavailable'));
      await _open(tester, gateway);
      await _send(tester, '需要重试的问题');
      expect(find.textContaining('服务端降级回复'), findsOneWidget);
      expect(find.text('AI 服务暂时不可用，可作为新问题发送'), findsOneWidget);
      expect(find.text('作为新问题发送'), findsOneWidget);
      expect(gateway.sent, streaming ? 0 : 1);

      gateway.streamFactory = () =>
          Stream.value(const AiDone({'content': '服务恢复后的回答'}));
      await tester.tap(find.text('作为新问题发送'));
      await _flush(tester);
      await tester.pumpAndSettle();
      expect(find.text('需要重试的问题'), findsOneWidget);
      expect(find.textContaining('服务恢复后的回答'), findsOneWidget);
      expect(gateway.requestIds.last, isNot(gateway.requestIds.first));
      expect(find.text('作为新问题发送'), findsNothing);
    });
  }

  for (final oldResult in ['success', 'failure', 'exception']) {
    testWidgets('late upload $oldResult cannot alter same-name replacement', (
      tester,
    ) async {
      final oldUpload = Completer<Map<String, dynamic>?>();
      final newUpload = Completer<Map<String, dynamic>?>();
      final gateway = _Gateway();
      gateway.pickFactory = () async => _document();
      // Each upload owns its future even when the first one is still pending.
      int uploadIndex = 0;
      gateway.uploadFactory = (_) =>
          uploadIndex++ == 0 ? oldUpload.future : newUpload.future;
      await _open(tester, gateway);
      await _chooseDocument(tester);
      await _chooseDocument(tester);
      switch (oldResult) {
        case 'success':
          oldUpload.complete({'fileId': 101});
        case 'failure':
          oldUpload.complete(null);
        case 'exception':
          oldUpload.completeError(StateError('late failure'));
      }
      await tester.pumpAndSettle();
      expect(find.text('同名文档.pdf'), findsOneWidget);
      expect(find.text('附件上传中…'), findsOneWidget);
      expect(find.text('附件上传失败，请重试'), findsNothing);
      final pendingSend = tester.widget<IconButton>(_inputButton('发送消息'));
      expect(pendingSend.onPressed, isNull);

      newUpload.complete({'fileId': 202});
      await tester.pumpAndSettle();
      expect(find.text('待发送 · 仅用于本次提问'), findsOneWidget);
      await _send(tester, '分析这个附件');
      expect(gateway.requestAttachments.single.single['fileId'], 202);
    });
  }

  testWidgets('removing attachment invalidates a late upload failure', (
    tester,
  ) async {
    final upload = Completer<Map<String, dynamic>?>();
    final gateway = _Gateway();
    gateway.pickFactory = () async => _document();
    gateway.uploadFactory = (_) => upload.future;
    await _open(tester, gateway);
    await _chooseDocument(tester);
    await tester.tap(find.byTooltip('移除附件'));
    await tester.pumpAndSettle();
    upload.complete(null);
    await tester.pumpAndSettle();
    expect(find.text('同名文档.pdf'), findsNothing);
    expect(find.text('附件上传失败，请重试'), findsNothing);
    await _send(tester, '只发送文本');
    expect(gateway.requestAttachments.single, isEmpty);
  });

  for (final phase in ['selection', 'upload']) {
    testWidgets('new conversation invalidates late attachment $phase', (
      tester,
    ) async {
      final selection = Completer<AiPickedAttachment?>();
      final upload = Completer<Map<String, dynamic>?>();
      final gateway = _Gateway();
      gateway.pickFactory = () =>
          phase == 'selection' ? selection.future : Future.value(_document());
      gateway.uploadFactory = (_) => upload.future;
      await _open(tester, gateway);
      await _chooseDocument(tester);
      await _newConversation(tester);
      if (phase == 'selection') {
        selection.complete(_document());
      } else {
        upload.complete({'fileId': 303});
      }
      await tester.pumpAndSettle();
      expect(find.text('同名文档.pdf'), findsNothing);
      expect(gateway.uploaded, phase == 'selection' ? 0 : 1);
      await _send(tester, '新会话文本');
      expect(gateway.requestAttachments.single, isEmpty);
    });
  }

  testWidgets('invalid uploaded file ID never sends a claimed attachment', (
    tester,
  ) async {
    final gateway = _Gateway();
    gateway.pickFactory = () async => _document();
    gateway.uploadFactory = (_) async => {'fileId': 'invalid'};
    await _open(tester, gateway);
    await _chooseDocument(tester);
    expect(find.text('同名文档.pdf'), findsNothing);
    expect(find.text('附件上传失败，请重试'), findsOneWidget);
    await _send(tester, '文本问题');
    expect(gateway.requestAttachments.single, isEmpty);
    expect(find.textContaining('[附件]'), findsNothing);
  });

  testWidgets(
    'standalone AI input respects bottom safe area and button semantics',
    (tester) async {
      final semantics = tester.ensureSemantics();
      try {
        await _open(
          tester,
          _Gateway(),
          bottomInset: 34,
          width: 320,
          textScale: 1,
        );
        expect(tester.takeException(), isNull);
        for (final label in ['添加附件', '语音输入', '发送消息']) {
          final button = _inputButton(label);
          expect(tester.getSize(button).width, greaterThanOrEqualTo(48));
          expect(tester.getSize(button).height, greaterThanOrEqualTo(48));
          expect(tester.getRect(button).bottom, lessThanOrEqualTo(844 - 34));
          expect(
            tester.getSemantics(button),
            matchesSemantics(
              label: label,
              isButton: true,
              hasEnabledState: true,
              isEnabled: true,
              hasTapAction: true,
              hasFocusAction: true,
              isFocusable: true,
            ),
          );
        }
      } finally {
        semantics.dispose();
      }
    },
  );
}
