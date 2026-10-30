import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/services/badge_catalog_sync.dart';
import 'package:smartcampus_app/services/badge_icon_cache.dart';

/// 徽章图标热更新（方案 B）客户端单元测试：
/// - catalog JSON -> badgeCode→iconUrl 内存映射的解析；
/// - 磁盘缓存文件名（FNV-1a）稳定且不同 URL 不冲突；
/// - resolve 对非绝对地址直接返回 null（不触发网络/插件）。
///
/// 全部用例不联网；磁盘清理使用临时目录和模拟平台通道。
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const catalogKey = BadgeCatalogSync.catalogJsonKey;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    BadgeCatalogSync.resetIconUrlsForTest();
    BadgeIconCache.resetForTest();
  });

  group('BadgeCatalogSync.iconUrlFor', () {
    test('解析 catalog 中的 iconUrl，缺失/空值/未知码返回 null', () async {
      SharedPreferences.setMockInitialValues({
        catalogKey: jsonEncode({
          'catalogVersion': '8f3c1d92a7b04e65',
          'changed': true,
          'badges': [
            {
              'badgeCode': 'COMMUNITY_FIRST_POST',
              'badgeName': '破冰首帖',
              'iconUrl': '/api/common/assets/badges/COMMUNITY_FIRST_POST.png?v=8f3c1d92a7b04e65',
            },
            {'badgeCode': 'COMMUNITY_POST_10', 'badgeName': '社区常客'},
            {'badgeCode': 'COMMUNITY_POST_50', 'iconUrl': '   '},
            {'badgeCode': '', 'iconUrl': '/api/common/assets/badges/x.png'},
          ],
        }),
      });

      // 装载前为空
      expect(BadgeCatalogSync.iconUrlFor('COMMUNITY_FIRST_POST'), isNull);

      await BadgeCatalogSync.ensureIconUrlsLoaded();

      expect(
        BadgeCatalogSync.iconUrlFor('COMMUNITY_FIRST_POST'),
        '/api/common/assets/badges/COMMUNITY_FIRST_POST.png?v=8f3c1d92a7b04e65',
      );
      // 服务端未下发 iconUrl 的内置码：交给本地素材兜底
      expect(BadgeCatalogSync.iconUrlFor('COMMUNITY_POST_10'), isNull);
      expect(BadgeCatalogSync.iconUrlFor('COMMUNITY_POST_50'), isNull);
      expect(BadgeCatalogSync.iconUrlFor('NOT_EXIST'), isNull);
    });

    test('绝对地址原样保留，由组件决定是否拼基地址', () async {
      const absolute = 'https://cdn.example.com/badges/LEVEL_3.png?v=9';
      SharedPreferences.setMockInitialValues({
        catalogKey: jsonEncode({
          'catalogVersion': '9',
          'changed': true,
          'badges': [
            {'badgeCode': 'LEVEL_3', 'iconUrl': absolute},
          ],
        }),
      });

      await BadgeCatalogSync.ensureIconUrlsLoaded();
      expect(BadgeCatalogSync.iconUrlFor('LEVEL_3'), absolute);
    });

    test('changed=false（无 badges 列表）时保留已有映射', () async {
      SharedPreferences.setMockInitialValues({
        catalogKey: jsonEncode({
          'catalogVersion': 'v1',
          'changed': true,
          'badges': [
            {
              'badgeCode': 'SERVICE_FIRST_REQUEST',
              'iconUrl':
                  '/api/common/assets/badges/SERVICE_FIRST_REQUEST.webp?v=v1',
            },
          ],
        }),
      });
      await BadgeCatalogSync.ensureIconUrlsLoaded();
      expect(BadgeCatalogSync.iconUrlFor('SERVICE_FIRST_REQUEST'), isNotNull);

      // 服务端返回 changed=false 时只有版本号，不应清空内存映射
      SharedPreferences.setMockInitialValues({
        catalogKey: jsonEncode({'catalogVersion': 'v2', 'changed': false}),
      });
      await BadgeCatalogSync.cachedCatalog();

      expect(
        BadgeCatalogSync.iconUrlFor('SERVICE_FIRST_REQUEST'),
        '/api/common/assets/badges/SERVICE_FIRST_REQUEST.webp?v=v1',
      );
    });

    test('无本地缓存时安全返回 null', () async {
      await BadgeCatalogSync.ensureIconUrlsLoaded();
      expect(BadgeCatalogSync.iconUrlFor('COMMUNITY_FIRST_POST'), isNull);
    });
  });

  group('BadgeIconCache 缓存键', () {
    test('同一 URL 的键稳定，文件名形如 <8位hex>.img', () {
      const url =
          '/api/common/assets/badges/COMMUNITY_FIRST_POST.png?v=8f3c1d92a7b04e65';
      final first = BadgeIconCache.cacheKeyFor(url);
      final second = BadgeIconCache.cacheKeyFor(url);
      expect(first, second);
      expect(first, matches(RegExp(r'^[0-9a-f]{8}$')));
      expect(BadgeIconCache.cacheFileNameFor(url), '$first.img');
    });

    test('管理端替换图标（?v 变化）会生成不同缓存键', () {
      const base = '/api/common/assets/badges/COMMUNITY_FIRST_POST.png';
      final v1 = BadgeIconCache.cacheKeyFor('$base?v=1');
      final v2 = BadgeIconCache.cacheKeyFor('$base?v=2');
      expect(v1, isNot(v2));
      expect(
        BadgeIconCache.cacheFileNameFor('$base?v=1'),
        isNot(BadgeIconCache.cacheFileNameFor('$base?v=2')),
      );
    });

    test('不同勋章/不同域的 URL 缓存键互不冲突', () {
      const urls = [
        'http://127.0.0.1:9000/api/common/assets/badges/COMMUNITY_FIRST_POST.png',
        'http://127.0.0.1:9000/api/common/assets/badges/COMMUNITY_POST_10.png',
        'https://cdn.example.com/COMMUNITY_FIRST_POST.png',
        'https://cdn.example.com/community_first_post.png',
      ];
      final keys = urls.map(BadgeIconCache.cacheKeyFor).toSet();
      expect(keys, hasLength(urls.length));
    });
  });

  group('BadgeIconCache.resolve 输入校验', () {
    test('空串、相对地址、非 http(s) 协议直接返回 null 且不访问网络', () async {
      expect(await BadgeIconCache.resolve(''), isNull);
      expect(
        await BadgeIconCache.resolve(
          'assets/images/badges/COMMUNITY_FIRST_POST.png',
        ),
        isNull,
      );
      expect(
        await BadgeIconCache.resolve('/api/common/assets/badges/x.png'),
        isNull,
      );
      expect(await BadgeIconCache.resolve('ftp://example.com/x.png'), isNull);
    });
  });

  group('BadgeIconCache 磁盘清理', () {
    const channel = MethodChannel('plugins.flutter.io/path_provider');
    late Directory support;

    setUp(() async {
      support = await Directory.systemTemp.createTemp('badge-cache-test-');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async => support.path);
    });

    tearDown(() async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
      if (await support.exists()) await support.delete(recursive: true);
    });

    test('统计真实字节，仅清理图片目录并保留登录、协议和更新文件', () async {
      SharedPreferences.setMockInitialValues({
        'auth_token_key': 'keep-session',
        'legal_consent_version': 'keep-consent',
      });
      final cache = Directory(
        '${support.path}/${BadgeIconCache.directoryName}',
      );
      await Directory('${cache.path}/nested').create(recursive: true);
      await File('${cache.path}/one.img').writeAsBytes([1, 2, 3]);
      await File('${cache.path}/nested/two.img').writeAsBytes([4, 5]);
      final update = await File('${support.path}/update.apk').writeAsBytes([9]);

      expect(await BadgeIconCache.diskUsageBytes(), 5);
      expect(await BadgeIconCache.clear(), 5);
      expect(await BadgeIconCache.diskUsageBytes(), 0);
      expect(await cache.exists(), isFalse);
      expect(await update.readAsBytes(), [9]);
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('auth_token_key'), 'keep-session');
      expect(prefs.getString('legal_consent_version'), 'keep-consent');
    });

    test('清理后解析同一地址重新下载，不复用失效的内存文件', () async {
      const url = 'https://example.test/badge.png';
      final cache = Directory(
        '${support.path}/${BadgeIconCache.directoryName}',
      );
      await cache.create();
      await File('${cache.path}/${BadgeIconCache.cacheFileNameFor(url)}')
          .writeAsBytes([1, 2]);
      expect(await BadgeIconCache.resolve(url), isNotNull);
      await BadgeIconCache.clear();

      var downloads = 0;
      final restored = await http.runWithClient(
        () => BadgeIconCache.resolve(url),
        () => MockClient((_) async {
          downloads++;
          return http.Response.bytes([3, 4, 5], 200);
        }),
      );
      expect(downloads, 1);
      expect(await restored!.readAsBytes(), [3, 4, 5]);
      expect(await BadgeIconCache.diskUsageBytes(), 3);
    });

    test('清理等待下载落盘，结束后没有残留图片', () async {
      final requested = Completer<void>();
      final response = Completer<http.Response>();
      final download = http.runWithClient(
        () => BadgeIconCache.resolve('https://example.test/pending.png'),
        () => MockClient((_) {
          requested.complete();
          return response.future;
        }),
      );
      await requested.future;
      final clearing = BadgeIconCache.clear();
      response.complete(http.Response.bytes([1, 2, 3, 4], 200));
      expect(await clearing, 4);
      final file = await download;
      expect(file, isNotNull);
      expect(await file!.exists(), isFalse);
      expect(await BadgeIconCache.diskUsageBytes(), 0);
    });
  });
}
