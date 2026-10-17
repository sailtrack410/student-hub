import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';
import 'evaluation_sheet.dart';
import 'work_order_detail_page.dart';

/// APP-我的申请 (对应画布 5:1308 节点，375 × 812)
/// 严格依据 Ardot 设计规范实现工单状态筛选、工单卡片、催单提醒、进度跟踪与验收评价联动
class MyRequestsPage extends StatefulWidget {
  final int initialFilterIndex;

  const MyRequestsPage({
    super.key,
    this.initialFilterIndex = 1, // 默认进入“处理中 (1)”激活态，与画布 5:1318 严格保持一致
  });

  @override
  State<MyRequestsPage> createState() => _MyRequestsPageState();
}

class _MyRequestsPageState extends State<MyRequestsPage> {
  late int _selectedFilterIndex;
  final Set<String> _urgedOrderIds = {};

  final List<_FilterTabItem> _filterTabs = const [
    _FilterTabItem('全部', 3),
    _FilterTabItem('处理中', 1),
    _FilterTabItem('待验收', 1),
    _FilterTabItem('已办结', 1),
  ];

  @override
  void initState() {
    super.initState();
    _selectedFilterIndex = widget.initialFilterIndex;
  }

  void _handleUrge(String orderNo) {
    if (_urgedOrderIds.contains(orderNo)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('已催促过师傅，后勤调度中心已标记为加急件！'),
          duration: Duration(seconds: 1),
          behavior: SnackBarBehavior.floating,
        ),
      );
      return;
    }
    setState(() => _urgedOrderIds.add(orderNo));
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: [
            const Icon(Icons.notifications_active_rounded, color: Colors.white, size: 18),
            const SizedBox(width: 8),
            Text('已向师傅发送 $orderNo 催单加急通知！'),
          ],
        ),
        backgroundColor: AppColors.brand,
        duration: const Duration(seconds: 2),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  void _openDetail(String orderNo, String location, String category, String status) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => WorkOrderDetailPage(
          orderNo: orderNo,
          location: location,
          category: category,
          status: status,
        ),
      ),
    );
  }

  void _openEvaluation(String orderNo, String location) async {
    final result = await EvaluationBottomSheet.show(
      context,
      orderNo: orderNo,
      location: location,
    );
    if (result == true && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('感谢您的评价！工单已归档结案。'),
          backgroundColor: Color(0xFF059669),
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  void _showReceiptDialog(String orderNo, String location, String title) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        titlePadding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
        contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
        title: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: const Color(0xFFECFDF5),
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(Icons.verified_rounded, color: Color(0xFF059669), size: 20),
            ),
            const SizedBox(width: 10),
            const Text(
              '服务结案回执',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: Color(0xFF0F172A),
              ),
            ),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Divider(height: 16, color: Color(0xFFF1F5F9)),
            _buildReceiptRow('工单编号', orderNo),
            const SizedBox(height: 6),
            _buildReceiptRow('报修项目', title),
            const SizedBox(height: 6),
            _buildReceiptRow('服务地点', location),
            const SizedBox(height: 6),
            _buildReceiptRow('完成时间', '2026-10-15 16:40'),
            const SizedBox(height: 6),
            _buildReceiptRow('检修师傅', '赵师傅 (后勤电力维修组)'),
            const SizedBox(height: 6),
            _buildReceiptRow('服务评分', '★★★★★ 5.0 分'),
            const Divider(height: 16, color: Color(0xFFF1F5F9)),
            const Text(
              '注：凭证已电子签章防伪，保修期为 90 天。',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 11,
                color: Color(0xFF94A3B8),
              ),
            ),
          ],
        ),
        actions: [
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(),
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF059669),
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
            ),
            child: const Text('关 闭'),
          ),
        ],
      ),
    );
  }

  Widget _buildReceiptRow(String label, String value) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 64,
          child: Text(
            label,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
            ),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: Color(0xFF0F172A),
            ),
          ),
        ),
      ],
    );
  }

  void _showFilterSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        padding: const EdgeInsets.all(20),
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              '快速筛选工单',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 14),
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: List.generate(_filterTabs.length, (idx) {
                final tab = _filterTabs[idx];
                final isSelected = _selectedFilterIndex == idx;
                return ChoiceChip(
                  label: Text('${tab.label} (${tab.count})'),
                  selected: isSelected,
                  selectedColor: const Color(0xFFEFF6FF),
                  labelStyle: TextStyle(
                    color: isSelected ? const Color(0xFF2563EB) : const Color(0xFF64748B),
                    fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                  ),
                  onSelected: (val) {
                    if (val) {
                      setState(() => _selectedFilterIndex = idx);
                      Navigator.of(ctx).pop();
                    }
                  },
                );
              }),
            ),
            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            // 1. 顶部导航栏 (节点 5:1309: 375 × 88)
            Container(
              color: Colors.white,
              padding: const EdgeInsets.only(left: 12, right: 12, top: 10, bottom: 10),
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(Icons.arrow_back_ios_new_rounded, size: 20, color: Color(0xFF0F172A)),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                  const Expanded(
                    child: Center(
                      child: Text(
                        '我的申请',
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 17,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF0F172A),
                        ),
                      ),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.filter_alt_outlined, size: 22, color: Color(0xFF475569)),
                    onPressed: _showFilterSheet,
                  ),
                ],
              ),
            ),

            // 2. 状态筛选栏 (节点 5:1315: 375 × 48, 包含 全部/处理中/待验收/已办结 4 个胶囊)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              decoration: const BoxDecoration(
                color: Colors.white,
                border: Border(
                  bottom: BorderSide(color: Color(0xFFF1F5F9), width: 1),
                ),
              ),
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                physics: const BouncingScrollPhysics(),
                child: Row(
                  children: List.generate(_filterTabs.length, (index) {
                    final tab = _filterTabs[index];
                    final isSelected = _selectedFilterIndex == index;
                    final isPendingEval = index == 2; // 待验收专有琥珀色提示

                    Color bgColor;
                    Color textColor;

                    if (isSelected) {
                      bgColor = const Color(0xFF2563EB);
                      textColor = Colors.white;
                    } else if (isPendingEval) {
                      bgColor = const Color(0xFFFEF3C7);
                      textColor = const Color(0xFFD97706);
                    } else {
                      bgColor = const Color(0xFFF1F5F9);
                      textColor = const Color(0xFF64748B);
                    }

                    return Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: GestureDetector(
                        onTap: () => setState(() => _selectedFilterIndex = index),
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 160),
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                          decoration: BoxDecoration(
                            color: bgColor,
                            borderRadius: BorderRadius.circular(16),
                          ),
                          child: Text(
                            '${tab.label} (${tab.count})',
                            style: TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 12,
                              fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
                              color: textColor,
                            ),
                          ),
                        ),
                      ),
                    );
                  }),
                ),
              ),
            ),

            // 3. 工单列表区 (节点 5:1324)
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                children: [
                  // 卡片 1: 处理中工单 (节点 5:1325)
                  if (_selectedFilterIndex == 0 || _selectedFilterIndex == 1)
                    _buildProcessingCard(),

                  // 卡片 2: 待验收工单 (节点 5:1342)
                  if (_selectedFilterIndex == 0 || _selectedFilterIndex == 2)
                    _buildPendingEvaluationCard(),

                  // 卡片 3: 已办结工单 (节点 5:1357)
                  if (_selectedFilterIndex == 0 || _selectedFilterIndex == 3)
                    _buildCompletedCard(),

                  const SizedBox(height: 16),

                  // 底部说明 (节点 5:1368)
                  const Center(
                    child: Text(
                      '— 仅展示近 90 天内服务申请 —',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        color: Color(0xFF94A3B8),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 卡片 1: 处理中工单 (节点 5:1325)
  Widget _buildProcessingCard() {
    const orderNo = 'WO-20261017-0842';
    final hasUrged = _urgedOrderIds.contains(orderNo);

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE0E7FF)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x060F172A),
            blurRadius: 10,
            offset: Offset(0, 4),
          ),
        ],
      ),
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                '教室公共设施报修 · 多媒体',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: Color(0xFF64748B),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFFEFF6FF),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Text(
                  '维修处理中',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF2563EB),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          const Text(
            '明德楼 (教学楼A栋) · 302 多媒体教室',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: Color(0xFF0F172A),
            ),
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: const Color(0xFFF8FAFC),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '14:15 · 现场检修中',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF2563EB),
                  ),
                ),
                SizedBox(height: 3),
                Text(
                  '王建国师傅正在排查主板电源并更换备件',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF64748B),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 10),
          const Text(
            '报修人：李航 · 138****6821 · 承诺 24h 办结',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF94A3B8),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Flexible(
                child: FittedBox(
                  fit: BoxFit.scaleDown,
                  alignment: Alignment.centerLeft,
                  child: Text(
                    '工单: WO-20261017-0842',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 11,
                      color: Color(0xFF94A3B8),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  OutlinedButton(
                    onPressed: () => _handleUrge(orderNo),
                    style: OutlinedButton.styleFrom(
                      backgroundColor: const Color(0xFFF1F5F9),
                      side: BorderSide.none,
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      minimumSize: const Size(0, 30),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
                    ),
                    child: Text(
                      hasUrged ? '已催单' : '催单提醒',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 11,
                        color: hasUrged ? const Color(0xFF94A3B8) : const Color(0xFF334155),
                      ),
                    ),
                  ),
                  const SizedBox(width: 6),
                  ElevatedButton(
                    onPressed: () => _openDetail(
                      'WO-20261017-0842',
                      '明德楼 (教学楼A栋) · 302 多媒体教室',
                      '多媒体投影专项',
                      '维修处理中',
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF2563EB),
                      foregroundColor: Colors.white,
                      elevation: 0,
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                      minimumSize: const Size(0, 30),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
                    ),
                    child: const Text(
                      '查看进度 >',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  /// 卡片 2: 待验收工单 (节点 5:1342)
  Widget _buildPendingEvaluationCard() {
    const orderNo = 'WO-20261017-0619';
    const loc = '海棠苑 4栋 · 208 宿舍水龙头';

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFFED7AA)), // 专属暖琥珀边框
        boxShadow: const [
          BoxShadow(
            color: Color(0x060F172A),
            blurRadius: 10,
            offset: Offset(0, 4),
          ),
        ],
      ),
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                '宿舍生活设施报修 · 水暖',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: Color(0xFF64748B),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFFFEF3C7),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Text(
                  '待验收评价',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFFD97706),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          const Text(
            loc,
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: Color(0xFF0F172A),
            ),
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: const Color(0xFFFFFBEB),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '12:30 · 师傅已拍照上传完工凭证',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFFD97706),
                  ),
                ),
                SizedBox(height: 3),
                Text(
                  '洗手池龙头已更换完毕，无渗漏，等待报修人确认',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF92400E),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 10),
          const Text(
            '报修人：李航 · 138****6821 · 今天 10:20 提交',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF94A3B8),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Flexible(
                child: FittedBox(
                  fit: BoxFit.scaleDown,
                  alignment: Alignment.centerLeft,
                  child: Text(
                    '工单: WO-20261017-0619',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 11,
                      color: Color(0xFF94A3B8),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              ElevatedButton(
                onPressed: () => _openEvaluation(orderNo, loc),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFFD97706),
                  foregroundColor: Colors.white,
                  elevation: 0,
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                  minimumSize: const Size(0, 32),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                ),
                child: const Text(
                  '去验收评价',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  /// 卡片 3: 已办结工单 (节点 5:1357)
  Widget _buildCompletedCard() {
    const orderNo = 'WO-20261015-0328';
    const loc = '图书馆 2楼 · 社科阅览室 08号自习桌';

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFF1F5F9)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x060F172A),
            blurRadius: 10,
            offset: Offset(0, 4),
          ),
        ],
      ),
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                '图书馆公共设施 · 电力',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: Color(0xFF64748B),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFFECFDF5),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Text(
                  '已办结',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF059669),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          const Text(
            loc,
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: Color(0xFF0F172A),
            ),
          ),
          const SizedBox(height: 10),
          const Text(
            '已于 10月15日 16:40 办结 · 报修人评分 5.0 分',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Flexible(
                child: FittedBox(
                  fit: BoxFit.scaleDown,
                  alignment: Alignment.centerLeft,
                  child: Text(
                    '工单: WO-20261015-0328',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 11,
                      color: Color(0xFF94A3B8),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              OutlinedButton(
                onPressed: () => _showReceiptDialog(orderNo, loc, '自习桌排插电源检修'),
                style: OutlinedButton.styleFrom(
                  backgroundColor: const Color(0xFFF1F5F9),
                  side: BorderSide.none,
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  minimumSize: const Size(0, 32),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                ),
                child: const Text(
                  '查看服务回执',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF334155),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _FilterTabItem {
  final String label;
  final int count;

  const _FilterTabItem(this.label, this.count);
}
