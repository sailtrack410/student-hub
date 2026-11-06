import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/pdm_api.dart';
import '../../models/pdm_models.dart';
import '../../services/message_state_events.dart';
import '../../theme/app_theme.dart';
import '../../widgets/app_status_panel.dart';
import '../service/service_hall_page.dart';
import 'pdm_connection_page.dart';
import 'pdm_notification_list_page.dart';
import 'pdm_todo_list_page.dart';

/// PDM 成长主页面
/// Tab 0: 成长概览（两行紧凑身份 → 待办优先展示与通知胶囊 → 学分进度条 → 导师状态与降级重试）
/// Tab 1: 学期目标（学校原样 label 筛选 + 本学期目标 / 简短空态与可用操作按钮）
/// Tab 2: 目标与总结（两行自适应筛选：第一行日/周/月，第二行目标/总结；支持分页与评价降级状态）
/// Tab 3: 成长成果（分为学分、五维能力与综合评价、成果与 CAP、毕业与就业进度四个清晰模块）
class PdmGrowthPage extends StatefulWidget {
  final int initialTabIndex;
  final String initialRecordPeriod;
  final String initialRecordKind;
  final bool highlightMentor;
  final PdmOverview? initialOverview;

  const PdmGrowthPage({
    super.key,
    this.initialTabIndex = 0,
    this.initialRecordPeriod = 'WEEK',
    this.initialRecordKind = 'SUMMARY',
    this.highlightMentor = false,
    this.initialOverview,
  });

  @override
  State<PdmGrowthPage> createState() => _PdmGrowthPageState();
}

