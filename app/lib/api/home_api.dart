import 'api_client.dart';
import '../models/home_dashboard_model.dart';

/// 首页聚合服务与公告接入 API (BFF)
class HomeApi {
  final ApiClient apiClient;

  HomeApi(this.apiClient);

  static final HomeApi _instance = HomeApi(ApiClient());

  /// 静态快捷方法：获取首页综合仪表盘 (GET /api/app/home/dashboard)
  static Future<HomeDashboardData?> fetchDashboard() async {
    return _instance.fetchDashboardRaw();
  }

  /// 静态快捷方法：获取校园公告列表 (GET /api/app/announcements)
  static Future<List<HomeAnnouncementModel>> fetchAnnouncements({
    int pageNum = 1,
    int pageSize = 10,
  }) async {
    return _instance.fetchAnnouncementsRaw(
      pageNum: pageNum,
      pageSize: pageSize,
    );
  }

  /// 获取首页综合仪表盘原生方法
  Future<HomeDashboardData?> fetchDashboardRaw() async {
    try {
      final res = await apiClient.get(
        '/api/app/home/dashboard',
        cancellation: RequestCancellation(),
      );
      if (res is Map<String, dynamic>) {
        if (res['success'] == true && res['data'] is Map<String, dynamic>) {
          return HomeDashboardData.fromJson(
            res['data'] as Map<String, dynamic>,
          );
        }
        if (res['success'] == false || !res.containsKey('greeting')) {
          return null;
        }
        return HomeDashboardData.fromJson(res);
      }
    } catch (_) {
      // 网络或服务异常由上层做优雅降级
    }
    return null;
  }

  /// 获取 APP 校园公告列表原生方法
  Future<List<HomeAnnouncementModel>> fetchAnnouncementsRaw({
    int pageNum = 1,
    int pageSize = 10,
  }) async {
    try {
      final res = await apiClient.get(
        '/api/app/announcements',
        query: {'pageNum': pageNum, 'pageSize': pageSize},
      );
      if (res is Map<String, dynamic>) {
        final records =
            res['records'] ??
            (res['data'] is Map ? res['data']['records'] : null);
        if (records is List) {
          return records
              .map(
                (item) => HomeAnnouncementModel.fromJson(
                  item as Map<String, dynamic>,
                ),
              )
              .toList();
        }
      }
    } catch (_) {}
    return [];
  }
}
