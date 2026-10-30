import '../models/user_session.dart';
import 'api_client.dart';

/// 服务目录 API 接入服务
class ServiceCatalogApi {
  final ApiClient apiClient;

  ServiceCatalogApi(this.apiClient);

  /// 查询当前校区上线的全部分类与服务项 (GET /api/app/services)
  Future<List<Map<String, dynamic>>?> fetchCatalog() async {
    try {
      final res = await apiClient.get('/api/app/services');
      final data = res is List
          ? res
          : (res is Map<String, dynamic> && res['data'] is List
                ? res['data'] as List
                : null);
      if (data != null) {
        return List<Map<String, dynamic>>.from(data);
      }
    } catch (_) {
      // 优雅降级由上层处理
    }
    return null;
  }

  /// 查询单个服务项详情与表单Schema (GET /api/app/services/{itemId})
  Future<Map<String, dynamic>?> fetchServiceDetail(String itemId) async {
    try {
      final res = await apiClient.get('/api/app/services/$itemId');
      if (res is Map<String, dynamic>) {
        if (res['success'] == true && res['data'] is Map<String, dynamic>) {
          return res['data'] as Map<String, dynamic>;
        }
        return res;
      }
    } catch (_) {}
    return null;
  }
}
