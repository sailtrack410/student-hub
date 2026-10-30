import 'api_client.dart';
import '../models/user_session.dart';
import '../services/schedule_cache.dart';

/// 课表查询与导入 API 接入服务
class ScheduleApi {
  final ApiClient apiClient;

  ScheduleApi(this.apiClient);

  static final ScheduleApi _instance = ScheduleApi(ApiClient());

  /// 静态快捷方法：获取下一节课
  static Future<Map<String, dynamic>?> fetchNextCourse() async {
    return _instance.fetchNextCourseRaw();
  }

  /// 静态快捷方法：获取周课表
  static Future<Map<String, dynamic>?> fetchWeekSchedule({
    String? semesterCode,
    int? week,
  }) async {
    return _instance.fetchWeekScheduleRaw(
      semesterCode: semesterCode,
      week: week,
    );
  }

  /// 首页当前/下一节课概要 (GET /api/app/schedule/next-course)
  Future<Map<String, dynamic>?> fetchNextCourseRaw() async {
    try {
      final res = await apiClient.get('/api/app/schedule/next-course');
      if (res is Map<String, dynamic>) {
        if (res['success'] == true && res['data'] is Map<String, dynamic>) {
          return res['data'];
        }
        return res;
      }
    } catch (_) {}
    return null;
  }

  /// 按周展开课表详情 (GET /api/app/schedule/view-week)
  Future<Map<String, dynamic>?> fetchWeekScheduleRaw({
    String? semesterCode,
    int? week,
  }) async {
    final accountId = UserSession.currentUserId;
    final requestToken = apiClient.token;
    final cache = ScheduleCache(
      accountId: accountId ?? '',
      baseUrl: apiClient.baseUrl,
    );
    bool current() =>
        UserSession.currentUserId == accountId &&
        apiClient.token == requestToken;
    try {
      final res = await apiClient.get(
        '/api/app/schedule/view-week',
        query: {'semesterCode': ?semesterCode, 'week': ?week},
        cancellation: RequestCancellation(),
      );
      if (!current()) return null;
      final data = res is Map<String, dynamic>
          ? (res['success'] == true && res['data'] is Map<String, dynamic>
                ? res['data'] as Map<String, dynamic>
                : res)
          : null;
      if (data != null && data['days'] is List) {
        final syncedAt = DateTime.now().toUtc().toIso8601String();
        try {
          await cache.save(data, semesterCode: semesterCode, week: week);
        } catch (_) {}
        return current()
            ? {...data, '_offline': false, '_syncedAt': syncedAt}
            : null;
      }
    } catch (_) {}
    if (!current()) return null;
    final cached = await cache.read(semesterCode: semesterCode, week: week);
    return current() ? cached : null;
  }

  /// 调课通知列表 (GET /api/app/schedule/adjustments)
  static Future<List<Map<String, dynamic>>> fetchAdjustments({
    String? semesterCode,
  }) async {
    try {
      final query = semesterCode == null ? '' : '?semesterCode=$semesterCode';
      final res = await _instance.apiClient.get(
        '/api/app/schedule/adjustments$query',
      );
      final list = res is List
          ? res
          : (res is Map<String, dynamic> && res['data'] is List
                ? res['data'] as List
                : const []);
      return list
          .whereType<Map>()
          .map((e) => e.map((k, v) => MapEntry(k.toString(), v)))
          .toList();
    } catch (_) {
      return const [];
    }
  }

  /// 从教务系统导入个人课表 (POST /api/app/schedule/import)
  static Future<ScheduleImportResult> importSchedule({
    String? semesterCode,
  }) async {
    try {
      final query = semesterCode == null ? '' : '?semesterCode=$semesterCode';
      await _instance.apiClient.post(
        '/api/app/schedule/import$query',
        body: {},
      );
      return const ScheduleImportResult();
    } on ApiException catch (error) {
      return ScheduleImportResult(
        errorCode: error.code,
        message:
            error.kind == ApiErrorKind.network ||
                error.kind == ApiErrorKind.timeout
            ? '网络连接失败，请检查网络后重试'
            : error.kind == ApiErrorKind.server
            ? '教务系统暂时不可用，请稍后重试'
            : error.message,
      );
    } catch (_) {
      return const ScheduleImportResult(message: '同步失败，请稍后重试');
    }
  }
}

class ScheduleImportResult {
  final String? errorCode;
  final String? message;

  const ScheduleImportResult({this.errorCode, this.message});

  bool get succeeded => errorCode == null;
  bool get needsReauth => errorCode?.startsWith('JW_') == true;
}
