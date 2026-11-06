import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/p1_api.dart';
import 'package:smartcampus_app/pages/message/announcement_detail_page.dart';
import 'package:smartcampus_app/pages/message/announcement_list_page.dart';
import 'package:smartcampus_app/pages/message/ai_todo_page.dart';

void main() {
  late ApiClient original;
  late List<http.Request> requests;
  late dynamic Function(http.Request) respond;
  setUp(() {
    original = P1Api.client;
    requests = [];
    P1Api.client = ApiClient(
      client: MockClient((r) async {
        requests.add(r);
        final result = respond(r);
        return http.Response(
          jsonEncode({'success': true, 'data': result}),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );
  });
  tearDown(() => P1Api.client = original);

  testWidgets('announcement renders server body and acknowledges exact id', (
    tester,
  ) async {
    respond = (r) => r.method == 'GET'
        ? {'title': '教务通知', 'content': '真实正文\n办理材料', 'summary': '摘要'}
        : null;
    await tester.pumpWidget(
      const MaterialApp(home: AnnouncementDetailPage(id: '42')),
    );
    await tester.pumpAndSettle();
    expect(find.text('真实正文\n办理材料'), findsOneWidget);
    expect(requests.map((r) => '${r.method} ${r.url.path}'), [
      'GET /api/app/announcements/42',
      'POST /api/app/announcements/42/read',
    ]);
  });
  testWidgets('failed announcement stays retryable and does not mark read', (
    tester,
  ) async {
    respond = (_) => throw Exception('offline');
    await tester.pumpWidget(
      const MaterialApp(home: AnnouncementDetailPage(id: '42')),
    );
    await tester.pumpAndSettle();
    expect(find.text('重试'), findsOneWidget);
    expect(requests.every((r) => r.method == 'GET'), isTrue);
  });
  testWidgets('list distinguishes empty result from network error', (
    tester,
  ) async {
    respond = (_) => {'records': [], 'total': '0'};
    await tester.pumpWidget(const MaterialApp(home: AnnouncementListPage()));
    await tester.pumpAndSettle();
    expect(find.text('暂无公告'), findsOneWidget);
    respond = (_) => throw Exception('offline');
    await tester.tap(find.text('重新加载'));
    await tester.pumpAndSettle();
    expect(find.text('公告加载失败'), findsOneWidget);
  });
  testWidgets('announcement list accepts string totals and loads next page', (
    tester,
  ) async {
    respond = (r) {
      final page = r.url.queryParameters['pageNum'];
      return {
        'total': '2',
        'records': [
          {
            'id': page,
            'title': page == '1' ? '第一条公告' : '第二条公告',
            'typeText': '通知',
            'read': false,
          },
        ],
      };
    };
    await tester.pumpWidget(const MaterialApp(home: AnnouncementListPage()));
    await tester.pumpAndSettle();
    expect(find.text('第一条公告'), findsOneWidget);
    await tester.tap(find.text('加载更多'));
    await tester.pumpAndSettle();
    expect(find.text('第二条公告'), findsOneWidget);
    expect(requests.last.url.queryParameters['pageNum'], '2');
  });

  testWidgets('todo completion and reopen use explicit server state', (
    tester,
  ) async {
    var state = 'OPEN';
    respond = (r) {
      if (r.method == 'PUT') {
        state = jsonDecode(r.body)['state'];
        return null;
      }
      return [
        {
          'id': '9',
          'title': '查看成绩',
          'state': state,
          'action': {'type': 'GRADES', 'label': '查看成绩'},
        },
      ];
    };
    await tester.pumpWidget(const MaterialApp(home: AiTodoPage()));
    await tester.pumpAndSettle();
    expect(find.text('待完成'), findsOneWidget);
    await tester.tap(find.text('完成'));
    await tester.pumpAndSettle();
    expect(find.text('已完成'), findsOneWidget);
    await tester.tap(find.text('重新打开'));
    await tester.pumpAndSettle();
    expect(find.text('待完成'), findsOneWidget);
    final writes = requests.where((r) => r.method == 'PUT').toList();
    expect(writes.map((r) => jsonDecode(r.body)['state']), ['DONE', 'OPEN']);
    expect(writes.every((r) => r.url.path == '/api/app/ai/todos/9'), isTrue);
  });
}
