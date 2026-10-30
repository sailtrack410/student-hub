import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/ad_slot_api.dart';
import '../../api/api_client.dart';
import '../../api/feature_flags.dart';
import '../../api/service_catalog_api.dart';
import '../../api/service_request_api.dart';
import '../../models/community_models.dart';
import '../../models/service_catalog_models.dart';
import '../../theme/app_theme.dart';
import '../../widgets/community_ad_card.dart';
import '../../widgets/skeleton_shimmer.dart';
import '../../widgets/app_status_panel.dart';
import '../homework/homework_page.dart';
import '../schedule/schedule_page.dart';
import 'my_requests_page.dart';
import 'repair_apply_page.dart';
import 'service_apply_page.dart';

/// APP-服务大厅 (画布 5:1191)
/// 顶部搜索 + 高频快捷办事 + 分类服务组 + 我的申请入口；数据走 /api/app/services。
class ServiceHallPage extends StatefulWidget {
  /// 二级入口：显示左上返回键、隐藏底栏
  final bool showBack;

  /// 测试/预览注入
  final List<ServiceCategoryData>? initialCategories;
  final int? initialRequestCount;

  const ServiceHallPage({
    super.key,
    this.showBack = false,
    this.initialCategories,
    this.initialRequestCount,
  });

  @override
  State<ServiceHallPage> createState() => _ServiceHallPageState();
}

class _ServiceHallPageState extends State<ServiceHallPage> {
  final TextEditingController _searchController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  List<ServiceCategoryData> _categories = [];
  String _keyword = '';
  int _myRequestCount = 0;
  bool _loading = true;
  bool _error = false;

  /// 服务大厅 SERVICE_HALL 广告位（开关关闭/无数据时为 null）
  CommunityAd? _serviceHallAd;
  final Set<String> _reportedAdImpressions = <String>{};

  @override
  void initState() {
    super.initState();
    FeatureFlags.revision.addListener(_onFeatureFlagsChanged);
    unawaited(_loadSlotAd());
    if (widget.initialCategories != null) {
      _categories = List<ServiceCategoryData>.of(widget.initialCategories!);
      _myRequestCount = widget.initialRequestCount ?? 0;
      _loading = false;
      _error = false;
    } else {
      _load();
    }
  }

  @override
  void dispose() {
    FeatureFlags.revision.removeListener(_onFeatureFlagsChanged);
    _searchController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  /// 功能开关到达/变化：广告关闭时立刻移除广告卡，开启时补拉。
  void _onFeatureFlagsChanged() {
    if (!mounted) return;
    if (!FeatureFlags.communityAdsEnabled) {
      if (_serviceHallAd != null) setState(() => _serviceHallAd = null);
      return;
    }
    unawaited(_loadSlotAd());
  }

  /// 拉取 SERVICE_HALL 广告位；开关关闭/无数据/失败时保持不展示。
  Future<void> _loadSlotAd({bool forceRefresh = false}) async {
    await FeatureFlags.waitReady();
    if (!mounted || !FeatureFlags.communityAdsEnabled) return;
    final ads = await AdSlotApi.fetchAds(
      AdSlot.serviceHall,
      forceRefresh: forceRefresh,
    );
    if (!mounted || !FeatureFlags.communityAdsEnabled) return;
    final ad = ads.isEmpty ? null : ads.first;
    if (_serviceHallAd?.clickId == ad?.clickId) return;
    setState(() => _serviceHallAd = ad);
    if (ad != null) _reportSlotImpression(ad);
  }

  void _reportSlotImpression(CommunityAd ad) {
    if (ad.clickId.isEmpty || !FeatureFlags.communityAdsEnabled) return;
    if (!_reportedAdImpressions.add(ad.clickId)) return;
    unawaited(AdSlotApi.reportImpression(ad));
  }

  Future<void> _openSlotAd(CommunityAd ad) {
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

  Future<void> _refreshAll() async {
    await Future.wait([_load(), _loadSlotAd(forceRefresh: true)]);
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = false;
    });
    final client = ApiClient();
    final catalog = await ServiceCatalogApi(client).fetchCatalog();
    final requests = await ServiceRequestApi(client)
        .fetchMyRequests(pageNum: 1, pageSize: 1);
    if (!mounted) return;

    final categories = <ServiceCategoryData>[];
    if (catalog != null) {
      for (final raw in catalog) {
        final items = ((raw['items'] as List?) ?? const [])
            .whereType<Map<String, dynamic>>()
            .map(
              (m) => ServiceItemData(
                itemId: m['itemId']?.toString() ?? '',
                itemCode: m['itemCode']?.toString() ?? '',
                categoryCode: raw['categoryCode']?.toString() ?? '',
                name: m['itemName']?.toString() ?? '',
                summary: m['summary']?.toString() ?? '',
                icon: m['icon']?.toString(),
                slaHours: (m['slaHours'] as num?)?.toInt(),
              ),
            )
            .toList();
        if (items.isEmpty) continue;
        categories.add(
          ServiceCategoryData(
            categoryId: raw['categoryId']?.toString() ?? '',
            categoryCode: raw['categoryCode']?.toString() ?? '',
            name: raw['categoryName']?.toString() ?? '',
            description: raw['description']?.toString() ?? '',
            items: items,
          ),
        );
      }
    }

    setState(() {
      _categories = categories;
      _myRequestCount = requests == null
          ? 0
          : int.tryParse(requests['total']?.toString() ?? '') ?? 0;
      _loading = false;
      _error = catalog == null;
    });
  }

