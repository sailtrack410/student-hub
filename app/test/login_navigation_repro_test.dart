import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/auth_service.dart';
import 'package:smartcampus_app/api/feature_flags.dart';
import 'package:smartcampus_app/pages/home/home_page.dart';
import 'package:smartcampus_app/pages/login/login_page.dart';
import 'package:smartcampus_app/theme/app_theme.dart';

void main() {
  tearDown(() async {
    await ApiClient().clearToken();
  });

  testWidgets('password login transition does not use deactivated ancestors', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    FeatureFlags.resetForTest();
    tester.view.physicalSize = const Size(411, 914);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final client = ApiClient(
      baseUrl: 'https://campus.test',
      client: MockClient((request) async {
        if (request.url.path.endsWith('/auth/login')) {
          return http.Response(
            headers: {'content-type': 'application/json; charset=utf-8'},
            jsonEncode({
              'success': true,
              'data': {
                'accessToken': 'test-token',
                'userId': 'student',
                'username': 'student01',
                'displayName': '陈同学',
              },
            }),
            200,
          );
        }
        return http.Response('', 500);
      }),
    );
    await client.init();
    await tester.runAsync(() => client.setToken('stale-test-token'));
    final auth = AuthService(client);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.lightTheme,
        home: LoginPage(authService: auth),
      ),
    );
    await tester.tap(find.byType(Checkbox));
    await tester.pump();
    await tester.longPress(find.bySemanticsLabel('绵阳城市学院校徽'));
    await tester.pumpAndSettle();
    await tester.runAsync(() async {
      await tester.tap(find.text('立即登录'));
      await Future<void>.delayed(const Duration(milliseconds: 200));
    });
    await tester.pumpAndSettle();
    expect(find.byType(HomePage), findsOneWidget);
    expect(tester.takeException(), isNull);
    FeatureFlags.resetForTest();
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
