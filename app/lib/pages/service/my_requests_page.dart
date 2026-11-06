import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/service_request_api.dart';
import '../../models/service_request_models.dart';
import '../../theme/app_theme.dart';
import '../../widgets/app_status_panel.dart';
import 'work_order_detail_page.dart';

/// APP-我的申请：真实分页列表 + 状态筛选 + 详情跳转。
///
/// 数据来源 `GET /api/app/service-requests`；列表不再使用任何演示卡片。
class MyRequestsPage extends StatefulWidget {
  final int initialFilterIndex;
  final Future<Map<String, dynamic>?> Function({
    int pageNum,
    int pageSize,
    String? status,
  })? requestsLoader;

  const MyRequestsPage({
    super.key,
    this.initialFilterIndex = 0,
    this.requestsLoader,
  });

  @override
  State<MyRequestsPage> createState() => _MyRequestsPageState();
}

class _MyRequestsPageState extends State<MyRequestsPage> {
  static const _pageSize = 20;
  static const _maxAutoPrefetchPages = 5;
  static const _filters = [
    _FilterTabItem('全部'),
    _FilterTabItem('处理中'),
    _FilterTabItem('待验收'),
    _FilterTabItem('已办结'),
  ];

  final ServiceRequestApi _api = ServiceRequestApi(ApiClient());
  final List<ServiceRequestSummary> _items = [];
  int _page = 1;
  int _total = 0;
  int _selectedFilterIndex = 0;
  bool _loading = true;
  bool _loadingMore = false;
  bool _error = false;
  int _loadGeneration = 0;

  Future<Map<String, dynamic>?> _fetchPage({
    required int pageNum,
    required int pageSize,
    String? status,
  }) {
    final loader = widget.requestsLoader;
    if (loader != null) {
      return loader(pageNum: pageNum, pageSize: pageSize, status: status);
    }
    return _api.fetchMyRequests(
      pageNum: pageNum,
      pageSize: pageSize,
      status: status,
    );
  }

  @override
  void initState() {
    super.initState();
    _selectedFilterIndex = widget.initialFilterIndex
        .clamp(0, _filters.length - 1)
        .toInt();
    _load(reset: true);
  }

  Future<void> _load({bool reset = false}) async {
    final generation = reset ? ++_loadGeneration : _loadGeneration;
    if (reset) {
      setState(() {
        _loading = true;
        _error = false;
        _page = 1;
        _items.clear();
        _total = 0;
      });
    } else {
      if (_loadingMore || _items.length >= _total) return;
      setState(() => _loadingMore = true);
    }

    final page = await _fetchPage(
      pageNum: _page,
      pageSize: _pageSize,
    );
    if (!mounted || generation != _loadGeneration) return;

    if (page == null) {
      setState(() {
        _loading = false;
        _loadingMore = false;
        _error = true;
      });
      return;
    }

    _appendPageRecords(page);
    setState(() {
      _loading = false;
      _loadingMore = false;
      _error = false;
    });

    // 若总条数超过首屏且在自动预取上限内，自动补齐剩余分页，
    // 保证「处理中 / 待验收 / 已办结」跨页筛选不漏单且各 Tab 计数完整显示。
    while (mounted &&
        generation == _loadGeneration &&
        _items.length < _total &&
        _page <= _maxAutoPrefetchPages) {
      setState(() => _loadingMore = true);
      final nextPage = await _fetchPage(
        pageNum: _page,
        pageSize: _pageSize,
      );
      if (!mounted || generation != _loadGeneration) return;
      if (nextPage == null) {
        setState(() => _loadingMore = false);
        break;
      }
      final added = _appendPageRecords(nextPage);
      setState(() => _loadingMore = false);
      if (added == 0) break;
    }
  }

  int _appendPageRecords(Map<String, dynamic> page) {
    final records = (page['records'] as List? ?? const [])
        .whereType<Map>()
        .map(
          (e) => ServiceRequestSummary.fromJson(Map<String, dynamic>.from(e)),
        )
        .where((e) => e.requestId.isNotEmpty)
        .toList();
    final existingIds = _items.map((item) => item.requestId).toSet();
    final newRecords = records
        .where((item) => !existingIds.contains(item.requestId))
        .toList();
    _items.addAll(newRecords);
    _total = int.tryParse(page['total']?.toString() ?? '') ?? _items.length;
    _page += 1;
    return newRecords.length;
  }

  List<ServiceRequestSummary> get _visibleItems =>
      _items.where(_matchesFilter).toList();

  bool _matchesFilter(ServiceRequestSummary item) {
    return switch (_selectedFilterIndex) {
      1 =>
        item.status == 'SUBMITTED' ||
            (item.status == 'PROCESSING' && !item.isPendingConfirmation),
      2 => item.isPendingConfirmation,
      3 =>
        item.status == 'COMPLETED' ||
            item.status == 'REJECTED' ||
            item.status == 'CANCELLED',
      _ => true,
    };
  }

