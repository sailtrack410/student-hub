import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// 主导航的内容高度随字号增长，底部空间交给系统安全区。
class AppBottomNavigation extends StatelessWidget {
  const AppBottomNavigation({
    super.key,
    required this.currentIndex,
    required this.onDestinationSelected,
    this.unreadCount = 0,
  });

  final int currentIndex;
  final ValueChanged<int> onDestinationSelected;
  final int unreadCount;

  static const _labels = ['首页', '社区', '绵宝AI', '消息', '我的'];
  static const _icons = [
    Icons.home_outlined,
    Icons.question_answer_outlined,
    Icons.auto_awesome_rounded,
    Icons.notifications_none_rounded,
    Icons.person_outline_rounded,
  ];
  static const _activeIcons = [
    Icons.home_rounded,
    Icons.question_answer_rounded,
    Icons.auto_awesome_rounded,
    Icons.notifications_rounded,
    Icons.person_rounded,
  ];

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.card,
      child: DecoratedBox(
        decoration: const BoxDecoration(
          border: Border(top: BorderSide(color: AppColors.border, width: 0.6)),
        ),
        child: SafeArea(
          top: false,
          minimum: const EdgeInsets.only(bottom: 8),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 64),
            child: IntrinsicHeight(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: List.generate(_labels.length, (index) {
                  final active = currentIndex == index;
                  final isAi = index == 2;
                  final hasUnread = index == 3 && unreadCount > 0;
                  final color = active
                      ? AppColors.brand
                      : AppColors.textSecondary;
                  return Expanded(
                    child: Semantics(
                      button: true,
                      onTap: () => onDestinationSelected(index),
                      selected: active,
                      label: _labels[index],
                      value: hasUnread ? '$unreadCount 条未读消息' : null,
                      child: ExcludeSemantics(
                        child: InkWell(
                          onTap: () => onDestinationSelected(index),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(vertical: 8),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Badge(
                                  isLabelVisible: hasUnread,
                                  label: Text(
                                    unreadCount > 99 ? '99+' : '$unreadCount',
                                  ),
                                  child: Container(
                                    width: 26,
                                    height: 26,
                                    decoration: isAi
                                        ? const BoxDecoration(
                                            color: AppColors.accentDeep,
                                            shape: BoxShape.circle,
                                          )
                                        : null,
                                    child: Icon(
                                      active
                                          ? _activeIcons[index]
                                          : _icons[index],
                                      size: isAi ? 17 : 24,
                                      color: isAi ? Colors.white : color,
                                    ),
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  _labels[index],
                                  maxLines: 1,
                                  style: TextStyle(
                                    fontSize: 12,
                                    height: 1.2,
                                    fontWeight: active
                                        ? FontWeight.w700
                                        : FontWeight.w500,
                                    color: color,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                  );
                }),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
