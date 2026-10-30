import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/pages/community/author_profile_page.dart';
import 'package:smartcampus_app/pages/community/community_home_page.dart';
import 'package:smartcampus_app/pages/community/post_detail_page.dart';
import 'package:smartcampus_app/services/community_feed_controller.dart';
import 'package:smartcampus_app/services/community_interaction_store.dart';

import 'community_interaction_test.dart' as fixtures;

class FeedGateway implements CommunityFeedGateway {
  final List<int> pages = [];
  Future<CommunityFeedBatch?> Function(CommunityFeedQuery, int)? load;
  @override
  Future<CommunityFeedBatch?> fetch(CommunityFeedQuery query, int pageNum) {
    pages.add(pageNum);
    return load!(query, pageNum);
  }
}

CommunityFeedBatch batch(String id, {int total = 2}) =>
    CommunityFeedBatch([CommunityFeedItem.ofPost(fixtures.post(id))], 1, total);

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test(
    'shared page scope isolates accounts and survives one page disposal',
    () async {
      final one = CommunityInteractionScope.acquire('account-one');
      final two = CommunityInteractionScope.acquire('account-one');
      final other = CommunityInteractionScope.acquire('account-two');
      expect(identical(one, two), isTrue);
      expect(identical(one, other), isFalse);
      CommunityInteractionScope.release(one);
      expect(await two.initialize(), isTrue);
      CommunityInteractionScope.release(two);
      final reopened = CommunityInteractionScope.acquire('account-one');
      expect(identical(one, reopened), isFalse);
      CommunityInteractionScope.release(reopened);
      CommunityInteractionScope.release(other);
    },
  );

  test(
    'late author profile cannot overwrite pending or completed follow',
    () async {
      final gateway = fixtures.FakeGateway();
      final result = Completer<bool?>();
      gateway.onFollow = (_, _) => result.future;
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: gateway,
      );
      addTearDown(store.dispose);
      final startedAt = store.revision;
      final operation = store.toggleAuthorFollowing(
        'author',
        currentFollowing: false,
      );
      store.acceptAuthor('author', false, startedAt);
      expect(store.isFollowing('author'), isTrue);
      result.complete(true);
      expect(await operation, isTrue);
      store.acceptAuthor('author', false, startedAt);
      expect(store.isFollowing('author'), isTrue);
    },
  );

  test(
    'refresh replaces older response and blocks pagination until settled',
    () async {
      final interactions = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: fixtures.FakeGateway(),
      );
      final gateway = FeedGateway();
      final slow = Completer<CommunityFeedBatch?>();
      gateway.load = (query, _) =>
          query.category == '推荐' ? slow.future : Future.value(batch('new'));
      final feed = CommunityFeedController(
        interactions: interactions,
        gateway: gateway,
      );
      addTearDown(feed.dispose);
      addTearDown(interactions.dispose);
      final older = feed.load(
        const CommunityFeedQuery(category: '推荐', adsEnabled: false),
        reset: true,
      );
      await feed.load(
        const CommunityFeedQuery(category: '推荐', adsEnabled: false),
      );
      expect(gateway.pages, [1]);
      await feed.load(
        const CommunityFeedQuery(category: '最新', adsEnabled: false),
        reset: true,
      );
      slow.complete(batch('old'));
      await older;
      expect(feed.items.single.post!.id, 'new');
      expect(feed.loading, isFalse);
    },
  );

  test(
    'failed pagination retries same page and deduplicates repeated posts',
    () async {
      final interactions = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: fixtures.FakeGateway(),
      );
      final gateway = FeedGateway();
      var failedOnce = false;
      gateway.load = (_, page) async {
        if (page == 1) return batch('first', total: 3);
        if (!failedOnce) {
          failedOnce = true;
          throw Exception('offline');
        }
        return CommunityFeedBatch(
          [
            CommunityFeedItem.ofPost(fixtures.post('first')),
            CommunityFeedItem.ofPost(fixtures.post('second')),
          ],
          2,
          3,
        );
      };
      final feed = CommunityFeedController(
        interactions: interactions,
        gateway: gateway,
      );
      addTearDown(feed.dispose);
      addTearDown(interactions.dispose);
      const query = CommunityFeedQuery(category: '最新', adsEnabled: false);
      await feed.load(query, reset: true);
      await feed.load(query);
      expect(feed.loadMoreFailed, isTrue);
      expect(feed.items.single.post!.id, 'first');
      await feed.load(query);
      expect(gateway.pages, [1, 2, 2]);
      expect(feed.items.map((e) => e.post!.id), ['first', 'second']);
      expect(feed.hasMore, isFalse);
      expect(feed.loadMoreFailed, isFalse);
    },
  );

  test(
    'disposing a feed ignores late completion without notifying listeners',
    () async {
      final interactions = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: fixtures.FakeGateway(),
      );
      final response = Completer<CommunityFeedBatch?>();
      final gateway = FeedGateway()..load = (_, _) => response.future;
      final feed = CommunityFeedController(
        interactions: interactions,
        gateway: gateway,
      );
      var notifications = 0;
      feed.addListener(() => notifications++);
      final loading = feed.load(
        const CommunityFeedQuery(category: '推荐', adsEnabled: false),
        reset: true,
      );
      feed.dispose();
      response.complete(batch('late'));
      await loading;
      expect(notifications, 1);
      expect(feed.items, isEmpty);
      interactions.dispose();
    },
  );

  testWidgets(
    'detail like blocks duplicates then restores failed request on home',
    (tester) async {
      final gateway = fixtures.FakeGateway();
      final response = Completer<CommunityPost?>();
      gateway.onLike = (_) => response.future;
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: gateway,
      );
      addTearDown(store.dispose);
      await tester.pumpWidget(
        MaterialApp(
          home: CommunityHomePage(
            initialPosts: [fixtures.post('a')],
            interactionStore: store,
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('帖子a'));
      await tester.pumpAndSettle();
      final like = find.byKey(const ValueKey('detail-like'));
      await tester.tap(like);
      await tester.tap(like);
      await tester.pump();
      expect(gateway.likeCalls, 1);
      expect(store.likePending('a'), isTrue);
      response.complete(null);
      await tester.pumpAndSettle();
      expect(find.text('点赞结果未确认，已恢复显示，请刷新后重试'), findsOneWidget);
      expect(store.apply(fixtures.post('a')).likeCount, 5);
      await tester.tap(find.byIcon(Icons.arrow_back_ios_new_rounded));
      await tester.pumpAndSettle();
      expect(find.byType(PostDetailPage), findsNothing);
      expect(find.text('5'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'detail follow confirms real response and remains synchronized on home',
    (tester) async {
      final gateway = fixtures.FakeGateway()
        ..onFetchFollowing = (_) async => true;
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: gateway,
      );
      addTearDown(store.dispose);
      await tester.pumpWidget(
        MaterialApp(
          home: CommunityHomePage(
            initialPosts: [fixtures.post('a')],
            interactionStore: store,
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('帖子a'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('detail-follow')));
      await tester.pumpAndSettle();
      expect(gateway.followCalls, 1);
      expect(store.isFollowing('author'), isFalse);
      expect(find.text('已取消关注'), findsOneWidget);
      gateway.onFetchFollowing = (_) async => false;
      await tester.tap(find.byIcon(Icons.arrow_back_ios_new_rounded));
      await tester.pumpAndSettle();
      expect(find.text('+ 关注'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'author follow blocks duplicate taps, rolls back failure and synchronizes detail',
    (tester) async {
      final gateway = fixtures.FakeGateway();
      final response = Completer<bool?>();
      gateway.onFollow = (_, _) => response.future;
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: gateway,
      );
      addTearDown(store.dispose);
      await tester.pumpWidget(
        MaterialApp(
          home: PostDetailPage(
            post: fixtures.post('a'),
            interactionStore: store,
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('同学author'));
      await tester.pumpAndSettle();
      expect(find.byType(AuthorProfilePage), findsOneWidget);
      final follow = find.byKey(const ValueKey('author-follow'));
      await tester.tap(follow);
      await tester.tap(follow);
      await tester.pump();
      expect(gateway.followCalls, 1);
      expect(find.text('处理中'), findsOneWidget);
      response.complete(null);
      await tester.pumpAndSettle();
      expect(store.isFollowing('author'), isFalse);
      expect(find.text('关注操作失败，已恢复显示，请重试'), findsOneWidget);
      gateway.onFollow = (_, value) async => value;
      await tester.tap(follow);
      await tester.pumpAndSettle();
      expect(store.isFollowing('author'), isTrue);
      await tester.tap(find.byIcon(Icons.arrow_back_ios_new_rounded));
      await tester.pumpAndSettle();
      expect(find.text('已关注'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
}
