/// 统一收件箱条目（GET /api/app/messages/inbox）
class InboxItem {
  final String sessionId;
  final String
  sessionType; // PROCESS_NOTIFICATION / COMMUNITY_NOTIFICATION / DIRECT_MESSAGE / AI_ASSISTANT
  final String? peerId;
  final String? conversationId;
  final String title;
  final String? avatarUrl;
  final String lastContent;
  final String? lastTime;
  final int unreadCount;
  final bool pinned;

  const InboxItem({
    required this.sessionId,
    required this.sessionType,
    this.peerId,
    this.conversationId,
    required this.title,
    this.avatarUrl,
    required this.lastContent,
    this.lastTime,
    this.unreadCount = 0,
    this.pinned = false,
  });

  bool get isProcess => sessionType == 'PROCESS_NOTIFICATION';
  bool get isCommunity => sessionType == 'COMMUNITY_NOTIFICATION';
  bool get isDirect => sessionType == 'DIRECT_MESSAGE';
  bool get isAi => sessionType == 'AI_ASSISTANT';
}

/// 社区互动通知（GET /api/app/notifications?communityOnly=true）
class CommunityNotificationItem {
  final int notificationId;
  final String notificationType;
  final String title;
  final String content;
  final String? relatedType;
  final int? relatedId;
  final bool read;
  final String createdAt;

  const CommunityNotificationItem({
    required this.notificationId,
    required this.notificationType,
    required this.title,
    required this.content,
    this.relatedType,
    this.relatedId,
    required this.read,
    required this.createdAt,
  });

  factory CommunityNotificationItem.fromJson(Map<String, dynamic> json) {
    return CommunityNotificationItem(
      notificationId: (json['notificationId'] as num?)?.toInt() ?? 0,
      notificationType: json['notificationType']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      content: json['content']?.toString() ?? '',
      relatedType: json['relatedType']?.toString(),
      relatedId: (json['relatedId'] as num?)?.toInt(),
      read: json['read'] == true,
      createdAt: json['createdAt']?.toString() ?? '',
    );
  }
}

/// 消息中心四类未读汇总（GET /api/app/messages/category-summary）
class MessageCategorySummary {
  final int processNoticeUnread;
  final int interactionUnread;
  final int announcementUnread;
  final int aiTodoCount;
  final int totalUnread;

  const MessageCategorySummary({
    this.processNoticeUnread = 0,
    this.interactionUnread = 0,
    this.announcementUnread = 0,
    this.aiTodoCount = 0,
    this.totalUnread = 0,
  });

  factory MessageCategorySummary.fromJson(Map<String, dynamic> json) {
    int read(String key) {
      final v = json[key];
      if (v is num) return v.toInt();
      return int.tryParse(v?.toString() ?? '') ?? 0;
    }

    return MessageCategorySummary(
      processNoticeUnread: read('processNoticeUnread'),
      interactionUnread: read('interactionUnread'),
      announcementUnread: read('announcementUnread'),
      aiTodoCount: read('aiTodoCount'),
      totalUnread: read('totalUnread'),
    );
  }
}

/// 收件箱分页结果
class MessageInboxPage {
  final List<InboxItem> items;
  final int total;

  const MessageInboxPage({required this.items, required this.total});
}
