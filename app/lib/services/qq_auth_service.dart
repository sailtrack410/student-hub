import 'dart:async';

import 'package:flutter/services.dart';
import 'package:tencent_qq/tencent_qq.dart';

/// QQ 授权结果：accessToken 交给本平台服务端反查 openId，openId 仅用于可选比对。
class QqLoginResult {
  final String accessToken;
  final String openId;

  const QqLoginResult({required this.accessToken, required this.openId});
}

class QqAuthException implements Exception {
  final String message;
  final bool cancelled;

  const QqAuthException(this.message, {this.cancelled = false});

  @override
  String toString() => message;
}

/// 封装 tencent_qq 的初始化、回调监听和登录流程。
///
/// 初始化必须在用户同意隐私政策之后：先 setIsPermissionGranted(true)，
/// 再 registerApp，最后才能发起 login。App Key 不参与客户端流程。
class QqAuthService {
  /// 必须与 pubspec.yaml 中 `tencent_qq.app_id` 保持一致。
  /// APP ID 会进入 APK，是 QQ SDK 的公开参数；App Key 只在服务端。
  static const String appId = String.fromEnvironment(
    'QQ_APP_ID',
    defaultValue: '1905799882',
  );

  static const Duration _loginTimeout = Duration(minutes: 3);

  final TencentQQPlatform _platform;
  final String _appId;

  StreamSubscription<TencentResp>? _subscription;
  Completer<QqLoginResult>? _pending;
  bool _permissionGranted = false;
  bool _registered = false;

  QqAuthService({TencentQQPlatform? platform, String? appId})
    : _platform = platform ?? TencentQQPlatform.instance,
      _appId = appId ?? QqAuthService.appId;

  /// 发起 QQ 登录。调用方必须先确认用户已同意隐私政策。
  Future<QqLoginResult> login({required bool privacyAccepted}) async {
    if (!privacyAccepted) {
      throw const QqAuthException('请先阅读并同意《用户协议》与《隐私政策》');
    }
    if (_pending != null) {
      throw const QqAuthException('QQ 授权正在进行中，请稍候');
    }

    try {
      await _prepare();
    } on MissingPluginException {
      throw const QqAuthException('QQ 登录组件未安装，请更新 App 后重试');
    } on PlatformException catch (error) {
      throw QqAuthException('QQ 登录初始化失败：${error.message ?? error.code}');
    } on QqAuthException {
      rethrow;
    } catch (error) {
      throw QqAuthException('QQ 登录初始化失败：$error');
    }

    final completer = Completer<QqLoginResult>();
    _pending = completer;

    try {
      // 未安装 QQ 时使用二维码/网页授权兜底，避免模拟器或无 QQ 设备直接失败。
      final installed = await _platform.isQQInstalled();
      await _platform.login(
        scope: const <String>[TencentScope.kGetSimpleUserInfo],
        qrcode: !installed,
      );
      return await completer.future.timeout(
        _loginTimeout,
        onTimeout: () {
          throw const QqAuthException('QQ 授权超时，请重试');
        },
      );
    } on MissingPluginException {
      throw const QqAuthException('QQ 登录组件未安装，请更新 App 后重试');
    } on PlatformException catch (error) {
      throw QqAuthException('QQ 授权失败：${error.message ?? error.code}');
    } finally {
      if (identical(_pending, completer)) {
        _pending = null;
      }
    }
  }

  Future<void> _prepare() async {
    _subscription ??= _platform.respStream().listen(_handleResponse);
    if (!_permissionGranted) {
      // 只有用户同意隐私政策后才允许 SDK 获取设备信息。
      await _platform.setIsPermissionGranted(granted: true);
      _permissionGranted = true;
    }
    if (!_registered) {
      await _platform.registerApp(appId: _appId);
      _registered = true;
    }
  }

  void _handleResponse(TencentResp response) {
    if (response is! TencentLoginResp) return;
    final completer = _pending;
    if (completer == null || completer.isCompleted) return;

    if (response.isCancelled) {
      completer.completeError(
        const QqAuthException('已取消 QQ 授权', cancelled: true),
      );
      return;
    }
    if (!response.isSuccessful) {
      completer.completeError(
        QqAuthException('QQ 授权失败：${response.msg ?? '未知错误'}'),
      );
      return;
    }

    final accessToken = response.accessToken?.trim() ?? '';
    final openId = response.openid?.trim() ?? '';
    if (accessToken.isEmpty || openId.isEmpty) {
      completer.completeError(const QqAuthException('QQ 授权返回的凭证不完整，请重试'));
      return;
    }
    completer.complete(QqLoginResult(accessToken: accessToken, openId: openId));
  }

  Future<void> dispose() async {
    _pending?.completeError(const QqAuthException('QQ 授权已取消', cancelled: true));
    _pending = null;
    await _subscription?.cancel();
    _subscription = null;
  }
}
