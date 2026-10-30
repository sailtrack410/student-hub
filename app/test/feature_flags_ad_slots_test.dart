import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/api/ad_slot_api.dart';
import 'package:smartcampus_app/main.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/feature_flags.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/models/service_catalog_models.dart';
import 'package:smartcampus_app/pages/service/service_hall_page.dart';

/// 功能开关消费 + 广告位读取的可测试替身：只覆写 get，不产生真实网络请求。
class _FakeApiClient extends ApiClient {
  _FakeApiClient({required this.onGet});

  final Future<dynamic> Function(String path, Map<String, dynamic>? query)
  onGet;
  final List<String> requestedPaths = <String>[];

  @override
  Future<dynamic> get(
    String path, {
    Map<String, dynamic>? query,
    RequestCancellation? cancellation,
  }) {
    requestedPaths.add(path);
    return onGet(path, query);
  }
}

CommunityAd _ad({
  String adId = '2112174270222426113',
  String clickId = 'click-1',
  String landingType = 'IN_APP_WEBVIEW',
  String title = '校园文创市集 · 秋季场',
}) {
  return CommunityAd(
    adId: adId,
    clickId: clickId,
    title: title,
    brandName: '校园文创中心',
    description: '秋季场限定周边',
    coverUrl: 'https://cdn.example.com/ad-cover.png',
    targetUrl: 'https://www.studenthub.example/campus-market',
    landingType: landingType,
    priority: 10,
    measurable: true,
    sponsoredLabel: '推广',
    ctaText: '查看市集',
  );
}

const List<ServiceCategoryData> _categories = [
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
];

