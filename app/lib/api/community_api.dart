import 'dart:math';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/community_models.dart';
import 'location_api.dart';
import 'api_client.dart';

/// 校园社区与互动 API 接入服务
class CommunityApi {
  final ApiClient apiClient;

  CommunityApi(this.apiClient);

  static final CommunityApi _instance = CommunityApi(ApiClient());

  static List<CommunitySection>? _sectionsCache;

  /// 分区中文别名 → 后端分区名（画布口径与后端数据口径的桥接）
  static const Map<String, String> _sectionAliases = {
    '校园日常': '校友杂谈',
    '二手闲置': '二手交易',
    '学习互助': '学习咨询',
    '学习交流': '学习咨询',
    '失物招领': '失物招领',
    '失物寻物': '失物招领',
  };

  /// 获取社区分区（进程内缓存）
  static Future<List<CommunitySection>> fetchSections() async {
    if (_sectionsCache != null) return _sectionsCache!;
    final list = await _instance.fetchSectionsRaw();
    if (list != null && list.isNotEmpty) {
      _sectionsCache = list;
    }
    return _sectionsCache ?? const [];
  }

  static int? sectionIdForName(String name) {
    final sections = _sectionsCache ?? const <CommunitySection>[];
    final target = _sectionAliases[name] ?? name;
    for (final s in sections) {
      if (s.name == target || s.code == target) return int.tryParse(s.id);
    }
    return null;
  }

  /// 分页查询帖子（失败返回 null；空列表是合法结果）
  static Future<CommunityPostPage?> fetchPostPage({
    String? category,
    int? sectionId,
    String? keyword,
    String sort = 'LATEST',
    int pageNum = 1,
    int pageSize = 10,
  }) async {
    if (sectionId == null &&
        category != null &&
        category != '推荐' &&
        category != '最新') {
      await fetchSections();
      sectionId = sectionIdForName(category);
    }
    final raw = await _instance.fetchPostsRaw(
      sectionId: sectionId?.toString(),
      keyword: keyword,
      pageNum: pageNum,
      pageSize: pageSize,
      sort: sort,
    );
    if (raw == null || raw['records'] is! List) return null;
    final posts = (raw['records'] as List)
        .whereType<Map<String, dynamic>>()
        .map(mapPost)
        .toList();
    final total = int.tryParse(raw['total']?.toString() ?? '') ?? posts.length;
    return CommunityPostPage(posts: posts, total: total);
  }

  /// 兼容首页：只返回真实接口数据；失败或空数据返回空列表，由页面展示空态/错误态
  static Future<List<CommunityPost>> fetchPosts({String? category}) async {
    final page = await fetchPostPage(
      category: category,
      sort: category == '推荐' ? 'HOT' : 'LATEST',
      pageSize: 20,
    );
    return page?.posts ?? const <CommunityPost>[];
  }

  /// 分页查询社区混合信息流（自然帖子 + 官方直投广告）
  ///
  /// 无关键词时社区首页走这里；接口失败返回 null，由页面展示错误态与重试。
  /// [category] 为「推荐/最新」或分区名称；[sectionId] 优先。
  static Future<CommunityFeedPage?> fetchFeedPage({
    String? category,
    int? sectionId,
    String sort = 'LATEST',
    int pageNum = 1,
    int pageSize = 10,
  }) async {
    if (sectionId == null &&
        category != null &&
        category != '推荐' &&
        category != '最新') {
      await fetchSections();
      sectionId = sectionIdForName(category);
    }
    final sessionId = await adSessionId();
    final raw = await _instance.fetchFeedPageRaw(
      sectionId: sectionId?.toString(),
      sort: sort,
      pageNum: pageNum,
      pageSize: pageSize,
      sessionId: sessionId,
    );
    if (raw == null) return null;
    return parseFeedPage(raw);
  }

  /// 解析混合信息流响应（供接口层与单测复用）
  static CommunityFeedPage parseFeedPage(Map<String, dynamic> raw) {
    return CommunityFeedPage.fromJson(raw, postParser: mapPost);
  }

  // ---------------------------------------------------------------------------
  // 官方直投广告事件链路：曝光 → 点击 → 落地加载完成 → 停留/跳出
  // 事件上报失败不应阻断浏览，调用方以本地广告数据兜底。
  // ---------------------------------------------------------------------------

  static const String _adSessionPrefKey = 'community_ad_session_id';
  static String? _adSessionId;

  /// 单次 App 会话 ID：用于后端频控（单会话曝光上限）与事件归因
  static Future<String> adSessionId() async {
    if (_adSessionId != null && _adSessionId!.isNotEmpty) return _adSessionId!;
    try {
      final prefs = await SharedPreferences.getInstance();
      final saved = prefs.getString(_adSessionPrefKey);
      if (saved != null && saved.isNotEmpty) {
        _adSessionId = saved;
        return saved;
      }
      final generated = _newAdSessionId();
      await prefs.setString(_adSessionPrefKey, generated);
      _adSessionId = generated;
      return generated;
    } catch (_) {
      _adSessionId ??= _newAdSessionId();
      return _adSessionId!;
    }
  }

