import 'package:flutter/material.dart';

import '../../api/achievement_api.dart';
import '../../services/badge_catalog_sync.dart';
import '../../widgets/badge_icon.dart';

/// 勋章分类（`code` 为空表示“全部”）。
class _BadgeCategory {
  final String code;
  final String name;

  const _BadgeCategory(this.code, this.name);
}

/// 勋章展示定义（纯字典，不含用户态；用户态来自 /state）。
class _BadgeDefinition {
  final String code;
  final String name;
  final String categoryCode;
  final String categoryName;
  final String description;
  final String condition;
  final String rarity; // 经典 / 稀有 / 史诗 / 传说
  final int targetProgress;

  // 仅作为“无任何服务端数据”时的首屏内置演示态，联网后会被 state 覆盖。
  final bool demoUnlocked;
  final bool demoWorn;
  final int demoProgress;

  const _BadgeDefinition({
    required this.code,
    required this.name,
    required this.categoryCode,
    required this.categoryName,
    required this.description,
    required this.condition,
    required this.rarity,
    this.targetProgress = 1,
    this.demoUnlocked = false,
    this.demoWorn = false,
    this.demoProgress = 0,
  });
}

/// 内置 19 枚兜底字典（离线/首屏占位；在线后以服务端 catalog 为准）。
const List<_BadgeDefinition> _builtInBadges = [
  _BadgeDefinition(
    code: 'COMMUNITY_FIRST_POST',
    name: '破冰首帖',
    categoryCode: 'COMMUNITY',
    categoryName: '社区先锋',
    description: '记录在绵城的第一声问候，迈出校园社交第一步',
    condition: '在校园社区发布 1 篇动态或文章',
    rarity: '经典',
    demoUnlocked: true,
    demoWorn: true,
    demoProgress: 1,
  ),
  _BadgeDefinition(
    code: 'COMMUNITY_POST_10',
    name: '社区常客',
    categoryCode: 'COMMUNITY',
    categoryName: '社区先锋',
    description: '分享校园生活日常，成为同学们熟悉的面孔',
    condition: '在校园社区累计发布 10 篇动态',
    rarity: '稀有',
    targetProgress: 10,
    demoProgress: 3,
  ),
  _BadgeDefinition(
    code: 'COMMUNITY_POST_50',
    name: '校园创作者',
    categoryCode: 'COMMUNITY',
    categoryName: '社区先锋',
    description: '持续输出高价值校园资讯与精彩日常',
    condition: '在校园社区累计发布 50 篇动态',
    rarity: '史诗',
    targetProgress: 50,
    demoProgress: 3,
  ),
  _BadgeDefinition(
    code: 'COMMUNITY_COMMENT_20',
    name: '热忱交流',
    categoryCode: 'COMMUNITY',
    categoryName: '社区先锋',
    description: '乐于助人积极留言，传递温暖校园正能量',
    condition: '在他人动态下发表 20 条友善评论',
    rarity: '稀有',
    targetProgress: 20,
    demoProgress: 8,
  ),
  _BadgeDefinition(
    code: 'COMMUNITY_LIKES_50',
    name: '人气新星',
    categoryCode: 'COMMUNITY',
    categoryName: '社区先锋',
    description: '内容备受同学喜爱，收获满溢的共鸣与好评',
    condition: '发布的动态累计获得 50 次点赞',
    rarity: '稀有',
    targetProgress: 50,
    demoProgress: 26,
  ),
  _BadgeDefinition(
    code: 'COMMUNITY_HOT_POST',
    name: '广场焦点',
    categoryCode: 'COMMUNITY',
    categoryName: '社区先锋',
    description: '点燃校园热门讨论，登顶广场焦点榜单',
    condition: '单篇动态获赞数突破 100 次',
    rarity: '传说',
    targetProgress: 100,
    demoProgress: 42,
  ),
  _BadgeDefinition(
    code: 'SERVICE_FIRST_REQUEST',
    name: '初试身手',
    categoryCode: 'SERVICE',
    categoryName: '办事达人',
    description: '体验数字化校园后勤，享受一键直达的高效响应',
    condition: '在服务大厅发起首次报修或办事申请',
    rarity: '经典',
    demoUnlocked: true,
    demoProgress: 1,
  ),
  _BadgeDefinition(
    code: 'SERVICE_COMPLETED_5',
    name: '办事通达',
    categoryCode: 'SERVICE',
    categoryName: '办事达人',
    description: '熟练运用校园数字服务，让学习与生活无忧',
    condition: '累计完成并办结 5 项服务申请',
    rarity: '稀有',
    targetProgress: 5,
    demoProgress: 2,
  ),
  _BadgeDefinition(
    code: 'SERVICE_EVALUATION_5',
    name: '质检先锋',
    categoryCode: 'SERVICE',
    categoryName: '办事达人',
    description: '认真给出验收反馈，助力校园后勤服务持续升级',
    condition: '完成 5 次报修工单完工验收与评分',
    rarity: '稀有',
    targetProgress: 5,
    demoProgress: 1,
  ),
  _BadgeDefinition(
    code: 'SERVICE_FIVE_STAR_10',
    name: '满意信使',
    categoryCode: 'SERVICE',
    categoryName: '办事达人',
    description: '肯定后勤师傅的辛勤付出，共建和谐文明校园',
    condition: '在完工评价中累计给出 10 次五星好评',
    rarity: '史诗',
    targetProgress: 10,
    demoProgress: 2,
  ),
  _BadgeDefinition(
    code: 'JW_FIRST_AUTH',
    name: '实名认证',
    categoryCode: 'JW',
    categoryName: '校园启航',
    description: '打通教务数据通道，开启智慧校园全功能体验',
    condition: '完成教务系统学号与密码首次绑定',
    rarity: '经典',
    demoUnlocked: true,
    demoProgress: 1,
  ),
  _BadgeDefinition(
    code: 'SCHEDULE_FIRST_IMPORT',
    name: '课表启航',
    categoryCode: 'JW',
    categoryName: '校园启航',
    description: '学期课程清晰在列，按时上课做自律绵城学子',
    condition: '成功导入并同步新学期个人课程表',
    rarity: '经典',
    demoUnlocked: true,
    demoProgress: 1,
  ),
  _BadgeDefinition(
    code: 'PROFILE_COMPLETE',
    name: '完美档案',
    categoryCode: 'JW',
    categoryName: '校园启航',
    description: '完善院系班级与个人信息，点亮真实校园身份',
    condition: '完善个人主页简介与各项基本信息',
    rarity: '稀有',
    targetProgress: 4,
    demoProgress: 3,
  ),
  _BadgeDefinition(
    code: 'LEVEL_3',
    name: '进阶三级',
    categoryCode: 'GROWTH',
    categoryName: '等级荣耀',
    description: '活跃在绵城校园各大板块，成长值突破初阶门槛',
    condition: '综合经验值达到 Lv.3 等级',
    rarity: '经典',
    targetProgress: 3,
    demoUnlocked: true,
    demoProgress: 3,
  ),
  _BadgeDefinition(
    code: 'LEVEL_5',
    name: '骨干五级',
    categoryCode: 'GROWTH',
    categoryName: '等级荣耀',
    description: '成为校园生活活跃骨干，解锁专属社区头衔',
    condition: '综合经验值达到 Lv.5 等级',
    rarity: '稀有',
    targetProgress: 5,
    demoProgress: 4,
  ),
  _BadgeDefinition(
    code: 'LEVEL_7',
    name: '领航七级',
    categoryCode: 'GROWTH',
    categoryName: '等级荣耀',
    description: '卓越校园贡献者，享有优先工单响应与尊贵标识',
    condition: '综合经验值达到 Lv.7 等级',
    rarity: '史诗',
    targetProgress: 7,
    demoProgress: 4,
  ),
  _BadgeDefinition(
    code: 'LEVEL_9',
    name: '巅峰九级',
    categoryCode: 'GROWTH',
    categoryName: '等级荣耀',
    description: '绵城学子最高荣誉殿堂，全服仅少数顶尖学者佩戴',
    condition: '综合经验值达到 Lv.9 等级',
    rarity: '传说',
    targetProgress: 9,
    demoProgress: 4,
  ),
  _BadgeDefinition(
    code: 'WORKER_FIRST_ORDER',
    name: '匠心起步',
    categoryCode: 'SERVICE',
    categoryName: '办事达人',
    description: '后勤服务专员专属勋章，践行高效检修承诺',
    condition: '服务工程师完成首次派单上门处置',
    rarity: '稀有',
    demoProgress: 0,
  ),
  _BadgeDefinition(
    code: 'WORKER_ORDER_20',
    name: '校园卫士',
    categoryCode: 'SERVICE',
    categoryName: '办事达人',
    description: '后勤服务工程师专属，守护绵城师生水电设备安全',
    condition: '后勤师傅累计完成 20 单高质量上门维修',
    rarity: '史诗',
    targetProgress: 20,
    demoProgress: 0,
  ),
];

