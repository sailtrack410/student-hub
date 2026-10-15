import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import '../../api/auth_service.dart';
import '../../models/user_session.dart';
import '../../theme/app_theme.dart';
import '../login/login_page.dart';
import '../service/repair_apply_page.dart';
import '../service/service_hall_page.dart';

/// 智慧校园 · 移动端主页 (APP-首页)
/// 严格依据 Ardot 设计画布节点 APP-首页 (5:1) 375 × 812 真实尺寸与层级还原
/// 融入微动效、卡片触控弹性反馈与平滑转场
class HomePage extends StatefulWidget {
  final UserSession session;
  final AuthService authService;
  final int initialTabIndex;

  const HomePage({
    super.key,
    required this.session,
    required this.authService,
    this.initialTabIndex = 0,
  });

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with SingleTickerProviderStateMixin {
  late int _currentTabIndex;
  bool _isLiked = false;
  int _likeCount = 234;

  @override
  void initState() {
    super.initState();
    _currentTabIndex = widget.initialTabIndex;
  }

  // 8项常用办事服务数据定义（严格对齐画布 5:45 金刚区）
  final List<_ServiceItemData> _services = const [
    _ServiceItemData(
      title: '我的课表',
      icon: Icons.calendar_month_rounded,
      bgColor: Color(0xFFEFF6FF), // 浅蓝
      iconColor: Color(0xFF2563EB),
    ),
    _ServiceItemData(
      title: '宿舍报修',
      icon: Icons.handyman_rounded,
      bgColor: Color(0xFFFEF3C7), // 浅琥珀
      iconColor: Color(0xFFD97706),
    ),
    _ServiceItemData(
      title: '请假申请',
      icon: Icons.description_rounded,
      bgColor: Color(0xFFDCFCE7), // 浅绿
      iconColor: Color(0xFF16A34A),
    ),
    _ServiceItemData(
      title: '在读证明',
      icon: Icons.workspace_premium_rounded,
      bgColor: Color(0xFFF3E8FF), // 浅紫
      iconColor: Color(0xFF9333EA),
    ),
    _ServiceItemData(
      title: '场地申请',
      icon: Icons.location_on_rounded,
      bgColor: Color(0xFFE0F2FE), // 浅天蓝
      iconColor: Color(0xFF0284C7),
    ),
    _ServiceItemData(
      title: '校园咨询',
      icon: Icons.chat_bubble_rounded,
      bgColor: Color(0xFFFFE4E6), // 浅粉
      iconColor: Color(0xFFE11D48),
    ),
    _ServiceItemData(
      title: '教室报修',
      icon: Icons.meeting_room_rounded,
      bgColor: Color(0xFFCCFBF1), // 浅青
      iconColor: Color(0xFF0D9488),
    ),
    _ServiceItemData(
      title: '更多服务',
      icon: Icons.grid_view_rounded,
      bgColor: Color(0xFFF1F5F9), // 浅灰蓝
      iconColor: Color(0xFF475569),
    ),
  ];

  void _handleServiceTap(String title) {
    if (title == '教室报修') {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => const RepairApplyPage(title: '教室与公共区域报修'),
        ),
      );
      return;
    }
    if (title == '宿舍报修') {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => const RepairApplyPage(title: '宿舍设施故障报修'),
        ),
      );
      return;
    }
    if (title == '服务大厅' || title == '更多服务') {
      setState(() => _currentTabIndex = 1);
      return;
    }

    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('已进入「$title」功能'),
        duration: const Duration(seconds: 1),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  void _handleLikeToggle() {
    setState(() {
      _isLiked = !_isLiked;
      _likeCount += _isLiked ? 1 : -1;
    });
  }

  Future<void> _handleLogout() async {
    await widget.authService.logout();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      PageRouteBuilder(
        pageBuilder: (_, __, ___) => LoginPage(authService: widget.authService),
        transitionsBuilder: (_, animation, __, child) =>
            FadeTransition(opacity: animation, child: child),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;
    final isDesktop = size.width > 600;

    return Scaffold(
      backgroundColor: isDesktop ? const Color(0xFFF1F5F9) : const Color(0xFFF7FAF9),
      body: Center(
        child: Container(
          width: isDesktop ? 375 : double.infinity,
          height: isDesktop ? 812 : double.infinity,
          clipBehavior: isDesktop ? Clip.antiAlias : Clip.none,
          decoration: BoxDecoration(
            color: const Color(0xFFF7FAF9),
            borderRadius: isDesktop ? BorderRadius.circular(24) : BorderRadius.zero,
            border: isDesktop ? Border.all(color: const Color(0xFFE2E8F0), width: 1) : null,
          ),
          child: _currentTabIndex == 1
              ? ServiceHallPage(
                  currentTabIndex: _currentTabIndex,
                  onTabChanged: (index) {
                    if (index == 4) {
                      _showMyProfileSheet();
                      return;
                    }
                    setState(() => _currentTabIndex = index);
                  },
                )
              : Stack(
                  children: [
              // 1. 顶部双色氛围光晕（5:2 节点：375 × 240，线性渐变）
              Positioned(
                top: 0,
                left: 0,
                right: 0,
                height: 240,
                child: Container(
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        Color(0x2E2563EB), // 宝蓝 18%
                        Color(0x1414B8A6), // 青绿 8%
                        Color(0x00F7FAF9), // 渐隐完全透明
                      ],
                      stops: [0.0, 0.6, 1.0],
                    ),
                  ),
                ),
              ),

              // 2. 主页面可滚动内容区（保证多端弹性触控与平滑滚动体验）
              Positioned.fill(
                bottom: 74, // 为底栏留出位置
                child: SingleChildScrollView(
                  physics: const BouncingScrollPhysics(),
                  padding: const EdgeInsets.only(bottom: 24),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: 50),
                      // 头部问候行与搜索通知动作按钮
                      _buildHeaderSection(),

                      const SizedBox(height: 14),
                      // 校园广播条（5:25 节点）
                      _buildBroadcastBar(),

                      const SizedBox(height: 12),
                      // 课表日程胶囊（5:30 节点）
                      _buildScheduleCapsule(),

                      const SizedBox(height: 16),
                      // 常用办事服务标题行（5:42 节点）
                      _buildSectionHeader(
                        title: '常用办事服务',
                        actionText: '服务大厅 (7项) ›',
                        onAction: () => _handleServiceTap('服务大厅'),
                      ),

                      const SizedBox(height: 8),
                      // 服务金刚区 8 宫格（5:45 节点）
                      _buildServiceGrid(),

                      const SizedBox(height: 16),
                      // 校园新鲜事标题行（5:102 节点）
                      _buildSectionHeader(
                        title: '校园新鲜事 · 热门动态',
                        actionText: '进入社区广场 ›',
                        actionColor: AppColors.brand,
                        onAction: () => _handleServiceTap('社区广场'),
                      ),

                      const SizedBox(height: 8),
                      // 热帖精选卡（5:105 节点）
                      _buildTrendingCard(),

                      const SizedBox(height: 14),
                      // 待办引导卡（5:121 节点）
                      _buildPendingActionCard(),
                    ],
                  ),
                ),
              ),

              // 3. 悬浮底栏 TabBar（5:131 节点：375 × 84，液态毛玻璃与微动效）
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                height: 74,
                child: _buildBottomTabBar(),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// 头部问候与功能按钮（对齐 5:15, 5:16, 5:17, 5:21）
  Widget _buildHeaderSection() {
    final name = widget.session.displayName.isNotEmpty
        ? widget.session.displayName
        : '李同学';

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          // 左侧文本列
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '早安，$name',
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF0F172A),
                  height: 1.2,
                  letterSpacing: -0.2,
                ),
              ),
              const SizedBox(height: 3),
              const Text(
                '绵阳城市学院 · 周四 第8周',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 11,
                  fontWeight: FontWeight.w400,
                  color: Color(0xFF64748B),
                  height: 1.2,
                ),
              ),
            ],
          ).animate().fadeIn(duration: 350.ms).slideY(begin: -0.08, end: 0),

          // 右侧搜索与通知按钮
          Row(
            children: [
              _buildCircularActionBtn(
                icon: Icons.search_rounded,
                tooltip: '搜索',
                onTap: () => _handleServiceTap('全局搜索'),
              ),
              const SizedBox(width: 8),
              _buildCircularActionBtn(
                icon: Icons.notifications_none_rounded,
                tooltip: '通知中心',
                hasBadge: true,
                onTap: () => _handleServiceTap('消息通知'),
              ),
            ],
          ).animate().fadeIn(duration: 350.ms, delay: 50.ms).scale(begin: const Offset(0.9, 0.9), end: const Offset(1, 1)),
        ],
      ),
    );
  }

  Widget _buildCircularActionBtn({
    required IconData icon,
    required String tooltip,
    required VoidCallback onTap,
    bool hasBadge = false,
  }) {
    return _AnimatedPressButton(
      onTap: onTap,
      child: Container(
        width: 36,
        height: 36,
        decoration: BoxDecoration(
          color: Colors.white,
          shape: BoxShape.circle,
          boxShadow: const [
            BoxShadow(
              color: Color(0x0F0D1B33),
              offset: Offset(0, 2),
              blurRadius: 8,
            ),
          ],
        ),
        child: Stack(
          alignment: Alignment.center,
          children: [
            Icon(icon, size: 18, color: const Color(0xFF64748B)),
            if (hasBadge)
              Positioned(
                top: 7,
                right: 7,
                child: Container(
                  width: 6.5,
                  height: 6.5,
                  decoration: const BoxDecoration(
                    color: Color(0xFFEF4444),
                    shape: BoxShape.circle,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  /// 校园广播条（5:25 节点：343 × 34，圆角 17，#F0F6FF）
  Widget _buildBroadcastBar() {
    return Center(
      child: _AnimatedPressButton(
        onTap: () => _handleServiceTap('图书馆公告详情'),
        child: Container(
          width: 343,
          height: 34,
          padding: const EdgeInsets.symmetric(horizontal: 12),
          decoration: BoxDecoration(
            color: const Color(0xFFF0F5FF),
            borderRadius: BorderRadius.circular(17),
          ),
          child: Row(
            children: [
              // 喇叭图标（微动效：轻微缩放呼吸）
              const Icon(
                Icons.campaign_rounded,
                size: 16,
                color: AppColors.brand,
              )
                  .animate(onPlay: (c) => c.repeat(reverse: true))
                  .scale(begin: const Offset(0.92, 0.92), end: const Offset(1.08, 1.08), duration: 1000.ms),
              const SizedBox(width: 8),
              const Expanded(
                child: Text(
                  '【通知】本周五图书馆闭馆检修，请提前归还图书',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 11,
                    fontWeight: FontWeight.w400,
                    color: Color(0xFF334155),
                    height: 1.0,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ),
      ),
    ).animate().fadeIn(duration: 400.ms, delay: 100.ms).slideY(begin: 0.08, end: 0);
  }

  /// 课表日程胶囊（5:30 节点：343 × 66，圆角 18，白色浮动）
  Widget _buildScheduleCapsule() {
    return Center(
      child: _AnimatedPressButton(
        onTap: () => _handleServiceTap('完整课表'),
        child: Container(
          width: 343,
          height: 66,
          padding: const EdgeInsets.symmetric(horizontal: 14),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(18),
            boxShadow: const [
              BoxShadow(
                color: Color(0x0F0D1B33),
                offset: Offset(0, 4),
                blurRadius: 12,
              ),
            ],
          ),
          child: Row(
            children: [
              // 左侧日历图标容器
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: const Color(0xFFEFF6FF),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(
                  Icons.calendar_today_rounded,
                  size: 20,
                  color: AppColors.brand,
                ),
              ),
              const SizedBox(width: 12),

              // 中间课程信息
              const Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '下一节 · 大学英语 (四)',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 14,
                        fontWeight: FontWeight.bold,
                        color: Color(0xFF0F172A),
                        height: 1.2,
                      ),
                    ),
                    SizedBox(height: 3),
                    Text(
                      '08:00-09:35 · B202 阶梯教室',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 11,
                        fontWeight: FontWeight.w400,
                        color: Color(0xFF64748B),
                        height: 1.2,
                      ),
                    ),
                  ],
                ),
              ),

              // 右侧跳转
              const Text(
                '全部课表 ›',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: AppColors.brand,
                ),
              ),
            ],
          ),
        ),
      ),
    ).animate().fadeIn(duration: 450.ms, delay: 150.ms).slideY(begin: 0.08, end: 0);
  }

  /// 通用板块标题行（5:42, 5:102 节点：343 × 20）
  Widget _buildSectionHeader({
    required String title,
    required String actionText,
    Color? actionColor,
    required VoidCallback onAction,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Text(
            title,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 13,
              fontWeight: FontWeight.bold,
              color: Color(0xFF0F172A),
            ),
          ),
          GestureDetector(
            onTap: onAction,
            behavior: HitTestBehavior.opaque,
            child: Text(
              actionText,
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 11,
                fontWeight: FontWeight.w500,
                color: actionColor ?? const Color(0xFF64748B),
              ),
            ),
          ),
        ],
      ),
    ).animate().fadeIn(duration: 350.ms, delay: 200.ms);
  }

  /// 服务金刚区 8 宫格（5:45 节点：343 × 132，2行4列）
  Widget _buildServiceGrid() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: List.generate(4, (i) => _buildServiceTile(_services[i], i)),
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: List.generate(4, (i) => _buildServiceTile(_services[i + 4], i + 4)),
          ),
        ],
      ),
    );
  }

  Widget _buildServiceTile(_ServiceItemData item, int index) {
    return _AnimatedPressButton(
      onTap: () => _handleServiceTap(item.title),
      child: SizedBox(
        width: 76,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: item.bgColor,
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(
                item.icon,
                size: 22,
                color: item.iconColor,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              item.title,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 11,
                fontWeight: FontWeight.w500,
                color: Color(0xFF334155),
                height: 1.1,
              ),
              textAlign: TextAlign.center,
              maxLines: 1,
            ),
          ],
        ),
      ),
    )
        .animate()
        .fadeIn(duration: 350.ms, delay: (200 + index * 30).ms)
        .slideY(begin: 0.12, end: 0, curve: Curves.easeOutBack);
  }

  /// 热帖精选卡（5:105 节点：343 × 160，圆角 18，白色浮动）
  Widget _buildTrendingCard() {
    return Center(
      child: _AnimatedPressButton(
        onTap: () => _handleServiceTap('讨论详情'),
        child: Container(
          width: 343,
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(18),
            boxShadow: const [
              BoxShadow(
                color: Color(0x0F0D1B33),
                offset: Offset(0, 4),
                blurRadius: 12,
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 作者信息行
              Row(
                children: [
                  // 头像圆形
                  Container(
                    width: 24,
                    height: 24,
                    decoration: const BoxDecoration(
                      color: Color(0xFFFFE4E6),
                      shape: BoxShape.circle,
                    ),
                    alignment: Alignment.center,
                    child: const Text(
                      '李',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: Color(0xFFE11D48),
                        height: 1.0,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  const Text(
                    '李同学 · 计算机学院',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                      color: Color(0xFF334155),
                    ),
                  ),
                  const SizedBox(width: 6),
                  // Lv.4 徽章
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1.5),
                    decoration: BoxDecoration(
                      color: const Color(0xFFEFF6FF),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: const Text(
                      'Lv.4',
                      style: TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 9,
                        fontWeight: FontWeight.bold,
                        color: AppColors.brand,
                        height: 1.0,
                      ),
                    ),
                  ),
                  const Spacer(),
                  // 热门讨论标签
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: const Color(0xFFFEF3C7),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: const Text(
                      '热门讨论',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFFD97706),
                        height: 1.0,
                      ),
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 8),
              // 帖子标题
              const Text(
                '图书馆占座现象，大家怎么看？',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF0F172A),
                  height: 1.25,
                ),
              ),

              const SizedBox(height: 5),
              // 摘要文本
              const Text(
                '每天早上去自习室，很多位置放着书却一上午没人来，期末自习越来越紧张了，大家觉得应该怎么规范比较好？',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 11,
                  fontWeight: FontWeight.w400,
                  color: Color(0xFF64748B),
                  height: 1.4,
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),

              const SizedBox(height: 10),
              // 底部话题与互动数据
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: const Color(0xFFF3E8FF),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: const Text(
                      '# 校园生活',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 10,
                        fontWeight: FontWeight.normal,
                        color: Color(0xFF7E22CE),
                        height: 1.0,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  // 点赞微互动
                  Flexible(
                    child: FittedBox(
                      fit: BoxFit.scaleDown,
                      alignment: Alignment.centerRight,
                      child: GestureDetector(
                        onTap: _handleLikeToggle,
                        behavior: HitTestBehavior.opaque,
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              '浏览 1.2k · 评论 56 · ',
                              style: const TextStyle(
                                fontFamily: 'Noto Sans SC',
                                fontSize: 10,
                                color: Color(0xFF94A3B8),
                              ),
                            ),
                            AnimatedScale(
                              scale: _isLiked ? 1.2 : 1.0,
                              duration: const Duration(milliseconds: 150),
                              curve: Curves.easeOutBack,
                              child: Icon(
                                _isLiked ? Icons.favorite_rounded : Icons.favorite_border_rounded,
                                size: 12,
                                color: _isLiked ? const Color(0xFFEF4444) : const Color(0xFF94A3B8),
                              ),
                            ),
                            const SizedBox(width: 2),
                            Text(
                              '赞 $_likeCount',
                              style: TextStyle(
                                fontFamily: 'Noto Sans SC',
                                fontSize: 10,
                                color: _isLiked ? const Color(0xFFEF4444) : const Color(0xFF94A3B8),
                                fontWeight: _isLiked ? FontWeight.bold : FontWeight.normal,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    ).animate().fadeIn(duration: 450.ms, delay: 350.ms).slideY(begin: 0.08, end: 0);
  }

  /// 待办引导卡（5:121 节点：343 × 56，圆角 16，#F1F5FD）
  Widget _buildPendingActionCard() {
    return Center(
      child: _AnimatedPressButton(
        onTap: () => _handleServiceTap('教务学籍认证'),
        child: Container(
          width: 343,
          height: 56,
          padding: const EdgeInsets.symmetric(horizontal: 14),
          decoration: BoxDecoration(
            color: const Color(0xFFF1F5FD),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Row(
            children: [
              // 星光图标底
              Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  color: const Color(0xFFE8F0FC),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Icon(
                  Icons.auto_awesome_rounded,
                  size: 16,
                  color: AppColors.brand,
                ),
              ),
              const SizedBox(width: 10),

              // 文本列
              const Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '教务待办 · 待认证',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                        color: Color(0xFF0F172A),
                        height: 1.2,
                      ),
                    ),
                    SizedBox(height: 2),
                    Text(
                      '完成认证，解锁成绩与全学期课表',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 10,
                        fontWeight: FontWeight.w400,
                        color: Color(0xFF64748B),
                        height: 1.2,
                      ),
                    ),
                  ],
                ),
              ),

              // 去完成按钮（微光扫光微动效）
              Container(
                width: 62,
                height: 28,
                decoration: BoxDecoration(
                  color: AppColors.brand,
                  borderRadius: BorderRadius.circular(14),
                ),
                alignment: Alignment.center,
                child: const Text(
                  '去完成',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                    height: 1.0,
                  ),
                ),
              )
                  .animate(onPlay: (c) => c.repeat(period: 3.seconds))
                  .shimmer(duration: 1200.ms, color: Colors.white.withValues(alpha: 0.35)),
            ],
          ),
        ),
      ),
    ).animate().fadeIn(duration: 450.ms, delay: 400.ms).slideY(begin: 0.08, end: 0);
  }

  /// 悬浮底栏 TabBar（5:131 节点：375 × 74，半透明高斯模糊）
  Widget _buildBottomTabBar() {
    final tabs = const [
      _TabItemData('首页', Icons.home_rounded, Icons.home_outlined),
      _TabItemData('服务', Icons.grid_view_rounded, Icons.grid_view_outlined),
      _TabItemData('AI助手', Icons.auto_awesome_rounded, Icons.auto_awesome_outlined),
      _TabItemData('社区', Icons.forum_rounded, Icons.forum_outlined),
      _TabItemData('我的', Icons.person_rounded, Icons.person_outline_rounded),
    ];

    return Container(
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.96),
        border: const Border(
          top: BorderSide(
            color: Color(0xFFE2E8F0),
            width: 0.6,
          ),
        ),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: List.generate(tabs.length, (index) {
          final tab = tabs[index];
          final isActive = _currentTabIndex == index;

          return GestureDetector(
            onTap: () {
              if (index == 4) {
                // 点击「我的」支持退出登录切换测试
                _showMyProfileSheet();
                return;
              }
              setState(() => _currentTabIndex = index);
            },
            behavior: HitTestBehavior.opaque,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              curve: Curves.easeOutCubic,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  AnimatedScale(
                    scale: isActive ? 1.08 : 1.0,
                    duration: const Duration(milliseconds: 180),
                    child: Icon(
                      isActive ? tab.activeIcon : tab.inactiveIcon,
                      size: 22,
                      color: isActive ? AppColors.brand : const Color(0xFF64748B),
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    tab.label,
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 10,
                      fontWeight: isActive ? FontWeight.bold : FontWeight.w500,
                      color: isActive ? AppColors.brand : const Color(0xFF64748B),
                      height: 1.0,
                    ),
                  ),
                ],
              ),
            ),
          );
        }),
      ),
    );
  }

  /// 「我的」快速弹层（支持退出登录与账号信息预览）
  void _showMyProfileSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        padding: const EdgeInsets.all(24),
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: const BoxDecoration(
                    color: Color(0xFFFFE4E6),
                    shape: BoxShape.circle,
                  ),
                  alignment: Alignment.center,
                  child: const Text(
                    '李',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Color(0xFFE11D48)),
                  ),
                ),
                const SizedBox(width: 14),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.session.displayName.isNotEmpty ? widget.session.displayName : '李同学',
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Color(0xFF0F172A)),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '学号: ${widget.session.username} · 绵阳城市学院',
                      style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: () {
                Navigator.of(ctx).pop();
                _handleLogout();
              },
              icon: const Icon(Icons.logout_rounded, size: 18),
              label: const Text('退出当前账号并返回登录页'),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFFF1F5F9),
                foregroundColor: const Color(0xFFE11D48),
                elevation: 0,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                padding: const EdgeInsets.symmetric(vertical: 12),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 动效微交互包装组件：点击轻微下压回弹 (Scale press bounce)
class _AnimatedPressButton extends StatefulWidget {
  final Widget child;
  final VoidCallback onTap;

  const _AnimatedPressButton({
    required this.child,
    required this.onTap,
  });

  @override
  State<_AnimatedPressButton> createState() => _AnimatedPressButtonState();
}

class _AnimatedPressButtonState extends State<_AnimatedPressButton> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => setState(() => _isPressed = true),
      onTapUp: (_) {
        setState(() => _isPressed = false);
        widget.onTap();
      },
      onTapCancel: () => setState(() => _isPressed = false),
      behavior: HitTestBehavior.opaque,
      child: AnimatedScale(
        scale: _isPressed ? 0.95 : 1.0,
        duration: const Duration(milliseconds: 120),
        curve: Curves.easeInOut,
        child: widget.child,
      ),
    );
  }
}

class _ServiceItemData {
  final String title;
  final IconData icon;
  final Color bgColor;
  final Color iconColor;

  const _ServiceItemData({
    required this.title,
    required this.icon,
    required this.bgColor,
    required this.iconColor,
  });
}

class _TabItemData {
  final String label;
  final IconData activeIcon;
  final IconData inactiveIcon;

  const _TabItemData(this.label, this.activeIcon, this.inactiveIcon);
}