  static String _newAdSessionId() {
    final random = Random();
    final suffix = random.nextInt(0x7FFFFFFF).toRadixString(16);
    return 'app_${DateTime.now().millisecondsSinceEpoch}_$suffix';
  }

  /// 上报广告曝光；返回后端确认的 clickId（失败返回 null，可由调用方自增兜底）
  static Future<String?> reportAdImpression({
    required String adId,
    required String clickId,
  }) async {
    if (adId.isEmpty) return null;
    try {
      final res = await _instance.apiClient.post(
        '/api/app/community/direct-ads/$adId/impression',
        body: {
          if (clickId.isNotEmpty) 'clickId': clickId,
          'sessionId': await adSessionId(),
        },
      );
      if (res is Map<String, dynamic>) {
        final returned = res['clickId']?.toString();
        if (returned != null && returned.isNotEmpty) return returned;
      }
      return clickId.isEmpty ? null : clickId;
    } catch (_) {
      return null;
    }
  }

  /// 上报广告点击；返回跳转配置（失败返回 null，调用方退回信息流里的广告数据）
  static Future<AdClickResult?> reportAdClick({
    required String adId,
    required String clickId,
  }) async {
    if (adId.isEmpty) return null;
    try {
      final res = await _instance.apiClient.post(
        '/api/app/community/direct-ads/$adId/click',
        body: {
          if (clickId.isNotEmpty) 'clickId': clickId,
          'sessionId': await adSessionId(),
        },
      );
      if (res is Map<String, dynamic>) return AdClickResult.fromJson(res);
    } catch (_) {}
    return null;
  }

  /// 落地页加载完成（仅 IN_APP_WEBVIEW / APP_PAGE 可测链路调用）
  static Future<bool> reportLandingLoaded({required String clickId}) async {
    if (clickId.isEmpty) return false;
    try {
      await _instance.apiClient.post(
        '/api/app/community/direct-ads/landing/loaded',
        body: {'clickId': clickId, 'sessionId': await adSessionId()},
      );
      return true;
    } catch (_) {
      return false;
    }
  }

  /// 落地页退出（dwellMs 从进入 WebView 页面开始计时）
  static Future<bool> reportLandingExit({
    required String clickId,
    required int dwellMs,
  }) async {
    if (clickId.isEmpty) return false;
    try {
      await _instance.apiClient.post(
        '/api/app/community/direct-ads/landing/exit',
        body: {
          'clickId': clickId,
          'dwellMs': dwellMs,
          'sessionId': await adSessionId(),
        },
      );
      return true;
    } catch (_) {
      return false;
    }
  }

  /// 查询帖子详情与评论（失败返回 null）
  static Future<CommunityPostDetail?> fetchPostDetail(String postId) async {
    final raw = await _instance.fetchPostDetailRaw(postId);
    if (raw == null) return null;
    return _parseDetail(raw);
  }

  /// 区分「404/已删除或隐藏」与网络失败，供内容不可见页使用
  static Future<PostDetailFetchResult> fetchPostDetailResult(
    String postId,
  ) async {
    try {
      final raw = await _instance.fetchPostDetailRawOrThrow(postId);
      if (raw == null) {
        return const PostDetailFetchResult(notFound: true);
      }
      return PostDetailFetchResult(detail: _parseDetail(raw));
    } on ApiException catch (e) {
      final notFound = e.code == 'COMMON_404' || e.code == 'NOT_FOUND';
      return PostDetailFetchResult(notFound: notFound);
    } catch (_) {
      return const PostDetailFetchResult();
    }
  }

  static CommunityPostDetail? _parseDetail(Map<String, dynamic> raw) {
    final postMap = raw['post'];
    if (postMap is! Map<String, dynamic>) return null;
    final post = mapPost(postMap);
    final rawComments = (raw['comments'] as List?) ?? const [];
    final replies = <String, List<Map<String, dynamic>>>{};
    final roots = <Map<String, dynamic>>[];
    for (final item in rawComments.whereType<Map<String, dynamic>>()) {
      final parentId = item['parentCommentId']?.toString();
      if (parentId == null || parentId.isEmpty) {
        roots.add(item);
      } else {
        replies.putIfAbsent(parentId, () => []).add(item);
      }
    }
    final comments = roots.map((m) {
      final id = m['commentId']?.toString() ?? '';
      final replyList = replies[id] ?? const <Map<String, dynamic>>[];
      return _mapComment(m, replies: replyList.map(_mapComment).toList());
    }).toList();
    return CommunityPostDetail(post: post, comments: comments);
  }

