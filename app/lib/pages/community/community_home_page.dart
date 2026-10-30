import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/ad_slot_api.dart';
import '../../api/community_api.dart';
import '../../api/feature_flags.dart';
import '../../models/community_models.dart';
import '../../models/user_session.dart';
import '../../services/community_interaction_store.dart';
import '../../services/community_feed_controller.dart';
import '../../widgets/community_post_card.dart';
import '../../widgets/app_status_panel.dart';
import '../../theme/app_theme.dart';
import '../../widgets/community_ad_card.dart';
import '../../widgets/skeleton_shimmer.dart';
import 'author_profile_page.dart';
import 'community_report_sheet.dart';
import 'community_share_sheet.dart';
import 'post_detail_page.dart';
import 'post_publish_page.dart';

/// APP-社区主页 (对应画布 5:157 节点，375 × 812)
/// 顶部标题/搜索/发帖、分区横滑、信息流卡片、多图与互动；数据走社区真实接口。
class CommunityHomePage extends StatefulWidget {
  final ValueChanged<int>? onTabChanged;
  final int currentTabIndex;

  /// 测试/预览注入初始帖子；传入时跳过网络请求
  final List<CommunityPost>? initialPosts;

  /// 可注入交互网关，测试与预览无需依赖真实网络。
  final CommunityInteractionStore? interactionStore;

  const CommunityHomePage({
    super.key,
    this.onTabChanged,
    this.currentTabIndex = 1,
    this.initialPosts,
    this.interactionStore,
  });

  @override
  State<CommunityHomePage> createState() => _CommunityHomePageState();
}

class _CommunityHomePageState extends State<CommunityHomePage> {
  List<String> _tabs = const ['推荐', '最新'];
  List<CommunitySection> _sections = const [];
  bool _loadingSections = true;

  int _selectedTab = 0;
  String _keyword = '';
  late final CommunityFeedController _feed;
  List<CommunityFeedItem> get _items => _feed.items;
  bool get _loading => _feed.loading;
  bool get _error => _feed.error;
  int get _loadToken => _feed.generation;
  late final CommunityInteractionStore _interactions;

  /// 广告曝光去重（按后端返回的 clickId）与卡片定位 Key，用于判断是否滚入视口
  final Set<String> _reportedImpressions = {};
  final Map<String, GlobalKey> _adKeys = {};

  final ScrollController _scrollController = ScrollController();
  Timer? _impressionTimer;

  @override
  void initState() {
    super.initState();
    _interactions =
        widget.interactionStore ??
        CommunityInteractionScope.acquire(UserSession.currentUserId ?? 'guest');
    _feed = CommunityFeedController(interactions: _interactions);
    _feed.addListener(_onFeedChanged);
    _interactions.addListener(_onInteractionsChanged);
    unawaited(_interactions.initialize());
    FeatureFlags.revision.addListener(_onFeatureFlagsChanged);
    if (widget.initialPosts != null) {
      _feed.seed(widget.initialPosts!);
      _tabs = const ['推荐', '最新', '二手闲置', '学习互助', '失物招领'];
    } else {
      _scrollController.addListener(_onScroll);
      _loadSections();
    }
  }

  @override
  void dispose() {
    FeatureFlags.revision.removeListener(_onFeatureFlagsChanged);
    _feed.removeListener(_onFeedChanged);
    _feed.dispose();
    _interactions.removeListener(_onInteractionsChanged);
    if (widget.interactionStore == null) {
      CommunityInteractionScope.release(_interactions);
    }
    _impressionTimer?.cancel();
    _scrollController.dispose();
    super.dispose();
  }

  void _onFeedChanged() {
    if (mounted) setState(() {});
  }

  void _onInteractionsChanged() {
    if (mounted) setState(_feed.applyInteractions);
  }

  /// 开关到达/变化后重载信息流：关闭广告时改用纯自然内容接口。
  void _onFeatureFlagsChanged() {
    if (!mounted || widget.initialPosts != null || _loadingSections) return;
    _loadPosts(reset: true);
  }

  double _topInset(BuildContext context) {
    final top = MediaQuery.of(context).padding.top;
    return top > 0 ? top + 6 : 50;
  }

  Future<void> _loadSections() async {
    final sections = await CommunityApi.fetchSections();
    if (!mounted) return;
    setState(() {
      _sections = sections;
      _tabs = ['推荐', '最新', ...sections.map((s) => s.name)];
      if (_selectedTab >= _tabs.length) _selectedTab = 0;
      _loadingSections = false;
    });
    await _loadPosts(reset: true);
  }

