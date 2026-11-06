import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/auth_service.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/models/home_dashboard_model.dart';
import 'package:smartcampus_app/models/user_session.dart';
import 'package:smartcampus_app/pages/home/home_page.dart';
import 'package:smartcampus_app/pages/home/widgets/home_action_card.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/models/service_catalog_models.dart';
import 'package:smartcampus_app/models/service_request_models.dart';
import 'package:smartcampus_app/pages/home/home_dashboard_controller.dart';
import 'package:smartcampus_app/pages/home/home_navigation.dart';
import 'package:smartcampus_app/pages/service/my_requests_page.dart';
import 'package:smartcampus_app/pages/service/repair_apply_page.dart';
import 'package:smartcampus_app/pages/service/service_apply_page.dart';
import 'package:smartcampus_app/pages/service/service_hall_page.dart';
import 'package:smartcampus_app/pages/service/work_order_detail_page.dart';

Map<String, dynamic> request({
  String flow = 'LEAVE_APPROVAL',
  String status = 'PROCESSING',
  List<String> actions = const [],
}) => {
  'requestId': 'request-1',
  'requestNo': 'SR-TEST-1',
  'title': '业务申请',
  'itemName': '服务事项',
  'itemCode': 'AFFAIR-LEAVE',
  'flowType': flow,
  'status': status,
  'businessStatus': flow == 'LEAVE_APPROVAL' ? 'APPROVED' : 'PROCESSING',
  'statusLabel': flow == 'LEAVE_APPROVAL' ? '已批准，待销假' : '处理中',
  'availableActions': actions,
  'leaveType': '事假',
  'leaveStartAt': '2026-11-07T08:00:00',
  'leaveEndAt': '2026-11-08T20:00:00',
  'leaveDays': 2,
  'approvalComment': '同意申请，请按时返校',
  // Historical erroneous order must never leak repair controls into a leave.
  'workOrder': {
    'orderId': 'order-1',
    'orderNo': 'WO-OLD',
    'status': 'PENDING_CONFIRMATION',
    'assigneeName': '维修测试人员',
    'assigneePhone': '13800000000',
    'repairAddress': '维修地址不应展示',
  },
};

