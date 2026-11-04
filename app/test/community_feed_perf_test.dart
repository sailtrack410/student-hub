import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/pages/community/community_home_page.dart';
import 'package:smartcampus_app/pages/community/post_detail_page.dart';
import 'package:smartcampus_app/services/community_interaction_store.dart';
import 'package:smartcampus_app/widgets/community_post_card.dart';

import 'community_interaction_test.dart' as fixtures;

/// B2/B3 验收：
/// - 信息流列表 cacheExtent 500（视口外预构建约 2.5 卡），每卡带重绘边界；
/// - 图片按显示尺寸×像素密度解码（cacheWidth）：信息流 92 格、详情 160 格；
/// - 加载淡入：首帧透明→完成后 200ms 淡入，缓存命中/禁用动画直接显示。
void main() {
  Future<CommunityInteractionStore> pumpFeed(
    WidgetTester tester, {
    List<CommunityPost>? posts,
    double dpr = 1.0,
  }) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = Size(375 * dpr, 812 * dpr);
    tester.view.devicePixelRatio = dpr;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: fixtures.FakeGateway(),
    );
    addTearDown(store.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: CommunityHomePage(
          initialPosts: posts ?? CommunityMockData.getInitialPosts(),
          interactionStore: store,
        ),
      ),
    );
    await tester.pumpAndSettle();
    return store;
  }

  testWidgets('信息流列表 cacheExtent 500 且卡片直接挂在重绘边界下', (tester) async {
    await pumpFeed(tester);

    final lists = tester.widgetList<ListView>(find.byType(ListView)).toList();
    expect(lists, isNotEmpty);
    expect(
      lists.any((l) {
        final extent = l.scrollCacheExtent;
        if (extent == null) return false;
        return (extent as dynamic).pixels == 500;
      }),
      isTrue,
      reason: '信息流列表应显式设置 scrollCacheExtent 500',
    );

    // 沿卡片向上最近几层必须遇到 RepaintBoundary（列表默认 addRepaintBoundaries，
    // 防止后续改代理时静默丢失导致整表重绘）
    int hops = 0;
    bool boundaryAbove = false;
    tester.element(find.byType(CommunityPostCard).first).visitAncestorElements((
      ancestor,
    ) {
      hops++;
      if (ancestor.widget is RepaintBoundary) {
        boundaryAbove = true;
        return false;
      }
      return hops < 6;
    });
    expect(boundaryAbove, isTrue, reason: '卡片应被 RepaintBoundary 包裹');
  });

  testWidgets('信息流图片按 92 格宽×像素密度解码（cacheWidth=276@3x）', (tester) async {
    await pumpFeed(
      tester,
      dpr: 3.0,
      posts: [
        fixtures.post(
          'img',
          images: ['https://example.com/a.jpg'],
          imageLabels: ['图一'],
        ),
      ],
    );

    final netImages = tester
        .widgetList<Image>(find.byType(Image))
        .where((w) => w.image is ResizeImage)
        .toList();
    expect(netImages, isNotEmpty);
    // Image.network(cacheWidth:) 会把 provider 包成 ResizeImage 按目标宽解码
    expect((netImages.first.image as ResizeImage).width, 276);
    expect(netImages.first.frameBuilder, isNotNull, reason: '应挂加载淡入');
  });

  testWidgets('详情图集按格宽×像素密度解码（单图 160 格→480@3x）', (tester) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(1125, 2436);
    tester.view.devicePixelRatio = 3.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: fixtures.FakeGateway(),
    );
    addTearDown(store.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: PostDetailPage(
          post: fixtures.post(
            'img',
            images: ['https://example.com/a.jpg'],
            imageLabels: ['图一'],
          ),
          interactionStore: store,
        ),
      ),
    );
    await tester.pumpAndSettle();

    final netImages = tester
        .widgetList<Image>(find.byType(Image))
        .where((w) => w.image is ResizeImage)
        .toList();
    expect(netImages, isNotEmpty);
    expect((netImages.first.image as ResizeImage).width, 480);
  });

  testWidgets('加载淡入 frameBuilder：首帧透明、完成淡入 200ms、缓存直显、禁动画零时长', (
    tester,
  ) async {
    late Widget loading, done, synced, reduced;
    await tester.pumpWidget(
      MaterialApp(
        home: Column(
          children: [
            Builder(
              builder: (context) {
                loading = communityImageFrameBuilder(
                  context,
                  const Text('c'),
                  null,
                  false,
                );
                done = communityImageFrameBuilder(
                  context,
                  const Text('c'),
                  1,
                  false,
                );
                synced = communityImageFrameBuilder(
                  context,
                  const Text('c'),
                  null,
                  true,
                );
                return const SizedBox.shrink();
              },
            ),
            MediaQuery(
              data: const MediaQueryData(disableAnimations: true),
              child: Builder(
                builder: (context) {
                  reduced = communityImageFrameBuilder(
                    context,
                    const Text('c'),
                    null,
                    false,
                  );
                  return const SizedBox.shrink();
                },
              ),
            ),
          ],
        ),
      ),
    );

    expect((loading as AnimatedOpacity).opacity, 0, reason: '首帧未就绪应全透明');
    expect((done as AnimatedOpacity).opacity, 1, reason: '解码完成应淡入到可见');
    expect((done as AnimatedOpacity).duration, const Duration(milliseconds: 200));
    expect(synced is AnimatedOpacity, isFalse, reason: '内存缓存命中不应闪淡入');
    expect((reduced as AnimatedOpacity).duration, Duration.zero, reason: '禁用动画应零时长');
  });
}
