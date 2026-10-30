import 'dart:async';
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
import 'package:smartcampus_app/services/community_socket.dart';
import 'package:smartcampus_app/theme/app_theme.dart';
import 'package:tencent_qq/tencent_qq.dart';

class FakeQqPlatform extends TencentQQPlatform {
  final responses = StreamController<TencentResp>.broadcast(sync: true);
  final loginCalled = Completer<void>();
  @override
  Stream<TencentResp> respStream() => responses.stream;
  @override
  Future<void> setIsPermissionGranted({
    required bool granted,
    String? buildModel,
  }) async {}
  @override
  Future<void> registerApp({
    required String appId,
    String? universalLink,
  }) async {}
  @override
  Future<bool> isQQInstalled() async => false;
  @override
  Future<void> login({required List<String> scope, bool qrcode = false}) async {
    responses.add(
      const TencentLoginResp(
        ret: TencentResp.kRetSuccess,
        openid: 'preview-openid',
        accessToken: 'preview-access-token',
        expiresIn: 3600,
        createAt: 0,
      ),
    );
  }
}

void main() {
  tearDown(() async {
    await ApiClient().clearToken();
  });

  testWidgets('QQ login return transition keeps widget ancestors valid', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    FeatureFlags.resetForTest();
    tester.view.physicalSize = const Size(411, 914);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    TencentQQPlatform.instance = FakeQqPlatform();
    final platform = TencentQQPlatform.instance as FakeQqPlatform;
    final client = ApiClient(
      baseUrl: 'https://campus.test',
      client: MockClient((request) async {
        if (request.url.path.endsWith('/auth/qq-login')) {
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
    final auth = AuthService(client);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.lightTheme,
        home: LoginPage(authService: auth),
      ),
    );
    await tester.tap(find.byType(Checkbox));
    await tester.pump();
    await tester.tap(find.text('QQ 一键登录'));
    await tester.pumpAndSettle();
    expect(find.byType(HomePage), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.runAsync(() async {
      await Future.wait([
        platform.responses.close(),
        CommunitySocket.shared.disconnect(),
        ApiClient().clearToken(),
      ]);
    });
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump();
  });

  testWidgets(
    'QQ authorization rejection stays on login and keeps its message',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      TencentQQPlatform.instance = FakeQqPlatform();
      final client = ApiClient(
        client: MockClient(
          (_) async => http.Response(
            headers: {'content-type': 'application/json; charset=utf-8'},
            jsonEncode({
              'success': false,
              'code': 'AUTH_401_03',
              'message': 'QQ 登录失败，请稍后重试',
            }),
            401,
          ),
        ),
      );
      await client.init();
      final auth = AuthService(client);
      await tester.pumpWidget(MaterialApp(home: LoginPage(authService: auth)));
      await tester.tap(find.byType(Checkbox));
      await tester.pump();
      await tester.tap(find.text('QQ 一键登录'));
      await tester.pumpAndSettle();

      expect(find.byType(LoginPage), findsOneWidget);
      expect(find.text('QQ 登录失败，请稍后重试'), findsOneWidget);
      expect(find.text('登录状态已失效，请重新登录'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.runAsync(() async {
        final platform = TencentQQPlatform.instance as FakeQqPlatform;
        await platform.responses.close();
        await CommunitySocket.shared.disconnect();
      });
    },
  );
}