const List<_BadgeCategory> _builtInCategories = [
  _BadgeCategory('', '全部'),
  _BadgeCategory('COMMUNITY', '社区先锋'),
  _BadgeCategory('SERVICE', '办事达人'),
  _BadgeCategory('GROWTH', '等级荣耀'),
  _BadgeCategory('JW', '校园启航'),
];

final Map<String, _BadgeDefinition> _builtInByCode = {
  for (final badge in _builtInBadges) badge.code: badge,
};

/// APP-荣誉勋章墙 (对应画布 5:1934 节点，375 × 812)
/// 数据层：内置字典/本地缓存先渲染 → BadgeCatalogSync 同步 → catalog + state 重建。
/// 保留原有视觉结构：顶部总览、分类胶囊条、3 列勋章卡片、详情底部弹层与佩戴交互。
class BadgeWallPage extends StatefulWidget {
  const BadgeWallPage({super.key});

  @override
  State<BadgeWallPage> createState() => _BadgeWallPageState();
}

class _BadgeWallPageState extends State<BadgeWallPage> {
  static const String _demoWornCode = 'COMMUNITY_FIRST_POST';

  String _selectedCategoryCode = '';
  List<_BadgeCategory> _categories = _builtInCategories;
  List<_BadgeDefinition> _badges = _builtInBadges;

