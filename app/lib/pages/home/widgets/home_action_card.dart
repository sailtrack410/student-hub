import 'package:flutter/material.dart';

import '../../../models/home_dashboard_model.dart';
import '../../../theme/app_theme.dart';

class HomeActionCard extends StatelessWidget {
  final HomeInProgressRequestModel? request;
  final VoidCallback onOpen;

  const HomeActionCard({super.key, this.request, required this.onOpen});

  @override
  Widget build(BuildContext context) {
    final current = request;
    final title = current?.itemName ?? '完成教务认证';
    final subtitle = current == null
        ? '绑定后可查询完整课表与成绩'
        : '${current.statusText} · ${current.orderNo ?? '等待生成工单'}';
    final color = current == null ? AppColors.brand : const Color(0xFFB45309);
    final background = current == null
        ? AppColors.brandSoft
        : AppColors.warningSoft;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.homeHorizontal),
      child: Semantics(
        button: true,
        label: current == null
            ? '去完成教务认证'
            : '$title，${current.statusText}，查看进度',
        child: Material(
          color: background,
          borderRadius: BorderRadius.circular(14),
          child: InkWell(
            key: const ValueKey('home-action-card'),
            onTap: onOpen,
            borderRadius: BorderRadius.circular(14),
            child: Container(
              constraints: const BoxConstraints(
                minHeight: AppSpacing.preferredTapTarget,
              ),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
              child: Row(
                children: [
                  Icon(
                    current == null
                        ? Icons.school_outlined
                        : Icons.assignment_outlined,
                    color: color,
                    size: 24,
                  ),
                  const SizedBox(width: AppSpacing.avatarTextGap),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          title,
                          style: const TextStyle(
                            color: AppColors.textPrimary,
                            fontSize: AppTypography.itemTitleSize,
                            fontWeight: FontWeight.w600,
                            height: 1.45,
                          ),
                        ),
                        const SizedBox(height: 3),
                        Text(
                          subtitle,
                          style: const TextStyle(
                            color: AppColors.textSecondary,
                            fontSize: AppTypography.metaSize,
                            height: AppTypography.summaryLineHeight,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: AppSpacing.compactAvatarGap),
                  Text(
                    current == null ? '去认证' : '查看进度',
                    style: TextStyle(
                      color: color,
                      fontSize: AppTypography.feedSummarySize,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  Icon(Icons.chevron_right_rounded, color: color, size: 20),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
