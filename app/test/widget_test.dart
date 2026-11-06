import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/auth_service.dart';
import 'package:smartcampus_app/models/user_session.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/pages/service/evaluation_sheet.dart';
import 'package:smartcampus_app/pages/service/work_order_detail_page.dart';
import 'package:smartcampus_app/pages/community/community_home_page.dart';
import 'package:smartcampus_app/pages/community/post_detail_page.dart';
import 'package:smartcampus_app/pages/community/post_unavailable_page.dart';
import 'package:smartcampus_app/pages/community/post_appeal_page.dart';
import 'package:smartcampus_app/pages/community/post_publish_page.dart';
import 'package:smartcampus_app/pages/community/community_report_success_sheet.dart';
import 'package:smartcampus_app/pages/community/chat_detail_page.dart';
import 'package:smartcampus_app/pages/assistant/ai_assistant_page.dart';
import 'package:smartcampus_app/pages/profile/badge_wall_page.dart';
import 'package:smartcampus_app/pages/profile/jw_auth_page.dart';
import 'package:smartcampus_app/pages/profile/profile_page.dart';
import 'package:smartcampus_app/pages/profile/email_binding_page.dart';
import 'package:smartcampus_app/pages/login/register_page.dart';
import 'package:smartcampus_app/pages/login/forgot_password_page.dart';
import 'package:smartcampus_app/pages/legal/legal_document_page.dart';
import 'package:smartcampus_app/pages/profile/settings_security_page.dart';
import 'package:smartcampus_app/pages/profile/device_management_page.dart';
import 'package:smartcampus_app/pages/profile/account_center_page.dart';
import 'package:smartcampus_app/pages/schedule/grade_query_page.dart';
import 'package:smartcampus_app/pages/schedule/schedule_page.dart';
import 'package:smartcampus_app/pages/schedule/week_picker_sheet.dart';
import 'package:smartcampus_app/pages/service/service_detail_page.dart';
import 'package:smartcampus_app/pages/service/service_hall_page.dart';
import 'package:smartcampus_app/pages/service/my_requests_page.dart';
import 'package:smartcampus_app/pages/message/notification_center_page.dart';
import 'package:smartcampus_app/models/message_models.dart';
import 'package:smartcampus_app/models/service_catalog_models.dart';
import 'package:smartcampus_app/pages/community/content_status_pages.dart';
import 'package:smartcampus_app/main.dart';
import 'package:smartcampus_app/widgets/markdown_text.dart';

