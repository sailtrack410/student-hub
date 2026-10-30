import 'dart:io';

import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../models/community_models.dart';
import '../services/badge_catalog_sync.dart';
import '../services/badge_icon_cache.dart';

/// 统一的勋章图标组件（方案 B：服务端 iconUrl 优先，本地素材兜底）。
///
/// 渲染优先级：
/// 1. `BadgeCatalogSync.iconUrlFor(code)`（管理端上传/替换图标后任意勋章都会下发）
///    → [BadgeIconCache.resolve] 取磁盘缓存/下载；
/// 2. [iconUrl]（如用户名片接口直接下发的地址），仅在 catalog 未覆盖时使用；
/// 3. `communityBadgeAsset(code)` 内置本地素材；
/// 4. 占位图标。
///
/// 下载中、下载失败、无 iconUrl 时都不会出现空白，始终保持本地素材或占位图。
class BadgeIcon extends StatefulWidget {
  /// 勋章编码；为空时直接走本地素材/占位。
  final String? code;

  /// 图标外框尺寸。
  final double size;

  /// 占位图标尺寸，默认 `size * 0.7`。
  final double? placeholderSize;

  /// 图片填充方式。
  final BoxFit fit;

  /// 占位图标颜色。
  final Color placeholderColor;

  /// 显式图标地址（绝对或相对），优先级低于 catalog 下发的 iconUrl。
  final String? iconUrl;

  const BadgeIcon({
    super.key,
    required this.code,
    this.size = 24,
    this.placeholderSize,
    this.fit = BoxFit.contain,
    this.placeholderColor = const Color(0xFF2563EB),
    this.iconUrl,
  });

  @override
  State<BadgeIcon> createState() => _BadgeIconState();
}

class _BadgeIconState extends State<BadgeIcon> {
  Future<File?>? _iconFuture;

  @override
  void initState() {
    super.initState();
    _iconFuture = _resolve();
    BadgeCatalogSync.iconRegistryRevision.addListener(_handleRegistryChange);
  }

  @override
  void didUpdateWidget(covariant BadgeIcon oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.code != widget.code || oldWidget.iconUrl != widget.iconUrl) {
      _iconFuture = _resolve();
    }
  }

  @override
  void dispose() {
    BadgeCatalogSync.iconRegistryRevision.removeListener(_handleRegistryChange);
    super.dispose();
  }

  void _handleRegistryChange() {
    if (!mounted) return;
    setState(() => _iconFuture = _resolve());
  }

  Future<File?> _resolve() async {
    final code = widget.code?.trim() ?? '';
    var url = '';
    if (code.isNotEmpty) {
      await BadgeCatalogSync.ensureIconUrlsLoaded();
      url = BadgeCatalogSync.iconUrlFor(code) ?? '';
    }
    if (url.isEmpty) {
      url = widget.iconUrl?.trim() ?? '';
    }
    if (url.isEmpty) return null;

    final absolute = _absoluteUrl(url);
    if (absolute == null) return null;
    return BadgeIconCache.resolve(absolute);
  }

  String? _absoluteUrl(String raw) {
    if (raw.startsWith('http://') || raw.startsWith('https://')) return raw;
    final base = ApiClient.defaultBaseUrl;
    if (base.isEmpty) return null;
    return raw.startsWith('/') ? '$base$raw' : '$base/$raw';
  }

  @override
  Widget build(BuildContext context) {
    final code = widget.code?.trim() ?? '';
    return SizedBox(
      width: widget.size,
      height: widget.size,
      child: FutureBuilder<File?>(
        future: _iconFuture,
        builder: (context, snapshot) {
          final file = snapshot.connectionState == ConnectionState.done
              ? snapshot.data
              : null;
          if (file != null) {
            return Image.file(
              file,
              width: widget.size,
              height: widget.size,
              fit: widget.fit,
              errorBuilder: (_, _, _) => _localFallback(code),
            );
          }
          return _localFallback(code);
        },
      ),
    );
  }

  /// 本地素材兜底；内置 19 枚之外的勋章没有本地素材，落到占位图标。
  Widget _localFallback(String code) {
    final asset = communityBadgeAsset(code);
    if (asset != null) {
      return Image.asset(
        asset,
        width: widget.size,
        height: widget.size,
        fit: widget.fit,
        errorBuilder: (_, _, _) => _placeholder(),
      );
    }
    return _placeholder();
  }

  Widget _placeholder() {
    return Icon(
      Icons.military_tech_rounded,
      size: widget.placeholderSize ?? widget.size * 0.7,
      color: widget.placeholderColor,
    );
  }
}
