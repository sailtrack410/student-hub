import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
// SharedPreferences 的平台故障注入只用于验证真实写入失败的缓存恢复。
// ignore: depend_on_referenced_packages
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/community_api.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/models/user_session.dart';
import 'package:smartcampus_app/pages/community/community_home_page.dart';
import 'package:smartcampus_app/pages/community/post_detail_page.dart';
import 'package:smartcampus_app/services/community_interaction_store.dart';
import 'package:smartcampus_app/services/community_preferences_store.dart';

CommunityPost post(
  String id, {
  String author = 'author',
  int likes = 5,
  bool liked = false,
  String? title,
}) => CommunityPost(
  id: id,
  authorId: author,
  authorName: '同学$author',
  authorLevel: 'Lv.1',
  timeAgo: '刚刚',
  location: '校园',
  title: title ?? '帖子$id',
  content: '正文',
  likeCount: likes,
  isLiked: liked,
);

class FakeGateway implements CommunityInteractionGateway {
  int likeCalls = 0;
  int followCalls = 0;
  int bookmarkCalls = 0;
  int hideCalls = 0;
  final followingReads = <String>[];
  Future<CommunityPost?> Function(String)? onLike;
  Future<CommunityPost?> Function(String)? onFetchPost;
  Future<bool?> Function(String, bool)? onFollow;
  Future<bool?> Function(String)? onFetchFollowing;
  Future<bool?> Function(String, bool)? onBookmark;
  Future<bool?> Function(String, bool)? onHide;
  Map<String, List<String>> behaviorIds = {};

  @override
  Future<CommunityPost?> toggleLike(String id) async {
    likeCalls++;
    return onLike == null ? post(id, likes: 6, liked: true) : await onLike!(id);
  }

  @override
  Future<CommunityPost?> fetchPost(String id) async => onFetchPost?.call(id);
  @override
  Future<bool?> setFollowing(String id, bool value) async {
    followCalls++;
    return onFollow == null ? value : await onFollow!(id, value);
  }

  @override
  Future<bool?> fetchFollowing(String id) async {
    followingReads.add(id);
    return onFetchFollowing == null ? false : await onFetchFollowing!(id);
  }

  @override
  Future<bool?> setBookmarked(String id, bool value) async {
    bookmarkCalls++;
    return onBookmark == null ? value : await onBookmark!(id, value);
  }

  @override
  Future<bool?> setHidden(String id, bool value) async {
    hideCalls++;
    return onHide == null ? value : await onHide!(id, value);
  }

  @override
  Future<List<String>?> fetchBehaviorIds(String kind) async =>
      behaviorIds[kind];
}

class FakeApiClient extends ApiClient {
  dynamic response;
  bool fail = false;
  String? lastPath;
  @override
  Future<dynamic> post(
    String path, {
    dynamic body,
    RequestCancellation? cancellation,
    Duration? requestTimeout,
  }) async {
    lastPath = path;
    if (fail) throw Exception('offline');
    return response;
  }

  @override
  Future<dynamic> delete(
    String path, {
    dynamic body,
    RequestCancellation? cancellation,
  }) => post(path, body: body);
  @override
  Future<dynamic> get(
    String path, {
    Map<String, dynamic>? query,
    RequestCancellation? cancellation,
  }) => post(path);
}

class FailingPreferencesPlatform extends InMemorySharedPreferencesStore {
  FailingPreferencesPlatform() : super.empty();
  bool failWrites = false;
  Completer<void>? writeGate;
  int writes = 0;

