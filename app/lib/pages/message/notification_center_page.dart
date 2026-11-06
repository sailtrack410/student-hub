import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/message_api.dart';
import '../../models/community_models.dart';
import '../../models/message_models.dart';
import '../../services/community_socket.dart';
import '../../services/message_state_events.dart';
import '../../theme/app_theme.dart';
import '../../widgets/skeleton_shimmer.dart';
import '../assistant/ai_assistant_page.dart';
import '../community/chat_detail_page.dart';
import 'announcement_list_page.dart';
import 'ai_todo_page.dart';
import 'community_notification_list_page.dart';

/// APP-消息中心 (画布 5:2433)
/// 统一收件箱：流程通知折叠 + 私信 + AI 会话；四类快捷入口 + 全部已读。
class NotificationCenterPage extends StatefulWidget {
  final int initialTabIndex;

  /// 作为一级 Tab 嵌入首页时传 true：隐藏返回键，由首页统一提供底栏
  final bool embedded;

  /// 点击 AI 会话时切到 AI Tab（嵌入首页时由 HomePage 注入）
  final VoidCallback? onOpenAi;

  /// 已读状态变化后通知首页刷新未读角标
  final VoidCallback? onUnreadChanged;

  /// 测试/预览注入
  final List<InboxItem>? initialItems;
  final MessageCategorySummary? initialSummary;

  const NotificationCenterPage({
    super.key,
    this.initialTabIndex = 0,
    this.embedded = false,
    this.onOpenAi,
    this.onUnreadChanged,
    this.initialItems,
    this.initialSummary,
  });

  @override
  State<NotificationCenterPage> createState() => _NotificationCenterPageState();
}