  static CommunityComment _mapComment(
    Map<String, dynamic> m, {
    List<CommunityComment> replies = const [],
  }) {
    // 后端 assembleComments 下发真实等级（等级=f(总经验)）；缺省留空走「社区成员」兜底
    final rawLevel = m['authorLevel'];
    final level = rawLevel == null ? '' : 'Lv.${rawLevel}';
    final style = communityLevelStyle(level.isEmpty ? 'Lv.1' : level);
    return CommunityComment(
      id:
          m['commentId']?.toString() ??
          'comment_${DateTime.now().millisecondsSinceEpoch}',
      authorName: m['authorName']?.toString() ?? '同学',
      avatarPath: _absoluteUrl(m['avatarUrl']?.toString()),
      authorLevel: level,
      authorOfficial: m['authorOfficial'] == true,
      authorOfficialTitle: m['authorOfficialTitle']?.toString(),
      levelBgColor: style.bgColor,
      levelTextColor: style.textColor,
      isHot: m['pinned'] == true,
      content: m['content']?.toString() ?? '',
      timeAgo: _relativeTime(m['createdAt']?.toString()),
      likeCount: (m['likeCount'] as num?)?.toInt() ?? 0,
      isLiked: m['liked'] == true,
      replies: replies,
    );
  }

  /// 查询私信消息
  static Future<List<DirectMessage>> fetchMessages({
    required String withUserId,
  }) async {
    final raw = await _instance.fetchMessagesRaw(withUserId);
    if (raw == null) return const [];
    return raw.map((m) {
      final createdAt = m['createdAt']?.toString();
      final images = ((m['imageUrls'] as List?) ?? const [])
          .map((e) => e.toString())
          .where((e) => e.isNotEmpty)
          .map(
            (e) => e.startsWith('http') ? e : '${ApiClient.defaultBaseUrl}$e',
          )
          .toList();
      return DirectMessage(
        id: m['messageId']?.toString() ?? '',
        senderId: m['senderId']?.toString() ?? '',
        receiverId: m['receiverId']?.toString() ?? '',
        content: m['content']?.toString() ?? '',
        imageUrls: images,
        createdAt: createdAt == null ? null : DateTime.tryParse(createdAt),
        deliveredAt: DateTime.tryParse(m['deliveredAt']?.toString() ?? ''),
        readAt: DateTime.tryParse(m['readAt']?.toString() ?? ''),
        status: m['status']?.toString() ?? 'SENT',
      );
    }).toList();
  }

  /// 发送私信
  static Future<DirectMessage?> sendDirectMessage({
    required String receiverId,
    String content = '',
    List<String> imageFileIds = const [],
  }) async {
    final raw = await _instance.sendMessageRaw(
      receiverId,
      content,
      imageFileIds: imageFileIds,
    );
    if (raw == null) return null;
    return DirectMessage(
      id: raw['messageId']?.toString() ?? '',
      senderId: raw['senderId']?.toString() ?? '',
      receiverId: raw['receiverId']?.toString() ?? '',
      content: raw['content']?.toString() ?? '',
      imageUrls: ((raw['imageUrls'] as List?) ?? const [])
          .map((e) => e.toString())
          .map(
            (e) => e.startsWith('http') ? e : '${ApiClient.defaultBaseUrl}$e',
          )
          .toList(),
      createdAt: DateTime.tryParse(raw['createdAt']?.toString() ?? ''),
      deliveredAt: DateTime.tryParse(raw['deliveredAt']?.toString() ?? ''),
      readAt: DateTime.tryParse(raw['readAt']?.toString() ?? ''),
      status: raw['status']?.toString() ?? 'SENT',
    );
  }

  /// 批量标记送达
  static Future<void> markMessagesDelivered(List<String> messageIds) async {
    if (messageIds.isEmpty) return;
    try {
      await _instance.apiClient.post(
        '/api/app/community/messages/delivered',
        body: {'messageIds': messageIds},
      );
    } catch (_) {}
  }

  /// 标记指定消息已读；ids 为空时标记整个会话
  static Future<void> markMessagesRead({
    required String withUserId,
    List<String> messageIds = const [],
  }) async {
    try {
      await _instance.apiClient.post(
        '/api/app/community/messages/read?withUserId=$withUserId',
        body: messageIds.isEmpty ? {} : {'messageIds': messageIds},
      );
    } catch (_) {}
  }

  /// 批量查询在线状态
  static Future<Map<String, bool>> fetchPresence(List<String> userIds) async {
    if (userIds.isEmpty) return const {};
    try {
      final res = await _instance.apiClient.get(
        '/api/app/community/presence?userIds=${userIds.join(",")}',
      );
      final list = res is List
          ? res
          : (res is Map && res['data'] is List
                ? res['data'] as List
                : const []);
      return {
        for (final item in list.whereType<Map>())
          item['userId'].toString(): item['online'] == true,
      };
    } catch (_) {
      return const {};
    }
  }

  /// 私信会话列表
  static Future<List<DirectConversation>> fetchConversations() async {
    return await _instance.fetchConversationsRaw() ?? const [];
  }

