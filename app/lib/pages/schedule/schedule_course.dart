/// 青果作息兜底；新同步数据优先使用学校原始 timeRange。
String scheduleSectionTimeRange(int start, int end) {
  const starts = [
    '08:30',
    '09:20',
    '10:25',
    '11:15',
    '14:10',
    '15:00',
    '16:05',
    '16:55',
    '18:30',
    '19:20',
    '20:10',
    '21:00',
  ];
  const ends = [
    '09:15',
    '10:05',
    '11:10',
    '12:00',
    '14:55',
    '15:45',
    '16:50',
    '17:40',
    '19:15',
    '20:05',
    '20:55',
    '21:45',
  ];
  if (start < 1 || end > starts.length || start > end) return '';
  return '${starts[start - 1]}–${ends[end - 1]}';
}

int scheduleCourseSection(Map<String, dynamic> course, String key) {
  final value = course[key];
  return value is num ? value.toInt() : int.tryParse('$value') ?? 1;
}

/// 只合并同一天、课程属性一致且节次相邻的记录，换教室时不合并。
List<Map<String, dynamic>> mergeContinuousCourses(
  List<Map<String, dynamic>> courses,
) {
  final groups = <String, List<Map<String, dynamic>>>{};
  for (final course in courses) {
    final key = [
      'courseName',
      'teacher',
      'location',
      'weeks',
      'weekParity',
      'credits',
    ].map((field) => '${course[field] ?? ''}').join('|');
    groups.putIfAbsent(key, () => []).add(course);
  }
  final merged = <Map<String, dynamic>>[];
  for (final group in groups.values) {
    group.sort(
      (a, b) => scheduleCourseSection(
        a,
        'startSection',
      ).compareTo(scheduleCourseSection(b, 'startSection')),
    );
    Map<String, dynamic>? current;
    for (final course in group) {
      final start = scheduleCourseSection(course, 'startSection');
      final end = scheduleCourseSection(course, 'endSection');
      if (current == null) {
        current = {...course};
      } else if (start == scheduleCourseSection(current, 'endSection') + 1) {
        current['endSection'] = end;
      } else {
        merged.add(current);
        current = {...course};
      }
    }
    if (current != null) merged.add(current);
  }
  merged.sort(
    (a, b) => scheduleCourseSection(
      a,
      'startSection',
    ).compareTo(scheduleCourseSection(b, 'startSection')),
  );
  for (final course in merged) {
    course['timeRange'] = scheduleSectionTimeRange(
      scheduleCourseSection(course, 'startSection'),
      scheduleCourseSection(course, 'endSection'),
    );
  }
  return merged;
}