  int _countForFilter(int index) {
    if (index == 0) {
      return _total > _items.length ? _total : _items.length;
    }
    return _items.where((item) {
      return switch (index) {
        1 =>
          item.status == 'SUBMITTED' ||
              (item.status == 'PROCESSING' && !item.isPendingConfirmation),
        2 => item.isPendingConfirmation,
        3 =>
          item.status == 'COMPLETED' ||
              item.status == 'REJECTED' ||
              item.status == 'CANCELLED',
        _ => true,
      };
    }).length;
  }

  Future<void> _openDetail(ServiceRequestSummary item) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => WorkOrderDetailPage(requestId: item.requestId),
      ),
    );
    if (mounted) await _load(reset: true);
  }

  Future<void> _urge(ServiceRequestSummary item) async {
    final result = await ServiceRequestApi.urge(item.requestId);
    if (!mounted) return;
    if (result == null) {
      _toast('催单失败，请稍后重试');
      return;
    }
    if (result['urged'] == false) {
      final seconds = result['nextUrgeAvailableSeconds'] ?? 0;
      _toast('催单过于频繁，请 ${seconds}s 后再试', color: const Color(0xFFD97706));
      return;
    }
    _toast('已向师傅发送催单加急通知', color: const Color(0xFF16A34A));
  }

  Future<void> _cancel(ServiceRequestSummary item) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('撤销申请'),
        content: const Text('撤销后申请将终止，确定继续吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('再想想'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.danger),
            child: const Text('确认撤销'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    final ok = await _api.cancelRequest(item.requestId);
    if (!mounted) return;
    if (!ok) {
      _toast('撤销失败，请稍后重试');
      return;
    }
    _toast('申请已撤销', color: const Color(0xFF16A34A));
    await _load(reset: true);
  }

  void _toast(String message, {Color color = const Color(0xFFDC2626)}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: color,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.card,
        elevation: 0,
        title: const Text(
          '我的申请',
          style: TextStyle(
            fontFamily: AppTypography.fontFamily,
            fontSize: AppTypography.navTitleSize,
            fontWeight: AppTypography.navTitleWeight,
            color: AppColors.textPrimary,
          ),
        ),
        centerTitle: true,
        actions: [
          IconButton(
            tooltip: '刷新',
            onPressed: _loading ? null : () => _load(reset: true),
            icon: const Icon(Icons.refresh_rounded, size: 20),
          ),
        ],
      ),
      body: Column(
        children: [
          _buildFilterBar(),
          Expanded(child: _buildBody()),
        ],
      ),
    );
  }

  Widget _buildFilterBar() {
    return Container(
      color: AppColors.card,
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.cardGroupGap,
        4,
        AppSpacing.cardGroupGap,
        10,
      ),
      child: Row(
        children: [
          for (var i = 0; i < _filters.length; i++)
            Expanded(
              child: GestureDetector(
                onTap: () => setState(() => _selectedFilterIndex = i),
                behavior: HitTestBehavior.opaque,
                child: Container(
                  margin: const EdgeInsets.symmetric(horizontal: 3),
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  decoration: BoxDecoration(
                    color: _selectedFilterIndex == i
                        ? AppColors.brandSoft
                        : Colors.transparent,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Column(
                    children: [
                      Text(
                        _filters[i].label,
                        style: TextStyle(
                          fontFamily: AppTypography.fontFamily,
                          fontSize: AppTypography.metaSize,
                          fontWeight: _selectedFilterIndex == i
                              ? FontWeight.w600
                              : FontWeight.w400,
                          color: _selectedFilterIndex == i
                              ? AppColors.brand
                              : AppColors.textSecondary,
                        ),
                      ),
                      if (_countForFilter(i) >= 0) ...[
                        const SizedBox(height: 2),
                        Text(
                          '${_countForFilter(i)}',
                          style: TextStyle(
                            fontSize: AppTypography.metaSize,
                            color: _selectedFilterIndex == i
                                ? AppColors.brand
                                : AppColors.textSecondary,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return const AppStatusPanel(title: '正在加载申请', loading: true);
    }
    if (_error && _items.isEmpty) {
      return AppStatusPanel(title: '申请列表加载失败', onRetry: () => _load(reset: true), retryLabel: '重新加载');
    }

    final visible = _visibleItems;
    if (visible.isEmpty) {
      return RefreshIndicator(
        onRefresh: () => _load(reset: true),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            SizedBox(height: MediaQuery.sizeOf(context).height * 0.18),
            const Icon(
              Icons.inbox_outlined,
              size: 52,
              color: Color(0xFFCBD5E1),
            ),
            const SizedBox(height: 12),
            Text(
              _selectedFilterIndex == 0 ? '还没有提交过申请' : '当前筛选暂无申请',
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                color: Color(0xFF64748B),
              ),
            ),
            if (_items.length < _total) ...[
              const SizedBox(height: 12),
              Center(
                child: TextButton(
                  onPressed: _loadingMore ? null : () => _load(),
                  child: Text(_loadingMore ? '加载中…' : '加载更多'),
                ),
              ),
            ],
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: () => _load(reset: true),
      child: ListView.builder(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.pageHorizontal,
          AppSpacing.cardGroupGap,
          AppSpacing.pageHorizontal,
          28,
        ),
        itemCount: visible.length + (_items.length < _total ? 1 : 0),
        itemBuilder: (context, index) {
          if (index >= visible.length) {
            return Padding(
              padding: const EdgeInsets.symmetric(
                vertical: AppSpacing.cardPadding,
              ),
              child: Center(
                child: TextButton(
                  onPressed: _loadingMore ? null : () => _load(),
                  child: Text(_loadingMore ? '加载中…' : '加载更多'),
                ),
              ),
            );
          }
          return _buildCard(visible[index]);
        },
      ),
    );
  }

  Widget _buildCard(ServiceRequestSummary item) {
    final color = _statusColor(item.status);
    return Container(
      margin: const EdgeInsets.only(bottom: AppSpacing.cardGap),
      padding: const EdgeInsets.all(AppSpacing.cardPadding),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.feedDivider),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  item.requestNo.isEmpty ? '申请编号待生成' : item.requestNo,
                  style: const TextStyle(
                    fontFamily: AppTypography.fontFamily,
                    fontSize: AppTypography.metaSize,
                    color: AppColors.textSecondary,
                  ),
                ),
              ),
              _statusPill(
                item.isPendingConfirmation
                    ? '待验收'
                    : serviceRequestStatusLabel(item.status),
                item.isPendingConfirmation ? const Color(0xFFD97706) : color,
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.cardGroupGap),
          Text(
            item.title.isEmpty ? item.itemName : item.title,
            style: const TextStyle(
              fontFamily: AppTypography.fontFamily,
              fontSize: AppTypography.cardTitleSize,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
              height: AppTypography.titleLineHeight,
            ),
          ),
          const SizedBox(height: AppSpacing.titleSummaryGap),
          Text(
            '${item.itemName} · ${_formatTime(item.submittedAt)}提交',
            style: const TextStyle(
              fontFamily: AppTypography.fontFamily,
              fontSize: AppTypography.metaSize,
              color: AppColors.textSecondary,
            ),
          ),
          if (item.workOrderNo != null && item.workOrderNo!.isNotEmpty) ...[
            const SizedBox(height: AppSpacing.cardGroupGap),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: AppColors.background,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  const Icon(
                    Icons.build_circle_outlined,
                    size: 16,
                    color: AppColors.textSecondary,
                  ),
                  const SizedBox(width: AppSpacing.titleSummaryGap),
                  Expanded(
                    child: Text(
                      '工单 ${item.workOrderNo} · ${workOrderStatusLabel(item.workOrderStatus ?? '')}'
                      '${item.assigneeName?.isNotEmpty == true ? ' · ${item.assigneeName}' : ''}',
                      style: const TextStyle(
                        fontFamily: AppTypography.fontFamily,
                        fontSize: AppTypography.metaSize,
                        color: AppColors.textBody,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
          const SizedBox(height: AppSpacing.cardGroupGap),
          Row(
            children: [
              if (item.canUrge) ...[
                OutlinedButton(
                  onPressed: () => _urge(item),
                  child: const Text('催单'),
                ),
                const SizedBox(width: 8),
                if (item.canCancel)
                  TextButton(
                    onPressed: () => _cancel(item),
                    child: const Text(
                      '撤销',
                      style: TextStyle(color: Color(0xFFDC2626)),
                    ),
                  ),
              ],
              const Spacer(),
              TextButton(
                onPressed: () => _openDetail(item),
                child: Text(
                  item.isPendingConfirmation
                      ? '去验收'
                      : item.isClosed
                      ? '查看详情'
                      : '查看进度',
                  style: const TextStyle(
                    color: AppColors.brand,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _statusPill(String label, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.bold,
          color: color,
        ),
      ),
    );
  }

  Color _statusColor(String status) {
    return switch (status) {
      'COMPLETED' => const Color(0xFF059669),
      'REJECTED' => const Color(0xFFDC2626),
      'CANCELLED' => const Color(0xFF64748B),
      'PROCESSING' => AppColors.brand,
      _ => const Color(0xFFD97706),
    };
  }

  String _formatTime(String? raw) {
    if (raw == null || raw.isEmpty) return '-';
    final normalized = raw.replaceFirst('T', ' ');
    return normalized.length > 16 ? normalized.substring(0, 16) : normalized;
  }
}

class _FilterTabItem {
  final String label;

  const _FilterTabItem(this.label);
}
