import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/models/service_catalog_models.dart';
import 'package:smartcampus_app/pages/homework/homework_detail_page.dart';
import 'package:smartcampus_app/pages/homework/homework_page.dart';
import 'package:smartcampus_app/pages/service/service_hall_page.dart';

/// 作业中心页面骨架：入口跳转、加载失败态与重试（测试环境网络请求被拦截，不注入任何 mock 数据）。
void main() {
  testWidgets('ServiceHallPage opens homework center entry', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(
      const MaterialApp(
        home: ServiceHallPage(
          showBack: true,
          initialCategories: [
            ServiceCategoryData(
              categoryId: '100',
              categoryCode: 'REPAIR',
              name: '报修服务',
              description: '宿舍、教室与公共区域设施报修',
              items: [
                ServiceItemData(
                  itemId: '1001',
                  itemCode: 'REPAIR-DORM',
                  name: '宿舍报修',
                  summary: '水电、家具、门窗等宿舍设施故障报修',
                  slaHours: 24,
                ),
              ],
            ),
          ],
          initialRequestCount: 0,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('作业中心'), findsOneWidget);
    expect(find.text('查看课程作业与截止时间，在线提交作业'), findsOneWidget);

    await tester.tap(find.text('作业中心'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));

    // 已进入作业中心列表页；无网络时展示失败态与重试，不出现 mock 作业
    expect(find.text('作业中心'), findsOneWidget);
    expect(find.text('重新加载'), findsOneWidget);
    expect(find.text('暂无作业'), findsNothing);
  });

  testWidgets('HomeworkPage shows retryable load-failure state', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(const MaterialApp(home: HomeworkPage()));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));

    expect(find.text('作业中心'), findsOneWidget);
    expect(find.text('重新加载'), findsOneWidget);
    expect(find.text('暂无作业'), findsNothing);

    // 点击重试仍失败时保持失败态，不崩溃
    await tester.tap(find.text('重新加载'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('重新加载'), findsOneWidget);
  });

  testWidgets('HomeworkDetailPage shows retryable load-failure state', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(
      const MaterialApp(home: HomeworkDetailPage(homeworkId: '123')),
    );
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));

    expect(find.text('作业详情'), findsOneWidget);
    expect(find.text('重新加载'), findsOneWidget);
  });
}
