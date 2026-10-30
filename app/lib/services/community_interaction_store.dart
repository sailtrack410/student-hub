import 'package:flutter/foundation.dart';

import '../api/community_api.dart';
import 'session_store.dart';
import '../models/community_models.dart';
import 'community_preferences_store.dart';

abstract interface class CommunityInteractionGateway {
  Future<CommunityPost?> toggleLike(String postId);
  Future<CommunityPost?> fetchPost(String postId);
  Future<bool?> setFollowing(String authorId, bool following);
  Future<bool?> fetchFollowing(String authorId);
}

class ApiCommunityInteractionGateway implements CommunityInteractionGateway {
  @override
  Future<CommunityPost?> toggleLike(String postId) =>
      CommunityApi.togglePostLikeResult(postId: postId);

  @override
  Future<CommunityPost?> fetchPost(String postId) async =>
      (await CommunityApi.fetchPostDetail(postId))?.post;

  @override
  Future<bool?> setFollowing(String authorId, bool following) =>
      CommunityApi.setFollowResult(userId: authorId, follow: following);

  @override
  Future<bool?> fetchFollowing(String authorId) =>
      CommunityApi.fetchFollowing(authorId);
}

/// 同一登录会话的页面共用交互状态，最后一个页面离开后释放。
/// 凭证变化后新页面获得独立状态，旧请求不会写入新账号的 store。
abstract final class CommunityInteractionScope {
  static final _stores =
      <(String, String?), ({CommunityInteractionStore store, int users})>{};

  static CommunityInteractionStore acquire(String accountId) {
    final key = (accountId, SessionStore.token);
    final current = _stores[key];
    final store =
        current?.store ?? CommunityInteractionStore(accountId: accountId);
    _stores[key] = (store: store, users: (current?.users ?? 0) + 1);
    return store;
  }

  static void release(CommunityInteractionStore store) {
    final keys = _stores.keys
        .where((key) => identical(_stores[key]!.store, store))
        .toList();
    for (final key in keys) {
      final entry = _stores[key]!;
      if (entry.users == 1) {
        _stores.remove(key);
        store.dispose();
      } else {
        _stores[key] = (store: store, users: entry.users - 1);
      }
    }
  }
}

/// 页面交互按实体 ID 合并，异步响应不会依赖列表中的旧索引。
class CommunityInteractionStore extends ChangeNotifier {
  final String accountId;
  final CommunityInteractionGateway _gateway;
  final CommunityPreferencesStore _preferences;
  final _likes = <String, ({bool liked, int count, int revision})>{};
  final _following = <String, ({bool value, int revision})>{};
  final _pendingLikes = <String>{};
  final _pendingFollows = <String>{};
  final _pendingBookmarks = <String>{};
  final _pendingHidden = <String>{};
  final _followReads = <String>{};
  final _followReadTimes = <String, DateTime>{};
  Future<void> _followRefreshes = Future<void>.value();
  int _revision = 0;
  bool _disposed = false;

  CommunityInteractionStore({
    required this.accountId,
    CommunityInteractionGateway? gateway,
    CommunityPreferencesStore? preferences,
  }) : _gateway = gateway ?? ApiCommunityInteractionGateway(),
       _preferences =
           preferences ?? CommunityPreferencesStore(accountId: accountId);

  int get revision => _revision;
  bool likePending(String id) => _pendingLikes.contains(id);
  bool followPending(String id) => _pendingFollows.contains(id);
  bool bookmarkPending(String id) => _pendingBookmarks.contains(id);
  bool isHidden(String id) => _preferences.isHidden(id);
  bool isFollowing(String authorId, {bool fallback = false}) =>
      _following[authorId]?.value ?? fallback;

  void acceptAuthor(String authorId, bool following, int requestRevision) {
    if (followPending(authorId) ||
        (_following[authorId]?.revision ?? 0) > requestRevision) {
      return;
    }
    _following[authorId] = (value: following, revision: ++_revision);
    _followReadTimes[authorId] = DateTime.now();
    _changed();
  }

  void _changed() {
    if (!_disposed) notifyListeners();
  }

  Future<bool> initialize() async {
    final ready = await _preferences.initialize();
    if (ready) _changed();
    return ready;
  }

  CommunityPost apply(CommunityPost post) {
    final like = _likes[post.id];
    return post.copyWith(
      isLiked: like?.liked,
      likeCount: like?.count,
      isFollowed: _following[post.authorId]?.value,
      isBookmarked: _preferences.isBookmarked(post.id),
    );
  }

  /// 只有在请求发出前已完成的点赞操作，才能被这次服务端快照替代。
  void acceptPage(Iterable<CommunityPost> posts, int requestRevision) {
    for (final post in posts) {
      final like = _likes[post.id];
      if (like != null &&
          like.revision <= requestRevision &&
          !likePending(post.id)) {
        _likes.remove(post.id);
      }
    }
  }

