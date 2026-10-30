import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:open_filex/open_filex.dart';
import 'package:url_launcher/url_launcher.dart';

import '../api/api_client.dart';
import '../app_version.dart';
import '../theme/app_theme.dart';
import 'app_update_downloader.dart';

/// 公开接口 `latest` 字段（契约 §3.1）。
class AppVersionLatest {
  final int versionCode;
  final String versionName;
  final int? minSupportedVersionCode;
  final bool forceUpdate;
  final String updateLog;
  final String downloadUrl;
  final int packageSize;
  final String? packageSha256;
  final String? publishedAt;

  const AppVersionLatest({
    required this.versionCode,
    required this.versionName,
    this.minSupportedVersionCode,
    this.forceUpdate = false,
    this.updateLog = '',
    this.downloadUrl = '',
    this.packageSize = 0,
    this.packageSha256,
    this.publishedAt,
  });

  /// 宽容解析：字段缺失或类型不符时回退为安全默认值，不抛异常。
  factory AppVersionLatest.fromJson(Map<dynamic, dynamic> json) {
    return AppVersionLatest(
      versionCode: AppUpdateService._asInt(json['versionCode']) ?? 0,
      versionName: AppUpdateService._asString(json['versionName']),
      minSupportedVersionCode: AppUpdateService._asInt(
        json['minSupportedVersionCode'],
      ),
      forceUpdate: AppUpdateService._asBool(json['forceUpdate']),
      updateLog: AppUpdateService._asString(json['updateLog']),
      downloadUrl: AppUpdateService._asString(json['downloadUrl']),
      packageSize: AppUpdateService._asInt(json['packageSize']) ?? 0,
      packageSha256: AppUpdateService._asNullableString(json['packageSha256']),
      publishedAt: AppUpdateService._asNullableString(json['publishedAt']),
    );
  }
}

/// `GET /api/public/app-version/latest` 的解析结果。
class AppUpdateInfo {
  final bool hasUpdate;
  final bool forceUpdate;
  final int currentVersionCode;
  final AppVersionLatest? latest;

  const AppUpdateInfo({
    required this.hasUpdate,
    required this.forceUpdate,
    required this.currentVersionCode,
    this.latest,
  });
}

/// App 启动更新检查（契约 §5）。
///
/// - [check] 调公开接口，任何异常/Web 环境都返回 null，不阻塞启动；
/// - [checkAndPrompt] 启动时调用一次，有更新才弹层；全局只提示一次；
/// - 强制更新：弹层不可点遮罩关闭，且只保留「立即更新」按钮。
class AppUpdateService {
  AppUpdateService._();

  static const String latestPath = '/api/public/app-version/latest';

  /// 全局只提示一次（进程级）：首页可能因重新登录等原因重建。
  static bool _prompted = false;

  /// 检查进行中标记，避免首页重建时并发重复请求。
  static bool _checking = false;

  /// 供测试复位「已提示」标记。
  @visibleForTesting
  static void resetPromptedForTest() => _prompted = false;

  /// 当前平台标识；Web 或不需要检查更新的桌面端返回 null。
  static String? get currentPlatform {
    if (kIsWeb) return null;
    try {
      if (Platform.isAndroid) return 'ANDROID';
      if (Platform.isIOS) return 'IOS';
    } catch (_) {
      // 极端情况下平台信息不可用，按不检查处理。
    }
    return null;
  }

  /// 查询最新版本；网络失败、后端未就绪或非移动端时返回 null（静默）。
  static Future<AppUpdateInfo?> check() async {
    final platform = currentPlatform;
    if (platform == null) return null;
    try {
      final data = await ApiClient().get(
        latestPath,
        query: {
          'platform': platform,
          'channel': appChannel,
          'versionCode': appVersionCode,
        },
      );
      return parseResponse(data);
    } catch (_) {
      // 启动检查失败不影响使用。
      return null;
    }
  }

