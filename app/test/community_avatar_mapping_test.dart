import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/community_api.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/widgets/community_post_card.dart';
import 'package:smartcampus_app/widgets/user_avatar.dart';

/// 回归：社区头像与个人中心头像不一致。
///
/// 根因一：mapPost 未映射后端下发的 avatarUrl，CommunityPost.authorAvatar 恒为 null；
/// 根因二：帖子卡片/帖子详情/作者主页均用昵称首字母占位，从不渲染真实头像。
/// 修复后口径与评论行一致（评论行此前已用 UserAvatar 渲染 avatarUrl）。
void main() {
  group('CommunityApi.mapPost avatarUrl 映射', () {
    test('相对路径补全为完整地址', () {
      final post = CommunityApi.mapPost({
        'postId': '1',
        'authorName': '张三',
        'avatarUrl': '/files/avatar/1.png',
      });
      expect(
        post.authorAvatar,
        '${ApiClient.defaultBaseUrl}/files/avatar/1.png',
      );
    });

    test('完整 https 地址原样保留', () {
      final post = CommunityApi.mapPost({
        'postId': '2',
        'authorName': '李四',
        'avatarUrl': 'https://oss.example.com/a.png',
      });
      expect(post.authorAvatar, 'https://oss.example.com/a.png');
    });

    test('无 avatarUrl 时保持 null（首字母兜底）', () {
      final post = CommunityApi.mapPost({'postId': '3', 'authorName': '王五'});
      expect(post.authorAvatar, isNull);
    });
  });

  group('UserAvatar 渲染口径', () {
    testWidgets('有头像地址渲染网络图', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: UserAvatar(
              name: '张三',
              avatarPath: 'https://x.example/a.png',
              size: 40,
              fontSize: 16,
            ),
          ),
        ),
      );
      final image = tester.widget<Image>(find.byType(Image));
      expect((image.image as NetworkImage).url, 'https://x.example/a.png');
    });

    testWidgets('无头像回落昵称首字', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: UserAvatar(name: '张三', size: 40, fontSize: 16),
          ),
        ),
      );
      expect(find.byType(Image), findsNothing);
      expect(find.text('张'), findsOneWidget);
    });
  });

  testWidgets('帖子卡片作者行渲染真实头像', (tester) async {
    final post = CommunityPost(
      id: 'p1',
      authorId: '1',
      authorName: '张三',
      authorAvatar: 'https://x.example/a.png',
      authorLevel: 'Lv.4',
      timeAgo: '刚刚',
      location: '绵阳城市学院',
      title: '标题',
      content: '内容',
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: CommunityPostCard(
            post: post,
            accountId: 'me',
            likePending: false,
            followPending: false,
            bookmarkPending: false,
            onOpen: () {},
            onAuthor: () {},
            onFollow: () {},
            onMore: () {},
            onShare: () {},
            onBookmark: () {},
            onLike: () {},
          ),
        ),
      ),
    );
    await tester.pump();
    final image = tester.widget<Image>(find.byType(Image));
    expect((image.image as NetworkImage).url, 'https://x.example/a.png');
  });
}