  List<ServiceItemData> get _allItems =>
      _categories.expand((c) => c.items).toList();

  List<ServiceItemData> get _searchResults {
    final keyword = _keyword.trim();
    if (keyword.isEmpty) return const [];
    return _allItems
        .where(
          (item) =>
              item.name.contains(keyword) || item.summary.contains(keyword),
        )
        .toList();
  }

  ServiceItemData? _findItem(String code) {
    for (final item in _allItems) {
      if (item.itemCode == code) return item;
    }
    return null;
  }

  String _groupTitle(String code) {
    switch (code) {
      case 'REPAIR':
        return '后勤报修服务';
      case 'AFFAIR':
        return '学生事务办理';
      case 'CERTIFICATE':
        return '证明开具与指引';
      case 'GUIDE':
        return '咨询与指引';
      case 'APP':
        return '校园应用';
      default:
        return '服务事项';
    }
  }

  String _badgeText(ServiceItemData item) {
    if (item.isRepair) return '${item.slaHours ?? 24}h响应';
    switch (item.itemCode) {
      case 'AFFAIR-LEAVE':
        return '线上审批';
      case 'AFFAIR-VENUE':
        return '即时报备';
      case 'CERT-ENROLL':
        return '秒级生成';
      case 'GUIDE-CONSULT':
        return '在线咨询';
      case 'SCHEDULE-VIEW':
        return '查看';
      default:
        return item.slaHours != null ? '${item.slaHours}h响应' : '在线办理';
    }
  }

  Color _badgeColor(ServiceItemData item) {
    if (item.isRepair) return const Color(0xFF2563EB);
    switch (item.itemCode) {
      case 'AFFAIR-LEAVE':
      case 'AFFAIR-VENUE':
        return const Color(0xFF059669);
      case 'CERT-ENROLL':
        return const Color(0xFF0F766E);
      case 'GUIDE-CONSULT':
        return const Color(0xFFF59E0B);
      default:
        return AppColors.textSecondary;
    }
  }

  void _openMyRequests() {
    Navigator.of(context)
        .push(MaterialPageRoute(builder: (_) => const MyRequestsPage()))
        .then((_) => _load());
  }

