import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/auth_service.dart';
import 'package:smartcampus_app/api/schedule_api.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/models/home_dashboard_model.dart';
import 'package:smartcampus_app/models/service_catalog_models.dart';
import 'package:smartcampus_app/models/user_session.dart';
import 'package:smartcampus_app/pages/home/home_dashboard_controller.dart';
import 'package:smartcampus_app/pages/home/home_page.dart';
import 'package:smartcampus_app/pages/home/widgets/home_community_preview.dart';
import 'package:smartcampus_app/pages/home/widgets/home_course_card.dart';
import 'package:smartcampus_app/widgets/app_bottom_navigation.dart';
import 'package:smartcampus_app/pages/community/post_detail_page.dart';
import 'package:smartcampus_app/pages/schedule/schedule_page.dart';
import 'package:smartcampus_app/pages/service/my_requests_page.dart';
import 'package:smartcampus_app/pages/profile/profile_page.dart';
import 'package:smartcampus_app/services/schedule_cache.dart';

class ControlledGateway implements HomeDashboardGateway {
  final dashboardRequests = <Completer<HomeDashboardData?>>[];
  final serviceRequests = <Completer<List<ServiceItemData>?>>[];
  final postRequests = <Completer<List<CommunityPost>?>>[];
  final announcementRequests = <Completer<List<HomeAnnouncementModel>?>>[];
  @override
  Future<HomeDashboardData?> dashboard() {
    final c = Completer<HomeDashboardData?>();
    dashboardRequests.add(c);
    return c.future;
  }

  @override
  Future<List<ServiceItemData>?> services() {
    final c = Completer<List<ServiceItemData>?>();
    serviceRequests.add(c);
    return c.future;
  }

  @override
  Future<List<CommunityPost>?> posts() {
    final c = Completer<List<CommunityPost>?>();
    postRequests.add(c);
    return c.future;
  }

  @override
  Future<List<HomeAnnouncementModel>?> announcements() {
    final c = Completer<List<HomeAnnouncementModel>?>();
    announcementRequests.add(c);
    return c.future;
  }
}

class ReadyGateway implements HomeDashboardGateway {
  @override
  Future<HomeDashboardData?> dashboard() async => const HomeDashboardData(
    greeting: '下午好，测试同学',
    nextSchedule: NextScheduleModel(
      courseName: '很长很长的课程名称与实践项目设计课程',
      location: '综合实训中心很长的教室名称',
      teacher: '测试教师',
    ),
  );
  @override
  Future<List<ServiceItemData>?> services() async => [
    for (var i = 0; i < 7; i++)
      ServiceItemData(
        itemId: '$i',
        itemCode: 'TEST',
        name: '很长的常用服务名称$i',
        summary: '',
      ),
  ];
  @override
  Future<List<CommunityPost>?> posts() async => [];
  @override
  Future<List<HomeAnnouncementModel>?> announcements() async => [];
}

class _PostsGateway extends ReadyGateway {
  final List<CommunityPost> data;
  _PostsGateway(this.data);
  @override
  Future<List<CommunityPost>?> posts() async => data;
}

class BusyHomeGateway extends ReadyGateway {
  @override
  Future<HomeDashboardData?> dashboard() async => const HomeDashboardData(
    greeting: '早上好，测试同学',
    academicInfo: AcademicInfoModel(formattedDate: '10月25日 · 第8周'),
    nextSchedule: NextScheduleModel(
      courseName: '移动应用交互设计',
      timeRange: '10:00–11:40',
      location: '信息楼 402',
      teacher: '陈老师',
    ),
    inProgressRequest: HomeInProgressRequestModel(
      requestId: 'request-real-1',
      orderNo: 'WO-20261025-001',
      itemName: '宿舍空调报修',
      currentStatus: 'PROCESSING',
    ),
  );

