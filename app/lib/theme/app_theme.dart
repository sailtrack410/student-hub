import 'package:flutter/material.dart';

/// 移动端设计令牌系统
/// 严格对齐 styles/tokens.wxss 与移动端画布设计规范
abstract final class AppColors {
  // 品牌色调
  static const brand = Color(0xFF2563EB); // #2563EB 宝蓝
  static const brandDeep = Color(0xFF1D4ED8);
  static const brandSoft = Color(0xFFEFF6FF); // #EFF6FF 浅蓝底

  // 辅助强调色
  static const accent = Color(0xFF10B981); // #10B981 青绿（v2.1）
  static const accentDeep = Color(0xFF059669);
  static const accentSoft = Color(0xFFECFDF5);

  // 语义色
  static const success = Color(0xFF10B981);
  static const warning = Color(0xFFF59E0B);
  static const warningSoft = Color(0xFFFFFBEB);
  static const warningSoftBorder = Color(0xFFFDE68A);
  static const danger = Color(0xFFEF4444);
  static const dangerSoft = Color(0xFFFEF2F2);
  static const dangerBorder = Color(0xFFFECACA);

  // 文字颜色
  static const textPrimary = Color(0xFF0F172A); // #0F172A 正黑
  static const textSecondary = Color(0xFF64748B); // #64748B 次要灰
  static const textMuted = Color(0xFF64748B); // 白卡上的辅助文字保持可读对比度
  static const textBody = Color(0xFF475569); // #475569 正文
  static const textWhite = Color(0xFFFFFFFF);

  // 背景与边框
  static const background = Color(0xFFF8FAFC); // 画布统一底色
  static const card = Color(0xFFFFFFFF);
  static const border = Color(0xFFE2E8F0);
  static const divider = Color(0xFFF1F5F9);

  // 阴影
  static const brandShadow = Color(0x472563EB); // 28% alpha
}

abstract final class AppTheme {
  static ThemeData get lightTheme {
    const textTheme = TextTheme(
      headlineMedium: TextStyle(
        fontSize: 28,
        height: 1.3,
        fontWeight: FontWeight.w700,
        color: AppColors.textPrimary,
      ),
      titleLarge: TextStyle(
        fontSize: 22,
        height: 1.4,
        fontWeight: FontWeight.w600,
        color: AppColors.textPrimary,
      ),
      titleMedium: TextStyle(
        fontSize: 18,
        height: 1.4,
        fontWeight: FontWeight.w600,
        color: AppColors.textPrimary,
      ),
      titleSmall: TextStyle(
        fontSize: 16,
        height: 1.4,
        fontWeight: FontWeight.w600,
        color: AppColors.textPrimary,
      ),
      bodyLarge: TextStyle(
        fontSize: 16,
        height: 1.5,
        color: AppColors.textPrimary,
      ),
      bodyMedium: TextStyle(
        fontSize: 14,
        height: 1.5,
        color: AppColors.textBody,
      ),
      bodySmall: TextStyle(
        fontSize: 12,
        height: 1.5,
        color: AppColors.textSecondary,
      ),
      labelLarge: TextStyle(
        fontSize: 14,
        height: 1.4,
        fontWeight: FontWeight.w600,
      ),
      labelMedium: TextStyle(
        fontSize: 12,
        height: 1.4,
        fontWeight: FontWeight.w600,
      ),
      labelSmall: TextStyle(fontSize: 12, height: 1.4),
    );
    final buttonStyle = ButtonStyle(
      minimumSize: const WidgetStatePropertyAll(Size(48, 48)),
      padding: const WidgetStatePropertyAll(
        EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      ),
      shape: WidgetStatePropertyAll(
        RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      textStyle: const WidgetStatePropertyAll(
        TextStyle(fontSize: 14, height: 1.4, fontWeight: FontWeight.w600),
      ),
    );
    return ThemeData(
      useMaterial3: true,
      pageTransitionsTheme: const PageTransitionsTheme(builders: {
        TargetPlatform.android: AccessiblePageTransitionsBuilder(),
        TargetPlatform.iOS: AccessiblePageTransitionsBuilder(),
        TargetPlatform.macOS: AccessiblePageTransitionsBuilder(),
        TargetPlatform.windows: AccessiblePageTransitionsBuilder(),
        TargetPlatform.linux: AccessiblePageTransitionsBuilder(),
      }),
      scaffoldBackgroundColor: AppColors.background,
      fontFamily: '-apple-system',
      textTheme: textTheme,
      materialTapTargetSize: MaterialTapTargetSize.padded,
      filledButtonTheme: FilledButtonThemeData(style: buttonStyle),
      elevatedButtonTheme: ElevatedButtonThemeData(style: buttonStyle),
      outlinedButtonTheme: OutlinedButtonThemeData(style: buttonStyle),
      textButtonTheme: TextButtonThemeData(style: buttonStyle),
      iconButtonTheme: const IconButtonThemeData(
        style: ButtonStyle(minimumSize: WidgetStatePropertyAll(Size(48, 48))),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.card,
        contentPadding: const EdgeInsets.all(16),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
        errorMaxLines: 3,
      ),
      cardTheme: CardThemeData(
        color: AppColors.card,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: AppColors.border),
        ),
      ),
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

class AccessiblePageTransitionsBuilder extends PageTransitionsBuilder {
  const AccessiblePageTransitionsBuilder();
  @override
  Widget buildTransitions<T>(PageRoute<T> route, BuildContext context,
      Animation<double> animation, Animation<double> secondaryAnimation, Widget child) {
    if (MediaQuery.disableAnimationsOf(context)) return child;
    return const FadeUpwardsPageTransitionsBuilder().buildTransitions(route, context, animation, secondaryAnimation, child);
  }
}
