import 'package:flutter/material.dart';

/// 移动端设计令牌系统
/// 严格对齐 styles/tokens.wxss 与移动端画布设计规范
abstract final class AppColors {
  // 品牌色调
  static const brand = Color(0xFF2563EB); // #2563EB 宝蓝
  static const brandDeep = Color(0xFF1D4ED8);
  static const brandSoft = Color(0xFFEAF0FE); // #EAF0FE 浅蓝底

  // 辅助强调色
  static const accent = Color(0xFF14B8A6); // #14B8A6 青绿
  static const accentDeep = Color(0xFF0F766E);
  static const accentSoft = Color(0xFFE3F7F4);

  // 语义色
  static const success = Color(0xFF16A34A);
  static const warning = Color(0xFFD97706);
  static const danger = Color(0xFFDC2626);
  static const dangerSoft = Color(0xFFFDF2F2);
  static const dangerBorder = Color(0xFFF9D1D1);

  // 文字颜色
  static const textPrimary = Color(0xFF0F172A); // #0F172A 正黑
  static const textSecondary = Color(0xFF64748B); // #64748B 次要灰
  static const textMuted = Color(0xFF94A3B8); // #94A3B8 弱化灰
  static const textWhite = Color(0xFFFFFFFF);

  // 背景与边框
  static const background = Color(0xFFF7FAF9); // 画布背景色
  static const card = Color(0xFFFFFFFF);
  static const border = Color(0xFFE2E8F0);
  static const divider = Color(0xFFF1F5F9);

  // 阴影
  static const brandShadow = Color(0x472563EB); // 28% alpha
}

abstract final class AppTheme {
  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      scaffoldBackgroundColor: AppColors.background,
      fontFamily: '-apple-system',
      colorScheme: ColorScheme.fromSeed(
        seedColor: AppColors.brand,
        primary: AppColors.brand,
        surface: AppColors.card,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: true,
        titleTextStyle: TextStyle(
          fontSize: 17,
          fontWeight: FontWeight.w600,
          color: AppColors.textPrimary,
        ),
      ),
    );
  }
}
