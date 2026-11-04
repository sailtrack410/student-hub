import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/pages/community/community_home_page.dart';
import 'package:smartcampus_app/services/community_interaction_store.dart';
import 'package:smartcampus_app/widgets/community_post_card.dart';

import 'community_interaction_test.dart' as fixtures;

/// B1 重建粒度验收：点赞/收藏只重建目标卡（其它卡 0 重建，不再整页 setState），
/// 隐藏因列表结构变化仍走 hiddenRevision 整表过滤。
/// 依据 community_post_card.debugBuildsByPostId（assert 内自增，release 零成本）。
void main() {
  setUp(() {
    CommunityPostCard.debugBuildCount = 0;
    CommunityPostCard.debugBuildsByPostId.clear();
  });

  Future<CommunityInteractionStore> pumpFeed(
    WidgetTester tester, {
    fixtures.FakeGateway? gateway,
  }) async {
    SharedPreferences.setMockInitialValues({});
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: gateway ?? fixtures.FakeGateway(),
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

  /// 断言：除 [targetId] 外所有卡零重建；[targetId] 重建 [expected] 次。
  void expectOnlyCardRebuilt(
    Map<String, int> before,
    String targetId,
    int minBuilds,
    int maxBuilds,
  ) {
    final after = Map<String, int>.of(CommunityPostCard.debugBuildsByPostId);
    final targetDelta = (after[targetId] ?? 0) - (before[targetId] ?? 0);
    expect(
      targetDelta,
      inInclusiveRange(minBuilds, maxBuilds),
      reason: '目标卡 $targetId 应重建 $minBuilds~$maxBuilds 次，实际 $targetDelta',
    );
    final others = <String>{...before.keys, ...after.keys}..remove(targetId);
    for (final id in others) {
      expect(
        (after[id] ?? 0) - (before[id] ?? 0),
        0,
        reason: '卡 $id 不应因其它卡的互动而重建',
      );
    }
  }

  Map<String, int> snapshotBuilds() =>
      Map<String, int>.of(CommunityPostCard.debugBuildsByPostId);

  testWidgets('点赞乐观更新+确认只重建被点卡片，计数滚动到位', (tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    final gateway = fixtures.FakeGateway()
      ..onLike = (id) async => fixtures.post(id, likes: 287, liked: true);
    await pumpFeed(tester, gateway: gateway);
    final before = snapshotBuilds();

    await tester.tap(find.text('286'));
    await tester.pumpAndSettle();

    expectOnlyCardRebuilt(before, 'post_101', 1, 2);
    expect(find.text('287'), findsOneWidget);
    expect(find.text('286'), findsNothing);
  });

  testWidgets('收藏只重建目标卡，成功提示照常弹出', (tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    await pumpFeed(tester);
    final before = snapshotBuilds();

    await tester.tap(find.byKey(const ValueKey('community-bookmark')).first);
    await tester.pumpAndSettle();

    expectOnlyCardRebuilt(before, 'post_101', 1, 2);
    expect(find.text('已收藏'), findsOneWidget);
  });

  testWidgets('隐藏仍会移除列表项，撤销后恢复（hiddenRevision 路径）', (tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    await pumpFeed(tester);
    const title = '关于自习室占座现象的建议与思考，大家怎么看？';
    expect(find.text(title), findsOneWidget);

    await tester.tap(find.byTooltip('帖子更多操作').first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('在此设备隐藏此帖'));
    await tester.pumpAndSettle();
    expect(find.text(title), findsNothing);

    await tester.tap(find.text('撤销'));
    await tester.pumpAndSettle();
    expect(find.text(title), findsOneWidget);
  });
}
