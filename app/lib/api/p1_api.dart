import 'api_client.dart';
import '../services/message_state_events.dart';

abstract final class P1Api {
  static ApiClient client = ApiClient();
  static dynamic data(dynamic res) {
    if (res is Map && res['success'] == false) {
      throw Exception(res['message'] ?? '请求失败');
    }
    return res is Map && res.containsKey('data') ? res['data'] : res;
  }

  static Future<Map<String, dynamic>> announcements(int page) async =>
      Map<String, dynamic>.from(
        data(
          await client.get(
            '/api/app/announcements',
            query: {'pageNum': page, 'pageSize': 20},
          ),
        ) as Map,
      );

  static Future<Map<String, dynamic>> announcement(String id) async =>
      Map<String, dynamic>.from(
        data(await client.get('/api/app/announcements/$id')) as Map,
      );
  static Future<void> readAnnouncement(String id) async {
    data(await client.post('/api/app/announcements/$id/read'));
    MessageStateEvents.changed();
  }

  static Future<void> readAnnouncements() async {
    data(await client.post('/api/app/announcements/read-all'));
    MessageStateEvents.changed();
  }

  static Future<List<Map<String, dynamic>>> todos({int page = 1}) async =>
      (data(
        await client.get('/api/app/ai/todos', query: {'pageNum': page}),
      ) as List).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  static Future<void> addTodo(String messageId, int index) async {
    data(
      await client.post(
        '/api/app/ai/todos',
        body: {'messageId': messageId, 'actionIndex': index},
      ),
    );
    MessageStateEvents.changed();
  }

  static Future<void> todoState(String id, String state) async {
    data(await client.put('/api/app/ai/todos/$id', body: {'state': state}));
    MessageStateEvents.changed();
  }
}
