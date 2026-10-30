import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

/// 服务端徽章图标的本地磁盘缓存（方案 B：iconUrl 优先，本地素材兜底）。
///
/// - 只接受绝对 `http(s)://` 地址，相对地址由调用方（`BadgeIcon`）拼接 `ApiClient` 基地址；
/// - 缓存文件位于 `getApplicationSupportDirectory()/badge_icons/<url 稳定哈希>.img`；
/// - 管理端替换图标后，catalog 下发的 `iconUrl` 会带新的 `?v=catalogVersion`，
///   哈希随之变化，天然产生新缓存文件，不会被旧图覆盖；
/// - 内存缓存 + 并发去重 + 失败短路（同一 URL 失败后本次进程不再重试）；
/// - 任何异常（插件未注册、网络失败、磁盘不可写）都返回 null，由组件回退本地素材。
class BadgeIconCache {
  BadgeIconCache._();

  /// 应用支持目录下的缓存子目录名。
  static const String directoryName = 'badge_icons';

  /// 缓存文件扩展名（内容可能是 png/jpg/webp/svg 之外的任意字节，统一用 .img）。
  static const String fileExtension = '.img';

  /// 单次下载超时，避免首屏长时间等待。
  static const Duration networkTimeout = Duration(seconds: 8);

  /// url -> 已落盘文件（只存成功项）。
  static final Map<String, File> _memory = {};

  /// 同一 url 的并发请求合并为一次下载。
  static final Map<String, Future<File?>> _inFlight = {};

  /// 本次进程内已失败的 url，避免反复重试拖慢列表。
  static final Set<String> _failed = {};

  /// 清理期间暂停新的解析，待正在落盘的请求完成后统一删除。
  static Future<int>? _clearing;

  /// 解析 [iconUrl] 对应的本地缓存文件；失败返回 null。
  static Future<File?> resolve(String iconUrl) {
    final clearing = _clearing;
    if (clearing != null) {
      return clearing.then((_) => resolve(iconUrl));
    }
    final url = iconUrl.trim();
    if (url.isEmpty || !_isAbsoluteHttpUrl(url)) {
      return Future<File?>.value();
    }
    final cached = _memory[url];
    if (cached != null) return Future<File?>.value(cached);
    if (_failed.contains(url)) return Future<File?>.value();

    final running = _inFlight[url];
    if (running != null) return running;

    final future = _resolveOnce(url).whenComplete(() {
      _inFlight.remove(url);
    });
    _inFlight[url] = future;
    return future;
  }

  /// 测试用：清空内存状态（不删除磁盘文件）。
  @visibleForTesting
  static void resetForTest() {
    _memory.clear();
    _inFlight.clear();
    _failed.clear();
    _clearing = null;
  }

  /// 图片磁盘缓存的实际大小；平台目录不可用时返回 null。
  static Future<int?> diskUsageBytes() async {
    final directory = await _cacheDirectory();
    if (directory == null) return null;
    return _directorySize(directory);
  }

  /// 只删除可重新下载的徽章图片，不触及账号、协议和升级文件。
  /// 返回实际移除的字节数；失败交给调用方显示，避免误报成功。
  static Future<int> clear() {
    final running = _clearing;
    if (running != null) return running;
    final future = _clearDiskCache();
    _clearing = future;
    return future.whenComplete(() => _clearing = null);
  }

  static Future<int> _clearDiskCache() async {
    await Future.wait(_inFlight.values.toList());
    final directory = await _cacheDirectory();
    if (directory == null) {
      throw StateError('图片缓存目录暂不可用');
    }
    final bytes = await _directorySize(directory);
    if (await directory.exists()) await directory.delete(recursive: true);
    _memory.clear();
    _failed.clear();
    return bytes;
  }

  static Future<int> _directorySize(Directory directory) async {
    if (!await directory.exists()) return 0;
    var bytes = 0;
    await for (final entity in directory.list(
      recursive: true,
      followLinks: false,
    )) {
      if (entity is File) bytes += await entity.length();
    }
    return bytes;
  }

  /// URL 的稳定短哈希（FNV-1a 32 位，输出 8 位小写十六进制）。
  ///
  /// 不用 `String.hashCode`：它不保证跨进程/跨平台稳定，不能作为磁盘文件名。
  static String cacheKeyFor(String url) {
    var hash = 0x811c9dc5; // FNV offset basis
    for (final byte in utf8.encode(url)) {
      hash ^= byte;
      // 32 位无符号乘法，避免在 Web(dart2js) 上出现大整数精度问题。
      hash = (hash * 0x01000193) & 0xffffffff;
    }
    return hash.toRadixString(16).padLeft(8, '0');
  }

  /// 缓存文件名，如 `3f2a9c1b.img`。
  static String cacheFileNameFor(String url) =>
      '${cacheKeyFor(url)}$fileExtension';

  static bool _isAbsoluteHttpUrl(String url) {
    final lower = url.toLowerCase();
    return lower.startsWith('http://') || lower.startsWith('https://');
  }

  static Future<File?> _resolveOnce(String url) async {
    try {
      final directory = await _cacheDirectory();
      if (directory == null) return null;

      final file = File(
        '${directory.path}${Platform.pathSeparator}${cacheKeyFor(url)}$fileExtension',
      );
      if (await file.exists()) {
        final length = await file.length();
        if (length > 0) {
          _memory[url] = file;
          return file;
        }
        // 空文件视为损坏缓存，删掉后重新下载。
        await file.delete();
      }

      final bytes = await _download(url);
      if (bytes == null || bytes.isEmpty) {
        _failed.add(url);
        return null;
      }

      await directory.create(recursive: true);
      final temp = File(
        '${file.path}.${DateTime.now().microsecondsSinceEpoch}.tmp',
      );
      await temp.writeAsBytes(bytes, flush: true);
      if (await file.exists()) {
        await file.delete();
      }
      final saved = await temp.rename(file.path);
      _memory[url] = saved;
      return saved;
    } catch (_) {
      _failed.add(url);
      return null;
    }
  }

  static Future<Directory?> _cacheDirectory() async {
    try {
      final base = await getApplicationSupportDirectory();
      return Directory('${base.path}${Platform.pathSeparator}$directoryName');
    } catch (_) {
      // 测试环境/平台未注册 path_provider 插件：按无缓存处理。
      return null;
    }
  }

  static Future<Uint8List?> _download(String url) async {
    try {
      final response = await http.get(Uri.parse(url)).timeout(networkTimeout);
      if (response.statusCode < 200 || response.statusCode >= 300) {
        return null;
      }
      return response.bodyBytes;
    } catch (_) {
      return null;
    }
  }
}
