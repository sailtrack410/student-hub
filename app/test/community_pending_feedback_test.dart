import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/pages/community/community_home_page.dart';
import 'package:smartcampus_app/services/community_interaction_store.dart';
import 'package:smartcampus_app/widgets/community_post_card.dart';

import 'community_interaction_test.dart' as fixtures;

/// A1 pending 去跳变验收：
/// - 点击瞬间图标不消失（无 hourglass、无立即 spinner），300ms 内无 spinner；
/// - 超 300ms 未返回才淡入小 spinner，确认后 spinner 消失、计数落定；
/// - disableAnimations 时全程无 spinner。
void main() {
  Future<CommunityInteractionStore> pumpFeed(
    WidgetTester tester, {
    required Future<CommunityPost?> Function(String) onLike,
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
      gateway: fixtures.FakeGateway()..onLike = onLike,
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

  testWidgets('点赞 300ms 内图标保持原位，之后才淡入 spinner，确认后消失', (tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    final gate = Completer<CommunityPost?>();
    await pumpFeed(tester, onLike: (id) => gate.future);

    await tester.tap(find.text('286'));
    await tester.pump(); // pending 置位 + 乐观计数落位
    await tester.pump(const Duration(milliseconds: 120)); // 脉冲进行中

    // 关键口径：心形图标全程在原位（乐观计数 287 同帧显示），
    // 未超 300ms 不出现 spinner
    expect(find.text('287'), findsOneWidget);
    expect(_cardHasIcon(tester, '287', Icons.favorite_rounded), isTrue);
    expect(find.byType(CircularProgressIndicator), findsNothing);

    await tester.pump(const Duration(milliseconds: 260)); // 累计 380ms
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    gate.complete(fixtures.post('post_101', likes: 287, liked: true));
    await tester.pumpAndSettle();
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('287'), findsOneWidget);
  });

  testWidgets('秒回（pending 立即清除）时不闪 spinner', (tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    await pumpFeed(
      tester,
      onLike: (id) async => fixtures.post(id, likes: 287, liked: true),
    );

    await tester.tap(find.text('286'));
    await tester.pumpAndSettle();

    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('287'), findsOneWidget);
  });

  testWidgets('disableAnimations 下 pending 全程无 spinner', (tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    final gate = Completer<CommunityPost?>();
    await pumpFeed(
      tester,
      onLike: (id) => gate.future,
      disableAnimations: true,
    );

    await tester.tap(find.text('286'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));

    expect(find.text('287'), findsOneWidget);
    expect(_cardHasIcon(tester, '287', Icons.favorite_rounded), isTrue);
    expect(find.byType(CircularProgressIndicator), findsNothing);

    gate.complete(fixtures.post('post_101', likes: 287, liked: true));
    await tester.pumpAndSettle();
    expect(find.text('287'), findsOneWidget);
  });
}

/// 目标卡（按点赞数文本定位）内是否存在指定点赞图标
bool _cardHasIcon(WidgetTester tester, String countText, IconData icon) {
  final card = find
      .ancestor(of: find.text(countText), matching: find.byType(CommunityPostCard))
      .first;
  return find.descendant(of: card, matching: find.byIcon(icon)).evaluate().isNotEmpty;
}
