import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/auth_service.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/models/home_dashboard_model.dart';
import 'package:smartcampus_app/models/service_catalog_models.dart';
import 'package:smartcampus_app/models/service_request_models.dart';
import 'package:smartcampus_app/models/user_session.dart';
import 'package:smartcampus_app/pages/community/community_home_page.dart';
import 'package:smartcampus_app/pages/home/home_dashboard_controller.dart';
import 'package:smartcampus_app/pages/home/home_page.dart';
import 'package:smartcampus_app/pages/home/widgets/home_community_preview.dart';
import 'package:smartcampus_app/pages/message/announcement_detail_page.dart';
import 'package:smartcampus_app/pages/profile/profile_page.dart';
import 'package:smartcampus_app/pages/service/my_requests_page.dart';
import 'package:smartcampus_app/pages/service/service_hall_page.dart';
import 'package:smartcampus_app/services/community_feed_controller.dart';
import 'package:smartcampus_app/services/community_interaction_store.dart';
import 'package:smartcampus_app/theme/app_theme.dart';

import 'community_interaction_test.dart' as fixtures;

class _FakeHomeGateway implements HomeDashboardGateway {
  @override
  Future<HomeDashboardData?> dashboard() async => const HomeDashboardData(
    greeting: '早上好，李同学',
    nextSchedule: NextScheduleModel(
      courseName: '移动应用交互设计',
      teacher: '王老师',
      timeRange: '10:00–11:40',
      location: '信息楼 402',
      startSection: 3,
      endSection: 4,
    ),
    announcement: HomeAnnouncementModel(
      id: '101',
      title: '关于开展秋季消防安全检查的通知',
      tag: '通知',
    ),
  );

  @override
  Future<List<ServiceItemData>?> services() async => const [
    ServiceItemData(
      itemId: '1001',
      itemCode: 'REPAIR-DORM',
      name: '宿舍报修',
      summary: '空调、水电、门窗故障在线报修',
    ),
    ServiceItemData(
      itemId: '2001',
      itemCode: 'AFFAIR-LEAVE',
      name: '请假申请',
      summary: '病假、事假线上审批',
    ),
  ];

  @override
  Future<List<CommunityPost>?> posts() async => [
    CommunityPost(
      id: 'post-99',
      authorId: 'u-1',
      authorName: '校园观察员',
      authorLevel: 'Lv.3',
      timeAgo: '5 分钟前',
      location: '',
      title: '二食堂新开轻食窗口测评',
      content: '今天中午去试了一下，分量很足。',
      commentCount: 12,
      likeCount: 38,
    ),
  ];

  @override
  Future<List<HomeAnnouncementModel>?> announcements() async => const [
    HomeAnnouncementModel(
      id: '101',
      title: '关于开展秋季消防安全检查的通知',
      tag: '通知',
      summary: '请各宿舍于本周五前排查违规电器，保卫处将联合宿管中心开展专项检查。',
      publishDate: '2026-11-06',
      author: '保卫处',
    ),
  ];
}

class _SpyFeedGateway implements CommunityFeedGateway {
  final List<CommunityFeedQuery> queries = [];

