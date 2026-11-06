import 'dart:async';

import '../../services/message_state_events.dart';
import '../../services/notification_navigation.dart';
import '../../models/message_models.dart';

import 'package:flutter/material.dart';

import '../../api/ad_slot_api.dart';
import '../../api/auth_service.dart';
import '../../api/community_api.dart';
import '../../api/feature_flags.dart';
import '../../models/community_models.dart';
import '../../models/home_dashboard_model.dart';
import '../../services/community_deep_link_service.dart';
import '../../models/service_catalog_models.dart';
import '../../models/user_session.dart';
import '../../services/app_update_service.dart';
import '../../services/badge_catalog_sync.dart';
import '../../services/community_interaction_store.dart';
import '../../services/community_socket.dart';
import '../../services/system_notification.dart';
import '../message/announcement_detail_page.dart';
import '../../theme/app_theme.dart';
import '../../widgets/user_avatar.dart';
import '../../widgets/app_bottom_navigation.dart';
import '../../widgets/community_ad_card.dart';
import 'home_dashboard_controller.dart';
import 'home_navigation.dart';
import 'widgets/home_action_card.dart';
import 'widgets/home_campus_header.dart';
import 'widgets/home_community_preview.dart';
import 'widgets/home_course_card.dart';
import 'widgets/home_service_hub.dart';
import 'widgets/home_section_status.dart';
import '../community/post_detail_page.dart';
import '../login/login_page.dart';
import '../profile/jw_auth_page.dart';
import '../schedule/schedule_page.dart';
import '../service/my_requests_page.dart';
import '../service/repair_apply_page.dart';
import '../service/service_hall_page.dart';
import '../service/service_apply_page.dart';

/// 首页按课程、待办、服务和资讯排列，业务数据来自真实聚合接口。
class HomePage extends StatefulWidget {
  final UserSession session;
  final AuthService authService;
  final int initialTabIndex;
  final HomeDashboardGateway? dashboardGateway;

  const HomePage({
    super.key,
    required this.session,
    required this.authService,
    this.initialTabIndex = 0,
    this.dashboardGateway,
  });

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage>
    with SingleTickerProviderStateMixin, WidgetsBindingObserver {
  late int _currentTabIndex;
  final List<Widget?> _tabPages = List<Widget?>.filled(5, null);

  /// feature.ai 当前值：关闭时不构建 AIAssistantPage，改为停用说明页（真实降级）
  bool _aiEnabled = FeatureFlags.aiEnabled;

  late final HomeDashboardController _home;
  HomeDashboardData? get _dashboardData => _home.dashboard.data;
  HomeAnnouncementModel? get _announcement {
    final dashAnn = _dashboardData?.announcement;
    final list = _home.announcements.data;
    if (dashAnn == null) {
      return list?.isNotEmpty == true ? list!.first : null;
    }
    if (list == null || list.isEmpty) return dashAnn;
    final matched = list
        .where((e) => e.id == dashAnn.id || e.title == dashAnn.title)
        .firstOrNull;
    if (matched == null) return dashAnn;
    return HomeAnnouncementModel(
      id: dashAnn.id.isNotEmpty ? dashAnn.id : matched.id,
      title: dashAnn.title.isNotEmpty ? dashAnn.title : matched.title,
      tag: dashAnn.tag.isNotEmpty ? dashAnn.tag : matched.tag,
      url: dashAnn.url ?? matched.url,
      read: dashAnn.read || matched.read,
      summary: dashAnn.summary ?? matched.summary,
      publishDate: dashAnn.publishDate ?? matched.publishDate,
      author: dashAnn.author ?? matched.author,
    );
  }
  List<ServiceItemData> get _serviceItems => _home.orderedServices;
  bool get _servicesLoading =>
      _home.services.loading && _home.services.data == null;
  CommunityPost? _hotPost;
  late final CommunityInteractionStore _interactions;

  /// 首页 BANNER 广告位（开关关闭/无数据时为 null，不占位）
  CommunityAd? _bannerAd;
  final Set<String> _reportedAdImpressions = <String>{};
  final ValueNotifier<String?> _communitySearchKeyword =
      ValueNotifier<String?>(null);
  StreamSubscription<void>? _unreadSubscription;
  StreamSubscription<bool>? _statusSubscription;
  Timer? _unreadTimer;
  Timer? _notificationBannerTimer;
  OverlayEntry? _notificationBannerEntry;
  StreamSubscription<DirectMessage>? _socketSubscription;
  StreamSubscription<AppNotificationEvent>? _notificationSubscription;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.session.activate();
    // D-Share：进入主界面（登录/会话恢复完成）即启用帖子深链路由（幂等）
    CommunityDeepLinkService.ensureInitialized();
    _currentTabIndex = widget.initialTabIndex < 0
        ? 0
        : (widget.initialTabIndex > 4 ? 4 : widget.initialTabIndex);
    FeatureFlags.revision.addListener(_onFeatureFlagsChanged);
    _interactions = CommunityInteractionScope.acquire(widget.session.userId)
      ..addListener(_onInteractionsChanged);
    unawaited(_interactions.initialize());
    _home = HomeDashboardController(
      gateway:
          widget.dashboardGateway ??
          ApiHomeDashboardGateway(widget.authService.client),
      accountId: widget.session.userId,
      baseUrl: widget.authService.client.baseUrl,
    )..addListener(_homeChanged);
    unawaited(_home.loadPins());
    _fetchHomeData();
    _startSocket();
    // 徽章字典懒同步：启动时静默拉一次 state，catalogVersion 变化时才拉 catalog。
    unawaited(BadgeCatalogSync.sync());
    // App 版本检查：首帧后触发，不阻塞启动；全局只提示一次，失败静默。
    WidgetsBinding.instance.addPostFrameCallback((_) {
      unawaited(() async {
        try {
          await AppUpdateService.checkAndPrompt(context);
        } catch (_) {
          // 版本检查失败不影响首页使用。
        }
      }());
    });
  }