void main() {
  testWidgets('LoginPage renders correctly and validates agreement', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    final client = ApiClient();
    final authService = AuthService(client);
    await tester.pumpWidget(
      SmartCampusApp(authService: authService, forceLogin: true),
    );
    await tester.pumpAndSettle();

    // 验证核心标题与文本存在
    expect(find.text('绵城校园'), findsOneWidget);
    expect(find.text('绵阳城市学院 · 智慧校园'), findsOneWidget);
    expect(find.text('MIANYANG CITY COLLEGE'), findsOneWidget);
    expect(find.text('连接师生生活 · 共享多元校园空间'), findsOneWidget);
    expect(find.text('QQ 一键登录'), findsOneWidget);
    expect(find.text('面向全校师生开放 · 支持账号 ID / 邮箱注册'), findsOneWidget);
    expect(find.text('注册账号'), findsOneWidget);
    expect(find.text('找回密码'), findsOneWidget);
    expect(find.text('我已阅读并同意'), findsOneWidget);
    expect(find.text('《用户协议》'), findsOneWidget);
    expect(find.text('《隐私政策》'), findsOneWidget);
    expect(find.text('绵阳城市学院校园服务平台 · 安全技术保障'), findsOneWidget);

    // 默认未勾选，点击 QQ 登录应被协议校验拦下
    await tester.tap(find.text('QQ 一键登录'));
    await tester.pumpAndSettle();
    expect(find.text('请先阅读并同意《用户协议》与《隐私政策》'), findsOneWidget);

    // 点击《用户协议》只打开应用内文档页，不改变勾选状态
    await tester.tap(find.text('《用户协议》'));
    await tester.pumpAndSettle();
    expect(find.byType(LegalDocumentPage), findsOneWidget);
    await tester.tap(find.byIcon(Icons.arrow_back_ios_new_rounded));
    await tester.pumpAndSettle();
    expect(find.text('我已阅读并同意'), findsOneWidget);
  });

  testWidgets(
    'HomePage renders standard tab bar and load-failure retry state',
    (WidgetTester tester) async {
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

      await tester.pumpWidget(
        SmartCampusApp(authService: authService, initialSession: session),
      );
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));

      // 测试环境网络请求被拦截，首页展示可重试的加载失败态
      expect(find.text('课程与待办加载失败'), findsOneWidget);
      expect(find.text('网络开小差了，请检查网络后重试'), findsWidgets);
      expect(find.text('重试'), findsWidgets);

      // 标准底栏：首页 / 社区 / 绵宝AI / 消息 / 我的
      expect(find.text('首页'), findsOneWidget);
      expect(find.text('社区'), findsOneWidget);
      expect(find.text('绵宝AI'), findsOneWidget);
      expect(find.text('消息'), findsOneWidget);
      expect(find.text('我的'), findsOneWidget);
      // 服务、课表已降为二级入口，不再是一级 Tab
      expect(find.text('服务'), findsNothing);
      expect(find.text('AI助手'), findsNothing);

      // 点击重试不崩溃，仍无网络时继续展示失败态
      await tester.tap(find.text('重试').first);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      expect(find.text('课程与待办加载失败'), findsOneWidget);
    },
  );

  testWidgets(
    'ServiceHallPage renders catalog groups and routes to repair apply',
    (WidgetTester tester) async {
      tester.view.physicalSize = const Size(375, 812);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      const categories = [
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
            ServiceItemData(
              itemId: '1002',
              itemCode: 'REPAIR-PUBLIC',
              name: '教室与公共区域报修',
              summary: '教学楼教室、自习区、走廊等公共设施报修',
              slaHours: 24,
            ),
          ],
        ),
        ServiceCategoryData(
          categoryId: '200',
          categoryCode: 'AFFAIR',
          name: '事务办理',
          description: '请假、活动场地等日常事务申请',
          items: [
            ServiceItemData(
              itemId: '2001',
              itemCode: 'AFFAIR-LEAVE',
              name: '请假申请',
              summary: '病假、事假等学生请假申请',
              slaHours: 48,
            ),
          ],
        ),
        ServiceCategoryData(
          categoryId: '300',
          categoryCode: 'CERTIFICATE',
          name: '证明开具',
          description: '在读证明等常用证明申请',
          items: [
            ServiceItemData(
              itemId: '3001',
              itemCode: 'CERT-ENROLL',
              name: '在读证明申请',
              summary: '用于签证办理、贷款等场景的在读证明',
              slaHours: 48,
            ),
          ],
        ),
      ];

      await tester.pumpWidget(
        MaterialApp(
          home: ServiceHallPage(
            showBack: true,
            initialCategories: categories,
            initialRequestCount: 2,
          ),
        ),
      );
      await tester.pumpAndSettle();

      // 头部与分类服务（不再重复展示顶部高频快捷办事胶囊行）
      expect(find.text('服务大厅'), findsOneWidget);
      expect(find.text('我的申请 (2)'), findsOneWidget);
      expect(find.text('高频快捷办事'), findsNothing);
      expect(find.text('宿舍报修'), findsOneWidget);
      expect(find.text('教室与公共区域报修'), findsOneWidget);
      expect(find.text('请假申请'), findsOneWidget);
      expect(find.text('在读证明申请'), findsOneWidget);

      // 分类组
      expect(find.text('后勤报修服务'), findsOneWidget);
      expect(find.text('学生事务办理'), findsOneWidget);
      expect(find.text('证明开具与指引'), findsOneWidget);
      expect(find.text('24h响应'), findsWidgets);
      // 二级入口不应再带底栏
      expect(find.text('首页'), findsNothing);

      // 点击「教室与公共区域报修」进入申请页
      await tester.tap(find.text('教室与公共区域报修'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));

      expect(find.text('办事指南'), findsOneWidget);
      expect(find.text('报修位置 *'), findsOneWidget);
      expect(find.text('故障分类 *'), findsOneWidget);
      expect(find.text('问题描述 *'), findsOneWidget);
      expect(find.text('联系电话 *'), findsOneWidget);
      await tester.drag(find.byType(ListView).last, const Offset(0, -350));
      await tester.pump();
      expect(find.text('立即提交教室报修申请'), findsOneWidget);
    },
  );

  testWidgets('MyRequestsPage uses real API and shows honest failure state', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(
      const MaterialApp(home: MyRequestsPage(initialFilterIndex: 1)),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));

    // 页面骨架与真实筛选标签存在，不再渲染假申请卡片
    expect(find.text('我的申请'), findsOneWidget);
    expect(find.text('全部'), findsOneWidget);
    expect(find.text('处理中'), findsOneWidget);
    expect(find.text('待验收'), findsOneWidget);
    expect(find.text('已办结'), findsOneWidget);

    // 测试环境网络被拦截：必须给出可重试的错误态，而不是演示数据
    expect(find.text('申请列表加载失败'), findsOneWidget);
    expect(find.text('明德楼 (教学楼A栋) · 302 多媒体教室'), findsNothing);
    expect(find.text('催单提醒'), findsNothing);
  });

  testWidgets(
    'WorkOrderDetailPage requires real requestId and shows failure state',
    (WidgetTester tester) async {
      tester.view.physicalSize = const Size(375, 812);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(
        const MaterialApp(home: WorkOrderDetailPage(requestId: '9001')),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));

      expect(find.text('申请详情'), findsOneWidget);
      expect(find.text('申请详情加载失败'), findsOneWidget);

      // 不再出现任何演示工单号、师傅与假时间线
      expect(find.text('WO-20261017-0842'), findsNothing);
      expect(find.text('王建国师傅 (高级电工组)'), findsNothing);
      expect(find.text('确认完工与评价'), findsNothing);
    },
  );

  testWidgets(
    'EvaluationBottomSheet renders rating stars, tag pills, and submits',
    (WidgetTester tester) async {
      tester.view.physicalSize = const Size(375, 812);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      bool submitted = false;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => ElevatedButton(
                onPressed: () {
                  EvaluationBottomSheet.show(
                    context,
                    requestId: '9101',
                    location: '海棠苑 4栋 · 208 宿舍水龙头',
                  ).then((val) {
                    if (val == true) submitted = true;
                  });
                },
                child: const Text('打开评价'),
              ),
            ),
          ),
        ),
      );
      await tester.pump();

      // 点击打开评价弹层
      await tester.tap(find.text('打开评价'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));

      // 验证评价弹层核心元素
      expect(find.text('服务完工确认与评价'), findsOneWidget);
      expect(find.text('请核对工单照片与维修结果后确认'), findsOneWidget);
      expect(find.text('整体满意度'), findsOneWidget);
      expect(find.text('5.0 非常满意'), findsOneWidget);
      expect(find.text('响应神速'), findsOneWidget);
      expect(find.text('技术精湛'), findsOneWidget);
      expect(find.text('态度亲切'), findsOneWidget);
      expect(find.text('清理现场'), findsOneWidget);
      expect(find.text('未修好，退回'), findsOneWidget);
      expect(find.text('确认完工并提交评价'), findsOneWidget);

      // 点击后必须真实调用接口；测试环境失败时不能假装提交成功
      await tester.tap(find.text('确认完工并提交评价'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));

      expect(submitted, isFalse);
      expect(find.text('评价提交失败，请稍后重试'), findsOneWidget);
    },
  );

  testWidgets(
    'CommunityHomePage renders categories, restores failed like and opens profile',
    (WidgetTester tester) async {
      tester.view.physicalSize = const Size(375, 812);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(
        MaterialApp(
          home: CommunityHomePage(
            initialPosts: CommunityMockData.getInitialPosts(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // 1. 验证顶部元素（发帖入口已改为右下角 FAB）
      expect(
        find.byKey(const ValueKey('community-publish-fab')),
        findsOneWidget,
      );

      // 2. 验证排序标签；分区胶囊为 fetchSections 接口下发，离线测试环境不渲染
      expect(find.text('推荐'), findsOneWidget);
      expect(find.text('最新'), findsOneWidget);
      expect(find.text('二手闲置'), findsNothing);

      // 3. 验证帖子卡片与等级徽章（等级已去中文称号，仅纯 Lv.X）
      expect(find.text('追光的少年'), findsOneWidget);
      expect(find.text('Lv.4'), findsWidgets);
      expect(find.text('关于自习室占座现象的建议与思考，大家怎么看？'), findsOneWidget);
      expect(find.text('三食堂新开的轻食减脂餐亲测推荐！'), findsOneWidget);
      expect(find.text('自习室清晨'), findsOneWidget);
      expect(find.text('# 校园生活'), findsWidgets);

      // 4. 离线点赞恢复原状态，不能把乐观更新当成提交成功。
      expect(find.text('286'), findsOneWidget);
      await tester.tap(find.text('286'));
      await tester.pumpAndSettle();
      expect(find.text('286'), findsOneWidget);
      expect(find.text('287'), findsNothing);
      expect(find.text('点赞结果未确认，已恢复显示，请刷新后重试'), findsOneWidget);

      // 5. 点击发帖人头像进入对方个人主页 (APP-他人主页 5:1778)
      await tester.tap(find.text('追光的少年').first);
      await tester.pumpAndSettle();
      expect(find.text('个人主页'), findsOneWidget);
      expect(find.text('私信'), findsOneWidget);
      expect(find.text('帖子动态'), findsOneWidget);
      expect(find.text('获赞互动'), findsOneWidget);
      expect(find.text('二手闲置 (1)'), findsNothing);
    },
  );

  testWidgets('PostUnavailablePage renders hidden content prompt', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(
      const MaterialApp(
        home: PostUnavailablePage(
          postId: '2111782337503170561',
          reason: PostUnavailableReason.hidden,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('帖子已被隐藏'), findsOneWidget);
    expect(find.textContaining('违反社区规范'), findsOneWidget);
    expect(find.text('返回社区'), findsOneWidget);
    expect(find.textContaining('帖子编号'), findsOneWidget);
  });

  testWidgets('PostAppealPage renders appeal form and validates empty reason', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(
      const MaterialApp(
        home: PostAppealPage(postId: '2111813897510170625', postTitle: '测试帖子'),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('内容申诉'), findsOneWidget);
    expect(find.text('复核原因'), findsOneWidget);
    expect(find.text('内容被误判'), findsOneWidget);
    expect(find.text('补充说明'), findsOneWidget);
    expect(find.text('证明材料'), findsOneWidget);
    expect(find.text('提交申诉'), findsOneWidget);

    await tester.tap(find.text('提交申诉'));
    await tester.pumpAndSettle();
    expect(find.text('请选择复核原因或填写补充说明'), findsOneWidget);
  });

  testWidgets('PostDetailPage shows review state for reviewing post', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    final post = CommunityMockData.getInitialPosts().first.copyWith(
      status: 'REVIEWING',
    );
    await tester.pumpWidget(MaterialApp(home: PostDetailPage(post: post)));
    await tester.pumpAndSettle();

    expect(find.textContaining('人工安全审核中'), findsOneWidget);
  });

  testWidgets(
    'PostDetailPage renders full post content, comments, and natural keyboard-avoidance bottom bar',
    (WidgetTester tester) async {
      tester.view.physicalSize = const Size(375, 812);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final post = CommunityMockData.getInitialPosts().first;
      await tester.pumpWidget(MaterialApp(home: PostDetailPage(post: post)));
      await tester.pumpAndSettle();

      // 1. 验证标题与作者
      expect(find.text('帖子详情'), findsOneWidget);
      expect(find.text('追光的少年'), findsOneWidget);
      expect(find.textContaining('Lv.4'), findsWidgets);
      expect(find.text('+ 关注'), findsOneWidget);
      expect(find.text('关于自习室占座现象的建议与思考，大家怎么看？'), findsOneWidget);

      // 2. 验证评论区
      expect(find.textContaining('全部评论'), findsOneWidget);
      expect(find.text('绵城干饭王'), findsOneWidget);
      expect(find.text('热评'), findsOneWidget);
      expect(find.textContaining('强烈支持！'), findsOneWidget);
      expect(find.text('薄荷汽水'), findsOneWidget);
      expect(find.text('追光的少年'), findsWidgets);

      // 3. 验证自然吸底输入栏 (非弹窗模式，常驻可顶起输入框)
      expect(find.text('友善评论，说点什么吧...'), findsOneWidget);
      expect(find.text('64'), findsWidgets);
      expect(find.text('286'), findsOneWidget);

      // 4. 直接在常驻输入栏输入评论并发送
      await tester.enterText(find.byType(TextField), '好主意，支持尽快落地');
      await tester.pump();
      expect(find.text('发送'), findsOneWidget);
      await tester.tap(find.text('发送'));
      await tester.pumpAndSettle();

      expect(find.text('好主意，支持尽快落地'), findsOneWidget);
      expect(find.text('发送失败，请重试'), findsOneWidget);
      expect(find.text('评论发表成功'), findsNothing);
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller?.text,
        '好主意，支持尽快落地',
      );

      // 5. 点击右上角“更多”弹出操作菜单 (非直接弹举报)
      await tester.tap(find.byIcon(Icons.more_horiz_rounded));
      await tester.pumpAndSettle();
      expect(find.text('标题＋链接分享'), findsOneWidget);
      expect(find.text('复制链接'), findsOneWidget);
      expect(find.text('不感兴趣（在信息流隐藏此帖）'), findsOneWidget);
      expect(find.text('举报违规内容'), findsOneWidget);
      expect(find.text('取消'), findsOneWidget);
    },
  );

  testWidgets(
    'PostPublishPage validates required title, switches mode, and selects macro location',
    (WidgetTester tester) async {
      tester.view.physicalSize = const Size(375, 812);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(const MaterialApp(home: PostPublishPage()));
      await tester.pumpAndSettle();

      expect(find.text('取消'), findsOneWidget);
      expect(find.text('动态'), findsOneWidget);
      expect(find.text('文章'), findsOneWidget);
      expect(find.text('发布'), findsOneWidget);
      expect(find.text('填写标题 *必填'), findsOneWidget);
      expect(find.text('发布到'), findsOneWidget);
      expect(find.text('校园日常'), findsOneWidget);
      expect(find.text('话题'), findsOneWidget);
      expect(find.text('自建话题'), findsOneWidget);
      expect(find.byIcon(Icons.location_on_outlined), findsOneWidget);

      // 切到文章模式，验证段落式图文编辑区（图片从底栏插入）
      await tester.tap(find.text('文章'));
      await tester.pumpAndSettle();
      expect(find.text('添加段落'), findsOneWidget);
      expect(find.byIcon(Icons.location_on_outlined), findsOneWidget);

      // 未填标题直接点击发布
      await tester.tap(find.text('发布'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.text('请填写帖子标题'), findsOneWidget);
    },
  );

  testWidgets('ChatDetailPage renders message bubbles and sends text', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(const MaterialApp(home: ChatDetailPage()));
    await tester.pumpAndSettle();

    expect(find.text('苏晴晴'), findsOneWidget);
    expect(find.byIcon(Icons.verified_rounded), findsNothing);
    expect(find.textContaining('同学你好！我看到你发的帖子'), findsOneWidget);
    expect(find.textContaining('在的！卡背面的姓名是“苏*”'), findsOneWidget);

    final input = find.byType(TextField).last;
    await tester.enterText(input, '好的，我马上到');
    await tester.tap(find.text('发送'));
    await tester.pumpAndSettle();

    expect(find.text('好的，我马上到'), findsWidgets);
  });

  testWidgets('AIAssistantPage renders recommendations and truthful failure', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(const MaterialApp(home: AIAssistantPage()));
    await tester.pumpAndSettle();

    expect(find.text('绵宝AI'), findsOneWidget);
    expect(find.text('常见问答场景推荐'), findsOneWidget);
    expect(find.text('查明天下节课'), findsWidgets);
    expect(find.text('宿舍空调报修'), findsOneWidget);
    expect(find.text('补办校园一卡通'), findsOneWidget);
    expect(find.text('学期成绩与GPA'), findsOneWidget);

    // 点击推荐问答卡
    await tester.tap(find.text('查明天下节课').first);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 900));

    expect(find.textContaining('暂未取得 AI 回复'), findsOneWidget);
    expect(find.text('重试此问题'), findsOneWidget);
    expect(find.textContaining('高等数学 (下)'), findsNothing);
    expect(find.textContaining('软件工程导论'), findsNothing);
  });

  testWidgets('AIAssistantPage opens voice sheet and attachment panel', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(const MaterialApp(home: AIAssistantPage()));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.mic_none_rounded));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('松开发送'), findsOneWidget);
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.add_rounded));
    await tester.pumpAndSettle();
    expect(find.text('图片'), findsOneWidget);
    expect(find.text('文档'), findsOneWidget);
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
  });

  testWidgets('CommunityReportSuccessSheet renders receipt details', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: CommunityReportSuccessSheet(
            reportNo: 'RP-202610190045',
            reason: '涉假诈骗/商业广告',
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('举报已提交审核'), findsOneWidget);
    expect(find.text('RP-202610190045'), findsOneWidget);
    expect(find.text('人工管理员复审中'), findsOneWidget);
    expect(find.text('我知道了'), findsOneWidget);
  });

  testWidgets('BadgeWallPage renders 19 badges and handles tapping detail', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(const MaterialApp(home: BadgeWallPage()));
    await tester.pumpAndSettle();

    expect(find.text('荣誉勋章墙'), findsOneWidget);
    expect(find.byType(GridView), findsOneWidget);
    expect(find.text('破冰首帖'), findsWidgets);
  });

  testWidgets('SchedulePage shows honest load failure without demo courses', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: SchedulePage(
          loadWeek: (_) async => null,
          loadAdjustments: () async => [],
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('课表加载失败'), findsOneWidget);
    expect(find.text('高等数学 (下)'), findsNothing);
    expect(find.textContaining('计算机网络原理'), findsNothing);
    expect(find.text('重试'), findsOneWidget);
  });

  testWidgets(
    'SchedulePage switches real weeks and opens real course details',
    (tester) async {
      final weeks = <int?>[];
      await tester.pumpWidget(
        MaterialApp(
          home: SchedulePage(
            loadWeek: (week) async {
              weeks.add(week);
              return {
                'week': week ?? 8,
                'currentWeek': 8,
                'weekStart': '2026-10-19',
                'days': [
                  {
                    'dayOfWeek': 1,
                    'courses': [
                      {
                        'courseName': '测试课程',
                        'location': '测试楼101',
                        'teacher': '测试教师',
                        'startSection': 1,
                        'endSection': 2,
                      },
                    ],
                  },
                ],
              };
            },
            loadAdjustments: () async => [],
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('第 8 周 · 本周 ▾'), findsOneWidget);
      await tester.tap(find.text('测试课程'));
      await tester.pumpAndSettle();
      expect(find.text('上课教室'), findsOneWidget);
      expect(find.text('测试楼101'), findsWidgets);
      expect(find.text('路线导航'), findsNothing);
      await tester.tap(find.text('关闭详情'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('第 8 周 · 本周 ▾'));
      await tester.pumpAndSettle();
      expect(find.text('选择周次'), findsOneWidget);
      await tester.tap(
        find.descendant(
          of: find.byType(WeekPickerSheet),
          matching: find.text('10'),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('第 10 周 ▾'), findsOneWidget);
      expect(weeks, [null, 10]);
    },
  );

  testWidgets('JwAuthPage renders binding form and validates input', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(const MaterialApp(home: JwAuthPage()));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));

    expect(find.text('教务系统实名认证'), findsOneWidget);
    expect(find.text('教务系统学号'), findsOneWidget);
    expect(find.text('教务系统密码'), findsOneWidget);
    expect(find.text('图形验证码'), findsOneWidget);
    expect(find.text('立即绑定并同步课表'), findsOneWidget);

    // 未填密码点击提交
    await tester.tap(find.text('立即绑定并同步课表'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));

    expect(find.textContaining('请输入'), findsWidgets);
  });

  testWidgets('GradeQueryPage shows retry after unavailable grades', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: GradeQueryPage(
          loadReport: () async => throw Exception('请先完成教务认证'),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('学期成绩与GPA'), findsOneWidget);
    expect(find.text('重试'), findsOneWidget);
    expect(find.text('教务认证'), findsOneWidget);
    expect(find.text('高等数学 (下)'), findsNothing);
    expect(find.text('3.99'), findsNothing);
  });

  testWidgets(
    'NotificationCenterPage renders categories and handles mark read',
    (WidgetTester tester) async {
      tester.view.physicalSize = const Size(375, 812);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      const items = [
        InboxItem(
          sessionId: 'PROCESS_NOTIFICATION',
          sessionType: 'PROCESS_NOTIFICATION',
          title: '流程办理通知',
          lastContent: '明德楼302报修：王师傅正在现场处理',
          lastTime: '2026-10-18T10:45:00',
          unreadCount: 1,
          pinned: true,
        ),
        InboxItem(
          sessionId: 'AI_ASSISTANT:1',
          sessionType: 'AI_ASSISTANT',
          conversationId: '1',
          title: '冒烟',
          lastContent: '继续和 AI 助手小绵对话',
          lastTime: '2026-10-17T21:48:00',
        ),
      ];
      await tester.pumpWidget(
        MaterialApp(
          home: NotificationCenterPage(
            initialItems: items,
            initialSummary: const MessageCategorySummary(
              processNoticeUnread: 1,
              totalUnread: 1,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('消息中心'), findsOneWidget);
      expect(find.text('全部已读'), findsOneWidget);
      expect(find.text('流程办理'), findsOneWidget);
      expect(find.text('互动提醒'), findsOneWidget);
      expect(find.text('校园公告'), findsOneWidget);
      expect(find.text('AI待办'), findsOneWidget);
      expect(find.text('冒烟'), findsNothing);
      expect(find.text('流程办理通知'), findsOneWidget);
      expect(find.text('置顶'), findsOneWidget);

      await tester.tap(find.text('全部已读'));
      await tester.pumpAndSettle();

      expect(find.text('已将所有未读消息标记为已读'), findsOneWidget);
    },
  );

  testWidgets('ProfilePage renders identity, stats, badges and menus', (
    WidgetTester tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    final client = ApiClient();
    final authService = AuthService(client);
    const session = UserSession(
      token: 'mock_token',
      userId: '20220816428',
      username: 'student01',
      displayName: '李同学',
      identityType: 'STUDENT',
      authorizationStatus: 'BASIC',
      isNewUser: false,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: ProfilePage(session: session, authService: authService),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('个人中心'), findsOneWidget);
    expect(find.text('李同学'), findsOneWidget);
    expect(find.text('获赞'), findsOneWidget);
    expect(find.text('荣誉典藏室'), findsOneWidget);
    expect(find.textContaining('成长等级与荣誉勋章'), findsOneWidget);
    final scrollable = find.descendant(
      of: find.byType(ProfilePage),
      matching: find.byType(Scrollable).first,
    );
    await tester.scrollUntilVisible(
      find.text('社区足迹'),
      500,
      scrollable: scrollable,
    );
    expect(find.text('社区足迹'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text('帮助中心与反馈'),
      500,
      scrollable: scrollable,
    );
    expect(find.text('我的浏览历史'), findsOneWidget);
    expect(find.text('设置与账号安全'), findsOneWidget);
    expect(find.text('帮助中心与反馈'), findsOneWidget);
  });

  testWidgets('Settings, device and account center pages render', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    final authService = AuthService(ApiClient());
    await tester.pumpWidget(
      MaterialApp(home: SettingsSecurityPage(authService: authService)),
    );
    await tester.pumpAndSettle();
    expect(find.text('通用设置'), findsOneWidget);
    expect(find.text('社区发帖显示院系标签'), findsOneWidget);
    expect(find.text('退出当前账号'), findsNothing);
    await tester.scrollUntilVisible(
      find.text('隐私政策'),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('隐私政策'), findsOneWidget);

    await tester.pumpWidget(const MaterialApp(home: DeviceManagementPage()));
    await tester.pumpAndSettle();
    expect(find.text('登录设备管理'), findsOneWidget);

    await tester.pumpWidget(
      MaterialApp(home: AccountCenterPage(authService: authService)),
    );
    await tester.pumpAndSettle();
    expect(find.text('账号中心'), findsOneWidget);
    expect(find.text('账号信息'), findsOneWidget);
    expect(find.text('编辑个人资料'), findsOneWidget);
  });

  testWidgets('Register, forgot password and email binding pages render', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());
    final authService = AuthService(ApiClient());

    await tester.pumpWidget(
      MaterialApp(home: RegisterPage(authService: authService)),
    );
    await tester.pumpAndSettle();
    expect(find.text('注册账号'), findsOneWidget);
    expect(find.text('登录 ID'), findsOneWidget);
    expect(find.text('登录密码'), findsOneWidget);
    expect(find.text('邮箱'), findsWidgets);
    expect(find.text('完成注册'), findsOneWidget);

    await tester.pumpWidget(
      MaterialApp(home: ForgotPasswordPage(authService: authService)),
    );
    await tester.pumpAndSettle();
    expect(find.text('找回密码'), findsOneWidget);
    expect(find.text('重置密码'), findsOneWidget);

    await tester.pumpWidget(
      MaterialApp(home: EmailBindingPage(authService: authService)),
    );
    await tester.pumpAndSettle();
    expect(find.text('邮箱'), findsWidgets);
    expect(find.text('绑定邮箱'), findsOneWidget);
  });

  testWidgets('MarkdownText renders headings, code blocks and mermaid', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: MarkdownText(
              data: '# 标题\n\n正文 **加粗**\n\n```dart\nvoid main() {}\n```\n\n```mermaid\ngraph TD\nA-->B\n```',
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    expect(find.textContaining('标题', findRichText: true), findsOneWidget);
    expect(find.text('dart'), findsOneWidget);
    expect(find.text('代码'), findsNWidgets(2));
    expect(find.text('预览'), findsNWidgets(2));
    expect(find.text('Mermaid 图表'), findsNothing);
    await tester.tap(find.text('预览').last);
    await tester.pump();
    expect(find.byType(Image), findsOneWidget);
    expect(find.text('Mermaid 图表'), findsNothing);
  });

  testWidgets(
    'ServiceDetailPage renders service info, process steps, and apply button',
    (WidgetTester tester) async {
      tester.view.physicalSize = const Size(375, 812);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(
        const MaterialApp(
          home: ServiceDetailPage(title: '在读证明办理', department: '教务处 · 学籍科'),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('在读证明办理'), findsWidgets);
      expect(find.text('教务处 · 学籍科'), findsOneWidget);
      expect(find.text('办理条件与申报须知'), findsOneWidget);
      expect(find.text('办事闭环流程'), findsOneWidget);
      expect(find.text('立即在线办理'), findsOneWidget);
    },
  );

  testWidgets(
    'ContentStatusPages render removal placeholder, review card, and penalty banner',
    (WidgetTester tester) async {
      tester.view.physicalSize = const Size(375, 812);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: Column(
              children: [
                ContentRemovedPlaceholder(),
                ContentUnderReviewCard(),
                UserPenaltyBanner(),
              ],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('内容不可见'), findsOneWidget);
      expect(find.text('审核中 (仅自己可见)'), findsOneWidget);
      expect(find.textContaining('账号已被限制在社区公开发言'), findsOneWidget);
    },
  );
}
