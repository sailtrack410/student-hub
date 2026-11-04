import 'api_client.dart';
import '../models/message_models.dart';

/// 消息中心 / 统一收件箱 API
class MessageApi {
  final ApiClient apiClient;

  MessageApi(this.apiClient);

  static final MessageApi _instance = MessageApi(ApiClient());

  /// 统一收件箱：流程通知折叠 + 私信 + AI 会话（GET /api/app/messages/inbox）
  static Future<MessageInboxPage?> fetchInbox({
    int pageNum = 1,
    int pageSize = 20,
  }) async {
    try {
      final res = await _instance.apiClient.get(
        '/api/app/messages/inbox',
        query: {'pageNum': '$pageNum', 'pageSize': '$pageSize'},
      );
      final data = res is Map<String, dynamic> ? res : null;
      if (data == null || data['records'] is! List) return null;
      final items = (data['records'] as List)
          .whereType<Map<String, dynamic>>()
          .map(
            (m) => InboxItem(
              sessionId: m['sessionId']?.toString() ?? '',
              sessionType: m['sessionType']?.toString() ?? '',
              peerId: m['peerId']?.toString(),
              conversationId: m['conversationId']?.toString(),
              title: m['title']?.toString() ?? '消息',
              avatarUrl: m['avatarUrl']?.toString(),
              lastContent: m['lastContent']?.toString() ?? '',
              lastTime: m['lastTime']?.toString(),
              unreadCount: _toInt(m['unreadCount']),
              pinned: m['pinned'] == true,
            ),
          )
          .toList();
      return MessageInboxPage(
        items: items,
        total: _toInt(data['total']) == 0
            ? items.length
            : _toInt(data['total']),
      );
    } catch (_) {
      return null;
    }
  }

  /// 四类未读汇总（GET /api/app/messages/category-summary）
  static Future<MessageCategorySummary?> fetchCategorySummary() async {
    try {
      final res = await _instance.apiClient.get(
        '/api/app/messages/category-summary',
      );
      if (res is Map<String, dynamic>) {
        return MessageCategorySummary.fromJson(res);
      }
    } catch (_) {}
    return null;
  }

  /// 社区互动通知分页（GET /api/app/notifications?communityOnly=true）
  static Future<List<CommunityNotificationItem>> fetchCommunityNotifications({
    int pageNum = 1,
    int pageSize = 20,
  }) async {
    try {
      final res = await _instance.apiClient.get(
        '/api/app/notifications',
        query: {
          'pageNum': '$pageNum',
          'pageSize': '$pageSize',
          'communityOnly': 'true',
        },
      );
      final data = res is Map<String, dynamic> ? res : null;
      if (data == null || data['records'] is! List) return const [];
      return (data['records'] as List)
          .whereType<Map<String, dynamic>>()
          .map(CommunityNotificationItem.fromJson)
          .toList();
    } catch (_) {
      return const [];
    }
  }

  /// 全部通知标记已读（POST /api/app/notifications/read-all）
  static Future<bool> markAllNotificationsRead() async {
    try {
      await _instance.apiClient.post(
        '/api/app/notifications/read-all',
        body: {},
      );
      return true;
    } catch (_) {
      return false;
    }
  }

  /// 单条通知已读（POST /api/app/notifications/{id}/read）
  static Future<bool> markNotificationRead(String notificationId) async {
    if (notificationId.isEmpty) return false;
    try {
      await _instance.apiClient.post(
        '/api/app/notifications/$notificationId/read',
        body: {},
      );
      return true;
    } catch (_) {
      return false;
    }
  }

  /// 私信会话已读（POST /api/app/community/messages/read?withUserId=）
  static Future<bool> markMessageRead(String peerId) async {
    if (peerId.isEmpty) return false;
    try {
      await _instance.apiClient.post(
        '/api/app/community/messages/read?withUserId=$peerId',
        body: {},
      );
      return true;
    } catch (_) {
      return false;
    }
  }

  static int _toInt(dynamic v) {
    if (v is num) return v.toInt();
    return int.tryParse(v?.toString() ?? '') ?? 0;
  }
}
