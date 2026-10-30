import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api/achievement_api.dart';

/// 徽章方案 B：客户端字典缓存与同步（契约见 outputs/badge-plan-b-contract.md §5）。
///
/// - 本地用 [SharedPreferences] 缓存完整 catalog JSON、catalogVersion 与用户态 state JSON；
/// - [sync] 先拉 `GET /api/app/achievements/state`，用返回的 catalogVersion 与本地比较，
///   版本不同才拉 `GET /api/app/achievements/catalog?version=本地版本`（changed=true 时落盘）；
/// - 任何异常都静默处理：离线时继续使用旧缓存，不阻塞首页/勋章墙首屏。
class BadgeCatalogSync {
  BadgeCatalogSync._();

  static const String catalogJsonKey = 'badge_catalog_json';
  static const String catalogVersionKey = 'badge_catalog_version';
  static const String stateJsonKey = 'badge_state_json';

  /// 同一时刻只允许一次同步（首页与勋章墙可能同时触发）。
  static Future<void>? _inFlight;

  /// badgeCode -> iconUrl（相对地址保留原样，由 `BadgeIcon` 拼基地址）。
  /// 来自最近一次解析成功的 catalog；服务端未下发 iconUrl 的内置码不在表中。
  static final Map<String, String> _iconUrlByCode = {};

  /// 是否已尝试从本地缓存装载 iconUrl 映射（避免每次 build 重复读 SharedPreferences）。
  static bool _iconUrlsLoaded = false;

  /// iconUrl 映射变化通知：catalog 同步完成后自增，`BadgeIcon` 据此刷新远程图标。
  static final ValueNotifier<int> iconRegistryRevision = ValueNotifier<int>(0);

  /// 拉取并缓存最新勋章字典与用户态；失败静默（保留旧缓存）。
  static Future<void> sync() {
    final running = _inFlight;
    if (running != null) return running;
    final future = _syncSafely().whenComplete(() => _inFlight = null);
    _inFlight = future;
    return future;
  }

  /// 本地缓存的完整字典（含 catalogVersion/categories/badges），无缓存或解析失败返回 null。
  ///
  /// 读取的同时刷新内存 iconUrl 映射，保证页面无需额外调用即可用 [iconUrlFor]。
  static Future<Map<String, dynamic>?> cachedCatalog() async {
    final catalog = await _readJson(catalogJsonKey);
    if (catalog != null) {
      _installCatalog(catalog);
    }
    return catalog;
  }

  /// 服务端为 [badgeCode] 下发的图标地址；未下发/未知返回 null。
  ///
  /// 地址可能是相对路径（如 `/api/common/assets/badges/COMMUNITY_FIRST_POST.png?v=xxx`）。
  static String? iconUrlFor(String badgeCode) {
    if (badgeCode.isEmpty) return null;
    return _iconUrlByCode[badgeCode];
  }

  /// 确保已从本地缓存装载 iconUrl 映射（幂等，可在组件 initState 中调用）。
  static Future<void> ensureIconUrlsLoaded() async {
    if (_iconUrlsLoaded) return;
    await cachedCatalog();
    _iconUrlsLoaded = true;
  }

  /// 测试用：重置内存映射，模拟冷启动。
  @visibleForTesting
  static void resetIconUrlsForTest() {
    _iconUrlByCode.clear();
    _iconUrlsLoaded = false;
    iconRegistryRevision.value = 0;
  }

  /// 用 catalog 的 badges 列表重建 iconUrl 映射；无 badges 列表（如 changed=false）时保留旧值。
  static void _installCatalog(Map<String, dynamic> catalog) {
    final rawBadges = catalog['badges'];
    if (rawBadges is! List) return;

    final next = <String, String>{};
    for (final raw in rawBadges) {
      if (raw is! Map) continue;
      final code = (raw['badgeCode'] ?? '').toString().trim();
      if (code.isEmpty) continue;
      final iconUrl = (raw['iconUrl'] ?? '').toString().trim();
      if (iconUrl.isEmpty) continue;
      next[code] = iconUrl;
    }

    final changed = !mapEquals(_iconUrlByCode, next);
    _iconUrlByCode
      ..clear()
      ..addAll(next);
    _iconUrlsLoaded = true;
    if (changed) {
      iconRegistryRevision.value++;
    }
  }

  /// 本地缓存的用户态（wornBadgeCode/activated/progress），无缓存或解析失败返回 null。
  static Future<Map<String, dynamic>?> cachedState() => _readJson(stateJsonKey);

  /// 本地缓存的字典版本号，用于诊断或跨页面比较。
  static Future<String?> cachedCatalogVersion() async {
    final version = await _readString(catalogVersionKey);
    if (version == null || version.isEmpty) return null;
    return version;
  }

  static Future<void> _syncSafely() async {
    try {
      await _syncOnce();
    } catch (_) {
      // 静默失败：离线或后端未就绪时继续使用旧缓存。
    }
  }

  static Future<void> _syncOnce() async {
    final state = await AchievementApi.fetchState();
    final remoteVersion = _versionOf(state);
    if (state != null) {
      await _writeJson(stateJsonKey, state);
    }
    if (remoteVersion == null) {
      // 拿不到用户态（离线/未登录）时不做版本比较，等下次启动再试。
      return;
    }

    final localVersion = await _readString(catalogVersionKey);
    final hasCachedCatalog = await cachedCatalog() != null;
    if (localVersion == remoteVersion && hasCachedCatalog) {
      return;
    }

    // 本地没有字典时不能带 version（否则服务端会因版本一致而不下发列表）。
    final catalog = await AchievementApi.fetchCatalog(
      version: hasCachedCatalog ? localVersion : null,
    );
    if (catalog == null) {
      return;
    }

    // changed=false 表示服务端认为本地版本已是最新，只更新版本号即可。
    if (catalog['changed'] != false &&
        (catalog['badges'] is List || catalog['categories'] is List)) {
      await _writeJson(catalogJsonKey, catalog);
    }
    // 无论是否落盘都刷新内存映射（changed=false 时没有 badges，_installCatalog 会保留旧值）。
    _installCatalog(catalog);

    final newVersion = _versionOf(catalog) ?? remoteVersion;
    await _writeString(catalogVersionKey, newVersion);
  }

  static String? _versionOf(Map<String, dynamic>? payload) {
    final raw = payload?['catalogVersion'];
    if (raw == null) return null;
    final version = raw.toString().trim();
    return version.isEmpty ? null : version;
  }

  static Future<SharedPreferences?> _prefs() async {
    try {
      return await SharedPreferences.getInstance();
    } catch (_) {
      // 测试环境或插件未注册：按无缓存处理。
      return null;
    }
  }

  static Future<String?> _readString(String key) async {
    try {
      final prefs = await _prefs();
      return prefs?.getString(key);
    } catch (_) {
      return null;
    }
  }

  static Future<void> _writeString(String key, String value) async {
    try {
      final prefs = await _prefs();
      await prefs?.setString(key, value);
    } catch (_) {
      // 缓存写入失败不影响业务。
    }
  }

  static Future<Map<String, dynamic>?> _readJson(String key) async {
    try {
      final raw = await _readString(key);
      if (raw == null || raw.isEmpty) return null;
      final decoded = jsonDecode(raw);
      return decoded is Map<String, dynamic> ? decoded : null;
    } catch (_) {
      return null;
    }
  }

  static Future<void> _writeJson(String key, Map<String, dynamic> value) async {
    try {
      await _writeString(key, jsonEncode(value));
    } catch (_) {
      // 序列化失败时忽略，下一次同步会覆盖。
    }
  }
}
