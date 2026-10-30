import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/api/community_api.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/pages/community/ad_landing_page.dart';
import 'package:smartcampus_app/widgets/community_ad_card.dart';

void main() {
  group('CommunityFeedPage 混合信息流解析', () {
    test('解析 8 条自然帖子 + 1 条广告，广告字段与顺序正确', () {
      final page = CommunityApi.parseFeedPage(_feedFixture());

      expect(page.total, 26, reason: 'total 是自然内容总数（字符串形式）');
      expect(page.postCount, 8);
      expect(page.items, hasLength(9));

      final adItem = page.items[8];
      expect(adItem.itemType, 'AD');
      expect(adItem.isAd, isTrue);
      expect(adItem.post, isNull);

      final ad = adItem.ad!;
      expect(ad.adId, '2112174270222426113');
      expect(ad.clickId, 'c725cba7f3e394173bf216e537294aa64');
      expect(ad.title, '校园文创市集 · 秋季场');
      expect(ad.brandName, '校园文创中心');
      expect(ad.targetUrl, 'https://www.studenthub.example/campus-market');
      expect(ad.landingType, 'IN_APP_WEBVIEW');
      expect(ad.priority, 10, reason: '后端 Long/Integer 以字符串下发时也要能解析');
      expect(ad.measurable, isTrue);
      expect(ad.sponsoredLabel, '推广');
      expect(ad.ctaText, '查看市集');
      expect(ad.displayCta, '查看市集');
      expect(ad.isWebViewLanding, isTrue);
      expect(ad.isTrackableLanding, isTrue);

      expect(page.items.first.isPost, isTrue);
      expect(page.items.first.post!.title, '测试帖子 1');
    });

    test('广告缺字段/未知类型时安全跳过，不抛异常', () {
      final page = CommunityApi.parseFeedPage({
        'total': '3',
        'records': [
          {'itemType': 'AD', 'post': null, 'ad': null},
          {'itemType': 'POST', 'post': null, 'ad': null},
          {'itemType': 'POST', 'post': _postJson(1), 'ad': null},
        ],
      });
      expect(page.items, hasLength(1));
      expect(page.total, 3);
    });

    test('广告缺省 sponsoredLabel 时回退为「推广」，measurable 缺失时按落地类型推断', () {
      final ad = CommunityAd.fromJson({
        'adId': 'ad-1',
        'clickId': 'c-1',
        'title': '标题',
        'brandName': '品牌',
        'targetUrl': 'https://example.com',
        'landingType': 'APP_PAGE',
      });
      expect(ad.sponsoredLabel, '推广');
      expect(ad.measurable, isTrue);
      expect(ad.displayCta, '了解详情');
      expect(ad.isTrackableLanding, isTrue);

      final relativeCover = CommunityAd.fromJson({
        'adId': 'ad-3',
        'clickId': 'c-3',
        'title': '相对封面',
        'brandName': '品牌',
        'coverUrl': '/files/ad-cover.png',
        'targetUrl': 'https://example.com',
        'landingType': 'IN_APP_WEBVIEW',
      });
      expect(relativeCover.coverUrl, startsWith('http'));

      final external = CommunityAd.fromJson({
        'adId': 'ad-2',
        'clickId': 'c-2',
        'title': '外部广告',
        'brandName': '品牌',
        'targetUrl': 'https://example.com',
        'landingType': 'EXTERNAL_BROWSER',
      });
      expect(external.measurable, isFalse);
      expect(external.isExternalBrowser, isTrue);
      expect(external.isTrackableLanding, isFalse);
    });
  });

  group('广告跳转类型与安全 URL', () {
    test('IN_APP_WEBVIEW / APP_PAGE 走站内 WebView 可测链路', () {
      for (final type in ['IN_APP_WEBVIEW', 'APP_PAGE']) {
        final ad = _adWithLanding(type);
        expect(ad.isWebViewLanding, isTrue, reason: type);
        expect(ad.isTrackableLanding, isTrue, reason: type);
      }
    });

    test('EXTERNAL_BROWSER / DEEPLINK 只记录点击，不可测落地', () {
      final external = _adWithLanding('EXTERNAL_BROWSER');
      expect(external.isExternalBrowser, isTrue);
      expect(external.isTrackableLanding, isFalse);
      expect(external.measurable, isFalse);

      final deeplink = _adWithLanding('DEEPLINK');
      expect(deeplink.isDeeplink, isTrue);
      expect(deeplink.isTrackableLanding, isFalse);
    });

    test('WebView 仅接受 http/https 绝对地址', () {
      expect(AdLandingPage.isSafeHttpUrl('https://example.com/a?b=1'), isTrue);
      expect(AdLandingPage.isSafeHttpUrl('http://127.0.0.1:9000/page'), isTrue);
      expect(AdLandingPage.isSafeHttpUrl('javascript:alert(1)'), isFalse);
      expect(AdLandingPage.isSafeHttpUrl('file:///etc/passwd'), isFalse);
      expect(AdLandingPage.isSafeHttpUrl('intent://scan'), isFalse);
      expect(AdLandingPage.isSafeHttpUrl(''), isFalse);
      expect(AdLandingPage.isSafeHttpUrl('www.example.com'), isFalse);
    });

    test('AdClickResult 解析点击上报结果', () {
      final result = AdClickResult.fromJson({
        'clickId': 'c-1',
        'adId': 'ad-1',
        'targetUrl': 'https://example.com',
        'landingType': 'IN_APP_WEBVIEW',
        'measurable': true,
      });
      expect(result.clickId, 'c-1');
      expect(result.adId, 'ad-1');
      expect(result.targetUrl, 'https://example.com');
      expect(result.landingType, 'IN_APP_WEBVIEW');
      expect(result.measurable, isTrue);
    });
  });

  group('AdLandingPage 安全兜底', () {
    testWidgets('非 http/https 链接不创建 WebView，展示安全兜底', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: AdLandingPage(clickId: 'c-1', targetUrl: 'javascript:alert(1)'),
        ),
      );
      await tester.pump();
      expect(find.text('该广告链接暂不支持 App 内打开'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('无内嵌 WebView 实现的环境安全降级，不崩溃', (WidgetTester tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: AdLandingPage(
            clickId: 'c-1',
            targetUrl: 'https://example.com/landing',
            title: '广告详情',
          ),
        ),
      );
      await tester.pump();
      expect(find.text('该广告链接暂不支持 App 内打开'), findsOneWidget);
      expect(find.text('广告详情'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });

  group('CommunityAdCard 广告卡片', () {
    testWidgets('展示推广标识、品牌、标题、描述与 CTA，并区分普通帖子', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(375, 812);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      var tapped = 0;
      final ad = CommunityAd.fromJson({
        'adId': 'ad-1',
        'clickId': 'c-1',
        'title': '校园文创市集 · 秋季场',
        'brandName': '校园文创中心',
        'description': '本周六在图书馆前广场，学生原创手作与校园周边限时展售。',
        'coverUrl': null,
        'targetUrl': 'https://example.com/market',
        'landingType': 'IN_APP_WEBVIEW',
        'priority': '10',
        'measurable': true,
        'sponsoredLabel': '推广',
        'ctaText': '查看市集',
      });

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: CommunityAdCard(ad: ad, onTap: () => tapped++),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('校园文创中心'), findsOneWidget);
      expect(find.text('校园文创市集 · 秋季场'), findsOneWidget);
      expect(find.textContaining('学生原创手作'), findsOneWidget);
      expect(find.text('查看市集'), findsOneWidget);
      expect(find.text('广告'), findsOneWidget);
      expect(find.text('推广'), findsWidgets);

      await tester.tap(find.text('查看市集'));
      await tester.pumpAndSettle();
      expect(tapped, 1);
    });
  });
}

CommunityAd _adWithLanding(String landingType) {
  return CommunityAd.fromJson({
    'adId': 'ad-$landingType',
    'clickId': 'c-$landingType',
    'title': '广告标题',
    'brandName': '广告品牌',
    'targetUrl': 'https://example.com',
    'landingType': landingType,
    'measurable': landingType == 'IN_APP_WEBVIEW' || landingType == 'APP_PAGE',
    'sponsoredLabel': '推广',
  });
}

Map<String, dynamic> _feedFixture() {
  return {
    'pageNum': '1',
    'pageSize': '10',
    'total': '26',
    'records': [
      for (var i = 1; i <= 8; i++)
        {'itemType': 'POST', 'post': _postJson(i), 'ad': null},
      {
        'itemType': 'AD',
        'post': null,
        'ad': {
          'adId': '2112174270222426113',
          'clickId': 'c725cba7f3e394173bf216e537294aa64',
          'title': '校园文创市集 · 秋季场',
          'brandName': '校园文创中心',
          'description': '本周六在图书馆前广场，学生原创手作与校园周边限时展售。',
          'coverUrl': null,
          'targetUrl': 'https://www.studenthub.example/campus-market',
          'landingType': 'IN_APP_WEBVIEW',
          'sectionId': null,
          'priority': '10',
          'measurable': true,
          'sponsoredLabel': '推广',
          'ctaText': '查看市集',
        },
      },
    ],
  };
}

Map<String, dynamic> _postJson(int index) {
  return {
    'postId': '21120000000000000$index',
    'sectionId': '6001',
    'sectionName': '校园日常',
    'sectionIcon': null,
    'authorId': '1002',
    'authorName': '测试同学$index',
    'avatarUrl': null,
    'authorLevel': 4,
    'authorBadgeCode': null,
    'authorOfficial': false,
    'authorOfficialTitle': null,
    'postType': 'DYNAMIC',
    'status': 'ENABLED',
    'hiddenReason': null,
    'title': '测试帖子 $index',
    'content': '测试内容 $index',
    'imageUrls': const <String>[],
    'thumbnailUrls': const <String>[],
    'contentBlocks': const <Map<String, dynamic>>[],
    'topics': const ['校园生活'],
    'commentCount': index,
    'likeCount': index * 2,
    'liked': false,
    'createdAt': '2026-10-19T10:00:00',
    'locationName': null,
    'locationAddress': null,
    'latitude': null,
    'longitude': null,
    'poiId': null,
  };
}
