import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import '../../theme/app_theme.dart';
import 'evaluation_sheet.dart';

/// APP-工单流转详情 (对应画布 5:1096 节点，375 × 812)
class WorkOrderDetailPage extends StatefulWidget {
  final String orderNo;
  final String location;
  final String category;
  final String status;

  const WorkOrderDetailPage({
    super.key,
    this.orderNo = 'WO-20261017-0842',
    this.location = '明德楼 (教学楼A栋) · 302 多媒体教室',
    this.category = '多媒体投影专项',
    this.status = '维修处理中',
  });

  @override
  State<WorkOrderDetailPage> createState() => _WorkOrderDetailPageState();
}

class _WorkOrderDetailPageState extends State<WorkOrderDetailPage> {
  bool _reminded = false;

  void _handleUrge() {
    if (_reminded) return;
    setState(() => _reminded = true);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Row(
          children: [
            Icon(Icons.notifications_active_rounded, color: Colors.white, size: 20),
            SizedBox(width: 8),
            Text('已向王师傅发送催单加急通知！'),
          ],
        ),
        backgroundColor: AppColors.brand,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  void _handleCall() {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Text('正在为您转接王师傅：139 8168 5210'),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  void _openEvaluation() async {
    final result = await EvaluationBottomSheet.show(
      context,
      orderNo: widget.orderNo,
      location: widget.location,
    );
    if (result == true && mounted) {
      Navigator.of(context).pop(); // 评价完返回
    }
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;
    final isDesktopOrWeb = kIsWeb || size.width > 450;

    return Scaffold(
      backgroundColor: isDesktopOrWeb ? const Color(0xFFF1F5F9) : const Color(0xFFF7FAF9),
      body: Center(
        child: Container(
          width: 375,
          height: 812,
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            color: const Color(0xFFF7FAF9),
            borderRadius: isDesktopOrWeb ? BorderRadius.circular(40) : BorderRadius.zero,
            boxShadow: [
              if (isDesktopOrWeb)
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.12),
                  blurRadius: 36,
                  offset: const Offset(0, 12),
                ),
            ],
          ),
          child: Column(
            children: [
              // 1. 顶部导航栏 (5:1097 节点：375 × 56)
              Container(
                padding: const EdgeInsets.only(top: 44, left: 12, right: 12, bottom: 10),
                color: Colors.white,
                child: Row(
                  children: [
                    IconButton(
                      icon: const Icon(Icons.arrow_back_ios_new_rounded, size: 18),
                      onPressed: () => Navigator.of(context).pop(),
                    ),
                    const Expanded(
                      child: Text(
                        '工单流转详情',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF0F172A),
                        ),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.share_outlined, size: 20, color: Color(0xFF64748B)),
                      onPressed: () {},
                    ),
                  ],
                ),
              ),

              // 2. 主内容滑动区
              Expanded(
                child: SingleChildScrollView(
                  physics: const BouncingScrollPhysics(),
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      // 卡片1: 工单概要卡片 (5:1108 节点)
                      _buildSummaryCard(),
                      const SizedBox(height: 12),

                      // 卡片2: 维修师傅信息卡片 (5:1116 节点)
                      _buildTechnicianCard(),
                      const SizedBox(height: 12),

                      // 卡片3: 时间轴节点卡片 (5:1125 节点)
                      _buildTimelineCard(),
                    ],
                  ),
                ),
              ),

              // 3. 底部操作行 (5:1139 节点：375 × 68)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: const BoxDecoration(
                  color: Colors.white,
                  border: Border(top: BorderSide(color: Color(0xFFE2E8F0), width: 0.8)),
                ),
                child: Row(
                  children: [
                    Expanded(
                      flex: 2,
                      child: SizedBox(
                        height: 44,
                        child: OutlinedButton(
                          onPressed: _reminded ? null : _handleUrge,
                          style: OutlinedButton.styleFrom(
                            backgroundColor: const Color(0xFFF8FAFC),
                            foregroundColor: const Color(0xFF334155),
                            side: const BorderSide(color: Color(0xFFCBD5E1)),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
                          ),
                          child: Text(
                            _reminded ? '已提醒催单' : '催单提醒',
                            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      flex: 3,
                      child: SizedBox(
                        height: 44,
                        child: ElevatedButton(
                          onPressed: _openEvaluation,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: AppColors.brand,
                            foregroundColor: Colors.white,
                            elevation: 0,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
                          ),
                          child: const Text('确认完工与评价', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// 工单概要卡片 (5:1108 节点)
  Widget _buildSummaryCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
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
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              // 状态胶囊
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: const Color(0xFFEFF6FF),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.circle, size: 7, color: AppColors.brand),
                    SizedBox(width: 4),
                    Text(
                      '维修处理中',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: AppColors.brand,
                      ),
                    ),
                  ],
                ),
              ),
              // SLA 承诺胶囊
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: const Color(0xFFFEF3C7),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Text(
                  '承诺 24h 办结',
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
          const SizedBox(height: 12),
          Text(
            widget.location,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: Color(0xFF0F172A),
              height: 1.3,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            '工单号：${widget.orderNo} · ${widget.category}',
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 11,
              color: Color(0xFF64748B),
            ),
          ),
        ],
      ),
    ).animate().fadeIn(duration: 350.ms).slideY(begin: 0.05, end: 0);
  }

  /// 师傅信息卡片 (5:1116 节点)
  Widget _buildTechnicianCard() {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
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
          // 师傅头像圆形
          Container(
            width: 42,
            height: 42,
            decoration: const BoxDecoration(
              color: Color(0xFF0D9488),
              shape: BoxShape.circle,
            ),
            alignment: Alignment.center,
            child: const Text(
              '王',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '王建国师傅 (高级电工组)',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                ),
                SizedBox(height: 2),
                Text(
                  '已服务 384 单 · 满意度 99.8%',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 11,
                    color: Color(0xFF64748B),
                  ),
                ),
              ],
            ),
          ),
          GestureDetector(
            onTap: _handleCall,
            behavior: HitTestBehavior.opaque,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                color: const Color(0xFFEFF6FF),
                borderRadius: BorderRadius.circular(16),
              ),
              child: const Row(
                children: [
                  Icon(Icons.phone_in_talk_rounded, size: 14, color: AppColors.brand),
                  SizedBox(width: 4),
                  Text(
                    '电话联系',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: AppColors.brand,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    ).animate().fadeIn(duration: 400.ms, delay: 50.ms).slideY(begin: 0.05, end: 0);
  }

  /// 时间轴节点卡片 (5:1125 节点)
  Widget _buildTimelineCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
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
          const Text(
            '工单流转节点 (实时跟踪)',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 13,
              fontWeight: FontWeight.bold,
              color: Color(0xFF0F172A),
            ),
          ),
          const SizedBox(height: 16),
          _buildTimelineItem(
            time: '14:15',
            title: '现场检修中 (当前进度)',
            desc: '王师傅正在排查投影仪主板电源并更换备件',
            isCurrent: true,
            isLast: false,
          ),
          _buildTimelineItem(
            time: '13:40',
            title: '师傅已接单',
            desc: '后勤二组 王建国 师傅已接单前往现场',
            isCurrent: false,
            isLast: false,
          ),
          _buildTimelineItem(
            time: '13:10',
            title: '调度中心派单',
            desc: '平台指派至后勤维修二组 (多媒体专项)',
            isCurrent: false,
            isLast: false,
          ),
          _buildTimelineItem(
            time: '13:05',
            title: '申请提交',
            desc: '报修人 李航 提交教室报修申请',
            isCurrent: false,
            isLast: true,
          ),
        ],
      ),
    ).animate().fadeIn(duration: 450.ms, delay: 100.ms).slideY(begin: 0.05, end: 0);
  }

  Widget _buildTimelineItem({
    required String time,
    required String title,
    required String desc,
    required bool isCurrent,
    required bool isLast,
  }) {
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 左侧节点圆点与垂直连线
          SizedBox(
            width: 20,
            child: Column(
              children: [
                Container(
                  width: 10,
                  height: 10,
                  decoration: BoxDecoration(
                    color: isCurrent ? AppColors.brand : const Color(0xFFCBD5E1),
                    shape: BoxShape.circle,
                    border: isCurrent
                        ? Border.all(color: const Color(0xFFBFDBFE), width: 3)
                        : null,
                  ),
                ),
                if (!isLast)
                  Expanded(
                    child: Container(
                      width: 1.5,
                      color: const Color(0xFFE2E8F0),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(width: 8),

          // 右侧内容
          Expanded(
            child: Padding(
              padding: EdgeInsets.only(bottom: isLast ? 0 : 18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '$time · $title',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      fontWeight: isCurrent ? FontWeight.bold : FontWeight.w600,
                      color: isCurrent ? AppColors.brand : const Color(0xFF0F172A),
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    desc,
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 11,
                      color: Color(0xFF64748B),
                      height: 1.3,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
