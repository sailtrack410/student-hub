import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/achievement_api.dart';
import '../../api/auth_service.dart';
import '../../api/profile_api.dart';
import '../../models/user_session.dart';
import '../../services/recently_viewed_store.dart';
import '../../theme/app_theme.dart';
import '../../widgets/badge_icon.dart';
import '../../widgets/user_avatar.dart';
import '../schedule/grade_query_page.dart';
import '../schedule/schedule_page.dart';
import '../service/my_requests_page.dart';
import 'account_center_page.dart';
import 'badge_wall_page.dart';
import 'browse_history_page.dart';
import 'electricity_query_page.dart';
import 'help_center_page.dart';
import 'jw_auth_page.dart';
import 'settings_security_page.dart';

class ProfilePage extends StatefulWidget {
  final UserSession session;
  final AuthService authService;
  final ValueChanged<int>? onTabChanged;
  final int currentTabIndex;

  const ProfilePage({
    super.key,
    required this.session,
    required this.authService,
    this.onTabChanged,
    this.currentTabIndex = 4,
  });

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  static const _ink = Color(0xFF20221F);
  static const _muted = Color(0xFF7B7B70);
  static const _paper = Color(0xFFF6F7F9);
  static const _gold = Color(0xFFB59A62);
  static const _builtInBadges = <(String, String)>[
    ('COMMUNITY_FIRST_POST', '破冰首帖'),
    ('COMMUNITY_POST_10', '社区常客'),
    ('COMMUNITY_POST_50', '校园创作者'),
    ('COMMUNITY_COMMENT_20', '热忱交流'),
    ('COMMUNITY_LIKES_50', '人气新星'),
    ('COMMUNITY_HOT_POST', '广场焦点'),
    ('SERVICE_FIRST_REQUEST', '初试身手'),
    ('SERVICE_COMPLETED_5', '办事通达'),
    ('SERVICE_EVALUATION_5', '质检先锋'),
    ('SERVICE_FIVE_STAR_10', '满意信使'),
    ('JW_FIRST_AUTH', '实名认证'),
    ('SCHEDULE_FIRST_IMPORT', '课表启航'),
    ('PROFILE_COMPLETE', '完美档案'),
    ('LEVEL_3', '进阶三级'),
    ('LEVEL_5', '骨干五级'),
    ('LEVEL_7', '领航七级'),
    ('LEVEL_9', '巅峰九级'),
    ('WORKER_FIRST_ORDER', '匠心起步'),
    ('WORKER_ORDER_20', '校园卫士'),
  ];

  bool _loading = true;
  Map<String, dynamic>? _me;
  Map<String, dynamic>? _stats;
  Map<String, dynamic>? _author;
  Map<String, dynamic>? _achievement;
  List<(String, String)> _badges = _builtInBadges;
  Set<String> _unlocked = const {};

  @override
  void initState() {
    super.initState();
    UserSession.currentUserId = widget.session.userId;
    UserSession.currentUserName = widget.session.displayName;
    _load();
  }

  Future<void> _load() async {
    final results = await Future.wait<Object?>([
      ProfileApi.fetchMe(),
      ProfileApi.fetchSocialStats(),
      ProfileApi.fetchAuthorProfile(widget.session.userId),
      AchievementApi.fetchProfile(),
      AchievementApi.fetchCatalog(),
      AchievementApi.fetchState(),
    ]);
    if (!mounted) return;
    final achievement = results[3] as Map<String, dynamic>?;
    final catalog = results[4] as Map<String, dynamic>?;
    final state = results[5] as Map<String, dynamic>?;
    final badges = <(String, String)>[];
    final rawBadges = catalog?['badges'];
    if (rawBadges is List) {
      for (final raw in rawBadges.whereType<Map>()) {
        final code = raw['badgeCode']?.toString() ?? '';
        if (code.isEmpty) continue;
        badges.add((code, raw['badgeName']?.toString() ?? code));
      }
    }
    if (badges.isEmpty) badges.addAll(_builtInBadges);
    final unlocked = <String>{};
    for (final raw
        in (state?['activated'] as List? ?? const []).whereType<Map>()) {
      final code = raw['badgeCode']?.toString() ?? '';
      if (code.isNotEmpty) unlocked.add(code);
    }
    for (final raw
        in (achievement?['recentBadges'] as List? ?? const [])
            .whereType<Map>()) {
      final code = raw['badgeCode']?.toString() ?? '';
      if (code.isEmpty) continue;
      unlocked.add(code);
      if (!badges.any((badge) => badge.$1 == code)) {
        badges.add((code, raw['badgeName']?.toString() ?? code));
      }
    }
    setState(() {
      _me = results[0] as Map<String, dynamic>?;
      _stats = results[1] as Map<String, dynamic>?;
      _author = results[2] as Map<String, dynamic>?;
      _achievement = achievement;
      _badges = badges;
      _unlocked = unlocked;
      _loading = false;
    });
  }

