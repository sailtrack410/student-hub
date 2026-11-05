import 'dart:async';

import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/models/user_session.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/community_api.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/pages/community/community_home_page.dart';
import 'package:smartcampus_app/services/community_feed_cache.dart';
import 'package:smartcampus_app/services/community_feed_controller.dart';
import 'package:smartcampus_app/services/session_store.dart';
import 'package:smartcampus_app/services/community_interaction_store.dart';
import 'package:smartcampus_app/widgets/skeleton_shimmer.dart';

import 'community_interaction_test.dart' as fixtures;

CommunityInteractionStore makeStore() => CommunityInteractionStore(
  accountId: 'viewer',
  gateway: fixtures.FakeGateway(),
);

class DelayedFeedGateway implements CommunityFeedGateway {
  final response = Completer<CommunityFeedBatch?>();

  @override
  Future<CommunityFeedBatch?> fetch(CommunityFeedQuery query, int pageNum) =>
      response.future;
}

/// C 系列验收：
/// - C1 骨架屏：冷启动加载中（无数据）首帧显示骨架；
/// - C2 分页预取：距底 900px 即应触发（阈值纯函数边界断言）；
/// - C3 缓存先显：上次快照冷启动秒开（刷新失败不清空），解析与在线同管线。
void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    UserSession.fromJson({
      'userId': 'viewer',
      'campusId': 1,
    }, 'test').activate();
  });

  CommunityFeedCacheScope scope() => CommunityFeedCacheScope(
    baseUrl: ApiClient.defaultBaseUrl,
    accountId: 'viewer',
    campusId: '1',
  );

  Map<String, dynamic> rawRecord(String id, String title) => {
    'itemType': 'POST',
    'post': {
      'id': id,
      'title': title,
      'content': '快照内容',
      'likeCount': 3,
      'commentCount': 2,
    },
  };

  testWidgets('C1 冷启动加载中首帧显示骨架屏', (tester) async {
    final store = makeStore();
    addTearDown(store.dispose);
    await tester.pumpWidget(
      MaterialApp(home: CommunityHomePage(interactionStore: store)),
    );
    // 首帧即断言：多 pump 一帧微任务就把网络失败链跑完切到错误态了
    expect(find.byType(SkeletonShimmer), findsOneWidget, reason: '无数据加载中应显示骨架');

    await tester.pumpAndSettle(); // 网络失败收尾，避免遗留计时器
  });

  test('C2 距底不足 900px 即应预取下一页', () {
    expect(CommunityHomePage.feedPreloadExtent, 900);
    expect(
      CommunityHomePage.shouldPreloadNextPage(99, 1000),
      isFalse,
      reason: '距底 901px 不应预取',
    );
    expect(
      CommunityHomePage.shouldPreloadNextPage(100, 1000),
      isTrue,
      reason: '距底恰 900px 即预取',
    );
    expect(CommunityHomePage.shouldPreloadNextPage(999, 1000), isTrue);
  });

  test('C3 快照往返：原始 records 经同管线解析出帖子且字段不失真', () async {
    await CommunityFeedCache.saveRawRecords(scope(), [
      rawRecord('9', '快照帖'),
      {'itemType': 'AD'}, // 广告条目原样保留，解析后会被 whereType 过滤
    ]);

    final records = await CommunityFeedCache.loadRawRecords(scope());
    expect(records, isNotNull);
    final page = CommunityApi.parseFeedPage({'records': records});
    final posts = page.items
        .map((item) => item.post)
        .whereType<CommunityPost>()
        .toList();

    expect(posts, hasLength(1));
    expect(posts.first.id, '9');
    expect(posts.first.title, '快照帖');
    expect(posts.first.likeCount, 3);
  });

  test('C3 无缓存/损坏快照按无缓存处理', () async {
    expect(await CommunityFeedCache.loadRawRecords(scope()), isNull);

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(scope().storageKey, '{broken');
    expect(await CommunityFeedCache.loadRawRecords(scope()), isNull);
  });

  testWidgets('C3 冷启动先显快照：网络失败也保留旧数据、不进错误态', (tester) async {
    await CommunityFeedCache.saveRawRecords(scope(), [rawRecord('9', '快照帖')]);
    final store = makeStore();
    addTearDown(store.dispose);
    await tester.pumpWidget(
      MaterialApp(home: CommunityHomePage(interactionStore: store)),
    );
    await tester.pumpAndSettle();

    expect(find.text('快照帖'), findsOneWidget, reason: '快照应先于网络结果显示');
    expect(find.text('加载失败'), findsNothing, reason: '列表非空时刷新失败不应进错误态');
  });
  testWidgets('分区请求未返回时快照独立刷新，在线空结果随后替换快照', (tester) async {
    await CommunityFeedCache.saveRawRecords(scope(), [rawRecord('9', '慢网快照')]);
    final sections = Completer<List<CommunitySection>>();
    final gateway = DelayedFeedGateway();
    final store = makeStore();
    addTearDown(store.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: CommunityHomePage(
          interactionStore: store,
          feedGateway: gateway,
          sectionsLoader: () => sections.future,
        ),
      ),
    );
    await tester.pump();
    expect(find.text('慢网快照'), findsOneWidget);
    expect(sections.isCompleted, isFalse);
    sections.complete([]);
    await tester.pump();
    gateway.response.complete(const CommunityFeedBatch([], 0, 0));
    await tester.pumpAndSettle();
    expect(find.text('慢网快照'), findsNothing);
  });

  test('退出登录清理快照，换号后迟到的列表响应不会进入控制器', () async {
    await CommunityFeedCache.saveRawRecords(scope(), [rawRecord('9', '旧账号')]);
    final store = makeStore();
    final gateway = DelayedFeedGateway();
    final controller = CommunityFeedController(
      interactions: store,
      gateway: gateway,
    );
    addTearDown(controller.dispose);
    addTearDown(store.dispose);
    final request = controller.load(
      const CommunityFeedQuery(category: '推荐', adsEnabled: false),
    );
    await SessionStore.clearToken();
    expect(await CommunityFeedCache.loadRawRecords(scope()), isNull);
    gateway.response.complete(
      CommunityFeedBatch([CommunityFeedItem.ofPost(fixtures.post('9'))], 1, 1),
    );
    await request;
    expect(controller.items, isEmpty);
  });

  test('快照按账号、校区、服务地址和查询隔离，空结果替换旧内容', () async {
    final original = scope();
    await CommunityFeedCache.saveRawRecords(original, [rawRecord('9', '旧内容')]);
    for (final other in [
      CommunityFeedCacheScope(
        baseUrl: original.baseUrl,
        accountId: 'other',
        campusId: '1',
      ),
      CommunityFeedCacheScope(
        baseUrl: original.baseUrl,
        accountId: 'viewer',
        campusId: '2',
      ),
      const CommunityFeedCacheScope(
        baseUrl: 'https://other.test',
        accountId: 'viewer',
        campusId: '1',
      ),
      CommunityFeedCacheScope(
        baseUrl: original.baseUrl,
        accountId: 'viewer',
        campusId: '1',
        sectionId: 2,
      ),
      CommunityFeedCacheScope(
        baseUrl: original.baseUrl,
        accountId: 'viewer',
        campusId: '1',
        sort: 'LATEST',
      ),
    ]) {
      expect(await CommunityFeedCache.loadRawRecords(other), isNull);
    }
    await CommunityFeedCache.saveRawRecords(original, []);
    expect(await CommunityFeedCache.loadRawRecords(original), isEmpty);
  });

  test('过期快照与退出后快照不会复用，旧写入不能在清理后复活', () async {
    await CommunityFeedCache.saveRawRecords(scope(), [rawRecord('9', '旧内容')]);
    expect(
      await CommunityFeedCache.loadRawRecords(
        scope(),
        now: DateTime.now().add(const Duration(hours: 2)),
      ),
      isNull,
    );
    final writing = CommunityFeedCache.saveRawRecords(scope(), [
      rawRecord('9', '迟到内容'),
    ]);
    await CommunityFeedCache.clear();
    await writing;
    expect(await CommunityFeedCache.loadRawRecords(scope()), isNull);
  });
}
