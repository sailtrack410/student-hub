import 'package:flutter/material.dart';
import 'my_requests_page.dart';
import 'work_order_detail_page.dart';

/// APP-教室报修申请 (对应画布 5:1049 节点，375 × 812)
/// 严格依据 Ardot 设计规范实现报修位置选择、故障多分类切换、问题描述输入、现场照片上传与提交流转
class RepairApplyPage extends StatefulWidget {
  final String title;

  const RepairApplyPage({
    super.key,
    this.title = '教室与公共区域报修',
  });

  @override
  State<RepairApplyPage> createState() => _RepairApplyPageState();
}

class _RepairApplyPageState extends State<RepairApplyPage> {
  String _selectedLocation = '明德楼 (教学楼A栋) · 302 多媒体教室';
  String _selectedFaultCategory = '多媒体投影';
  late final TextEditingController _descriptionController;
  final TextEditingController _contactNameController = TextEditingController(text: '李航');
  final TextEditingController _contactPhoneController = TextEditingController(text: '138 0816 6821');

  final List<String> _photos = ['故障现场'];
  bool _isSubmitting = false;

  final List<String> _classroomFaultCategories = const [
    '多媒体投影',
    '桌椅损坏',
    '照明电路',
    '空调风扇',
    '其他故障',
  ];

  final List<String> _dormFaultCategories = const [
    '水管水龙头',
    '门窗合页锁具',
    '宿舍照明',
    '空调风扇',
    '排插电路',
    '其他故障',
  ];

  final List<String> _candidateLocations = const [
    '明德楼 (教学楼A栋) · 302 多媒体教室',
    '明德楼 (教学楼A栋) · 101 大阶梯教室',
    '明德楼 (教学楼A栋) · 204 智慧研讨室',
    '笃学楼 (教学楼B栋) · 405 计算机网络室',
    '实验实训楼 · 501 语音实验室',
    '海棠苑 4栋 · 208 宿舍',
    '海棠苑 4栋 · 315 宿舍',
    '综合图书馆 · 2楼 社科阅览室',
  ];

  @override
  void initState() {
    super.initState();
    if (widget.title.contains('宿舍')) {
      _selectedLocation = '海棠苑 4栋 · 208 宿舍';
      _selectedFaultCategory = '水管水龙头';
      _descriptionController = TextEditingController(
        text: '宿舍阳台洗手池水龙头出水异常，关紧后仍不停滴水，影响作息，请师傅更换胶垫或水阀！',
      );
    } else {
      _descriptionController = TextEditingController(
        text: '开机后投影仪主灯不亮，红灯一直闪烁，无法投射电脑屏幕。周三下午有课急需使用，烦请师傅尽快协助检修！',
      );
    }
  }

  @override
  void dispose() {
    _descriptionController.dispose();
    _contactNameController.dispose();
    _contactPhoneController.dispose();
    super.dispose();
  }