  @override
  Future<List<HomeAnnouncementModel>?> announcements() async => [
    const HomeAnnouncementModel(
      id: 'notice-1',
      title: '图书馆开放时间调整',
      tag: '校园公告',
    ),
  ];
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    UserSession.clearCurrentUser();
  });

  test('home loads independently, preserves good sections and retries only failures', () async {
    final gateway = ControlledGateway();
    final controller = HomeDashboardController(
      gateway: gateway,
      accountId: 'a',
      baseUrl: 'https://a',
    );
    final refresh = controller.refresh();
    expect(gateway.dashboardRequests, hasLength(1));
    expect(gateway.serviceRequests, hasLength(1));
    expect(gateway.postRequests, hasLength(1));
    expect(gateway.announcementRequests, hasLength(1));
    gateway.serviceRequests.single.complete([]);
    gateway.postRequests.single.complete([]);
    gateway.announcementRequests.single.complete([]);
    await Future<void>.delayed(Duration.zero);
    expect(controller.services.loading, isFalse);
    expect(controller.dashboard.loading, isTrue);
    gateway.dashboardRequests.single.complete(null);
    await refresh;
    expect(controller.dashboard.failed, isTrue);
    expect(controller.services.failed, isFalse);
    final retry = controller.reloadDashboard();
    gateway.dashboardRequests.last.complete(
      const HomeDashboardData(greeting: 'new'),
    );
    await retry;
    expect(controller.dashboard.data?.greeting, 'new');
    expect(gateway.serviceRequests, hasLength(1));
    final failedRefresh = controller.reloadDashboard();
    gateway.dashboardRequests.last.complete(null);
    await failedRefresh;
    expect(controller.dashboard.data?.greeting, 'new');
    expect(controller.dashboard.failed, isTrue);
    controller.dispose();
  });

  test('home rejects late responses and disposed completions', () async {
    final gateway = ControlledGateway();
    final controller = HomeDashboardController(
      gateway: gateway,
      accountId: 'a',
      baseUrl: 'a',
    );
    final old = controller.reloadDashboard();
    final fresh = controller.reloadDashboard();
    gateway.dashboardRequests.last.complete(
      const HomeDashboardData(greeting: 'fresh'),
    );
    await fresh;
    gateway.dashboardRequests.first.complete(
      const HomeDashboardData(greeting: 'old'),
    );
    await old;
    expect(controller.dashboard.data?.greeting, 'fresh');
    final pending = controller.reloadDashboard();
    controller.dispose();
    gateway.dashboardRequests.last.complete(null);
    await pending;
  });

  test('pinned services persist only for their account and host', () async {
    final controller = HomeDashboardController(
      gateway: ReadyGateway(),
      accountId: 'a',
      baseUrl: 'a',
    );
    await controller.reloadServices();
    expect(await controller.togglePin('5'), isTrue);
    expect(controller.orderedServices.first.itemId, '5');
    final restored = HomeDashboardController(
      gateway: ReadyGateway(),
      accountId: 'a',
      baseUrl: 'a',
    );
    await restored.loadPins();
    expect(restored.isPinned('5'), isTrue);
    final other = HomeDashboardController(
      gateway: ReadyGateway(),
      accountId: 'b',
      baseUrl: 'a',
    );
    await other.loadPins();
    expect(other.isPinned('5'), isFalse);
    final otherHost = HomeDashboardController(
      gateway: ReadyGateway(),
      accountId: 'a',
      baseUrl: 'b',
    );
    await otherHost.loadPins();
    expect(otherHost.isPinned('5'), isFalse);
    controller.dispose();
    restored.dispose();
    other.dispose();
    otherHost.dispose();
  });

  test('real schedule snapshot survives new cache instances and isolates query/account/host', () async {
    const cache = ScheduleCache(accountId: 'a', baseUrl: 'a');
    final data = {'semesterCode': '2026-1', 'week': 8, 'days': <Object>[]};
    await cache.save(data);
    final restored = await const ScheduleCache(
      accountId: 'a',
      baseUrl: 'a',
    ).read(week: 8);
    expect(restored?['days'], isEmpty);
    expect(restored?['_offline'], isTrue);
    expect(DateTime.tryParse('${restored?['_syncedAt']}'), isNotNull);
    expect(await cache.read(week: 9), isNull);
    expect(await cache.read(semesterCode: '2027-1', week: 8), isNull);
    expect(
      await const ScheduleCache(accountId: 'b', baseUrl: 'a').read(),
      isNull,
    );
    expect(
      await const ScheduleCache(accountId: 'a', baseUrl: 'b').read(),
      isNull,
    );
    expect(
      await const ScheduleCache(accountId: '', baseUrl: 'a').read(),
      isNull,
    );
    final prefs = await SharedPreferences.getInstance();
    for (final key in prefs.getKeys()) {
      await prefs.setString(key, '{corrupt');
    }
    expect(await cache.read(), isNull);
  });

  test('schedule API saves real data then returns offline snapshot, discards old-account result', () async {
    var offline = false;
    final response = Completer<http.Response>();
    var defer = false;
    final api = ScheduleApi(
      ApiClient(
        baseUrl: 'https://school.test',
        client: MockClient((_) async {
          if (defer) return response.future;
          if (offline) throw Exception('offline');
          return http.Response(
            jsonEncode({
              'success': true,
              'data': {'week': 8, 'days': []},
            }),
            200,
          );
        }),
      ),
    );
    UserSession.currentUserId = 'a';
    expect((await api.fetchWeekScheduleRaw())?['_offline'], isFalse);
    offline = true;
    expect((await api.fetchWeekScheduleRaw())?['_offline'], isTrue);
    UserSession.currentUserId = 'b';
    expect(await api.fetchWeekScheduleRaw(), isNull);
    UserSession.currentUserId = 'a';
    defer = true;
    final pending = api.fetchWeekScheduleRaw(week: 9);
    UserSession.currentUserId = 'b';
    response.complete(http.Response(jsonEncode({'week': 9, 'days': []}), 200));
    expect(await pending, isNull);
  });

  for (final width in [320.0, 360.0, 390.0, 430.0]) {
    for (final scale in [1.0, 1.3, 1.5]) {
      testWidgets(
        'course card wraps at $width / $scale and empty state stays usable',
        (tester) async {
          tester.view.physicalSize = Size(width, 1000);
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.resetPhysicalSize);
          var opened = false;
          await tester.pumpWidget(
            MaterialApp(
              home: MediaQuery(
                data: MediaQueryData(
                  size: Size(width, 1000),
                  textScaler: TextScaler.linear(scale),
                ),
                child: Scaffold(
                  body: SingleChildScrollView(
                    child: HomeCourseCard(
                      course: const NextScheduleModel(
                        courseName: '分布式系统体系结构与应用实践这是一门特别长的课程名称',
                        teacher: '很长的教师姓名及课程团队',
                        location: '综合实训中心东区B座一楼一百零一教室',
                      ),
                      onOpen: () => opened = true,
                    ),
                  ),
                ),
              ),
            ),
          );
          expect(tester.takeException(), isNull);
          await tester.tap(find.byKey(const ValueKey('home-course-card')));
          expect(opened, isTrue);
          expect(
            tester
                .getSize(find.byKey(const ValueKey('home-course-card')))
                .height,
            greaterThanOrEqualTo(48),
          );
          await tester.pumpWidget(
            MaterialApp(
              home: Scaffold(body: HomeCourseCard(onOpen: () {})),
            ),
          );
          expect(find.text('给今天留一点从容'), findsOneWidget);
          expect(tester.takeException(), isNull);
        },
      );
    }
  }

  testWidgets(
    'home redesign prioritizes real tasks and opens request progress',
    (tester) async {
      tester.view.physicalSize = const Size(390, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      const session = UserSession(
        token: 'home-redesign',
        userId: 'student',
        username: 'student01',
        displayName: '测试同学',
        identityType: 'STUDENT',
        authorizationStatus: 'AUTHORIZED',
        isNewUser: false,
      );
      await tester.pumpWidget(
        MaterialApp(
          home: HomePage(
            session: session,
            authService: AuthService(ApiClient()),
            dashboardGateway: BusyHomeGateway(),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      expect(find.text('移动应用交互设计'), findsOneWidget);
      expect(find.textContaining('10:00–11:40'), findsOneWidget);
      expect(find.textContaining('信息楼 402'), findsOneWidget);
      expect(find.text('宿舍空调报修'), findsOneWidget);
      expect(find.text('校园公告 · 图书馆开放时间调整'), findsOneWidget);
      expect(find.text('校园服务台'), findsOneWidget);
      expect(find.text('校园生活 · 运转顺畅'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('home-action-card')));
      await tester.pumpAndSettle();
      expect(find.byType(MyRequestsPage), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('campus feed uses real posts and opens the selected discussion', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    tester.view.viewPadding = FakeViewPadding.zero;
    tester.view.padding = FakeViewPadding.zero;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetViewPadding);
    addTearDown(tester.view.resetPadding);
    const session = UserSession(
      token: 'campus-feed',
      userId: 'student',
      username: 'student01',
      displayName: '测试同学',
      identityType: 'STUDENT',
      authorizationStatus: 'BASIC',
      isNewUser: false,
    );
    CommunityPost post(int index) => CommunityPost(
      id: 'post-$index',
      authorId: 'author-$index',
      authorName: '真实作者$index',
      authorLevel: '',
      timeAgo: '刚刚',
      location: '',
      title: '真实校园动态$index',
      content: '接口返回的帖子内容',
      commentCount: index,
    );
    await tester.pumpWidget(
      MaterialApp(
        home: HomePage(
          session: session,
          authService: AuthService(ApiClient()),
          dashboardGateway: _PostsGateway([post(1), post(2), post(3)]),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('真实校园动态1'), findsOneWidget);
    expect(find.text('真实校园动态2'), findsOneWidget);
    expect(find.text('真实校园动态3'), findsOneWidget);
    final scrollable = find.byType(Scrollable).first;
    final position = tester.state<ScrollableState>(scrollable).position;
    expect(position.maxScrollExtent, 0, reason: '390×844 常规字号应完整展示三条动态，无需下拉');
    expect(find.text('学习互助小组'), findsNothing);
    expect(find.text('一起去社团赶集'), findsNothing);
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('home-post-post-3')),
      180,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.byKey(const ValueKey('home-post-post-3')));
    await tester.pumpAndSettle();
    expect(find.byType(PostDetailPage), findsOneWidget);
    expect(
      tester.widget<PostDetailPage>(find.byType(PostDetailPage)).post.id,
      'post-3',
    );
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('unauthenticated home keeps full services without forced task', (
    tester,
  ) async {
    const session = UserSession(
      token: 'home-basic',
      userId: 'student',
      username: 'student01',
      displayName: '测试同学',
      identityType: 'STUDENT',
      authorizationStatus: 'BASIC',
      isNewUser: false,
    );
    await tester.pumpWidget(
      MaterialApp(
        home: HomePage(
          session: session,
          authService: AuthService(ApiClient()),
          dashboardGateway: BusyHomeGateway(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('校园服务台'), findsOneWidget);
    expect(find.text('完成教务认证'), findsNothing);
    expect(find.byKey(const ValueKey('home-action-card')), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('home omits empty announcement and completed task areas', (
    tester,
  ) async {
    const session = UserSession(
      token: 'home-empty',
      userId: 'student',
      username: 'student01',
      displayName: '测试同学',
      identityType: 'STUDENT',
      authorizationStatus: 'AUTHORIZED',
      isNewUser: false,
    );
    await tester.pumpWidget(
      MaterialApp(
        home: HomePage(
          session: session,
          authService: AuthService(ApiClient()),
          dashboardGateway: ReadyGateway(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    expect(find.byKey(const ValueKey('home-action-card')), findsNothing);
    expect(find.text('暂无公告'), findsNothing);
    expect(find.byIcon(Icons.campaign_outlined), findsNothing);
    final service = find.byKey(const ValueKey('home-service-0'));
    await tester.scrollUntilVisible(
      service,
      180,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.longPress(service);
    await tester.pump();
    expect(find.text('已固定到常用服务前面'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'home has readable immediate content under reduced motion at 320 / 1.5',
    (tester) async {
      tester.view.physicalSize = const Size(320, 1200);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      const session = UserSession(
        token: 't',
        userId: 'a',
        username: 'a',
        displayName: '测试',
        identityType: 'STUDENT',
        authorizationStatus: 'BASIC',
        isNewUser: false,
      );
      await tester.pumpWidget(
        MaterialApp(
          home: MediaQuery(
            data: const MediaQueryData(
              size: Size(320, 1200),
              textScaler: TextScaler.linear(1.5),
              disableAnimations: true,
            ),
            child: HomePage(
              session: session,
              authService: AuthService(ApiClient()),
              dashboardGateway: ReadyGateway(),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      expect(find.textContaining('下一节'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'week changes ignore late result; offline timestamp and long course visible',
    (tester) async {
      final requests = <Completer<Map<String, dynamic>?>>[];
      await tester.pumpWidget(
        MaterialApp(
          home: SchedulePage(
            loadWeek: (_) {
              final c = Completer<Map<String, dynamic>?>();
              requests.add(c);
              return c.future;
            },
            loadAdjustments: () async => [],
          ),
        ),
      );
      requests[0].complete({'week': 8, 'currentWeek': 8, 'days': []});
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('下一周'));
      await tester.pump();
      await tester.tap(find.byTooltip('下一周'));
      await tester.pump();
      requests[2].complete({
        'week': 10,
        'currentWeek': 8,
        '_offline': true,
        '_syncedAt': '2026-10-24T12:00:00Z',
        'days': [
          {
            'dayOfWeek': 1,
            'courses': [
              {'courseName': '真实课程', 'startSection': 1, 'endSection': 2},
            ],
          },
        ],
      });
      await tester.pumpAndSettle();
      requests[1].complete({'week': 9, 'days': []});
      await tester.pumpAndSettle();
      expect(find.text('第 10 周 ▾'), findsOneWidget);
      expect(find.text('真实课程'), findsOneWidget);
      expect(find.textContaining('离线缓存'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  for (final width in [360.0, 390.0, 430.0]) {
    for (final scale in [1.3, 1.5]) {
      for (final page in ['Home', 'Schedule', 'Profile']) {
        testWidgets('$page full page at $width / $scale', (tester) async {
          tester.view.physicalSize = Size(width, 900);
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.resetPhysicalSize);
          const session = UserSession(
            token: 'matrix',
            userId: 'matrix-student',
            username: 'student01',
            displayName: '测试同学',
            identityType: 'STUDENT',
            authorizationStatus: 'BASIC',
            isNewUser: false,
          );
          final schedule = <String, dynamic>{
            'week': 4,
            'currentWeek': 4,
            'totalWeeks': 20,
            'weekStart': '2026-10-19',
            'days': [
              for (var day = 1; day <= 7; day++)
                {
                  'dayOfWeek': day,
                  'courses': [
                    {
                      'courseName': '分布式系统体系结构与实践项目设计课程',
                      'teacher': '测试教师与教学团队',
                      'location': '综合实训中心东区B座一百零一教室',
                      'startSection': 1,
                      'endSection': 2,
                    },
                  ],
                },
            ],
          };
          final server = MockClient((request) async {
            Object? data = <String, dynamic>{};
            final path = request.url.path;
            if (path.endsWith('/auth/me')) {
              data = {
                'user': {'displayName': '测试同学'},
              };
            } else if (path.contains('/community/authors/')) {
              data = {'college': '信息工程与数字技术学院', 'major': '软件工程与智能系统专业'};
            } else if (path.endsWith('/achievements/profile')) {
              data = {
                'badgeCount': 1,
                'totalBadgeCount': 7,
                'recentBadges': [
                  {'badgeCode': 'ACTUAL_AWARD', 'badgeName': '校园服务实践之星'},
                ],
              };
            } else if (path.endsWith('/service-requests')) {
              data = {
                'records': [
                  {
                    'id': 'request-1',
                    'itemName': '宿舍报修',
                    'title': '空调设备无法运行需要维修处理',
                    'status': 'PROCESSING',
                  },
                ],
              };
            } else if (path.endsWith('/schedule/week')) {
              data = schedule;
            }
            return http.Response(
              jsonEncode({'success': true, 'data': data}),
              200,
              headers: {'content-type': 'application/json; charset=utf-8'},
            );
          });
          await http.runWithClient(() async {
            final auth = AuthService(ApiClient());
            final Widget child = switch (page) {
              'Home' => HomePage(
                session: session,
                authService: auth,
                dashboardGateway: ReadyGateway(),
              ),
              'Schedule' => SchedulePage(
                loadWeek: (_) async => schedule,
                loadAdjustments: () async => [],
              ),
              _ => ProfilePage(session: session, authService: auth),
            };
            await tester.pumpWidget(
              MaterialApp(
                builder: (context, child) => MediaQuery(
                  data: MediaQuery.of(context).copyWith(
                    textScaler: TextScaler.linear(scale),
                    disableAnimations: true,
                  ),
                  child: child!,
                ),
                home: child,
              ),
            );
            await tester.pumpAndSettle();
            expect(tester.takeException(), isNull);
            final verticalScroll = find
                .byWidgetPredicate(
                  (widget) =>
                      widget is Scrollable &&
                      widget.axisDirection == AxisDirection.down,
                )
                .first;
            for (var step = 0; step < 5; step++) {
              await tester.drag(verticalScroll, const Offset(0, -650));
              await tester.pumpAndSettle();
              expect(tester.takeException(), isNull);
            }
            await tester.pumpWidget(const SizedBox.shrink());
            await tester.pump();
          }, () => server);
        });
      }
    }
  }
}
