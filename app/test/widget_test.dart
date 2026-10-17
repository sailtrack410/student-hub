import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/auth_service.dart';
import 'package:smartcampus_app/models/user_session.dart';
import 'package:smartcampus_app/pages/service/evaluation_sheet.dart';
import 'package:smartcampus_app/pages/service/work_order_detail_page.dart';
import 'package:smartcampus_app/main.dart';

void main() {
  testWidgets('LoginPage renders correctly and validates agreement', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    final client = ApiClient();
    final authService = AuthService(client);
    await tester.pumpWidget(SmartCampusApp(
      authService: authService,
      forceLogin: true,
    ));
    await tester.pumpAndSettle();

    // 验证核心标题与文本存在
    expect(find.text('绵城校园'), findsOneWidget);
    expect(find.text('绵阳城市学院 · 智慧校园'), findsOneWidget);
    expect(find.text('MIANYANG CITY COLLEGE'), findsOneWidget);
    expect(find.text('连接师生生活 · 共享多元校园空间'), findsOneWidget);
    expect(find.text('QQ 一键登录'), findsOneWidget);
    expect(find.text('面向全校师生开放 · 授权即自动建号，免注册'), findsOneWidget);
    expect(find.text('登录即代表已阅读并同意'), findsOneWidget);
    expect(find.text('《用户协议》与《隐私政策》'), findsOneWidget);
    expect(find.text('绵阳城市学院校园服务平台 · 安全技术保障'), findsOneWidget);

    // 取消勾选协议
    await tester.tap(find.text('登录即代表已阅读并同意'));
    await tester.pumpAndSettle();

    // 未勾选协议时点击 QQ 一键登录
    await tester.tap(find.text('QQ 一键登录'));
    await tester.pumpAndSettle();

    // 应当滑出异常状态 Toast
    expect(find.text('请先阅读并同意《用户协议》与《隐私政策》'), findsOneWidget);
  });

  testWidgets('HomePage renders accurately with all canvas 5:1 sections and micro-interactions', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    final client = ApiClient();
    final authService = AuthService(client);
    const session = UserSession(
      token: 'test_token',
      userId: '1001',
      username: 'student01',
      displayName: '李同学',
      identityType: 'STUDENT',
      authorizationStatus: 'BASIC',
      isNewUser: false,
    );

    await tester.pumpWidget(SmartCampusApp(
      authService: authService,
      initialSession: session,
    ));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));

    // 1. 验证头部问候与信息
    expect(find.text('早安，李同学'), findsOneWidget);
    expect(find.text('绵阳城市学院 · 周四 第8周'), findsOneWidget);

    // 2. 验证校园广播条
    expect(find.text('【通知】本周五图书馆闭馆检修，请提前归还图书'), findsOneWidget);

    // 3. 验证课表日程胶囊
    expect(find.text('下一节 · 大学英语 (四)'), findsOneWidget);
    expect(find.text('08:00-09:35 · B202 阶梯教室'), findsOneWidget);
    expect(find.text('全部课表 ›'), findsOneWidget);

    // 4. 验证常用办事服务与 8 宫格
    expect(find.text('常用办事服务'), findsOneWidget);
    expect(find.text('服务大厅 (7项) ›'), findsOneWidget);
    expect(find.text('我的课表'), findsOneWidget);
    expect(find.text('宿舍报修'), findsOneWidget);
    expect(find.text('请假申请'), findsOneWidget);
    expect(find.text('在读证明'), findsOneWidget);
    expect(find.text('场地申请'), findsOneWidget);
    expect(find.text('校园咨询'), findsOneWidget);
    expect(find.text('教室报修'), findsOneWidget);
    expect(find.text('更多服务'), findsOneWidget);

    // 5. 验证校园新鲜事与讨论卡片
    expect(find.text('校园新鲜事 · 热门动态'), findsOneWidget);
    expect(find.text('进入社区广场 ›'), findsOneWidget);
    expect(find.text('李同学 · 计算机学院'), findsOneWidget);
    expect(find.text('Lv.4'), findsOneWidget);
    expect(find.text('热门讨论'), findsOneWidget);
    expect(find.text('图书馆占座现象，大家怎么看？'), findsOneWidget);
    expect(find.text('# 校园生活'), findsOneWidget);

    // 6. 验证待办引导卡
    expect(find.text('教务待办 · 待认证'), findsOneWidget);
    expect(find.text('完成认证，解锁成绩与全学期课表'), findsOneWidget);
    expect(find.text('去完成'), findsOneWidget);

    // 7. 验证底栏 5 Tab
    expect(find.text('首页'), findsOneWidget);
    expect(find.text('服务'), findsOneWidget);
    expect(find.text('AI助手'), findsOneWidget);
    expect(find.text('社区'), findsOneWidget);
    expect(find.text('我的'), findsOneWidget);

    // 8. 验证互动微动效：点击点赞
    expect(find.text('赞 234'), findsOneWidget);
    await tester.tap(find.text('赞 234'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    expect(find.text('赞 235'), findsOneWidget);
  });

  testWidgets('ServiceHallPage renders categories, search, and routes to repair apply', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    final client = ApiClient();
    final authService = AuthService(client);
    const session = UserSession(
      token: 'test_token',
      userId: '1001',
      username: 'student01',
      displayName: '李同学',
      identityType: 'STUDENT',
      authorizationStatus: 'BASIC',
      isNewUser: false,
    );

    await tester.pumpWidget(SmartCampusApp(
      authService: authService,
      initialSession: session,
    ));
    await tester.pump(const Duration(milliseconds: 300));

    // 点击底栏「服务」Tab 切换至服务大厅
    await tester.tap(find.text('服务'));
    await tester.pump(const Duration(milliseconds: 300));

    // 验证服务大厅头部与各分类栏目
    expect(find.text('服务大厅'), findsOneWidget);
    expect(find.text('我的申请 (2)'), findsOneWidget);
    expect(find.text('报修服务'), findsOneWidget);
    expect(find.text('日常事务'), findsOneWidget);
    expect(find.text('证明开具'), findsOneWidget);
    expect(find.text('咨询指引'), findsOneWidget);

    // 验证报修卡片
    expect(find.text('教室与公共区域报修'), findsOneWidget);
    expect(find.text('宿舍设施故障报修'), findsOneWidget);

    // 点击「教室与公共区域报修」进入申请页
    await tester.tap(find.text('教室与公共区域报修'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));

    // 验证申请页内容
    expect(find.text('办事指南'), findsOneWidget);
    expect(find.text('报修位置 *'), findsOneWidget);
    expect(find.text('故障分类 *'), findsOneWidget);
    expect(find.text('问题描述 *'), findsOneWidget);
    expect(find.text('报修联系人 *'), findsOneWidget);
    expect(find.text('联系电话 *'), findsOneWidget);
    await tester.drag(find.byType(ListView).last, const Offset(0, -350));
    await tester.pump();
    expect(find.text('立即提交教室报修申请'), findsOneWidget);
  });

  testWidgets('MyRequestsPage renders status filter tabs and triggers actions', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    final client = ApiClient();
    final authService = AuthService(client);
    const session = UserSession(
      token: 'test_token',
      userId: '1001',
      username: 'student01',
      displayName: '李同学',
      identityType: 'STUDENT',
      authorizationStatus: 'BASIC',
      isNewUser: false,
    );

    await tester.pumpWidget(SmartCampusApp(
      authService: authService,
      initialSession: session,
    ));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    // 切换至服务大厅并点击「我的申请 (2)」
    await tester.tap(find.text('服务'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tap(find.text('我的申请 (2)'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));

    // 验证我的申请页元素
    expect(find.text('我的申请'), findsOneWidget);
    expect(find.text('全部 (3)'), findsOneWidget);
    expect(find.text('处理中 (1)'), findsOneWidget);
    expect(find.text('待验收 (1)'), findsOneWidget);
    expect(find.text('已办结 (1)'), findsOneWidget);

    // 默认在“处理中”状态，验证卡片 1 存在
    expect(find.text('明德楼 (教学楼A栋) · 302 多媒体教室'), findsOneWidget);
    expect(find.text('催单提醒'), findsOneWidget);
    expect(find.text('查看进度 >'), findsOneWidget);

    // 切换至“待验收 (1)”
    await tester.ensureVisible(find.text('待验收 (1)'));
    await tester.tap(find.text('待验收 (1)'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(find.text('海棠苑 4栋 · 208 宿舍水龙头'), findsOneWidget);
    expect(find.text('去验收评价'), findsOneWidget);

    // 切换至“已办结 (1)”
    await tester.ensureVisible(find.text('已办结 (1)'));
    await tester.tap(find.text('已办结 (1)'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(find.text('图书馆 2楼 · 社科阅览室 08号自习桌'), findsOneWidget);
    expect(find.text('查看服务回执'), findsOneWidget);
  });

  testWidgets('WorkOrderDetailPage renders order details, technician, and 4-step timeline', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(const MaterialApp(
      home: WorkOrderDetailPage(
        orderNo: 'WO-20261017-0842',
        location: '明德楼 (教学楼A栋) · 302 多媒体教室',
        category: '多媒体投影专项',
        status: '维修处理中',
      ),
    ));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    // 验证标题与工单基本信息
    expect(find.text('工单流转详情'), findsOneWidget);
    expect(find.text('工单号：WO-20261017-0842 · 多媒体投影专项'), findsOneWidget);
    expect(find.text('维修处理中'), findsOneWidget);
    expect(find.text('明德楼 (教学楼A栋) · 302 多媒体教室'), findsOneWidget);

    // 验证师傅信息卡
    expect(find.text('王建国师傅 (高级电工组)'), findsOneWidget);
    expect(find.text('已服务 384 单 · 满意度 99.8%'), findsOneWidget);

    // 验证时间轴状态节点
    expect(find.text('工单流转节点 (实时跟踪)'), findsOneWidget);
    expect(find.text('14:15 · 现场检修中 (当前进度)'), findsOneWidget);
    expect(find.text('13:40 · 师傅已接单'), findsOneWidget);
    expect(find.text('13:10 · 调度中心派单'), findsOneWidget);
    expect(find.text('13:05 · 申请提交'), findsOneWidget);

    // 验证底部操作按钮
    expect(find.text('催单提醒'), findsOneWidget);
    expect(find.text('确认完工与评价'), findsOneWidget);
  });

  testWidgets('EvaluationBottomSheet renders rating stars, tag pills, and submits', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    bool submitted = false;

    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: Builder(
          builder: (context) => ElevatedButton(
            onPressed: () {
              EvaluationBottomSheet.show(
                context,
                orderNo: 'WO-20261017-0619',
                location: '海棠苑 4栋 · 208 宿舍水龙头',
              ).then((val) {
                if (val == true) submitted = true;
              });
            },
            child: const Text('打开评价'),
          ),
        ),
      ),
    ));
    await tester.pump();

    // 点击打开评价弹层
    await tester.tap(find.text('打开评价'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    // 验证评价弹层核心元素
    expect(find.text('服务完工确认与评价'), findsOneWidget);
    expect(find.text('师傅已拍照上传完工证明'), findsOneWidget);
    expect(find.text('整体满意度'), findsOneWidget);
    expect(find.text('5.0 非常满意'), findsOneWidget);
    expect(find.text('响应神速'), findsOneWidget);
    expect(find.text('技术精湛'), findsOneWidget);
    expect(find.text('态度亲切'), findsOneWidget);
    expect(find.text('清理现场'), findsOneWidget);
    expect(find.text('未修好，退回'), findsOneWidget);
    expect(find.text('确认完工并提交评价'), findsOneWidget);

    // 提交评价
    await tester.tap(find.text('确认完工并提交评价'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(submitted, isTrue);
  });
}

