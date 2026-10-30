import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/auth_service.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/models/home_dashboard_model.dart';
import 'package:smartcampus_app/models/service_catalog_models.dart';
import 'package:smartcampus_app/models/user_session.dart';
import 'package:smartcampus_app/pages/home/home_dashboard_controller.dart';
import 'package:smartcampus_app/pages/home/home_page.dart';
import 'package:smartcampus_app/pages/home/widgets/home_campus_header.dart';
import 'package:smartcampus_app/pages/home/widgets/home_community_preview.dart';
import 'package:smartcampus_app/pages/home/widgets/home_service_hub.dart';
import 'package:smartcampus_app/widgets/app_bottom_navigation.dart';

class _OneScreenGateway implements HomeDashboardGateway {
  @override
  Future<HomeDashboardData?> dashboard() async => const HomeDashboardData(
    greeting: '早上好，陈同学',
    nextSchedule: NextScheduleModel(
      courseName: '移动应用交互设计',
      timeRange: '10:00–11:40',
      location: '信息楼 402',
      startSection: 3,
      endSection: 4,
    ),
    inProgressRequest: HomeInProgressRequestModel(
      requestId: 'preview',
      orderNo: 'WO-20261025-001',
      itemName: '宿舍空调报修',
      currentStatus: 'PROCESSING',
    ),
  );

  @override
  Future<List<ServiceItemData>?> services() async => const [
    ServiceItemData(
      itemId: '1',
      itemCode: 'REPAIR-DORM',
      name: '宿舍报修',
      summary: '',
    ),
    ServiceItemData(
      itemId: '2',
      itemCode: 'AFFAIR-LEAVE',
      name: '我的申请',
      summary: '',
    ),
    ServiceItemData(
      itemId: '3',
      itemCode: 'AFFAIR-VENUE',
      name: '场地申请',
      summary: '',
    ),
    ServiceItemData(
      itemId: '4',
      itemCode: 'GUIDE-CONSULT',
      name: '校园咨询',
      summary: '',
    ),
  ];

  @override
  Future<List<CommunityPost>?> posts() async => [
    for (var index = 1; index <= 3; index++)
      CommunityPost(
        id: 'post-$index',
        authorId: 'author-$index',
        authorName: '校园生活研究所',
        authorLevel: '',
        timeAgo: '10 分钟前',
        location: '',
        title: switch (index) {
          1 => '银杏季校园漫游：你最喜欢哪个角落？',
          2 => '图书馆自习座位体验交流',
          _ => '周末运动场使用情况分享',
        },
        content: '接口内容在详情页完整展示。',
        commentCount: 10 + index,
        likeCount: 40 + index,
      ),
  ];

  @override
  Future<List<HomeAnnouncementModel>?> announcements() async => const [
    HomeAnnouncementModel(id: 'n', title: '图书馆开放时间调整', tag: '校园公告'),
  ];
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('390x844 home fits one screen with long title and three posts', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    tester.view.viewPadding = FakeViewPadding.zero;
    tester.view.padding = FakeViewPadding.zero;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetViewPadding);
    addTearDown(tester.view.resetPadding);
    const session = UserSession(
      token: 'one-screen',
      userId: 'student',
      username: 'student01',
      displayName: '陈同学',
      identityType: 'STUDENT',
      authorizationStatus: 'BASIC',
      isNewUser: false,
    );
    await tester.pumpWidget(
      MaterialApp(
        home: HomePage(
          session: session,
          authService: AuthService(ApiClient()),
          dashboardGateway: _OneScreenGateway(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    final position = tester
        .state<ScrollableState>(find.byType(Scrollable).first)
        .position;
    expect(position.maxScrollExtent, 0);
    expect(
      tester.getBottomLeft(find.byType(HomeCommunityPreview)).dy,
      lessThanOrEqualTo(tester.getTopLeft(find.byType(AppBottomNavigation)).dy),
    );
    expect(find.byKey(const ValueKey('home-post-post-3')), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'header keeps actions clear and opens all services and pinned item',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      var aiOpened = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: HomeCampusHeader(
              greeting: '早上好，陈同学',
              date: '10月26日',
              unreadCount: 3,
              onSearch: () {},
              onNotifications: () {},
              onAi: () => aiOpened = true,
            ),
          ),
        ),
      );
      final aiBounds = tester.getRect(
        find.byKey(const ValueKey('home-header-ai')),
      );
      expect(
        aiBounds.overlaps(tester.getRect(find.byTooltip('全局搜索'))),
        isFalse,
      );
      expect(
        aiBounds.overlaps(tester.getRect(find.byTooltip('通知中心'))),
        isFalse,
      );
      await tester.tap(find.byKey(const ValueKey('home-header-ai')));
      expect(aiOpened, isTrue);

      var openedAll = false;
      final pinned = <String>{'4'};
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: HomeServiceHub(
              services: await _OneScreenGateway().services() ?? const [],
              isPinned: pinned.contains,
              onTogglePin: (_) async {},
              onOpen: (_) {},
              onOpenAll: () => openedAll = true,
            ),
          ),
        ),
      );
      await tester.pump();
      expect(find.text('校园咨询'), findsOneWidget);
      expect(find.text('全部服务'), findsOneWidget);
      expect(tester.getTopLeft(find.text('校园咨询')).dx, lessThan(100));
      await tester.tap(find.byKey(const ValueKey('home-all-services')));
      expect(openedAll, isTrue);
      expect(tester.takeException(), isNull);
    },
  );
}
