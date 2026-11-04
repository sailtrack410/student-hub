import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/pages/community/post_detail_page.dart';
import 'package:smartcampus_app/services/community_interaction_store.dart';
import 'package:smartcampus_app/widgets/community_action_bar.dart';

import 'community_interaction_test.dart' as fixtures;

/// 详情页点赞动效验收（D1/D2）：
/// - 底部互动条与信息流同一动效语言：激活 burst、计数滚动、pending 脉冲/spinner；
/// - 双击正文/图片区触发点赞：点击位置弹心形 pop，已赞时双击不取消，
///   disableAnimations 时点赞仍生效但无 pop。
void main() {
  Future<CommunityInteractionStore> pumpDetail(
    WidgetTester tester, {
    fixtures.FakeGateway? gateway,
    CommunityPost? post,
    bool disableAnimations = false,
  }) async {
    SharedPreferences.setMockInitialValues({});
    if (disableAnimations) {
      tester.platformDispatcher.accessibilityFeaturesTestValue =
          const FakeAccessibilityFeatures(disableAnimations: true);
      addTearDown(tester.platformDispatcher.clearAccessibilityFeaturesTestValue);
    }
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: gateway ?? fixtures.FakeGateway(),
    );
    addTearDown(store.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: PostDetailPage(
          post: post ?? fixtures.post('a'),
          interactionStore: store,
        ),
      ),
    );
    await tester.pumpAndSettle();
    return store;
  }

  double burstScale(WidgetTester tester) {
    final scales = tester
        .widgetList<ScaleTransition>(find.byType(ScaleTransition))
        .map((s) => s.scale.value);
    return scales.reduce((a, b) => a > b ? a : b);
  }

  Finder popHeart() => find.byWidgetPredicate(
    (w) => w is Icon && w.icon == Icons.favorite_rounded && w.size == 64,
  );

  Finder likeCountText(String text) => find.descendant(
    of: find.byKey(const ValueKey('detail-like')),
    matching: find.text(text),
  );

  testWidgets('详情底栏四个互动入口单行横排（防 alignment 撑满行回归）', (tester) async {
    await pumpDetail(tester);

    final entries = find.byType(CommunityActionEntry).evaluate().toList();
    expect(entries.length, 4);
    final ys = entries.map((e) {
      final box = e.renderObject! as RenderBox;
      return box.localToGlobal(Offset.zero).dy;
    }).toList();
    for (final y in ys) {
      expect((y - ys.first).abs(), lessThan(2), reason: '四入口应同一行');
    }
  });

  testWidgets('详情底部点赞：激活 burst + 计数滚动，落定停回 1', (tester) async {
    await pumpDetail(tester);

    await tester.tap(find.byKey(const ValueKey('detail-like')));
    await tester.pump(); // 乐观置位 + burst 启动
    await tester.pump(const Duration(milliseconds: 40));

    expect(burstScale(tester), greaterThan(1.01), reason: 'burst 上扬应可见');
    expect(likeCountText('5'), findsWidgets, reason: '旧计数滚动淡出中应仍可见');
    expect(likeCountText('6'), findsWidgets, reason: '新计数滑入应已可见');

    await tester.pumpAndSettle();
    expect(burstScale(tester), 1.0, reason: 'burst 结束必须停回 1');
    expect(likeCountText('6'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('detail-like')),
        matching: find.byIcon(Icons.favorite_rounded),
      ),
      findsOneWidget,
    );
  });

  testWidgets('双击正文触发点赞：点击位置弹心形 pop，计数落定', (tester) async {
    await pumpDetail(tester);

    await tester.tap(find.text('帖子a'));
    await tester.pump(const Duration(milliseconds: 100));
    await tester.tap(find.text('帖子a'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 80));

    expect(popHeart(), findsOneWidget, reason: '双击后应在点击位置弹心形 pop');
    expect(likeCountText('6'), findsWidgets, reason: '双击应触发点赞乐观计数');

    await tester.pumpAndSettle();
    expect(popHeart(), findsNothing, reason: 'pop 应自清');
    expect(likeCountText('6'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('detail-like')),
        matching: find.byIcon(Icons.favorite_rounded),
      ),
      findsOneWidget,
    );
  });

  testWidgets('已赞时双击正文不取消赞', (tester) async {
    final gateway = fixtures.FakeGateway();
    await pumpDetail(
      tester,
      gateway: gateway,
      post: fixtures.post('a', liked: true),
    );

    await tester.tap(find.text('帖子a'));
    await tester.pump(const Duration(milliseconds: 100));
    await tester.tap(find.text('帖子a'));
    await tester.pumpAndSettle();

    expect(gateway.likeCalls, 0, reason: '已赞时双击不应发请求');
    expect(likeCountText('5'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('detail-like')),
        matching: find.byIcon(Icons.favorite_rounded),
      ),
      findsOneWidget,
    );
    expect(popHeart(), findsNothing, reason: '未触发点赞不应弹 pop');
  });

  testWidgets('disableAnimations 下双击仍点赞但不弹 pop', (tester) async {
    await pumpDetail(tester, disableAnimations: true);

    await tester.tap(find.text('帖子a'));
    await tester.pump(const Duration(milliseconds: 100));
    await tester.tap(find.text('帖子a'));
    await tester.pumpAndSettle();

    expect(likeCountText('6'), findsOneWidget, reason: '点赞动作本身不受影响');
    expect(popHeart(), findsNothing, reason: '禁用动画时无心形 pop');
  });
}
