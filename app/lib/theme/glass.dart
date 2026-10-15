import 'dart:ui' show ImageFilter;
import 'package:flutter/material.dart';

/// 液态玻璃设计系统（与 Ardot 画布配方 v3 对齐）
///
/// 核心原则：玻璃本体无色相，颜色由背后背景透过模糊呈现；
/// 深色页呈暗玻璃、浅色页呈亮玻璃。
abstract final class Glass {
  // ---- 磨砂玻璃底条（深色烟玻璃：暗部透暗、亮部透亮，边缘亮）----
  static const fillTop = Color(0x7318103C);
  static const fillBottom = Color(0x2E18103C);

  // 折射描边
  static const strokeTop = Color(0xCCFFFFFF); // white 80%
  static const strokeMid = Color(0x1AFFFFFF); // white 10%
  static const strokeBottom = Color(0x47AFAFBE); // 淡紫灰 28%

  // 高透透镜（激活态）
  static const lensTop = Color(0x4DFFFFFF);
  static const lensBottom = Color(0x24FFFFFF);
  static const lensStrokeTop = Color(0xE6FFFFFF);
  static const lensStrokeBottom = Color(0x40FFFFFF);

  // 阴影
  static const shadowAmbient = Color(0x59140E33);
  static const shadowContact = Color(0x47140E33);
  static const lensShadow = Color(0x38140E33);

  // 深色烟玻璃
  static const deepTint = Color(0xFF18103C);

  // 文字
  static const textActive = Color(0xF52E7CF6);
  static const textSecondary = Color(0xFF8F96B3);
  static const textPrimary = Color(0xFF1B1E33);
  static const textOnDark = Color(0xFFFFFFFF);

  /// 低模糊 + 高饱和透传（真液态玻璃：背景透过玻璃仍鲜艳可辨）
  static ImageFilter get frosted => ImageFilter.compose(
        outer: const ColorFilter.matrix(<double>[
          1.2725, -0.2105, -0.018, 0, 0,
          -0.0795, 1.1295, -0.018, 0, 0,
          -0.0795, -0.2105, 1.29, 0, 0,
          0, 0, 0, 1, 0,
        ]),
        inner: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
      );
}