  /// 解析接口响应；`data` 不是 Map 时返回 null。
  ///
  /// 除服务端下发的 [AppUpdateInfo.hasUpdate]/[AppUpdateInfo.forceUpdate] 外，
  /// 客户端再按 `versionCode` 与 `minSupportedVersionCode` 兜底推导，
  /// 避免字段缺失时漏掉更新或强更。
  static AppUpdateInfo? parseResponse(dynamic payload) {
    if (payload is! Map) return null;

    final currentVersionCode =
        _asInt(payload['currentVersionCode']) ?? appVersionCode;
    final latestRaw = payload['latest'];
    final latest = latestRaw is Map
        ? AppVersionLatest.fromJson(latestRaw)
        : null;

    final hasUpdate =
        _asBool(payload['hasUpdate']) ||
        (latest != null && latest.versionCode > currentVersionCode);

    final minSupported = latest?.minSupportedVersionCode;
    final forceUpdate =
        _asBool(payload['forceUpdate']) ||
        (latest != null &&
            (latest.forceUpdate ||
                (minSupported != null && currentVersionCode < minSupported)));

    return AppUpdateInfo(
      hasUpdate: hasUpdate,
      forceUpdate: forceUpdate,
      currentVersionCode: currentVersionCode,
      latest: latest,
    );
  }

  /// 启动时检查并提示；全局只提示一次，异常静默。
  ///
  /// 只有真正弹出更新层才占用「一次」机会：网络失败/无更新时不置位，
  /// 首页后续重建仍可再试。
  static Future<void> checkAndPrompt(BuildContext context) async {
    if (_prompted || _checking) return;
    _checking = true;
    AppUpdateInfo? info;
    try {
      info = await check();
    } finally {
      _checking = false;
    }

    if (info == null) return;
    await prompt(context, info);
  }

  static Future<void> prompt(BuildContext context, AppUpdateInfo info) async {
    final latest = info.latest;
    if (!info.hasUpdate || latest == null || !context.mounted) return;

    _prompted = true;
    final forceUpdate = info.forceUpdate;
    await showDialog<void>(
      context: context,
      barrierDismissible: !forceUpdate,
      builder: (dialogContext) => _buildUpdateDialog(
        dialogContext: dialogContext,
        pageContext: context,
        latest: latest,
        forceUpdate: forceUpdate,
      ),
    );
  }

  /// 相对下载地址拼 [ApiClient.defaultBaseUrl]，绝对地址原样返回。
  static String resolveDownloadUrl(String rawUrl) {
    final url = rawUrl.trim();
    final base = ApiClient.defaultBaseUrl;
    final normalizedBase = base.endsWith('/')
        ? base.substring(0, base.length - 1)
        : base;
    if (url.isEmpty) return normalizedBase;
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    if (url.startsWith('/')) return '$normalizedBase$url';
    return '$normalizedBase/$url';
  }