class _PdmGrowthPageState extends State<PdmGrowthPage>
    with SingleTickerProviderStateMixin {
  late final TabController _tabController;

  // Overview state
  PdmDataEnvelope<PdmOverview>? _overviewEnv;
  bool _overviewLoading = true;
  String? _overviewErrorCode;
  String? _overviewErrorMessage;
  bool _syncing = false;

  // Term goal state
  List<Map<String, dynamic>> _termOptions = const [];
  String? _selectedYear;
  String? _selectedTerm;
  Map<String, dynamic>? _termGoal;
  bool _termLoading = false;
  bool _termLoadedOnce = false;
  String? _termError;

  // Records state
  late String _recordPeriod;
  late String _recordKind;
  PdmRecordsPage _recordsPage = const PdmRecordsPage();
  bool _recordsLoading = false;
  bool _recordsLoadedOnce = false;
  String? _recordsError;
  int _recordsRequestId = 0;

  // Achievements state
  PdmAchievements? _achievements;
  bool _achievementsLoading = false;
  bool _achievementsLoadedOnce = false;
  String? _achievementsError;

  @override
  void initState() {
    super.initState();
    _recordPeriod =
        const {'DAY', 'WEEK', 'MONTH'}.contains(widget.initialRecordPeriod)
        ? widget.initialRecordPeriod
        : 'WEEK';
    _recordKind = const {'PLAN', 'SUMMARY'}.contains(widget.initialRecordKind)
        ? widget.initialRecordKind
        : 'SUMMARY';
    _tabController = TabController(
      length: 4,
      vsync: this,
      initialIndex: widget.initialTabIndex.clamp(0, 3),
    )..addListener(_onTabChanged);

    if (widget.initialOverview != null) {
      _overviewEnv = PdmDataEnvelope(content: widget.initialOverview!);
      _overviewLoading = false;
    } else {
      _loadOverview();
    }
    _ensureCurrentTabLoaded();
  }

  @override
  void dispose() {
    _tabController.removeListener(_onTabChanged);
    _tabController.dispose();
    super.dispose();
  }

  void _onTabChanged() {
    if (_tabController.indexIsChanging) return;
    _ensureCurrentTabLoaded();
  }

  void _ensureCurrentTabLoaded() {
    switch (_tabController.index) {
      case 1:
        if (!_termLoadedOnce && !_termLoading) _loadTermGoal(loadOptions: true);
        break;
      case 2:
        if (!_recordsLoadedOnce && !_recordsLoading) _loadRecords();
        break;
      case 3:
        if (!_achievementsLoadedOnce && !_achievementsLoading) {
          _loadAchievements();
        }
        break;
    }
  }

  Future<void> _loadOverview({bool forceSync = false}) async {
    setState(() {
      _overviewLoading = true;
      _overviewErrorCode = null;
      _overviewErrorMessage = null;
    });
    try {
      final res = forceSync
          ? await PdmApi.sync()
          : await PdmApi.overview(refresh: false);
      if (!mounted) return;
      setState(() {
        _overviewEnv = res;
        _overviewLoading = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _overviewLoading = false;
        _overviewErrorCode = e.code;
        _overviewErrorMessage = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _overviewLoading = false;
        _overviewErrorCode = 'ERROR';
        _overviewErrorMessage = '加载成长概览失败，请稍后重试';
      });
    }
  }

  Future<void> _syncCurrentTab() async {
    if (_syncing) return;
    setState(() => _syncing = true);
    try {
      await _loadOverview(forceSync: true);
      if (!mounted || _overviewErrorCode != null) return;
      // /sync 更新概览；其他模块仍需各自读取，未显示的页签延迟刷新。
      _termLoadedOnce = false;
      _recordsLoadedOnce = false;
      _achievementsLoadedOnce = false;
      switch (_tabController.index) {
        case 1:
          await _loadTermGoal(loadOptions: true);
          break;
        case 2:
          await _loadRecords(page: _recordsPage.page);
          break;
        case 3:
          await _loadAchievements();
          break;
      }
    } finally {
      if (mounted) setState(() => _syncing = false);
    }
  }

  Future<void> _loadTermGoal({bool loadOptions = false}) async {
    setState(() {
      _termLoading = true;
      _termError = null;
    });
    try {
      if (loadOptions || _termOptions.isEmpty) {
        final opts = await PdmApi.termOptions();
        _termOptions = opts.content;
        if (_selectedYear == null && _termOptions.isNotEmpty) {
          final currentOpt =
              _termOptions.where((o) => '${o['current']}' == '1').firstOrNull ??
              _termOptions.first;
          _selectedYear = currentOpt['academicYear']?.toString();
          _selectedTerm = (currentOpt['termType'] ?? currentOpt['term'])
              ?.toString();
        }
      }
      final goalRes = await PdmApi.termGoal(
        academicYear: _selectedYear,
        termType: _selectedTerm,
      );
      if (!mounted) return;
      setState(() {
        _termGoal = goalRes.content;
        _termLoading = false;
        _termLoadedOnce = true;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _termLoading = false;
        _termError = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _termLoading = false;
        _termError = '加载学期目标失败，请稍后重试';
      });
    }
  }

  Future<void> _loadRecords({int page = 1}) async {
    final requestId = ++_recordsRequestId;
    final period = _recordPeriod;
    setState(() {
      _recordsLoading = true;
      _recordsError = null;
    });
    try {
      final res = await PdmApi.records(
        period: period,
        page: page,
        pageSize: 20,
      );
      if (!mounted || requestId != _recordsRequestId) return;
      setState(() {
        _recordsPage = res.content;
        _recordsLoading = false;
        _recordsLoadedOnce = true;
      });
    } on ApiException catch (e) {
      if (!mounted || requestId != _recordsRequestId) return;
      setState(() {
        _recordsLoading = false;
        _recordsError = e.message;
      });
    } catch (_) {
      if (!mounted || requestId != _recordsRequestId) return;
      setState(() {
        _recordsLoading = false;
        _recordsError = '加载记录列表失败，请稍后重试';
      });
    }
  }

  Future<void> _loadAchievements() async {
    setState(() {
      _achievementsLoading = true;
      _achievementsError = null;
    });
    try {
      final res = await PdmApi.achievements();
      if (!mounted) return;
      setState(() {
        _achievements = res.content;
        _achievementsLoading = false;
        _achievementsLoadedOnce = true;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _achievementsLoading = false;
        _achievementsError = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _achievementsLoading = false;
        _achievementsError = '加载成果数据失败，请稍后重试';
      });
    }
  }

  Future<void> _openConnection() async {
    await Navigator.of(context)
        .push(MaterialPageRoute(builder: (_) => const PdmConnectionPage()));
    if (!mounted) return;
    _termLoadedOnce = false;
    _recordsLoadedOnce = false;
    _achievementsLoadedOnce = false;
    await _loadOverview();
    _ensureCurrentTabLoaded();
  }

  Future<void> _openNotifications() async {
    await Navigator.of(
      context,
    ).push(MaterialPageRoute(builder: (_) => const PdmNotificationListPage()));
    if (!mounted) return;
    MessageStateEvents.changed();
    await _loadOverview();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(
            Icons.arrow_back_ios_new_rounded,
            size: 20,
            color: AppColors.textPrimary,
          ),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: const Text(
          'PDM 成长档案',
          style: TextStyle(
            fontSize: AppTypography.navTitleSize,
            fontWeight: AppTypography.navTitleWeight,
            color: AppColors.textPrimary,
          ),
        ),
        actions: [
          IconButton(
            tooltip: '强制同步',
            onPressed: _overviewLoading || _syncing ? null : _syncCurrentTab,
            icon: const Icon(
              Icons.sync_rounded,
              size: 20,
              color: AppColors.brand,
            ),
          ),
          IconButton(
            tooltip: 'PDM 连接管理',
            onPressed: _openConnection,
            icon: const Icon(
              Icons.link_rounded,
              size: 20,
              color: AppColors.textPrimary,
            ),
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          labelColor: AppColors.brand,
          unselectedLabelColor: AppColors.textSecondary,
          indicatorColor: AppColors.brand,
          labelPadding: const EdgeInsets.symmetric(horizontal: 6),
          labelStyle: const TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.bold,
          ),
          unselectedLabelStyle: const TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w500,
          ),
          tabs: const [
            Tab(text: '概览'),
            Tab(text: '学期目标'),
            Tab(text: '目标总结'),
            Tab(text: '成长成果'),
          ],
        ),
      ),
      body: _buildMainContent(),
    );
  }

  Widget _buildMainContent() {
    if (_overviewLoading && _overviewEnv == null) {
      return const Center(child: CircularProgressIndicator(strokeWidth: 2));
    }
    if (_overviewErrorCode == 'PDM_409_01' ||
        _overviewErrorCode == 'PDM_401_01') {
      final expired = _overviewErrorCode == 'PDM_401_01';
      return AppStatusPanel(
        title: expired ? 'PDM 授权已失效' : '尚未连接 PDM 账号',
        message: expired
            ? '您的学校会话已过期，请重新粘贴导入链接（不会退出当前平台账号）。'
            : '请先在“我的 → PDM 连接”导入链接，即可查看目标、总结、导师评价与成果。',
        onRetry: _openConnection,
        retryLabel: expired ? '重新导入链接' : '去导入链接',
      );
    }
    if (_overviewErrorCode != null && _overviewEnv == null) {
      return AppStatusPanel(
        title: _overviewErrorMessage ?? 'PDM 服务暂时不可用',
        message: '学校服务异常时已保留您的加密凭证，可点击重试恢复连接。',
        onRetry: () => _loadOverview(forceSync: true),
        retryLabel: '重试同步',
      );
    }

    return TabBarView(
      controller: _tabController,
      children: [
        _buildOverviewTab(),
        _buildTermGoalTab(),
        _buildRecordsTab(),
        _buildAchievementsTab(),
      ],
    );
  }

  // ==================== Tab 0: 成长概览 ====================
  Widget _buildOverviewTab() {
    final env = _overviewEnv;
    final overview = env?.content;
    if (overview == null) {
      return const AppStatusPanel(title: '暂无概览数据');
    }
    final account = overview.connection.account;
    final pendingCount = overview.todos.pendingCount;
    final unreadCount = overview.notifications.unreadCount;
    final pendingItems = overview.todos.items
        .where((e) => !e.isCompleted)
        .toList();

    return RefreshIndicator(
      onRefresh: () => _loadOverview(forceSync: true),
      color: AppColors.brand,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
        children: [
          // 1. 紧凑两行身份条 + 消息通知入口胶囊
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.feedDivider),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Flexible(
                            child: Text(
                              account?.displayLabel ?? '已连接 PDM 学生账号',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 15,
                                fontWeight: FontWeight.bold,
                                color: AppColors.textPrimary,
                              ),
                            ),
                          ),
                          const SizedBox(width: 6),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 6,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color: const Color(0xFFECFDF5),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: const Text(
                              '外部关联',
                              style: TextStyle(
                                fontSize: 10,
                                fontWeight: FontWeight.w600,
                                color: Color(0xFF059669),
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '${account?.termDisplayText ?? '当前学期'} · 同步于 ${formatPdmTimestamp(env?.syncedAt)}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 11,
                          color: AppColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 10),
                InkWell(
                  key: const ValueKey('pdm-overview-open-notices'),
                  onTap: _openNotifications,
                  borderRadius: BorderRadius.circular(20),
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: unreadCount > 0
                          ? const Color(0xFFEFF6FF)
                          : const Color(0xFFF8FAFC),
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(
                        color: unreadCount > 0
                            ? const Color(0xFFBFDBFE)
                            : const Color(0xFFE2E8F0),
                      ),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          Icons.notifications_outlined,
                          size: 16,
                          color: unreadCount > 0
                              ? AppColors.brand
                              : AppColors.textSecondary,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          unreadCount > 0 ? '通知 $unreadCount' : '通知',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: unreadCount > 0
                                ? AppColors.brand
                                : AppColors.textSecondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),

          // 2. 第一优先级：待办事项卡片（突出操作）
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.feedDivider),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(
                      Icons.pending_actions_rounded,
                      size: 18,
                      color: Color(0xFFD97706),
                    ),
                    const SizedBox(width: 6),
                    Text(
                      pendingCount > 0 ? '待办事项 ($pendingCount)' : '待办事项',
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const Spacer(),
                    InkWell(
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) =>
                              PdmTodoListPage(initialTasks: overview.todos),
                        ),
                      ),
                      borderRadius: BorderRadius.circular(8),
                      child: const Padding(
                        padding: EdgeInsets.symmetric(
                          horizontal: 4,
                          vertical: 2,
                        ),
                        child: Text(
                          '全部待办 ›',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: AppColors.brand,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                if (pendingItems.isEmpty)
                  Row(
                    children: [
                      const Icon(
                        Icons.check_circle_outline_rounded,
                        size: 18,
                        color: Color(0xFF059669),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          overview.todos.completedCount > 0
                              ? '当前无待处理事项（已完成 ${overview.todos.completedCount} 项）'
                              : '当前暂无待处理目标或总结',
                          style: const TextStyle(
                            fontSize: 13,
                            color: AppColors.textSecondary,
                          ),
                        ),
                      ),
                    ],
                  )
                else
                  ...pendingItems
                      .take(3)
                      .map(
                        (task) => Padding(
                          padding: const EdgeInsets.only(bottom: 8),
                          child: InkWell(
                            onTap: () =>
                                PdmActionNavigator.open(context, task.action),
                            borderRadius: BorderRadius.circular(12),
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 12,
                                vertical: 10,
                              ),
                              decoration: BoxDecoration(
                                color: const Color(0xFFFFFBEB),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Row(
                                children: [
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          task.title,
                                          style: const TextStyle(
                                            fontSize: 13,
                                            fontWeight: FontWeight.bold,
                                            color: AppColors.textPrimary,
                                          ),
                                        ),
                                        const SizedBox(height: 2),
                                        Text(
                                          task.dueLabel,
                                          style: const TextStyle(
                                            fontSize: 11,
                                            color: Color(0xFFB45309),
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 10,
                                      vertical: 5,
                                    ),
                                    decoration: BoxDecoration(
                                      color: AppColors.brand,
                                      borderRadius: BorderRadius.circular(16),
                                    ),
                                    child: const Text(
                                      '去处理',
                                      style: TextStyle(
                                        fontSize: 11,
                                        fontWeight: FontWeight.w600,
                                        color: Colors.white,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ),
              ],
            ),
          ),
          const SizedBox(height: 12),

          // 3. 第二优先级：学分进度条卡片
          InkWell(
            onTap: () => _tabController.animateTo(3),
            borderRadius: BorderRadius.circular(16),
            child: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.feedDivider),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Expanded(
                        child: Text(
                          'PDM 学分进度',
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: AppColors.textPrimary,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Flexible(
                        child: Text(
                          overview.creditSummaryText,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF059669),
                          ),
                        ),
                      ),
                      const SizedBox(width: 4),
                      const Icon(
                        Icons.chevron_right_rounded,
                        size: 18,
                        color: AppColors.textSecondary,
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(6),
                    child: LinearProgressIndicator(
                      value: overview.creditProgressRatio,
                      minHeight: 8,
                      backgroundColor: const Color(0xFFF1F5F9),
                      valueColor: const AlwaysStoppedAnimation<Color>(
                        Color(0xFF059669),
                      ),
                    ),
                  ),
                  if (overview.pdmCreditRemaining != null) ...[
                    const SizedBox(height: 6),
                    Text(
                      '还需修读 ${formatPdmNumber(overview.pdmCreditRemaining)} 学分 · 点击查看能力与成果详情',
                      style: const TextStyle(
                        fontSize: 11,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),

          // 4. 第三优先级：导师区域（区分 BOUND / UNBOUND / UNAVAILABLE）
          _buildOverviewMentorSection(overview),
          const SizedBox(height: 12),

          // 5. 关联办事大厅快捷入口
          Material(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
            child: InkWell(
              borderRadius: BorderRadius.circular(16),
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => const ServiceHallPage(showBack: true),
                ),
              ),
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 13,
                ),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.feedDivider),
                ),
                child: const Row(
                  children: [
                    Icon(
                      Icons.assignment_turned_in_outlined,
                      color: AppColors.brand,
                      size: 20,
                    ),
                    SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        '去办事大厅办理考证、竞赛、实践与证明事项',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: AppColors.textPrimary,
                        ),
                      ),
                    ),
                    Icon(
                      Icons.chevron_right_rounded,
                      size: 18,
                      color: AppColors.textSecondary,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildOverviewMentorSection(PdmOverview overview) {
    final mentor = overview.mentor;
    if (overview.isMentorUnavailable) {
      return Container(
        key: const ValueKey('pdm-overview-mentor-unavailable'),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: const Color(0xFFFFFBEB),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFFFDE68A)),
        ),
        child: Row(
          children: [
            const Icon(
              Icons.warning_amber_rounded,
              size: 20,
              color: Color(0xFFD97706),
            ),
            const SizedBox(width: 10),
            const Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '导师信息暂不可用',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFFB45309),
                    ),
                  ),
                  SizedBox(height: 2),
                  Text(
                    '学校导师接口响应异常，不影响待办、通知与成果使用',
                    style: TextStyle(fontSize: 11, color: Color(0xFFB45309)),
                  ),
                ],
              ),
            ),
            TextButton(
              key: const ValueKey('pdm-overview-mentor-retry'),
              onPressed: _overviewLoading
                  ? null
                  : () => _loadOverview(forceSync: true),
              child: const Text('重试'),
            ),
          ],
        ),
      );
    }

    if (overview.isMentorBound && mentor != null) {
      return Container(
        key: const ValueKey('pdm-overview-mentor-card'),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
        decoration: BoxDecoration(
          color: widget.highlightMentor
              ? const Color(0xFFEFF6FF)
              : Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: widget.highlightMentor
                ? AppColors.brand
                : AppColors.feedDivider,
          ),
        ),
        child: Row(
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: const BoxDecoration(
                color: AppColors.brandSoft,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.person_outline_rounded,
                color: AppColors.brand,
                size: 20,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '指导导师：${mentor.name}',
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: AppColors.textPrimary,
                    ),
                  ),
                  if (mentor.tutorNo != null && mentor.tutorNo!.isNotEmpty)
                    Text(
                      '工号 ${mentor.tutorNo}',
                      style: const TextStyle(
                        fontSize: 11,
                        color: AppColors.textSecondary,
                      ),
                    ),
                ],
              ),
            ),
            TextButton(
              onPressed: () => _tabController.animateTo(2),
              child: const Text('查看评价 ›', style: TextStyle(fontSize: 12)),
            ),
          ],
        ),
      );
    }

    // UNBOUND：简洁紧凑状态，不占整块大卡片，历史评价仍可从周/月记录详情查看
    return Container(
      key: const ValueKey('pdm-overview-mentor-unbound'),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 11),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.feedDivider),
      ),
      child: Row(
        children: [
          const Icon(
            Icons.person_off_outlined,
            size: 18,
            color: AppColors.textSecondary,
          ),
          const SizedBox(width: 10),
          const Expanded(
            child: Text(
              '暂未绑定导师 · 历史评价可在目标总结详情查看',
              style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
            ),
          ),
          InkWell(
            onTap: () => _tabController.animateTo(2),
            child: const Text(
              '去查看 ›',
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: AppColors.brand,
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ==================== Tab 1: 学期目标 ====================
  Widget _buildTermGoalTab() {
    if (_termLoading) {
      return const Center(child: CircularProgressIndicator(strokeWidth: 2));
    }
    if (_termError != null) {
      return AppStatusPanel(
        title: _termError!,
        onRetry: () => _loadTermGoal(loadOptions: true),
        retryLabel: '重新加载',
      );
    }

    return RefreshIndicator(
      onRefresh: () => _loadTermGoal(loadOptions: true),
      color: AppColors.brand,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
        children: [
          if (_termOptions.isNotEmpty) ...[
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.feedDivider),
              ),
              child: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: _termOptions.map((opt) {
                  final y = opt['academicYear']?.toString() ?? '';
                  final t = (opt['termType'] ?? opt['term'])?.toString() ?? '';
                  final label = formatPdmTermOptionLabel(opt);
                  final selected = _selectedYear == y && _selectedTerm == t;
                  return ChoiceChip(
                    label: Text(label, style: const TextStyle(fontSize: 12)),
                    selected: selected,
                    onSelected: (_) {
                      setState(() {
                        _selectedYear = y;
                        _selectedTerm = t;
                      });
                      _loadTermGoal();
                    },
                  );
                }).toList(),
              ),
            ),
            const SizedBox(height: 12),
          ],
          Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.feedDivider),
            ),
            child: _termGoal == null || _termGoal!.isEmpty
                ? Column(
                    crossAxisAlignment: CrossAxisAlignment.center,
                    children: [
                      const SizedBox(height: 8),
                      const Icon(
                        Icons.flag_outlined,
                        size: 36,
                        color: AppColors.brand,
                      ),
                      const SizedBox(height: 10),
                      const Text(
                        '本学期暂无目标',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: AppColors.textPrimary,
                        ),
                      ),
                      const SizedBox(height: 6),
                      const Text(
                        '请在钉钉「PDM 个人发展管理系统」填写提交本学期目标，完成后点击下方刷新同步状态。',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 13,
                          color: AppColors.textSecondary,
                          height: 1.5,
                        ),
                      ),
                      const SizedBox(height: 16),
                      Wrap(
                        spacing: 10,
                        runSpacing: 8,
                        alignment: WrapAlignment.center,
                        children: [
                          OutlinedButton.icon(
                            key: const ValueKey('pdm-term-goal-refresh-btn'),
                            onPressed: () => _loadTermGoal(loadOptions: false),
                            icon: const Icon(Icons.refresh_rounded, size: 16),
                            label: const Text('刷新目标状态'),
                          ),
                          ElevatedButton.icon(
                            key: const ValueKey(
                              'pdm-term-goal-service-hall-btn',
                            ),
                            onPressed: () {
                              Navigator.of(context).push(
                                MaterialPageRoute(
                                  builder: (_) =>
                                      const ServiceHallPage(showBack: true),
                                ),
                              );
                            },
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AppColors.brand,
                              foregroundColor: Colors.white,
                              elevation: 0,
                            ),
                            icon: const Icon(
                              Icons.assignment_turned_in_outlined,
                              size: 16,
                            ),
                            label: const Text('前往办事大厅'),
                          ),
                        ],
                      ),
                    ],
                  )
                : _buildTermGoalDetails(_termGoal!),
          ),
        ],
      ),
    );
  }

  String? _termGoalText(dynamic value) {
    if (value is! String && value is! num) return null;
    final text = stripPdmHtmlToPlain('$value').trim();
    return text.isEmpty ? null : text;
  }

  List<Map<String, dynamic>> _termGoalCourses(dynamic value) {
    if (value is! List) return const [];
    return value
        .whereType<Map>()
        .where(
          (course) =>
              course['courseName'] is String &&
              _termGoalText(course['courseName']) != null,
        )
        .map((course) => Map<String, dynamic>.from(course))
        .toList();
  }

  Widget _buildTermGoalDetails(Map<String, dynamic> goal) {
    final content = [
      goal['goal'],
      goal['goalContent'],
      goal['content'],
    ].map(_termGoalText).whereType<String>().firstOrNull;
    final courses = goal['courses'];
    final requiredCourses = _termGoalCourses(
      courses is Map ? courses['required'] : null,
    );
    final optionalCourses = _termGoalCourses(
      courses is Map ? courses['optional'] : null,
    );
    final stateName = _termGoalText(goal['stateName']);
    final submittedAt =
        _termGoalText(goal['submitTime']) ?? _termGoalText(goal['createTime']);

    // Render known business fields only. Permission flags and unknown nested
    // payloads are not display text; grade/rate have no confirmed school labels.
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '学期目标详情',
          style: TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        if (stateName != null) _termGoalDetailField('状态', stateName),
        if (submittedAt != null) _termGoalDetailField('提交时间', submittedAt),
        if (content != null) _termGoalDetailField('目标内容', content),
        if (requiredCourses.isNotEmpty)
          _termGoalCourseSection('必修课程', requiredCourses),
        if (optionalCourses.isNotEmpty)
          _termGoalCourseSection('选修课程', optionalCourses),
        if (content == null &&
            requiredCourses.isEmpty &&
            optionalCourses.isEmpty)
          const Padding(
            padding: EdgeInsets.only(top: 12),
            child: Text(
              '暂无目标内容',
              style: TextStyle(color: AppColors.textSecondary, fontSize: 13),
            ),
          ),
      ],
    );
  }

  Widget _termGoalDetailField(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(
              fontSize: 12,
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: const TextStyle(
              fontSize: 13,
              height: 1.5,
              color: AppColors.textPrimary,
            ),
          ),
        ],
      ),
    );
  }

  Widget _termGoalCourseSection(
    String label,
    List<Map<String, dynamic>> courses,
  ) {
    return Padding(
      padding: const EdgeInsets.only(top: 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: AppColors.textPrimary,
            ),
          ),
          for (final course in courses) _termGoalCourseCard(course),
        ],
      ),
    );
  }

  Widget _termGoalCourseCard(Map<String, dynamic> course) {
    final metrics = <String>[];
    for (final (field, label) in const [
      ('credit', '学分'),
      ('goalCourseScore', '目标成绩'),
      ('goalCoursePoint', '目标绩点'),
    ]) {
      final raw = _termGoalText(course[field]);
      final number = raw == null ? null : num.tryParse(raw);
      if (number != null && number.isFinite) {
        metrics.add('$label：${formatPdmNumber(number)}');
      }
    }
    final teacher = _termGoalText(course['teacherName']);
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(top: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.feedDivider),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            _termGoalText(course['courseName'])!,
            style: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: AppColors.textPrimary,
            ),
          ),
          if (metrics.isNotEmpty) ...[
            const SizedBox(height: 8),
            Wrap(
              spacing: 12,
              runSpacing: 6,
              children: metrics
                  .map(
                    (text) => Text(
                      text,
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  )
                  .toList(),
            ),
          ],
          if (teacher != null) ...[
            const SizedBox(height: 6),
            Text(
              '任课教师：$teacher',
              style: const TextStyle(
                fontSize: 12,
                color: AppColors.textSecondary,
              ),
            ),
          ],
        ],
      ),
    );
  }

  // ==================== Tab 2: 日/周/月 目标与总结 ====================
  Widget _buildRecordsTab() {
    return Column(
      children: [
        // 两行筛选栏：第一行选日/周/月，第二行选目标/总结 + 记录总数，彻底消除窄屏横向溢出
        Container(
          color: Colors.white,
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  for (final entry in const [
                    ('WEEK', '周记录'),
                    ('MONTH', '月记录'),
                    ('DAY', '日记录'),
                  ]) ...[
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 3),
                        child: _buildSegmentPill(
                          key: ValueKey('pdm-period-${entry.$1}'),
                          label: entry.$2,
                          selected: _recordPeriod == entry.$1,
                          onTap: () {
                            if (_recordPeriod == entry.$1) return;
                            setState(() => _recordPeriod = entry.$1);
                            _loadRecords(page: 1);
                          },
                        ),
                      ),
                    ),
                  ],
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  _buildKindChip('PLAN', '目标计划'),
                  const SizedBox(width: 8),
                  _buildKindChip('SUMMARY', '总结复盘'),
                  const Spacer(),
                  if (!_recordsLoading && _recordsError == null)
                    Text(
                      '共 ${_recordsPage.total} 条',
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppColors.textSecondary,
                      ),
                    ),
                ],
              ),
            ],
          ),
        ),
        const Divider(height: 1),
        Expanded(
          child: _recordsLoading
              ? const Center(child: CircularProgressIndicator(strokeWidth: 2))
              : _recordsError != null
              ? AppStatusPanel(
                  title: _recordsError!,
                  onRetry: () => _loadRecords(page: 1),
                  retryLabel: '重试',
                )
              : _recordsPage.items.isEmpty
              ? AppStatusPanel(
                  title: '暂无${_periodLabel(_recordPeriod)}记录',
                  message: '可切换日/周/月查看其他周期的目标与总结',
                )
              : RefreshIndicator(
                  onRefresh: () => _loadRecords(page: _recordsPage.page),
                  color: AppColors.brand,
                  child: ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
                    itemCount:
                        _recordsPage.items.length +
                        (_recordsPage.total > _recordsPage.pageSize ? 1 : 0),
                    separatorBuilder: (_, _) => const SizedBox(height: 10),
                    itemBuilder: (context, index) {
                      if (index >= _recordsPage.items.length) {
                        return _buildRecordsPaginationBar();
                      }
                      final item = _recordsPage.items[index];
                      return _buildRecordItemCard(item);
                    },
                  ),
                ),
        ),
      ],
    );
  }

  String _periodLabel(String period) => switch (period) {
    'DAY' => '日',
    'MONTH' => '月',
    _ => '周',
  };

  Widget _buildSegmentPill({
    required Key key,
    required String label,
    required bool selected,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      key: key,
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 7),
        decoration: BoxDecoration(
          color: selected ? AppColors.brand : const Color(0xFFF1F5F9),
          borderRadius: BorderRadius.circular(10),
        ),
        alignment: Alignment.center,
        child: Text(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: selected ? Colors.white : AppColors.textSecondary,
          ),
        ),
      ),
    );
  }

  Widget _buildKindChip(String kind, String label) {
    final selected = _recordKind == kind;
    return GestureDetector(
      key: ValueKey('pdm-kind-$kind'),
      onTap: () {
        if (_recordKind != kind) {
          setState(() => _recordKind = kind);
        }
      },
      behavior: HitTestBehavior.opaque,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
        decoration: BoxDecoration(
          color: selected ? AppColors.brandSoft : const Color(0xFFF8FAFC),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: selected ? AppColors.brand : const Color(0xFFE2E8F0),
          ),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: selected ? FontWeight.bold : FontWeight.w500,
            color: selected ? AppColors.brand : AppColors.textSecondary,
          ),
        ),
      ),
    );
  }

  Widget _buildRecordsPaginationBar() {
    final hasPrev = _recordsPage.page > 1;
    final hasNext =
        _recordsPage.page * _recordsPage.pageSize < _recordsPage.total;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.feedDivider),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          TextButton(
            onPressed: hasPrev
                ? () => _loadRecords(page: _recordsPage.page - 1)
                : null,
            child: const Text('上一页'),
          ),
          Text(
            '第 ${_recordsPage.page} 页 / 共 ${_recordsPage.total} 条',
            style: const TextStyle(
              fontSize: 12,
              color: AppColors.textSecondary,
            ),
          ),
          TextButton(
            onPressed: hasNext
                ? () => _loadRecords(page: _recordsPage.page + 1)
                : null,
            child: const Text('下一页'),
          ),
        ],
      ),
    );
  }

  Widget _buildRecordItemCard(Map<String, dynamic> item) {
    final id = item['id']?.toString() ?? '';
    final startDate = item['startDate']?.toString() ?? '—';
    final endDate = item['endDate']?.toString();
    final finishRateStr = formatPdmFinishRate(item['finishRate']);
    final summaryState = _formatSummaryState(item);
    final reviewState = _recordReviewState(item);
    final submitTime = item['submitTime']?.toString();

    return Material(
      key: ValueKey('pdm-record-$id'),
      color: Colors.white,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: id.isEmpty
            ? null
            : () {
                Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => PdmRecordDetailPage(
                      recordId: id,
                      period: _recordsPage.period,
                      initialKind: _recordKind,
                      dateRangeLabel: endDate != null && endDate.isNotEmpty
                          ? '$startDate ~ $endDate'
                          : startDate,
                    ),
                  ),
                );
              },
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppColors.feedDivider),
          ),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 6,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            color: AppColors.brandSoft,
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            '${_periodLabel(_recordPeriod)}${_recordKind == 'PLAN' ? '目标' : '总结'}',
                            style: const TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              color: AppColors.brand,
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            endDate != null && endDate.isNotEmpty
                                ? '$startDate 至 $endDate'
                                : startDate,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.bold,
                              color: AppColors.textPrimary,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      runSpacing: 6,
                      children: [
                        if (summaryState != null)
                          _statusTag(
                            '总结：$summaryState',
                            const Color(0xFF475569),
                          ),
                        if (reviewState != null)
                          _statusTag(
                            '评价：$reviewState',
                            const Color(0xFF2563EB),
                          ),
                        if (finishRateStr != null)
                          _statusTag(
                            '完成率 $finishRateStr',
                            const Color(0xFF059669),
                          ),
                        if (submitTime != null && submitTime.isNotEmpty)
                          Text(
                            submitTime,
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppColors.textTertiary,
                            ),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 6),
              const Icon(
                Icons.chevron_right_rounded,
                color: AppColors.textSecondary,
              ),
            ],
          ),
        ),
      ),
    );
  }

  String? _formatSummaryState(Map<String, dynamic> item) {
    // 学校列表 state=0/1 表示待总结/已总结，不是计划提交状态。
    final raw = item['state']?.toString().trim();
    return switch (raw) {
      '1' => '已总结',
      '0' => '待总结',
      _ => null,
    };
  }

  String? _recordReviewState(Map<String, dynamic> item) {
    // 日记录无导师评价，周/月计划和总结各自使用学校对应的评价字段。
    if (_recordPeriod == 'DAY') return null;
    final String? raw;
    if (_recordKind == 'PLAN') {
      raw =
          (_recordPeriod == 'WEEK' ? item['weekPlanState'] : item['planState'])
              ?.toString();
    } else {
      raw =
          item['reviewState']?.toString() == '1' ||
              item['weekSummaryState']?.toString() == '1'
          ? '1'
          : (item['reviewState'] ?? item['weekSummaryState'])?.toString();
    }
    return switch (raw?.trim()) {
      '1' => '已评价',
      '0' => '待评价',
      _ => null,
    };
  }

  Widget _statusTag(String text, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.09),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        text,
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: color,
        ),
      ),
    );
  }

  // ==================== Tab 3: 成长成果 ====================
  Widget _buildAchievementsTab() {
    if (_achievementsLoading) {
      return const Center(child: CircularProgressIndicator(strokeWidth: 2));
    }
    if (_achievementsError != null) {
      return AppStatusPanel(
        title: _achievementsError!,
        onRetry: _loadAchievements,
        retryLabel: '重试',
      );
    }
    final ach = _achievements;
    if (ach == null) {
      return const AppStatusPanel(title: '暂无成果数据');
    }

    return RefreshIndicator(
      onRefresh: _loadAchievements,
      color: AppColors.brand,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
        children: [
          _buildCreditsModule(ach),
          const SizedBox(height: 12),
          _buildAbilitiesAndAssessmentModule(ach),
          const SizedBox(height: 12),
          _buildStatisticsAndCapModule(ach),
          const SizedBox(height: 12),
          _buildGraduationAndEmploymentModule(ach),
        ],
      ),
    );
  }

  Widget _buildCreditsModule(PdmAchievements ach) {
    final pdmCredit = ach.credits?['pdmCredit'];
    final second = ach.credits?['second'];
    final third = ach.credits?['third'];

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.feedDivider),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '学分修读情况',
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 12),
          if (pdmCredit is Map)
            Row(
              children: [
                _metricCell(
                  label: '要求学分',
                  value: formatPdmNumber(pdmCredit['required']),
                  color: AppColors.textPrimary,
                ),
                const SizedBox(width: 8),
                _metricCell(
                  label: '已修学分',
                  value: formatPdmNumber(pdmCredit['completed']),
                  color: const Color(0xFF059669),
                ),
                const SizedBox(width: 8),
                _metricCell(
                  label: '剩余学分',
                  value: formatPdmNumber(pdmCredit['remaining']),
                  color: const Color(0xFFD97706),
                ),
              ],
            )
          else
            const Text(
              '暂无 PDM 学分统计',
              style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
            ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(child: _classroomCreditLine('第二课堂', second)),
              const SizedBox(width: 10),
              Expanded(child: _classroomCreditLine('第三课堂', third)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _classroomCreditLine(String title, dynamic raw) {
    String text = '暂无学分记录';
    if (raw is Map && raw.isNotEmpty) {
      final completed =
          raw['okScore'] ??
          raw['completed'] ??
          raw['score'] ??
          raw['credit'] ??
          raw['total'];
      final remaining = raw['noScore'];
      if (completed != null) {
        text = '已获 ${formatPdmNumber(completed)} 学分';
      }
      if (remaining != null) {
        final remainingText = '未获 ${formatPdmNumber(remaining)} 学分';
        text = completed == null ? remainingText : '$text\n$remainingText';
      }
    }
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            text,
            style: const TextStyle(
              fontSize: 11,
              color: AppColors.textSecondary,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAbilitiesAndAssessmentModule(PdmAchievements ach) {
    final assessment = ach.assessment;
    final overallScore = assessment?['overallScore'];
    final grade = assessment?['grade']?.toString().trim();
    final gradeDesc = assessment?['gradeDesc']?.toString().trim();
    final evaluation =
        (assessment?['evaluation'] ?? assessment?['evaluationDesc'])
            ?.toString()
            .trim();

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.feedDivider),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Expanded(
                child: Text(
                  '五维能力与综合评价',
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                    color: AppColors.textPrimary,
                  ),
                ),
              ),
              if (overallScore != null)
                Text(
                  '综合得分 ${formatPdmNumber(overallScore)}',
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                    color: AppColors.brand,
                  ),
                ),
              // 仅在等级非空时显示等级标签，不再显示“等级：—”
              if (grade != null && grade.isNotEmpty) ...[
                const SizedBox(width: 6),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 7,
                    vertical: 2,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.brandSoft,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    gradeDesc != null && gradeDesc.isNotEmpty
                        ? '$grade ($gradeDesc)'
                        : grade,
                    style: const TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                      color: AppColors.brand,
                    ),
                  ),
                ),
              ],
            ],
          ),
          if (evaluation != null && evaluation.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              stripPdmHtmlToPlain(evaluation),
              style: const TextStyle(
                fontSize: 12,
                color: AppColors.textSecondary,
              ),
            ),
          ],
          const SizedBox(height: 12),
          if (ach.abilities.isEmpty)
            const Text(
              '暂未生成五维能力画像',
              style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
            )
          else
            ...ach.abilities.map((ab) {
              final name =
                  ab['categoryName']?.toString() ??
                  ab['category']?.toString() ??
                  '能力维度';
              final scoreText = formatPdmNumber(ab['score']);
              final scoreVal = double.tryParse('${ab['score'] ?? 0}') ?? 0.0;
              final ratio = (scoreVal / 100.0).clamp(0.0, 1.0);
              return Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            name,
                            style: const TextStyle(
                              fontSize: 13,
                              color: AppColors.textPrimary,
                            ),
                          ),
                        ),
                        Text(
                          '$scoreText 分',
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.bold,
                            color: AppColors.textPrimary,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: LinearProgressIndicator(
                        value: ratio,
                        minHeight: 6,
                        backgroundColor: const Color(0xFFF1F5F9),
                        valueColor: const AlwaysStoppedAnimation<Color>(
                          AppColors.brand,
                        ),
                      ),
                    ),
                  ],
                ),
              );
            }),
        ],
      ),
    );
  }

  Widget _buildStatisticsAndCapModule(PdmAchievements ach) {
    final cap = ach.capProjects;
    final assistantCount = (cap?['assistantList'] as List?)?.length ?? 0;
    final foundingCount = (cap?['foundingList'] as List?)?.length ?? 0;
    final expClassCount = (cap?['experimentalClassList'] as List?)?.length ?? 0;
    final totalCap = assistantCount + foundingCount + expClassCount;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.feedDivider),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '成果分类与 CAP 项目',
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 10),
          if (ach.statistics.isEmpty)
            const Text(
              '暂无已认定的成果记录',
              style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
            )
          else
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: ach.statistics.map((stat) {
                final name =
                    stat['certificateTypeName']?.toString() ??
                    stat['name']?.toString() ??
                    '成果';
                final numStr = formatPdmNumber(
                  stat['num'] ?? stat['count'] ?? 0,
                );
                return Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 7,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF8FAFC),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    '$name：$numStr',
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: AppColors.textPrimary,
                    ),
                  ),
                );
              }).toList(),
            ),
          const Divider(height: 22),
          Text(
            totalCap == 0
                ? 'CAP 关联项目：暂无参与记录（助教 0 · 科创 0 · 实验班 0）'
                : 'CAP 关联项目：助教 $assistantCount 项 · 科创 $foundingCount 项 · 实验班 $expClassCount 项',
            style: const TextStyle(
              fontSize: 12,
              color: AppColors.textSecondary,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildGraduationAndEmploymentModule(PdmAchievements ach) {
    final grad = ach.graduation;
    final reportStatus = grad?['reportStatus']?.toString().trim();
    final firstDraftStatus = grad?['firstDraftStatus']?.toString().trim();
    final finalDraftStatus = grad?['finalDraftStatus']?.toString().trim();
    final hasAnyGradStatus =
        (reportStatus != null && reportStatus.isNotEmpty) ||
        (firstDraftStatus != null && firstDraftStatus.isNotEmpty) ||
        (finalDraftStatus != null && finalDraftStatus.isNotEmpty);

    final emp = ach.employment;
    String empText = '暂未登记就业去向';
    if (emp != null && emp.isNotEmpty) {
      final company =
          emp['companyName'] ?? emp['unitName'] ?? emp['jobStatusName'];
      if (company != null && '$company'.trim().isNotEmpty) {
        empText = '$company';
      }
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.feedDivider),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '毕业设计与就业进度',
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 10),
          if (!hasAnyGradStatus)
            const Text(
              '毕业设计：当前阶段暂无开题或稿件记录',
              style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
            )
          else
            Row(
              children: [
                _metricCell(
                  label: '开题报告',
                  value: reportStatus?.isNotEmpty == true
                      ? reportStatus!
                      : '未开始',
                  color: AppColors.textPrimary,
                ),
                const SizedBox(width: 8),
                _metricCell(
                  label: '初稿状态',
                  value: firstDraftStatus?.isNotEmpty == true
                      ? firstDraftStatus!
                      : '未开始',
                  color: AppColors.textPrimary,
                ),
                const SizedBox(width: 8),
                _metricCell(
                  label: '终稿状态',
                  value: finalDraftStatus?.isNotEmpty == true
                      ? finalDraftStatus!
                      : '未开始',
                  color: AppColors.textPrimary,
                ),
              ],
            ),
          const SizedBox(height: 10),
          Text(
            '就业状态：$empText',
            style: const TextStyle(
              fontSize: 12,
              color: AppColors.textSecondary,
            ),
          ),
        ],
      ),
    );
  }

  Widget _metricCell({
    required String label,
    required String value,
    required Color color,
  }) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 8),
        decoration: BoxDecoration(
          color: const Color(0xFFF8FAFC),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          children: [
            Text(
              value,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.bold,
                color: color,
              ),
            ),
            const SizedBox(height: 3),
            Text(
              label,
              style: const TextStyle(
                fontSize: 11,
                color: AppColors.textSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 单条周/月/日 目标或总结详情页（包含导师评价列表与 mentorReviewsStatus 降级状态处理）
class PdmRecordDetailPage extends StatefulWidget {
  final String recordId;
  final String period;
  final String initialKind;
  final String dateRangeLabel;

  const PdmRecordDetailPage({
    super.key,
    required this.recordId,
    required this.period,
    this.initialKind = 'SUMMARY',
    this.dateRangeLabel = '',
  });

  @override
  State<PdmRecordDetailPage> createState() => _PdmRecordDetailPageState();
}

class _PdmRecordDetailPageState extends State<PdmRecordDetailPage> {
  late String _kind;
  PdmRecordDetail? _data;
  bool _loading = true;
  String? _error;
  int _requestId = 0;

  @override
  void initState() {
    super.initState();
    _kind = widget.initialKind;
    _load();
  }

  Future<void> _load() async {
    final requestId = ++_requestId;
    final kind = _kind;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final res = await PdmApi.recordDetail(
        recordId: widget.recordId,
        period: widget.period,
        kind: kind,
      );
      if (!mounted || requestId != _requestId) return;
      setState(() {
        _data = res.content;
        _loading = false;
      });
    } on ApiException catch (e) {
      if (!mounted || requestId != _requestId) return;
      setState(() {
        _loading = false;
        _error = e.message;
      });
    } catch (_) {
      if (!mounted || requestId != _requestId) return;
      setState(() {
        _loading = false;
        _error = '加载记录详情失败，请稍后重试';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final detail = _data?.detail ?? const <String, dynamic>{};
    final finishRate = formatPdmFinishRate(detail['finishRate']);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(
            Icons.arrow_back_ios_new_rounded,
            size: 20,
            color: AppColors.textPrimary,
          ),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: Text(
          widget.dateRangeLabel.isNotEmpty ? widget.dateRangeLabel : '记录详情',
          style: const TextStyle(
            fontSize: AppTypography.navTitleSize,
            fontWeight: AppTypography.navTitleWeight,
            color: AppColors.textPrimary,
          ),
        ),
      ),
      body: Column(
        children: [
          Container(
            color: Colors.white,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                ChoiceChip(
                  label: const Text('总结复盘'),
                  selected: _kind == 'SUMMARY',
                  onSelected: (s) {
                    if (!s || _kind == 'SUMMARY') return;
                    setState(() => _kind = 'SUMMARY');
                    _load();
                  },
                ),
                const SizedBox(width: 10),
                ChoiceChip(
                  label: const Text('目标计划'),
                  selected: _kind == 'PLAN',
                  onSelected: (s) {
                    if (!s || _kind == 'PLAN') return;
                    setState(() => _kind = 'PLAN');
                    _load();
                  },
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator(strokeWidth: 2))
                : _error != null
                ? AppStatusPanel(
                    title: _error!,
                    onRetry: _load,
                    retryLabel: '重试',
                  )
                : ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: AppColors.feedDivider),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Text(
                                  _kind == 'PLAN' ? '目标与计划内容' : '总结与完成情况',
                                  style: const TextStyle(
                                    fontSize: 15,
                                    fontWeight: FontWeight.bold,
                                    color: AppColors.textPrimary,
                                  ),
                                ),
                                const Spacer(),
                                if (finishRate != null)
                                  Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 3,
                                    ),
                                    decoration: BoxDecoration(
                                      color: const Color(0xFFECFDF5),
                                      borderRadius: BorderRadius.circular(8),
                                    ),
                                    child: Text(
                                      '完成率 $finishRate',
                                      style: const TextStyle(
                                        fontSize: 12,
                                        fontWeight: FontWeight.bold,
                                        color: Color(0xFF059669),
                                      ),
                                    ),
                                  ),
                              ],
                            ),
                            const Divider(height: 20),
                            _buildDetailContent(detail),
                          ],
                        ),
                      ),
                      if (_data != null && !_data!.isReviewsNotSupported) ...[
                        const SizedBox(height: 14),
                        _buildReviewsSection(_data!),
                      ],
                    ],
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildDetailContent(Map<String, dynamic> detail) {
    final summaryText = stripPdmHtmlToPlain(detail['summary']?.toString());
    if (summaryText.isNotEmpty) {
      return SelectableText(
        summaryText,
        style: const TextStyle(
          fontSize: 14,
          color: AppColors.textBody,
          height: 1.6,
        ),
      );
    }
    if (detail['projectList'] is List &&
        (detail['projectList'] as List).isNotEmpty) {
      final list = (detail['projectList'] as List).whereType<Map>().toList();
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: list.map((proj) {
          final contents = (proj['planContents'] as List?)
              ?.map(
                (e) => stripPdmHtmlToPlain(
                  e is Map
                      ? (e['planContent'] ?? e['content'] ?? '').toString()
                      : e is String
                      ? e
                      : '',
                ),
              )
              .where((s) => s.isNotEmpty)
              .join('；');
          final line = (contents != null && contents.isNotEmpty)
              ? contents
              : stripPdmHtmlToPlain(
                  '${proj['content'] ?? proj['title'] ?? '目标项'}',
                );
          return Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              '• $line',
              style: const TextStyle(fontSize: 13, height: 1.5),
            ),
          );
        }).toList(),
      );
    }
    if (detail['recordDetails'] is List &&
        (detail['recordDetails'] as List).isNotEmpty) {
      final list = (detail['recordDetails'] as List).whereType<Map>().toList();
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: list.map((item) {
          final content = stripPdmHtmlToPlain(
            '${item['content'] ?? item['summary'] ?? item['planContent'] ?? ''}',
          );
          return Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              content.isNotEmpty ? '• $content' : '• 已记录细项',
              style: const TextStyle(fontSize: 13, height: 1.5),
            ),
          );
        }).toList(),
      );
    }
    return const Text(
      '暂无详细正文内容',
      style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
    );
  }

  Widget _buildReviewsSection(PdmRecordDetail record) {
    final reviews = record.mentorReviews;
    return Container(
      key: const ValueKey('pdm-record-reviews-section'),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.feedDivider),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '导师指导评价',
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 10),
          if (record.isReviewsUnavailable)
            Row(
              children: [
                const Icon(
                  Icons.warning_amber_rounded,
                  size: 18,
                  color: Color(0xFFD97706),
                ),
                const SizedBox(width: 8),
                const Expanded(
                  child: Text(
                    '导师评价暂不可用，记录详情不受影响',
                    style: TextStyle(fontSize: 12, color: Color(0xFFB45309)),
                  ),
                ),
                TextButton(
                  key: const ValueKey('pdm-record-reviews-retry'),
                  onPressed: _load,
                  child: const Text('重试'),
                ),
              ],
            )
          else if (reviews.isEmpty)
            const Text(
              '暂无导师评价',
              style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
            )
          else
            ...reviews.map(
              (r) => Container(
                margin: const EdgeInsets.only(bottom: 10),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFFF8FAFC),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          r['tutorName']?.toString() ?? '导师',
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.bold,
                            color: AppColors.textPrimary,
                          ),
                        ),
                        const Spacer(),
                        if (r['createTime'] != null)
                          Text(
                            '${r['createTime']}',
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppColors.textSecondary,
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    SelectableText(
                      stripPdmHtmlToPlain(r['summary']?.toString() ?? '已审阅'),
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppColors.textBody,
                        height: 1.5,
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
}