void main() {
  setUp(() {
    FeatureFlags.resetForTest();
    AdSlotApi.clearCache();
  });

  tearDown(() {
    FeatureFlags.resetForTest();
    AdSlotApi.clearCache();
  });

  group('FeatureFlags 功能开关', () {
    test('未加载时使用默认值：广告/作业/AI 开，维护模式关', () {
      expect(FeatureFlags.isLoaded, isFalse);
      expect(FeatureFlags.communityAdsEnabled, isTrue);
      expect(FeatureFlags.homeworkEnabled, isTrue);
      expect(FeatureFlags.aiEnabled, isTrue);
      expect(FeatureFlags.maintenanceEnabled, isFalse);
      // 未下发的 key 使用调用方 fallback
      expect(FeatureFlags.enabled('feature.unknown', fallback: false), isFalse);
    });

    test('load 解析 /api/common/features 并缓存，关闭项生效', () async {
      final client = _FakeApiClient(
        onGet: (path, query) async {
          expect(path, '/api/common/features');
          return <String, dynamic>{
            'campusId': 0,
            'flags': <String, dynamic>{
              FeatureFlags.keyCommunityAds: false,
              FeatureFlags.keyHomework: false,
              FeatureFlags.keyAi: 'true',
            },
            'generatedAt': '2026-10-20T00:00:00',
          };
        },
      );

      await FeatureFlags.load(client);

      expect(FeatureFlags.isLoaded, isTrue);
      expect(FeatureFlags.communityAdsEnabled, isFalse);
      expect(FeatureFlags.homeworkEnabled, isFalse);
      expect(FeatureFlags.aiEnabled, isTrue, reason: '字符串 true 也要能解析');
      expect(FeatureFlags.maintenanceEnabled, isFalse, reason: '未下发时保留默认');
    });

    test('load 失败保留默认值且不抛出（不阻塞启动）', () async {
      final before = FeatureFlags.revision.value;
      final client = _FakeApiClient(
        onGet: (path, query) async {
          throw const ApiException(code: 'NETWORK_ERROR', message: 'offline');
        },
      );

      await FeatureFlags.load(client);

      expect(FeatureFlags.isLoaded, isFalse);
      expect(FeatureFlags.communityAdsEnabled, isTrue);
      expect(FeatureFlags.revision.value, before + 1, reason: '失败也要通知页面重读开关');
    });
  });

  group('AdSlotApi 广告位', () {
    test('开关关闭时不发请求、返回空列表（无广告请求副作用）', () async {
      FeatureFlags.applyForTest(<String, bool>{
        FeatureFlags.keyCommunityAds: false,
      });
      final client = _FakeApiClient(onGet: (_, _) async => <dynamic>[]);

      for (final slot in AdSlot.values) {
        final ads = await AdSlotApi.fetchAds(slot, client: client);
        expect(ads, isEmpty, reason: slot.apiValue);
      }
      expect(client.requestedPaths, isEmpty, reason: '关闭后不得请求广告位接口');
    });

    test('解析 FeedAdItemResponse 数组，跳过缺关键字段的条目', () async {
      FeatureFlags.applyForTest(<String, bool>{
        FeatureFlags.keyCommunityAds: true,
      });
      final client = _FakeApiClient(
        onGet: (path, query) async {
          expect(path, '/api/app/community/slot-ads');
          expect(query, <String, dynamic>{'slot': 'BANNER'});
          return <dynamic>[
            <String, dynamic>{
              'adId': '1',
              'clickId': 'c1',
              'title': '开学季校园卡',
              'brandName': '校园一卡通',
              'targetUrl': 'https://example.com/a',
              'landingType': 'IN_APP_WEBVIEW',
              'sponsoredLabel': '推广',
              'ctaText': '立即查看',
            },
            <String, dynamic>{'adId': '', 'title': '缺 adId 的脏数据'},
          ];
        },
      );

      final ads = await AdSlotApi.fetchAds(AdSlot.banner, client: client);

      expect(ads, hasLength(1));
      expect(ads.first.adId, '1');
      expect(ads.first.clickId, 'c1');
      expect(ads.first.isWebViewLanding, isTrue);
    });

    test('同一广告位进程内缓存，forceRefresh 才重新请求', () async {
      FeatureFlags.applyForTest(<String, bool>{
        FeatureFlags.keyCommunityAds: true,
      });
      var calls = 0;
      final client = _FakeApiClient(
        onGet: (_, _) async {
          calls++;
          return <dynamic>[
            <String, dynamic>{
              'adId': '1',
              'clickId': 'c$calls',
              'title': '广告 $calls',
              'brandName': '品牌',
              'targetUrl': 'https://example.com/a',
              'landingType': 'APP_PAGE',
            },
          ];
        },
      );

      final first = await AdSlotApi.fetchAds(
        AdSlot.serviceHall,
        client: client,
      );
      final cached = await AdSlotApi.fetchAds(
        AdSlot.serviceHall,
        client: client,
      );
      final refreshed = await AdSlotApi.fetchAds(
        AdSlot.serviceHall,
        client: client,
        forceRefresh: true,
      );

      expect(calls, 2);
      expect(cached.first.clickId, first.first.clickId);
      expect(refreshed.first.clickId, 'c2');
    });

    test('同一广告位并发读取合并为一次请求', () async {
      FeatureFlags.applyForTest(<String, bool>{
        FeatureFlags.keyCommunityAds: true,
      });
      var calls = 0;
      final completer = Completer<dynamic>();
      final client = _FakeApiClient(
        onGet: (_, _) {
          calls++;
          return completer.future;
        },
      );

      final first = AdSlotApi.fetchAds(AdSlot.banner, client: client);
      final second = AdSlotApi.fetchAds(AdSlot.banner, client: client);
      await Future<void>.delayed(Duration.zero);
      expect(calls, 1);
      completer.complete(<dynamic>[]);
      await Future.wait(<Future<List<CommunityAd>>>[first, second]);
      expect(calls, 1);
    });

    test('接口失败静默返回空列表，不抛异常', () async {
      FeatureFlags.applyForTest(<String, bool>{
        FeatureFlags.keyCommunityAds: true,
      });
      final client = _FakeApiClient(
        onGet: (_, _) async =>
            throw const ApiException(code: 'HTTP_500', message: 'boom'),
      );

      final ads = await AdSlotApi.fetchAds(AdSlot.splash, client: client);
      expect(ads, isEmpty);
    });
  });

  group('社区信息流广告过滤', () {
    test('开关关闭时移除 AD 条目，自然内容顺序不变', () {
      final items = <CommunityFeedItem>[
        const CommunityFeedItem(itemType: 'POST'),
        CommunityFeedItem.ofAd(_ad()),
        const CommunityFeedItem(itemType: 'POST'),
      ];

      final filtered = AdSlotApi.filterFeedItems(items, adsEnabled: false);

      expect(filtered, hasLength(2));
      expect(filtered.any((item) => item.isAd), isFalse);
    });

    test('开关开启时保留广告条目与原有位置', () {
      final items = <CommunityFeedItem>[
        const CommunityFeedItem(itemType: 'POST'),
        CommunityFeedItem.ofAd(_ad()),
        const CommunityFeedItem(itemType: 'POST'),
      ];

      final kept = AdSlotApi.filterFeedItems(items, adsEnabled: true);

      expect(kept, hasLength(3));
      expect(kept[1].isAd, isTrue);
    });
  });

  group('广告落地意图', () {
    test('无点击上报结果时回退广告自带配置', () {
      final ad = _ad(landingType: 'EXTERNAL_BROWSER');
      final intent = AdLandingIntent.fromClick(ad, null);

      expect(intent.clickId, ad.clickId);
      expect(intent.targetUrl, ad.targetUrl);
      expect(intent.landingType, 'EXTERNAL_BROWSER');
      expect(intent.isInAppLanding, isFalse);
    });

    test('点击上报结果优先于广告自带配置', () {
      final ad = _ad();
      const result = AdClickResult(
        clickId: 'server-click',
        adId: '1',
        targetUrl: 'https://server.example/landing',
        landingType: 'APP_PAGE',
        measurable: true,
      );

      final intent = AdLandingIntent.fromClick(ad, result);

      expect(intent.clickId, 'server-click');
      expect(intent.targetUrl, 'https://server.example/landing');
      expect(intent.isInAppLanding, isTrue);
    });
  });

  group('服务大厅功能开关消费', () {
    testWidgets('feature.homework=false 时隐藏作业中心入口', (tester) async {
      FeatureFlags.applyForTest(<String, bool>{
        FeatureFlags.keyHomework: false,
        FeatureFlags.keyCommunityAds: false,
      });

      await tester.pumpWidget(
        MaterialApp(
          home: ServiceHallPage(
            initialCategories: _categories,
            initialRequestCount: 0,
          ),
        ),
      );
      await tester.pump();

      expect(find.text('作业中心'), findsNothing);
      expect(find.text('高频快捷办事'), findsOneWidget);
      expect(find.text('宿舍报修'), findsWidgets);
    });

    testWidgets('feature.homework=true 时展示作业中心入口', (tester) async {
      FeatureFlags.applyForTest(<String, bool>{
        FeatureFlags.keyHomework: true,
        FeatureFlags.keyCommunityAds: false,
      });

      await tester.pumpWidget(
        MaterialApp(
          home: ServiceHallPage(
            initialCategories: _categories,
            initialRequestCount: 0,
          ),
        ),
      );
      await tester.pump();

      expect(find.text('作业中心'), findsOneWidget);
    });
  });

  group('SPLASH 开屏广告覆盖层', () {
    testWidgets('有数据时展示可跳过覆盖层，3 秒后自动关闭', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: SplashAdGate(
            debugInitialAd: _ad(title: '开屏推广'),
            child: const Scaffold(body: Text('首页内容')),
          ),
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(find.text('首页内容'), findsOneWidget);
      expect(find.text('跳过'), findsOneWidget);
      expect(find.text('开屏推广'), findsOneWidget);

      await tester.pump(const Duration(seconds: 3));

      expect(find.text('跳过'), findsNothing);
      expect(find.text('首页内容'), findsOneWidget);
    });

    testWidgets('点击跳过立即关闭覆盖层', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: SplashAdGate(
            debugInitialAd: _ad(),
            child: const Scaffold(body: Text('首页内容')),
          ),
        ),
      );
      await tester.pump();
      await tester.pump();

      await tester.tap(find.text('跳过'));
      await tester.pump();

      expect(find.text('跳过'), findsNothing);
      expect(find.text('首页内容'), findsOneWidget);
    });

    testWidgets('无广告数据时不展示覆盖层', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: SplashAdGate(child: Scaffold(body: Text('首页内容'))),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      expect(find.text('跳过'), findsNothing);
      expect(find.text('首页内容'), findsOneWidget);
    });
  });
}
