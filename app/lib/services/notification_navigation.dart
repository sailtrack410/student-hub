import 'package:flutter/material.dart';

import '../api/message_api.dart';
import '../api/community_api.dart';
import '../models/message_models.dart';
import '../pages/community/post_detail_page.dart';
import '../pages/service/work_order_detail_page.dart';
import '../pages/service/assigned_work_order_detail_page.dart';

abstract final class NotificationNavigation {
  static Future<void> open(
    BuildContext context,
    CommunityNotificationItem item,
  ) async {
    try {
      final target = await MessageApi.resolveNotificationTarget(
        '${item.notificationId}',
      );
      if (!context.mounted) return;
      if (target == null) throw Exception('关联内容暂时不可用');
      final id = target['id']?.toString();
      Widget? page;
      switch (target['type']) {
        case 'SERVICE_REQUEST':
          if (id != null) page = WorkOrderDetailPage(requestId: id);
        case 'WORK_ORDER':
          if (id != null) page = AssignedWorkOrderDetailPage(orderId: id);
        case 'COMMUNITY_POST':
          if (id != null) {
            final result = await CommunityApi.fetchPostDetailResult(id);
            if (!context.mounted) return;
            if (result.detail == null) throw Exception('帖子已删除或暂时不可访问');
            page = PostDetailPage(post: result.detail!.post);
          }
      }
      if (!item.read &&
          !await MessageApi.markNotificationRead('${item.notificationId}')) {
        throw Exception('标记已读失败，请重试');
      }
      if (!context.mounted) return;
      if (page != null) {
        await Navigator.of(context)
            .push(MaterialPageRoute(builder: (_) => page!));
      } else {
        await showDialog<void>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: Text(item.title),
            content: Text(item.content),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(),
                child: const Text('关闭'),
              ),
            ],
          ),
        );
      }
    } catch (error) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    }
  }
}
