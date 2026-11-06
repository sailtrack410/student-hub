import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../api/schedule_api.dart';
import '../../theme/app_theme.dart';
import '../../widgets/app_status_panel.dart';
import '../profile/jw_auth_page.dart';
import 'schedule_adjustment_sheet.dart';
import 'schedule_course.dart';
import 'schedule_week_grid.dart';
import 'week_picker_sheet.dart';

/// 只渲染服务端或当前账号缓存中的真实课程。
class SchedulePage extends StatefulWidget {
  const SchedulePage({super.key, this.loadWeek, this.loadAdjustments});

  final Future<Map<String, dynamic>?> Function(int? week)? loadWeek;
  final Future<List<Map<String, dynamic>>> Function()? loadAdjustments;

  @override
  State<SchedulePage> createState() => _SchedulePageState();
}

class _SchedulePageState extends State<SchedulePage> {
  int? _selectedWeek;
  int? _currentWeek;
  int _totalWeeks = 20;
  int _generation = 0;
  Map<String, dynamic>? _data;
  List<Map<String, dynamic>> _adjustments = [];
  bool _loading = true;
  bool _failed = false;
  bool _syncing = false;
  bool _adjustmentsExpanded = false;
  bool _listView = false;
  int _selectedWeekday = DateTime.now().weekday;
  static const _weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

  @override
  void initState() {
    super.initState();
    _load();
    _loadAdjustments();
  }

  Future<void> _loadAdjustments() async {
    final adjustments =
        await (widget.loadAdjustments?.call() ??
            ScheduleApi.fetchAdjustments());
    if (mounted) setState(() => _adjustments = adjustments);
  }

  Future<void> _load({int? week}) async {
    final generation = ++_generation;
    // 不传周次表示当前系统日期所在周，加载期间保留上一个有效页面。
    setState(() {
      if (week != null && week != _selectedWeek) _data = null;
      _selectedWeek = week;
      _loading = true;
      _failed = false;
    });
    Map<String, dynamic>? data;
    try {
      data =
          await (widget.loadWeek?.call(week) ??
              ScheduleApi.fetchWeekSchedule(week: week));
    } catch (_) {}
    if (!mounted || generation != _generation) return;
    setState(() {
      _loading = false;
      _failed = data == null;
      if (data != null) {
        _data = data;
        final dataWeek = (data['week'] as num?)?.toInt() ?? week;
        final dataCurrentWeek = (data['currentWeek'] as num?)?.toInt();
        _currentWeek = dataCurrentWeek ?? _currentWeek;
        _selectedWeek = dataWeek;
        if (_selectedWeek == _currentWeek) {
          _selectedWeekday = DateTime.now().weekday;
        }
        _totalWeeks = math.max(
          (data['totalWeeks'] as num?)?.toInt() ?? 20,
          math.max(_selectedWeek ?? 1, _currentWeek ?? 1),
        );
      }
    });
  }

  List<Map<String, dynamic>> get _days => ((_data?['days'] as List?) ?? [])
      .whereType<Map<String, dynamic>>()
      .toList();
  List<Map<String, dynamic>> _courses(Map<String, dynamic> day) =>
      mergeContinuousCourses(
        ((day['courses'] as List?) ?? [])
            .whereType<Map<String, dynamic>>()
            .map((course) => {...course, '_weekday': day['dayOfWeek']})
            .toList(),
      );
  DateTime? get _weekStart => DateTime.tryParse('${_data?['weekStart'] ?? ''}');
  bool get _offline => _data?['_offline'] == true;
  DateTime? get _syncedAt => DateTime.tryParse('${_data?['_syncedAt'] ?? ''}');
  int get _courseCount =>
      _days.fold(0, (count, day) => count + _courses(day).length);

  String _dayLabel(int weekday) {
    final date = _weekStart?.add(Duration(days: weekday - 1));
    final now = DateTime.now();
    final today =
        date != null &&
        date.year == now.year &&
        date.month == now.month &&
        date.day == now.day;
    return '${_weekdays[(weekday - 1).clamp(0, 6)]}${date == null ? '' : ' ${date.month}/${date.day}'}${today ? ' · 今天' : ''}';
  }

  Future<void> _pickWeek() async {
    final selected = await WeekPickerSheet.show(
      context,
      currentWeek: _selectedWeek ?? _currentWeek ?? 1,
      actualCurrentWeek: _currentWeek,
      totalWeeks: _totalWeeks,
    );
    if (selected != null && mounted) _load(week: selected);
  }

