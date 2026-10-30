import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/app_version.dart';
import 'package:smartcampus_app/services/app_update_service.dart';

/// 版本检查接口的响应解析单测（契约 §3.1），不访问网络。
void main() {
  group('AppUpdateService.parseResponse', () {
    test('有更新：解析 latest 全部字段', () {
      final info = AppUpdateService.parseResponse({
        'hasUpdate': true,
        'forceUpdate': false,
        'currentVersionCode': 10,
        'latest': {
          'id': 1,
          'versionCode': 12,
          'versionName': '1.2.0',
          'minSupportedVersionCode': 9,
          'forceUpdate': false,
          'updateLog': '修复若干问题',
          'downloadUrl': '/api/public/app-version/1/package',
          'packageSize': 48210345,
          'packageSha256': 'a' * 64,
          'publishedAt': '2026-10-19T10:00:00',
        },
      });

      expect(info, isNotNull);
      expect(info!.hasUpdate, isTrue);
      expect(info.forceUpdate, isFalse);
      expect(info.currentVersionCode, 10);
      final latest = info.latest!;
      expect(latest.versionCode, 12);
      expect(latest.versionName, '1.2.0');
      expect(latest.minSupportedVersionCode, 9);
      expect(latest.forceUpdate, isFalse);
      expect(latest.updateLog, '修复若干问题');
      expect(latest.downloadUrl, '/api/public/app-version/1/package');
      expect(latest.packageSize, 48210345);
      expect(latest.packageSha256, 'a' * 64);
      expect(latest.publishedAt, '2026-10-19T10:00:00');
    });

    test('无更新：latest 为 null', () {
      final info = AppUpdateService.parseResponse({
        'hasUpdate': false,
        'forceUpdate': false,
        'currentVersionCode': 12,
        'latest': null,
      });

      expect(info, isNotNull);
      expect(info!.hasUpdate, isFalse);
      expect(info.forceUpdate, isFalse);
      expect(info.currentVersionCode, 12);
      expect(info.latest, isNull);
    });

    test('强更：服务端 forceUpdate 标记为 true', () {
      final info = AppUpdateService.parseResponse({
        'hasUpdate': true,
        'forceUpdate': true,
        'currentVersionCode': 11,
        'latest': {
          'versionCode': 12,
          'versionName': '1.2.0',
          'minSupportedVersionCode': 11,
          'forceUpdate': true,
        },
      });

      expect(info, isNotNull);
      expect(info!.hasUpdate, isTrue);
      expect(info.forceUpdate, isTrue);
    });

    test('强更：当前版本低于 minSupportedVersionCode 时客户端兜底推导', () {
      final info = AppUpdateService.parseResponse({
        // 服务端未标记强更，但当前版本已低于最低支持版本。
        'hasUpdate': true,
        'forceUpdate': false,
        'currentVersionCode': 8,
        'latest': {
          'versionCode': 12,
          'versionName': '1.2.0',
          'minSupportedVersionCode': 9,
          'forceUpdate': false,
        },
      });

      expect(info, isNotNull);
      expect(info!.hasUpdate, isTrue);
      expect(info.forceUpdate, isTrue);
    });

    test('字段缺失：安全回退且不抛异常', () {
      final info = AppUpdateService.parseResponse({
        'hasUpdate': true,
        'latest': {'versionCode': 13},
      });

      expect(info, isNotNull);
      expect(info!.hasUpdate, isTrue);
      expect(info.forceUpdate, isFalse);
      // 响应缺少 currentVersionCode 时回退为本地版本号。
      expect(info.currentVersionCode, appVersionCode);
      final latest = info.latest!;
      expect(latest.versionCode, 13);
      expect(latest.versionName, '');
      expect(latest.minSupportedVersionCode, isNull);
      expect(latest.updateLog, '');
      expect(latest.downloadUrl, '');
      expect(latest.packageSize, 0);
      expect(latest.packageSha256, isNull);
      expect(latest.publishedAt, isNull);
    });

    test('字段缺失：latest 非 Map 时忽略，hasUpdate 缺失则按版本号推导', () {
      final ignored = AppUpdateService.parseResponse({
        'hasUpdate': false,
        'latest': 'invalid',
      });
      expect(ignored, isNotNull);
      expect(ignored!.hasUpdate, isFalse);
      expect(ignored.latest, isNull);

      final derived = AppUpdateService.parseResponse({
        'latest': {'versionCode': 999, 'versionName': '9.9.9'},
      });
      expect(derived, isNotNull);
      expect(derived!.hasUpdate, isTrue);
      expect(derived.latest!.versionCode, 999);
    });

    test('响应异常：非 Map 或为 null 时返回 null', () {
      expect(AppUpdateService.parseResponse(null), isNull);
      expect(AppUpdateService.parseResponse('oops'), isNull);
      expect(AppUpdateService.parseResponse(<dynamic>[1, 2, 3]), isNull);
    });
  });

  group('AppUpdateService.resolveDownloadUrl', () {
    test('相对地址拼接 defaultBaseUrl', () {
      final resolved = AppUpdateService.resolveDownloadUrl(
        '/api/public/app-version/1/package',
      );
      expect(resolved, '${_normalizedBase()}/api/public/app-version/1/package');
    });

    test('无前导斜杠的相对地址也能拼接', () {
      final resolved = AppUpdateService.resolveDownloadUrl(
        'api/public/app-version/1/package',
      );
      expect(resolved, '${_normalizedBase()}/api/public/app-version/1/package');
    });

    test('绝对地址（App Store 外链）原样返回', () {
      const absolute = 'https://apps.apple.com/cn/app/id1234567890';
      expect(AppUpdateService.resolveDownloadUrl(absolute), absolute);
    });
  });

  group('AppUpdateService.formatPackageSize', () {
    test('按量级格式化安装包大小', () {
      expect(AppUpdateService.formatPackageSize(0), '未知大小');
      expect(AppUpdateService.formatPackageSize(512), '512 B');
      expect(AppUpdateService.formatPackageSize(2048), '2.0 KB');
      expect(AppUpdateService.formatPackageSize(48210345), '46.0 MB');
      expect(
        AppUpdateService.formatPackageSize(2 * 1024 * 1024 * 1024),
        '2.00 GB',
      );
    });
  });

  test('版本常量与当前构建配置一致', () {
    expect(appVersionCode, 6);
    expect(appVersionName, '1.1.6');
    expect(appChannel, 'OFFICIAL');
  });
}

String _normalizedBase() {
  final base = ApiClient.defaultBaseUrl;
  return base.endsWith('/') ? base.substring(0, base.length - 1) : base;
}