  String get _displayName {
    final user = _me?['user'];
    final name = user is Map<String, dynamic>
        ? user['displayName']?.toString()
        : _me?['displayName']?.toString();
    return name == null || name.isEmpty
        ? (widget.session.displayName.isEmpty
              ? '同学'
              : widget.session.displayName)
        : name;
  }

  String? get _avatarPath {
    final path = _me?['avatarPath']?.toString();
    return path == null || path.isEmpty ? widget.session.avatarPath : path;
  }

  String get _uid {
    final growth = _me?['growth'];
    final uid = growth is Map<String, dynamic>
        ? growth['uid']?.toString()
        : null;
    return uid == null || uid.isEmpty ? widget.session.userId : uid;
  }

  String get _academicLine {
    final author = _author;
    if (author == null) return '学籍信息待完善';
    final parts = <String>[
      author['college']?.toString() ?? '',
      author['major']?.toString() ?? '',
      author['className']?.toString() ?? '',
    ].where((part) => part.isNotEmpty && !part.endsWith('未填写')).toList();
    return parts.isEmpty ? '学籍信息待完善' : parts.join(' · ');
  }

  int get _level {
    final value = _achievement?['level'] ?? _author?['level'];
    return value is num ? value.toInt() : int.tryParse('$value') ?? 1;
  }

  int get _badgeCount {
    final value = _achievement?['badgeCount'];
    if (value is num) return value.toInt();
    return int.tryParse('$value') ?? _unlocked.length;
  }

  int? get _totalBadgeCount {
    final value = _achievement?['totalBadgeCount'];
    return value is num ? value.toInt() : int.tryParse('$value');
  }

  String? get _wornCode {
    final code = _author?['wornBadgeCode'] ?? _achievement?['wornBadgeCode'];
    return code == null || '$code'.isEmpty ? null : '$code';
  }

  int _stat(String key) {
    final value = _stats?[key];
    return value is num ? value.toInt() : int.tryParse('$value') ?? 0;
  }

  void _copyUid() {
    Clipboard.setData(ClipboardData(text: _uid));
    ScaffoldMessenger.of(context)
        .showSnackBar(const SnackBar(content: Text('UID 已复制')));
  }

