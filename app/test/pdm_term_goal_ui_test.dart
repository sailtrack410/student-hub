import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/pdm_api.dart';
import 'package:smartcampus_app/models/pdm_models.dart';
import 'package:smartcampus_app/pages/pdm/pdm_growth_page.dart';

void main() {
  late ApiClient previous;
  dynamic goal;

  setUp(() {
    previous = PdmApi.client;
    goal = null;
    PdmApi.client = ApiClient(
      client: MockClient((request) async {
        final content = request.url.path.endsWith('/term-options')
            ? [
                {
                  'label': '2026-2027学年第一学期',
                  'academicYear': '2026',
                  'termType': '1',
                  'current': '1',
                },
              ]
            : goal;
        return http.Response(
          jsonEncode({
            'success': true,
            'code': 'OK',
            'data': {'source': 'PDM', 'content': content},
          }),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );
  });
  tearDown(() => PdmApi.client = previous);

  Future<void> showGoal(WidgetTester tester, {double width = 390}) async {
    tester.view.physicalSize = Size(width, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      const MaterialApp(
        home: PdmGrowthPage(
          initialTabIndex: 1,
          initialOverview: PdmOverview(
            connection: PdmConnection(status: 'CONNECTED'),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets(
    'term goal renders required and optional course targets without raw fields',
    (tester) async {
      goal = {
        'content': '<p>一步步往前走就好。</p>',
        'grade': 3,
        'rate': 70,
        'editable': false,
        'courses': {
          'required': [
            {
              'id': 'internal-course-id',
              'courseNo': 'internal-course-number',
              'courseName': 'Python高级程序设计',
              'credit': 3,
              'goalCourseScore': 80,
              'goalCoursePoint': 3,
              'teacherNo': 'internal-teacher-id',
              'teacherName': '测试教师',
              'requiredName': '必修',
            },
          ],
          'optional': [
            {
              'courseName': '摄影基础',
              'credit': '0.5',
              'goalCourseScore': '90',
              'goalCoursePoint': '4.0',
            },
          ],
        },
      };
      await showGoal(tester);
      expect(find.text('一步步往前走就好。'), findsOneWidget);
      expect(find.text('必修课程'), findsOneWidget);
      expect(find.text('选修课程'), findsOneWidget);
      expect(find.text('Python高级程序设计'), findsOneWidget);
      expect(find.text('摄影基础'), findsOneWidget);
      expect(find.text('学分：3'), findsOneWidget);
      expect(find.text('目标成绩：80'), findsOneWidget);
      expect(find.text('目标绩点：3'), findsOneWidget);
      expect(find.text('任课教师：测试教师'), findsOneWidget);
      expect(find.text('学分：0.5'), findsOneWidget);
      expect(find.text('目标成绩：90'), findsOneWidget);
      for (final raw in [
        'grade',
        'rate',
        'editable',
        'false',
        'courses',
        'internal-course-id',
        'internal-course-number',
        'internal-teacher-id',
      ]) {
        expect(find.textContaining(raw), findsNothing, reason: raw);
      }
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('term goal keeps plain content and hides empty course groups', (
    tester,
  ) async {
    goal = {
      'goalContent': '<p>保持阅读</p><p>认真完成课程</p>',
      'courses': {'required': [], 'optional': []},
      'editable': true,
    };
    await showGoal(tester);
    expect(find.textContaining('保持阅读'), findsOneWidget);
    expect(find.textContaining('<p>'), findsNothing);
    expect(find.text('必修课程'), findsNothing);
    expect(find.text('选修课程'), findsNothing);
    expect(find.text('true'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('term goal ignores malformed values without dumping objects', (
    tester,
  ) async {
    goal = {
      'goal': {'privateKey': 'hidden-target'},
      'content': '保留有效的目标正文',
      'unknownField': {'privateKey': 'hidden-extra'},
      'courses': {
        'required': [
          null,
          12,
          {},
          {
            'courseName': {'privateKey': 'hidden-name'},
          },
        ],
        'optional': 'not-a-list',
      },
    };
    await showGoal(tester);
    expect(find.text('保留有效的目标正文'), findsOneWidget);
    expect(find.textContaining('privateKey'), findsNothing);
    expect(find.textContaining('unknownField'), findsNothing);
    expect(find.text('必修课程'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('long course details wrap on a narrow screen', (tester) async {
    goal = {
      'content': '认真学习',
      'courses': {
        'required': [
          {
            'courseName': '面向复杂业务场景的数据库设计与应用实践课程',
            'credit': 0,
            'goalCourseScore': 0,
            'goalCoursePoint': 0,
            'teacherName': '课程联合教学团队与实践指导教师',
          },
        ],
        'optional': [],
      },
    };
    await showGoal(tester, width: 320);
    expect(find.text('面向复杂业务场景的数据库设计与应用实践课程'), findsOneWidget);
    expect(find.text('目标成绩：0'), findsOneWidget);
    expect(find.text('目标绩点：0'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('missing term goal preserves the existing empty state', (
    tester,
  ) async {
    await showGoal(tester);
    expect(find.text('本学期暂无目标'), findsOneWidget);
    expect(find.text('刷新目标状态'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
