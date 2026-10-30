/// 作业中心模型（GET /api/app/homework、GET /api/app/homework/{id}）
///
/// 后端 Long 统一序列化为字符串（雪花 ID 防精度丢失），这里一律按字符串解析。
library;

class HomeworkSummary {
  final String homeworkId;
  final String courseName;
  final String title;
  final String? teacherName;
  final DateTime? publishAt;
  final DateTime? dueAt;
  final String status;
  final bool submitted;
  final DateTime? submittedAt;

  const HomeworkSummary({
    required this.homeworkId,
    required this.courseName,
    required this.title,
    this.teacherName,
    this.publishAt,
    this.dueAt,
    required this.status,
    required this.submitted,
    this.submittedAt,
  });

  factory HomeworkSummary.fromJson(Map<String, dynamic> json) {
    return HomeworkSummary(
      homeworkId: json['homeworkId']?.toString() ?? '',
      courseName: json['courseName']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      teacherName: _nullableText(json['teacherName']),
      publishAt: _parseDateTime(json['publishAt']),
      dueAt: _parseDateTime(json['dueAt']),
      status: json['status']?.toString() ?? 'PUBLISHED',
      submitted: json['submitted'] == true,
      submittedAt: _parseDateTime(json['submittedAt']),
    );
  }

  bool get isClosed => status == 'CLOSED';

  bool get isOverdue => dueAt != null && DateTime.now().isAfter(dueAt!);

  /// 列表展示口径：已交 / 未交 / 已截止 / 已关闭
  String get submissionLabel {
    if (submitted) return '已交';
    if (isClosed) return '已关闭';
    if (isOverdue) return '已截止';
    return '未交';
  }
}

class HomeworkPageResult {
  final int pageNum;
  final int pageSize;
  final int total;
  final List<HomeworkSummary> records;

  const HomeworkPageResult({
    required this.pageNum,
    required this.pageSize,
    required this.total,
    required this.records,
  });

  factory HomeworkPageResult.fromJson(Map<String, dynamic> json) {
    final rawRecords = (json['records'] as List?) ?? const [];
    return HomeworkPageResult(
      pageNum: _parseInt(json['pageNum']) ?? 1,
      pageSize: _parseInt(json['pageSize']) ?? rawRecords.length,
      total: _parseInt(json['total']) ?? rawRecords.length,
      records: rawRecords
          .whereType<Map<String, dynamic>>()
          .map(HomeworkSummary.fromJson)
          .toList(),
    );
  }

  bool get hasMore => records.length < total;
}

class HomeworkDetail {
  final String homeworkId;
  final String campusId;
  final String courseName;
  final String title;
  final String content;
  final String? teacherName;
  final DateTime? publishAt;
  final DateTime? dueAt;
  final String status;
  final bool submittable;
  final bool submitted;
  final String? submissionId;
  final String? submissionContent;
  final DateTime? submittedAt;

  const HomeworkDetail({
    required this.homeworkId,
    required this.campusId,
    required this.courseName,
    required this.title,
    required this.content,
    this.teacherName,
    this.publishAt,
    this.dueAt,
    required this.status,
    required this.submittable,
    required this.submitted,
    this.submissionId,
    this.submissionContent,
    this.submittedAt,
  });

  factory HomeworkDetail.fromJson(Map<String, dynamic> json) {
    final dueAt = _parseDateTime(json['dueAt']);
    final status = json['status']?.toString() ?? 'PUBLISHED';
    final closed = status == 'CLOSED';
    final overdue = dueAt != null && DateTime.now().isAfter(dueAt);
    return HomeworkDetail(
      homeworkId: json['homeworkId']?.toString() ?? '',
      campusId: json['campusId']?.toString() ?? '',
      courseName: json['courseName']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      content: json['content']?.toString() ?? '',
      teacherName: _nullableText(json['teacherName']),
      publishAt: _parseDateTime(json['publishAt']),
      dueAt: dueAt,
      status: status,
      // 后端 submittable 为准，同时本地兜底关闭/截止判断，避免时钟或旧缓存误判
      submittable: (json['submittable'] == true) && !closed && !overdue,
      submitted: json['submitted'] == true,
      submissionId: json['submissionId']?.toString(),
      submissionContent: _nullableText(json['submissionContent']),
      submittedAt: _parseDateTime(json['submittedAt']),
    );
  }

  bool get isClosed => status == 'CLOSED';

  bool get isOverdue => dueAt != null && DateTime.now().isAfter(dueAt!);
}

DateTime? _parseDateTime(dynamic value) {
  if (value == null) return null;
  return DateTime.tryParse(value.toString());
}

String? _nullableText(dynamic value) {
  if (value == null) return null;
  final text = value.toString();
  return text.isEmpty ? null : text;
}

int? _parseInt(dynamic value) {
  if (value is int) return value;
  return int.tryParse(value?.toString() ?? '');
}