  // 无任何服务端数据前的内置演示态；拿到 state 后整体替换。
  Set<String> _unlockedBadgeCodes = _builtInBadges
      .where((badge) => badge.demoUnlocked)
      .map((badge) => badge.code)
      .toSet();
  Map<String, int> _progressByCode = {
    for (final badge in _builtInBadges) badge.code: badge.demoProgress,
  };
  String? _wornBadgeCode = _demoWornCode;

  @override
  void initState() {
    super.initState();
    _bootstrap();
  }

  /// 先读本地缓存渲染，再静默同步并重建（失败保留缓存/内置字典）。
  Future<void> _bootstrap() async {
    final cachedCatalog = await BadgeCatalogSync.cachedCatalog();
    final cachedState = await BadgeCatalogSync.cachedState();
    if (!mounted) return;
    if (cachedCatalog != null || cachedState != null) {
      setState(() => _applyData(cachedCatalog, cachedState));
    }

    await BadgeCatalogSync.sync();
    await _refreshFromCache();
  }

  Future<void> _refreshFromCache() async {
    final catalog = await BadgeCatalogSync.cachedCatalog();
    final state = await BadgeCatalogSync.cachedState();
    if (!mounted) return;
    setState(() => _applyData(catalog, state));
  }

  void _applyData(Map<String, dynamic>? catalog, Map<String, dynamic>? state) {
    final categoryNames = <String, String>{};
    final categories = <_BadgeCategory>[const _BadgeCategory('', '全部')];
    final rawCategories = catalog?['categories'];
    if (rawCategories is List) {
      for (final raw in rawCategories) {
        if (raw is! Map) continue;
        final code = (raw['categoryCode'] ?? '').toString();
        if (code.isEmpty) continue;
        final name = (raw['categoryName'] ?? '').toString();
        categoryNames[code] = name.isEmpty ? code : name;
        categories.add(_BadgeCategory(code, categoryNames[code]!));
      }
    }

    final rawBadges = catalog?['badges'];
    final badges = <_BadgeDefinition>[];
    if (rawBadges is List && rawBadges.isNotEmpty) {
      for (final raw in rawBadges) {
        if (raw is! Map) continue;
        final badge = _fromCatalogItem(raw, categoryNames);
        if (badge != null) badges.add(badge);
      }
    }
    if (badges.isEmpty) {
      badges.addAll(_builtInBadges);
    }
    if (categories.length == 1) {
      // catalog 未带分类时按勋章顺序兜底生成
      final seen = <String>{};
      for (final badge in badges) {
        if (badge.categoryCode.isEmpty) continue;
        if (!seen.add(badge.categoryCode)) continue;
        categories.add(_BadgeCategory(badge.categoryCode, badge.categoryName));
      }
    }

    final unlocked = <String>{};
    final progress = <String, int>{};
    String? worn;
    if (state != null) {
      final rawActivated = state['activated'];
      if (rawActivated is List) {
        for (final raw in rawActivated) {
          if (raw is! Map) continue;
          final code = (raw['badgeCode'] ?? '').toString();
          if (code.isNotEmpty) unlocked.add(code);
        }
      }
      final rawProgress = state['progress'];
      if (rawProgress is List) {
        for (final raw in rawProgress) {
          if (raw is! Map) continue;
          final code = (raw['badgeCode'] ?? '').toString();
          if (code.isEmpty) continue;
          progress[code] = _asInt(raw['currentProgress']);
        }
      }
      final rawWorn = state['wornBadgeCode']?.toString();
      worn = (rawWorn == null || rawWorn.isEmpty) ? null : rawWorn;
    } else if (catalog == null || rawBadges == null) {
      // 完全无服务端数据（首装离线）时沿用内置演示态，保证首屏可读。
      unlocked.addAll(
        _builtInBadges
            .where((badge) => badge.demoUnlocked)
            .map((badge) => badge.code),
      );
      for (final badge in _builtInBadges) {
        progress[badge.code] = badge.demoProgress;
      }
      worn = _demoWornCode;
    }

    if (worn != null && !badges.any((badge) => badge.code == worn)) {
      worn = null;
    }

    _categories = categories;
    _badges = badges;
    _unlockedBadgeCodes = unlocked;
    _progressByCode = progress;
    _wornBadgeCode = worn;
    if (!_categories.any(
      (category) => category.code == _selectedCategoryCode,
    )) {
      _selectedCategoryCode = '';
    }
  }

