import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/user_session.dart';

/// 一份快照只属于同一服务、账号、校区和查询。
class CommunityFeedCacheScope {
  const CommunityFeedCacheScope({
    required this.baseUrl,
    required this.accountId,
    this.campusId,
    this.sectionId,
    this.sort = 'HOT',
    this.keyword = '',
  });

  final String baseUrl;
  final String accountId;
  final String? campusId;
  final int? sectionId;
  final String sort;
  final String keyword;

  String get storageKey {
    final identity = jsonEncode([
      baseUrl.replaceFirst(RegExp(r'/+$'), ''),
      accountId,
      campusId,
      sectionId,
      sort.toUpperCase(),
      keyword.trim(),
    ]);
    return '${CommunityFeedCache.prefix}${sha256.convert(utf8.encode(identity))}';
  }
}

class CommunityFeedCache {
  CommunityFeedCache._();

  static const prefix = 'community.feed.snapshot.v2.';
  static const maxAge = Duration(hours: 1);
  static int _epoch = 0;

  static CommunityFeedCacheScope? currentScope({
    required String baseUrl,
    int? sectionId,
    String sort = 'HOT',
    String keyword = '',
  }) {
    final accountId = UserSession.currentUserId;
    if (accountId == null || accountId.isEmpty) return null;
    return CommunityFeedCacheScope(
      baseUrl: baseUrl,
      accountId: accountId,
      campusId: UserSession.currentCampusId,
      sectionId: sectionId,
      sort: sort,
      keyword: keyword,
    );
  }

  static Future<void> saveRawRecords(
    CommunityFeedCacheScope scope,
    List<dynamic> records,
  ) async {
    final epoch = _epoch;
    final encoded = jsonEncode({
      'savedAt': DateTime.now().toUtc().toIso8601String(),
      'records': records,
    });
    try {
      final prefs = await SharedPreferences.getInstance();
      if (epoch != _epoch) return;
      await prefs.setString(scope.storageKey, encoded);
    } catch (_) {
      // 缓存故障不影响在线内容。
    }
  }

  static Future<List<dynamic>?> loadRawRecords(
    CommunityFeedCacheScope scope, {
    DateTime? now,
  }) async {
    final epoch = _epoch;
    try {
      final prefs = await SharedPreferences.getInstance();
      if (epoch != _epoch) return null;
      final raw = prefs.getString(scope.storageKey);
      if (raw == null) return null;
      final data = jsonDecode(raw);
      if (data is! Map<String, dynamic>) return null;
      final savedAt = DateTime.tryParse('${data['savedAt']}');
      final age = (now ?? DateTime.now()).toUtc().difference(
        savedAt ?? DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
      );
      if (savedAt == null || age.isNegative || age > maxAge) return null;
      return data['records'] is List ? data['records'] as List : null;
    } catch (_) {
      return null;
    }
  }

  /// 同步作废旧写入；登录身份在清理完成后才会重新激活。
  static Future<void> clear() async {
    _epoch++;
    try {
      final prefs = await SharedPreferences.getInstance();
      final keys = prefs
          .getKeys()
          .where(
            (key) =>
                key.startsWith(prefix) ||
                key == 'community.feed.snapshot.records.v1',
          )
          .toList();
      await Future.wait(keys.map(prefs.remove));
    } catch (_) {
      // 缓存故障不阻止退出登录。
    }
  }
}
