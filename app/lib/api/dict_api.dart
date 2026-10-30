import 'api_client.dart';

/// 服务端统一字典：报修社区、楼栋和故障类型均由管理端维护。
class DictApi {
  static Future<List<Map<String, dynamic>>> fetchItems(String typeCode) async {
    final res = await ApiClient().get('/api/app/dicts/$typeCode');
    if (res is! List) return const [];
    return res.whereType<Map<String, dynamic>>().toList();
  }
}