  void _openItem(ServiceItemData item) {
    if (item.isRepair) {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) =>
              RepairApplyPage(title: item.name, itemId: item.itemId),
        ),
      );
      return;
    }
    if (item.isSchedule) {
      Navigator.of(context)
          .push(MaterialPageRoute(builder: (_) => const SchedulePage()));
      return;
    }
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) =>
            ServiceApplyPage(itemId: item.itemId, itemName: item.name),
      ),
    );
  }

  void _openHomeworkCenter() {
    Navigator.of(context)
        .push(MaterialPageRoute(builder: (_) => const HomeworkPage()));
  }

  Widget _buildHomeworkEntry() {
    return GestureDetector(
      onTap: _openHomeworkCenter,
      behavior: HitTestBehavior.opaque,
      child: Container(
        padding: const EdgeInsets.fromLTRB(14, 13, 12, 13),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFFF1F5F9)),
        ),
        child: Row(
          children: [
            Container(
              width: 38,
              constraints: const BoxConstraints(minHeight: 48),
              decoration: BoxDecoration(
                color: AppColors.brandSoft,
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(
                Icons.menu_book_rounded,
                size: 20,
                color: AppColors.brand,
              ),
            ),
            const SizedBox(width: 12),
            const Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '作业中心',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: AppColors.textPrimary,
                    ),
                  ),
                  SizedBox(height: 3),
                  Text(
                    '查看课程作业与截止时间，在线提交作业',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      color: Color(0xFF64748B),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            const Text(
              '去提交',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: AppColors.brand,
              ),
            ),
            const Icon(
              Icons.chevron_right_rounded,
              size: 18,
              color: Color(0xFFCBD5E1),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        bottom: true,
        child: Column(
          children: [
            _buildHeader(),
            _buildSearchBar(),
            Expanded(child: _buildBody()),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      color: Colors.white,
      padding: const EdgeInsets.fromLTRB(12, 6, 16, 6),
      child: Row(
        children: [
          if (widget.showBack)
            IconButton(
              onPressed: () => Navigator.of(context).maybePop(),
              icon: const Icon(
                Icons.arrow_back_ios_new_rounded,
                size: 18,
                color: AppColors.textPrimary,
              ),
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
            )
          else
            const SizedBox(width: 4),
          Expanded(
            child: Text(
              '服务大厅',
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 17,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
          ),
          GestureDetector(
            onTap: _openMyRequests,
            behavior: HitTestBehavior.opaque,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
              child: Text(
                _myRequestCount > 0 ? '我的申请 ($_myRequestCount)' : '我的申请',
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: AppColors.brand,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSearchBar() {
    return Container(
      color: Colors.white,
      padding: const EdgeInsets.fromLTRB(16, 2, 16, 10),
      child: Container(
        constraints: const BoxConstraints(minHeight: 48),
        padding: const EdgeInsets.symmetric(horizontal: 12),
        decoration: BoxDecoration(
          color: const Color(0xFFF1F5F9),
          borderRadius: BorderRadius.circular(19),
        ),
        child: Row(
          children: [
            const Icon(
              Icons.search_rounded,
              size: 18,
              color: Color(0xFF94A3B8),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: TextField(
                controller: _searchController,
                onChanged: (value) => setState(() => _keyword = value),
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                ),
                decoration: const InputDecoration(
                  isDense: true,
                  filled: false, // 灰底胶囊内关闭主题白底
                  border: InputBorder.none,
                  hintText: '搜索办事服务，如教室报修、在读证明...',
                  hintStyle: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 13,
                    color: Color(0xFF64748B),
                  ),
                ),
              ),
            ),
            if (_keyword.isNotEmpty)
              GestureDetector(
                onTap: () {
                  _searchController.clear();
                  setState(() => _keyword = '');
                },
                child: const Icon(
                  Icons.close_rounded,
                  size: 16,
                  color: Color(0xFF94A3B8),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return SkeletonShimmer(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
          children: List.generate(
            3,
            (index) => Container(
              height: 150,
              margin: const EdgeInsets.only(bottom: 12),
              decoration: BoxDecoration(
                color: const Color(0xFFE2E8F0),
                borderRadius: BorderRadius.circular(16),
              ),
            ),
          ),
        ),
      );
    }
    if (_error) {
      return AppStatusPanel(title: '服务目录加载失败', onRetry: _load, retryLabel: '重新加载');
    }
    if (_categories.isEmpty) return const AppStatusPanel(title: '暂无可用服务');
    if (_keyword.trim().isNotEmpty) return _buildSearchResult();
    return RefreshIndicator(
      onRefresh: _refreshAll,
      color: AppColors.brand,
      child: ListView(
        controller: _scrollController,
        physics: const AlwaysScrollableScrollPhysics(
          parent: BouncingScrollPhysics(),
        ),
        // 广告卡自带 16pt 外边距，故列表本身不再横向内边距，其余内容单独补齐。
        padding: const EdgeInsets.fromLTRB(0, 14, 0, 28),
        children: [
          // 服务大厅顶部广告位：开关关闭/无数据时不占位
          if (_serviceHallAd != null) _buildSlotAdCard(_serviceHallAd!),
          _padded(_buildQuickSection()),
          const SizedBox(height: 16),
          if (FeatureFlags.homeworkEnabled) ...[
            // feature.homework=false 时隐藏作业入口（不进入作业中心，也不请求作业接口）
            _padded(_buildHomeworkEntry()),
            const SizedBox(height: 16),
          ],
          ..._categories.map((c) => _padded(_buildCategoryCard(c))),
          const SizedBox(height: 8),
          const Center(
            child: Text(
              '绵阳城市学院 · 一站式服务大厅',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: Color(0xFF64748B),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _padded(Widget child) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: child,
    );
  }

  /// 服务大厅广告卡：复用社区广告卡样式（推广标识 + 品牌 + CTA）。
  Widget _buildSlotAdCard(CommunityAd ad) {
    return CommunityAdCard(
      ad: ad,
      onTap: () => _openSlotAd(ad),
      onCtaTap: () => _openSlotAd(ad),
    );
  }

  Widget _buildQuickSection() {
    const quickCodes = [
      'REPAIR-DORM',
      'REPAIR-PUBLIC',
      'AFFAIR-LEAVE',
      'CERT-ENROLL',
    ];
    final items = quickCodes
        .map(_findItem)
        .whereType<ServiceItemData>()
        .toList();
    if (items.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '高频快捷办事',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 14,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        const SizedBox(height: 10),
        Row(
          children: items.map((item) {
            final style = _quickStyle(item);
            return Expanded(
              child: GestureDetector(
                onTap: () => _openItem(item),
                behavior: HitTestBehavior.opaque,
                child: Container(
                  height: 32,
                  margin: const EdgeInsets.only(right: 8),
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: style.$1,
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Text(
                    _quickLabel(item),
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: style.$2,
                    ),
                  ),
                ),
              ),
            );
          }).toList(),
        ),
      ],
    );
  }

  String _quickLabel(ServiceItemData item) {
    switch (item.itemCode) {
      case 'REPAIR-DORM':
        return '宿舍报修';
      case 'REPAIR-PUBLIC':
        return '教室报修';
      case 'AFFAIR-LEAVE':
        return '请假申请';
      case 'CERT-ENROLL':
        return '在读证明';
      default:
        return item.name;
    }
  }

  (Color, Color) _quickStyle(ServiceItemData item) {
    switch (item.itemCode) {
      case 'REPAIR-DORM':
        return (const Color(0xFFEFF6FF), const Color(0xFF2563EB));
      case 'REPAIR-PUBLIC':
        return (const Color(0xFFF0FDFA), const Color(0xFF0D9488));
      case 'AFFAIR-LEAVE':
        return (const Color(0xFFFFFBEB), const Color(0xFFD97706));
      case 'CERT-ENROLL':
        return (const Color(0xFFF0FDF4), const Color(0xFF16A34A));
      default:
        return (const Color(0xFFF1F5F9), const Color(0xFF475569));
    }
  }

  Widget _buildCategoryCard(ServiceCategoryData category) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFF1F5F9)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 12, 14, 2),
            child: Text(
              _groupTitle(category.categoryCode),
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 15,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
          ),
          for (int i = 0; i < category.items.length; i++) ...[
            if (i > 0)
              const Divider(
                height: 1,
                thickness: 1,
                color: Color(0xFFF8FAFC),
                indent: 14,
                endIndent: 14,
              ),
            _buildItemRow(category.items[i]),
          ],
          const SizedBox(height: 4),
        ],
      ),
    );
  }

  Widget _buildItemRow(ServiceItemData item) {
    return InkWell(
      onTap: () => _openItem(item),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 12, 12),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.name,
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: AppColors.textPrimary,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    item.summary,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      height: 1.4,
                      color: Color(0xFF64748B),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Text(
              _badgeText(item),
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: _badgeColor(item),
              ),
            ),
            const SizedBox(width: 2),
            const Icon(
              Icons.chevron_right_rounded,
              size: 18,
              color: Color(0xFFCBD5E1),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSearchResult() {
    final results = _searchResults;
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
      children: [
        Text(
          '搜索结果 (${results.length})',
          style: const TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 13,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        const SizedBox(height: 10),
        if (results.isEmpty)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 48),
            child: Center(
              child: Text(
                '未找到相关办事服务',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  color: Color(0xFF64748B),
                ),
              ),
            ),
          )
        else
          Container(
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: const Color(0xFFF1F5F9)),
            ),
            child: Column(
              children: [
                for (int i = 0; i < results.length; i++) ...[
                  if (i > 0)
                    const Divider(
                      height: 1,
                      thickness: 1,
                      color: Color(0xFFF8FAFC),
                      indent: 14,
                      endIndent: 14,
                    ),
                  _buildItemRow(results[i]),
                ],
              ],
            ),
          ),
      ],
    );
  }
}