  /// 安装包大小展示，例如 `46.0 MB`。
  static String formatPackageSize(int bytes) {
    if (bytes <= 0) return '未知大小';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) {
      return '${(bytes / 1024).toStringAsFixed(1)} KB';
    }
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
    }
    return '${(bytes / 1024 / 1024 / 1024).toStringAsFixed(2)} GB';
  }

  static Widget _buildUpdateDialog({
    required BuildContext dialogContext,
    required BuildContext pageContext,
    required AppVersionLatest latest,
    required bool forceUpdate,
  }) {
    final log = latest.updateLog.trim().isEmpty
        ? '本次更新包含稳定性与体验优化。'
        : latest.updateLog.trim();

    return PopScope(
      // 强制更新时同时禁用返回键，避免绕过更新弹层。
      canPop: !forceUpdate,
      child: AlertDialog(
        backgroundColor: AppColors.card,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        titlePadding: const EdgeInsets.fromLTRB(24, 22, 24, 0),
        contentPadding: const EdgeInsets.fromLTRB(24, 14, 24, 0),
        actionsPadding: const EdgeInsets.fromLTRB(16, 8, 16, 14),
        title: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: const BoxDecoration(
                color: AppColors.brandSoft,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.system_update_alt_rounded,
                size: 22,
                color: AppColors.brand,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                '发现新版本 v${latest.versionName}',
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 17,
                  fontWeight: FontWeight.bold,
                  color: AppColors.textPrimary,
                ),
              ),
            ),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(
                  '版本 ${latest.versionCode}',
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
                const SizedBox(width: 10),
                Text(
                  '安装包 ${formatPackageSize(latest.packageSize)}',
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              constraints: const BoxConstraints(maxHeight: 180),
              decoration: BoxDecoration(
                color: AppColors.background,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.border),
              ),
              child: SingleChildScrollView(
                child: Text(
                  log,
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 13,
                    height: 1.5,
                    color: AppColors.textBody,
                  ),
                ),
              ),
            ),
            if (forceUpdate) ...[
              const SizedBox(height: 12),
              const Text(
                '该版本为强制更新，更新后才能继续使用。',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: AppColors.warning,
                ),
              ),
            ],
          ],
        ),
        actions: [
          if (!forceUpdate)
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: const Text(
                '稍后',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  color: AppColors.textSecondary,
                ),
              ),
            ),
          ElevatedButton(
            onPressed: () => _openDownload(
              dialogContext: dialogContext,
              pageContext: pageContext,
              downloadUrl: latest.downloadUrl,
              forceUpdate: forceUpdate,
              latest: latest,
            ),
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.brand,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
            ),
            child: const Text(
              '立即更新',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// 下载更新：Android 走应用内流式下载 + 进度 + SHA-256 校验；
  /// Web/iOS/其他平台仍走系统浏览器。
  static Future<void> _openDownload({
    required BuildContext dialogContext,
    required BuildContext pageContext,
    required String downloadUrl,
    required bool forceUpdate,
    AppVersionLatest? latest,
  }) async {
    final raw = downloadUrl.trim();
    if (raw.isEmpty) {
      _showToast(pageContext, '暂未获取到下载地址，请稍后再试');
      return;
    }

    final canInAppInstall = !kIsWeb && Platform.isAndroid && latest != null;
    if (canInAppInstall) {
      if (!dialogContext.mounted) return;
      Navigator.of(dialogContext).pop();
      await showDialog<void>(
        context: pageContext,
        barrierDismissible: false,
        builder: (_) =>
            _UpdateDownloadDialog(latest: latest, forceUpdate: forceUpdate),
      );
      return;
    }

    final url = resolveDownloadUrl(raw);
    var opened = false;
    final uri = Uri.tryParse(url);
    if (uri != null) {
      try {
        opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
      } catch (_) {
        opened = false;
      }
    }

    if (opened) {
      if (!forceUpdate && dialogContext.mounted) {
        Navigator.of(dialogContext).pop();
      }
      return;
    }

    // 无 url_launcher 能力或拉起失败：复制下载链接，用户可自行在浏览器打开。
    var copied = false;
    try {
      await Clipboard.setData(ClipboardData(text: url));
      copied = true;
    } catch (_) {
      copied = false;
    }
    if (pageContext.mounted) {
      _showToast(
        pageContext,
        copied ? '下载链接已复制到剪贴板，请在浏览器中打开' : '打开下载链接失败，请稍后再试',
      );
    }
    if (copied && !forceUpdate && dialogContext.mounted) {
      Navigator.of(dialogContext).pop();
    }
  }

  static void _showToast(BuildContext context, String message) {
    if (!context.mounted) return;
    ScaffoldMessenger.maybeOf(context)?.showSnackBar(
      SnackBar(
        content: Text(message),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  static int? _asInt(dynamic value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    if (value is String) return int.tryParse(value.trim());
    return null;
  }

  static bool _asBool(dynamic value) {
    if (value is bool) return value;
    if (value is num) return value != 0;
    if (value is String) {
      final normalized = value.trim().toLowerCase();
      return normalized == 'true' || normalized == '1';
    }
    return false;
  }

  static String _asString(dynamic value) {
    if (value == null) return '';
    return value.toString().trim();
  }

  static String? _asNullableString(dynamic value) {
    final text = _asString(value);
    return text.isEmpty ? null : text;
  }
}

class _UpdateDownloadDialog extends StatefulWidget {
  final AppVersionLatest latest;
  final bool forceUpdate;

  const _UpdateDownloadDialog({
    required this.latest,
    required this.forceUpdate,
  });

  @override
  State<_UpdateDownloadDialog> createState() => _UpdateDownloadDialogState();
}

class _UpdateDownloadDialogState extends State<_UpdateDownloadDialog> {
  double? _progress;
  int _received = 0;
  int _total = 0;
  bool _verifying = false;
  bool _cancelled = false;
  bool _opening = false;
  String? _error;
  DownloadedPackage? _package;

  @override
  void initState() {
    super.initState();
    _start();
  }

  Future<void> _start() async {
    setState(() {
      _error = null;
      _progress = null;
      _received = 0;
      _total = widget.latest.packageSize;
      _verifying = false;
      _cancelled = false;
      _opening = false;
    });
    try {
      final package = await AppUpdateDownloader.download(
        url: AppUpdateService.resolveDownloadUrl(widget.latest.downloadUrl),
        expectedSize: widget.latest.packageSize,
        expectedSha256: widget.latest.packageSha256,
        onProgress: (received, total) {
          if (!mounted) return;
          setState(() {
            _received = received;
            _total = total;
            _progress = total > 0 ? received / total : null;
          });
        },
        isCancelled: () => _cancelled,
      );
      if (!mounted) return;
      setState(() {
        _verifying = true;
        _progress = 1;
        _package = package;
      });
      await Future<void>.delayed(const Duration(milliseconds: 250));
      if (!mounted) return;
      await _install(package);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _verifying = false;
        _error = e.toString().replaceFirst('UpdateDownloadException: ', '');
      });
    }
  }

  Future<void> _install(DownloadedPackage package) async {
    setState(() => _opening = true);
    try {
      final result = await OpenFilex.open(
        package.file.path,
        type: 'application/vnd.android.package-archive',
      );
      if (!mounted) return;
      if (result.type == ResultType.done) {
        Navigator.of(context).pop();
        AppUpdateService._showToast(
          context,
          package.checksumVerified
              ? '安装包校验通过，正在打开安装程序'
              : '安装包大小校验通过（服务端未提供 SHA-256），正在打开安装程序',
        );
      } else {
        setState(() {
          _opening = false;
          _error = result.message.isEmpty ? '无法打开安装程序，请检查安装权限' : result.message;
        });
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _opening = false;
        _error = '打开安装程序失败：$e';
      });
    }
  }

  void _cancel() {
    _cancelled = true;
    Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final total = _total;
    final received = _received;
    final status = _error != null
        ? _error!
        : _opening
        ? '正在打开安装程序…'
        : _verifying
        ? '正在校验安装包（SHA-256）…'
        : _progress == null
        ? '正在下载安装包…'
        : '正在下载 ${(_progress! * 100).toStringAsFixed(0)}%';

    return PopScope(
      canPop: !widget.forceUpdate,
      child: AlertDialog(
        backgroundColor: AppColors.card,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text(
          '应用更新',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            LinearProgressIndicator(
              value: _error != null ? null : (_verifying ? 1 : _progress),
              minHeight: 8,
              borderRadius: BorderRadius.circular(4),
              backgroundColor: AppColors.border,
              color: _error != null ? AppColors.danger : AppColors.brand,
            ),
            const SizedBox(height: 10),
            Text(
              status,
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: _error != null
                    ? AppColors.danger
                    : AppColors.textSecondary,
              ),
            ),
            if (total > 0) ...[
              const SizedBox(height: 4),
              Text(
                '${AppUpdateService.formatPackageSize(received)} / ${AppUpdateService.formatPackageSize(total)}',
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: AppColors.textSecondary,
                ),
              ),
            ],
            if (_package != null && _package!.checksumVerified) ...[
              const SizedBox(height: 8),
              const Row(
                children: [
                  Icon(
                    Icons.verified_rounded,
                    size: 16,
                    color: Color(0xFF16A34A),
                  ),
                  SizedBox(width: 4),
                  Text(
                    'SHA-256 校验通过',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      color: Color(0xFF16A34A),
                    ),
                  ),
                ],
              ),
            ],
            if (_opening) ...[
              const SizedBox(height: 12),
              const Center(
                child: SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ),
            ],
          ],
        ),
        actions: [
          if (_error != null && !widget.forceUpdate)
            TextButton(onPressed: _cancel, child: const Text('关闭')),
          if (_error != null)
            ElevatedButton(onPressed: _start, child: const Text('重试')),
          if (_error == null && !_opening)
            TextButton(onPressed: _cancel, child: const Text('取消')),
        ],
      ),
    );
  }
}
