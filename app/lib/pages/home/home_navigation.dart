import 'package:flutter/material.dart';

import '../../api/auth_service.dart';
import '../../models/service_catalog_models.dart';
import '../../models/user_session.dart';
import '../assistant/ai_assistant_page.dart';
import '../assistant/ai_disabled_page.dart';
import '../community/community_home_page.dart';
import '../message/notification_center_page.dart';
import '../profile/profile_page.dart';
import '../schedule/schedule_page.dart';
import '../service/repair_apply_page.dart';
import '../service/service_apply_page.dart';

abstract final class HomeNavigation {
  static Widget tab(
    int index, {
    required bool aiEnabled,
    required UserSession session,
    required AuthService authService,
    required ValueChanged<int> switchTab,
    required VoidCallback onUnreadChanged,
  }) => switch (index) {
    1 => CommunityHomePage(currentTabIndex: index, onTabChanged: (_) {}),
    2 =>
      aiEnabled
          ? AIAssistantPage(currentTabIndex: index, onTabChanged: (_) {})
          : AiDisabledPage(currentTabIndex: index),
    3 => NotificationCenterPage(
      embedded: true,
      onOpenAi: () => switchTab(2),
      onUnreadChanged: onUnreadChanged,
    ),
    4 => ProfilePage(
      session: session,
      authService: authService,
      currentTabIndex: index,
      onTabChanged: (_) {},
    ),
    _ => const SizedBox.shrink(),
  };

  static void openService(
    BuildContext context,
    ServiceItemData item, {
    required VoidCallback onOpenAi,
  }) {
    if (item.itemCode == 'GUIDE-CONSULT') {
      onOpenAi();
      return;
    }
    final Widget destination = item.itemCode.startsWith('REPAIR')
        ? RepairApplyPage(title: item.name, itemId: item.itemId)
        : item.itemCode == 'SCHEDULE-VIEW'
        ? const SchedulePage()
        : ServiceApplyPage(itemId: item.itemId, itemName: item.name);
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => destination));
  }
}
