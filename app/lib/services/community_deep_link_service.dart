import 'dart:async';

import 'package:app_links/app_links.dart';
import 'package:flutter/material.dart';

import '../api/community_api.dart';
import '../main.dart' show appNavigatorKey;
import '../models/user_session.dart';
import '../pages/community/post_detail_page.dart';
import 'community_interaction_store.dart';

/// D-Share 深链路由：订阅 App Links / 自定义 scheme 链接，
/// 解析出帖子标识后拉详情并 push（全局 navigatorKey，不依赖页面上下文）。
///
/// 支持形态：
/// - https://sailtrack.cn/c/{slug}（App Links，已装 App 系统直跳）
/// - studenthub://p/{slug}（自定义 scheme 兜底）
/// - 兼容旧纯数字 id
class CommunityDeepLinkService {
  CommunityDeepLinkService._();

  static final AppLinks _appLinks = AppLinks();
  static StreamSubscription<Uri>? _subscription;
  static bool _initialized = false;

  /// 登录后调用一次；重复调用幂等。
  static void ensureInitialized() {
    if (_initialized) return;
    _initialized = true;
    _subscription = _appLinks.uriLinkStream.listen(
      _handleUri,
      onError: (_) {},
    );
  }

  static void dispose() {
    _subscription?.cancel();
    _subscription = null;
    _initialized = false;
  }

  /// 测试/内部入口：直接处理一个 URI
  static Future<void> handleUriForTest(Uri uri) => _handleUri(uri);

  /// 从 URI 提取帖子标识；非帖子链接返回 null
  static String? postIdFromUri(Uri uri) {
    // studenthub://p/{slug}
    if (uri.scheme == 'studenthub') {
      return uri.host == 'p' && uri.pathSegments.isNotEmpty
          ? uri.pathSegments.first
          : null;
    }
    // https://sailtrack.cn/c/{slug}
    if ((uri.scheme == 'https' || uri.scheme == 'http') &&
        uri.host == 'sailtrack.cn' &&
        uri.pathSegments.length >= 2 &&
        uri.pathSegments.first == 'c') {
      return uri.pathSegments[1];
    }
    return null;
  }

  static Future<void> _handleUri(Uri uri) async {
    final navigator = appNavigatorKey.currentState;
    if (navigator == null) return;
    final slugOrId = postIdFromUri(uri);
    if (slugOrId == null) return;
    // 未登录时静默忽略（登录态建立后 ensureInitialized 重新接管新链接）
    if (UserSession.currentUserId == null) return;

    final post = await CommunityApi.fetchPostByShareSlug(slugOrId);
    if (post == null) return;
    if (!navigator.mounted) return;
    final store = CommunityInteractionScope.acquire(
      UserSession.currentUserId ?? 'guest',
    );
    unawaited(
      navigator.push(
        MaterialPageRoute(
          builder: (_) => PostDetailPage(post: post, interactionStore: store),
        ),
      ),
    );
  }
}
