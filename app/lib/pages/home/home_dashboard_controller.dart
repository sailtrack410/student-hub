import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../api/api_client.dart';
import '../../api/community_api.dart';
import '../../api/home_api.dart';
import '../../api/service_catalog_api.dart';
import '../../models/community_models.dart';
import '../../models/home_dashboard_model.dart';
import '../../models/service_catalog_models.dart';

abstract interface class HomeDashboardGateway {
  Future<HomeDashboardData?> dashboard();
  Future<List<ServiceItemData>?> services();
  Future<List<CommunityPost>?> posts();
  Future<List<HomeAnnouncementModel>?> announcements();
}

class ApiHomeDashboardGateway implements HomeDashboardGateway {
  final ApiClient client;
  ApiHomeDashboardGateway(this.client);

  @override
  Future<HomeDashboardData?> dashboard() => HomeApi(client).fetchDashboardRaw();

  @override
  Future<List<ServiceItemData>?> services() async {
    final catalog = await ServiceCatalogApi(client).fetchCatalog();
    if (catalog == null) return null;
    return [
      for (final category in catalog)
        for (final raw
            in ((category['items'] as List?) ?? const [])
                .whereType<Map<String, dynamic>>())
          if ('${raw['itemId'] ?? ''}'.isNotEmpty &&
              '${raw['itemName'] ?? ''}'.isNotEmpty)
            ServiceItemData(
              itemId: '${raw['itemId']}',
              itemCode: '${raw['itemCode'] ?? ''}',
              flowType: raw['flowType']?.toString(),
              categoryCode: '${category['categoryCode'] ?? ''}',
              name: '${raw['itemName']}',
              summary: '${raw['summary'] ?? ''}',
              icon: raw['icon']?.toString(),
              slaHours: (raw['slaHours'] as num?)?.toInt(),
            ),
    ];
  }

  @override
  Future<List<CommunityPost>?> posts() async =>
      (await CommunityApi.fetchPostPage(
        category: '推荐',
        sort: 'HOT',
        pageSize: 3,
      ))?.posts;

  @override
  Future<List<HomeAnnouncementModel>?> announcements() async {
    final response = await client.get(
      '/api/app/announcements',
      query: {'pageNum': 1, 'pageSize': 5},
    );
    final data = response is Map<String, dynamic>
        ? (response['data'] ?? response)
        : null;
    final records = data is Map ? data['records'] : null;
    return records is List
        ? records
              .whereType<Map<String, dynamic>>()
              .map(HomeAnnouncementModel.fromJson)
              .toList()
        : null;
  }
}

/// 每个区域独立加载、保留旧数据并拒绝迟到的旧响应。
class HomeSection<T> {
  T? data;
  bool loading = false;
  bool failed = false;
  int generation = 0;
}

class HomeDashboardController extends ChangeNotifier {
  final HomeDashboardGateway gateway;
  final String accountId;
  final String baseUrl;
  final dashboard = HomeSection<HomeDashboardData>();
  final services = HomeSection<List<ServiceItemData>>();
  final posts = HomeSection<List<CommunityPost>>();
  final announcements = HomeSection<List<HomeAnnouncementModel>>();
  List<String> _pinned = [];
  bool _disposed = false;
  bool savingPins = false;
  int _pinGeneration = 0;

  HomeDashboardController({
    required this.gateway,
    required this.accountId,
    required this.baseUrl,
  });

  String get _pinKey =>
      'home.services.v1.${sha256.convert(utf8.encode('$baseUrl|$accountId'))}';
  bool isPinned(String id) => _pinned.contains(id);

  Future<void> loadPins() async {
    final generation = _pinGeneration;
    try {
      final prefs = await SharedPreferences.getInstance();
      if (!_disposed && generation == _pinGeneration) {
        _pinned = prefs.getStringList(_pinKey) ?? [];
      }
    } catch (_) {}
    _changed();
  }

  Future<bool> togglePin(String id) async {
    if (savingPins || _disposed) return false;
    savingPins = true;
    _pinGeneration++;
    final before = [..._pinned];
    _pinned = isPinned(id)
        ? (_pinned.where((item) => item != id).toList())
        : [..._pinned, id];
    _changed();
    var saved = false;
    try {
      saved = await (await SharedPreferences.getInstance()).setStringList(
        _pinKey,
        _pinned,
      );
    } catch (_) {}
    if (!saved) _pinned = before;
    savingPins = false;
    _changed();
    return saved;
  }

  static const recommendedCodes = [
    'SCHEDULE-VIEW',
    'REPAIR-DORM',
    'REPAIR-PUBLIC',
    'AFFAIR-LEAVE',
    'CERT-ENROLL',
    'AFFAIR-VENUE',
    'GUIDE-CONSULT',
  ];

  List<ServiceItemData> get orderedServices {
    final indexed = (services.data ?? <ServiceItemData>[]).indexed.toList();
    int rank(ServiceItemData item) {
      final pinned = _pinned.indexOf(item.itemId);
      if (pinned >= 0) return pinned;
      final recommended = recommendedCodes.indexOf(item.itemCode);
      return 1000 + (recommended < 0 ? recommendedCodes.length : recommended);
    }

    indexed.sort((a, b) {
      final order = rank(a.$2).compareTo(rank(b.$2));
      return order == 0 ? a.$1.compareTo(b.$1) : order;
    });
    return indexed.map((entry) => entry.$2).toList();
  }

  static String shortLabel(ServiceItemData item) => switch (item.itemCode) {
    'SCHEDULE-VIEW' => '我的课表',
    'REPAIR-DORM' => '宿舍报修',
    'REPAIR-PUBLIC' => '公共报修',
    'AFFAIR-LEAVE' => '请假申请',
    'CERT-ENROLL' => '在读证明',
    'AFFAIR-VENUE' => '场地申请',
    'GUIDE-CONSULT' => '校园咨询',
    _ => item.name,
  };

  Future<void> refresh() => Future.wait([
    reloadDashboard(),
    reloadServices(),
    reloadPosts(),
    reloadAnnouncements(),
  ]);
  Future<void> reloadDashboard() => _load(dashboard, gateway.dashboard);
  Future<void> reloadServices() => _load(services, gateway.services);
  Future<void> reloadPosts() => _load(posts, gateway.posts);
  Future<void> reloadAnnouncements() =>
      _load(announcements, gateway.announcements);

  Future<void> _load<T>(
    HomeSection<T> section,
    Future<T?> Function() fetch,
  ) async {
    if (_disposed) return;
    final generation = ++section.generation;
    section.loading = true;
    section.failed = false;
    _changed();
    T? result;
    try {
      result = await fetch();
    } catch (_) {}
    if (_disposed || generation != section.generation) return;
    section.loading = false;
    section.failed = result == null;
    if (result != null) section.data = result;
    _changed();
  }

  void incrementUnread() {
    final data = dashboard.data;
    if (data != null) {
      dashboard.data = data.copyWith(
        unreadMessageCount: data.unreadMessageCount + 1,
      );
    }
    _changed();
  }

  void _changed() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