  Future<void> _openToday() async {
    if (_loading) return;
    await _load();
    if (!mounted) return;
    if (_data != null && _selectedWeek == _currentWeek) {
      setState(() {
        _selectedWeekday = DateTime.now().weekday;
        _listView = true;
      });
    } else {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('当前周课表加载失败，已保留原页面')));
    }
  }

  Future<void> _sync() async {
    if (_syncing) return;
    setState(() => _syncing = true);
    final result = await ScheduleApi.importSchedule();
    if (!mounted) return;
    setState(() => _syncing = false);
    if (result.succeeded) {
      await _load();
      await _loadAdjustments();
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            result.needsReauth
                ? '教务认证已失效，课表已保留。请重新认证后再同步'
                : result.message ?? '同步失败，请稍后重试',
          ),
          action: result.needsReauth
              ? SnackBarAction(
                  label: '重新认证',
                  onPressed: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => const JwAuthPage(isReauth: true),
                    ),
                  ),
                )
              : null,
        ),
      );
    }
  }

  void _showDetail(Map<String, dynamic> course, int weekday) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                '${course['courseName'] ?? '课程'}',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 20),
              _detail('上课日期', _dayLabel(weekday)),
              _detail('上课节次', _sections(course)),
              _detail('上课教室', '${course['location'] ?? '待公布'}'),
              _detail('授课教师', '${course['teacher'] ?? '待公布'}'),
              if (course['weeks'] != null)
                _detail('开课周次', '${course['weeks']}'),
              if (course['credits'] != null)
                _detail('课程学分', '${course['credits']}'),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('关闭详情'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _detail(String label, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: 4),
        SelectableText(value.isEmpty ? '待公布' : value),
      ],
    ),
  );

  String _sections(Map<String, dynamic> course) =>
      '第 ${course['startSection'] ?? '?'}–${course['endSection'] ?? course['startSection'] ?? '?'} 节';

  String _timeRange(Map<String, dynamic> course) {
    final serverRange = '${course['timeRange'] ?? ''}';
    if (serverRange.isNotEmpty) return serverRange.replaceFirst('-', '–');
    return scheduleSectionTimeRange(
      scheduleCourseSection(course, 'startSection'),
      scheduleCourseSection(course, 'endSection'),
    );
  }

  String get _weekRangeText {
    final start = _weekStart;
    if (start == null) return '';
    final end = start.add(const Duration(days: 6));
    return '${start.month}月${start.day}日–${end.month}月${end.day}日';
  }

  bool _isToday(int weekday) {
    final date = _weekStart?.add(Duration(days: weekday - 1));
    final now = DateTime.now();
    return date != null &&
        date.year == now.year &&
        date.month == now.month &&
        date.day == now.day;
  }

  int _courseWeekday(Map<String, dynamic> target) =>
      (target['_weekday'] as num?)?.toInt() ?? _selectedWeekday;

  Widget _todaySummary() {
    final isCurrentWeek =
        _selectedWeek == null || _selectedWeek == _currentWeek;
    final isTodaySelected = _isToday(_selectedWeekday);
    final weekday = isTodaySelected ? _selectedWeekday : DateTime.now().weekday;
    final courses = isCurrentWeek
        ? [
            for (final day in _days)
              if (day['dayOfWeek'] == weekday) ..._courses(day),
          ]
        : const <Map<String, dynamic>>[];
    if (isCurrentWeek) _selectedWeekday = weekday;
    final next = courses.where((course) {
      final time = _timeRange(course).split('–').last;
      final parts = time.split(':');
      if (parts.length != 2) return true;
      final minutes =
          (int.tryParse(parts[0]) ?? 23) * 60 + (int.tryParse(parts[1]) ?? 59);
      final now = DateTime.now();
      return minutes >= now.hour * 60 + now.minute;
    }).firstOrNull;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: isCurrentWeek ? AppColors.brandSoft : AppColors.warningSoft,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Icon(
            isCurrentWeek ? Icons.today_outlined : Icons.history,
            size: 16,
            color: isCurrentWeek ? AppColors.brand : AppColors.warning,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              !isCurrentWeek
                  ? '正在查看${_selectedWeek != null && _selectedWeek! < (_currentWeek ?? 1) ? '历史' : '未来'}课表，点击“回到本周”查看今天'
                  : courses.isEmpty
                  ? '今天没有课程安排'
                  : next == null
                  ? '今天 ${courses.length} 段课程，已全部结束'
                  : '今天 ${courses.length} 段课程 · 下一节 ${_timeRange(next)} · ${next['courseName'] ?? '课程'}（${next['location'] ?? '地点待公布'}）',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _toolbarPill({
    required String label,
    required VoidCallback onTap,
    IconData? icon,
    bool highlighted = false,
  }) {
    final fg = highlighted ? AppColors.brand : AppColors.textSecondary;
    final bg = highlighted ? AppColors.brandSoft : const Color(0xFFF1F5F9);
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        constraints: const BoxConstraints(minHeight: 32),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (icon != null) ...[
              Icon(icon, size: 14, color: fg),
              const SizedBox(width: 4),
            ],
            Text(
              label,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: fg,
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final grid = !_listView && MediaQuery.textScalerOf(context).scale(1) <= 1.2;
    final isCurrentWeek =
        _selectedWeek == null || _selectedWeek == _currentWeek;
    return Scaffold(
      appBar: AppBar(
        title: const Text('我的课表'),
        actions: [
          IconButton(
            tooltip: '教务绑定',
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const JwAuthPage()),
            ),
            icon: const Icon(Icons.verified_user_outlined),
          ),
          IconButton(
            tooltip: '同步教务课表',
            onPressed: _syncing ? null : _sync,
            icon: _syncing
                ? const SizedBox(
                    width: 22,
                    height: 22,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.sync),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => _load(),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
          children: [
            Row(
              children: [
                IconButton(
                  tooltip: '上一周',
                  visualDensity: VisualDensity.compact,
                  onPressed: (_selectedWeek ?? _currentWeek ?? 1) > 1
                      ? () => _load(week: (_selectedWeek ?? _currentWeek!) - 1)
                      : null,
                  icon: const Icon(Icons.chevron_left),
                ),
                Expanded(
                  child: InkWell(
                    onTap: _pickWeek,
                    borderRadius: BorderRadius.circular(10),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 4,
                      ),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            _selectedWeek == null
                                ? '选择周次'
                                : '第 $_selectedWeek 周'
                                      '${_selectedWeek == _currentWeek ? ' · 本周' : ''} ▾',
                            style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                              color: AppColors.textPrimary,
                            ),
                          ),
                          if (_weekRangeText.isNotEmpty)
                            Text(
                              _weekRangeText,
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                        ],
                      ),
                    ),
                  ),
                ),
                IconButton(
                  tooltip: '下一周',
                  visualDensity: VisualDensity.compact,
                  onPressed: (_selectedWeek ?? _currentWeek ?? 1) < _totalWeeks
                      ? () => _load(
                          week: (_selectedWeek ?? _currentWeek ?? 0) + 1,
                        )
                      : null,
                  icon: const Icon(Icons.chevron_right),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 8,
              runSpacing: 6,
              children: [
                _toolbarPill(
                  label: '回到本周',
                  onTap: () => _load(),
                  highlighted: !isCurrentWeek,
                ),
                _toolbarPill(
                  label: '今天',
                  icon: Icons.today_outlined,
                  onTap: _openToday,
                ),
                _toolbarPill(
                  label: grid ? '列表视图' : '整周网格',
                  icon: grid
                      ? Icons.view_agenda_outlined
                      : Icons.calendar_view_week,
                  onTap: () => setState(() => _listView = grid),
                ),
              ],
            ),
            const SizedBox(height: 6),
            if (_loading && _data == null)
              const AppStatusPanel(title: '正在加载课表', loading: true)
            else if (_failed && _data == null)
              AppStatusPanel(
                title: '课表加载失败',
                message: '尚无当前账号的本周缓存，请联网后重试或同步教务课表。',
                onRetry: () => _load(week: _selectedWeek),
              ),
            if (_data != null) ...[
              _todaySummary(),
              const SizedBox(height: 6),
              AppRefreshStatus(
                refreshing: _loading,
                stale: _offline || _failed,
                updatedAt: _syncedAt,
              ),
              if (_weekAdjustments.isNotEmpty) _adjustmentSection(),
              if (_failed)
                AppStatusPanel(
                  title: '刷新失败，保留上次课表',
                  compact: true,
                  onRetry: () => _load(week: _selectedWeek),
                ),
              if (_courseCount == 0)
                const AppStatusPanel(
                  title: '本周暂无课程',
                  message: '可切换周次，或通过右上角同步教务课表。',
                )
              else if (grid)
                LayoutBuilder(
                  builder: (context, constraints) => ScheduleWeekGrid(
                    days: _days,
                    weekStart: _weekStart,
                    maxHeight:
                        MediaQuery.sizeOf(context).height -
                        MediaQuery.paddingOf(context).top -
                        kToolbarHeight -
                        196,
                    selectedWeekday: _selectedWeekday,
                    onSelectWeekday: (weekday) => setState(() {
                      _selectedWeekday = weekday;
                      _listView = true;
                    }),
                    onCourseTap: (course) =>
                        _showDetail(course, _courseWeekday(course)),
                  ),
                )
              else
                _agenda(),
            ],
          ],
        ),
      ),
    );
  }

  List<Map<String, dynamic>> get _weekAdjustments {
    final start = _weekStart;
    final semester = '${_data?['semesterCode'] ?? ''}';
    return _adjustments.where((adjustment) {
      if (semester.isNotEmpty &&
          '${adjustment['semesterCode'] ?? ''}' != semester) {
        return false;
      }
      final time = DateTime.tryParse('${adjustment['noticeTime'] ?? ''}');
      return start == null ||
          time == null ||
          (!time.isBefore(start) &&
              time.isBefore(start.add(const Duration(days: 7))));
    }).toList();
  }

  Widget _adjustmentSection() {
    final adjustments = _weekAdjustments;
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: Column(
        children: [
          ListTile(
            leading: const Icon(
              Icons.event_repeat_outlined,
              color: AppColors.warning,
            ),
            title: Text('本周调课通知 · ${adjustments.length} 条'),
            subtitle: const Text('只显示当前学期和本周相关调整'),
            trailing: Icon(
              _adjustmentsExpanded
                  ? Icons.keyboard_arrow_up
                  : Icons.keyboard_arrow_down,
            ),
            onTap: () =>
                setState(() => _adjustmentsExpanded = !_adjustmentsExpanded),
          ),
          if (_adjustmentsExpanded)
            for (var index = 0; index < adjustments.length; index++) ...[
              if (index > 0)
                const Divider(height: 1, indent: 16, endIndent: 16),
              ListTile(
                dense: true,
                contentPadding: const EdgeInsets.symmetric(horizontal: 16),
                title: Text('${adjustments[index]['courseName'] ?? '课程'}调课通知'),
                subtitle: Text(
                  '${adjustments[index]['summary'] ?? '点击查看调整详情'}',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                trailing: const Icon(Icons.chevron_right, size: 18),
                onTap: () => ScheduleAdjustmentSheet.show(
                  context,
                  adjustment: adjustments[index],
                ),
              ),
            ],
        ],
      ),
    );
  }

  Widget _agenda() {
    final weekdays = [_selectedWeekday.clamp(1, 7)];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final weekday in weekdays) ...[
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Text(
              _dayLabel(weekday),
              style: Theme.of(context).textTheme.titleSmall,
            ),
          ),
          for (final day in _days.where((day) => day['dayOfWeek'] == weekday))
            for (final course in _courses(day)) _courseCard(course, weekday),
          if (!_days.any(
            (day) => day['dayOfWeek'] == weekday && _courses(day).isNotEmpty,
          ))
            const Padding(
              padding: EdgeInsets.only(bottom: 8),
              child: Text('暂无课程'),
            ),
        ],
      ],
    );
  }

  Widget _courseCard(Map<String, dynamic> course, int weekday) => Card(
    child: InkWell(
      onTap: () => _showDetail(course, weekday),
      borderRadius: BorderRadius.circular(16),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '${course['courseName'] ?? '课程'}',
              style: Theme.of(context).textTheme.titleSmall,
            ),
            const SizedBox(height: 8),
            Text(
              '${_timeRange(course)} · ${_sections(course)}',
              style: const TextStyle(
                color: AppColors.brand,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 4),
            Text('${course['location'] ?? '地点待公布'}'),
            if ('${course['teacher'] ?? ''}'.isNotEmpty)
              Text(
                '${course['teacher']}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
          ],
        ),
      ),
    ),
  );
}
