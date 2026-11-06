/// 服务申请 / 工单接口模型。
///
/// 字段与后端 `ServiceRequestSummaryResponse`、`ServiceRequestDetailResponse`、
/// `WorkOrderSummaryResponse` 保持一致；页面不再直接解析裸 Map。
library;

class ServiceRequestSummary {
  final String requestId;
  final String requestNo;
  final String title;
  final String itemName;
  final String status;
  final String? flowType;
  final String? itemCode;
  final String? businessStatus;
  final String? statusLabel;

  /// null means an older response omitted the action contract; [] is authoritative.
  final List<String>? availableActions;
  final String? submittedAt;
  final String? completedAt;
  final String? workOrderId;
  final String? workOrderNo;
  final String? workOrderStatus;
  final String? assigneeName;

  const ServiceRequestSummary({
    required this.requestId,
    required this.requestNo,
    required this.title,
    required this.itemName,
    required this.status,
    this.flowType,
    this.itemCode,
    this.businessStatus,
    this.statusLabel,
    this.availableActions,
    this.submittedAt,
    this.completedAt,
    this.workOrderId,
    this.workOrderNo,
    this.workOrderStatus,
    this.assigneeName,
  });

  factory ServiceRequestSummary.fromJson(Map<String, dynamic> json) {
    return ServiceRequestSummary(
      requestId: json['requestId']?.toString() ?? '',
      requestNo: json['requestNo']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      itemName: json['itemName']?.toString() ?? '',
      status: json['status']?.toString() ?? '',
      flowType: json['flowType']?.toString(),
      itemCode: json['itemCode']?.toString(),
      businessStatus: json['businessStatus']?.toString(),
      statusLabel: json['statusLabel']?.toString(),
      availableActions: _availableActions(json),
      submittedAt: json['submittedAt']?.toString(),
      completedAt: json['completedAt']?.toString(),
      workOrderId: json['workOrderId']?.toString(),
      workOrderNo: json['workOrderNo']?.toString(),
      workOrderStatus: json['workOrderStatus']?.toString(),
      assigneeName: json['assigneeName']?.toString(),
    );
  }

  bool get isRepair => flowType != null
      ? flowType == 'REPAIR'
      : workOrderId != null || workOrderStatus != null;

  bool get isPendingConfirmation =>
      isRepair && workOrderStatus == 'PENDING_CONFIRMATION';

  String get displayStatusLabel => statusLabel?.isNotEmpty == true
      ? statusLabel!
      : isPendingConfirmation
      ? '待验收'
      : serviceRequestStatusLabel(status);

  bool get isClosed =>
      status == 'COMPLETED' || status == 'REJECTED' || status == 'CANCELLED';

  bool _allows(String action, bool legacyFallback) => availableActions != null
      ? availableActions!.contains(action)
      : flowType == null && legacyFallback;

  bool get canUrge => _allows('URGE', !isClosed && !isPendingConfirmation);
  bool get canCancel => _allows('CANCEL', !isClosed && !isPendingConfirmation);
}

class ServiceRequestTimelineItem {
  final String action;
  final String operatorName;
  final String? comment;
  final String? createdAt;

  const ServiceRequestTimelineItem({
    required this.action,
    required this.operatorName,
    this.comment,
    this.createdAt,
  });

  factory ServiceRequestTimelineItem.fromJson(Map<String, dynamic> json) {
    return ServiceRequestTimelineItem(
      action: json['action']?.toString() ?? '',
      operatorName: json['operatorName']?.toString() ?? '',
      comment: json['comment']?.toString(),
      createdAt: json['createdAt']?.toString(),
    );
  }
}

class WorkOrderInfo {
  final String orderId;
  final String orderNo;
  final String requestId;
  final String title;
  final String status;
  final String? priority;
  final String? assigneeName;
  final String? assigneePhone;
  final String? repairAddress;
  final String? arrivalPhotoUrl;
  final String? completionPhotoUrl;
  final List<String> completionPhotoUrls;
  final String? completeRemark;
  final String? confirmationRejectReason;
  final String? createdAt;
  final String? assignedAt;
  final String? startedAt;
  final String? completedAt;
  final int? slaHours;
  final double? remainingHours;
  final bool overdue;
  final bool dueSoon;

  const WorkOrderInfo({
    required this.orderId,
    required this.orderNo,
    required this.requestId,
    required this.title,
    required this.status,
    this.priority,
    this.assigneeName,
    this.assigneePhone,
    this.repairAddress,
    this.arrivalPhotoUrl,
    this.completionPhotoUrl,
    this.completionPhotoUrls = const [],
    this.completeRemark,
    this.confirmationRejectReason,
    this.createdAt,
    this.assignedAt,
    this.startedAt,
    this.completedAt,
    this.slaHours,
    this.remainingHours,
    this.overdue = false,
    this.dueSoon = false,
  });