  @override
  Future<CommunityFeedBatch?> fetch(
    CommunityFeedQuery query,
    int pageNum,
  ) async {
    queries.add(query);
    return CommunityFeedBatch(
      [
        CommunityFeedItem.ofPost(
          fixtures.post('p-${query.keyword.isEmpty ? "all" : query.keyword}'),
        ),
      ],
      1,
      1,
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const testSession = UserSession(
    token: 'test-token',
    userId: 'u-test',
    username: '20260001',
    displayName: '李同学',
    identityType: 'STUDENT',
    authorizationStatus: 'BASIC',
    isNewUser: false,
  );

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  testWidgets(
    'P0-1: Home global search modal filters services/schedule/announcements/posts in real time and links to community search',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: HomePage(
            session: testSession,
            authService: AuthService(ApiClient()),
            dashboardGateway: _FakeHomeGateway(),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      // 打开全局搜索弹层
      await tester.tap(find.byTooltip('全局搜索'));
      await tester.pumpAndSettle();

      expect(find.byKey(const ValueKey('home-global-search-input')), findsOneWidget);
      expect(find.text('快捷推荐'), findsOneWidget);

      // 输入“宿舍”，应匹配到「宿舍报修」服务与包含“宿舍”摘要的公告
      await tester.enterText(
        find.byKey(const ValueKey('home-global-search-input')),
        '宿舍',
      );
      await tester.pump();

      expect(
        find.byKey(const ValueKey('home-search-service-REPAIR-DORM')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('home-search-ann-101')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('home-search-in-community')),
        findsOneWidget,
      );

      // 输入“交互设计”，应匹配到当前课表课程
      await tester.enterText(
        find.byKey(const ValueKey('home-global-search-input')),
        '交互设计',
      );
      await tester.pump();
      expect(
        find.byKey(const ValueKey('home-search-schedule')),
        findsOneWidget,
      );

      // 输入“轻食”，应匹配到热门动态帖子，点击「在社区中搜索“轻食”」跳转到社区并激活搜索条
      await tester.enterText(
        find.byKey(const ValueKey('home-global-search-input')),
        '轻食',
      );
      await tester.pump();
      expect(
        find.byKey(const ValueKey('home-search-post-post-99')),
        findsOneWidget,
      );

      await tester.tap(find.byKey(const ValueKey('home-search-in-community')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(
        find.byKey(const ValueKey('community-search-active-bar')),
        findsOneWidget,
      );
      expect(find.text('搜索：“轻食”'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'P0-2: Home announcement detail displays real summary/author/date and provides All Announcements action',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: HomePage(
            session: testSession,
            authService: AuthService(ApiClient()),
            dashboardGateway: _FakeHomeGateway(),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      // 点击首页公告栏
      await tester.tap(find.textContaining('关于开展秋季消防安全检查的通知'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.byType(AnnouncementDetailPage), findsOneWidget);
      expect(find.text('关于开展秋季消防安全检查的通知'), findsOneWidget);
      expect(
        find.text('请各宿舍于本周五前排查违规电器，保卫处将联合宿管中心开展专项检查。'),
        findsOneWidget,
      );
      expect(find.textContaining('保卫处'), findsWidgets);
      expect(find.textContaining('2026-11-06'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('announcement-detail-open-all')),
        findsOneWidget,
      );
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'P0-3: Community active search bar shows current keyword and clears in one tap',
    (tester) async {
      final spyGateway = _SpyFeedGateway();
      final store = CommunityInteractionStore(
        accountId: 'viewer',
        gateway: fixtures.FakeGateway(),
      );
      addTearDown(store.dispose);

      final externalKeyword = ValueNotifier<String?>('考研自习室');
      addTearDown(externalKeyword.dispose);

      await tester.pumpWidget(
        MaterialApp(
          home: CommunityHomePage(
            interactionStore: store,
            feedGateway: spyGateway,
            sectionsLoader: () async => const [
              CommunitySection(id: '1', code: 'TREEHOLE', name: '树洞'),
            ],
            externalSearchKeyword: externalKeyword,
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(
        find.byKey(const ValueKey('community-search-active-bar')),
        findsOneWidget,
      );
      expect(find.text('搜索：“考研自习室”'), findsOneWidget);
      expect(spyGateway.queries.last.keyword, '考研自习室');

      // 一键清除搜索词
      await tester.tap(find.byKey(const ValueKey('community-search-clear')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(
        find.byKey(const ValueKey('community-search-active-bar')),
        findsNothing,
      );
      expect(spyGateway.queries.last.keyword, isEmpty);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'P0-4: MyRequestsPage auto-prefetches across pages so filter counts and filtered items are complete',
    (tester) async {
      final page1Records = List.generate(
        20,
        (i) => <String, dynamic>{
          'requestId': '${i + 1}',
          'requestNo': 'SR-20261106-${(i + 1).toString().padLeft(3, '0')}',
          'title': '已办结申请 #${i + 1}',
          'itemName': '在读证明',
          'status': 'COMPLETED',
          'submittedAt': '2026-11-01 10:00:00',
        },
      );
      final page2Records = <Map<String, dynamic>>[
        {
          'requestId': '21',
          'requestNo': 'SR-20261106-021',
          'title': '二号楼 302 空调漏水待验收',
          'itemName': '宿舍报修',
          'status': 'PROCESSING',
          'workOrderId': '901',
          'workOrderNo': 'WO-20261106-021',
          'workOrderStatus': 'PENDING_CONFIRMATION',
          'submittedAt': '2026-11-05 14:00:00',
        },
        {
          'requestId': '22',
          'requestNo': 'SR-20261106-022',
          'title': '图书馆插座松动处理中',
          'itemName': '公共报修',
          'status': 'PROCESSING',
          'workOrderId': '902',
          'workOrderNo': 'WO-20261106-022',
          'workOrderStatus': 'PROCESSING',
          'submittedAt': '2026-11-06 09:00:00',
        },
      ];

      await tester.pumpWidget(
        MaterialApp(
          home: MyRequestsPage(
            requestsLoader: ({pageNum = 1, pageSize = 20, status}) async {
              if (pageNum == 1) {
                return {'records': page1Records, 'total': 22};
              }
              if (pageNum == 2) {
                return {'records': page2Records, 'total': 22};
              }
              return {'records': const [], 'total': 22};
            },
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      // 断言四个筛选 Tab 计数均已展示（22 / 1 / 1 / 20）
      expect(find.text('22'), findsOneWidget);
      expect(find.text('20'), findsOneWidget);
      expect(find.text('1'), findsNWidgets(2));

      // 切换到「待验收」Tab，能准确展示位于第 2 页的待验收工单
      await tester.tap(find.text('待验收'));
      await tester.pumpAndSettle();

      expect(find.text('二号楼 302 空调漏水待验收'), findsOneWidget);
      expect(find.text('去验收'), findsOneWidget);

      // 切换到「处理中」Tab，能准确展示位于第 2 页的处理中工单
      await tester.tap(find.text('处理中'));
      await tester.pumpAndSettle();

      expect(find.text('图书馆插座松动处理中'), findsOneWidget);
    },
  );

  testWidgets(
    'P1-1: ProfilePage provides high-frequency campus service entries with AppColors theme',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: ProfilePage(
            session: testSession,
            authService: AuthService(ApiClient()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('校园事务与服务'), findsOneWidget);
      expect(find.text('我的申请'), findsOneWidget);
      expect(find.text('我的课表'), findsOneWidget);
      expect(find.text('成绩与绩点'), findsOneWidget);
      expect(find.text('教务实名认证'), findsOneWidget);
      expect(find.text('宿舍电费查询'), findsOneWidget);
    },
  );

  testWidgets(
    'P1-2: ServiceHallPage replaces duplicate quick chips with active request status banner',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: ServiceHallPage(
            initialCategories: [
              ServiceCategoryData(
                categoryId: '100',
                categoryCode: 'REPAIR',
                name: '报修服务',
                description: '',
                items: [
                  ServiceItemData(
                    itemId: '1001',
                    itemCode: 'REPAIR-DORM',
                    name: '宿舍报修',
                    summary: '水电门窗故障报修',
                  ),
                ],
              ),
            ],
            initialRequestCount: 1,
            initialActiveRequest: ServiceRequestSummary(
              requestId: '88',
              requestNo: 'SR-20261106-088',
              title: '海棠苑 4 栋空调滴水',
              itemName: '宿舍报修',
              status: 'PROCESSING',
              workOrderStatus: 'PENDING_CONFIRMATION',
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('高频快捷办事'), findsNothing);
      expect(
        find.byKey(const ValueKey('service-hall-active-request')),
        findsOneWidget,
      );
      expect(find.text('我的进行中申请 · 待验收'), findsOneWidget);
      expect(find.text('海棠苑 4 栋空调滴水'), findsOneWidget);
      expect(find.text('去验收'), findsOneWidget);
    },
  );

  testWidgets(
    'P1-3: HomeCommunityPreview synchronizes like state via CommunityInteractionStore and uses AppColors.likeActive',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: HomeCommunityPreview(
              post: const CommunityPost(
                id: 'post-99',
                authorId: 'u-1',
                authorName: '校园观察员',
                authorLevel: 'Lv.3',
                timeAgo: '5 分钟前',
                location: '',
                title: '二食堂新开轻食窗口测评',
                content: '今天中午去试了一下，分量很足。',
                commentCount: 12,
                likeCount: 39,
                isLiked: true,
              ),
              baseUrl: 'https://school.test',
              liked: true,
              likeCount: 39,
              likePending: false,
              onOpen: () {},
              onLike: () {},
            ),
          ),
        ),
      );
      await tester.pump();

      final favIcon = tester.widget<Icon>(
        find.descendant(
          of: find.byKey(const ValueKey('home-community-like')),
          matching: find.byType(Icon),
        ),
      );
      expect(favIcon.icon, Icons.favorite_rounded);
      expect(favIcon.color, AppColors.likeActive);
    },
  );
}