  @override
  Future<bool> setValue(String valueType, String key, Object value) async {
    writes++;
    await writeGate?.future;
    if (failWrites) return false;
    return super.setValue(valueType, key, value);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));
  tearDown(UserSession.clearCurrentUser);

  test(
    'duplicate taps send one request and reconcile the authoritative count',
    () async {
      final gateway = FakeGateway();
      final response = Completer<CommunityPost?>();
      gateway.onLike = (_) => response.future;
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: gateway,
      );
      addTearDown(store.dispose);
      final original = post('a');
      final request = store.toggleLike(original);
      expect(store.apply(original).likeCount, 6);
      expect(store.likePending('a'), isTrue);
      expect(await store.toggleLike(original), isFalse);
      expect(gateway.likeCalls, 1);
      response.complete(post('a', likes: 27, liked: true));
      expect(await request, isTrue);
      expect(store.apply(original).likeCount, 27);
      expect(store.likePending('a'), isFalse);
    },
  );

  test('failed request rolls back its ID after a feed reorder and keeps new content', () async {
    final gateway = FakeGateway();
    final response = Completer<CommunityPost?>();
    gateway.onLike = (_) => response.future;
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: gateway,
    );
    addTearDown(store.dispose);
    final requestRevision = store.revision;
    final request = store.toggleLike(post('a'));
    final refreshed = [post('b', likes: 90), post('a', title: '刷新后的内容')];
    store.acceptPage(refreshed, requestRevision);
    expect(store.apply(refreshed[1]).likeCount, 6);
    response.complete(null);
    expect(await request, isFalse);
    expect(store.apply(refreshed[0]).likeCount, 90);
    expect(store.apply(refreshed[1]).likeCount, 5);
    expect(store.apply(refreshed[1]).title, '刷新后的内容');
  });

  test(
    'stale refresh cannot undo a completed like; later server snapshot can',
    () async {
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: FakeGateway(),
      );
      addTearDown(store.dispose);
      final startedAt = store.revision;
      await store.toggleLike(post('a'));
      store.acceptPage([post('a')], startedAt);
      expect(store.apply(post('a')).isLiked, isTrue);
      final later = store.revision;
      store.acceptPage([post('a', likes: 19)], later);
      expect(store.apply(post('a', likes: 19)).likeCount, 19);
      expect(store.apply(post('a', likes: 19)).isLiked, isFalse);
    },
  );

  test('unlike clamps zero and exceptions restore the previous like', () async {
    final gateway = FakeGateway();
    final response = Completer<CommunityPost?>();
    gateway.onLike = (_) => response.future;
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: gateway,
    );
    addTearDown(store.dispose);
    final original = post('a', liked: true, likes: 0);
    final request = store.toggleLike(original);
    expect(store.apply(original).likeCount, 0);
    response.completeError(Exception('offline'));
    expect(await request, isFalse);
    expect(store.apply(original).isLiked, isTrue);
  });

  test(
    'following locks and updates every post by that author, then rolls back',
    () async {
      final gateway = FakeGateway();
      final response = Completer<bool?>();
      gateway.onFollow = (_, _) => response.future;
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: gateway,
      );
      addTearDown(store.dispose);
      final request = store.toggleFollowing(post('a'));
      expect(store.apply(post('b')).isFollowed, isTrue);
      expect(store.apply(post('c', author: 'other')).isFollowed, isFalse);
      expect(await store.toggleFollowing(post('b')), isFalse);
      expect(gateway.followCalls, 1);
      response.complete(null);
      expect(await request, isFalse);
      expect(store.apply(post('a')).isFollowed, isFalse);
      expect(store.apply(post('b')).isFollowed, isFalse);
    },
  );

  test('follow query deduplicates authors, caches state and supports forced refresh', () async {
    final gateway = FakeGateway()..onFetchFollowing = (_) async => true;
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: gateway,
    );
    addTearDown(store.dispose);
    await store.refreshFollowing(['author', 'author', 'viewer', 'other']);
    expect(gateway.followingReads, ['author', 'other']);
    expect(store.apply(post('a')).isFollowed, isTrue);
    await store.refreshFollowing(['author', 'other']);
    expect(gateway.followingReads.length, 2);
    gateway.onFetchFollowing = (_) async => false;
    await store.refreshFollowing(['author'], force: true);
    expect(store.apply(post('a')).isFollowed, isFalse);
  });

  test(
    'follow queries limit concurrency to four across overlapping feed pages',
    () async {
      final gateway = FakeGateway();
      final firstBatch = Completer<void>();
      var active = 0;
      var maximum = 0;
      gateway.onFetchFollowing = (_) async {
        active++;
        if (active > maximum) maximum = active;
        await firstBatch.future;
        active--;
        return false;
      };
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: gateway,
      );
      addTearDown(store.dispose);
      final request = store.refreshFollowing(List.generate(10, (i) => '$i'));
      final nextPage = store.refreshFollowing(
        List.generate(10, (i) => '${i + 10}'),
      );
      await Future<void>.delayed(Duration.zero);
      expect(gateway.followingReads.length, 4);
      firstBatch.complete();
      await Future.wait([request, nextPage]);
      expect(maximum, 4);
      expect(gateway.followingReads.length, 20);
    },
  );

  test('stale author query cannot overwrite a newer follow action', () async {
    final gateway = FakeGateway();
    final query = Completer<bool?>();
    gateway.onFetchFollowing = (_) => query.future;
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: gateway,
    );
    addTearDown(store.dispose);
    final read = store.refreshFollowing(['author']);
    await store.toggleFollowing(post('a'));
    query.complete(false);
    await read;
    expect(store.apply(post('a')).isFollowed, isTrue);
  });

  test(
    'returning from detail synchronizes likes without replacing post content',
    () async {
      final gateway = FakeGateway()
        ..onFetchPost = (_) async => post('a', likes: 23, liked: true);
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: gateway,
      );
      addTearDown(store.dispose);
      final original = post('a', title: '保留内容');
      await store.refreshPost(original);
      expect(store.apply(original).likeCount, 23);
      expect(store.apply(original).title, '保留内容');
    },
  );

  test(
    'local bookmarks and hidden posts survive reload and stay account isolated',
    () async {
      final store = CommunityPreferencesStore(accountId: 'a');
      await Future.wait([
        store.setBookmarked('p1', true),
        store.setBookmarked('p2', true),
        store.setHidden('p3', true),
      ]);
      final reopened = CommunityPreferencesStore(accountId: 'a');
      await reopened.initialize();
      expect(reopened.isBookmarked('p1'), isTrue);
      expect(reopened.isBookmarked('p2'), isTrue);
      expect(reopened.isHidden('p3'), isTrue);
      final other = CommunityPreferencesStore(accountId: 'b');
      await other.initialize();
      expect(other.isBookmarked('p1'), isFalse);
      expect(other.isHidden('p3'), isFalse);
      await reopened.setHidden('p3', false);
      expect(reopened.isHidden('p3'), isFalse);
    },
  );

  test(
    'bookmark tap before initialization toggles the persisted state correctly',
    () async {
      final local = CommunityPreferencesStore(accountId: 'viewer');
      await local.setBookmarked('a', true);
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: FakeGateway(),
      );
      addTearDown(store.dispose);
      // 真实调用链（home/detail）都会先 initialize；此处对齐真实时序。
      await store.initialize();
      // P1 服务端化后 toggle 走服务端：本地 a=true → 目标取消 → 服务端确认 false，
      // 本地缓存同步为 false。
      expect(await store.toggleBookmark(post('a')), isTrue);
      expect(store.apply(post('a')).isBookmarked, isFalse);
    },
  );

  test(
    'server failure keeps the old bookmark state and does not claim success',
    () async {
      final local = CommunityPreferencesStore(accountId: 'viewer');
      await local.setBookmarked('a', true);
      final gateway = FakeGateway();
      // 服务端失败（null）：toggle 不得声称成功，本地保持原状态。
      gateway.onBookmark = (_, _) async => null;
      gateway.onHide = (_, _) async => null;
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: gateway,
        preferences: local,
      );
      addTearDown(store.dispose);
      expect(await store.toggleBookmark(post('a')), isFalse);
      expect(store.apply(post('a')).isBookmarked, isTrue);
      expect(await store.setHidden('a', true), isFalse);
      expect(store.isHidden('a'), isFalse);
    },
  );

  test(
    'failed platform writes restore the preferences cache for a reopened store',
    () async {
      final platform = FailingPreferencesPlatform();
      SharedPreferencesStorePlatform.instance = platform;
      SharedPreferences.resetStatic();
      final local = CommunityPreferencesStore(accountId: 'viewer');
      expect(await local.setBookmarked('existing', true), isTrue);
      platform.failWrites = true;
      expect(await local.setBookmarked('new', true), isFalse);
      expect(await local.setHidden('hidden', true), isFalse);
      final reopened = CommunityPreferencesStore(accountId: 'viewer');
      await reopened.initialize();
      expect(reopened.isBookmarked('existing'), isTrue);
      expect(reopened.isBookmarked('new'), isFalse);
      expect(reopened.isHidden('hidden'), isFalse);
    },
  );

  test('API retains authoritative interaction fields and distinguishes failure from false', () async {
    final client = FakeApiClient();
    final api = CommunityApi(client);
    client.response = {'postId': 12, 'liked': true, 'likeCount': 31};
    final liked = await api.togglePostLikeResultRaw('12');
    expect(liked?.isLiked, isTrue);
    expect(liked?.likeCount, 31);
    expect(client.lastPath, '/api/app/community/posts/12/like');
    client.response = {
      'data': {'following': false},
      'success': true,
    };
    expect(await api.setFollowResultRaw(userId: '12', follow: false), isFalse);
    client.response = {'isFollowing': true};
    expect(await api.fetchFollowingRaw('12'), isTrue);
    client.response = {'success': true};
    expect(await api.togglePostLikeResultRaw('12'), isNull);
    client.fail = true;
    expect(await api.setFollowResultRaw(userId: '12', follow: true), isNull);
  });

  testWidgets('home shows truthful local bookmark and hides a post with undo', (
    tester,
  ) async {
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: FakeGateway(),
    );
    addTearDown(store.dispose);
    tester.view.physicalSize = const Size(430, 1000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        home: CommunityHomePage(
          initialPosts: [post('a')],
          interactionStore: store,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('community-bookmark')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('community-bookmark')));
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.bookmark_rounded), findsWidgets);
    await tester.tap(find.byTooltip('帖子更多操作'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('在此设备隐藏此帖'));
    await tester.pumpAndSettle();
    expect(find.text('帖子a'), findsNothing);
    expect(store.isHidden('a'), isTrue);
    await tester.tap(find.text('撤销'));
    await tester.pumpAndSettle();
    expect(find.text('帖子a'), findsOneWidget);
    expect(store.isHidden('a'), isFalse);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('home restores a failed like and shows an error', (tester) async {
    final gateway = FakeGateway()..onLike = (_) async => null;
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: gateway,
    );
    addTearDown(store.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: CommunityHomePage(
          initialPosts: [post('a')],
          interactionStore: store,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('5'));
    await tester.pumpAndSettle();
    expect(find.text('5'), findsOneWidget);
    expect(find.text('6'), findsNothing);
    expect(find.text('点赞结果未确认，已恢复显示，请刷新后重试'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('detail and home share local bookmarks in both directions', (
    tester,
  ) async {
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: FakeGateway(),
    );
    addTearDown(store.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: CommunityHomePage(
          initialPosts: [post('a')],
          interactionStore: store,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('帖子a'));
    await tester.pumpAndSettle();
    expect(find.byType(PostDetailPage), findsOneWidget);
    expect(find.byKey(const ValueKey('detail-local-bookmark')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('detail-local-bookmark')));
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.bookmark_rounded), findsWidgets);
    await tester.tap(find.byIcon(Icons.arrow_back_ios_new_rounded));
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.bookmark_rounded), findsWidgets);

    await tester.tap(find.text('帖子a'));
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.bookmark_rounded), findsWidgets);
    await tester.tap(find.byKey(const ValueKey('detail-local-bookmark')));
    await tester.pumpAndSettle();
    expect(find.text('已取消收藏'), findsOneWidget);
    await tester.tap(find.byIcon(Icons.arrow_back_ios_new_rounded));
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.bookmark_border_rounded), findsWidgets);
    final reopened = CommunityPreferencesStore(accountId: 'viewer');
    await reopened.initialize();
    expect(reopened.isBookmarked('a'), isFalse);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('standalone detail loads the current account local bookmarks', (
    tester,
  ) async {
    final local = CommunityPreferencesStore(accountId: 'account-a');
    await local.setBookmarked('a', true);
    UserSession.currentUserId = 'account-a';
    await tester.pumpWidget(MaterialApp(home: PostDetailPage(post: post('a'))));
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.bookmark_rounded), findsWidgets);
    await tester.pumpWidget(const SizedBox.shrink());

    UserSession.currentUserId = 'account-b';
    await tester.pumpWidget(
      MaterialApp(
        home: PostDetailPage(post: post('a').copyWith(isBookmarked: true)),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('detail-local-bookmark')), findsOneWidget);
    expect(find.text('已存本机'), findsNothing);
    expect(local.isBookmarked('a'), isTrue);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'detail blocks duplicate bookmark taps until the write completes',
    (tester) async {
      final platform = FailingPreferencesPlatform();
      SharedPreferencesStorePlatform.instance = platform;
      SharedPreferences.resetStatic();
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: FakeGateway(),
      );
      addTearDown(store.dispose);
      await tester.pumpWidget(
        MaterialApp(
          home: PostDetailPage(post: post('a'), interactionStore: store),
        ),
      );
      await tester.pumpAndSettle();
      final gate = Completer<void>();
      platform.writeGate = gate;
      await tester.tap(find.byKey(const ValueKey('detail-local-bookmark')));
      await tester.tap(find.byKey(const ValueKey('detail-local-bookmark')));
      await tester.pump();
      expect(platform.writes, 1);
      expect(find.byType(CircularProgressIndicator), findsWidgets);
      expect(find.text('已收藏到本机，仅保存在此设备'), findsNothing);
      gate.complete();
      await tester.pumpAndSettle();
      expect(platform.writes, 1);
      expect(find.byIcon(Icons.bookmark_rounded), findsWidgets);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'detail reports a failed bookmark request and keeps the old state',
    (tester) async {
      // P1 服务端化后的失败语义：服务端请求失败（非本地写失败）才提示失败；
      // 本地缓存写失败不改变"服务端已收藏"的事实（换设备由 me/behaviors 恢复）。
      final gateway = FakeGateway();
      gateway.onBookmark = (_, _) async => null;
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: gateway,
      );
      addTearDown(store.dispose);
      await tester.pumpWidget(
        MaterialApp(
          home: PostDetailPage(post: post('a'), interactionStore: store),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('detail-local-bookmark')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('收藏操作失败，请重试'), findsOneWidget);
      expect(find.byKey(const ValueKey('detail-local-bookmark')), findsOneWidget);
      final reopened = CommunityPreferencesStore(accountId: 'viewer');
      await reopened.initialize();
      expect(reopened.isBookmarked('a'), isFalse);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'detail keeps server-confirmed bookmark even if local cache write fails',
    (tester) async {
      // 服务端成功 + 本地平台写失败：服务端权威，UI 提示已收藏；
      // 本地持久层失败只影响缓存，换设备登录后由服务端合并恢复。
      final platform = FailingPreferencesPlatform();
      SharedPreferencesStorePlatform.instance = platform;
      SharedPreferences.resetStatic();
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: FakeGateway(),
      );
      addTearDown(store.dispose);
      await tester.pumpWidget(
        MaterialApp(
          home: PostDetailPage(post: post('a'), interactionStore: store),
        ),
      );
      await tester.pumpAndSettle();
      platform.failWrites = true;
      await tester.tap(find.byKey(const ValueKey('detail-local-bookmark')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('已收藏'), findsOneWidget);
      await tester.pumpAndSettle();
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
}
