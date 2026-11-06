import 'package:flutter/material.dart';

import '../../../models/service_catalog_models.dart';
import '../../../theme/app_theme.dart';
import '../home_dashboard_controller.dart';

class HomeServiceHub extends StatelessWidget {
  final List<ServiceItemData> services;
  final bool Function(String id) isPinned;
  final Future<void> Function(String id) onTogglePin;
  final ValueChanged<ServiceItemData> onOpen;
  final VoidCallback onOpenAll;

  const HomeServiceHub({
    super.key,
    required this.services,
    required this.isPinned,
    required this.onTogglePin,
    required this.onOpen,
    required this.onOpenAll,
  });

  @override
  Widget build(BuildContext context) {
    const codes = ['REPAIR-DORM', 'AFFAIR-LEAVE', 'AFFAIR-VENUE'];
    final selected = <ServiceItemData>[];
    for (final item in services) {
      if (selected.length >= 3) break;
      if (isPinned(item.itemId)) selected.add(item);
    }
    for (final code in codes) {
      if (selected.length >= 3) break;
      for (final item in services) {
        if (item.itemCode == code &&
            !selected.any((current) => current.itemId == item.itemId)) {
          selected.add(item);
          break;
        }
      }
    }
    for (final item in services) {
      if (selected.length >= 3) break;
      if (!selected.any((current) => current.itemId == item.itemId)) {
        selected.add(item);
      }
    }

    final remaining = <ServiceItemData>[];
    for (final code in HomeDashboardController.recommendedCodes) {
      if (remaining.length >= 4) break;
      for (final item in services) {
        if (item.itemCode == code &&
            !selected.any((current) => current.itemId == item.itemId) &&
            !remaining.any((current) => current.itemId == item.itemId)) {
          remaining.add(item);
          break;
        }
      }
    }
    for (final item in services) {
      if (remaining.length >= 4) break;
      if (!selected.any((current) => current.itemId == item.itemId) &&
          !remaining.any((current) => current.itemId == item.itemId)) {
        remaining.add(item);
      }
    }

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.homeHorizontal),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            height: 72 * MediaQuery.textScalerOf(context).scale(1),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                for (var i = 0; i < selected.length; i++) ...[
                  if (i > 0) const SizedBox(width: AppSpacing.compactAvatarGap),
                  Expanded(child: _serviceTile(selected[i], i)),
                ],
                if (selected.isNotEmpty)
                  const SizedBox(width: AppSpacing.compactAvatarGap),
                Expanded(child: _allServicesTile()),
              ],
            ),
          ),
          if (remaining.isNotEmpty) ...[
            const SizedBox(height: 5),
            SizedBox(
              height: 24,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: remaining.length,
                separatorBuilder: (_, _) =>
                    const SizedBox(width: AppSpacing.titleSummaryGap),
                itemBuilder: (context, index) {
                  final item = remaining[index];
                  final tileIndex = selected.length + index;
                  return _quickCapsule(item, tileIndex);
                },
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _serviceTile(ServiceItemData item, int index) {
    final asset = switch (item.itemCode) {
      'REPAIR-DORM' || 'REPAIR-PUBLIC' => 'service-repair',
      'AFFAIR-VENUE' => 'service-venue',
      'GUIDE-CONSULT' => 'service-ai',
      _ => 'service-requests',
    };
    final pinned = isPinned(item.itemId);
    return Semantics(
      button: true,
      selected: pinned,
      label: '${item.name}，长按固定或取消固定',
      child: Tooltip(
        message: '${item.name}\n长按固定或取消固定',
        child: InkWell(
          key: ValueKey('home-service-$index'),
          onTap: () => onOpen(item),
          onLongPress: () => onTogglePin(item.itemId),
          borderRadius: BorderRadius.circular(18),
          child: Column(
            children: [
              Expanded(
                child: Stack(
                  children: [
                    Positioned.fill(
                      child: Image.asset(
                        'assets/images/home/$asset.png',
                        fit: BoxFit.contain,
                      ),
                    ),
                    if (pinned)
                      const Positioned(
                        right: 2,
                        top: 2,
                        child: Icon(
                          Icons.push_pin_rounded,
                          size: 14,
                          color: AppColors.brand,
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 2),
              Text(
                HomeDashboardController.shortLabel(item),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: AppTypography.metaSize,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _quickCapsule(ServiceItemData item, int index) {
    final pinned = isPinned(item.itemId);
    return Semantics(
      button: true,
      selected: pinned,
      label: '${item.name}，长按固定或取消固定',
      child: InkWell(
        key: ValueKey('home-service-$index'),
        onTap: () => onOpen(item),
        onLongPress: () => onTogglePin(item.itemId),
        borderRadius: BorderRadius.circular(12),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
          decoration: BoxDecoration(
            color: AppColors.card,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: const Color(0xFFD9E7FF)),
          ),
          alignment: Alignment.center,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                _capsuleIcon(item.itemCode),
                size: 13,
                color: AppColors.brand,
              ),
              const SizedBox(width: 4),
              Text(
                HomeDashboardController.shortLabel(item),
                style: const TextStyle(
                  fontSize: AppTypography.feedMetaSize,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textPrimary,
                  height: 1.1,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  IconData _capsuleIcon(String code) => switch (code) {
    'SCHEDULE-VIEW' => Icons.calendar_month_rounded,
    'REPAIR-DORM' || 'REPAIR-PUBLIC' => Icons.handyman_rounded,
    'AFFAIR-LEAVE' => Icons.description_rounded,
    'AFFAIR-VENUE' => Icons.location_on_rounded,
    'CERT-ENROLL' => Icons.workspace_premium_rounded,
    'GUIDE-CONSULT' => Icons.auto_awesome_rounded,
    _ => Icons.apps_rounded,
  };

  Widget _allServicesTile() {
    return Semantics(
      button: true,
      label: '全部服务，进入服务大厅',
      child: InkWell(
        key: const ValueKey('home-all-services'),
        onTap: onOpenAll,
        borderRadius: BorderRadius.circular(18),
        child: Column(
          children: [
            Expanded(
              child: Center(
                child: Container(
                  width: 52,
                  height: 52,
                  decoration: BoxDecoration(
                    color: AppColors.brandSoft,
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: const Icon(
                    Icons.grid_view_rounded,
                    color: AppColors.brand,
                    size: 25,
                  ),
                ),
              ),
            ),
            const SizedBox(height: 2),
            const Text(
              '全部服务',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: TextStyle(
                color: AppColors.brand,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