  /// 帖子 JSON → 模型（/posts 与 /feed 共用，供解析单测复用）
  static CommunityPost mapPost(Map<String, dynamic> m) {
    final levelValue = m['authorLevel'];
    final levelNum = levelValue is num
        ? levelValue.toInt()
        : int.tryParse(levelValue?.toString() ?? '') ?? 3;
    final level = 'Lv.$levelNum';
    final style = communityLevelStyle(level);
    final rawImages = (m['imageUrls'] as List?) ?? const [];
    final images = rawImages
        .map((e) => e.toString())
        .where((e) => e.isNotEmpty)
        .map((url) {
          if (url.startsWith('http://') || url.startsWith('https://')) {
            return url;
          }
          return '${ApiClient.defaultBaseUrl}$url';
        })
        .toList();
    final topics = ((m['topics'] as List?) ?? const [])
        .map((e) => e.toString())
        .toList();
    final createdAt = m['createdAt']?.toString();
    final sectionName = m['sectionName']?.toString() ?? '';
    final rawBlocks = (m['contentBlocks'] as List?) ?? const [];
    final blocks = rawBlocks
        .whereType<Map<String, dynamic>>()
        .map(
          (b) => CommunityContentBlock(
            type: b['type']?.toString() ?? 'TEXT',
            text: b['text']?.toString(),
            fileId: b['fileId']?.toString(),
            url: _absoluteUrl(b['url']?.toString()),
            thumbnailUrl: _absoluteUrl(b['thumbnailUrl']?.toString()),
          ),
        )
        .toList();
    final blockImages = blocks
        .where((b) => b.type == 'IMAGE' && (b.url ?? '').isNotEmpty)
        .map((b) => b.url!)
        .toList();
    final locationName = m['locationName']?.toString() ?? '';
    return CommunityPost(
      id:
          m['postId']?.toString() ??
          m['id']?.toString() ??
          'post_${DateTime.now().millisecondsSinceEpoch}',
      authorId: m['authorId']?.toString() ?? '',
      authorName: m['authorName']?.toString() ?? '同学',
      authorAvatar: _absoluteUrl(m['avatarUrl']?.toString()),
      authorLevel: level,
      authorOfficial: m['authorOfficial'] == true,
      authorOfficialTitle: m['authorOfficialTitle']?.toString(),
      levelBgColor: style.bgColor,
      levelTextColor: style.textColor,
      badgeAsset: communityBadgeAsset(m['authorBadgeCode']?.toString()),
      authorDepartment: sectionName.isNotEmpty ? sectionName : '绵阳城市学院',
      timeAgo: _relativeTime(createdAt),
      location: locationName.isNotEmpty ? locationName : '绵阳城市学院 (游仙校区)',
      title: m['title']?.toString() ?? '',
      content: m['content']?.toString() ?? '',
      images: images.isNotEmpty ? images : blockImages,
      topics: topics,
      partition: sectionName,
      postType: m['postType']?.toString() ?? 'DYNAMIC',
      status: m['status']?.toString() ?? 'ENABLED',
      hiddenReason: m['hiddenReason']?.toString(),
      contentBlocks: blocks,
      likeCount: (m['likeCount'] as num?)?.toInt() ?? 0,
      commentCount: (m['commentCount'] as num?)?.toInt() ?? 0,
      shareCount: (m['shareCount'] as num?)?.toInt() ?? 0,
      viewsCount: (m['viewCount'] as num?)?.toInt() ?? 0,
      isLiked: m['liked'] == true,
      isFollowed: m['following'] == true || m['isFollowing'] == true,
      isBookmarked: m['bookmarked'] == true,
    );
  }

  static String? _absoluteUrl(String? url) {
    if (url == null || url.isEmpty) return null;
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    return '${ApiClient.defaultBaseUrl}$url';
  }

  static String _relativeTime(String? iso) {
    if (iso == null || iso.isEmpty) return '刚刚';
    try {
      final dt = DateTime.parse(iso);
      final diff = DateTime.now().difference(dt);
      if (diff.inMinutes < 1) return '刚刚';
      if (diff.inMinutes < 60) return '${diff.inMinutes}分钟前';
      if (diff.inHours < 24) return '${diff.inHours}小时前';
      if (diff.inDays < 7) return '${diff.inDays}天前';
      return '${dt.month}月${dt.day}日';
    } catch (_) {
      return '刚刚';
    }
  }

  /// 关注 / 取关作者
  static Future<bool> toggleFollow({
    required String userId,
    required bool follow,
  }) async => (await setFollowResult(userId: userId, follow: follow)) != null;

  /// null 表示请求失败；false 是服务端确认的未关注状态。
  static Future<bool?> setFollowResult({
    required String userId,
    required bool follow,
  }) => _instance.setFollowResultRaw(userId: userId, follow: follow);

  Future<bool?> setFollowResultRaw({
    required String userId,
    required bool follow,
  }) async {
    if (userId.isEmpty) return null;
    try {
      final res = follow
          ? await apiClient.post('/api/app/users/$userId/follow', body: {})
          : await apiClient.delete('/api/app/users/$userId/follow');
      final data = _responseMap(res);
      return data?['following'] is bool ? data!['following'] as bool : null;
    } catch (_) {
      return null;
    }
  }

  static Future<bool?> fetchFollowing(String userId) =>
      _instance.fetchFollowingRaw(userId);

