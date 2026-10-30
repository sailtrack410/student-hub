import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// 仅保存真实课表，账号及服务地址隔离；没有身份时不读写缓存。
class ScheduleCache {
  final String accountId;
  final String baseUrl;

  const ScheduleCache({required this.accountId, required this.baseUrl});

  String _key(String? semesterCode, int? week) {
    final scope = sha256.convert(utf8.encode('$baseUrl|$accountId'));
    return 'schedule.v1.$scope.${semesterCode ?? "current"}.${week ?? "latest"}';
  }

  Future<void> save(
    Map<String, dynamic> data, {
    String? semesterCode,
    int? week,
  }) async {
    if (accountId.isEmpty) return;
    final prefs = await SharedPreferences.getInstance();
    final encoded = jsonEncode({
      'version': 1,
      'syncedAt': DateTime.now().toUtc().toIso8601String(),
      'data': data,
    });
    await prefs.setString(_key(semesterCode, week), encoded);
    final actualWeek = (data['week'] as num?)?.toInt();
    final actualSemester = data['semesterCode']?.toString() ?? semesterCode;
    if (actualWeek != null) {
      await prefs.setString(_key(actualSemester, actualWeek), encoded);
      // 当前学期的显式周查询复用同一真实快照。
      if (semesterCode == null) {
        await prefs.setString(_key(null, actualWeek), encoded);
      }
    }
  }

  Future<Map<String, dynamic>?> read({String? semesterCode, int? week}) async {
    if (accountId.isEmpty) return null;
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(_key(semesterCode, week));
      if (raw == null) return null;
      final snapshot = jsonDecode(raw);
      if (snapshot is! Map<String, dynamic> ||
          snapshot['version'] != 1 ||
          snapshot['data'] is! Map<String, dynamic> ||
          DateTime.tryParse('${snapshot['syncedAt']}') == null) {
        return null;
      }
      return {
        ...snapshot['data'] as Map<String, dynamic>,
        '_offline': true,
        '_syncedAt': snapshot['syncedAt'],
      };
    } catch (_) {
      return null;
    }
  }
}
