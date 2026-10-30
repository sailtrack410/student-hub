import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/pages/community/post_detail_page.dart';
import 'package:smartcampus_app/services/community_interaction_store.dart';

import 'community_interaction_test.dart' as fixtures;

/// 回归：「不感兴趣」必须落真实 setHidden，不允许假提示。
/// 隐藏只作用于信息流（首页过滤），详情页自身与分享/主页入口不受影响。
void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('not-interested persists hidden state and shows undo', (
    tester,
  ) async {
    final gateway = fixtures.FakeGateway();
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: gateway,
    );
    addTearDown(store.dispose);
    await store.initialize();

    final post = fixtures.post('a');
    expect(store.isHidden(post.id), isFalse);

    await tester.pumpWidget(
      MaterialApp(home: PostDetailPage(post: post, interactionStore: store)),
    );
    await tester.pumpAndSettle();

    // 打开右上角更多菜单，点「不感兴趣」。
    await tester.tap(find.byIcon(Icons.more_horiz_rounded));
    await tester.pumpAndSettle();
    await tester.tap(find.text('不感兴趣（在信息流隐藏此帖）'));
    await tester.pump();

    // 状态真实写入 + 诚实提示（含撤销），旧的假话术不再出现。
    expect(store.isHidden(post.id), isTrue);
    expect(find.text('已为您减少此类内容推荐'), findsNothing);
    expect(find.text('已在信息流隐藏此帖（仅本机，不影响分享与主页查看）'), findsOneWidget);
    expect(find.text('撤销'), findsOneWidget);

    // 撤销恢复可见状态（SnackBar 动画先稳定，再滚到可见处点击）。
    await tester.pump(const Duration(milliseconds: 400));
    await tester.ensureVisible(find.text('撤销'));
    await tester.pump();
    await tester.tap(find.text('撤销'), warnIfMissed: false);
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();
    expect(store.isHidden(post.id), isFalse);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
