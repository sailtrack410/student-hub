import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/pages/community/community_home_page.dart';
import 'package:smartcampus_app/services/community_interaction_store.dart';
import 'package:smartcampus_app/widgets/community_post_card.dart';

import 'community_interaction_test.dart' as fixtures;

/// A2/A3/A4 动效验收：
/// - 点赞激活瞬间 burst：中段 scale>1，结束回落 1；
/// - 计数滚动：切换瞬间新旧文本并存（滚动过渡），落定后仅新文本；
/// - 收藏激活：图标交叉淡化（新旧并存瞬态），burst 同语言。
void main() {
  setUp(() => CommunityPostCard.debugBuildsByPostId.clear());

  Future<CommunityInteractionStore> pumpFeed(WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: fixtures.FakeGateway()
        ..onLike = (id) async => fixtures.post(id, likes: 287, liked: true),
    );
    addTearDown(store.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: CommunityHomePage(
          initialPosts: CommunityMockData.getInitialPosts(),
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

  testWidgets('点赞激活瞬间弹 burst，结束回落 1', (tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    await pumpFeed(tester);
    await tester.tap(find.text('286'));
    await tester.pump(); // 乐观置位 + burst 启动
    await tester.pump(const Duration(milliseconds: 40)); // 上扬中段

    expect(burstScale(tester), greaterThan(1.01), reason: 'burst 上扬应可见');

    await tester.pumpAndSettle();
    expect(burstScale(tester), 1.0, reason: 'burst 结束必须停回 1，无常驻放大');
    expect(find.text('287'), findsOneWidget);
  });

  testWidgets('计数变化走滚动过渡：瞬态新旧并存，落定仅新值', (tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    await pumpFeed(tester);
    await tester.tap(find.text('286'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 60)); // 滚动中段

    expect(find.text('286'), findsWidgets, reason: '旧计数淡出中应仍可见');
    expect(find.text('287'), findsWidgets, reason: '新计数滑入应已可见');

    await tester.pumpAndSettle();
    expect(find.text('286'), findsNothing);
    expect(find.text('287'), findsOneWidget);
  });

  testWidgets('收藏激活：图标交叉淡化瞬态 + burst，落定仅实心书签', (tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    await pumpFeed(tester);
    await tester.tap(find.byKey(const ValueKey('community-bookmark')).first);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50)); // 交叉淡化中段

    final card1 = find
        .ancestor(of: find.text('286'), matching: find.byType(CommunityPostCard))
        .first;
    bool card1Has(IconData icon) => find
        .descendant(of: card1, matching: find.byIcon(icon))
        .evaluate()
        .isNotEmpty;

    expect(card1Has(Icons.bookmark_border_rounded), isTrue,
        reason: '旧描边书签淡出中应仍可见');
    expect(card1Has(Icons.bookmark_rounded), isTrue, reason: '新实心书签淡入应已可见');
    expect(burstScale(tester), greaterThan(1.01));

    await tester.pumpAndSettle();
    expect(card1Has(Icons.bookmark_rounded), isTrue);
    expect(card1Has(Icons.bookmark_border_rounded), isFalse);
    expect(burstScale(tester), 1.0);
  });
}
