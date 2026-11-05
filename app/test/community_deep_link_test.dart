import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/community_api.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/services/community_deep_link_service.dart';

import 'community_interaction_test.dart' as fixtures;

/// D-Share 深链验收：
/// - URI 解析：App Links / scheme / 旧数字 id 三形态，非帖子链接拒绝；
/// - 分享链接构建：优先 slug，无 slug 回退数字 id。
void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('URI 解析三种帖子形态', () {
    expect(
      CommunityDeepLinkService.postIdFromUri(
        Uri.parse('https://sailtrack.cn/c/HTOPUVso'),
      ),
      'HTOPUVso',
    );
    expect(
      CommunityDeepLinkService.postIdFromUri(
        Uri.parse('https://sailtrack.cn/c/12345'),
      ),
      '12345',
    );
    expect(
      CommunityDeepLinkService.postIdFromUri(
        Uri.parse('studenthub://p/HTOPUVso'),
      ),
      'HTOPUVso',
    );
    // 非帖子链接拒绝
    expect(
      CommunityDeepLinkService.postIdFromUri(
        Uri.parse('https://sailtrack.cn/'),
      ),
      isNull,
    );
    expect(
      CommunityDeepLinkService.postIdFromUri(
        Uri.parse('https://sailtrack.cn/x/HTOPUVso'),
      ),
      isNull,
    );
    expect(
      CommunityDeepLinkService.postIdFromUri(
        Uri.parse('https://evil.example.com/c/HTOPUVso'),
      ),
      isNull,
    );
    expect(
      CommunityDeepLinkService.postIdFromUri(Uri.parse('studenthub://q/abc')),
      isNull,
    );
  });

  test('分享链接构建：优先 slug，无 slug 回退数字 id', () {
    final base = fixtures.post('9');
    expect(
      CommunityApi.shareLinkOf(base),
      'https://sailtrack.cn/c/9',
      reason: '无 slug 回退数字 id',
    );
    final withSlug = CommunityPost(
      id: '9',
      authorId: 'a',
      authorName: 'x',
      authorLevel: 'Lv.1',
      timeAgo: '刚刚',
      location: '校园',
      title: 't',
      content: 'c',
      shareSlug: 'HTOPUVso',
    );
    expect(
      CommunityApi.shareLinkOf(withSlug),
      'https://sailtrack.cn/c/HTOPUVso',
      reason: '有 slug 用 slug',
    );
  });
  test('冷启动和登录前的链接会等待就绪，同一链接只打开一次', () async {
    String? account;
    var ready = false;
    var fetches = 0;
    final opened = <String>[];
    final router = CommunityDeepLinkRouter(
      fetchPost: (id) async {
        fetches++;
        return fixtures.post(id);
      },
      openPost: (post) => opened.add(post.id),
      accountId: () => account,
      sessionRevision: () => 1,
      isReady: () => ready,
    );
    final uri = Uri.parse('https://sailtrack.cn/c/ABC234xy');
    await router.receive(uri);
    account = 'viewer';
    await router.resume();
    expect(fetches, 0);
    ready = true;
    await router.resume();
    await router.receive(uri);
    expect(opened, ['ABC234xy']);
    expect(fetches, 1);
  });

  test('请求中换号或退出后，不会打开旧账号的帖子', () async {
    var revision = 1;
    String? account = 'viewer';
    final response = Completer<CommunityPost?>();
    final opened = <String>[];
    final router = CommunityDeepLinkRouter(
      fetchPost: (_) => response.future,
      openPost: (post) => opened.add(post.id),
      accountId: () => account,
      sessionRevision: () => revision,
      isReady: () => true,
    );
    final pending = router.receive(Uri.parse('studenthub://p/ABC234xy'));
    account = 'other';
    revision++;
    response.complete(fixtures.post('9'));
    await pending;
    expect(opened, isEmpty);
  });

  test('就绪前换号会丢弃旧待办，新账号可立即打开同一链接', () async {
    var revision = 1;
    var ready = false;
    var account = 'first';
    final opened = <String>[];
    final router = CommunityDeepLinkRouter(
      fetchPost: (id) async => fixtures.post(id),
      openPost: (post) => opened.add(post.id),
      accountId: () => account,
      sessionRevision: () => revision,
      isReady: () => ready,
    );
    final uri = Uri.parse('studenthub://p/ABC234xy');
    await router.receive(uri);
    account = 'second';
    revision++;
    ready = true;
    await router.resume();
    expect(opened, isEmpty);
    await router.receive(uri);
    expect(opened, ['ABC234xy']);
    account = 'third';
    revision++;
    await router.receive(uri);
    expect(opened, ['ABC234xy', 'ABC234xy']);
  });

  test('新链接替代请求中的旧链接', () async {
    final firstResponse = Completer<CommunityPost?>();
    final opened = <String>[];
    final router = CommunityDeepLinkRouter(
      fetchPost: (id) => id == 'first'
          ? firstResponse.future
          : Future.value(fixtures.post(id)),
      openPost: (post) => opened.add(post.id),
      accountId: () => 'viewer',
      sessionRevision: () => 1,
      isReady: () => true,
    );
    final first = router.receive(Uri.parse('studenthub://p/first'));
    await router.receive(Uri.parse('studenthub://p/second'));
    firstResponse.complete(fixtures.post('first'));
    await first;
    await Future<void>.delayed(Duration.zero);
    expect(opened, ['second']);
  });

  test('帖子不可用时给出反馈，非法路径不进入请求', () async {
    var failures = 0;
    final router = CommunityDeepLinkRouter(
      fetchPost: (_) async => null,
      openPost: (_) => fail('不可导航'),
      accountId: () => 'viewer',
      sessionRevision: () => 1,
      isReady: () => true,
      onUnavailable: () => failures++,
    );
    await router.receive(Uri.parse('studenthub://p/a/b'));
    expect(failures, 0);
    await router.receive(Uri.parse('studenthub://p/unknown'));
    expect(failures, 1);
  });
}