  factory WorkOrderInfo.fromJson(Map<String, dynamic> json) {
    final urls = (json['completionPhotoUrls'] as List? ?? const [])
        .map((e) => e.toString())
        .where((e) => e.isNotEmpty)
        .toList();
    return WorkOrderInfo(
      orderId: json['orderId']?.toString() ?? '',
      orderNo: json['orderNo']?.toString() ?? '',
      requestId: json['requestId']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      status: json['status']?.toString() ?? '',
      priority: json['priority']?.toString(),
      assigneeName: json['assigneeName']?.toString(),
      assigneePhone: json['assigneePhone']?.toString(),
      repairAddress: json['repairAddress']?.toString(),
      arrivalPhotoUrl: json['arrivalPhotoUrl']?.toString(),
      completionPhotoUrl: json['completionPhotoUrl']?.toString(),
      completionPhotoUrls: urls,
      completeRemark: json['completeRemark']?.toString(),
      confirmationRejectReason: json['confirmationRejectReason']?.toString(),
      createdAt: json['createdAt']?.toString(),
      assignedAt: json['assignedAt']?.toString(),
      startedAt: json['startedAt']?.toString(),
      completedAt: json['completedAt']?.toString(),
      slaHours: (json['slaHours'] as num?)?.toInt(),
      remainingHours: (json['remainingHours'] as num?)?.toDouble(),
      overdue: json['overdue'] == true,
      dueSoon: json['dueSoon'] == true,
    );
  }

  bool get isPendingConfirmation => status == 'PENDING_CONFIRMATION';

  bool get isClosed =>
      status == 'COMPLETED' || status == 'CANCELLED' || status == 'REJECTED';

  List<String> get allPhotoUrls {
    final result = <String>[];
    if (arrivalPhotoUrl != null && arrivalPhotoUrl!.isNotEmpty) {
      result.add(arrivalPhotoUrl!);
    }
    if (completionPhotoUrl != null && completionPhotoUrl!.isNotEmpty) {
      result.add(completionPhotoUrl!);
    }
    result.addAll(completionPhotoUrls.where((e) => e.isNotEmpty));
    return result.toSet().toList();
  }
}

class ServiceEvaluationInfo {
  final int rating;
  final String? comment;
  final String? tags;
  final String? createdAt;

  const ServiceEvaluationInfo({
    required this.rating,
    this.comment,
    this.tags,
    this.createdAt,
  });

  factory ServiceEvaluationInfo.fromJson(Map<String, dynamic> json) {
    return ServiceEvaluationInfo(
      rating: (json['rating'] as num?)?.toInt() ?? 0,
      comment: json['comment']?.toString(),
      tags: json['tags']?.toString(),
      createdAt: json['createdAt']?.toString(),
    );
  }
}

class ServiceRequestDetail {
  final String requestId;
  final String requestNo;
  final String title;
  final String itemName;
  final String? itemIcon;
  final String status;
  final String? flowType;
  final String? itemCode;
  final String? businessStatus;
  final String? statusLabel;
  final List<String>? availableActions;
  final String? leaveType;
  final String? leaveStartAt;
  final String? leaveEndAt;
  final num? leaveDays;
  final String? approvalComment;
  final String? leaveCancelAt;
  final String? leaveCancelStatus;
  final int? slaHours;
  final Map<String, dynamic> formData;
  final String? rejectReason;
  final String? cancelReason;
  final String? submittedAt;
  final String? completedAt;
  final WorkOrderInfo? workOrder;
  final ServiceEvaluationInfo? evaluation;
  final List<ServiceRequestTimelineItem> timeline;

  const ServiceRequestDetail({
    required this.requestId,
    required this.requestNo,
    required this.title,
    required this.itemName,
    this.itemIcon,
    required this.status,
    this.flowType,
    this.itemCode,
    this.businessStatus,
    this.statusLabel,
    this.availableActions,
    this.leaveType,
    this.leaveStartAt,
    this.leaveEndAt,
    this.leaveDays,
    this.approvalComment,
    this.leaveCancelAt,
    this.leaveCancelStatus,
    this.slaHours,
    this.formData = const {},
    this.rejectReason,
    this.cancelReason,
    this.submittedAt,
    this.completedAt,
    this.workOrder,
    this.evaluation,
    this.timeline = const [],
  });