  void _startSocket() {
    _socketSubscription = CommunitySocket.shared.messages.listen((message) {
      if (!mounted) return;
      if (message.senderId == widget.session.userId) return;
      _refreshUnread();
    });
    _notificationSubscription = CommunitySocket.shared.notificationEvents
        .listen((event) {
          if (!mounted) return;
          _refreshUnread();
          showSystemNotification(event.title, event.content);
          _showTopNotificationBanner(event);
        });
    _unreadSubscription = MessageStateEvents.changes.listen(
      (_) => _refreshUnread(),
    );
    _statusSubscription = CommunitySocket.shared.connectionStatus.listen((
      connected,
    ) {
      if (connected) _refreshUnread();
    });
    CommunitySocket.shared.connect();
  }

  void _dismissNotificationBanner() {
    _notificationBannerTimer?.cancel();
    _notificationBannerTimer = null;
    _notificationBannerEntry?.remove();
    _notificationBannerEntry = null;
  }

  void _showTopNotificationBanner(AppNotificationEvent event) {
    _dismissNotificationBanner();
    final overlay = Overlay.maybeOf(context);
    if (overlay == null) return;
    _notificationBannerEntry = OverlayEntry(
      builder: (ctx) => Positioned(
        top: MediaQuery.paddingOf(ctx).top + 8,
        left: 16,
        right: 16,
        child: Material(
          key: const ValueKey('home-top-notification-banner'),
          color: Colors.transparent,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: const Color(0xFF0F172A).withValues(alpha: 0.94),
              borderRadius: BorderRadius.circular(14),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x260F172A),
                  blurRadius: 12,
                  offset: Offset(0, 4),
                ),
              ],
            ),
            child: Row(
              children: [
                const Icon(
                  Icons.notifications_active_rounded,
                  size: 18,
                  color: Color(0xFF60A5FA),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        event.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                          color: Colors.white,
                        ),
                      ),
                      if (event.content.isNotEmpty)
                        Text(
                          event.content,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 12,
                            color: Color(0xFFCBD5E1),
                          ),
                        ),
                    ],
                  ),
                ),
                TextButton(
                  onPressed: () {
                    _dismissNotificationBanner();
                    if (!mounted) return;
                    NotificationNavigation.open(
                      context,
                      CommunityNotificationItem(
                        notificationId: int.tryParse(event.notificationId) ?? 0,
                        notificationType: event.type,
                        title: event.title,
                        content: event.content,
                        relatedType: event.relatedType,
                        relatedId: int.tryParse(event.relatedId ?? ''),
                        read: false,
                        createdAt: '',
                      ),
                    );
                  },
                  style: TextButton.styleFrom(
                    minimumSize: const Size(44, 32),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    foregroundColor: const Color(0xFF60A5FA),
                  ),
                  child: const Text(
                    '查看',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                  ),
                ),
                GestureDetector(
                  onTap: _dismissNotificationBanner,
                  child: const Padding(
                    padding: EdgeInsets.only(left: 4),
                    child: Icon(
                      Icons.close_rounded,
                      size: 16,
                      color: Color(0xFF94A3B8),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    overlay.insert(_notificationBannerEntry!);
    _notificationBannerTimer = Timer(
      const Duration(seconds: 3),
      _dismissNotificationBanner,
    );
  }

  void _refreshUnread() {
    _unreadTimer?.cancel();
    _unreadTimer = Timer(const Duration(milliseconds: 120), () {
      if (mounted) unawaited(_home.reloadDashboard());
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshUnread();
      CommunitySocket.shared.connect();
      MessageStateEvents.changed();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _dismissNotificationBanner();
    _unreadTimer?.cancel();
    _unreadSubscription?.cancel();
    _statusSubscription?.cancel();
    FeatureFlags.revision.removeListener(_onFeatureFlagsChanged);
    _socketSubscription?.cancel();
    _notificationSubscription?.cancel();
    _communitySearchKeyword.dispose();
    _interactions.removeListener(_onInteractionsChanged);
    CommunityInteractionScope.release(_interactions);
    _home.removeListener(_homeChanged);
    _home.dispose();
    super.dispose();
  }

  void _onInteractionsChanged() {
    if (mounted) setState(() {});
  }

  /// 功能开关到达/变化：广告关闭时立刻移除 BANNER，开启时补拉；AI 关闭时切换到停用页。
  void _onFeatureFlagsChanged() {
    if (!mounted) return;
    final aiEnabled = FeatureFlags.aiEnabled;
    if (aiEnabled != _aiEnabled) {
      setState(() {
        _aiEnabled = aiEnabled;
        _tabPages[2] = null;
        _ensureTab(2);
      });
    }
    if (!FeatureFlags.communityAdsEnabled) {
      if (_bannerAd != null) setState(() => _bannerAd = null);
      return;
    }
    unawaited(_loadBannerAd());
  }

  /// 拉取首页 BANNER 广告位；开关关闭/无数据/失败时保持不展示。
  Future<void> _loadBannerAd() async {
    await FeatureFlags.waitReady();
    if (!mounted || !FeatureFlags.communityAdsEnabled) return;
    final ads = await AdSlotApi.fetchAds(AdSlot.banner);
    if (!mounted || !FeatureFlags.communityAdsEnabled) return;
    final ad = ads.isEmpty ? null : ads.first;
    if (_bannerAd?.clickId == ad?.clickId) return;
    setState(() => _bannerAd = ad);
    if (ad != null) _reportBannerAdImpression(ad);
  }

  void _reportBannerAdImpression(CommunityAd ad) {
    if (ad.clickId.isEmpty || !FeatureFlags.communityAdsEnabled) return;
    if (!_reportedAdImpressions.add(ad.clickId)) return;
    unawaited(AdSlotApi.reportImpression(ad));
  }

  Future<void> _openBannerAd(CommunityAd ad) {
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

  void _homeChanged() {
    if (!mounted) return;
    final posts = _home.posts.data;
    final nextPost = posts?.isNotEmpty == true ? posts!.first : null;
    setState(() {
      _hotPost = nextPost;
    });
  }

  Future<void> _fetchHomeData() async {
    unawaited(_loadBannerAd());
    await _home.refresh();
  }

  /// 主 Tab（0 首页 / 1 社区 / 2 绵宝AI / 3 消息 / 4 我的）
  void _switchTab(int index) {
    _refreshUnread();
    if (index == 3) MessageStateEvents.changed();
    if (index == _currentTabIndex) return;
    setState(() {
      _currentTabIndex = index;
      _ensureTab(index);
    });
  }

  /// 懒加载并缓存 Tab 页面，切回时保留滚动位置与状态
  void _ensureTab(int index) {
    if (index <= 0 || index >= _tabPages.length || _tabPages[index] != null) {
      return;
    }
    _tabPages[index] = HomeNavigation.tab(
      index,
      aiEnabled: _aiEnabled,
      session: widget.session,
      authService: widget.authService,
      switchTab: _switchTab,
      onUnreadChanged: _refreshUnread,
      communitySearchKeyword: _communitySearchKeyword,
    );
  }

  void _openCommunityWithKeyword(String keyword) {
    final trimmed = keyword.trim();
    if (trimmed.isEmpty) {
      _switchTab(1);
      return;
    }
    if (_communitySearchKeyword.value == trimmed) {
      _communitySearchKeyword.value = null;
    }
    _communitySearchKeyword.value = trimmed;
    _switchTab(1);
  }

  void _handleServiceTap(String title) {
    if (title == '账号设置' || title == '个人中心' || title == '退出登录') {
      _showMyProfileSheet();
      return;
    }
    if (title == '进行中工单' || title == '我的申请') {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => const MyRequestsPage(initialFilterIndex: 1),
        ),
      );
      return;
    }
    if (title == '全局搜索') {
      _showSearchModal();
      return;
    }
    if (title == '校园公告' || title == '图书馆公告详情') {
      final ann = _announcement ?? _dashboardData?.announcement;
      if (ann == null) {
        // 没有真实公告时不伪造内容
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('暂无公告'),
            duration: const Duration(seconds: 1),
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
            ),
          ),
        );
        return;
      }
      _showAnnouncementModal(ann);
      return;
    }
    if (title == '教室报修') {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) =>
              const RepairApplyPage(title: '教室与公共区域报修', itemId: '1002'),
        ),
      );
      return;
    }
    if (title == '宿舍报修') {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => const RepairApplyPage(title: '宿舍报修', itemId: '1001'),
        ),
      );
      return;
    }
    if (title == '我的课表' || title == '完整课表') {
      Navigator.of(context)
          .push(MaterialPageRoute(builder: (_) => const SchedulePage()));
      return;
    }
    if (title == '教务学籍认证') {
      Navigator.of(context)
          .push(MaterialPageRoute(builder: (_) => const JwAuthPage()))
          .then((_) => _fetchHomeData());
      return;
    }
    if (title == '消息通知') {
      // 消息为一级 Tab，不再 push 二级页
      _switchTab(3);
      return;
    }
    if (title == '在读证明') {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) =>
              const ServiceApplyPage(itemId: '3001', itemName: '在读证明申请'),
        ),
      );
      return;
    }
    if (title == '请假申请') {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) =>
              const ServiceApplyPage(itemId: '2001', itemName: '请假申请'),
        ),
      );
      return;
    }
    if (title == '场地申请') {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) =>
              const ServiceApplyPage(itemId: '2002', itemName: '活动场地申请'),
        ),
      );
      return;
    }
    if (title == '校园咨询') {
      _switchTab(2);
      return;
    }
    if (title == '服务大厅' || title == '更多服务') {
      // 服务大厅为二级入口：带返回、无底栏
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => const ServiceHallPage(showBack: true),
        ),
      );
      return;
    }
    if (title == '社区广场') {
      _switchTab(1);
      return;
    }
    if (title == '讨论详情') {
      final hotPost = _hotPost;
      if (hotPost == null) {
        // 没有真实热帖时进入社区广场，不伪造帖子详情
        _switchTab(1);
        ScaffoldMessenger.of(context).hideCurrentSnackBar();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('暂无热门讨论，已为你打开社区广场'),
            duration: const Duration(seconds: 1),
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
            ),
          ),
        );
        return;
      }
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => PostDetailPage(post: _interactions.apply(hotPost)),
        ),
      );
      return;
    }

    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('已进入「$title」功能'),
        duration: const Duration(seconds: 1),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  /// 真实点赞微互动联动后端并同步 CommunityInteractionStore
  Future<void> _handleLikeToggle() async {
    final post = _hotPost;
    if (post == null || _interactions.likePending(post.id)) return;
    final confirmed = await _interactions.toggleLike(post);
    if (!confirmed && mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('点赞保存失败，请重试')));
    }
  }

  Future<void> _handleLogout() async {
    await widget.authService.logout();
    if (!mounted) return;
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
    Navigator.of(context).pushReplacement(
      PageRouteBuilder(
        transitionDuration: reduceMotion
            ? Duration.zero
            : const Duration(milliseconds: 300),
        reverseTransitionDuration: reduceMotion
            ? Duration.zero
            : const Duration(milliseconds: 300),
        pageBuilder: (context, anim, secAnim) =>
            LoginPage(authService: widget.authService),
        transitionsBuilder: (context, animation, secAnim, child) => reduceMotion
            ? child
            : FadeTransition(opacity: animation, child: child),
      ),
    );
  }

  /// 「我的」快捷账号弹层（支持退出登录与账号信息预览）
  void _showMyProfileSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        padding: const EdgeInsets.all(24),
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                UserAvatar(
                  name: widget.session.displayName.isNotEmpty
                      ? widget.session.displayName
                      : '同学',
                  avatarPath: widget.session.avatarPath,
                  size: 48,
                  baseUrl: widget.authService.client.baseUrl,
                  gradientColors: const [Color(0xFFFB7185), Color(0xFFE11D48)],
                  fontSize: 20,
                ),
                const SizedBox(width: 14),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.session.displayName.isNotEmpty
                          ? widget.session.displayName
                          : '同学',
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: Color(0xFF0F172A),
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '账号: ${widget.session.username} · 绵阳城市学院',
                      style: const TextStyle(
                        fontSize: 12,
                        color: Color(0xFF64748B),
                      ),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: () {
                Navigator.of(ctx).pop();
                _handleLogout();
              },
              icon: const Icon(Icons.logout_rounded, size: 18),
              label: const Text('退出当前账号并返回登录页'),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFFF1F5F9),
                foregroundColor: const Color(0xFFE11D48),
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
                padding: const EdgeInsets.symmetric(vertical: 12),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;
    final isDesktop = size.width > 600;
    _ensureTab(_currentTabIndex);

    final tabChildren = <Widget>[
      _buildHomeTab(),
      for (int i = 1; i < 5; i++) _tabPages[i] ?? const SizedBox.shrink(),
    ];

    return Scaffold(
      backgroundColor: isDesktop
          ? const Color(0xFFF1F5F9)
          : AppColors.background,
      body: Center(
        child: Container(
          width: isDesktop ? 375 : double.infinity,
          height: isDesktop ? 812 : double.infinity,
          clipBehavior: isDesktop ? Clip.antiAlias : Clip.none,
          decoration: BoxDecoration(
            color: AppColors.background,
            borderRadius: isDesktop
                ? BorderRadius.circular(24)
                : BorderRadius.zero,
            border: isDesktop
                ? Border.all(color: AppColors.border, width: 1)
                : null,
          ),
          child: Column(
            children: [
              Expanded(
                child: IndexedStack(
                  index: _currentTabIndex,
                  children: tabChildren,
                ),
              ),
              _buildBottomTabBar(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHomeTab() {
    final announcement = _announcement;
    final fallbackName = widget.session.displayName.isNotEmpty
        ? widget.session.displayName
        : '同学';
    final greeting = _dashboardData?.greeting.isNotEmpty == true
        ? _dashboardData!.greeting
        : '你好，$fallbackName';
    final academic = _dashboardData?.academicInfo?.formattedDate;
    final hasAnnouncement =
        announcement != null && announcement.title.isNotEmpty;
    return RefreshIndicator(
      color: AppColors.brand,
      backgroundColor: Colors.white,
      onRefresh: _fetchHomeData,
      child: SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(
          parent: BouncingScrollPhysics(),
        ),
        padding: EdgeInsets.zero,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            HomeCampusHeader(
              greeting: greeting,
              date: academic?.isNotEmpty == true ? academic! : '发现校园里的每一种可能',
              unreadCount: _dashboardData?.unreadMessageCount ?? 0,
              onSearch: () => _handleServiceTap('全局搜索'),
              onNotifications: () => _handleServiceTap('消息通知'),
              onAi: () => _switchTab(2),
            ),
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Transform.translate(
                offset: const Offset(0, -22),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    HomeSectionStatus(
                      title: '课程与待办',
                      loading: _home.dashboard.loading,
                      failed: _home.dashboard.failed,
                      hasData: _dashboardData != null,
                      onRetry: _home.reloadDashboard,
                    ),
                    if (_dashboardData != null) _buildScheduleCapsule(),
                  ],
                ),
              ),
            ),
            if (_home.announcements.failed && announcement == null)
              HomeSectionStatus(
                title: '公告',
                loading: false,
                failed: true,
                hasData: false,
                onRetry: _home.reloadAnnouncements,
              )
            else if (hasAnnouncement)
              _buildBroadcastBar(),
            if (_dashboardData?.inProgressRequest != null) ...[
              const SizedBox(height: 6),
              _buildPendingActionCard(),
            ],
            const SizedBox(height: 3),
            _buildSectionHeader(
              title: '校园服务台',
              actionText: '全部服务',
              onAction: () => _handleServiceTap('服务大厅'),
            ),
            HomeSectionStatus(
              title: '常用服务',
              loading: _home.services.loading,
              failed: _home.services.failed,
              hasData: _home.services.data != null,
              onRetry: _home.reloadServices,
            ),
            if (!_servicesLoading && _serviceItems.isNotEmpty)
              HomeServiceHub(
                services: _serviceItems,
                isPinned: _home.isPinned,
                onTogglePin: _toggleServicePin,
                onOpen: _handleServiceItemTap,
                onOpenAll: () => _handleServiceTap('服务大厅'),
              )
            else
              _buildServiceGrid(),
            if (_bannerAd != null) ...[
              const SizedBox(height: 20),
              _buildHomeBannerAd(_bannerAd!),
            ],
            const SizedBox(height: 1),
            ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 40),
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppSpacing.homeHorizontal,
                ),
                child: Row(
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: Image.asset(
                        'assets/images/home/campus-life-editorial-scene.jpg',
                        width: 34,
                        height: 28,
                        fit: BoxFit.cover,
                        alignment: Alignment.centerRight,
                      ),
                    ),
                    const SizedBox(width: 9),
                    const Expanded(
                      child: Text(
                        '此刻，校园里',
                        style: TextStyle(
                          fontSize: AppTypography.navTitleSize,
                          fontWeight: FontWeight.w700,
                          height: 1.35,
                        ),
                      ),
                    ),
                    TextButton(
                      onPressed: () => _switchTab(1),
                      style: TextButton.styleFrom(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                      ),
                      child: const Text('去社区看看 ›'),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 2),
            HomeSectionStatus(
              title: '校园动态',
              loading: _home.posts.loading,
              failed: _home.posts.failed,
              hasData: _home.posts.data != null,
              onRetry: _home.reloadPosts,
            ),
            if (_home.posts.data != null) _buildTrendingCard(),
          ],
        ),
      ),
    );
  }

  Future<void> _toggleServicePin(String id) async {
    final saved = await _home.togglePin(id);
    if (!mounted) return;
    _showAdSnack(
      saved ? (_home.isPinned(id) ? '已固定到常用服务前面' : '已取消固定') : '保存失败，已恢复原顺序',
    );
  }

  Widget _buildBroadcastBar() {
    final announcement = _announcement!;
    final noticeText = announcement.title.startsWith('【')
        ? announcement.title
        : '${announcement.tag} · ${announcement.title}';
    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.homeHorizontal,
      ),
      child: Semantics(
        button: true,
        label: '校园公告：$noticeText',
        child: InkWell(
          onTap: () => _handleServiceTap('校园公告'),
          borderRadius: BorderRadius.circular(10),
          child: Container(
            constraints: const BoxConstraints(
              minHeight: AppSpacing.preferredTapTarget,
            ),
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.cardGroupGap,
              vertical: 10,
            ),
            decoration: BoxDecoration(
              color: AppColors.brandSoft.withValues(alpha: 0.65),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(
              children: [
                const Icon(
                  Icons.campaign_outlined,
                  size: 18,
                  color: AppColors.brand,
                ),
                const SizedBox(width: AppSpacing.compactAvatarGap),
                Expanded(
                  child: Text(
                    noticeText,
                    style: const TextStyle(
                      fontSize: AppTypography.metaSize,
                      color: AppColors.textBody,
                      height: AppTypography.summaryLineHeight,
                    ),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                const Icon(
                  Icons.chevron_right_rounded,
                  size: 18,
                  color: AppColors.textSecondary,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// 首页 BANNER 广告卡：复用社区广告卡样式（推广标识 + 品牌 + CTA）。
  Widget _buildHomeBannerAd(CommunityAd ad) {
    return Center(
      child: CommunityAdCard(
        ad: ad,
        onTap: () => _openBannerAd(ad),
        onCtaTap: () => _openBannerAd(ad),
      ),
    );
  }

  Widget _buildScheduleCapsule() => HomeCourseCard(
    course: _dashboardData?.nextSchedule,
    onOpen: () => _handleServiceTap('完整课表'),
  );

  Widget _buildSectionHeader({
    required String title,
    required String actionText,
    Color? actionColor,
    required VoidCallback onAction,
  }) {
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 40),
      child: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.homeHorizontal,
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(
                title,
                style: const TextStyle(
                  fontSize: AppTypography.navTitleSize,
                  fontWeight: FontWeight.w700,
                  height: 1.35,
                ),
              ),
            ),
            InkWell(
              onTap: onAction,
              borderRadius: BorderRadius.circular(12),
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 4,
                  vertical: 10,
                ),
                child: Row(
                  children: [
                    Text(
                      actionText,
                      style: TextStyle(
                        color: actionColor ?? AppColors.textSecondary,
                        fontSize: AppTypography.metaSize,
                      ),
                    ),
                    Icon(
                      Icons.chevron_right_rounded,
                      size: 16,
                      color: actionColor ?? AppColors.textSecondary,
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 服务金刚区：后端服务目录前 7 项 + 固定「更多服务」；
  /// 图标/配色仅作为渲染映射，不承载业务数据。
  Widget _buildServiceGrid() {
    if (_servicesLoading) {
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Column(
          children: [
            _buildServiceGridRow(
              List.generate(4, (_) => _buildServiceSkeletonTile()),
            ),
            const SizedBox(height: 12),
            _buildServiceGridRow(
              List.generate(4, (_) => _buildServiceSkeletonTile()),
            ),
          ],
        ),
      );
    }
    if (_serviceItems.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 18),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFE2E8F0)),
          ),
          child: Column(
            children: [
              const Icon(
                Icons.apps_rounded,
                size: 30,
                color: Color(0xFF64748B),
              ),
              const SizedBox(height: 8),
              const Text(
                '暂无常用服务',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: AppColors.textPrimary,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                _home.services.failed ? '服务目录加载失败，请重试' : '当前没有上线的服务',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: AppColors.textSecondary,
                ),
              ),
              const SizedBox(height: 8),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  TextButton(
                    onPressed: _home.reloadServices,
                    child: const Text('重新加载'),
                  ),
                  const SizedBox(width: 8),
                  TextButton(
                    onPressed: () => _handleServiceTap('更多服务'),
                    child: const Text('更多服务 ›'),
                  ),
                ],
              ),
            ],
          ),
        ),
      );
    }

    final tiles = <Widget>[];
    final display = _serviceItems.take(7).toList();
    for (var i = 0; i < display.length; i++) {
      final item = display[i];
      final visual = _serviceVisual(item);
      tiles.add(
        _buildServiceTile(
          title: HomeDashboardController.shortLabel(item),
          semanticLabel: item.name,
          pinned: _home.isPinned(item.itemId),
          onLongPress: () async {
            final saved = await _home.togglePin(item.itemId);
            if (!mounted) return;
            _showAdSnack(
              saved
                  ? (_home.isPinned(item.itemId) ? '已固定到常用服务前面' : '已取消固定')
                  : '保存失败，已恢复原顺序',
            );
          },
          icon: visual.icon,
          iconColor: visual.iconColor,
          index: i,
          onTap: () => _handleServiceItemTap(item),
        ),
      );
    }
    tiles.add(
      _buildServiceTile(
        title: '更多服务',
        icon: Icons.grid_view_rounded,
        iconColor: AppColors.textBody,
        index: tiles.length,
        onTap: () => _handleServiceTap('更多服务'),
      ),
    );
    while (tiles.length % 4 != 0) {
      tiles.add(const SizedBox.shrink());
    }
    final rows = <Widget>[];
    for (var i = 0; i < tiles.length; i += 4) {
      if (i > 0) rows.add(const SizedBox(height: 10));
      rows.add(_buildServiceGridRow(tiles.sublist(i, i + 4)));
    }
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Column(children: rows),
    );
  }

  Widget _buildServiceGridRow(List<Widget> children) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var i = 0; i < children.length; i++) ...[
          if (i > 0) const SizedBox(width: 10),
          Expanded(child: children[i]),
        ],
      ],
    );
  }

  Widget _buildServiceSkeletonTile() {
    return SizedBox(
      width: double.infinity,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: const Color(0xFFE2E8F0),
              borderRadius: BorderRadius.circular(14),
            ),
          ),
          const SizedBox(height: 8),
          Container(
            width: 40,
            height: 8,
            decoration: BoxDecoration(
              color: const Color(0xFFE2E8F0),
              borderRadius: BorderRadius.circular(4),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildServiceTile({
    required String title,
    required IconData icon,
    required Color iconColor,
    required int index,
    required VoidCallback onTap,
    VoidCallback? onLongPress,
    String? semanticLabel,
    bool pinned = false,
  }) {
    return Semantics(
      button: true,
      label:
          '${semanticLabel ?? title}${onLongPress == null ? '' : '，长按可固定或取消固定'}',
      selected: pinned,
      onLongPress: onLongPress,
      child: Tooltip(
        message: onLongPress == null ? title : '$title\n长按固定或取消固定',
        child: InkWell(
          key: ValueKey('home-service-$index'),
          onTap: onTap,
          onLongPress: onLongPress,
          borderRadius: BorderRadius.circular(14),
          child: Container(
            width: double.infinity,
            constraints: const BoxConstraints(minHeight: 88),
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 10),
            decoration: BoxDecoration(
              color: AppColors.card,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: AppColors.divider),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              mainAxisSize: MainAxisSize.min,
              children: [
                SizedBox(
                  width: 40,
                  height: 32,
                  child: Stack(
                    clipBehavior: Clip.none,
                    children: [
                      Center(child: Icon(icon, size: 26, color: iconColor)),
                      if (pinned)
                        Positioned(
                          right: -2,
                          top: -4,
                          child: Icon(
                            Icons.push_pin_rounded,
                            size: 13,
                            color: iconColor,
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    color: AppColors.textBody,
                    height: 1.4,
                  ),
                  textAlign: TextAlign.center,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// 服务项点击路由：REPAIR-* 报修、SCHEDULE-VIEW 课表、GUIDE-CONSULT 咨询，
  /// 其余（AFFAIR / CERTIFICATE / 未知编码）统一进入服务申请页兜底。
  void _handleServiceItemTap(ServiceItemData item) =>
      HomeNavigation.openService(context, item, onOpenAi: () => _switchTab(2));

  _ServiceVisual _serviceVisual(ServiceItemData item) {
    const repair = _ServiceVisual(Icons.handyman_outlined, Color(0xFFB45309));
    const affairs = _ServiceVisual(
      Icons.description_outlined,
      AppColors.accentDeep,
    );
    const certificate = _ServiceVisual(
      Icons.workspace_premium_outlined,
      Color(0xFF7C3AED),
    );
    return switch (item.itemCode) {
      'SCHEDULE-VIEW' => const _ServiceVisual(
        Icons.calendar_month_outlined,
        AppColors.brand,
      ),
      'REPAIR-DORM' => repair,
      'REPAIR-PUBLIC' => const _ServiceVisual(
        Icons.meeting_room_outlined,
        AppColors.accentDeep,
      ),
      'AFFAIR-LEAVE' => affairs,
      'AFFAIR-VENUE' => const _ServiceVisual(
        Icons.location_on_outlined,
        Color(0xFF0284C7),
      ),
      'CERT-ENROLL' => certificate,
      'GUIDE-CONSULT' => const _ServiceVisual(
        Icons.chat_bubble_outline_rounded,
        Color(0xFFBE123C),
      ),
      _ => switch (item.categoryCode) {
        'REPAIR' => repair,
        'AFFAIR' => affairs,
        'CERTIFICATE' => certificate,
        'GUIDE' => const _ServiceVisual(
          Icons.chat_bubble_outline_rounded,
          Color(0xFFBE123C),
        ),
        'APP' => const _ServiceVisual(
          Icons.calendar_month_outlined,
          AppColors.brand,
        ),
        _ => const _ServiceVisual(Icons.apps_rounded, AppColors.textBody),
      },
    };
  }

  Widget _buildTrendingCard() {
    final posts = _home.posts.data ?? const <CommunityPost>[];
    final currentPost = _hotPost == null ? null : _interactions.apply(_hotPost!);
    return HomeCommunityPreview(
      post: currentPost,
      morePosts: posts.skip(1).take(2).map(_interactions.apply).toList(),
      baseUrl: widget.authService.client.baseUrl,
      liked: currentPost?.isLiked ?? false,
      likeCount: currentPost?.likeCount ?? 0,
      likePending: currentPost != null && _interactions.likePending(currentPost.id),
      onOpen: currentPost == null
          ? () => _switchTab(1)
          : () => _handleServiceTap('讨论详情'),
      onOpenPost: (post) => Navigator.of(context)
          .push(MaterialPageRoute(builder: (_) => PostDetailPage(post: post))),
      onLike: _handleLikeToggle,
    );
  }

  Widget _buildPendingActionCard() {
    final inProgress = _dashboardData?.inProgressRequest;
    return HomeActionCard(
      request: inProgress,
      onOpen: () => _handleServiceTap(inProgress == null ? '教务学籍认证' : '进行中工单'),
    );
  }

  /// 全局搜索弹层
  void _showSearchModal() {
    final controller = TextEditingController();
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) {
          final query = controller.text.trim();
          final lower = query.toLowerCase();

          final matchedServices = query.isEmpty
              ? const <ServiceItemData>[]
              : _serviceItems.where((item) {
                  final shortLabel = HomeDashboardController.shortLabel(item);
                  return item.name.toLowerCase().contains(lower) ||
                      shortLabel.toLowerCase().contains(lower) ||
                      item.summary.toLowerCase().contains(lower);
                }).toList();

          final nextCourse = _dashboardData?.nextSchedule;
          final matchesSchedule = query.isNotEmpty &&
              (lower.contains('课表') ||
                  lower.contains('课程') ||
                  lower.contains('上课') ||
                  (nextCourse != null &&
                      (nextCourse.courseName.toLowerCase().contains(lower) ||
                          (nextCourse.location ?? '')
                              .toLowerCase()
                              .contains(lower) ||
                          (nextCourse.teacher ?? '')
                              .toLowerCase()
                              .contains(lower))));

          final allAnnouncements = <HomeAnnouncementModel>[
            if (_announcement != null) _announcement!,
            for (final item
                in _home.announcements.data ?? const <HomeAnnouncementModel>[])
              if (_announcement?.id != item.id) item,
          ];
          final matchedAnnouncements = query.isEmpty
              ? const <HomeAnnouncementModel>[]
              : allAnnouncements.where((ann) {
                  return ann.title.toLowerCase().contains(lower) ||
                      ann.tag.toLowerCase().contains(lower) ||
                      (ann.summary ?? '').toLowerCase().contains(lower);
                }).toList();

          final matchedPosts = query.isEmpty
              ? const <CommunityPost>[]
              : (_home.posts.data ?? const <CommunityPost>[]).where((post) {
                  return post.title.toLowerCase().contains(lower) ||
                      post.content.toLowerCase().contains(lower) ||
                      post.authorName.toLowerCase().contains(lower);
                }).toList();

          void handleSubmit(String raw) {
            final text = raw.trim();
            if (text.isEmpty) return;
            Navigator.of(ctx).pop();
            if (matchedServices.length == 1 &&
                matchedAnnouncements.isEmpty &&
                matchedPosts.isEmpty &&
                !matchesSchedule) {
              _handleServiceItemTap(matchedServices.first);
              return;
            }
            _openCommunityWithKeyword(text);
          }

          return Material(
            color: Colors.white,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
            clipBehavior: Clip.antiAlias,
            child: SizedBox(
              height: MediaQuery.of(context).size.height * 0.68,
              child: Padding(
                padding: EdgeInsets.fromLTRB(
                  20,
                  20,
                  20,
                  20 + MediaQuery.of(ctx).viewInsets.bottom,
                ),
                child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 36,
                    height: 4,
                    decoration: BoxDecoration(
                      color: const Color(0xFFCBD5E1),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                const Text(
                  '校园全能搜索',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                ),
                const SizedBox(height: 14),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF1F5F9),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    children: [
                      const Icon(
                        Icons.search_rounded,
                        size: 20,
                        color: Color(0xFF64748B),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: TextField(
                          key: const ValueKey('home-global-search-input'),
                          controller: controller,
                          autofocus: true,
                          textInputAction: TextInputAction.search,
                          onChanged: (_) => setModalState(() {}),
                          onSubmitted: handleSubmit,
                          decoration: const InputDecoration(
                            hintText: '搜索办事服务、课表、通知、帖子...',
                            hintStyle: TextStyle(
                              fontSize: 13,
                              color: Color(0xFF64748B),
                            ),
                            filled: false,
                            border: InputBorder.none,
                          ),
                        ),
                      ),
                      if (query.isNotEmpty)
                        GestureDetector(
                          key: const ValueKey('home-global-search-clear'),
                          onTap: () {
                            controller.clear();
                            setModalState(() {});
                          },
                          child: const Icon(
                            Icons.close_rounded,
                            size: 18,
                            color: Color(0xFF94A3B8),
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                Expanded(
                  child: query.isEmpty
                      ? SingleChildScrollView(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                '快捷推荐',
                                style: TextStyle(
                                  fontFamily: 'Noto Sans SC',
                                  fontSize: 13,
                                  fontWeight: FontWeight.w600,
                                  color: Color(0xFF64748B),
                                ),
                              ),
                              const SizedBox(height: 10),
                              Wrap(
                                spacing: 8,
                                runSpacing: 8,
                                children: [
                                  _buildSearchTag(ctx, '宿舍报修', () {
                                    Navigator.of(ctx).pop();
                                    _handleServiceTap('宿舍报修');
                                  }),
                                  _buildSearchTag(ctx, '我的课表', () {
                                    Navigator.of(ctx).pop();
                                    _handleServiceTap('我的课表');
                                  }),
                                  _buildSearchTag(ctx, '日常请假', () {
                                    Navigator.of(ctx).pop();
                                    _handleServiceTap('请假申请');
                                  }),
                                  _buildSearchTag(ctx, '在读证明', () {
                                    Navigator.of(ctx).pop();
                                    _handleServiceTap('在读证明');
                                  }),
                                  _buildSearchTag(ctx, '教室报修', () {
                                    Navigator.of(ctx).pop();
                                    _handleServiceTap('教室报修');
                                  }),
                                  _buildSearchTag(ctx, '校园AI助手', () {
                                    Navigator.of(ctx).pop();
                                    _switchTab(2);
                                  }),
                                ],
                              ),
                            ],
                          ),
                        )
                      : ListView(
                          padding: EdgeInsets.zero,
                          children: [
                            for (final item in matchedServices)
                              ListTile(
                                key: ValueKey(
                                  'home-search-service-${item.itemCode}',
                                ),
                                dense: true,
                                contentPadding: const EdgeInsets.symmetric(
                                  horizontal: 4,
                                ),
                                leading: const Icon(
                                  Icons.apps_rounded,
                                  size: 20,
                                  color: AppColors.brand,
                                ),
                                title: Text(
                                  item.name,
                                  style: const TextStyle(
                                    fontSize: 14,
                                    fontWeight: FontWeight.w600,
                                    color: AppColors.textPrimary,
                                  ),
                                ),
                                subtitle: item.summary.isNotEmpty
                                    ? Text(
                                        item.summary,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(
                                          fontSize: 12,
                                          color: AppColors.textSecondary,
                                        ),
                                      )
                                    : null,
                                trailing: const Text(
                                  '去办理 ›',
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: AppColors.brand,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                                onTap: () {
                                  Navigator.of(ctx).pop();
                                  _handleServiceItemTap(item);
                                },
                              ),
                            if (matchesSchedule)
                              ListTile(
                                key: const ValueKey('home-search-schedule'),
                                dense: true,
                                contentPadding: const EdgeInsets.symmetric(
                                  horizontal: 4,
                                ),
                                leading: const Icon(
                                  Icons.calendar_month_rounded,
                                  size: 20,
                                  color: AppColors.brand,
                                ),
                                title: Text(
                                  nextCourse != null
                                      ? '课表 · ${nextCourse.courseName}'
                                      : '我的课表',
                                  style: const TextStyle(
                                    fontSize: 14,
                                    fontWeight: FontWeight.w600,
                                    color: AppColors.textPrimary,
                                  ),
                                ),
                                subtitle: nextCourse != null
                                    ? Text(
                                        nextCourse.displayText,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(
                                          fontSize: 12,
                                          color: AppColors.textSecondary,
                                        ),
                                      )
                                    : null,
                                trailing: const Text(
                                  '查看课表 ›',
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: AppColors.brand,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                                onTap: () {
                                  Navigator.of(ctx).pop();
                                  _handleServiceTap('我的课表');
                                },
                              ),
                            for (final ann in matchedAnnouncements)
                              ListTile(
                                key: ValueKey('home-search-ann-${ann.id}'),
                                dense: true,
                                contentPadding: const EdgeInsets.symmetric(
                                  horizontal: 4,
                                ),
                                leading: const Icon(
                                  Icons.campaign_rounded,
                                  size: 20,
                                  color: Color(0xFFF59E0B),
                                ),
                                title: Text(
                                  ann.title,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(
                                    fontSize: 14,
                                    fontWeight: FontWeight.w600,
                                    color: AppColors.textPrimary,
                                  ),
                                ),
                                subtitle: Text(
                                  '校园公告 · ${ann.tag}',
                                  style: const TextStyle(
                                    fontSize: 12,
                                    color: AppColors.textSecondary,
                                  ),
                                ),
                                trailing: const Icon(
                                  Icons.chevron_right_rounded,
                                  size: 18,
                                  color: AppColors.textSecondary,
                                ),
                                onTap: () {
                                  Navigator.of(ctx).pop();
                                  _showAnnouncementModal(ann);
                                },
                              ),
                            for (final post in matchedPosts)
                              ListTile(
                                key: ValueKey('home-search-post-${post.id}'),
                                dense: true,
                                contentPadding: const EdgeInsets.symmetric(
                                  horizontal: 4,
                                ),
                                leading: const Icon(
                                  Icons.forum_outlined,
                                  size: 20,
                                  color: AppColors.brand,
                                ),
                                title: Text(
                                  post.title,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(
                                    fontSize: 14,
                                    fontWeight: FontWeight.w600,
                                    color: AppColors.textPrimary,
                                  ),
                                ),
                                subtitle: Text(
                                  '${post.authorName} · ${post.commentCount} 条讨论',
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(
                                    fontSize: 12,
                                    color: AppColors.textSecondary,
                                  ),
                                ),
                                trailing: const Icon(
                                  Icons.chevron_right_rounded,
                                  size: 18,
                                  color: AppColors.textSecondary,
                                ),
                                onTap: () {
                                  Navigator.of(ctx).pop();
                                  Navigator.of(context).push(
                                    MaterialPageRoute(
                                      builder: (_) =>
                                          PostDetailPage(post: post),
                                    ),
                                  );
                                },
                              ),
                            const Divider(height: 16),
                            ListTile(
                              key: const ValueKey('home-search-in-community'),
                              dense: true,
                              contentPadding: const EdgeInsets.symmetric(
                                horizontal: 4,
                              ),
                              leading: const Icon(
                                Icons.manage_search_rounded,
                                size: 20,
                                color: AppColors.brand,
                              ),
                              title: Text(
                                '在社区中搜索“$query”',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontSize: 13,
                                  fontWeight: FontWeight.w600,
                                  color: AppColors.brand,
                                ),
                              ),
                              trailing: const Icon(
                                Icons.arrow_forward_ios_rounded,
                                size: 13,
                                color: AppColors.brand,
                              ),
                              onTap: () {
                                Navigator.of(ctx).pop();
                                _openCommunityWithKeyword(query);
                              },
                            ),
                            ListTile(
                              key: const ValueKey('home-search-ask-ai'),
                              dense: true,
                              contentPadding: const EdgeInsets.symmetric(
                                horizontal: 4,
                              ),
                              leading: const Icon(
                                Icons.auto_awesome_rounded,
                                size: 18,
                                color: AppColors.accentDeep,
                              ),
                              title: Text(
                                '向绵宝AI咨询“$query”',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontSize: 13,
                                  fontWeight: FontWeight.w600,
                                  color: AppColors.accentDeep,
                                ),
                              ),
                              trailing: const Icon(
                                Icons.arrow_forward_ios_rounded,
                                size: 13,
                                color: AppColors.accentDeep,
                              ),
                              onTap: () {
                                Navigator.of(ctx).pop();
                                _switchTab(2);
                              },
                            ),
                          ],
                        ),
                ),
              ],
            ),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildSearchTag(BuildContext ctx, String label, VoidCallback onTap) {
    return _AnimatedPressButton(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: const Color(0xFFF8FAFC),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: const Color(0xFFE2E8F0)),
        ),
        child: Text(
          label,
          style: const TextStyle(fontSize: 12, color: AppColors.textBody),
        ),
      ),
    );
  }

  /// 公告详情页跳转（附带已知公告初始信息与「全部公告」入口）
  void _showAnnouncementModal(HomeAnnouncementModel ann) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => AnnouncementDetailPage(
          id: ann.id,
          initialAnnouncement: ann,
          showAllAnnouncementsAction: true,
        ),
      ),
    );
  }

  Widget _buildBottomTabBar() => AppBottomNavigation(
    currentIndex: _currentTabIndex,
    onDestinationSelected: _switchTab,
    unreadCount: _dashboardData?.unreadMessageCount ?? 0,
  );
}

/// 动效微交互包装组件：点击轻微下压回弹 (Scale press bounce)
class _AnimatedPressButton extends StatefulWidget {
  final Widget child;
  final VoidCallback onTap;

  const _AnimatedPressButton({required this.child, required this.onTap});

  @override
  State<_AnimatedPressButton> createState() => _AnimatedPressButtonState();
}

class _AnimatedPressButtonState extends State<_AnimatedPressButton> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => setState(() => _isPressed = true),
      onTapUp: (_) {
        setState(() => _isPressed = false);
        widget.onTap();
      },
      onTapCancel: () => setState(() => _isPressed = false),
      behavior: HitTestBehavior.opaque,
      child: AnimatedScale(
        scale: MediaQuery.disableAnimationsOf(context)
            ? 1
            : (_isPressed ? 0.95 : 1.0),
        duration: MediaQuery.disableAnimationsOf(context)
            ? Duration.zero
            : const Duration(milliseconds: 120),
        curve: Curves.easeInOut,
        child: widget.child,
      ),
    );
  }
}

class _ServiceVisual {
  final IconData icon;
  final Color iconColor;

  const _ServiceVisual(this.icon, this.iconColor);
}
