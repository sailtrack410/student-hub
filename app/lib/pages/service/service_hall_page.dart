import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';
import 'my_requests_page.dart';
import 'repair_apply_page.dart';

/// APP-服务大厅 (对应画布 5:1191 节点，375 × 812)
/// 严格依据 Ardot 设计规范实现左侧多级分类、右侧服务卡片、搜索过滤与底栏导航
class ServiceHallPage extends StatefulWidget {
  final ValueChanged<int>? onTabChanged;
  final int currentTabIndex;

  const ServiceHallPage({
    super.key,
    this.onTabChanged,
    this.currentTabIndex = 1,
  });

  @override
  State<ServiceHallPage> createState() => _ServiceHallPageState();
}

class _ServiceHallPageState extends State<ServiceHallPage> {
  int _selectedCategoryIndex = 0;
  final TextEditingController _searchController = TextEditingController();
  String _searchKeyword = '';

  final List<_CategoryData> _categories = const [
    _CategoryData(
      name: '报修服务',
      items: [
        _ServiceCardData(
          title: '教室与公共区域报修',
          description: '教学楼教室、多媒体、桌椅、照明损坏快速报修',
          isRepair: true,
          routeTitle: '教室与公共区域报修',
        ),
        _ServiceCardData(
          title: '宿舍设施故障报修',
          description: '宿舍水电、门窗、空调风扇破损报修',
          isRepair: true,
          routeTitle: '宿舍设施故障报修',
        ),
      ],
    ),
    _CategoryData(
      name: '日常事务',
      items: [
        _ServiceCardData(
          title: '教室借用申请',
          description: '班级活动、学术讲座、排练等场地预约审批',
        ),
        _ServiceCardData(
          title: '学生请假申请',
          description: '日常病假事假、节假日离校审批',
        ),
      ],
    ),
    _CategoryData(
      name: '证明开具',
      items: [
        _ServiceCardData(
          title: '在读证明办理',
          description: '电子版在线即刻生成，支持官方防伪印章',
        ),
        _ServiceCardData(
          title: '成绩单打印申请',
          description: '教务中英双语成绩单申请与自助终端打印',
        ),
      ],
    ),
    _CategoryData(
      name: '咨询指引',
      items: [
        _ServiceCardData(
          title: '校园失物招领',
          description: '挂失发布、拾物登记与线下一站式认领',
        ),
        _ServiceCardData(
          title: '心理发展咨询',
          description: '心晴驿站 1对1 预约，专业心理教师支持',
        ),
      ],
    ),
  ];

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _openMyRequests() {
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => const MyRequestsPage()),
    );
  }

  void _openServiceItem(_ServiceCardData item) {
    if (item.isRepair) {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => RepairApplyPage(
            title: item.routeTitle ?? item.title,
          ),
        ),
      );
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('已进入「${item.title}」办理页面'),
          duration: const Duration(seconds: 1),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final currentCat = _categories[_selectedCategoryIndex];
    final displayItems = _searchKeyword.trim().isEmpty
        ? currentCat.items
        : _categories
            .expand((c) => c.items)
            .where((i) =>
                i.title.contains(_searchKeyword.trim()) ||
                i.description.contains(_searchKeyword.trim()))
            .toList();

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            // 1. 顶部导航栏 (节点 5:1192: 375 × 88, 标题与我的申请链接)
            Container(
              color: Colors.white,
              padding: const EdgeInsets.only(left: 16, right: 16, top: 12, bottom: 8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    '服务大厅',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF0F172A),
                    ),
                  ),
                  GestureDetector(
                    onTap: _openMyRequests,
                    behavior: HitTestBehavior.opaque,
                    child: const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 6, vertical: 6),
                      child: Text(
                        '我的申请 (2)',
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: Color(0xFF2563EB),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // 2. 搜索栏外框 (节点 5:1195: 高度 48, 搜索胶囊)
            Container(
              color: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
              child: Container(
                height: 38,
                decoration: BoxDecoration(
                  color: const Color(0xFFF1F5F9),
                  borderRadius: BorderRadius.circular(19),
                ),
                padding: const EdgeInsets.symmetric(horizontal: 12),
                child: Row(
                  children: [
                    const Icon(
                      Icons.search_rounded,
                      size: 18,
                      color: Color(0xFF94A3B8),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: TextField(
                        controller: _searchController,
                        onChanged: (val) => setState(() => _searchKeyword = val),
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 13,
                          color: Color(0xFF0F172A),
                        ),
                        decoration: const InputDecoration(
                          hintText: '搜索办事服务，如教室报修、在读证明...',
                          hintStyle: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 13,
                            color: Color(0xFF94A3B8),
                          ),
                          border: InputBorder.none,
                          isDense: true,
                          contentPadding: EdgeInsets.zero,
                        ),
                      ),
                    ),
                    if (_searchKeyword.isNotEmpty)
                      GestureDetector(
                        onTap: () {
                          _searchController.clear();
                          setState(() => _searchKeyword = '');
                        },
                        child: const Icon(
                          Icons.cancel_rounded,
                          size: 16,
                          color: Color(0xFF94A3B8),
                        ),
                      ),
                  ],
                ),
              ),
            ),

            // 3. 双栏内容区 (节点 5:1201: 左侧 85px 分类栏，右侧 290px 列表区)
            Expanded(
              child: Row(
                children: [
                  // 左侧分类栏 (节点 5:1202: 宽 85, 背景 #F1F5F9, 激活态纯白无缝衔接右侧)
                  Container(
                    width: 85,
                    color: const Color(0xFFF1F5F9),
                    child: ListView.builder(
                      padding: const EdgeInsets.only(top: 10),
                      itemCount: _categories.length,
                      physics: const ClampingScrollPhysics(),
                      itemBuilder: (context, index) {
                        final cat = _categories[index];
                        final isSelected = index == _selectedCategoryIndex && _searchKeyword.isEmpty;

                        return GestureDetector(
                          onTap: () {
                            if (_searchKeyword.isNotEmpty) {
                              _searchController.clear();
                              _searchKeyword = '';
                            }
                            setState(() => _selectedCategoryIndex = index);
                          },
                          behavior: HitTestBehavior.opaque,
                          child: AnimatedContainer(
                            duration: const Duration(milliseconds: 150),
                            height: 44,
                            color: isSelected ? Colors.white : const Color(0xFFF1F5F9),
                            alignment: Alignment.center,
                            child: Text(
                              cat.name,
                              style: TextStyle(
                                fontFamily: 'Noto Sans SC',
                                fontSize: 13,
                                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                                color: isSelected
                                    ? const Color(0xFF2563EB)
                                    : const Color(0xFF64748B),
                              ),
                            ),
                          ),
                        );
                      },
                    ),
                  ),

                  // 右侧服务列表区 (节点 5:1211)
                  Expanded(
                    child: Container(
                      color: Colors.white,
                      child: ListView(
                        padding: const EdgeInsets.all(12),
                        children: [
                          Padding(
                            padding: const EdgeInsets.only(bottom: 12, top: 4),
                            child: Text(
                              _searchKeyword.isEmpty
                                  ? '${currentCat.name} (${displayItems.length} 项可办)'
                                  : '搜索结果 (${displayItems.length} 项)',
                              style: const TextStyle(
                                fontFamily: 'Noto Sans SC',
                                fontSize: 14,
                                fontWeight: FontWeight.bold,
                                color: Color(0xFF0F172A),
                              ),
                            ),
                          ),
                          ...displayItems.map((item) => _buildServiceCard(item)),
                          if (displayItems.isEmpty)
                            Padding(
                              padding: const EdgeInsets.symmetric(vertical: 48),
                              child: Center(
                                child: Text(
                                  '未找到相关办事服务',
                                  style: TextStyle(
                                    fontFamily: 'Noto Sans SC',
                                    fontSize: 13,
                                    color: Colors.grey.shade400,
                                  ),
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // 4. 底栏 TabBar (节点 5:1282: 375 × 84, 服务Tab激活)
            _buildBottomTabBar(),
          ],
        ),
      ),
    );
  }

  Widget _buildServiceCard(_ServiceCardData item) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      child: GestureDetector(
        onTap: () => _openServiceItem(item),
        behavior: HitTestBehavior.opaque,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
          decoration: BoxDecoration(
            color: const Color(0xFFF8FAFC),
            border: Border.all(color: const Color(0xFFF1F5F9)),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                item.title,
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF0F172A),
                ),
              ),
              const SizedBox(height: 6),
              Text(
                item.description,
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: Color(0xFF64748B),
                  height: 1.4,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildBottomTabBar() {
    final tabs = const [
      _TabItem('首页', Icons.home_rounded, Icons.home_outlined),
      _TabItem('服务', Icons.grid_view_rounded, Icons.grid_view_outlined),
      _TabItem('AI助手', Icons.auto_awesome_rounded, Icons.auto_awesome_outlined),
      _TabItem('社区', Icons.chat_bubble_rounded, Icons.chat_bubble_outline_rounded),
      _TabItem('我的', Icons.person_rounded, Icons.person_outline_rounded),
    ];

    return Container(
      height: 74,
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(
          top: BorderSide(color: Color(0xFFE2E8F0), width: 0.5),
        ),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: List.generate(tabs.length, (index) {
          final tab = tabs[index];
          final isActive = widget.currentTabIndex == index;

          return GestureDetector(
            onTap: () {
              if (widget.onTabChanged != null) {
                widget.onTabChanged!(index);
              } else if (index == 0) {
                Navigator.of(context).pop();
              }
            },
            behavior: HitTestBehavior.opaque,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    isActive ? tab.activeIcon : tab.inactiveIcon,
                    size: 22,
                    color: isActive ? AppColors.brand : const Color(0xFF64748B),
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
}

class _CategoryData {
  final String name;
  final List<_ServiceCardData> items;

  const _CategoryData({
    required this.name,
    required this.items,
  });
}

class _ServiceCardData {
  final String title;
  final String description;
  final bool isRepair;
  final String? routeTitle;

  const _ServiceCardData({
    required this.title,
    required this.description,
    this.isRepair = false,
    this.routeTitle,
  });
}

class _TabItem {
  final String label;
  final IconData activeIcon;
  final IconData inactiveIcon;

  const _TabItem(this.label, this.activeIcon, this.inactiveIcon);
}
