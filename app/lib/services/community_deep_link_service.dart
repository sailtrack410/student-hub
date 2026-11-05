import 'dart:async';

import 'package:app_links/app_links.dart';
import 'package:flutter/material.dart';

import '../api/community_api.dart';
import '../main.dart' show appNavigatorKey;
import '../models/community_models.dart';
import '../models/user_session.dart';
import '../pages/community/post_detail_page.dart';
import 'community_interaction_store.dart';
import 'session_store.dart';

/// 接收链接和执行导航分开，冷启动、登录完成后消费最近的有效目标。
class CommunityDeepLinkRouter {
  CommunityDeepLinkRouter({
    required this.fetchPost,
    required this.openPost,
    required this.accountId,
    required this.sessionRevision,
    required this.isReady,
    this.onUnavailable,
  });

  final Future<CommunityPost?> Function(String) fetchPost;
  final void Function(CommunityPost) openPost;
  final String? Function() accountId;
  final int Function() sessionRevision;
  final bool Function() isReady;
  final void Function()? onUnavailable;
  String? _pending;
  int? _pendingRevision;
  String? _opening;
  int? _openingRevision;
  String? _lastOpened;
  DateTime? _lastOpenedAt;
  int? _lastOpenedRevision;
  bool _draining = false;
  int _generation = 0;

  Future<void> receive(Uri uri) async {
    final target = CommunityDeepLinkService.postIdFromUri(uri);
    if (target == null) return;
    final revision = sessionRevision();
    if ((target == _pending &&
            (_pendingRevision == null || _pendingRevision == revision)) ||
        (target == _opening && _openingRevision == revision)) {
      return;
    }
    if (target == _lastOpened &&
        _lastOpenedRevision == revision &&
        _lastOpenedAt != null &&
        DateTime.now().difference(_lastOpenedAt!) <
            const Duration(seconds: 2)) {
      return;
    }
    _pending = target;
    _pendingRevision = accountId() == null ? null : revision;
    await resume();
  }

  Future<void> resume() async {
    if (_draining || _pending == null) return;
    if (_pendingRevision != null && _pendingRevision != sessionRevision()) {
      _pending = null;
      _pendingRevision = null;
      return;
    }
    if (accountId() == null || !isReady()) return;
    _draining = true;
    final target = _pending!;
    _pending = null;
    _pendingRevision = null;
    _opening = target;
    _openingRevision = sessionRevision();
    final account = accountId();
    final revision = sessionRevision();
    final generation = _generation;
    bool isCurrent() =>
        generation == _generation &&
        account == accountId() &&
        revision == sessionRevision();
    try {
      final post = await fetchPost(target);
      if (!isCurrent()) return;
      if (!isReady()) {
        if (_pending == null) {
          _pending = target;
          _pendingRevision = revision;
        }
        return;
      }
      // 新链接到达时，旧请求只收尾，不覆盖用户最新选择。
      if (_pending != null) return;
      if (post == null) {
        onUnavailable?.call();
        return;
      }
      _lastOpened = target;
      _lastOpenedRevision = revision;
      _lastOpenedAt = DateTime.now();
      openPost(post);
    } catch (_) {
      if (isCurrent() && isReady()) onUnavailable?.call();
    } finally {
      _opening = null;
      _openingRevision = null;
      _draining = false;
      if (_pending != null && isReady() && accountId() != null) {
        unawaited(resume());
      }
    }
  }

  void dispose() {
    _generation++;
    _pending = null;
    _pendingRevision = null;
    _lastOpenedRevision = null;
    _lastOpened = null;
    _lastOpenedAt = null;
  }
}

class CommunityDeepLinkService {
  CommunityDeepLinkService._();

  static StreamSubscription<Uri>? _subscription;
  static bool _initialized = false;
  static final _router = CommunityDeepLinkRouter(
    fetchPost: CommunityApi.fetchPostByShareSlug,
    accountId: () => UserSession.currentUserId,
    sessionRevision: () => SessionStore.revision,
    isReady: () => appNavigatorKey.currentState?.mounted == true,
    openPost: (post) {
      final navigator = appNavigatorKey.currentState!;
      final store = CommunityInteractionScope.acquire(
        UserSession.currentUserId!,
      );
      unawaited(
        navigator
            .push(
              MaterialPageRoute<void>(
                builder: (_) =>
                    PostDetailPage(post: post, interactionStore: store),
              ),
            )
            .whenComplete(() => CommunityInteractionScope.release(store)),
      );
    },
    onUnavailable: () {
      final context = appNavigatorKey.currentContext;
      if (context == null) return;
      ScaffoldMessenger.maybeOf(context)
          ?.showSnackBar(const SnackBar(content: Text('帖子暂时无法打开，请稍后重试')));
    },
  );

  /// 在登录前接收系统链接；导航与身份就绪后再处理。
  static void ensureInitialized() {
    if (!_initialized) {
      _initialized = true;
      final links = AppLinks();
      _subscription = links.uriLinkStream.listen(
        (uri) => unawaited(_router.receive(uri)),
        onError: (_) {},
      );
      unawaited(() async {
        try {
          final initial = await links.getInitialLink();
          if (initial != null && _initialized) await _router.receive(initial);
        } catch (_) {
          // 无链接或平台插件不可用时，保持正常启动流程。
        }
      }());
    }
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => unawaited(_router.resume()),
    );
  }

  static void dispose() {
    unawaited(_subscription?.cancel());
    _subscription = null;
    _initialized = false;
    _router.dispose();
  }

  static Future<void> handleUriForTest(Uri uri) => _router.receive(uri);

  static String? postIdFromUri(Uri uri) {
    String? target;
    if (uri.scheme == 'studenthub' &&
        !uri.hasPort &&
        uri.userInfo.isEmpty &&
        uri.host == 'p' &&
        uri.pathSegments.length == 1) {
      target = uri.pathSegments.single;
    } else if (uri.scheme == 'https' &&
        uri.host == 'sailtrack.cn' &&
        uri.pathSegments.length == 2 &&
        uri.pathSegments.first == 'c' &&
        !uri.hasPort &&
        uri.userInfo.isEmpty) {
      target = uri.pathSegments[1];
    }
    return target != null && RegExp(r'^[A-Za-z0-9]{1,19}$').hasMatch(target)
        ? target
        : null;
  }
}