class _ShortcutGateway implements HomeDashboardGateway {
  @override
  Future<HomeDashboardData?> dashboard() async =>
      const HomeDashboardData(greeting: '上午好');
  @override
  Future<List<ServiceItemData>?> services() async => const [
    ServiceItemData(
      itemId: 'configured-9',
      itemCode: 'REPAIR-DORM',
      name: '宿舍报修',
      summary: '',
      flowType: 'MANUAL',
    ),
  ];
  @override
  Future<List<CommunityPost>?> posts() async => [];
  @override
  Future<List<HomeAnnouncementModel>?> announcements() async => [];
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('首页批准请假优先显示业务状态且没有等待工单提示', (tester) async {
    final current = HomeInProgressRequestModel.fromJson({
      'requestId': 'leave-approved',
      'itemName': '请假申请',
      'currentStatus': 'PROCESSING',
      'flowType': 'LEAVE_APPROVAL',
      'businessStatus': 'APPROVED',
      'statusLabel': '已批准，待销假',
    });
    expect(current.flowType, 'LEAVE_APPROVAL');
    expect(current.businessStatus, 'APPROVED');
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HomeActionCard(request: current, onOpen: () {}),
        ),
      ),
    );
    expect(find.text('已批准，待销假'), findsOneWidget);
    expect(find.textContaining('等待生成工单'), findsNothing);
    expect(find.text('处理中'), findsNothing);
  });

  testWidgets('首页人工受理和旧响应无工单提示，报修保留待派单提示', (tester) async {
    for (final flowType in <String?>['MANUAL', null, 'REPAIR']) {
      final current = HomeInProgressRequestModel.fromJson({
        'requestId': 'request-1',
        'itemName': '服务申请',
        'currentStatus': 'SUBMITTED',
        'flowType': flowType,
      });
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: HomeActionCard(request: current, onOpen: () {}),
          ),
        ),
      );
      if (flowType == 'REPAIR') {
        expect(find.text('待派单 · 等待生成工单'), findsOneWidget);
      } else {
        expect(find.text('已提交'), findsOneWidget);
        expect(find.textContaining('等待生成工单'), findsNothing);
        expect(find.textContaining('待派单'), findsNothing);
      }
    }
  });

  testWidgets('搜索快捷推荐使用目录的真实事项ID与流程类型', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: HomePage(
          session: const UserSession(
            token: 'test-token',
            userId: 'flow-user',
            username: 'flow-user',
            displayName: '测试用户',
            identityType: 'STUDENT',
            authorizationStatus: 'BASIC',
            isNewUser: false,
          ),
          authService: AuthService(ApiClient()),
          dashboardGateway: _ShortcutGateway(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('全局搜索'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('宿舍报修').last);
    await tester.pumpAndSettle();
    expect(find.byType(RepairApplyPage), findsNothing);
    expect(find.byType(ServiceApplyPage), findsOneWidget);
    expect(
      tester.widget<ServiceApplyPage>(find.byType(ServiceApplyPage)).itemId,
      'configured-9',
    );
    await tester.pumpWidget(const SizedBox.shrink());
  });

  test('首页服务映射保留后端流程配置', () async {
    final client = ApiClient(
      client: MockClient(
        (_) async => http.Response(
          jsonEncode({
            'success': true,
            'data': [
              {
                'categoryCode': 'AFFAIR',
                'items': [
                  {
                    'itemId': 'manual-1',
                    'itemCode': 'REPAIR-DORM',
                    'itemName': '人工受理事项',
                    'flowType': 'MANUAL',
                  },
                ],
              },
            ],
          }),
          200,
          headers: {'content-type': 'application/json'},
        ),
      ),
    );
    final items = await ApiHomeDashboardGateway(client).services();
    expect(items?.single.flowType, 'MANUAL');
    expect(items?.single.isRepair, isFalse);
  });

  for (final (flow, code, destination) in [
    ('MANUAL', 'REPAIR-DORM', ServiceApplyPage),
    ('GUIDE', 'GUIDE-CONSULT', ServiceApplyPage),
    ('REPAIR', 'CUSTOM', RepairApplyPage),
  ]) {
    testWidgets('首页 $flow 按配置打开对应入口', (tester) async {
      var aiOpened = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () => HomeNavigation.openService(
                context,
                ServiceItemData(
                  itemId: 'entry-1',
                  itemCode: code,
                  name: '服务事项',
                  summary: '',
                  flowType: flow,
                ),
                onOpenAi: () => aiOpened = true,
              ),
              child: const Text('打开服务'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('打开服务'));
      await tester.pumpAndSettle();
      expect(aiOpened, isFalse);
      expect(find.byType(destination), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }

  test('目录配置优先于已知编码，不按名称推断流程', () {
    const overridden = ServiceItemData(
      itemId: '1',
      itemCode: 'REPAIR-DORM',
      name: '宿舍报修',
      summary: '',
      flowType: 'MANUAL',
    );
    expect(overridden.isRepair, isFalse);
    const customRepair = ServiceItemData(
      itemId: '2',
      itemCode: 'CUSTOM',
      name: '任意服务',
      summary: '',
      flowType: 'REPAIR',
    );
    expect(customRepair.isRepair, isTrue);
  });

  test('空 availableActions 是禁止操作，缺省旧响应保持兼容', () {
    final modern = ServiceRequestDetail.fromJson(request());
    expect(modern.canCancel, isFalse);
    expect(modern.canUrge, isFalse);
    expect(modern.canConfirm, isFalse);
    expect(modern.canEvaluate, isFalse);
    final legacy = ServiceRequestDetail.fromJson({
      'requestId': 'legacy',
      'status': 'SUBMITTED',
    });
    expect(legacy.canCancel, isTrue);
    expect(legacy.canUrge, isTrue);
    final omitted = ServiceRequestDetail.fromJson({
      ...request(),
      'availableActions': null,
    });
    expect(omitted.canUrge, isFalse);
  });

  testWidgets('请假批准详情展示审批与时间，不显示维修字段和验收操作', (tester) async {
    final data = request();
    await tester.pumpWidget(
      MaterialApp(
        home: WorkOrderDetailPage(
          requestId: 'request-1',
          detailLoader: (_) async => data,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('请假审批'), findsOneWidget);
    expect(find.text('已批准，待销假'), findsNWidgets(2));
    expect(find.text('2026-11-07 08:00'), findsOneWidget);
    expect(find.text('2 天'), findsOneWidget);
    expect(find.text('同意申请，请按时返校'), findsOneWidget);
    expect(find.text('处理师傅'), findsNothing);
    expect(find.text('报修地址'), findsNothing);
    expect(find.text('联系师傅'), findsNothing);
    expect(find.text('确认完工'), findsNothing);
    expect(find.text('撤销申请'), findsNothing);
    expect(find.text('催办'), findsNothing);
    expect(find.textContaining('后勤已受理'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('请假销假后显示学生业务终态及销假时间', (tester) async {
    final data = {
      ...request(status: 'COMPLETED'),
      'businessStatus': 'CLOSED',
      'statusLabel': '已销假',
      'leaveCancelAt': '2026-11-08T20:00:00',
      'leaveCancelStatus': 'CANCELLED',
    };
    await tester.pumpWidget(
      MaterialApp(
        home: WorkOrderDetailPage(
          requestId: 'request-1',
          detailLoader: (_) async => data,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('已销假'), findsNWidgets(2));
    expect(find.text('销假时间'), findsOneWidget);
    expect(find.text('评价本次服务'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('报修确认和退回独立依据 availableActions', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: WorkOrderDetailPage(
          requestId: 'request-1',
          detailLoader: (_) async =>
              request(flow: 'REPAIR', actions: ['REJECT_COMPLETION']),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('未修好，退回'), findsOneWidget);
    expect(find.text('确认完工'), findsNothing);
    expect(find.text('处理师傅'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(
      MaterialApp(
        home: WorkOrderDetailPage(
          requestId: 'request-1',
          detailLoader: (_) async => request(
            flow: 'REPAIR',
            actions: ['CONFIRM', 'REJECT_COMPLETION'],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('确认完工'), findsOneWidget);
    expect(find.text('未修好，退回'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('列表请假不进入维修待验收，撤回独立于催办', (tester) async {
    final data = {
      ...request(status: 'SUBMITTED', actions: ['CANCEL']),
      'businessStatus': 'PENDING_APPROVAL',
      'statusLabel': '待审批',
      'workOrderId': 'order-1',
      'workOrderNo': 'WO-OLD',
      'workOrderStatus': 'PENDING_CONFIRMATION',
    };
    expect(ServiceRequestSummary.fromJson(data).isPendingConfirmation, isFalse);
    await tester.pumpWidget(
      MaterialApp(
        home: MyRequestsPage(
          requestsLoader: ({pageNum = 1, pageSize = 20, status}) async => {
            'total': 1,
            'records': [data],
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('待审批'), findsOneWidget);
    expect(find.textContaining('WO-OLD'), findsNothing);
    expect(find.text('去验收'), findsNothing);
    expect(find.text('撤销'), findsOneWidget);
    expect(find.text('催办'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('服务大厅进行中请假显示后端审批状态', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: ServiceHallPage(
          initialCategories: const [
            ServiceCategoryData(
              categoryId: 'affair',
              categoryCode: 'AFFAIR',
              name: '学生事务',
              description: '',
              items: [
                ServiceItemData(
                  itemId: 'leave-1',
                  itemCode: 'AFFAIR-LEAVE',
                  flowType: 'LEAVE_APPROVAL',
                  name: '请假申请',
                  summary: '',
                ),
              ],
            ),
          ],
          initialActiveRequest: ServiceRequestSummary.fromJson(request()),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('已批准，待销假'), findsOneWidget);
    expect(find.text('去验收'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('GUIDE事项只有指南、材料和FAQ，没有提交表单入口', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: ServiceApplyPage(
          itemId: 'guide-1',
          detailLoader: (_) async => {
            'itemName': '证明办理咨询',
            'flowType': 'GUIDE',
            'guideContent': '请到受理窗口办理，不支持在线出证。',
            'materialList': [
              {'name': '学生证', 'required': true},
            ],
            'faq': [
              {'question': '如何咨询？', 'answer': '联系受理部门'},
            ],
            'formSchema': [
              {'key': 'reason', 'label': '事由', 'type': 'input'},
            ],
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('办事指南'), findsOneWidget);
    expect(find.text('学生证（必需）'), findsOneWidget);
    expect(find.text('如何咨询？'), findsOneWidget);
    expect(find.text('提交申请'), findsNothing);
    expect(find.byType(TextFormField), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
