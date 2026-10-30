import 'package:flutter/material.dart';

import '../../theme/app_theme.dart';
import 'schedule_course.dart';

typedef ScheduleCourseTap = void Function(Map<String, dynamic> course);

class ScheduleWeekGrid extends StatelessWidget {
  const ScheduleWeekGrid({
    super.key,
    required this.days,
    required this.weekStart,
    required this.maxHeight,
    required this.onCourseTap,
    this.selectedWeekday = 1,
    this.onSelectWeekday,
  });

  final List<Map<String, dynamic>> days;
  final DateTime? weekStart;
  final double maxHeight;
  final ScheduleCourseTap onCourseTap;
  final int selectedWeekday;
  final ValueChanged<int>? onSelectWeekday;

  static const weekdays = ['一', '二', '三', '四', '五', '六', '日'];

  int get _sectionCount {
    var count = 12;
    for (final day in days) {
      for (final course
          in (day['courses'] as List? ?? const [])
              .whereType<Map<String, dynamic>>()) {
        final end = scheduleCourseSection(course, 'endSection');
        if (end > count) count = end;
      }
    }
    return count;
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final gridHeight = maxHeight.clamp(420.0, 900.0);
        const timeWidth = 26.0;
        const headerHeight = 52.0;
        const gap = 2.0;
        final dayWidth = (constraints.maxWidth - timeWidth - gap * 6) / 7;
        final now = DateTime.now();
        return SizedBox(
          height: headerHeight + gridHeight,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                width: timeWidth,
                child: Column(
                  children: [
                    const SizedBox(height: headerHeight),
                    SizedBox(
                      height: gridHeight,
                      child: Column(
                        children: [
                          for (
                            var section = 1;
                            section <= _sectionCount;
                            section++
                          )
                            Expanded(
                              child: Align(
                                alignment: Alignment.topCenter,
                                child: Text(
                                  '$section',
                                  style: const TextStyle(
                                    fontSize: 10,
                                    color: AppColors.textSecondary,
                                  ),
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              for (var weekday = 1; weekday <= 7; weekday++) ...[
                if (weekday > 1) const SizedBox(width: gap),
                SizedBox(
                  width: dayWidth,
                  child: _dayColumn(
                    context,
                    weekday,
                    dayWidth,
                    gridHeight,
                    headerHeight,
                    now,
                  ),
                ),
              ],
            ],
          ),
        );
      },
    );
  }

  Widget _dayColumn(
    BuildContext context,
    int weekday,
    double width,
    double height,
    double headerHeight,
    DateTime now,
  ) {
    final date = weekStart?.add(Duration(days: weekday - 1));
    final isToday =
        date != null &&
        date.year == now.year &&
        date.month == now.month &&
        date.day == now.day;
    final isSelected = selectedWeekday == weekday;
    final courses = mergeContinuousCourses([
      for (final day in days)
        if (day['dayOfWeek'] == weekday)
          for (final course
              in (day['courses'] as List? ?? const [])
                  .whereType<Map<String, dynamic>>())
            {...course, '_weekday': weekday},
    ]);
    return Column(
      children: [
        SizedBox(
          height: headerHeight,
          child: Material(
            color: isSelected ? AppColors.brandSoft : Colors.transparent,
            borderRadius: BorderRadius.circular(10),
            child: InkWell(
              onTap: onSelectWeekday == null
                  ? null
                  : () => onSelectWeekday!(weekday),
              borderRadius: BorderRadius.circular(10),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    weekdays[weekday - 1],
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: isSelected
                          ? AppColors.brand
                          : AppColors.textPrimary,
                    ),
                  ),
                  Text(
                    date == null ? '' : '${date.month}/${date.day}',
                    style: TextStyle(
                      fontSize: 10,
                      color: isSelected
                          ? AppColors.brand
                          : AppColors.textSecondary,
                    ),
                  ),
                  if (isToday)
                    Container(
                      width: 4,
                      height: 4,
                      margin: const EdgeInsets.only(top: 2),
                      decoration: const BoxDecoration(
                        color: AppColors.brand,
                        shape: BoxShape.circle,
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
        SizedBox(
          height: height,
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: isSelected
                  ? AppColors.brandSoft.withValues(alpha: 0.32)
                  : Colors.white,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(
                color: isToday ? AppColors.brand : AppColors.divider,
              ),
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Stack(
                children: [
                  for (var section = 1; section <= 11; section++)
                    Positioned(
                      top: (section - 1) * height / _sectionCount,
                      left: 0,
                      right: 0,
                      height: 1,
                      child: const ColoredBox(color: AppColors.divider),
                    ),
                  for (final entry in _layoutCourses(courses).entries)
                    _courseBlock(
                      entry.value[entry.key],
                      entry.key,
                      entry.value.length,
                      width,
                      height,
                    ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  Map<int, List<Map<String, dynamic>>> _layoutCourses(
    List<Map<String, dynamic>> courses,
  ) {
    final result = <int, List<Map<String, dynamic>>>{};
    var group = <Map<String, dynamic>>[];
    var groupEnd = 0;

    for (final course in courses) {
      final start = scheduleCourseSection(course, 'startSection');
      final end = scheduleCourseSection(course, 'endSection');
      if (group.isNotEmpty && start > groupEnd) {
        _assignLanes(group, result);
        group = [];
        groupEnd = 0;
      }
      group.add(course);
      if (end > groupEnd) groupEnd = end;
    }
    _assignLanes(group, result);
    return result;
  }

  void _assignLanes(
    List<Map<String, dynamic>> group,
    Map<int, List<Map<String, dynamic>>> result,
  ) {
    final laneEnds = <int>[];
    for (final course in group) {
      final start = scheduleCourseSection(course, 'startSection');
      final end = scheduleCourseSection(course, 'endSection');
      var lane = laneEnds.indexWhere((laneEnd) => laneEnd < start);
      if (lane < 0) {
        lane = laneEnds.length;
        laneEnds.add(end);
      } else {
        laneEnds[lane] = end;
      }
      result[lane] = [...group];
    }
  }

  Widget _courseBlock(
    Map<String, dynamic> course,
    int lane,
    int lanes,
    double width,
    double height,
  ) {
    final start = scheduleCourseSection(course, 'startSection');
    final end = scheduleCourseSection(course, 'endSection');
    final laneWidth = (width - 4) / lanes;
    final blockHeight = (end - start + 1) * height / _sectionCount - 3;
    final name = '${course['courseName'] ?? '课程'}';
    final location = '${course['location'] ?? '待公布'}';
    return Positioned(
      top: (start - 1) * height / _sectionCount + 1.5,
      left: lane * laneWidth + 2,
      width: laneWidth - (lane == lanes - 1 ? 2 : 1),
      height: blockHeight,
      child: Material(
        color: lane.isEven ? AppColors.brandSoft : AppColors.accentSoft,
        borderRadius: BorderRadius.circular(6),
        child: InkWell(
          onTap: () => onCourseTap(course),
          borderRadius: BorderRadius.circular(6),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 3, vertical: 4),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  maxLines: (blockHeight / 15).floor().clamp(1, 5),
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 9,
                    height: 1.35,
                    fontWeight: FontWeight.w600,
                    color: AppColors.textPrimary,
                  ),
                ),
                if (blockHeight >= 54 && lanes == 1) ...[
                  const SizedBox(height: 2),
                  Text(
                    location,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 9,
                      height: 1.3,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
