import 'api_client.dart';

/// 荣誉勋章与成就模块 API 接入服务。
///
/// 注意：`ApiClient` 已完成统一 Result 解包（success=false 会抛 [ApiException]，
/// 成功时直接返回 `data`），因此本文件不再判断 `res['success']`。
class AchievementApi {
  final ApiClient apiClient;

  AchievementApi(this.apiClient);

  static final AchievementApi _instance = AchievementApi(ApiClient());

  /// 当前 API 基地址（用于拼接服务端下发的相对图标地址）。
  static String get baseUrl => _instance.apiClient.baseUrl;

  /// 静态快捷方法：查询成长档案
  static Future<Map<String, dynamic>?> fetchProfile() async {
    return _instance.fetchProfileRaw();
  }

  /// 静态快捷方法：查询勋章列表（旧接口，仅兼容保留）
  static Future<List<Map<String, dynamic>>?> fetchBadges() async {
    return _instance.fetchBadgesRaw();
  }

  /// 静态快捷方法：查询带版本号的勋章字典
  static Future<Map<String, dynamic>?> fetchCatalog({String? version}) async {
    return _instance.fetchCatalogRaw(version: version);
  }

  /// 静态快捷方法：查询用户态（已激活/当前佩戴/进度）
  static Future<Map<String, dynamic>?> fetchState() async {
    return _instance.fetchStateRaw();
  }

  /// 静态快捷方法：佩戴或卸下勋章
  ///
  /// [action] 传 `WEAR` / `UNWEAR`；缺省时按 [badgeCode] 是否为空推断。
  static Future<bool> wearBadge(String? badgeCode, {String? action}) async {
    return _instance.wearBadgeRaw(badgeCode, action: action);
  }

  /// 查询成长档案与等级概览 (GET /api/app/achievements/profile)
  Future<Map<String, dynamic>?> fetchProfileRaw() async {
    try {
      final res = await apiClient.get('/api/app/achievements/profile');
      return res is Map<String, dynamic> ? res : null;
    } catch (_) {}
    return null;
  }

  /// 查询勋章墙与解锁进度 (GET /api/app/achievements/badges)
  ///
  /// 兼容旧接口：`data` 形如 `{catalogVersion, badgeVersion, badges: [...]}`。
  /// 新代码请优先使用 [fetchCatalogRaw] + [fetchStateRaw]。
  Future<List<Map<String, dynamic>>?> fetchBadgesRaw() async {
    try {
      final res = await apiClient.get('/api/app/achievements/badges');
      if (res is Map<String, dynamic>) {
        return _asMapList(res['badges']);
      }
      if (res is List) {
        return _asMapList(res);
      }
    } catch (_) {}
    return null;
  }

  /// 查询带版本号的勋章字典 (GET /api/app/achievements/catalog)
  ///
  /// 请求携带本地版本 [version]；服务端认为版本一致时返回 `{catalogVersion, changed: false}`。
  Future<Map<String, dynamic>?> fetchCatalogRaw({String? version}) async {
    try {
      final res = await apiClient.get(
        '/api/app/achievements/catalog',
        query: (version == null || version.isEmpty)
            ? null
            : <String, dynamic>{'version': version},
      );
      return res is Map<String, dynamic> ? res : null;
    } catch (_) {}
    return null;
  }

  /// 查询用户态：已激活勋章、当前佩戴、全部启用勋章进度 (GET /api/app/achievements/state)
  Future<Map<String, dynamic>?> fetchStateRaw() async {
    try {
      final res = await apiClient.get('/api/app/achievements/state');
      return res is Map<String, dynamic> ? res : null;
    } catch (_) {}
    return null;
  }

  /// 佩戴或卸下勋章 (POST /api/app/achievements/wear)
  ///
  /// 成功返回 true；失败/网络异常返回 false（接口成功时 `data` 为 null，不解析 data）。
  Future<bool> wearBadgeRaw(String? badgeCode, {String? action}) async {
    try {
      final normalized = action?.trim().toUpperCase();
      final effectiveAction = (normalized == 'WEAR' || normalized == 'UNWEAR')
          ? normalized
          : ((badgeCode == null || badgeCode.isEmpty) ? 'UNWEAR' : 'WEAR');
      final body = <String, dynamic>{'action': effectiveAction};
      if (badgeCode != null && badgeCode.isNotEmpty) {
        body['badgeCode'] = badgeCode;
      }
      await apiClient.post('/api/app/achievements/wear', body: body);
      return true;
    } catch (_) {
      return false;
    }
  }

  static List<Map<String, dynamic>> _asMapList(dynamic raw) {
    if (raw is! List) return const [];
    return raw
        .whereType<Map>()
        .map((item) => Map<String, dynamic>.from(item))
        .toList();
  }
}
