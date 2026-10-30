import '../models/homework_models.dart';
import 'api_client.dart';

/// 作业中心 API 接入服务（列表 / 详情 / 提交）
///
/// 所有方法失败时抛出 [ApiException]，由页面区分「加载失败」「提交失败」并给出重试。
class HomeworkApi {
  final ApiClient apiClient;

  HomeworkApi(this.apiClient);

  static final HomeworkApi _instance = HomeworkApi(ApiClient());

  /// 分页查询作业 (GET /api/app/homework)
  Future<HomeworkPageResult> fetchHomeworks({
    int pageNum = 1,
    int pageSize = 10,
  }) async {
    final data = await apiClient.get(
      '/api/app/homework',
      query: {'pageNum': '$pageNum', 'pageSize': '$pageSize'},
    );
    if (data is! Map<String, dynamic>) {
      throw const ApiException(code: 'DATA_INVALID', message: '作业数据格式异常');
    }
    return HomeworkPageResult.fromJson(data);
  }

  /// 查询作业详情 (GET /api/app/homework/{id})
  Future<HomeworkDetail> fetchDetail(String homeworkId) async {
    final data = await apiClient.get('/api/app/homework/$homeworkId');
    if (data is! Map<String, dynamic>) {
      throw const ApiException(code: 'DATA_INVALID', message: '作业数据格式异常');
    }
    return HomeworkDetail.fromJson(data);
  }

  /// 提交作业 (POST /api/app/homework/{id}/submit)
  Future<HomeworkDetail> submit(String homeworkId, String content) async {
    final data = await apiClient.post(
      '/api/app/homework/$homeworkId/submit',
      body: {'content': content},
    );
    if (data is! Map<String, dynamic>) {
      throw const ApiException(code: 'DATA_INVALID', message: '提交结果格式异常');
    }
    return HomeworkDetail.fromJson(data);
  }

  /// 静态快捷方法：提交作业（供其他页面复用）
  static Future<HomeworkDetail> submitHomework({
    required String homeworkId,
    required String content,
  }) {
    return _instance.submit(homeworkId, content);
  }
}