  void _openSettings() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => SettingsSecurityPage(authService: widget.authService),
      ),
    );
  }

  Future<void> _openAccountCenter() async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => AccountCenterPage(
          authService: widget.authService,
          initialMe: _me,
          initialAuthor: _author,
        ),
      ),
    );
    if (mounted) _load();
  }

  void _openBadgeWall() {
    Navigator.of(context)
        .push(MaterialPageRoute(builder: (_) => const BadgeWallPage()));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _paper,
      body: SafeArea(
        bottom: false,
        child: RefreshIndicator(
          onRefresh: _load,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(
              parent: BouncingScrollPhysics(),
            ),
            padding: EdgeInsets.zero,
            children: [
              _buildIdentity(),
              _buildBadgeRoom(),
              _buildCampusServices(),
              _buildCommunity(),
              _buildAccount(),
              if (_loading)
                const Padding(
                  padding: EdgeInsets.only(bottom: 24),
                  child: Center(
                    child: SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildIdentity() {
    return Container(
      decoration: const BoxDecoration(
        image: DecorationImage(
          image: AssetImage(
            'assets/images/home/home-sky-campus-background.jpg',
          ),
          fit: BoxFit.cover,
          alignment: Alignment(0, -0.25),
        ),
      ),
      child: ColoredBox(
        color: const Color(0xFFF6F7F9).withValues(alpha: 0.68),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(22, 10, 18, 24),
          child: Column(
            children: [
              Row(
                children: [
                  const Text(
                    '个人中心',
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                      color: _ink,
                    ),
                  ),
                  const Spacer(),
                  IconButton(
                    onPressed: _openSettings,
                    icon: const Icon(
                      Icons.settings_outlined,
                      color: _ink,
                      size: 23,
                    ),
                    tooltip: '设置',
                  ),
                ],
              ),
              const SizedBox(height: 13),
              GestureDetector(
                onTap: _openAccountCenter,
                behavior: HitTestBehavior.opaque,
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(3),
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 2),
                        boxShadow: const [
                          BoxShadow(
                            color: Color(0x16000000),
                            blurRadius: 16,
                            offset: Offset(0, 5),
                          ),
                        ],
                      ),
                      child: UserAvatar(
                        name: _displayName,
                        avatarPath: _avatarPath,
                        size: 76,
                        baseUrl: widget.authService.client.baseUrl,
                        fontSize: 28,
                      ),
                    ),
                    const SizedBox(width: 17),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Wrap(
                            crossAxisAlignment: WrapCrossAlignment.center,
                            spacing: 9,
                            runSpacing: 5,
                            children: [
                              Text(
                                _displayName,
                                style: const TextStyle(
                                  fontSize: 25,
                                  fontWeight: FontWeight.w700,
                                  color: _ink,
                                ),
                              ),
                              Text(
                                'Lv.$_level',
                                style: const TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w600,
                                  color: Color(0xFF8A7041),
                                ),
                              ),
                              if (_wornCode != null)
                                BadgeIcon(code: _wornCode, size: 26),
                            ],
                          ),
                          const SizedBox(height: 8),
                          Text(
                            _academicLine,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 12,
                              height: 1.5,
                              color: _muted,
                            ),
                          ),
                          const SizedBox(height: 5),
                          GestureDetector(
                            onTap: _copyUid,
                            child: Text(
                              'UID  $_uid',
                              style: const TextStyle(
                                fontSize: 11,
                                letterSpacing: 0.4,
                                color: _muted,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 27),
              Row(
                children: [
                  _buildStat('获赞', _stat('likesCount')),
                  _buildStat('互关', _stat('mutualFollowCount')),
                  _buildStat('关注', _stat('followingCount')),
                  _buildStat('粉丝', _stat('followersCount')),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStat(String label, int value) {
    return Expanded(
      child: InkWell(
        onTap: _openAccountCenter,
        borderRadius: BorderRadius.circular(10),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Column(
            children: [
              Text(
                '$value',
                style: const TextStyle(
                  fontSize: 20,
                  height: 1.2,
                  fontWeight: FontWeight.w700,
                  color: _ink,
                ),
              ),
              const SizedBox(height: 4),
              Text(label, style: const TextStyle(fontSize: 11, color: _muted)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildBadgeRoom() {
    final recent = (_achievement?['recentBadges'] as List? ?? const [])
        .whereType<Map>()
        .map((badge) => badge['badgeCode']?.toString() ?? '')
        .where((code) => code.isNotEmpty)
        .take(3)
        .toList();
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      child: Material(
        color: const Color(0xFF171916),
        borderRadius: BorderRadius.circular(18),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: _openBadgeWall,
          borderRadius: BorderRadius.circular(18),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        '荣誉典藏室',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w600,
                          letterSpacing: 1.5,
                          color: Color(0xFFE0C995),
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        '成长等级与荣誉勋章  ·  $_badgeCount/${_totalBadgeCount ?? _badges.length}',
                        style: const TextStyle(fontSize: 11, color: _gold),
                      ),
                    ],
                  ),
                ),
                for (final code in recent)
                  Padding(
                    padding: const EdgeInsets.only(left: 2),
                    child: BadgeIcon(code: code, size: 46),
                  ),
                const SizedBox(width: 5),
                const Icon(
                  Icons.arrow_forward_ios_rounded,
                  size: 14,
                  color: _gold,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildCampusServices() {
    final isAuthorized =
        widget.session.authorizationStatus.toUpperCase() == 'AUTHORIZED';
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.only(left: 4, bottom: 8),
            child: Text(
              '校园事务与服务',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: _muted,
              ),
            ),
          ),
          _buildGroupCard([
            _buildMenuItem(
              title: '我的申请',
              icon: Icons.assignment_outlined,
              iconColor: AppColors.brand,
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const MyRequestsPage()),
              ),
            ),
            _buildMenuItem(
              title: '我的课表',
              icon: Icons.calendar_month_outlined,
              iconColor: AppColors.accentDeep,
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const SchedulePage()),
              ),
            ),
            _buildMenuItem(
              title: '成绩与绩点',
              icon: Icons.military_tech_outlined,
              iconColor: const Color(0xFF7C3AED),
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const GradeQueryPage()),
              ),
            ),
            _buildMenuItem(
              title: '教务实名认证',
              icon: Icons.verified_user_outlined,
              iconColor: const Color(0xFF0284C7),
              trailing: isAuthorized ? '已认证' : '去认证',
              onTap: () async {
                await Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const JwAuthPage()),
                );
                if (mounted) _load();
              },
            ),
            _buildMenuItem(
              title: '宿舍电费查询',
              icon: Icons.bolt_rounded,
              iconColor: const Color(0xFFD97706),
              showDivider: false,
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const ElectricityQueryPage()),
              ),
            ),
          ]),
        ],
      ),
    );
  }

  Widget _buildCommunity() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.only(left: 4, bottom: 8),
            child: Text(
              '社区足迹',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: _muted,
              ),
            ),
          ),
          _buildGroupCard([
            _buildMenuItem(
              title: '我的浏览历史',
              icon: Icons.history_rounded,
              iconColor: AppColors.brand,
              trailing: RecentlyViewedStore.count == 0
                  ? '暂无'
                  : '${RecentlyViewedStore.count} 条',
              showDivider: false,
              onTap: () async {
                await Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const BrowseHistoryPage()),
                );
                if (mounted) setState(() {});
              },
            ),
          ]),
        ],
      ),
    );
  }

  Widget _buildAccount() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.only(left: 4, bottom: 8),
            child: Text(
              '账号与管理',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: _muted,
              ),
            ),
          ),
          _buildGroupCard([
            _buildMenuItem(
              title: '账号中心',
              icon: Icons.manage_accounts_outlined,
              iconColor: AppColors.brand,
              onTap: _openAccountCenter,
            ),
            _buildMenuItem(
              title: '设置与账号安全',
              icon: Icons.security_outlined,
              iconColor: AppColors.accentDeep,
              onTap: _openSettings,
            ),
            _buildMenuItem(
              title: '帮助中心与反馈',
              icon: Icons.help_outline_rounded,
              iconColor: AppColors.textSecondary,
              showDivider: false,
              onTap: () => Navigator.of(
                context,
              ).push(MaterialPageRoute(builder: (_) => const HelpCenterPage())),
            ),
          ]),
        ],
      ),
    );
  }

  Widget _buildGroupCard(List<Widget> children) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.feedDivider),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(children: children),
    );
  }

  Widget _buildMenuItem({
    required String title,
    IconData? icon,
    Color? iconColor,
    String? trailing,
    bool showDivider = true,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      child: Container(
        constraints: const BoxConstraints(
          minHeight: AppSpacing.preferredTapTarget,
        ),
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.cardPadding,
          vertical: 10,
        ),
        decoration: BoxDecoration(
          border: showDivider
              ? const Border(bottom: BorderSide(color: AppColors.feedDivider))
              : null,
        ),
        child: Row(
          children: [
            if (icon != null) ...[
              Icon(icon, size: 19, color: iconColor ?? _muted),
              const SizedBox(width: AppSpacing.avatarTextGap),
            ],
            Expanded(
              child: Text(
                title,
                style: const TextStyle(
                  fontSize: AppTypography.itemTitleSize,
                  fontWeight: FontWeight.w500,
                  color: _ink,
                ),
              ),
            ),
            if (trailing != null)
              Text(
                trailing,
                style: const TextStyle(
                  fontSize: AppTypography.metaSize,
                  color: _muted,
                ),
              ),
            const SizedBox(width: AppSpacing.titleSummaryGap),
            const Icon(
              Icons.arrow_forward_ios_rounded,
              size: 13,
              color: _muted,
            ),
          ],
        ),
      ),
    );
  }
}