class _NotificationCenterPageState extends State<NotificationCenterPage>
    with WidgetsBindingObserver {
  static const List<_QuickEntry> _quickEntries = [
    _QuickEntry(
      key: 'process',
      label: '流程办理',
      icon: Icons.assignment_turned_in_rounded,
      iconColor: Color(0xFF2563EB),
      bgColor: Color(0xFFEFF6FF),
    ),
    _QuickEntry(
      key: 'interaction',
      label: '互动提醒',
      icon: Icons.favorite_rounded,
      iconColor: Color(0xFF10B981),
      bgColor: Color(0xFFECFDF5),
    ),
    _QuickEntry(
      key: 'announcement',
      label: '校园公告',
      icon: Icons.campaign_rounded,
      iconColor: Color(0xFFF59E0B),
      bgColor: Color(0xFFFFFBEB),
    ),
    _QuickEntry(
      key: 'ai',
      label: 'AI待办',
      icon: Icons.auto_awesome_rounded,
      iconColor: Color(0xFF7C3AED),
      bgColor: Color(0xFFF5F3FF),
    ),
  ];

  List<InboxItem> _items = [];
  MessageCategorySummary _summary = const MessageCategorySummary();
  bool _loading = true;
  bool _error = false;
  bool _loadingMore = false;
  bool _hasMore = true;
  int _pageNum = 1;
  String? _filter;
  String _keyword = '';
  final ScrollController _scrollController = ScrollController();
  StreamSubscription<DirectMessage>? _socketSubscription;
  StreamSubscription<AppNotificationEvent>? _notificationSubscription;

  StreamSubscription<void>? _unreadSubscription;
  StreamSubscription<bool>? _statusSubscription;
  int _loadGeneration = 0;
  int _loadedRecords = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    if (widget.initialItems != null) {
      _items = widget.initialItems!.where((item) => !item.isAi).toList();
      _summary = widget.initialSummary ?? const MessageCategorySummary();
      _loading = false;
      _hasMore = false;
    } else {
      _scrollController.addListener(_onScroll);
      _load(reset: true);
      _socketSubscription = CommunitySocket.shared.messages.listen((_) {
        if (!mounted) return;
        widget.onUnreadChanged?.call();
        _load(reset: true);
      });
      _notificationSubscription = CommunitySocket.shared.notificationEvents
          .listen((_) {
            if (!mounted) return;
            widget.onUnreadChanged?.call();
            _load(reset: true);
          });
      _unreadSubscription = MessageStateEvents.changes.listen(
        (_) => _load(reset: true),
      );
      _statusSubscription = CommunitySocket.shared.connectionStatus.listen((
        connected,
      ) {
        if (connected) _load(reset: true);
      });
      CommunitySocket.shared.connect();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _unreadSubscription?.cancel();
    _statusSubscription?.cancel();
    _loadGeneration++;
    _socketSubscription?.cancel();
    _notificationSubscription?.cancel();
    _scrollController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && widget.initialItems == null) {
      _load(reset: true);
    }
  }

  Future<void> _load({bool reset = false}) async {
    if (!reset && (_loading || _loadingMore || !_hasMore)) return;
    final generation = ++_loadGeneration;
    if (reset) {
      _pageNum = 1;
      _loadedRecords = 0;
      _hasMore = true;
      if (_items.isEmpty && mounted) {
        setState(() => _loading = true);
      }
    } else {
      if (_loadingMore || !_hasMore) return;
      setState(() => _loadingMore = true);
    }

    final page = await MessageApi.fetchInbox(pageNum: _pageNum, pageSize: 20);
    final summary = await MessageApi.fetchCategorySummary();

    if (!mounted || generation != _loadGeneration) return;
    if (page == null) {
      setState(() {
        _loading = false;
        _loadingMore = false;
        if (_items.isEmpty) _error = true;
      });
      return;
    }

    setState(() {
      _error = false;
      _loading = false;
      _loadingMore = false;
      if (reset) {
        _items = page.items.where((item) => !item.isAi).toList();
      } else {
        final known = _items.map((e) => e.sessionId).toSet();
        _items.addAll(
          page.items.where((e) => !e.isAi && known.add(e.sessionId)),
        );
      }
      _loadedRecords += page.items.length;
      _hasMore = page.items.isNotEmpty && _loadedRecords < page.total;
      if (_hasMore) _pageNum++;
      if (summary != null) _summary = summary;
    });
  }

  void _onScroll() {
    if (!_scrollController.hasClients) return;
    final position = _scrollController.position;
    if (position.pixels >= position.maxScrollExtent - 240) {
      _load();
    }
  }

  List<InboxItem> get _visibleItems {
    final keyword = _keyword.trim().toLowerCase();
    return _items.where((item) {
      if (_filter == 'process' && !item.isProcess) return false;
      if (_filter == 'interaction' && !(item.isDirect || item.isCommunity)) {
        return false;
      }
      if (_filter == 'announcement') return false;
      if (_filter == 'ai' && !item.isAi) return false;
      if (keyword.isNotEmpty) {
        final text = '${item.title}${item.lastContent}'.toLowerCase();
        if (!text.contains(keyword)) return false;
      }
      return true;
    }).toList();
  }

  int _unreadForFilter(String key) {
    switch (key) {
      case 'process':
        return _summary.processNoticeUnread;
      case 'interaction':
        return _summary.interactionUnread;
      case 'announcement':
        return _summary.announcementUnread;
      case 'ai':
        return _summary.aiTodoCount;
      default:
        return 0;
    }
  }

  Future<void> _markAllRead() async {
    final result = widget.initialItems != null
        ? const MessageCategorySummary()
        : await MessageApi.markAllRead();
    final ok = result != null;
    if (!mounted) return;
    if (ok) {
      setState(() {
        _items = _items
            .map(
              (e) => InboxItem(
                sessionId: e.sessionId,
                sessionType: e.sessionType,
                peerId: e.peerId,
                conversationId: e.conversationId,
                title: e.title,
                avatarUrl: e.avatarUrl,
                lastContent: e.lastContent,
                lastTime: e.lastTime,
                unreadCount: 0,
                pinned: e.pinned,
              ),
            )
            .toList();
        _summary = result;
      });
      if (widget.initialItems == null) await _load(reset: true);
      if (!mounted) return;
      widget.onUnreadChanged?.call();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('已将所有未读消息标记为已读'),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      );
    } else {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('标记已读失败，请重试')));
    }
  }

  void _openItem(InboxItem item) {
    if (item.isCommunity) {
      Navigator.of(context)
          .push(
            MaterialPageRoute(
              builder: (_) => const CommunityNotificationListPage(),
            ),
          )
          .then((_) {
            if (!mounted) return;
            widget.onUnreadChanged?.call();
            _load(reset: true);
          });
      return;
    }
    if (item.isProcess) {
      Navigator.of(context)
          .push(
            MaterialPageRoute(
              builder: (_) =>
                  const CommunityNotificationListPage(communityOnly: false),
            ),
          )
          .then((_) {
            if (!mounted) return;
            widget.onUnreadChanged?.call();
            _load(reset: true);
          });
      return;
    }
    if (item.isDirect) {
      Navigator.of(context)
          .push(
            MaterialPageRoute(
              builder: (_) => ChatDetailPage(
                peerId: item.peerId ?? '',
                peerName: item.title,
              ),
            ),
          )
          .then((_) {
            if (!mounted) return;
            widget.onUnreadChanged?.call();
            _load(reset: true);
          });
      return;
    }
    if (item.isAi) {
      if (widget.onOpenAi != null) {
        widget.onOpenAi!();
      } else {
        Navigator.of(context)
            .push(MaterialPageRoute(builder: (_) => const AIAssistantPage()));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final visible = _visibleItems;
    final unreadTotal = _summary.totalUnread;

    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        automaticallyImplyLeading: !widget.embedded,
        leading: widget.embedded
            ? null
            : IconButton(
                icon: const Icon(
                  Icons.arrow_back_ios_new_rounded,
                  size: 20,
                  color: Color(0xFF0F172A),
                ),
                onPressed: () => Navigator.of(context).pop(),
              ),
        title: const Text(
          '消息中心',
          style: TextStyle(
            fontFamily: AppTypography.fontFamily,
            fontSize: AppTypography.navTitleSize,
            fontWeight: AppTypography.navTitleWeight,
            color: AppColors.textPrimary,
          ),
        ),
        centerTitle: false,
        titleSpacing: AppSpacing.pageHorizontal,
        actions: [
          if (unreadTotal > 0)
            TextButton(
              onPressed: _markAllRead,
              child: const Text(
                '全部已读',
                style: TextStyle(
                  fontFamily: AppTypography.fontFamily,
                  fontSize: AppTypography.feedSummarySize,
                  color: AppColors.brand,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
        ],
      ),
      body: Column(
        children: [
          _buildSearchBar(),
          _buildQuickEntries(),
          const Divider(height: 1, thickness: 1, color: AppColors.feedDivider),
          if (_filter != null) _buildFilterTip(),
          Expanded(child: _buildBody(visible)),
        ],
      ),
    );
  }

  Widget _buildSearchBar() {
    return Container(
      color: Colors.white,
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.pageHorizontal,
        8,
        AppSpacing.pageHorizontal,
        10,
      ),
      child: Container(
        height: 38,
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.cardGroupGap),
        decoration: BoxDecoration(
          color: AppColors.chipBg,
          borderRadius: BorderRadius.circular(19),
        ),
        child: Row(
          children: [
            const Icon(
              Icons.search_rounded,
              size: 18,
              color: AppColors.textTertiary,
            ),
            const SizedBox(width: AppSpacing.compactAvatarGap),
            Expanded(
              child: TextField(
                onChanged: (value) => setState(() => _keyword = value),
                style: const TextStyle(
                  fontFamily: AppTypography.fontFamily,
                  fontSize: AppTypography.feedSummarySize,
                ),
                decoration: const InputDecoration(
                  isDense: true,
                  // 关闭主题 filled 白底，避免胶囊内双层底纹
                  filled: false,
                  border: InputBorder.none,
                  hintText: '搜索联系人、服务通知、工单进度...',
                  hintStyle: TextStyle(
                    fontFamily: AppTypography.fontFamily,
                    fontSize: AppTypography.feedSummarySize,
                    color: AppColors.textSecondary,
                  ),
                ),
              ),
            ),
            if (_keyword.isNotEmpty)
              GestureDetector(
                onTap: () => setState(() => _keyword = ''),
                child: const Icon(
                  Icons.close_rounded,
                  size: 16,
                  color: AppColors.textTertiary,
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildQuickEntries() {
    return Container(
      color: Colors.white,
      padding: const EdgeInsets.fromLTRB(12, 2, 12, 12),
      child: Row(
        children: _quickEntries.map((entry) {
          final selected = _filter == entry.key;
          final unread = _unreadForFilter(entry.key);
          return Expanded(
            child: GestureDetector(
              onTap: () {
                if (entry.key == 'announcement' || entry.key == 'ai') {
                  Navigator.of(context)
                      .push(
                        MaterialPageRoute(
                          builder: (_) => entry.key == 'ai'
                              ? const AiTodoPage()
                              : const AnnouncementListPage(),
                        ),
                      )
                      .then((_) {
                        if (mounted) _load(reset: true);
                      });
                  return;
                }
                setState(() => _filter = selected ? null : entry.key);
              },
              behavior: HitTestBehavior.opaque,
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 2),
                padding: const EdgeInsets.symmetric(vertical: 10),
                decoration: BoxDecoration(
                  color: selected
                      ? const Color(0xFFEFF6FF)
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(
                    color: selected
                        ? const Color(0xFFBFDBFE)
                        : Colors.transparent,
                  ),
                ),
                child: Column(
                  children: [
                    Stack(
                      clipBehavior: Clip.none,
                      children: [
                        Container(
                          width: 44,
                          height: 44,
                          decoration: BoxDecoration(
                            color: entry.bgColor,
                            shape: BoxShape.circle,
                          ),
                          child: Icon(
                            entry.icon,
                            size: 22,
                            color: entry.iconColor,
                          ),
                        ),
                        if (unread > 0)
                          Positioned(
                            right: -4,
                            top: -3,
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 5,
                                vertical: 1,
                              ),
                              constraints: const BoxConstraints(minWidth: 16),
                              decoration: BoxDecoration(
                                color: AppColors.danger,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Text(
                                unread > 99 ? '99+' : '$unread',
                                textAlign: TextAlign.center,
                                style: const TextStyle(
                                  fontSize: 12,
                                  height: 1.2,
                                  color: Colors.white,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Flexible(
                          child: Text(
                            entry.label,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 12,
                              fontWeight: selected
                                  ? FontWeight.bold
                                  : FontWeight.w500,
                              color: selected
                                  ? AppColors.brand
                                  : AppColors.textPrimary,
                            ),
                          ),
                        ),
                        if (entry.key == 'announcement' || entry.key == 'ai')
                          const Icon(
                            Icons.chevron_right_rounded,
                            size: 13,
                            color: AppColors.textTertiary,
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }

  Widget _buildFilterTip() {
    return Container(
      width: double.infinity,
      color: const Color(0xFFF1F5F9),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      child: Row(
        children: [
          Text(
            '筛选：${_quickEntries.firstWhere((e) => e.key == _filter).label}',
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: AppColors.textSecondary,
            ),
          ),
          const Spacer(),
          GestureDetector(
            onTap: () => setState(() => _filter = null),
            child: const Text(
              '查看全部',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: AppColors.brand,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBody(List<InboxItem> visible) {
    if (_loading && _items.isEmpty) return _buildSkeleton();
    if (_error && _items.isEmpty) return _buildErrorState();
    if (visible.isEmpty) return _buildEmptyState();
    return RefreshIndicator(
      onRefresh: () => _load(reset: true),
      color: AppColors.brand,
      child: ListView.separated(
        controller: _scrollController,
        physics: const AlwaysScrollableScrollPhysics(
          parent: BouncingScrollPhysics(),
        ),
        padding: const EdgeInsets.only(bottom: 24),
        itemCount: visible.length + (_loadingMore ? 1 : 0),
        separatorBuilder: (_, _) =>
            const Divider(height: 1, thickness: 1, color: AppColors.feedDivider),
        itemBuilder: (context, index) {
          if (index >= visible.length) {
            return const Padding(
              padding: EdgeInsets.symmetric(vertical: AppSpacing.cardGroupGap),
              child: Center(
                child: SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ),
            );
          }
          return _buildInboxCard(visible[index]);
        },
      ),
    );
  }

  Widget _buildInboxCard(InboxItem item) {
    final unread = item.unreadCount > 0;
    return GestureDetector(
      onTap: () => _openItem(item),
      behavior: HitTestBehavior.opaque,
      child: Container(
        color: Colors.white,
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.pageHorizontal,
          vertical: AppSpacing.cardPadding,
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildSessionIcon(item),
            const SizedBox(width: AppSpacing.avatarTextGap),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          item.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontFamily: AppTypography.fontFamily,
                            fontSize: AppTypography.sessionTitleSize,
                            fontWeight: AppTypography.sessionTitleWeight,
                            color: AppColors.textPrimary,
                          ),
                        ),
                      ),
                      if (item.pinned)
                        Container(
                          margin: const EdgeInsets.only(
                            right: AppSpacing.titleSummaryGap,
                          ),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 5,
                            vertical: 1,
                          ),
                          decoration: BoxDecoration(
                            color: AppColors.brandSoft,
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: const Text(
                            '置顶',
                            style: TextStyle(
                              fontSize: AppTypography.badgeSize,
                              color: AppColors.brand,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      Text(
                        _relativeTime(item.lastTime),
                        style: const TextStyle(
                          fontFamily: AppTypography.fontFamily,
                          fontSize: AppTypography.metaSize,
                          color: AppColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.titleSummaryGap),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Text(
                          item.lastContent,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontFamily: AppTypography.fontFamily,
                            fontSize: AppTypography.summarySize,
                            height: AppTypography.summaryLineHeight,
                            color: unread
                                ? AppColors.textBody
                                : AppColors.textSecondary,
                          ),
                        ),
                      ),
                      if (unread) ...[
                        const SizedBox(width: AppSpacing.compactAvatarGap),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 6,
                            vertical: 2,
                          ),
                          constraints: const BoxConstraints(minWidth: 20),
                          decoration: BoxDecoration(
                            color: AppColors.danger,
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Text(
                            item.unreadCount > 99
                                ? '99+'
                                : '${item.unreadCount}',
                            semanticsLabel: '${item.unreadCount} 条未读消息',
                            textAlign: TextAlign.center,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 11,
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSessionIcon(InboxItem item) {
    if (item.isCommunity) {
      return Container(
        width: 44,
        height: 44,
        decoration: const BoxDecoration(
          color: Color(0xFFECFDF5),
          shape: BoxShape.circle,
        ),
        alignment: Alignment.center,
        child: const Icon(
          Icons.favorite_rounded,
          size: 22,
          color: Color(0xFF10B981),
        ),
      );
    }
    if (item.isDirect) {
      final initial = item.title.isNotEmpty ? item.title.substring(0, 1) : '同';
      return Container(
        width: 44,
        height: 44,
        decoration: const BoxDecoration(
          color: Color(0xFFF3E8FF),
          shape: BoxShape.circle,
        ),
        alignment: Alignment.center,
        child: Text(
          initial,
          style: const TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: Color(0xFF7C3AED),
          ),
        ),
      );
    }
    final isAi = item.isAi;
    return Container(
      width: 44,
      height: 44,
      decoration: BoxDecoration(
        color: isAi ? const Color(0xFFF3E8FF) : const Color(0xFFEFF6FF),
        shape: BoxShape.circle,
      ),
      child: Icon(
        isAi ? Icons.auto_awesome_rounded : Icons.assignment_turned_in_rounded,
        size: 22,
        color: isAi ? const Color(0xFF7C3AED) : const Color(0xFF2563EB),
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: const [
          Icon(
            Icons.mark_email_read_outlined,
            size: 56,
            color: Color(0xFFCBD5E1),
          ),
          SizedBox(height: 12),
          Text(
            '暂无相关消息',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              color: Color(0xFF64748B),
              fontSize: 13,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildErrorState() {
    return Padding(
      padding: const EdgeInsets.only(top: 96, left: 32, right: 32),
      child: Column(
        children: [
          Container(
            width: 64,
            height: 64,
            decoration: const BoxDecoration(
              color: AppColors.dangerSoft,
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.wifi_off_rounded,
              size: 30,
              color: AppColors.danger,
            ),
          ),
          const SizedBox(height: 18),
          const Text(
            '加载失败',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 16,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            '网络开小差了，请检查网络后重试',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(height: 20),
          SizedBox(
            width: 160,
            height: 44,
            child: OutlinedButton(
              onPressed: () => _load(reset: true),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.brand,
                side: const BorderSide(color: AppColors.brand),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(22),
                ),
              ),
              child: const Text(
                '重新加载',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSkeleton() {
    return SkeletonShimmer(
      child: ListView(
        physics: const NeverScrollableScrollPhysics(),
        padding: const EdgeInsets.only(bottom: 24),
        children: List.generate(4, (index) {
          return Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
            decoration: const BoxDecoration(
              color: Colors.white,
              border: Border(bottom: BorderSide(color: Color(0xFFF1F5F9))),
            ),
            child: Row(
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: const BoxDecoration(
                    color: Color(0xFFE2E8F0),
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        width: 120,
                        height: 14,
                        color: const Color(0xFFE2E8F0),
                      ),
                      const SizedBox(height: 8),
                      Container(
                        width: 220,
                        height: 12,
                        color: const Color(0xFFE2E8F0),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          );
        }),
      ),
    );
  }

  String _relativeTime(String? iso) {
    if (iso == null || iso.isEmpty) return '';
    try {
      final dt = DateTime.parse(iso);
      final now = DateTime.now();
      final diff = now.difference(dt);
      if (diff.inMinutes < 1) return '刚刚';
      if (diff.inMinutes < 60) return '${diff.inMinutes}分钟前';
      if (dt.year == now.year && dt.month == now.month && dt.day == now.day) {
        final h = dt.hour.toString().padLeft(2, '0');
        final m = dt.minute.toString().padLeft(2, '0');
        return '$h:$m';
      }
      if (dt.year == now.year &&
          dt.month == now.month &&
          dt.day == now.day - 1) {
        return '昨天';
      }
      if (dt.year == now.year) {
        return '${dt.month}-${dt.day.toString().padLeft(2, '0')}';
      }
      return '${dt.year}-${dt.month}-${dt.day.toString().padLeft(2, '0')}';
    } catch (_) {
      return '';
    }
  }
}

class _QuickEntry {
  final String key;
  final String label;
  final IconData icon;
  final Color iconColor;
  final Color bgColor;

  const _QuickEntry({
    required this.key,
    required this.label,
    required this.icon,
    required this.iconColor,
    required this.bgColor,
  });
}
