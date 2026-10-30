import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

class UpdateDownloadException implements Exception {
  final String message;
  const UpdateDownloadException(this.message);
  @override
  String toString() => message;
}

class DownloadedPackage {
  final File file;
  final String sha256;
  final int size;
  final bool checksumVerified;

  const DownloadedPackage({
    required this.file,
    required this.sha256,
    required this.size,
    required this.checksumVerified,
  });
}

/// 下载安装包并校验大小与 SHA-256；失败自动删除半成品。
class AppUpdateDownloader {
  static Future<DownloadedPackage> download({
    required String url,
    required int expectedSize,
    String? expectedSha256,
    required void Function(int received, int total) onProgress,
    bool Function()? isCancelled,
  }) async {
    final client = http.Client();
    File? file;
    IOSink? sink;
    try {
      final response = await client.send(http.Request('GET', Uri.parse(url)));
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw UpdateDownloadException('下载失败：HTTP ${response.statusCode}');
      }
      final total = response.contentLength ?? expectedSize;
      final directory = await getTemporaryDirectory();
      final name = _fileNameFromUrl(url);
      file = File('${directory.path}/$name');
      sink = file.openWrite();
      var received = 0;
      await for (final chunk in response.stream) {
        if (isCancelled?.call() == true) {
          throw const UpdateDownloadException('下载已取消');
        }
        sink.add(chunk);
        received += chunk.length;
        onProgress(received, total);
      }
      await sink.flush();
      await sink.close();
      sink = null;

      if (expectedSize > 0 && received != expectedSize) {
        throw UpdateDownloadException('安装包大小校验失败（$received/$expectedSize 字节）');
      }

      final digest = await sha256.bind(file.openRead()).first;
      final actual = digest.toString().toLowerCase();
      final expected = (expectedSha256 ?? '').trim().toLowerCase();
      final verified = expected.isNotEmpty && expected == actual;
      if (expected.isNotEmpty && !verified) {
        throw const UpdateDownloadException('SHA-256 校验失败，安装包可能不完整或被篡改');
      }
      return DownloadedPackage(
        file: file,
        sha256: actual,
        size: received,
        checksumVerified: verified,
      );
    } catch (e) {
      try {
        await sink?.close();
      } catch (_) {}
      try {
        if (file != null && await file.exists()) await file.delete();
      } catch (_) {}
      if (e is UpdateDownloadException) rethrow;
      throw UpdateDownloadException('下载失败：$e');
    } finally {
      client.close();
    }
  }

  static String _fileNameFromUrl(String url) {
    final uri = Uri.tryParse(url);
    final last = uri?.pathSegments.isNotEmpty == true ? uri!.pathSegments.last : '';
    if (last.toLowerCase().endsWith('.apk')) return last;
    return 'student-hub-${DateTime.now().millisecondsSinceEpoch}.apk';
  }
}