  int? get _selectedSectionId {
    if (_selectedTab < 2) return null;
    return int.tryParse(_sections[_selectedTab - 2].id);
  }

  Future<void> _loadPosts({bool reset = false}) async {
    if (reset) {
      _reportedImpressions.clear();
      _adKeys.clear();
    }
    await _feed.load(
      CommunityFeedQuery(
        category: _tabs[_selectedTab],
        sectionId: _selectedSectionId,
        keyword: _keyword,
        adsEnabled: FeatureFlags.communityAdsEnabled,
      ),
      reset: reset,
    );
    if (mounted) _scheduleImpressionCheck();
  }

  /// 判断当前哪些广告卡片已进入视口，进入视口时上报一次曝光（同 clickId 去重）
  void _scheduleImpressionCheck() {
    if (!FeatureFlags.communityAdsEnabled) return;
    _impressionTimer?.cancel();
    _impressionTimer = Timer(
      const Duration(milliseconds: 180),
      _maybeReportImpressions,
    );
  }

  void _maybeReportImpressions() {
    if (!mounted || _items.isEmpty) return;
    if (!FeatureFlags.communityAdsEnabled) return;
    final screenHeight = MediaQuery.of(context).size.height;
    final pending = <CommunityAd>[];
    for (final item in _items) {
      final ad = item.ad;
      if (ad == null || ad.clickId.isEmpty) continue;
      if (_reportedImpressions.contains(ad.clickId)) continue;
      final key = _adKeys[ad.clickId];
      final cardContext = key?.currentContext;
      if (cardContext == null) continue;
      final renderObject = cardContext.findRenderObject();
      if (renderObject is! RenderBox || !renderObject.attached) continue;
      final top = renderObject.localToGlobal(Offset.zero).dy;
      final bottom = top + renderObject.size.height;
      if (bottom <= 0 || top >= screenHeight) continue;
      _reportedImpressions.add(ad.clickId);
      pending.add(ad);
    }
    for (final ad in pending) {
      _reportImpression(ad);
    }
  }

  Future<void> _reportImpression(CommunityAd ad) async {
    final confirmed = await CommunityApi.reportAdImpression(
      adId: ad.adId,
      clickId: ad.clickId,
    );
    if (!mounted) return;
    if (confirmed == null || confirmed.isEmpty || confirmed == ad.clickId) {
      return;
    }
    // 后端确认的 clickId 是后续点击/落地链路的唯一凭据
    final index = _items.indexWhere(
      (item) => item.isAd && item.ad?.clickId == ad.clickId,
    );
    if (index < 0) return;
    setState(() {
      _items[index] = CommunityFeedItem.ofAd(ad.copyWith(clickId: confirmed));
    });
  }

  void _onScroll() {
    if (!_scrollController.hasClients) return;
    final position = _scrollController.position;
    if (position.pixels >= position.maxScrollExtent - 240) {
      _loadPosts();
    }
    _scheduleImpressionCheck();
  }

  Future<void> _refresh() => _loadPosts(reset: true);

  void _selectTab(int index) {
    if (index == _selectedTab && _keyword.isEmpty) return;
    setState(() {
      _selectedTab = index;
      _keyword = '';
      _feed.clear();
    });
    _loadPosts(reset: true);
  }

  void _openPublish() async {
    final result = await Navigator.of(context).push<CommunityPost>(
      MaterialPageRoute(builder: (_) => const PostPublishPage()),
    );
    if (result != null) {
      await _loadPosts(reset: true);
    }
  }