  Future<bool> toggleLike(CommunityPost post) async {
    if (_disposed || !_pendingLikes.add(post.id)) return false;
    final before = apply(post);
    final next = !before.isLiked;
    _likes[post.id] = (
      liked: next,
      count: (before.likeCount + (next ? 1 : -1)).clamp(0, 1 << 31),
      revision: ++_revision,
    );
    _changed();
    CommunityPost? confirmed;
    try {
      // toggle 不是幂等接口：失败后恢复显示，由下一次查询核对，不自动重试。
      confirmed = await _gateway.toggleLike(post.id);
    } catch (_) {}
    _likes[post.id] = (
      liked: confirmed?.isLiked ?? before.isLiked,
      count: (confirmed?.likeCount ?? before.likeCount).clamp(0, 1 << 31),
      revision: ++_revision,
    );
    _pendingLikes.remove(post.id);
    _changed();
    return confirmed != null;
  }

  Future<bool> toggleFollowing(CommunityPost post) =>
      toggleAuthorFollowing(post.authorId, currentFollowing: post.isFollowed);

  Future<bool> toggleAuthorFollowing(
    String authorId, {
    required bool currentFollowing,
  }) async {
    if (_disposed ||
        authorId.isEmpty ||
        authorId == accountId ||
        !_pendingFollows.add(authorId)) {
      return false;
    }
    final before = isFollowing(authorId, fallback: currentFollowing);
    _following[authorId] = (value: !before, revision: ++_revision);
    _changed();
    bool? confirmed;
    try {
      confirmed = await _gateway.setFollowing(authorId, !before);
    } catch (_) {}
    _following[authorId] = (value: confirmed ?? before, revision: ++_revision);
    if (confirmed != null) _followReadTimes[authorId] = DateTime.now();
    _pendingFollows.remove(authorId);
    _changed();
    return confirmed != null;
  }

  /// 复用现有轻量名片接口；按作者去重、缓存一分钟，跨分页最多四个并发。
  Future<void> refreshFollowing(
    Iterable<String> authorIds, {
    bool force = false,
  }) {
    final ids = authorIds.toSet();
    return _followRefreshes = _followRefreshes.then(
      (_) => _refreshFollowing(ids, force: force),
    );
  }

  Future<void> _refreshFollowing(
    Iterable<String> authorIds, {
    required bool force,
  }) async {
    final now = DateTime.now();
    final queue = authorIds
        .where((id) => id.isNotEmpty && id != accountId)
        .toSet()
        .where((id) => !_followReads.contains(id))
        .where(
          (id) =>
              force ||
              _followReadTimes[id] == null ||
              now.difference(_followReadTimes[id]!) >=
                  const Duration(minutes: 1),
        )
        .toList();
    _followReads.addAll(queue);
    var cursor = 0;
    Future<void> worker() async {
      while (cursor < queue.length && !_disposed) {
        final id = queue[cursor++];
        final startedAt = _revision;
        try {
          final value = await _gateway.fetchFollowing(id);
          if (value != null &&
              !followPending(id) &&
              (_following[id]?.revision ?? 0) <= startedAt) {
            _following[id] = (value: value, revision: ++_revision);
            _followReadTimes[id] = DateTime.now();
            _changed();
          }
        } catch (_) {
          // 保留最后一次确认的关注态，查询失败不会变成“未关注”。
        } finally {
          _followReads.remove(id);
        }
      }
    }

    await Future.wait(List.generate(queue.length.clamp(0, 4), (_) => worker()));
    _followReads.removeAll(queue);
  }

  Future<void> refreshPost(CommunityPost post) async {
    final startedAt = _revision;
    try {
      final latest = await _gateway.fetchPost(post.id);
      if (latest != null &&
          !likePending(post.id) &&
          (_likes[post.id]?.revision ?? 0) <= startedAt) {
        _likes[post.id] = (
          liked: latest.isLiked,
          count: latest.likeCount.clamp(0, 1 << 31),
          revision: ++_revision,
        );
        _changed();
      }
    } catch (_) {}
    await refreshFollowing([post.authorId], force: true);
  }

  Future<bool> toggleBookmark(CommunityPost post) async {
    if (!_pendingBookmarks.add(post.id)) return false;
    _changed();
    final ready = await _preferences.initialize();
    final saved =
        ready &&
        await _preferences.setBookmarked(
          post.id,
          !_preferences.isBookmarked(post.id),
        );
    _pendingBookmarks.remove(post.id);
    _changed();
    return saved;
  }

  Future<bool> setHidden(String postId, bool hidden) async {
    if (!_pendingHidden.add(postId)) return false;
    final saved = await _preferences.setHidden(postId, hidden);
    _pendingHidden.remove(postId);
    if (saved) _changed();
    return saved;
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