  factory ServiceRequestDetail.fromJson(Map<String, dynamic> json) {
    final rawForm = json['formData'];
    final form = rawForm is Map<String, dynamic>
        ? rawForm
        : rawForm is Map
        ? Map<String, dynamic>.from(rawForm)
        : <String, dynamic>{};
    final rawWorkOrder = json['workOrder'];
    final rawEvaluation = json['evaluation'];
    final rawTimeline = json['timeline'];
    return ServiceRequestDetail(
      requestId: json['requestId']?.toString() ?? '',
      requestNo: json['requestNo']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      itemName: json['itemName']?.toString() ?? '',
      itemIcon: json['itemIcon']?.toString(),
      status: json['status']?.toString() ?? '',
      flowType: json['flowType']?.toString(),
      itemCode: json['itemCode']?.toString(),
      businessStatus: json['businessStatus']?.toString(),
      statusLabel: json['statusLabel']?.toString(),
      availableActions: _availableActions(json),
      leaveType: json['leaveType']?.toString(),
      leaveStartAt: json['leaveStartAt']?.toString(),
      leaveEndAt: json['leaveEndAt']?.toString(),
      leaveDays: json['leaveDays'] is num
          ? json['leaveDays'] as num
          : num.tryParse(json['leaveDays']?.toString() ?? ''),
      approvalComment: json['approvalComment']?.toString(),
      leaveCancelAt: json['leaveCancelAt']?.toString(),
      leaveCancelStatus: json['leaveCancelStatus']?.toString(),
      slaHours: (json['slaHours'] as num?)?.toInt(),
      formData: form,
      rejectReason: json['rejectReason']?.toString(),
      cancelReason: json['cancelReason']?.toString(),
      submittedAt: json['submittedAt']?.toString(),
      completedAt: json['completedAt']?.toString(),
      workOrder: rawWorkOrder is Map
          ? WorkOrderInfo.fromJson(Map<String, dynamic>.from(rawWorkOrder))
          : null,
      evaluation: rawEvaluation is Map
          ? ServiceEvaluationInfo.fromJson(
              Map<String, dynamic>.from(rawEvaluation),
            )
          : null,
      timeline: (rawTimeline as List? ?? const [])
          .whereType<Map>()
          .map(
            (e) => ServiceRequestTimelineItem.fromJson(
              Map<String, dynamic>.from(e),
            ),
          )
          .toList(),
    );
  }

  bool get isRepair =>
      flowType != null ? flowType == 'REPAIR' : workOrder != null;
  bool get isLeave => flowType == 'LEAVE_APPROVAL';
  String get displayStatusLabel => statusLabel?.isNotEmpty == true
      ? statusLabel!
      : serviceRequestStatusLabel(status);

  bool _allows(String action, bool legacyFallback) => availableActions != null
      ? availableActions!.contains(action)
      : flowType == null && legacyFallback;

  bool get canEvaluate =>
      isRepair &&
      _allows(
        'EVALUATE',
        status == 'COMPLETED' && evaluation == null && workOrder != null,
      );
  bool get canConfirm =>
      isRepair &&
      _allows('CONFIRM', workOrder?.status == 'PENDING_CONFIRMATION');
  bool get canRejectCompletion =>
      isRepair &&
      _allows('REJECT_COMPLETION', workOrder?.status == 'PENDING_CONFIRMATION');
  bool get canUrge =>
      _allows('URGE', status == 'SUBMITTED' || status == 'PROCESSING');
  bool get canCancel =>
      _allows('CANCEL', status == 'SUBMITTED' || status == 'PROCESSING');
}

String serviceRequestStatusLabel(String status) {
  return switch (status) {
    'SUBMITTED' => '已提交',
    'PROCESSING' => '处理中',
    'COMPLETED' => '已办结',
    'REJECTED' => '已驳回',
    'CANCELLED' => '已撤销',
    _ => status.isEmpty ? '未知状态' : status,
  };
}

String workOrderStatusLabel(String status) {
  return switch (status) {
    'PENDING_DISPATCH' => '待派单',
    'ASSIGNED' => '已派单',
    'PROCESSING' => '处理中',
    'ONSITE_PHOTO_RECORDED' => '现场已拍照',
    'PENDING_CONFIRMATION' => '待验收',
    'COMPLETED' => '已完成',
    'REJECTED' => '已驳回',
    'CANCELLED' => '已撤销',
    _ => status.isEmpty ? '待派单' : status,
  };
}

List<String>? _availableActions(Map<String, dynamic> json) {
  if (!json.containsKey('availableActions')) return null;
  final raw = json['availableActions'];
  return raw is List ? raw.whereType<String>().toList() : const [];
}
