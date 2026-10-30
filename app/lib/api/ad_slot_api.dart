import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../models/community_models.dart';
import '../pages/community/ad_landing_page.dart';
import 'api_client.dart';
import 'community_api.dart';
import 'feature_flags.dart';

/// App 广告位：BANNER（首页）、SERVICE_HALL（服务大厅）、SPLASH（开屏）。
///
/// 数据来源：GET /api/app/community/slot-ads?slot=BANNER|SERVICE_HALL|SPLASH
/// 返回 FeedAdItemResponse 数组（最多 3 条，按优先级倒序），每条带新的 clickId。
///
/// 约束：
/// - `feature.community_ads=false` 时不发请求、不展示、不上报曝光/点击；
/// - 请求失败/无数据一律返回空列表，绝不伪造广告；
/// - 曝光/点击/落地复用 /api/app/community/direct-ads 事件链路，不另建埋点。
enum AdSlot {
  banner('BANNER'),
  serviceHall('SERVICE_HALL'),
  splash('SPLASH');

  const AdSlot(this.apiValue);

  /// 与后端 APP_SLOTS 一致的广告位枚举值。
  final String apiValue;
}

/// 广告位读取 + 事件上报 + 落地打开的公共入口（首页/服务大厅/开屏共用）。
class AdSlotApi {
  AdSlotApi._();

  static const String path = '/api/app/community/slot-ads';

  /// 同一进程内缓存，避免页面重建/滚动重复请求；每个广告位 clickId 保持不变，
  /// 与曝光去重逻辑（同 clickId 只上报一次）配套。
  static const Duration cacheTtl = Duration(minutes: 2);

  static final Map<AdSlot, List<CommunityAd>> _cache = {};
  static final Map<AdSlot, DateTime> _cacheAt = {};

  /// 同广告位并发请求合并：页面初始化与开关到达可能同时触发读取，
  /// 合并后只发一次 HTTP，避免同一展示被重复计量。
  static final Map<AdSlot, Future<List<CommunityAd>>> _inflight = {};

  /// 读取指定广告位。开关关闭时直接返回空列表且不产生 HTTP 请求。
  static Future<List<CommunityAd>> fetchAds(
    AdSlot slot, {
    ApiClient? client,
    bool forceRefresh = false,
  }) {
    if (!FeatureFlags.communityAdsEnabled) {
      return Future<List<CommunityAd>>.value(const <CommunityAd>[]);
    }
    final cachedAt = _cacheAt[slot];
    if (!forceRefresh &&
        cachedAt != null &&
        DateTime.now().difference(cachedAt) < cacheTtl) {
      return Future<List<CommunityAd>>.value(
        _cache[slot] ?? const <CommunityAd>[],
      );
    }
    final pending = _inflight[slot];
    if (!forceRefresh && pending != null) return pending;

    final future = _fetch(slot, client ?? ApiClient());
    _inflight[slot] = future;
    future.whenComplete(() {
      if (identical(_inflight[slot], future)) _inflight.remove(slot);
    });
    return future;
  }

  static Future<List<CommunityAd>> _fetch(AdSlot slot, ApiClient client) async {
    List<CommunityAd> ads = const <CommunityAd>[];
    try {
      final raw = await client.get(
        path,
        query: <String, dynamic>{'slot': slot.apiValue},
      );
      ads = parseAds(raw);
    } catch (_) {
      // 广告位失败静默：不影响首页/服务大厅主流程。
      ads = const <CommunityAd>[];
    }
    // 仅缓存有数据的成功结果；空/失败不缓存，方便下拉刷新或下次进入重试。
    if (ads.isNotEmpty) {
      _cache[slot] = ads;
      _cacheAt[slot] = DateTime.now();
    } else {
      _cache.remove(slot);
      _cacheAt.remove(slot);
    }
    return ads;
  }

  /// 解析 `Result<List<FeedAdItemResponse>>` 的 data；字段缺失的条目安全跳过。
  static List<CommunityAd> parseAds(dynamic raw) {
    final List<dynamic> rows;
    if (raw is List) {
      rows = raw;
    } else if (raw is Map && raw['records'] is List) {
      rows = raw['records'] as List;
    } else {
      return const <CommunityAd>[];
    }
    final ads = <CommunityAd>[];
    for (final row in rows.whereType<Map<String, dynamic>>()) {
      final ad = CommunityAd.fromJson(row);
      if (ad.adId.isEmpty || ad.title.isEmpty) continue;
      ads.add(ad);
    }
    return ads;
  }

