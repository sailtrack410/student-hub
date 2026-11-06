/// 服务目录模型（GET /api/app/services）
class ServiceItemData {
  final String itemId;
  final String itemCode;
  final String? flowType;
  final String categoryCode;
  final String name;
  final String summary;
  final String? icon;
  final int? slaHours;

  const ServiceItemData({
    required this.itemId,
    required this.itemCode,
    required this.name,
    required this.summary,
    this.categoryCode = '',
    this.flowType,
    this.icon,
    this.slaHours,
  });

  bool get isRepair => flowType != null
      ? flowType == 'REPAIR'
      : itemCode == 'REPAIR-DORM' || itemCode == 'REPAIR-PUBLIC';
  bool get isLeave => flowType == 'LEAVE_APPROVAL';
  bool get isGuide => flowType == 'GUIDE';
  bool get isSchedule => itemCode == 'SCHEDULE-VIEW';
}

class ServiceCategoryData {
  final String categoryId;
  final String categoryCode;
  final String name;
  final String description;
  final List<ServiceItemData> items;

  const ServiceCategoryData({
    required this.categoryId,
    required this.categoryCode,
    required this.name,
    required this.description,
    required this.items,
  });
}