  void _showGuideSheet() {
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
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: const Color(0xFFEFF6FF),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(Icons.menu_book_rounded, color: Color(0xFF2563EB), size: 20),
                ),
                const SizedBox(width: 12),
                const Text(
                  '校园公共设施报修指南',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 17,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            const Text(
              '1. 受理范围：全校教学楼公用多媒体设备、课桌椅、门窗电路照明及暖通设施。\n'
              '2. 响应时效：日常工作日承诺 2 小时内接单响应，24 小时内核验上门排除故障。\n'
              '3. 紧急教学保障：若授课遇设备突发严重故障，可点击紧急热线直通调度中心。',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                color: Color(0xFF475569),
                height: 1.6,
              ),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () => Navigator.of(ctx).pop(),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF2563EB),
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                padding: const EdgeInsets.symmetric(vertical: 12),
              ),
              child: const Text('我知道了'),
            ),
          ],
        ),
      ),
    );
  }

  void _showLocationPicker() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        height: 380,
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 20),
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  '选择报修位置',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close_rounded, size: 20, color: Color(0xFF94A3B8)),
                  onPressed: () => Navigator.of(ctx).pop(),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Expanded(
              child: ListView.separated(
                itemCount: _candidateLocations.length,
                separatorBuilder: (_, __) => const Divider(height: 1, color: Color(0xFFF1F5F9)),
                itemBuilder: (context, index) {
                  final loc = _candidateLocations[index];
                  final isChosen = loc == _selectedLocation;
                  return ListTile(
                    contentPadding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                    title: Text(
                      loc,
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 14,
                        fontWeight: isChosen ? FontWeight.w600 : FontWeight.normal,
                        color: isChosen ? const Color(0xFF2563EB) : const Color(0xFF1E293B),
                      ),
                    ),
                    trailing: isChosen
                        ? const Icon(Icons.check_circle_rounded, color: Color(0xFF2563EB), size: 20)
                        : null,
                    onTap: () {
                      setState(() => _selectedLocation = loc);
                      Navigator.of(ctx).pop();
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _handleAddPhoto() {
    if (_photos.length >= 3) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('最多支持上传 3 张现场照片'),
          behavior: SnackBarBehavior.floating,
        ),
      );
      return;
    }
    setState(() {
      _photos.add('现场特写 ${_photos.length + 1}');
    });
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('已添加现场照片（${_photos.length}/3）'),
        duration: const Duration(seconds: 1),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  void _handleSubmit() async {
    final text = _descriptionController.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('请填写详细的问题描述以便维修师傅备件'),
          behavior: SnackBarBehavior.floating,
        ),
      );
      return;
    }

    setState(() => _isSubmitting = true);
    await Future.delayed(const Duration(milliseconds: 350));
    if (!mounted) return;
    setState(() => _isSubmitting = false);

    // 弹出提交成功对话框并引导跳转
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        contentPadding: const EdgeInsets.fromLTRB(24, 24, 24, 16),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 56,
              height: 56,
              decoration: const BoxDecoration(
                color: Color(0xFFEFF6FF),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.check_rounded, color: Color(0xFF2563EB), size: 32),
            ),
            const SizedBox(height: 16),
            const Text(
              '报修申请已提交',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: Color(0xFF0F172A),
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              '工单编号: WO-20261017-0915\n已指派至后勤综合维修组，承诺 24 小时内上门检修。',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                color: Color(0xFF64748B),
                height: 1.5,
              ),
            ),
          ],
        ),
        actions: [
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () {
                    Navigator.of(ctx).pop();
                    Navigator.of(context).pushReplacement(
                      MaterialPageRoute(builder: (_) => const MyRequestsPage()),
                    );
                  },
                  style: OutlinedButton.styleFrom(
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    side: const BorderSide(color: Color(0xFFE2E8F0)),
                  ),
                  child: const Text('我的申请', style: TextStyle(color: Color(0xFF475569))),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: ElevatedButton(
                  onPressed: () {
                    Navigator.of(ctx).pop();
                    Navigator.of(context).pushReplacement(
                      MaterialPageRoute(
                        builder: (_) => WorkOrderDetailPage(
                          orderNo: 'WO-20261017-0915',
                          location: _selectedLocation,
                          category: '$_selectedFaultCategory专项',
                          status: '维修处理中',
                        ),
                      ),
                    );
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF2563EB),
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  child: const Text('查看进度'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final faultCategories = widget.title.contains('宿舍')
        ? _dormFaultCategories
        : _classroomFaultCategories;

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            // 1. 顶部导航栏 (节点 5:1050: 375 × 88)
            Container(
              color: Colors.white,
              padding: const EdgeInsets.only(left: 12, right: 16, top: 10, bottom: 10),
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(Icons.arrow_back_ios_new_rounded, size: 20, color: Color(0xFF0F172A)),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                  Expanded(
                    child: Center(
                      child: Text(
                        widget.title,
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 17,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF0F172A),
                        ),
                      ),
                    ),
                  ),
                  InkWell(
                    onTap: _showGuideSheet,
                    borderRadius: BorderRadius.circular(6),
                    child: const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 4, vertical: 4),
                      child: Text(
                        '办事指南',
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

            // 2. 表单内容可滚动区域 (节点 5:1055)
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                children: [
                  // 提示横条 (节点 5:1056)
                  Container(
                    decoration: BoxDecoration(
                      color: const Color(0xFFEFF6FF),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Icon(
                          Icons.info_outline_rounded,
                          size: 16,
                          color: Color(0xFF2563EB),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            widget.title.contains('宿舍')
                                ? '提示：适用于学生宿舍楼设施故障，宿管阿姨核验后 24 小时内上门处置'
                                : '提示：适用于教学楼、多媒体教室等公共设施故障，承诺 24 小时内上门处置',
                            style: const TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 12,
                              color: Color(0xFF2563EB),
                              height: 1.4,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 12),

                  // 卡片 1: 报修位置与故障分类 (节点 5:1058)
                  _buildCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '报修位置 *',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF0F172A),
                          ),
                        ),
                        const SizedBox(height: 8),
                        GestureDetector(
                          onTap: _showLocationPicker,
                          child: Container(
                            decoration: BoxDecoration(
                              color: const Color(0xFFF8FAFC),
                              borderRadius: BorderRadius.circular(10),
                              border: Border.all(color: const Color(0xFFE2E8F0)),
                            ),
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                            child: Row(
                              children: [
                                const Icon(
                                  Icons.location_on_rounded,
                                  size: 18,
                                  color: Color(0xFF64748B),
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Text(
                                    _selectedLocation,
                                    style: const TextStyle(
                                      fontFamily: 'Noto Sans SC',
                                      fontSize: 13,
                                      color: Color(0xFF0F172A),
                                      fontWeight: FontWeight.w500,
                                    ),
                                  ),
                                ),
                                const Icon(
                                  Icons.chevron_right_rounded,
                                  size: 18,
                                  color: Color(0xFF94A3B8),
                                ),
                              ],
                            ),
                          ),
                        ),

                        const SizedBox(height: 16),

                        const Text(
                          '故障分类 *',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF0F172A),
                          ),
                        ),
                        const SizedBox(height: 10),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: faultCategories.map((cat) {
                            final isChosen = cat == _selectedFaultCategory;
                            return GestureDetector(
                              onTap: () => setState(() => _selectedFaultCategory = cat),
                              child: AnimatedContainer(
                                duration: const Duration(milliseconds: 150),
                                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
                                decoration: BoxDecoration(
                                  color: isChosen ? const Color(0xFFEFF6FF) : const Color(0xFFF8FAFC),
                                  borderRadius: BorderRadius.circular(16),
                                  border: Border.all(
                                    color: isChosen ? const Color(0xFFBFDBFE) : const Color(0xFFE2E8F0),
                                  ),
                                ),
                                child: Text(
                                  isChosen ? '$cat (选中)' : cat,
                                  style: TextStyle(
                                    fontFamily: 'Noto Sans SC',
                                    fontSize: 12,
                                    fontWeight: isChosen ? FontWeight.w600 : FontWeight.normal,
                                    color: isChosen ? const Color(0xFF2563EB) : const Color(0xFF64748B),
                                  ),
                                ),
                              ),
                            );
                          }).toList(),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 12),

                  // 卡片 2: 问题描述与现场照片 (节点 5:1074)
                  _buildCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '问题描述 *',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF0F172A),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Container(
                          decoration: BoxDecoration(
                            color: const Color(0xFFF8FAFC),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: const Color(0xFFE2E8F0)),
                          ),
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              TextField(
                                controller: _descriptionController,
                                maxLines: 4,
                                maxLength: 500,
                                buildCounter: (_, {required currentLength, required isFocused, maxLength}) => null,
                                onChanged: (_) => setState(() {}),
                                style: const TextStyle(
                                  fontFamily: 'Noto Sans SC',
                                  fontSize: 13,
                                  color: Color(0xFF0F172A),
                                  height: 1.5,
                                ),
                                decoration: const InputDecoration(
                                  hintText: '请详细描述具体故障现象、影响范围及具体急需情况...',
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
                              Align(
                                alignment: Alignment.bottomRight,
                                child: Text(
                                  '${_descriptionController.text.length} / 500',
                                  style: const TextStyle(
                                    fontFamily: 'Noto Sans SC',
                                    fontSize: 11,
                                    color: Color(0xFF94A3B8),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),

                        const SizedBox(height: 14),

                        const Text(
                          '现场照片 (有助于师傅快速排查备件)',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 13,
                            color: Color(0xFF334155),
                          ),
                        ),
                        const SizedBox(height: 10),
                        Row(
                          children: [
                            ..._photos.map((item) => Container(
                              width: 72,
                              height: 72,
                              margin: const EdgeInsets.only(right: 12),
                              decoration: BoxDecoration(
                                color: const Color(0xFFE2E8F0),
                                borderRadius: BorderRadius.circular(10),
                              ),
                              alignment: Alignment.center,
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  const Icon(Icons.broken_image_rounded, size: 24, color: Color(0xFF64748B)),
                                  const SizedBox(height: 4),
                                  Text(
                                    item,
                                    style: const TextStyle(
                                      fontFamily: 'Noto Sans SC',
                                      fontSize: 11,
                                      color: Color(0xFF64748B),
                                    ),
                                  ),
                                ],
                              ),
                            )),
                            if (_photos.length < 3)
                              GestureDetector(
                                onTap: _handleAddPhoto,
                                child: Container(
                                  width: 72,
                                  height: 72,
                                  decoration: BoxDecoration(
                                    color: const Color(0xFFF8FAFC),
                                    borderRadius: BorderRadius.circular(10),
                                    border: Border.all(
                                      color: const Color(0xFFCBD5E1),
                                      style: BorderStyle.solid,
                                    ),
                                  ),
                                  child: Column(
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    children: [
                                      const Icon(Icons.add_rounded, size: 22, color: Color(0xFF94A3B8)),
                                      const SizedBox(height: 4),
                                      Text(
                                        '添加 (${_photos.length}/3)',
                                        style: const TextStyle(
                                          fontFamily: 'Noto Sans SC',
                                          fontSize: 11,
                                          color: Color(0xFF94A3B8),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 12),

                  // 卡片 3: 联系与期望 (节点 5:1087)
                  _buildCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '报修联系人 *',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF0F172A),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Container(
                          decoration: BoxDecoration(
                            color: const Color(0xFFF8FAFC),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: const Color(0xFFE2E8F0)),
                          ),
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                          child: TextField(
                            controller: _contactNameController,
                            style: const TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 13,
                              color: Color(0xFF0F172A),
                            ),
                            decoration: const InputDecoration(
                              border: InputBorder.none,
                              isDense: true,
                              contentPadding: EdgeInsets.zero,
                            ),
                          ),
                        ),

                        const SizedBox(height: 12),

                        const Text(
                          '联系电话 *',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF0F172A),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Container(
                          decoration: BoxDecoration(
                            color: const Color(0xFFF8FAFC),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: const Color(0xFFE2E8F0)),
                          ),
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                          child: TextField(
                            controller: _contactPhoneController,
                            keyboardType: TextInputType.phone,
                            style: const TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 13,
                              color: Color(0xFF0F172A),
                            ),
                            decoration: const InputDecoration(
                              border: InputBorder.none,
                              isDense: true,
                              contentPadding: EdgeInsets.zero,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 20),

                  // 提交按钮 (节点 5:1094: 343 × 46, 圆角 23, 浅蓝渐变实色)
                  ElevatedButton(
                    onPressed: _isSubmitting ? null : _handleSubmit,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF2563EB),
                      foregroundColor: Colors.white,
                      elevation: 0,
                      minimumSize: const Size(double.infinity, 46),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(23)),
                    ),
                    child: _isSubmitting
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                          )
                        : Text(
                            widget.title.contains('宿舍') ? '立即提交宿舍报修申请' : '立即提交教室报修申请',
                            style: const TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 15,
                              fontWeight: FontWeight.w600,
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

  Widget _buildCard({required Widget child}) {
    return Container(
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
      child: child,
    );
  }
}
