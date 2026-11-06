/// 首页综合仪表盘 (BFF) 数据模型
/// 对应后端 GET /api/app/home/dashboard 聚合下发的各子模块
class HomeDashboardData {
  final String greeting;
  final AcademicInfoModel? academicInfo;
  final NextScheduleModel? nextSchedule;
  final HomeAnnouncementModel? announcement;
  final HomeInProgressRequestModel? inProgressRequest;
  final int unreadMessageCount;

  const HomeDashboardData({
    required this.greeting,
    this.academicInfo,
    this.nextSchedule,
    this.announcement,
    this.inProgressRequest,
    this.unreadMessageCount = 0,
  });

  HomeDashboardData copyWith({int? unreadMessageCount}) {
    return HomeDashboardData(
      greeting: greeting,
      academicInfo: academicInfo,
      nextSchedule: nextSchedule,
      announcement: announcement,
      inProgressRequest: inProgressRequest,
      unreadMessageCount: unreadMessageCount ?? this.unreadMessageCount,
    );
  }

  factory HomeDashboardData.fromJson(Map<String, dynamic> json) {
    return HomeDashboardData(
      greeting: json['greeting']?.toString() ?? '',
      academicInfo:
          json['academicInfo'] != null &&
              json['academicInfo'] is Map<String, dynamic>
          ? AcademicInfoModel.fromJson(
              json['academicInfo'] as Map<String, dynamic>,
            )
          : null,
      nextSchedule:
          json['nextSchedule'] != null &&
              json['nextSchedule'] is Map<String, dynamic>
          ? NextScheduleModel.fromJson(
              json['nextSchedule'] as Map<String, dynamic>,
            )
          : null,
      announcement:
          json['announcement'] != null &&
              json['announcement'] is Map<String, dynamic>
          ? HomeAnnouncementModel.fromJson(
              json['announcement'] as Map<String, dynamic>,
            )
          : null,
      inProgressRequest:
          json['inProgressRequest'] != null &&
              json['inProgressRequest'] is Map<String, dynamic>
          ? HomeInProgressRequestModel.fromJson(
              json['inProgressRequest'] as Map<String, dynamic>,
            )
          : null,
      unreadMessageCount: json['unreadMessageCount'] is num
          ? (json['unreadMessageCount'] as num).toInt()
          : int.tryParse(json['unreadMessageCount']?.toString() ?? '') ?? 0,
    );
  }
}

/// 学业信息条模型
class AcademicInfoModel {
  final String? campusName;
  final String? weekDay;
  final int academicWeek;
  final String formattedDate;

  const AcademicInfoModel({
    this.campusName,
    this.weekDay,
    this.academicWeek = 0,
    required this.formattedDate,
  });

  factory AcademicInfoModel.fromJson(Map<String, dynamic> json) {
    return AcademicInfoModel(
      campusName: json['campusName']?.toString(),
      weekDay: json['weekDay']?.toString(),
      academicWeek: (json['academicWeek'] as num?)?.toInt() ?? 0,
      formattedDate: json['formattedDate']?.toString() ?? '绵阳城市学院',
    );
  }
}

/// 下一节/当前课程卡模型
class NextScheduleModel {
  final String courseName;
  final String? teacher;
  final String? location;
  final int startSection;
  final int endSection;
  final String? timeRange;
  final String status; // "ONGOING" or "UPCOMING"

  const NextScheduleModel({
    required this.courseName,
    this.teacher,
    this.location,
    this.startSection = 1,
    this.endSection = 2,
    this.timeRange,
    this.status = 'UPCOMING',
  });

  bool get isOngoing => status == 'ONGOING';

  String get sectionText => '第$startSection-$endSection节';

  String get displayText {
    final range = timeRange != null && timeRange!.isNotEmpty
        ? timeRange!
        : sectionText;
    final loc = location != null && location!.isNotEmpty ? location! : '教学楼';
    return '$range · $loc';
  }

  factory NextScheduleModel.fromJson(Map<String, dynamic> json) {
    return NextScheduleModel(
      courseName: json['courseName']?.toString() ?? '待定课程',
      teacher: json['teacher']?.toString(),
      location: json['location']?.toString(),
      startSection: (json['startSection'] as num?)?.toInt() ?? 1,
      endSection: (json['endSection'] as num?)?.toInt() ?? 2,
      timeRange: json['timeRange']?.toString(),
      status: json['status']?.toString() ?? 'UPCOMING',
    );
  }
}

/// 校园公告卡模型
class HomeAnnouncementModel {
  final String id;
  final String title;
  final String tag;
  final String? url;
  final bool read;
  final String? summary;
  final String? publishDate;
  final String? author;

  const HomeAnnouncementModel({
    required this.id,
    required this.title,
    this.tag = '通知',
    this.url,
    this.read = false,
    this.summary,
    this.publishDate,
    this.author,
  });

  factory HomeAnnouncementModel.fromJson(Map<String, dynamic> json) {
    return HomeAnnouncementModel(
      id: json['id']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      tag: json['typeText']?.toString() ?? json['tag']?.toString() ?? '通知',
      read: json['read'] == true,
      url: json['url']?.toString(),
      summary: json['summary']?.toString(),
      publishDate: json['publishDate']?.toString(),
      author: json['author']?.toString(),
    );
  }
}

/// 进行中服务申请卡模型
class HomeInProgressRequestModel {
  final String requestId;
  final String? orderNo;
  final String itemName;
  final String currentStatus;
  final String? flowType;
  final String? businessStatus;
  final String? statusLabel;
  final String? submittedAt;

  const HomeInProgressRequestModel({
    required this.requestId,
    this.orderNo,
    required this.itemName,
    required this.currentStatus,
    this.flowType,
    this.businessStatus,
    this.statusLabel,
    this.submittedAt,
  });

  String get statusText {
    if (statusLabel?.isNotEmpty == true) return statusLabel!;
    switch (currentStatus) {
      case 'SUBMITTED':
        return flowType == 'REPAIR' ? '待派单' : '已提交';
      case 'PROCESSING':
        return '处理中';
      case 'COMPLETED':
        return '已办结';
      case 'REJECTED':
        return '已驳回';
      case 'CANCELLED':
        return '已撤销';
      default:
        return currentStatus;
    }
  }

  factory HomeInProgressRequestModel.fromJson(Map<String, dynamic> json) {
    return HomeInProgressRequestModel(
      requestId: json['requestId']?.toString() ?? '',
      orderNo: json['orderNo']?.toString(),
      itemName: json['itemName']?.toString() ?? '服务申请',
      currentStatus: json['currentStatus']?.toString() ?? 'PROCESSING',
      flowType: json['flowType']?.toString(),
      businessStatus: json['businessStatus']?.toString(),
      statusLabel: json['statusLabel']?.toString(),
      submittedAt: json['submittedAt']?.toString(),
    );
  }
}