  /// 信息流广告过滤：开关关闭时移除 AD 条目，自然内容顺序不变。
  static List<CommunityFeedItem> filterFeedItems(
    List<CommunityFeedItem> items, {
    bool? adsEnabled,
  }) {
    final enabled = adsEnabled ?? FeatureFlags.communityAdsEnabled;
    if (enabled) return items;
    return items.where((item) => !item.isAd).toList(growable: false);
  }

  /// 上报曝光；返回后端确认的 clickId（失败返回 null）。
  static Future<String?> reportImpression(CommunityAd ad) {
    return CommunityApi.reportAdImpression(adId: ad.adId, clickId: ad.clickId);
  }

  /// 点击上报并解析落地配置（失败退回广告自带配置）。
  static Future<AdLandingIntent> resolveClick(CommunityAd ad) async {
    final result = await CommunityApi.reportAdClick(
      adId: ad.adId,
      clickId: ad.clickId,
    );
    return AdLandingIntent.fromClick(ad, result);
  }

  /// 共用点击处理：站内 WebView（可测落地）/ 系统浏览器 / Deeplink。
  /// 调用方只负责传入错误提示回调（或走默认 SnackBar），避免复制整段链路。
  static Future<void> openLanding(
    BuildContext context,
    CommunityAd ad, {
    void Function(String message)? onError,
  }) async {
    final intent = await resolveClick(ad);
    if (!context.mounted) return;

    if (intent.isInAppLanding) {
      if (!AdLandingPage.isSafeHttpUrl(intent.targetUrl)) {
        _notifyError(context, onError, '广告落地链接不安全或格式错误');
        return;
      }
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => AdLandingPage(
            clickId: intent.clickId,
            targetUrl: intent.targetUrl,
            title: ad.title,
            brandName: ad.brandName,
          ),
        ),
      );
      return;
    }

    // EXTERNAL_BROWSER / DEEPLINK：仅记录点击，不调用 loaded/exit。
    final uri = Uri.tryParse(intent.targetUrl);
    if (uri == null) {
      _notifyError(context, onError, '广告链接格式错误');
      return;
    }
    const blockedSchemes = <String>{'javascript', 'file', 'data', 'blob'};
    if (blockedSchemes.contains(uri.scheme)) {
      _notifyError(context, onError, '广告链接不安全');
      return;
    }
    try {
      final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
      if (!context.mounted) return;
      if (!opened) _notifyError(context, onError, '无法打开广告链接');
    } catch (_) {
      if (!context.mounted) return;
      _notifyError(context, onError, '无法打开广告链接');
    }
  }

  static void _notifyError(
    BuildContext context,
    void Function(String message)? onError,
    String message,
  ) {
    if (onError != null) {
      onError(message);
      return;
    }
    if (!context.mounted) return;
    ScaffoldMessenger.maybeOf(context)?.showSnackBar(
      SnackBar(
        content: Text(message),
        duration: const Duration(seconds: 2),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  /// 仅测试使用：清空广告位缓存。
  @visibleForTesting
  static void clearCache() {
    _cache.clear();
    _cacheAt.clear();
    _inflight.clear();
  }
}

/// 点击后解析出的落地意图（后端返回优先，失败回退信息流/广告位自带字段）。
class AdLandingIntent {
  const AdLandingIntent({
    required this.clickId,
    required this.targetUrl,
    required this.landingType,
  });

  factory AdLandingIntent.fromClick(CommunityAd ad, AdClickResult? result) {
    final landingType = (result != null && result.landingType.isNotEmpty)
        ? result.landingType
        : ad.landingType;
    final targetUrl = (result != null && result.targetUrl.isNotEmpty)
        ? result.targetUrl
        : ad.targetUrl;
    final clickId = (result != null && result.clickId.isNotEmpty)
        ? result.clickId
        : ad.clickId;
    return AdLandingIntent(
      clickId: clickId,
      targetUrl: targetUrl,
      landingType: landingType,
    );
  }

  final String clickId;
  final String targetUrl;
  final String landingType;

  bool get isInAppLanding =>
      landingType == 'IN_APP_WEBVIEW' || landingType == 'APP_PAGE';
}
