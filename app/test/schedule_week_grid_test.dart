import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/pages/schedule/schedule_course.dart';
import 'package:smartcampus_app/pages/schedule/schedule_page.dart';
import 'package:smartcampus_app/pages/schedule/week_picker_sheet.dart';
import 'package:smartcampus_app/pages/schedule/schedule_week_grid.dart';

Map<String, dynamic> _course(
  int section, {
  String location = '信息楼 402',
  String name = '移动应用交互设计',
}) => {
  'courseName': name,
  'teacher': '陈老师',
  'location': location,
  'startSection': section,
  'endSection': section,
  'timeRange': scheduleSectionTimeRange(section, section),
};

Map<String, dynamic> _week({int week = 9, int currentWeek = 13}) => {
  'week': week,
  'currentWeek': currentWeek,
  'totalWeeks': 20,
  'weekStart': week == 13 ? '2026-10-26' : '2026-09-28',
  'days': [
    {
      'dayOfWeek': 1,
      'courses': [_course(1), _course(2), _course(3), _course(4)],
    },
    {
      'dayOfWeek': 2,
      'courses': [_course(1, location: '信息楼 403', name: '高等数学')],
    },
    {
      'dayOfWeek': 6,
      'courses': [_course(1, name: '周末实验课'), _course(1, name: '并行辅导课')],
    },
  ],
};

void main() {
  test('continuous course merge preserves room change and section times', () {
    final merged = mergeContinuousCourses([
      _course(3),
      _course(1),
      _course(2),
      _course(4, location: '另一间教室'),
    ]);
    expect(merged, hasLength(2));
    expect(merged.first['startSection'], 1);
    expect(merged.first['endSection'], 3);
    expect(merged.first['timeRange'], '08:30–11:10');
    expect(merged.last['location'], '另一间教室');
  });

  testWidgets(
    'history week and full-screen grid stay readable without overflow',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        MaterialApp(
          home: SchedulePage(
            loadWeek: (week) async => _week(week: week ?? 13, currentWeek: 13),
            loadAdjustments: () async => [],
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      expect(find.byType(ScheduleWeekGrid), findsOneWidget);
      expect(find.text('第 13 周 · 本周 ▾'), findsOneWidget);
      await tester.tap(find.text('第 13 周 · 本周 ▾'));
      await tester.pumpAndSettle();
      await tester.tap(
        find.descendant(
          of: find.byType(WeekPickerSheet),
          matching: find.text('9'),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('正在查看历史课表'), findsOneWidget);
      expect(find.text('9月28日–10月4日'), findsOneWidget);
      expect(find.text('移动应用交互设计'), findsOneWidget);
      expect(find.text('第 9 周 ▾'), findsOneWidget);
      await tester.tap(find.text('回到本周'));
      await tester.pumpAndSettle();
      expect(find.text('第 13 周 · 本周 ▾'), findsOneWidget);
      expect(find.textContaining('正在查看历史课表'), findsNothing);
      await tester.tap(find.text('第 13 周 · 本周 ▾'));
      await tester.pumpAndSettle();
      await tester.tap(
        find.descendant(
          of: find.byType(WeekPickerSheet),
          matching: find.text('9'),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('第 9 周 ▾'), findsOneWidget);
      await tester.tap(find.text('今天'));
      await tester.pumpAndSettle();
      expect(find.text('第 13 周 · 本周 ▾'), findsOneWidget);
      expect(find.textContaining('正在查看历史课表'), findsNothing);
      final today = DateTime.now();
      const weekdayNames = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
      expect(
        find.textContaining(
          '${weekdayNames[today.weekday - 1]} ${DateTime(2026, 10, 26).add(Duration(days: today.weekday - 1)).month}/${DateTime(2026, 10, 26).add(Duration(days: today.weekday - 1)).day}',
        ),
        findsOneWidget,
      );
      await tester.tap(find.text('整周网格'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('第 13 周 · 本周 ▾'));
      await tester.pumpAndSettle();
      await tester.tap(
        find.descendant(
          of: find.byType(WeekPickerSheet),
          matching: find.text('9'),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('第 9 周 ▾'), findsOneWidget);
      await tester.tap(find.byTooltip('下一周'));
      await tester.pumpAndSettle();
      expect(find.text('第 10 周 ▾'), findsOneWidget);
      await tester.tap(find.text('高等数学'));
      await tester.pumpAndSettle();
      expect(find.text('上课日期'), findsOneWidget);
      expect(find.text('周二 9/29'), findsOneWidget);
      await tester.tap(find.text('关闭详情'));
      await tester.pumpAndSettle();
      expect(find.text('周末实验课'), findsOneWidget);
      expect(find.text('并行辅导课'), findsOneWidget);
      await tester.tap(find.text('并行辅导课'));
      await tester.pumpAndSettle();
      expect(find.text('周六 10/3'), findsOneWidget);
      await tester.tap(find.text('关闭详情'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
  testWidgets('sync JW failure retains schedule and offers reauthentication', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    await ApiClient().clearToken();
    await ApiClient().setToken('retained-platform-token');
    ApiClient.onUnauthorized = () => fail('教务失效不得退出平台');
    final transport = MockClient((request) async {
      if (request.url.path.endsWith('/schedule/import')) {
        return http.Response(
          jsonEncode({
            'success': false,
            'code': 'JW_401_02',
            'message': '教务认证已失效，请重新认证',
          }),
          401,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }
      return http.Response(
        jsonEncode({
          'success': true,
          'data': request.url.path.endsWith('/jw/captcha')
              ? {'uid': 'test-captcha', 'imageBase64': ''}
              : request.url.path.endsWith('/jw/status')
              ? {'status': 'AUTHENTICATED'}
              : <String, dynamic>{},
        }),
        200,
        headers: {'content-type': 'application/json; charset=utf-8'},
      );
    });
    ApiClient(baseUrl: 'https://campus.test', client: transport);
    await http.runWithClient(() async {
      await tester.pumpWidget(
        MaterialApp(
          home: SchedulePage(
            loadWeek: (_) async => _week(week: 13, currentWeek: 13),
            loadAdjustments: () async => [],
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('同步教务课表'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      expect(find.textContaining('课表已保留'), findsOneWidget);
      expect(find.text('重新认证'), findsOneWidget);
      expect(ApiClient().token, 'retained-platform-token');
      expect(find.byType(ScheduleWeekGrid), findsOneWidget);
      await tester.tap(find.text('重新认证'));
      await tester.pump();
      await tester.pump(const Duration(seconds: 4));
      expect(find.text('教务系统实名认证'), findsOneWidget);
      expect(tester.takeException(), isNull);
    }, () => transport);
    ApiClient.onUnauthorized = null;
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('adjustments are grouped in one collapsible weekly section', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: SchedulePage(
          loadWeek: (_) async => _week(),
          loadAdjustments: () async => [
            {
              'courseName': '移动应用交互设计',
              'semesterCode': '2026-2027-1',
              'noticeTime': '2026-09-29T10:00:00',
              'summary': '调整至周五第5–6节，地点不变',
            },
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('本周调课通知 · 1 条'), findsOneWidget);
    expect(find.text('移动应用交互设计调课通知'), findsNothing);
    await tester.tap(find.text('本周调课通知 · 1 条'));
    await tester.pumpAndSettle();
    expect(find.text('移动应用交互设计调课通知'), findsOneWidget);
    expect(find.byType(Card), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