  void _openPostDetail(CommunityPost post) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) =>
            PostDetailPage(post: post, interactionStore: _interactions),
      ),
    );
    if (mounted) {
      _onInteractionsChanged();
      unawaited(_interactions.refreshPost(post));
    }
  }

  void _openAuthorProfile(CommunityPost post) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => AuthorProfilePage(
          authorId: post.authorId,
          authorName: post.authorName,
          avatarBgColor: post.avatarBgColor,
          authorLevel: post.authorLevel,
          authorOfficial: post.authorOfficial,
          authorOfficialTitle: post.authorOfficialTitle,
          badgeAsset: post.badgeAsset,
          sourcePost: post,
          interactionStore: _interactions,
        ),
      ),
    );
    if (mounted) {
      unawaited(_interactions.refreshFollowing([post.authorId], force: true));
    }
  }

  void _openShare(CommunityPost post) {
    CommunityShareSheet.show(context, post);
  }

  void _openReport(CommunityPost post) {
    CommunityReportSheet.show(context, post);
  }

  void _showMoreActions(CommunityPost post) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Material(
        color: Colors.white,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
        clipBehavior: Clip.antiAlias,
        child: Padding(
          padding: EdgeInsets.only(
            top: 16,
            bottom: MediaQuery.of(ctx).padding.bottom + 12,
            left: 16,
            right: 16,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: const Color(0xFFE2E8F0),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 16),
              ListTile(
                leading: const Icon(
                  Icons.share_outlined,
                  color: AppColors.textBody,
                ),
                title: const Text(
                  '分享给同学 / 社交平台',
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
                ),
                onTap: () {
                  Navigator.of(ctx).pop();
                  _openShare(post);
                },
              ),
              ListTile(
                leading: const Icon(
                  Icons.link_rounded,
                  color: AppColors.textBody,
                ),
                title: const Text(
                  '复制帖子内容',
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
                ),
                onTap: () async {
                  final text = [
                    if (post.title.trim().isNotEmpty) post.title.trim(),
                    if (post.content.trim().isNotEmpty) post.content.trim(),
                    '帖子 ID：${post.id}',
                  ].join('\n');
                  await Clipboard.setData(ClipboardData(text: text));
                  if (!ctx.mounted) return;
                  Navigator.of(ctx).pop();
                  if (!mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('帖子内容已复制到剪贴板'),
                      behavior: SnackBarBehavior.floating,
                    ),
                  );
                },
              ),
              ListTile(
                leading: const Icon(
                  Icons.visibility_off_outlined,
                  color: AppColors.textBody,
                ),
                title: const Text(
                  '在此设备隐藏此帖',
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
                ),
                onTap: () {
                  Navigator.of(ctx).pop();
                  _handleHide(post);
                },
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(
                  Icons.report_problem_outlined,
                  color: AppColors.danger,
                ),
                title: const Text(
                  '举报违规内容',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w500,
                    color: AppColors.danger,
                  ),
                ),
                onTap: () {
                  Navigator.of(ctx).pop();
                  _openReport(post);
                },
              ),
              const SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                height: 44,
                child: TextButton(
                  onPressed: () => Navigator.of(ctx).pop(),
                  style: TextButton.styleFrom(
                    backgroundColor: const Color(0xFFF1F5F9),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(22),
                    ),
                  ),
                  child: const Text(
                    '取消',
                    style: TextStyle(
                      fontSize: 14,
                      color: Color(0xFF64748B),
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  CommunityPost? _postById(String id) {
    for (final item in _items) {
      if (item.post?.id == id) return item.post;
    }
    return null;
  }

  void _showInteractionMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..removeCurrentSnackBar()
      ..showSnackBar(
        SnackBar(content: Text(message), behavior: SnackBarBehavior.floating),
      );
  }

  Future<void> _handleToggleLike(String postId) async {
    final post = _postById(postId);
    if (post == null || _interactions.likePending(postId)) return;
    if (!await _interactions.toggleLike(post)) {
      _showInteractionMessage('点赞结果未确认，已恢复显示，请刷新后重试');
    }
  }

  Future<void> _handleToggleBookmark(String postId) async {
    final post = _postById(postId);
    if (post == null || _interactions.bookmarkPending(postId)) return;
    final saved = await _interactions.toggleBookmark(post);
    _showInteractionMessage(
      saved
          ? (_interactions.apply(post).isBookmarked
                ? '已收藏'
                : '已取消收藏')
          : '收藏操作失败，请重试',
    );
  }

  Future<void> _handleToggleFollow(String postId) async {
    final post = _postById(postId);
    if (post == null || _interactions.followPending(post.authorId)) return;
    final confirmed = await _interactions.toggleFollowing(post);
    _showInteractionMessage(
      confirmed
          ? (_interactions.apply(post).isFollowed
                ? '已关注 ${post.authorName}'
                : '已取消关注')
          : '关注操作失败，已恢复显示，请重试',
    );
  }

  Future<void> _handleHide(CommunityPost post) async {
    final index = _items.indexWhere((item) => item.post?.id == post.id);
    final feedToken = _loadToken;
    if (index < 0) return;
    final saved = await _interactions.setHidden(post.id, true);
    if (!mounted) return;
    if (!saved) {
      _showInteractionMessage('隐藏设置保存失败，请重试');
      return;
    }
    ScaffoldMessenger.of(context)
      ..removeCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: const Text('已在此设备隐藏此帖'),
          behavior: SnackBarBehavior.floating,
          action: SnackBarAction(
            label: '撤销',
            onPressed: () async {
              final restored = await _interactions.setHidden(post.id, false);
              if (!mounted) return;
              if (!restored) {
                _showInteractionMessage('撤销失败，请重试');
              } else if (_loadToken == feedToken &&
                  _postById(post.id) == null) {
                setState(
                  () => _items.insert(
                    index.clamp(0, _items.length),
                    CommunityFeedItem.ofPost(_interactions.apply(post)),
                  ),
                );
              }
            },
          ),
        ),
      );
  }

  void _showSearchDialog() {
    showDialog(
      context: context,
      builder: (ctx) {
        final controller = TextEditingController(text: _keyword);
        return AlertDialog(
          title: const Text(
            '搜索社区动态',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
          ),
          content: TextField(
            controller: controller,
            autofocus: true,
            decoration: const InputDecoration(
              hintText: '输入关键词，如 自习室、轻食、考研...',
              border: OutlineInputBorder(),
              isDense: true,
            ),
            onSubmitted: (value) {
              setState(() => _keyword = value.trim());
              Navigator.of(ctx).pop();
              _loadPosts(reset: true);
            },
          ),
          actions: [
            if (_keyword.isNotEmpty)
              TextButton(
                onPressed: () {
                  setState(() => _keyword = '');
                  Navigator.of(ctx).pop();
                  _loadPosts(reset: true);
                },
                child: const Text('清除搜索'),
              ),
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('取消'),
            ),
            ElevatedButton(
              onPressed: () {
                setState(() => _keyword = controller.text.trim());
                Navigator.of(ctx).pop();
                _loadPosts(reset: true);
              },
              child: const Text('搜索'),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        top: false,
        bottom: false,
        child: Column(
          children: [
            _buildHeader(),
            _buildCategoryTabs(),
            Expanded(child: _buildFeed()),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      color: Colors.white,
      padding: EdgeInsets.fromLTRB(16, _topInset(context), 16, 8),
      child: Row(
        children: [
          const Text(
            '校园社区',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 20,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const Spacer(),
          GestureDetector(
            onTap: _showSearchDialog,
            behavior: HitTestBehavior.opaque,
            child: Container(
              width: 36,
              height: 36,
              decoration: const BoxDecoration(
                color: Color(0xFFF1F5F9),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.search_rounded,
                size: 20,
                color: AppColors.textPrimary,
              ),
            ),
          ),
          const SizedBox(width: 10),
          GestureDetector(
            onTap: _openPublish,
            child: Container(
              height: 32,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF2563EB), Color(0xFF1D4ED8)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(16),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x332563EB),
                    blurRadius: 8,
                    offset: Offset(0, 3),
                  ),
                ],
              ),
              child: const Row(
                children: [
                  Icon(Icons.add_rounded, size: 16, color: Colors.white),
                  SizedBox(width: 2),
                  Text(
                    '发帖',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCategoryTabs() {
    return Container(
      height: 42,
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(bottom: BorderSide(color: Color(0xFFF1F5F9))),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: List.generate(_tabs.length, (index) {
          final selected = _selectedTab == index && _keyword.isEmpty;
          return GestureDetector(
            onTap: () => _selectTab(index),
            behavior: HitTestBehavior.opaque,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  _tabs[index],
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 14,
                    fontWeight: selected ? FontWeight.bold : FontWeight.normal,
                    color: selected ? AppColors.brand : const Color(0xFF64748B),
                  ),
                ),
                const SizedBox(height: 4),
                AnimatedContainer(
                  duration: MediaQuery.disableAnimationsOf(context)
                      ? Duration.zero
                      : const Duration(milliseconds: 150),
                  curve: Curves.easeOutCubic,
                  width: selected ? 24 : 0,
                  height: 3,
                  decoration: BoxDecoration(
                    color: selected ? AppColors.brand : Colors.transparent,
                    borderRadius: BorderRadius.circular(1.5),
                  ),
                ),
              ],
            ),
          );
        }),
      ),
    );
  }

  Widget _buildFeed() {
    if (_loading && _items.isEmpty) return _buildFeedSkeleton();
    if (_error && _items.isEmpty) return _buildErrorState();
    if (_items.isEmpty) {
      return RefreshIndicator(
        onRefresh: _refresh,
        color: AppColors.brand,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [_buildEmptyState()],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _refresh,
      color: AppColors.brand,
      child: ListView.separated(
        controller: _scrollController,
        physics: const AlwaysScrollableScrollPhysics(
          parent: BouncingScrollPhysics(),
        ),
        padding: const EdgeInsets.only(bottom: 24),
        itemCount:
            _items.length + (_feed.loadingMore || _feed.loadMoreFailed ? 1 : 0),
        separatorBuilder: (_, _) =>
            Container(height: 1, color: const Color(0xFFF1F5F9)),
        itemBuilder: (context, index) {
          if (index >= _items.length) {
            if (_feed.loadMoreFailed) {
              return AppStatusPanel(
                title: '加载更多失败，点击重试',
                onRetry: _loadPosts,
                compact: true,
              );
            }
            return const Padding(
              padding: EdgeInsets.symmetric(vertical: 18),
              child: Center(
                child: SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ),
            );
          }
          final item = _items[index];
          if (item.isAd && item.ad != null) {
            return _buildAdCard(item.ad!);
          }
          final post = item.post;
          if (post == null) return const SizedBox.shrink();
          return _buildPostCard(post);
        },
      ),
    );
  }

  Widget _buildAdCard(CommunityAd ad) {
    final key = _adKeys.putIfAbsent(ad.clickId, GlobalKey.new);
    return KeyedSubtree(
      key: key,
      child: CommunityAdCard(
        ad: ad,
        onTap: () => _handleAdTap(ad),
        onCtaTap: () => _handleAdTap(ad),
      ),
    );
  }

  /// 点击广告：复用统一点击/落地链路（曝光→点击→落地加载→停留/跳出）。
  Future<void> _handleAdTap(CommunityAd ad) {
    return AdSlotApi.openLanding(context, ad, onError: _showAdSnack);
  }

  void _showAdSnack(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        duration: const Duration(seconds: 2),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  Widget _buildEmptyState() {
    return Padding(
      padding: const EdgeInsets.only(top: 96),
      child: Column(
        children: [
          Container(
            width: 72,
            height: 72,
            decoration: const BoxDecoration(
              color: Color(0xFFEFF6FF),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.forum_outlined,
              size: 34,
              color: AppColors.brand,
            ),
          ),
          const SizedBox(height: 18),
          const Text(
            '还没有动态',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 16,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            '来发布第一条校园动态吧',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(height: 20),
          GestureDetector(
            onTap: _openPublish,
            behavior: HitTestBehavior.opaque,
            child: Container(
              width: 140,
              height: 48,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: AppColors.brand,
                borderRadius: BorderRadius.circular(21),
              ),
              child: const Text(
                '去发帖',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildErrorState() => AppStatusPanel(
    title: '加载失败',
    message: '网络开小差了，请检查网络后重试',
    onRetry: _refresh,
  );

  Widget _buildFeedSkeleton() {
    return SkeletonShimmer(
      child: ListView(
        physics: const NeverScrollableScrollPhysics(),
        padding: EdgeInsets.only(top: _topInset(context)),
        children: List.generate(3, (index) => _buildPostSkeleton()),
      ),
    );
  }

  Widget _buildPostSkeleton() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              _box(40, 40, 20),
              const SizedBox(width: 10),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _box(96, 14, 7),
                  const SizedBox(height: 6),
                  _box(150, 11, 5),
                ],
              ),
            ],
          ),
          const SizedBox(height: 12),
          _box(300, 16, 8),
          const SizedBox(height: 8),
          _box(double.infinity, 13, 6),
          const SizedBox(height: 6),
          _box(260, 13, 6),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(child: _box(double.infinity, 104, 10)),
              const SizedBox(width: 8),
              Expanded(child: _box(double.infinity, 104, 10)),
              const SizedBox(width: 8),
              Expanded(child: _box(double.infinity, 104, 10)),
            ],
          ),
          const SizedBox(height: 12),
          _box(double.infinity, 18, 6),
        ],
      ),
    );
  }

  Widget _box(double width, double height, double radius) {
    return Container(
      width: width == double.infinity ? null : width,
      height: height,
      decoration: BoxDecoration(
        color: const Color(0xFFE2E8F0),
        borderRadius: BorderRadius.circular(radius),
      ),
    );
  }

  Widget _buildPostCard(CommunityPost post) => CommunityPostCard(
    key: ValueKey('community-post-${post.id}'),
    post: post,
    accountId: _interactions.accountId,
    likePending: _interactions.likePending(post.id),
    followPending: _interactions.followPending(post.authorId),
    bookmarkPending: _interactions.bookmarkPending(post.id),
    onOpen: () => _openPostDetail(post),
    onAuthor: () => _openAuthorProfile(post),
    onFollow: () => _handleToggleFollow(post.id),
    onMore: () => _showMoreActions(post),
    onShare: () => _openShare(post),
    onBookmark: () => _handleToggleBookmark(post.id),
    onLike: () => _handleToggleLike(post.id),
  );
}
