import 'package:flutter/foundation.dart';

import 'api_client.dart';

/// App 功能开关（GET /api/common/features）。
///
/// 约定：
/// - 登录/会话建立后由 [load] 拉取；请求失败保留默认值，不阻塞启动与页面渲染。
/// - 页面通过 [enabled] / 语义化 getter 读取，并监听 [revision] 在开关到达/变化后刷新。
/// - 关掉 `feature.community_ads` 时，客户端不请求、不展示、不上报任何广告。
class FeatureFlags {
  FeatureFlags._();

  static const String keyCommunityAds = 'feature.community_ads';
  static const String keyHomework = 'feature.homework';
  static const String keyAi = 'feature.ai';
  static const String keyMaintenance = 'feature.maintenance';

  /// 接口失败或字段缺失时使用的默认值。
  static const Map<String, bool> defaults = <String, bool>{
    keyCommunityAds: true,
    keyHomework: true,
    keyAi: true,
    keyMaintenance: false,
  };

  static Map<String, bool> _flags = Map<String, bool>.unmodifiable(defaults);
  static bool _loaded = false;
  static String? _campusId;
  static DateTime? _generatedAt;
  static Future<void> _loadFuture = Future<void>.value();

  /// 每次加载结束（成功或失败）自增，供页面感知开关就绪/变化。
  static final ValueNotifier<int> revision = ValueNotifier<int>(0);

  static bool get isLoaded => _loaded;
  static String? get campusId => _campusId;
  static DateTime? get generatedAt => _generatedAt;
  static Map<String, bool> get flags => _flags;

  /// 读取开关；未下发时使用 [fallback]。
  static bool enabled(String key, {bool fallback = true}) =>
      _flags[key] ?? fallback;

  static bool get communityAdsEnabled => enabled(keyCommunityAds);

  static bool get homeworkEnabled => enabled(keyHomework);

  static bool get aiEnabled => enabled(keyAi);

  static bool get maintenanceEnabled =>
      enabled(keyMaintenance, fallback: false);

  /// 拉取并缓存开关。失败（网络/鉴权/格式异常）时保留当前值，仅自增 [revision]。
  static Future<void> load(
    ApiClient client, {
    Duration timeout = const Duration(seconds: 4),
  }) {
    _loadFuture = _load(client, timeout);
    return _loadFuture;
  }

  static Future<void> _load(ApiClient client, Duration timeout) async {
    try {
      final data = await client.get('/api/common/features').timeout(timeout);
      if (data is Map<String, dynamic>) {
        _applyPayload(data);
      }
    } catch (_) {
      // 开关读取失败不阻塞启动：保留默认值/上一次成功值。
    } finally {
      revision.value++;
    }
  }

  static void _applyPayload(Map<String, dynamic> data) {
    final rawFlags = data['flags'];
    if (rawFlags is! Map) return;
    final parsed = <String, bool>{};
    rawFlags.forEach((key, value) {
      final name = key.toString();
      if (value is bool) {
        parsed[name] = value;
      } else if (value is num) {
        parsed[name] = value != 0;
      } else if (value is String) {
        final normalized = value.trim().toLowerCase();
        if (normalized == 'true' || normalized == '1') {
          parsed[name] = true;
        } else if (normalized == 'false' || normalized == '0') {
          parsed[name] = false;
        }
      }
    });
    if (parsed.isEmpty) return;
    _flags = Map<String, bool>.unmodifiable(<String, bool>{
      ...defaults,
      ...parsed,
    });
    _loaded = true;
    _campusId = data['campusId']?.toString();
    _generatedAt = DateTime.tryParse(data['generatedAt']?.toString() ?? '');
  }

  /// 等待最近的 [load] 完成（最多 [timeout]），用于启动阶段广告位等依赖开关的请求。
  /// 未调用过 [load] 时立即返回。
  static Future<void> waitReady({
    Duration timeout = const Duration(seconds: 2),
  }) async {
    try {
      await _loadFuture.timeout(timeout);
    } catch (_) {
      // 超时/失败都按默认值继续。
    }
  }

  /// 仅测试使用：注入开关值。
  @visibleForTesting
  static void applyForTest(Map<String, bool> overrides, {bool loaded = true}) {
    _flags = Map<String, bool>.unmodifiable(<String, bool>{
      ...defaults,
      ...overrides,
    });
    _loaded = loaded;
  }

  /// 仅测试使用：恢复默认值并清空加载状态。
  @visibleForTesting
  static void resetForTest() {
    _flags = Map<String, bool>.unmodifiable(defaults);
    _loaded = false;
    _campusId = null;
    _generatedAt = null;
    _loadFuture = Future<void>.value();
  }
}