  _BadgeDefinition? _fromCatalogItem(
    Map<dynamic, dynamic> raw,
    Map<String, String> categoryNames,
  ) {
    final code = (raw['badgeCode'] ?? '').toString();
    if (code.isEmpty) return null;
    final builtIn = _builtInByCode[code];
    final categoryCode = (raw['categoryCode'] ?? '').toString();
    final description = (raw['description'] ?? '').toString();
    final threshold = _asInt(raw['metricThreshold']);
    final name = (raw['badgeName'] ?? '').toString();
    final rarity = _rarityLabel((raw['rarity'] ?? '').toString());
    var condition = builtIn?.condition ?? '';
    if (condition.isEmpty) {
      condition = threshold > 1
          ? '累计达成 $threshold 次'
          : (description.isEmpty ? '完成对应条件' : description);
    }
    return _BadgeDefinition(
      code: code,
      name: name.isEmpty ? (builtIn?.name ?? code) : name,
      categoryCode: categoryCode.isEmpty
          ? (builtIn?.categoryCode ?? '')
          : categoryCode,
      categoryName:
          categoryNames[categoryCode] ??
          builtIn?.categoryName ??
          (categoryCode.isEmpty ? '未分类' : categoryCode),
      description: description.isEmpty
          ? (builtIn?.description ?? '')
          : description,
      condition: condition,
      rarity: rarity.isEmpty ? (builtIn?.rarity ?? '经典') : rarity,
      targetProgress: threshold < 1 ? 1 : threshold,
    );
  }

  static String _rarityLabel(String raw) {
    switch (raw.trim().toUpperCase()) {
      case 'COMMON':
        return '经典';
      case 'RARE':
        return '稀有';
      case 'EPIC':
        return '史诗';
      case 'LEGENDARY':
        return '传说';
      default:
        return raw;
    }
  }

  static int _asInt(dynamic value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }

  /// 勋章图标：服务端 iconUrl（任意勋章都可被管理端替换）优先，本地素材兜底。
  Widget _badgeImage(_BadgeDefinition badge, {required double size}) {
    return BadgeIcon(code: badge.code, size: size);
  }