  Future<bool?> fetchFollowingRaw(String userId) async {
    if (userId.isEmpty) return null;
    try {
      final data = _responseMap(
        await apiClient.get('/api/app/users/$userId/card'),
      );
      return data?['isFollowing'] is bool ? data!['isFollowing'] as bool : null;
    } catch (_) {
      return null;
    }
  }

  /// 收藏/取消收藏（P1 服务端化）。null=请求失败；bool 为服务端确认的 toggle 后状态。
  static Future<bool?> setBookmarkResult({
    required String postId,
    required bool bookmarked,
  }) => _instance.setBookmarkResultRaw(postId: postId, bookmarked: bookmarked);

  Future<bool?> setBookmarkResultRaw({
    required String postId,
    required bool bookmarked,
  }) async {
    if (postId.isEmpty) return null;
    try {
      // 后端 Result<Boolean>：apiClient 成功时已剥壳，直接拿到裸 bool。
      final res = await apiClient.post(
        '/api/app/community/posts/$postId/bookmark',
        body: {},
      );
      return res is bool ? res : null;
    } catch (_) {
      return null;
    }
  }

  /// 隐藏/取消隐藏（P1 服务端化，仅过滤本人信息流）。null=失败；bool 为 toggle 后状态。
  static Future<bool?> setHiddenResult({
    required String postId,
    required bool hidden,
  }) => _instance.setHiddenResultRaw(postId: postId, hidden: hidden);

  Future<bool?> setHiddenResultRaw({
    required String postId,
    required bool hidden,
  }) async {
    if (postId.isEmpty) return null;
    try {
      final res = await apiClient.post(
        '/api/app/community/posts/$postId/hide',
        body: {},
      );
      return res is bool ? res : null;
    } catch (_) {
      return null;
    }
  }

  /// 查询我的收藏/隐藏帖子 id 列表（登录后合并本机存量用）。
  /// 后端 Result<List<Long>>：apiClient 解包后即 List。
  static Future<List<String>?> fetchBehaviorPostIds({required String kind}) =>
      _instance.fetchBehaviorPostIdsRaw(kind: kind);

  Future<List<String>?> fetchBehaviorPostIdsRaw({required String kind}) async {
    try {
      final res = await apiClient.get(
        '/api/app/community/me/behaviors?kind=$kind',
      );
      if (res is List) return res.map((e) => e.toString()).toList();
      return null;
    } catch (_) {
      return null;
    }
  }

  static Map<String, dynamic>? _responseMap(dynamic response) {
    if (response is! Map<String, dynamic> || response['success'] == false) {
      return null;
    }
    return response['data'] is Map<String, dynamic>
        ? response['data'] as Map<String, dynamic>
        : response;
  }

  /// 静态快捷方法：发布帖子（动态/文章）
  static Future<CommunityPost?> createPost({
    required String title,
    required String content,
    String? category,
    int? sectionId,
    String postType = 'DYNAMIC',
    List<Map<String, dynamic>> contentBlocks = const [],
    List<String> imageFileIds = const [],
    List<String> tags = const [],
    PostLocation? location,
  }) async {
    try {
      await fetchSections();
      final resolvedSectionId =
          sectionId ?? sectionIdForName(category ?? '校园日常');
      final raw = await _instance.createPostRaw(
        sectionId: resolvedSectionId?.toString(),
        title: title,
        content: content,
        postType: postType,
        contentBlocks: contentBlocks,
        imageFileIds: imageFileIds,
        topics: tags,
        location: location,
      );
      if (raw != null && raw['postId'] != null) {
        return mapPost(raw);
      }
    } catch (_) {}
    return null;
  }

  /// 静态快捷方法：发表评论
  ///
  /// 失败返回 null：调用方必须提示失败并保留输入，不得本地捏造评论伪装成功
  /// （刷新即消失的「假发表成功」）。
  static Future<CommunityComment?> createComment({
    required String postId,
    required String content,
    String? parentCommentId,
  }) async {
    Map<String, dynamic>? raw;
    try {
      raw = await _instance.createCommentRaw(
        postId,
        content: content,
        parentCommentId: parentCommentId,
      );
    } catch (_) {}
    if (raw == null || raw['commentId'] == null) return null;
    return _mapComment(raw);
  }

  /// 静态快捷方法：删除作者自己的帖子
  static Future<bool> deletePost(String postId) async {
    try {
      final res = await _instance.apiClient.delete(
        '/api/app/community/posts/$postId',
      );
      return res is Map<String, dynamic> ? res['success'] != false : true;
    } catch (_) {
      return false;
    }
  }

  /// 静态快捷方法：帖子点赞
  static Future<bool> togglePostLike({required String postId}) async {
    return _instance.togglePostLikeRaw(postId);
  }

  static Future<CommunityPost?> togglePostLikeResult({
    required String postId,
  }) => _instance.togglePostLikeResultRaw(postId);

  /// 静态快捷方法：评论点赞
  static Future<bool> toggleCommentLike({
    required String postId,
    required String commentId,
  }) async {
    return _instance.toggleCommentLikeRaw(commentId);
  }

