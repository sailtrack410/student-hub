import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/models/message_models.dart';
import 'package:smartcampus_app/pages/message/notification_center_page.dart';

void main() {
  testWidgets('inbox badges show exact count and cap at 99+', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      const MaterialApp(
        home: NotificationCenterPage(
          initialItems: [
            InboxItem(
              sessionId: 'd',
              sessionType: 'DIRECT_MESSAGE',
              title: '测试私信',
              lastContent: '内容',
              unreadCount: 12,
            ),
            InboxItem(
              sessionId: 'p',
              sessionType: 'PROCESS_NOTIFICATION',
              title: '流程',
              lastContent: '内容',
              unreadCount: 130,
            ),
          ],
        ),
      ),
    );
    expect(find.text('12'), findsOneWidget);
    expect(find.text('99+'), findsOneWidget);
    expect(find.bySemanticsLabel(RegExp('12 条未读消息')), findsOneWidget);
    semantics.dispose();
  });
}
