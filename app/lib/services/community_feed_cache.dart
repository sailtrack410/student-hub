import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// C3 信息流快照缓存（缓存先显）：
/// 首页第一页拉取成功后把原始 records 落盘，下次冷启动先解析旧快照秒开，
/// 随后网络刷新整体替换；刷新失败时页面只在空列表才进错误态，快照自然兜底。
/// 只存原始 JSON 条目，回放走与在线完全相同的解析管线，字段不失真；
/// 快照损坏一律按无缓存处理，绝不挡住新数据。
class CommunityFeedCache {
  CommunityFeedCache._();

  static const _key = 'community.feed.snapshot.records.v1';

  /// 保存第一页原始条目（来自接口原始返回的 records 数组，天然可 JSON 化）
  static Future<void> saveRawRecords(List<dynamic>? records) async {
    if (records == null || records.isEmpty) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_key, jsonEncode(records));
    } catch (_) {
      // 落盘失败只影响下次秒开，不打断正常信息流
    }
  }

  /// 读取上次快照的原始条目；无缓存或数据损坏返回 null
  static Future<List<dynamic>?> loadRawRecords() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(_key);
      if (raw == null || raw.isEmpty) return null;
      final decoded = jsonDecode(raw);
      return decoded is List && decoded.isNotEmpty ? decoded : null;
    } catch (_) {
      return null;
    }
  }
}