  /// 静态快捷方法：提交举报
  static Future<bool> submitReport({
    required String postId,
    String targetType = 'POST',
    String reasonCode = 'OTHER',
    required String reason,
    String? description,
    List<String> evidenceUrls = const [],
  }) async {
    final detail = (description == null || description.trim().isEmpty)
        ? reason
        : '$reason：${description.trim()}';
    return _instance.submitReportRaw(
      targetType: targetType,
      targetId: postId,
      reasonCode: reasonCode,
      reasonDetail: detail,
    );
  }

  /// 静态快捷方法：查询作者资料
  ///
  /// 后端 GET /api/app/community/authors/{id} 实际下发 displayName/avatarUrl/wornBadgeCode/
  /// college/major/className（见 CommunityAuthorProfileResponse）；department/uid/统计四项
  /// 服务端暂未下发，维持兜底值（统计恒 0 的补齐属"社区标签"任务）。
  static Future<AuthorProfileInfo?> fetchAuthorProfile(String authorId) async {
    try {
      final res = await _instance.fetchAuthorProfileRaw(authorId);
      final name = (res?['displayName'] ?? res?['authorName'])?.toString();
      if (res != null && name != null && name.isNotEmpty) {
        return AuthorProfileInfo(
          authorId: authorId,
          authorName: name,
          avatarUrl: _absoluteUrl(res['avatarUrl']?.toString()),
          avatarBgColor: const Color(0xFFEFF6FF),
          authorLevel: 'Lv.${res['level'] ?? 4}',
          authorOfficial: res['official'] == true,
          authorOfficialTitle: res['officialTitle']?.toString(),
          badgeAsset: res['badgeAsset']?.toString(),
          department: res['department']?.toString() ?? '绵阳城市学院',
          uid: res['uid']?.toString() ?? authorId,
          likeCount: (res['likeTotal'] as num?)?.toInt() ?? 0,
          followCount: (res['followingCount'] as num?)?.toInt() ?? 0,
          fansCount: (res['followerCount'] as num?)?.toInt() ?? 0,
          postCount: (res['postCount'] as num?)?.toInt() ?? 0,
          isFollowed: res['isFollowing'] == true || res['isFollowed'] == true,
        );
      }
    } catch (_) {}
    return null;
  }

  /// 静态快捷方法：发送私信
  static Future<bool> sendMessage({
    required String receiverId,
    required String content,
    List<String> imageFileIds = const [],
  }) async {
    final res = await _instance.sendMessageRaw(
      receiverId,
      content,
      imageFileIds: imageFileIds,
    );
    return res != null;
  }

  /// 查询社区分区 (GET /api/app/community/sections)
  Future<List<CommunitySection>?> fetchSectionsRaw() async {
    try {
      final res = await apiClient.get('/api/app/community/sections');
      final data = res is List
          ? res
          : (res is Map<String, dynamic> && res['data'] is List
                ? res['data'] as List
                : null);
      if (data != null) {
        return data
            .whereType<Map<String, dynamic>>()
            .map(
              (m) => CommunitySection(
                id: m['sectionId']?.toString() ?? '',
                code: m['sectionCode']?.toString() ?? '',
                name: m['sectionName']?.toString() ?? '',
              ),
            )
            .toList();
      }
    } catch (_) {}
    return null;
  }

  /// 分页查询帖子列表 (GET /api/app/community/posts)
  Future<Map<String, dynamic>?> fetchPostsRaw({
    String? sectionId,
    String? keyword,
    int pageNum = 1,
    int pageSize = 10,
    String sort = 'LATEST',
  }) async {
    try {
      final query = <String, String>{
        'pageNum': '$pageNum',
        'pageSize': '$pageSize',
        'sort': sort,
        if (sectionId != null && sectionId.isNotEmpty) 'sectionId': sectionId,
        if (keyword != null && keyword.isNotEmpty) 'keyword': keyword,
      };
      final uri = Uri(
        path: '/api/app/community/posts',
        queryParameters: query,
      ).toString();
      final res = await apiClient.get(uri);
      if (res is Map<String, dynamic>) {
        if (res['success'] == true && res['data'] is Map<String, dynamic>) {
          return res['data'];
        }
        return res;
      }
    } catch (_) {}
    return null;
  }

  /// 混合信息流原始响应 (GET /api/app/community/feed)
  Future<Map<String, dynamic>?> fetchFeedPageRaw({
    String? sectionId,
    int pageNum = 1,
    int pageSize = 10,
    String sort = 'LATEST',
    String? sessionId,
  }) async {
    try {
      final query = <String, String>{
        'pageNum': '$pageNum',
        'pageSize': '$pageSize',
        'sort': sort,
        if (sectionId != null && sectionId.isNotEmpty) 'sectionId': sectionId,
        if (sessionId != null && sessionId.isNotEmpty) 'sessionId': sessionId,
      };
      final uri = Uri(
        path: '/api/app/community/feed',
        queryParameters: query,
      ).toString();
      final res = await apiClient.get(uri);
      if (res is Map<String, dynamic>) return res;
    } catch (_) {}
    return null;
  }

