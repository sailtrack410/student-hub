import 'package:flutter/material.dart';

import '../api/api_client.dart';

/// 统一的用户头像：有 QQ/上传头像时显示网络图，否则显示默认渐变头像 + 昵称首字。
/// 服务端对本地头像返回带签名的相对路径，对 QQ 头像返回完整 https 地址。
class UserAvatar extends StatelessWidget {
  final String? avatarPath;
  final String name;
  final double size;
  final String? baseUrl;
  final List<Color> gradientColors;
  final double fontSize;

  const UserAvatar({
    super.key,
    required this.name,
    this.avatarPath,
    required this.size,
    this.baseUrl,
    this.gradientColors = const [Color(0xFF2563EB), Color(0xFF60A5FA)],
    this.fontSize = 24,
  });

  String? get _resolvedUrl {
    final raw = avatarPath?.trim();
    if (raw == null || raw.isEmpty) return null;
    if (raw.startsWith('http://') || raw.startsWith('https://')) return raw;
    final base = (baseUrl ?? ApiClient.defaultBaseUrl).replaceAll(
      RegExp(r'/$'),
      '',
    );
    if (raw.startsWith('/')) return '$base$raw';
    return raw;
  }

  @override
  Widget build(BuildContext context) {
    final url = _resolvedUrl;
    final fallback = Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        gradient: LinearGradient(
          colors: gradientColors,
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      alignment: Alignment.center,
      child: Text(
        name.isNotEmpty ? name.substring(0, 1) : '我',
        style: TextStyle(
          fontFamily: 'Noto Sans SC',
          fontSize: fontSize,
          fontWeight: FontWeight.bold,
          color: Colors.white,
        ),
      ),
    );

    return Semantics(
      label: '$name的头像',
      image: url != null,
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: const Color(0xFFEFF6FF),
        ),
        clipBehavior: Clip.antiAlias,
        child: url == null
            ? fallback
            : Image.network(
                url,
                width: size,
                height: size,
                fit: BoxFit.cover,
                errorBuilder: (_, _, _) => fallback,
                loadingBuilder: (context, child, progress) {
                  return progress == null ? child : fallback;
                },
              ),
      ),
    );
  }
}
