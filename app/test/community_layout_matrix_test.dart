import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/pages/community/author_profile_page.dart';
import 'package:smartcampus_app/pages/community/community_home_page.dart';
import 'package:smartcampus_app/pages/community/post_detail_page.dart';
import 'package:smartcampus_app/services/community_interaction_store.dart';

import 'community_interaction_test.dart' as fixtures;

const longPost = CommunityPost(
  id: 'layout-post',
  authorId: 'layout-author-with-long-identifier',
  authorName: '校园学习互助与创新实践交流协会的同学',
  authorLevel: 'Lv.16',
  authorOfficial: true,
  authorOfficialTitle: '校园学习互助与创新实践交流协会',
  authorDepartment: '人工智能与软件工程学院创新实践实验班',
  authorUid: '202610250123456789',
  timeAgo: '十分钟前',
  location: '绵阳城市学院图书馆二楼自习交流区',
  title: '关于期末复习资料整理与校园学习互助活动报名的详细说明',
  content:
      '这是一条真实参与布局的长文本内容。我们希望同学在窄屏、大字体和减少动画的设置下，'
      '仍然可以阅读课程复习说明、查看作者信息以及使用关注、点赞和分享操作。'
      '欢迎不同学院的同学一起讨论课程学习方法，按自己的时间报名参加线下交流。',
  topics: ['期末复习资料整理', '校园学习互助与创新实践'],
  likeCount: 123456,
  commentCount: 1234,
);

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  for (final width in [360.0, 390.0, 430.0]) {
    for (final scale in [1.3, 1.5]) {
      for (final page in ['home', 'detail', 'author']) {
        testWidgets('$page long content fits ${width.toInt()} at $scale', (
          tester,
        ) async {
          tester.view.physicalSize = Size(width, 900);
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          final interactions = CommunityInteractionStore(
            accountId: 'layout-viewer',
            gateway: fixtures.FakeGateway(),
          );
          addTearDown(interactions.dispose);
          final Widget target = switch (page) {
            'home' => CommunityHomePage(
              initialPosts: const [longPost],
              interactionStore: interactions,
            ),
            'detail' => PostDetailPage(
              post: longPost,
              interactionStore: interactions,
            ),
            _ => AuthorProfilePage(
              authorId: longPost.authorId,
              authorName: longPost.authorName,
              authorOfficial: true,
              authorOfficialTitle: longPost.authorOfficialTitle,
              sourcePost: longPost,
              interactionStore: interactions,
            ),
          };
          await tester.pumpWidget(
            MaterialApp(
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(context).copyWith(
                  textScaler: TextScaler.linear(scale),
                  disableAnimations: true,
                ),
                child: child!,
              ),
              home: target,
            ),
          );
          await tester.pumpAndSettle();
          expect(find.text(longPost.authorName), findsWidgets);
          expect(tester.takeException(), isNull);
            if (page == 'detail') {
              for (final key in ['detail-like', 'detail-local-bookmark']) {
                final size = tester.getSize(find.byKey(ValueKey(key)));
                expect(size.width, greaterThanOrEqualTo(48));
                expect(size.height, greaterThanOrEqualTo(48));
              }
            }
            if (page == 'author') {
              for (final tooltip in ['返回', '分享作者主页']) {
                final size = tester.getSize(find.byTooltip(tooltip));
                expect(size.width, greaterThanOrEqualTo(48));
                expect(size.height, greaterThanOrEqualTo(48));
              }
            }
          final scroll = find.byType(Scrollable).first;
          for (var step = 0; step < 4; step++) {
            await tester.drag(scroll, const Offset(0, -450));
            await tester.pumpAndSettle();
            expect(tester.takeException(), isNull);
          }
          await tester.pumpWidget(const SizedBox.shrink());
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);
        });
      }
    }
  }
}