  /// 查询帖子详情与评论流 (GET /api/app/community/posts/{postId})
  Future<Map<String, dynamic>?> fetchPostDetailRaw(String postId) async {
    try {
      final res = await apiClient.get('/api/app/community/posts/$postId');
      if (res is Map<String, dynamic>) {
        if (res['success'] == true && res['data'] is Map<String, dynamic>) {
          return res['data'];
        }
        return res;
      }
    } catch (_) {}
    return null;
  }

  /// 查询帖子详情（抛 ApiException，供判断 404/已隐藏）
  Future<Map<String, dynamic>?> fetchPostDetailRawOrThrow(String postId) async {
    final res = await apiClient.get('/api/app/community/posts/$postId');
    if (res is Map<String, dynamic>) {
      if (res['success'] == true && res['data'] is Map<String, dynamic>) {
        return res['data'] as Map<String, dynamic>;
      }
      return res;
    }
    return null;
  }

  /// 发布帖子 (POST /api/app/community/posts)
  Future<Map<String, dynamic>?> createPostRaw({
    String? sectionId,
    required String title,
    required String content,
    String postType = 'DYNAMIC',
    List<Map<String, dynamic>> contentBlocks = const [],
    List<String> imageFileIds = const [],
    List<String> topics = const [],
    PostLocation? location,
  }) async {
    try {
      final res = await apiClient.post(
        '/api/app/community/posts',
        body: {
          if (sectionId != null) 'sectionId': sectionId,
          'title': title,
          'content': content,
          'postType': postType,
          'topics': topics,
          'imageFileIds': imageFileIds,
          if (contentBlocks.isNotEmpty) 'contentBlocks': contentBlocks,
          if (location != null) ...{
            'locationName': location.name,
            'locationAddress': location.address,
            'latitude': location.latitude,
            'longitude': location.longitude,
            if (location.poiId.isNotEmpty) 'poiId': location.poiId,
          },
        },
      );
      if (res is Map<String, dynamic>) {
        if (res['success'] == true && res['data'] is Map<String, dynamic>) {
          return res['data'] as Map<String, dynamic>;
        }
        return res;
      }
    } catch (_) {}
    return null;
  }

  /// 帖子点赞 / 取消点赞 (POST /api/app/community/posts/{postId}/like)
  Future<bool> togglePostLikeRaw(String postId) async =>
      (await togglePostLikeResultRaw(postId)) != null;

  Future<CommunityPost?> togglePostLikeResultRaw(String postId) async {
    try {
      final data = _responseMap(
        await apiClient.post('/api/app/community/posts/$postId/like', body: {}),
      );
      if (data == null ||
          data['postId']?.toString() != postId ||
          data['liked'] is! bool ||
          data['likeCount'] is! num) {
        return null;
      }
      return mapPost(data);
    } catch (_) {
      return null;
    }
  }

  /// 发表评论 (POST /api/app/community/posts/{postId}/comments)
  Future<Map<String, dynamic>?> createCommentRaw(
    String postId, {
    required String content,
    String? parentCommentId,
  }) async {
    try {
      final res = await apiClient.post(
        '/api/app/community/posts/$postId/comments',
        body: {
          'content': content,
          if (parentCommentId != null) 'parentCommentId': parentCommentId,
        },
      );
      if (res is Map<String, dynamic>) {
        if (res['success'] == true && res['data'] is Map<String, dynamic>) {
          return res['data'];
        }
        return res;
      }
    } catch (_) {}
    return null;
  }

  /// 评论点赞 (POST /api/app/community/comments/{commentId}/like)
  ///
  /// apiClient 成功时已剥掉 Result 外层、返回 data（即评论对象本身），
  /// 这里校验 data 里的 liked/likeCount 字段而非不存在的 success 外层。
  Future<bool> toggleCommentLikeRaw(String commentId) async {
    try {
      final data = _responseMap(
        await apiClient.post('/api/app/community/comments/$commentId/like', body: {}),
      );
      return data != null && data['liked'] is bool && data['likeCount'] is num;
    } catch (_) {
      return false;
    }
  }

  /// 提交违规举报 (POST /api/app/community/reports)
  Future<bool> submitReportRaw({
    required String targetType, // POST, COMMENT, MESSAGE, USER
    required String targetId,
    required String reasonCode, // AD, ABUSE, PORN, POLITICS, PRIVACY, OTHER
    String? reasonDetail,
  }) async {
    try {
      final res = await apiClient.post(
        '/api/app/community/reports',
        body: {
          'targetType': targetType,
          'targetId': targetId,
          'reasonCode': reasonCode,
          if (reasonDetail != null) 'reasonDetail': reasonDetail,
        },
      );
      return res is Map<String, dynamic> ? res['success'] == true : true;
    } catch (_) {
      return false;
    }
  }

  /// 查询作者主页资料与发帖 (GET /api/app/community/authors/{authorId})
  Future<Map<String, dynamic>?> fetchAuthorProfileRaw(String authorId) async {
    try {
      final res = await apiClient.get('/api/app/community/authors/$authorId');
      if (res is Map<String, dynamic>) {
        if (res['success'] == true && res['data'] is Map<String, dynamic>) {
          return res['data'];
        }
        return res;
      }
    } catch (_) {}
    return null;
  }