  /// 未解锁勋章叠加灰度滤镜；解锁勋章保持原色。
  Widget _badgeIcon(
    _BadgeDefinition badge, {
    required bool isUnlocked,
    required double size,
    double lockedOpacity = 0.45,
  }) {
    return SizedBox(
      width: size,
      height: size,
      child: ColorFiltered(
        colorFilter: isUnlocked
            ? const ColorFilter.mode(Colors.transparent, BlendMode.multiply)
            : ColorFilter.matrix(<double>[
                0.2126,
                0.7152,
                0.0722,
                0,
                0,
                0.2126,
                0.7152,
                0.0722,
                0,
                0,
                0.2126,
                0.7152,
                0.0722,
                0,
                0,
                0,
                0,
                0,
                lockedOpacity,
                0,
              ]),
        child: _badgeImage(badge, size: size),
      ),
    );
  }

  void _handleBadgeTap(_BadgeDefinition badge) {
    final isUnlocked = _unlockedBadgeCodes.contains(badge.code);
    final isWorn = _wornBadgeCode == badge.code;
    final currentProgress = _progressByCode[badge.code] ?? 0;
    final targetProgress = badge.targetProgress < 1 ? 1 : badge.targetProgress;

    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (ctx) => Material(
        color: Colors.white,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
        clipBehavior: Clip.antiAlias,
        child: Padding(
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 14,
            bottom: MediaQuery.of(ctx).padding.bottom + 20,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: const Color(0xFFE2E8F0),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 20),

              // 勋章大图标展示
              Container(
                width: 100,
                height: 100,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: isUnlocked
                          ? const Color(0x332563EB)
                          : const Color(0x1A0F172A),
                      blurRadius: 20,
                      offset: const Offset(0, 8),
                    ),
                  ],
                ),
                child: _badgeIcon(
                  badge,
                  isUnlocked: isUnlocked,
                  size: 100,
                  lockedOpacity: 0.6,
                ),
              ),
              const SizedBox(height: 14),

              // 勋章名称与稀有度
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    badge.name,
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF0F172A),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 2,
                    ),
                    decoration: BoxDecoration(
                      color: _getRarityColor(badge.rarity)
                          .withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(
                      badge.rarity,
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                        color: _getRarityColor(badge.rarity),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),

              // 描述
              if (badge.description.isNotEmpty)
                Text(
                  badge.description,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF64748B),
                    height: 1.4,
                  ),
                ),
              const SizedBox(height: 16),

              // 解锁条件卡片
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: const Color(0xFFF8FAFC),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: const Color(0xFFE2E8F0)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text(
                          '解锁条件',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF334155),
                          ),
                        ),
                        Text(
                          isUnlocked
                              ? '已达成'
                              : '$currentProgress/$targetProgress',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: isUnlocked
                                ? const Color(0xFF16A34A)
                                : const Color(0xFF2563EB),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      badge.condition,
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        color: Color(0xFF64748B),
                      ),
                    ),
                    const SizedBox(height: 8),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: LinearProgressIndicator(
                        value: isUnlocked
                            ? 1.0
                            : (currentProgress / targetProgress).clamp(
                                0.0,
                                1.0,
                              ),
                        backgroundColor: const Color(0xFFE2E8F0),
                        valueColor: AlwaysStoppedAnimation<Color>(
                          isUnlocked
                              ? const Color(0xFF16A34A)
                              : const Color(0xFF2563EB),
                        ),
                        minHeight: 6,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // 佩戴 / 卸下操作按钮
              if (isUnlocked) ...[
                SizedBox(
                  width: double.infinity,
                  height: 46,
                  child: ElevatedButton(
                    onPressed: () {
                      Navigator.of(ctx).pop();
                      _toggleWear(badge, isWorn);
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: isWorn
                          ? const Color(0xFFF1F5F9)
                          : const Color(0xFF2563EB),
                      foregroundColor: isWorn
                          ? const Color(0xFF475569)
                          : Colors.white,
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(23),
                      ),
                    ),
                    child: Text(
                      isWorn ? '卸下当前勋章' : '佩戴在个人主页',
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 14,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
              ] else ...[
                Container(
                  width: double.infinity,
                  height: 46,
                  decoration: BoxDecoration(
                    color: const Color(0xFFF1F5F9),
                    borderRadius: BorderRadius.circular(23),
                  ),
                  alignment: Alignment.center,
                  child: const Text(
                    '未解锁（完成上方条件即可点亮）',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 13,
                      color: Color(0xFF64748B),
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  /// 佩戴/卸下：调新 wear 契约（带 action），成功后重新拉 state 刷新。
  Future<void> _toggleWear(_BadgeDefinition badge, bool isWorn) async {
    final previousWorn = _wornBadgeCode;
    setState(() => _wornBadgeCode = isWorn ? null : badge.code);

    final ok = isWorn
        ? await AchievementApi.wearBadge(null, action: 'UNWEAR')
        : await AchievementApi.wearBadge(badge.code, action: 'WEAR');
    if (!mounted) return;

    if (!ok) {
      setState(() => _wornBadgeCode = previousWorn);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('操作失败，请稍后重试'),
          backgroundColor: Color(0xFFEF4444),
          behavior: SnackBarBehavior.floating,
        ),
      );
      return;
    }

    await _refreshFromCache();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          isWorn ? '已卸下 ${badge.name}' : '已佩戴 ${badge.name}，将在社区和个人主页对外公开展示',
        ),
        backgroundColor: const Color(0xFF2563EB),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  Color _getRarityColor(String rarity) {
    switch (rarity) {
      case '稀有':
        return const Color(0xFF2563EB);
      case '史诗':
        return const Color(0xFF9333EA);
      case '传说':
        return const Color(0xFFD97706);
      default:
        return const Color(0xFF16A34A);
    }
  }

  _BadgeDefinition? get _wornBadge {
    final code = _wornBadgeCode;
    if (code == null) return null;
    for (final badge in _badges) {
      if (badge.code == code) return badge;
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _selectedCategoryCode.isEmpty
        ? _badges
        : _badges
              .where((badge) => badge.categoryCode == _selectedCategoryCode)
              .toList();

    final unlockedCount = _badges
        .where((badge) => _unlockedBadgeCodes.contains(badge.code))
        .length;
    final wornBadge = _wornBadge;

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(
            Icons.arrow_back_ios_new_rounded,
            size: 20,
            color: Color(0xFF0F172A),
          ),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: const Text(
          '荣誉勋章墙',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: Color(0xFF0F172A),
          ),
        ),
        centerTitle: true,
      ),
      body: Column(
        children: [
          // 1. 顶部总览统计与佩戴卡片
          GestureDetector(
            onTap: wornBadge == null ? null : () => _handleBadgeTap(wornBadge),
            child: Container(
              margin: const EdgeInsets.all(16),
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF1E293B), Color(0xFF0F172A)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(18),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x1F0F172A),
                    blurRadius: 16,
                    offset: Offset(0, 6),
                  ),
                ],
              ),
              child: Row(
                children: [
                  // 佩戴勋章
                  Container(
                    width: 64,
                    height: 64,
                    decoration: const BoxDecoration(
                      color: Color(0x1AFFFFFF),
                      shape: BoxShape.circle,
                    ),
                    alignment: Alignment.center,
                    child: wornBadge == null
                        ? const Icon(
                            Icons.military_tech_outlined,
                            size: 36,
                            color: Color(0xFF64748B),
                          )
                        : _badgeIcon(wornBadge, isUnlocked: true, size: 64),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Text(
                              '已佩戴勋章',
                              style: TextStyle(
                                fontFamily: 'Noto Sans SC',
                                fontSize: 12,
                                color: Color(0xFF64748B),
                              ),
                            ),
                            const SizedBox(width: 6),
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 6,
                                vertical: 1,
                              ),
                              decoration: BoxDecoration(
                                color: wornBadge == null
                                    ? const Color(0xFF475569)
                                    : const Color(0xFF2563EB),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Text(
                                wornBadge?.name ?? '未佩戴',
                                style: const TextStyle(
                                  fontFamily: 'Noto Sans SC',
                                  fontSize: 12,
                                  color: Colors.white,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Text(
                          '成就点亮进度: $unlockedCount / ${_badges.length}',
                          style: const TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 15,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                        const SizedBox(height: 6),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(3),
                          child: LinearProgressIndicator(
                            value: _badges.isEmpty
                                ? 0
                                : unlockedCount / _badges.length,
                            backgroundColor: const Color(0x33FFFFFF),
                            valueColor: const AlwaysStoppedAnimation<Color>(
                              Color(0xFF38BDF8),
                            ),
                            minHeight: 4,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),

          // 2. 分类横向切换胶囊条
          Container(
            height: 42,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: _categories.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final category = _categories[index];
                final isSelected = _selectedCategoryCode == category.code;
                return GestureDetector(
                  onTap: () =>
                      setState(() => _selectedCategoryCode = category.code),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14),
                    decoration: BoxDecoration(
                      color: isSelected
                          ? const Color(0xFF2563EB)
                          : Colors.white,
                      borderRadius: BorderRadius.circular(21),
                      border: Border.all(
                        color: isSelected
                            ? const Color(0xFF2563EB)
                            : const Color(0xFFE2E8F0),
                      ),
                    ),
                    alignment: Alignment.center,
                    child: Text(
                      category.name,
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        fontWeight: isSelected
                            ? FontWeight.bold
                            : FontWeight.normal,
                        color: isSelected
                            ? Colors.white
                            : const Color(0xFF475569),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
          const SizedBox(height: 10),

          // 3. 勋章网格列表
          Expanded(
            child: GridView.builder(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3,
                crossAxisSpacing: 10,
                mainAxisSpacing: 12,
                mainAxisExtent: 174,
              ),
              itemCount: filtered.length,
              itemBuilder: (context, index) {
                final badge = filtered[index];
                final isUnlocked = _unlockedBadgeCodes.contains(badge.code);
                final isWorn = _wornBadgeCode == badge.code;
                final currentProgress = _progressByCode[badge.code] ?? 0;
                final targetProgress = badge.targetProgress < 1
                    ? 1
                    : badge.targetProgress;

                return GestureDetector(
                  onTap: () => _handleBadgeTap(badge),
                  child: Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(
                        color: isWorn
                            ? const Color(0xFF2563EB)
                            : (isUnlocked
                                  ? const Color(0xFFE2E8F0)
                                  : const Color(0xFFF1F5F9)),
                        width: isWorn ? 1.5 : 1,
                      ),
                      boxShadow: [
                        BoxShadow(
                          color: isWorn
                              ? const Color(0x1F2563EB)
                              : const Color(0x080F172A),
                          blurRadius: 8,
                          offset: const Offset(0, 2),
                        ),
                      ],
                    ),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        // 勋章图片
                        Stack(
                          clipBehavior: Clip.none,
                          alignment: Alignment.center,
                          children: [
                            _badgeIcon(badge, isUnlocked: isUnlocked, size: 58),
                            if (!isUnlocked)
                              Container(
                                width: 22,
                                height: 22,
                                decoration: const BoxDecoration(
                                  color: Color(0xCC0F172A),
                                  shape: BoxShape.circle,
                                ),
                                child: const Icon(
                                  Icons.lock_rounded,
                                  size: 12,
                                  color: Colors.white,
                                ),
                              ),
                            if (isWorn)
                              Positioned(
                                top: -4,
                                right: -4,
                                child: Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 4,
                                    vertical: 1,
                                  ),
                                  decoration: BoxDecoration(
                                    color: const Color(0xFF2563EB),
                                    borderRadius: BorderRadius.circular(6),
                                  ),
                                  child: const Text(
                                    '佩戴',
                                    style: TextStyle(
                                      fontFamily: 'Noto Sans SC',
                                      fontSize: 12,
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ),
                              ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        Text(
                          badge.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: isUnlocked
                                ? const Color(0xFF20221F)
                                : const Color(0xFF64748B),
                          ),
                        ),
                        const SizedBox(height: 3),
                        Text(
                          badge.rarity,
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 10,
                            color: _getRarityColor(badge.rarity),
                          ),
                        ),
                        const SizedBox(height: 7),
                        Text(
                          isUnlocked
                              ? '已解锁'
                              : badge.condition,
                          maxLines: 2,
                          textAlign: TextAlign.center,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 10,
                            height: 1.4,
                            color: Color(0xFF7B7B70),
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
