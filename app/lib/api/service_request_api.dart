import 'api_client.dart';

/// 服务申请与工单闭环 API 接入服务
class ServiceRequestApi {
  final ApiClient apiClient;

  ServiceRequestApi(this.apiClient);

  static final ServiceRequestApi _instance = ServiceRequestApi(ApiClient());

  /// 静态快捷方法：提交完工评价
  static Future<bool> submitEvaluation({
    required String requestId,
    required int rating,
    String? comment,
    List<String> tags = const [],
  }) async {
    return _instance.submitEvaluationRaw(
      requestId,
      rating: rating,
      tags: tags.join(','),
      comment: comment,
    );
  }

  /// 静态快捷方法：退回工单
  static Future<bool> rejectWorkOrder({
    required String workOrderId,
    required String reason,
  }) async {
    return _instance.rejectWorkOrderRaw(workOrderId, reason);
  }

  /// 静态快捷方法：提交服务申请
  static Future<Map<String, dynamic>?> submitRequest({
    required String itemId,
    String? title,
    required Map<String, dynamic> formData,
  }) async {
    return _instance.submitRequestRaw(
      itemId: itemId,
      title: title,
      formData: formData,
    );
  }

  /// 实例快捷方法：提交服务申请 (POST /api/app/service-requests)
  Future<Map<String, dynamic>?> submitRequestRaw({
    required String itemId,
    String? title,
    required Map<String, dynamic> formData,
  }) async {
    try {
      final res = await apiClient.post(
        '/api/app/service-requests',
        body: {
          'itemId': itemId,
          if (title != null && title.isNotEmpty) 'title': title,
          'formData': formData,
        },
      );
      if (res is Map<String, dynamic>) {
        final data = res['data'];
        if (data is Map<String, dynamic>) return data;
        return res;
      }
    } catch (_) {}
    return null;
  }

  /// 实例兼容别名
  Future<Map<String, dynamic>?> submitRequestInstance({
    required String itemId,
    String? title,
    required Map<String, dynamic> formData,
  }) => submitRequestRaw(itemId: itemId, title: title, formData: formData);

  /// 分页查询我的申请 (GET /api/app/service-requests)
  Future<Map<String, dynamic>?> fetchMyRequests({
    int pageNum = 1,
    int pageSize = 10,
    String? status,
  }) async {
    try {
      final query = <String, String>{
        'pageNum': '$pageNum',
        'pageSize': '$pageSize',
        if (status != null && status.isNotEmpty) 'status': status,
      };
      final uri = Uri(
        path: '/api/app/service-requests',
        queryParameters: query,
      ).toString();
      final res = await apiClient.get(uri);
      if (res is Map<String, dynamic>) {
        final data = res['data'];
        if (data is Map<String, dynamic>) return data;
        return res;
      }
    } catch (_) {}
    return null;
  }

  /// 查询申请详情与全流程时间线 (GET /api/app/service-requests/{requestId})
  Future<Map<String, dynamic>?> fetchRequestDetail(String requestId) async {
    try {
      final res = await apiClient.get('/api/app/service-requests/$requestId');
      if (res is Map<String, dynamic>) {
        final data = res['data'];
        if (data is Map<String, dynamic>) return data;
        return res;
      }
    } catch (_) {}
    return null;
  }

  /// 撤销服务申请 (POST /api/app/service-requests/{requestId}/cancel)
  Future<bool> cancelRequest(String requestId, {String? reason}) async {
    try {
      final res = await apiClient.post(
        '/api/app/service-requests/$requestId/cancel',
        body: {'reason': reason ?? '申请人自行撤销'},
      );
      return res is Map<String, dynamic> ? res['success'] != false : true;
    } catch (_) {
      return false;
    }
  }

  /// 提交服务完工评价 (POST /api/app/service-requests/{requestId}/evaluation)
  Future<bool> submitEvaluationRaw(
    String requestId, {
    required int rating,
    required String tags,
    String? comment,
  }) async {
    try {
      final res = await apiClient.post(
        '/api/app/service-requests/$requestId/evaluation',
        body: {
          'rating': rating,
          'tags': tags,
          if (comment != null && comment.isNotEmpty) 'comment': comment,
        },
      );
      return res is Map<String, dynamic> ? res['success'] != false : true;
    } catch (_) {
      return false;
    }
  }

  /// 报修人确认工单完成 (POST /api/app/work-orders/{orderId}/confirm)
  Future<bool> confirmWorkOrder(String orderId) async {
    try {
      final res = await apiClient.post(
        '/api/app/work-orders/$orderId/confirm',
        body: {},
      );
      return res is Map<String, dynamic> ? res['success'] != false : true;
    } catch (_) {
      return false;
    }
  }

  /// 报修人退回工单完工确认 (POST /api/app/work-orders/{orderId}/reject-confirmation)
  Future<bool> rejectWorkOrderRaw(String orderId, String reason) async {
    try {
      final res = await apiClient.post(
        '/api/app/work-orders/$orderId/reject-confirmation',
        body: {'reason': reason},
      );
      return res is Map<String, dynamic> ? res['success'] != false : true;
    } catch (_) {
      return false;
    }
  }

  /// 催单 (POST /api/app/service-requests/{requestId}/urge)
  static Future<Map<String, dynamic>?> urge(String requestId) async {
    try {
      final res = await _instance.apiClient.post(
        '/api/app/service-requests/$requestId/urge',
        body: {},
      );
      if (res is Map<String, dynamic>) {
        final data = res['data'];
        if (data is Map<String, dynamic>) return data;
        return res;
      }
    } catch (_) {}
    return null;
  }
}