  /// 作者主页真实帖子列表（GET /api/app/community/authors/{authorId}）
  static Future<List<CommunityPost>> fetchAuthorPosts(String authorId) async {
    try {
      final raw = await _instance.fetchAuthorProfileRaw(authorId);
      final list = (raw?['posts'] as List?) ?? const [];
      return list
          .whereType<Map>()
          .map((e) => mapPost(e.map((k, v) => MapEntry(k.toString(), v))))
          .toList();
    } catch (_) {
      return const [];
    }
  }

  /// 作者佩戴的勋章编码（作者主页 wornBadgeCode；未佩戴返回 null）
  static Future<String?> fetchWornBadge(String userId) async {
    if (userId.isEmpty) return null;
    try {
      final res = await _instance.fetchAuthorProfileRaw(userId);
      final code = res?['wornBadgeCode']?.toString();
      return code == null || code.isEmpty ? null : code;
    } catch (_) {
      return null;
    }
  }

  /// 用户名片（含勋章墙预览）
  static Future<UserCardInfo?> fetchUserCard(String userId) async {
    if (userId.isEmpty) return null;
    try {
      final res = await _instance.apiClient.get('/api/app/users/$userId/card');
      final data = res is Map<String, dynamic>
          ? (res['data'] is Map<String, dynamic>
                ? res['data'] as Map<String, dynamic>
                : res)
          : null;
      if (data == null) return null;
      final badges = ((data['previewBadges'] as List?) ?? const [])
          .whereType<Map<String, dynamic>>()
          .map(
            (m) => UserCardBadge(
              badgeCode: m['badgeCode']?.toString() ?? '',
              name: m['name']?.toString() ?? '',
              iconUrl: _absoluteUrl(m['iconUrl']?.toString()),
            ),
          )
          .toList();
      return UserCardInfo(
        userId: data['userId']?.toString() ?? userId,
        displayName: data['displayName']?.toString() ?? '同学',
        previewBadges: badges,
      );
    } catch (_) {
      return null;
    }
  }

  /// 提交内容申诉（作者本人，帖子被隐藏/删除后）
  static Future<void> submitAppeal({
    required String postId,
    String? reasonCode,
    String? reason,
    List<String> evidenceFileIds = const [],
  }) async {
    await _instance.apiClient.post(
      '/api/app/community/appeals',
      body: {
        'targetType': 'POST',
        'targetId': postId,
        if (reasonCode != null && reasonCode.isNotEmpty)
          'reasonCode': reasonCode,
        if (reason != null && reason.trim().isNotEmpty) 'reason': reason.trim(),
        'evidenceFileIds': evidenceFileIds,
      },
    );
  }

  /// 标记私信会话已读
  static Future<void> markConversationRead(
    String peerId, {
    List<String> messageIds = const [],
  }) async {
    if (peerId.isEmpty) return;
    await markMessagesRead(withUserId: peerId, messageIds: messageIds);
  }

  /// 查询私信消息 (GET /api/app/community/messages)
  Future<List<Map<String, dynamic>>?> fetchMessagesRaw(
    String withUserId,
  ) async {
    try {
      final res = await apiClient.get(
        '/api/app/community/messages?withUserId=$withUserId',
      );
      if (res is List) {
        return res.whereType<Map<String, dynamic>>().toList();
      }
      if (res is Map<String, dynamic> && res['data'] is List) {
        return (res['data'] as List).whereType<Map<String, dynamic>>().toList();
      }
    } catch (_) {}
    return null;
  }

  /// 发送私信（文本 + 图片）
  Future<Map<String, dynamic>?> sendMessageRaw(
    String receiverId,
    String content, {
    List<String> imageFileIds = const [],
  }) async {
    try {
      final res = await apiClient.post(
        '/api/app/community/messages',
        body: {
          'receiverId': receiverId,
          'content': content,
          'imageFileIds': imageFileIds,
        },
      );
      if (res is Map<String, dynamic>) {
        if (res['success'] == true && res['data'] is Map<String, dynamic>) {
          return res['data'] as Map<String, dynamic>;
        }
        return res;
      }
    } catch (_) {}
    return null;
  }

  /// 私信会话列表
  Future<List<DirectConversation>?> fetchConversationsRaw() async {
    try {
      final res = await apiClient.get('/api/app/community/messages/contacts');
      if (res is List) {
        return res
            .whereType<Map<String, dynamic>>()
            .map(
              (m) => DirectConversation(
                peerId: m['peerId']?.toString() ?? '',
                peerName: m['peerName']?.toString() ?? '同学',
                avatarUrl: _absoluteUrl(m['avatarUrl']?.toString()),
                lastContent: m['lastContent']?.toString() ?? '',
                lastAt: DateTime.tryParse(m['lastAt']?.toString() ?? ''),
              ),
            )
            .toList();
      }
    } catch (_) {}
    return null;
  }
}
