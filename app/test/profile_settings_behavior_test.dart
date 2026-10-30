import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/auth_service.dart';
import 'package:smartcampus_app/models/user_session.dart';
import 'package:smartcampus_app/pages/profile/account_center_page.dart';
import 'package:smartcampus_app/pages/profile/profile_page.dart';
import 'package:smartcampus_app/pages/profile/settings_security_page.dart';
import 'package:smartcampus_app/widgets/badge_icon.dart';

void main() {
  late Map<String, dynamic> preferences;
  late Map<String, dynamic> privacy;
  late Map<String, dynamic> achievement;
  late Map<String, dynamic> author;
  late List<Map<String, dynamic>> writes;
  Completer<http.Response>? preferenceSave;
  Completer<http.Response>? privacySave;

  http.Response success(Object? data) => http.Response(
    jsonEncode({'success': true, 'data': data}),
    200,
    headers: {'content-type': 'application/json; charset=utf-8'},
  );

  // 静态 API 客户端复用同一模拟服务，全部用例均不访问网络。
  final server = MockClient((request) async {
    final path = request.url.path;
    if (request.method == 'PUT') {
      writes.add(jsonDecode(request.body) as Map<String, dynamic>);
      if (path.endsWith('/preferences') && preferenceSave != null) {
        return preferenceSave!.future;
      }
      if (path.endsWith('/privacy') && privacySave != null) {
        return privacySave!.future;
      }
      return success(jsonDecode(request.body));
    }
    if (path.endsWith('/auth/me')) {
      return success({
        'user': {'userId': 'student-1', 'displayName': '测试同学'},
      });
    }
    if (path.endsWith('/preferences')) return success(preferences);
    if (path.endsWith('/privacy')) return success(privacy);
    if (path.endsWith('/credentials')) {
      return success({'username': 'student01', 'passwordSet': true});
    }
    if (path.endsWith('/achievements/profile')) return success(achievement);
    if (path.contains('/community/authors/')) return success(author);
    return success(<String, dynamic>{});
  });

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    preferences = {
      'chatPush': true,
      'chatReminder': false,
      'repairPush': true,
      'workOrderPush': true,
    };
    privacy = {'showAcademicInfo': true, 'showMedals': true};
    achievement = {'badgeCount': 0, 'recentBadges': []};
    author = {};
    writes = [];
    preferenceSave = null;
    privacySave = null;
  });

  Future<void> showSettings(WidgetTester tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: SettingsSecurityPage(authService: AuthService(ApiClient())),
      ),
    );
    await tester.pumpAndSettle();
  }

  Finder switchFor(String title) => find.descendant(
    of: find.ancestor(of: find.text(title), matching: find.byType(Row)).first,
    matching: find.byType(Switch),
  );

  testWidgets('消息保存中防止重复提交，失败后恢复真实开关状态', (tester) async {
    await http.runWithClient(() async {
      preferenceSave = Completer<http.Response>();
      preferences = {}; // 缺失配置沿用默认开启，失败也必须恢复默认值。
      await showSettings(tester);
      await tester.ensureVisible(find.text('私聊消息推送'));
      final toggle = switchFor('私聊消息推送');
      expect(tester.widget<Switch>(toggle).value, isTrue);
      await tester.tap(toggle);
      await tester.pump();
      expect(tester.widget<Switch>(toggle).value, isFalse);
      expect(tester.widget<Switch>(toggle).onChanged, isNull);
      expect(writes, [
        {'chatPush': false},
      ]);

      preferenceSave!.complete(http.Response('{"success":false}', 500));
      await tester.pumpAndSettle();
      expect(tester.widget<Switch>(toggle).value, isTrue);
      expect(tester.widget<Switch>(toggle).onChanged, isNotNull);
      expect(find.text('保存失败，已恢复原设置'), findsOneWidget);
    }, () => server);
  });

  testWidgets('隐私勋章开关失败时恢复，两个字段一同提交', (tester) async {
    await http.runWithClient(() async {
      privacySave = Completer<http.Response>();
      await showSettings(tester);
      await tester.scrollUntilVisible(find.text('对他人展示荣誉勋章'), 250);
      final medals = switchFor('对他人展示荣誉勋章');
      await tester.tap(medals);
      await tester.pump();
      expect(tester.widget<Switch>(medals).value, isFalse);
      expect(tester.widget<Switch>(switchFor('社区发帖显示院系标签')).onChanged, isNull);
      expect(writes, [
        {'showAcademicInfo': true, 'showMedals': false},
      ]);
      privacySave!.complete(http.Response('{"success":false}', 500));
      await tester.pumpAndSettle();
      expect(tester.widget<Switch>(medals).value, isTrue);
    }, () => server);
  });

  testWidgets('设置仅保留应用配置，账号密码与设备统一归账号中心', (tester) async {
    await http.runWithClient(() async {
      await showSettings(tester);
      expect(find.text('通用设置'), findsOneWidget);
      expect(find.text('账号与登录密码'), findsNothing);
      expect(find.text('我的登录设备'), findsNothing);
      expect(find.text('退出当前账号'), findsNothing);
      await tester.pumpWidget(
        MaterialApp(
          home: AccountCenterPage(authService: AuthService(ApiClient())),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('账号中心'), findsOneWidget);
      await tester.scrollUntilVisible(find.text('登录设备管理'), 250);
      expect(find.text('登录设备管理'), findsOneWidget);
      expect(find.text('设置与账号安全'), findsNothing);
      await tester.tap(find.text('登录密码'));
      await tester.pumpAndSettle();
      expect(find.text('账号与登录密码'), findsOneWidget);
      expect(find.text('当前密码'), findsOneWidget);
      expect(find.text('新密码'), findsOneWidget);
      expect(find.text('验证并保存'), findsOneWidget);
    }, () => server);
  });

  Future<void> showProfile(WidgetTester tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: ProfilePage(
          session: const UserSession(
            token: 'test-token',
            userId: 'student-1',
            username: 'student01',
            displayName: '测试同学',
            identityType: 'STUDENT',
            authorizationStatus: 'BASIC',
            isNewUser: false,
          ),
          authService: AuthService(ApiClient()),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('空资料显示待完善，零勋章不在入口虚构收藏', (tester) async {
    await http.runWithClient(() async {
      await showProfile(tester);
      expect(find.text('学籍信息待完善'), findsOneWidget);
      expect(find.textContaining('软件工程'), findsNothing);
      expect(find.text('成长等级与荣誉勋章  ·  0/19'), findsOneWidget);
      expect(find.text('破冰首帖'), findsNothing);
      expect(find.byType(BadgeIcon), findsNothing);
      expect(find.text('第 8 周'), findsNothing);
    }, () => server);
  });

  testWidgets('真实学籍与最近获得徽章保留，点击横幅进入二级典藏室', (tester) async {
    await http.runWithClient(() async {
      author = {'college': '真实学院', 'major': '真实专业'};
      achievement = {
        'badgeCount': 1,
        'totalBadgeCount': 19,
        'recentBadges': [
          {'badgeCode': 'COMMUNITY_FIRST_POST', 'badgeName': '破冰首帖'},
        ],
      };
      await showProfile(tester);
      expect(find.text('真实学院 · 真实专业'), findsOneWidget);
      expect(find.text('成长等级与荣誉勋章  ·  1/19'), findsOneWidget);
      expect(find.byType(BadgeIcon), findsOneWidget);
      await tester.tap(find.text('荣誉典藏室'));
      await tester.pumpAndSettle();
      expect(find.text('荣誉勋章墙'), findsOneWidget);
    }, () => server);
  });
}
