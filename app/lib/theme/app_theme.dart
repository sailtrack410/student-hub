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

  // 社区信息流令牌（V4 画布 30:1，2026-11-05）
  static const likeActive = Color(0xFFF43F5E); // 点赞激活品红 rose-500，与危险红 #EF4444 区分
  static const feedDivider = Color(0xFFE8EDF3); // 信息流通栏分隔线
  static const chipBg = Color(0xFFF1F5F9); // 分区胶囊默认底

  // 文字颜色
  static const textPrimary = Color(0xFF0F172A); // #0F172A 正黑
  static const textSecondary = Color(0xFF64748B); // #64748B 次要灰
  static const textTertiary = Color(0xFF94A3B8); // #94A3B8 辅助灰（社区画布 30:1：meta/互动条非激活）
  static const textMuted = Color(0xFF64748B); // 白卡上的辅助文字保持可读对比度
  static const textBody = Color(0xFF475569); // #475569 正文
  static const textWhite = Color(0xFFFFFFFF);

  // 背景与边框
  static const background = Color(0xFFF8FAFC); // 画布统一底色
  static const card = Color(0xFFFFFFFF);
  static const border = Color(0xFFE2E8F0);
  static const divider = Color(0xFFE8EDF3); // 统一分隔线色 #E8EDF3

  // 阴影
  static const brandShadow = Color(0x472563EB); // 28% alpha
}

/// 全局字号与行高令牌（禁止在业务页面散落硬编码字号）
abstract final class AppTypography {
  static const String fontFamily = 'Noto Sans SC';

  // 字号阶梯
  static const double navTitleSize = 17; // 导航栏标题 17–18，w600
  static const double sessionTitleSize = 16; // 消息会话名称 16，w600
  static const double postTitleSize = 16; // 社区/热帖标题 16，最多两行
  static const double cardTitleSize = 16; // 通用卡片主标题 16
  static const double feedTitleSize = 15; // 社区信息流紧凑单行标题
  static const double groupTitleSize = 15; // 分组标题
  static const double itemTitleSize = 14; // 列表项标题
  static const double summarySize = 14; // 消息摘要 / 社区内容摘要 14
  static const double feedSummarySize = 13; // 信息流单行摘要
  static const double metaSize = 12; // 时间、分类等辅助信息 12–13
  static const double feedMetaSize = 11; // 社区顶栏紧凑内联 meta
  static const double announcementBodySize = 16; // 公告正文 16
  static const double badgeSize = 10; // 小型徽章（仅短辅助标识）

  // 行高阶梯
  static const double titleLineHeight = 1.4; // 标题行高 1.4
  static const double summaryLineHeight = 1.5; // 摘要行高 1.5
  static const double announcementLineHeight = 1.7; // 公告正文行高 1.7

  // 字重令牌
  static const FontWeight navTitleWeight = FontWeight.w600;
  static const FontWeight sessionTitleWeight = FontWeight.w600;
  static const FontWeight postTitleWeight = FontWeight.w600;
}

/// 全局间距与触控尺寸令牌（禁止在业务页面散落硬编码间距）
abstract final class AppSpacing {
  static const double pageHorizontal = 16; // 列表页左右边距 16
  static const double homeHorizontal = 20; // 首页内容区左右边距 20
  static const double cardPadding = 16; // 卡片内部留白 16
  static const double avatarTextGap = 12; // 头像与文字距离 12
  static const double compactAvatarGap = 8; // 紧凑小头像与文字距离
  static const double titleSummaryGap = 6; // 标题与摘要距离 6–8
  static const double titleSummaryGapLarge = 8; // 标题与摘要距离（宽松档 8）
  static const double cardGroupGap = 12; // 同一卡片内的信息组间距 12
  static const double cardGap = 12; // 独立卡片之间 12
  static const double sectionGap = 24; // 页面大模块间距基准 24
  static const double minTapTarget = 44; // 图标最小点击区域 44
  static const double preferredTapTarget = 48; // 图标优先点击区域 48
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
      minimumSize: const WidgetStatePropertyAll(
        Size(AppSpacing.preferredTapTarget, AppSpacing.preferredTapTarget),
      ),
      padding: const WidgetStatePropertyAll(
        EdgeInsets.symmetric(
          horizontal: AppSpacing.pageHorizontal,
          vertical: AppSpacing.cardGroupGap,
        ),
      ),
      shape: WidgetStatePropertyAll(
        RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      textStyle: const WidgetStatePropertyAll(
        TextStyle(
          fontSize: AppTypography.itemTitleSize,
          height: AppTypography.titleLineHeight,
          fontWeight: FontWeight.w600,
        ),
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
        style: ButtonStyle(
          minimumSize: WidgetStatePropertyAll(
            Size(AppSpacing.preferredTapTarget, AppSpacing.preferredTapTarget),
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.card,
        contentPadding: const EdgeInsets.all(AppSpacing.cardPadding),
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
      dividerTheme: const DividerThemeData(
        color: AppColors.feedDivider,
        thickness: 1,
        space: 1,
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
          fontSize: AppTypography.navTitleSize,
          fontWeight: AppTypography.navTitleWeight,
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
