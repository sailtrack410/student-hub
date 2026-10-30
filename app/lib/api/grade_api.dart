import 'api_client.dart';
import '../models/grade_models.dart';

class GradeApi {
  static final ApiClient _client = ApiClient();

  static Future<GradeReport?> fetchReport({
    String range = 'ALL',
    String scoreType = 'EFFECTIVE',
  }) async {
    try {
      final res = await _client.get(
        '/api/app/grades?range=$range&scoreType=$scoreType',
      );
      if (res is Map<String, dynamic>) return GradeReport.fromJson(res);
    } catch (_) {}
    return null;
  }
}
