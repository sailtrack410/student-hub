import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/pages/community/community_home_page.dart';
import 'package:smartcampus_app/widgets/community_action_bar.dart';
import 'package:smartcampus_app/widgets/community_post_card.dart';
import 'package:smartcampus_app/widgets/user_avatar.dart';

/// 信息流卡片「逐元素尺寸 vs 画布」验收（画布 30:130/30:157/30:147 实测口径）。
/// 用户要求：UI 改动后必须量测元素尺寸与画布对比，跑绿测试不算验收。
/// 目标值与实现常量同源；实现改了而此处不改，验收即失败。
void main() {
  void report(String name, num actual, num target) {
    // ignore: avoid_print
    print('LAYOUT_AUDIT $name actual=$actual target=$target');
  }

  testWidgets('有图型卡片逐元素尺寸对照画布 30:130', (tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    await tester.pumpWidget(
      MaterialApp(
        home: CommunityHomePage(
          initialPosts: CommunityMockData.getInitialPosts(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final cardSize = tester.getSize(find.byType(CommunityPostCard).first);
    final avatar = tester.getSize(find.byType(UserAvatar).first);
    final badge = tester.getSize(
      find.byKey(const ValueKey('community-level-badge')).first,
    );
    final cell1 = tester.getRect(
      find.byKey(const ValueKey('community-image-cell-0')).first,
    );
    final cell2 = tester.getRect(
      find.byKey(const ValueKey('community-image-cell-1')).first,
    );
    final bar = tester.getSize(find.byType(CommunityPostActionBar).first);
    final likeIcon = tester.getSize(
      find.byIcon(Icons.favorite_border_rounded).first,
    );
    final more = tester.getSize(find.byTooltip('帖子更多操作').first);

    // meta（分区 · 时间）必须显示完整：渲染宽 >= 文本固有宽（防 Flexible 份额截断）
    const metaSample = '学习交流 · 2小时前';
    final metaRect = tester.getRect(find.text(metaSample).first);
    final metaTp = TextPainter(
      text: const TextSpan(
        text: metaSample,
        style: TextStyle(fontFamily: 'Noto Sans SC', fontSize: 11),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    report('meta 完整显示', metaRect.width, metaTp.width);
    expect(
      metaRect.width,
      greaterThanOrEqualTo(metaTp.width),
      reason: '分区·时间被截断说明头部行仍在按 Flexible 份额挤压 meta',
    );
    metaTp.dispose();

    report('卡片高(有图)', cardSize.height, 262);
    report('头像', avatar.height, 32);
    report('等级徽章高', badge.height, 16);
    report('图格边长', cell1.height, 92);
    report('图格间距', cell2.left - cell1.left - 92, 8);
    report('互动条高', bar.height, 20);
    report('点赞图标', likeIcon.height, 20);
    report('更多按钮宽', more.width, 20);

    expect(cardSize.height, 262);
    expect(avatar, const Size(32, 32));
    expect(badge.height, 16, reason: '徽章必须裹内容（h16），不得被有界约束撑满');
    expect(badge.width, lessThan(64), reason: '官方短标不得吃掉 Flexible 份额');
    expect(cell1.height, 92, reason: '预览格必须 1:1');
    expect(
      cell1.width,
      100,
      reason: '92 方格 + 右 margin 8（getRect 含 margin；间距断言已单列）',
    );
    expect(cell2.left - cell1.left - 92, 8, reason: 'getRect 含 margin，用规格宽 92 求 visual gap');
    expect(bar.height, 20);
    expect(likeIcon, const Size(20, 20));
    expect(more.width, 20);
  });

  testWidgets('无图型卡片高 162 且不渲染图格', (tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    const textOnly = CommunityPost(
      id: 'post_audit_text',
      authorId: 'user_201',
      authorName: '追光的少年',
      authorLevel: 'Lv.4',
      timeAgo: '2小时前',
      location: '绵阳城市学院 (游仙校区)',
      title: '无图型高度审计',
      content: '单行摘要审计',
      topics: ['校园生活'],
      partition: '学习交流',
      shareCount: 1,
      commentCount: 1,
      likeCount: 1,
    );
    await tester.pumpWidget(
      MaterialApp(home: CommunityHomePage(initialPosts: [textOnly])),
    );
    await tester.pumpAndSettle();

    final cardSize = tester.getSize(find.byType(CommunityPostCard).first);
    report('卡片高(无图)', cardSize.height, 162);
    expect(cardSize.height, 162);
    expect(
      find.byWidgetPredicate(
        (w) => w.key is ValueKey && (w.key as ValueKey).value.toString().startsWith('community-image-cell-'),
      ),
      findsNothing,
    );
  });
}
