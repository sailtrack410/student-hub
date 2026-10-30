import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/services/qq_auth_service.dart';
import 'package:tencent_qq/tencent_qq.dart';

class _FakeQqPlatform extends TencentQQPlatform {
  final StreamController<TencentResp> _responses =
      StreamController<TencentResp>.broadcast();
  bool permissionGranted = false;
  String? registeredAppId;
  bool isInstalled = true;
  TencentResp? responseOnLogin;

  @override
  Stream<TencentResp> respStream() => _responses.stream;

  @override
  Future<void> setIsPermissionGranted({
    required bool granted,
    String? buildModel,
  }) async {
    permissionGranted = granted;
  }

  @override
  Future<void> registerApp({
    required String appId,
    String? universalLink,
  }) async {
    registeredAppId = appId;
  }

  @override
  Future<bool> isQQInstalled() async => isInstalled;

  @override
  Future<void> login({required List<String> scope, bool qrcode = false}) async {
    final response = responseOnLogin;
    if (response != null) {
      _responses.add(response);
    }
  }

  Future<void> close() => _responses.close();
}

void main() {
  test('QQ 登录：先隐私授权/注册，再映射 accessToken 和 openId', () async {
    final platform = _FakeQqPlatform();
    final service = QqAuthService(platform: platform, appId: 'test-app');
    platform.responseOnLogin = const TencentLoginResp(
      ret: TencentResp.kRetSuccess,
      openid: 'qq-openid',
      accessToken: 'qq-access-token',
      expiresIn: 3600,
      createAt: 0,
    );

    final result = await service.login(privacyAccepted: true);

    expect(result.accessToken, 'qq-access-token');
    expect(result.openId, 'qq-openid');
    expect(platform.permissionGranted, isTrue);
    expect(platform.registeredAppId, 'test-app');

    await service.dispose();
    await platform.close();
  });

  test('QQ 登录：用户取消返回可识别的取消异常', () async {
    final platform = _FakeQqPlatform();
    final service = QqAuthService(platform: platform, appId: 'test-app');
    platform.responseOnLogin = const TencentLoginResp(
      ret: TencentResp.kRetUserCancel,
    );

    await expectLater(
      service.login(privacyAccepted: true),
      throwsA(
        isA<QqAuthException>().having((e) => e.cancelled, 'cancelled', isTrue),
      ),
    );

    await service.dispose();
    await platform.close();
  });

  test('QQ 登录：未同意隐私政策时不初始化 SDK', () async {
    final platform = _FakeQqPlatform();
    final service = QqAuthService(platform: platform, appId: 'test-app');

    await expectLater(
      service.login(privacyAccepted: false),
      throwsA(isA<QqAuthException>()),
    );
    expect(platform.permissionGranted, isFalse);
    expect(platform.registeredAppId, isNull);

    await service.dispose();
    await platform.close();
  });
}
