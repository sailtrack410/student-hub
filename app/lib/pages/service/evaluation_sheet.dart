import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

/// APP-完工验收与评价弹层 (对应画布 5:1144 节点)
class EvaluationBottomSheet extends StatefulWidget {
  final String orderNo;
  final String location;
  final VoidCallback? onSubmitted;

  const EvaluationBottomSheet({
    super.key,
    required this.orderNo,
    required this.location,
    this.onSubmitted,
  });

  static Future<bool?> show(
    BuildContext context, {
    required String orderNo,
    required String location,
  }) {
    return showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => EvaluationBottomSheet(
        orderNo: orderNo,
        location: location,
      ),
    );
  }

  @override
  State<EvaluationBottomSheet> createState() => _EvaluationBottomSheetState();
}

class _EvaluationBottomSheetState extends State<EvaluationBottomSheet> {
  int _rating = 5;
  final Set<String> _selectedTags = {'响应神速', '技术精湛'};
  final TextEditingController _feedbackController = TextEditingController(
    text: '王师傅来得特别快，十来分钟就排查出是电源短路并修好了，投影特别清晰，点赞后勤效率！',
  );

  final List<String> _tags = const ['响应神速', '技术精湛', '态度亲切', '清理现场'];

  @override
  void dispose() {
    _feedbackController.dispose();
    super.dispose();
  }

  void _handleTagToggle(String tag) {
    setState(() {
      if (_selectedTags.contains(tag)) {
        _selectedTags.remove(tag);
      } else {
        _selectedTags.add(tag);
      }
    });
  }

  void _handleSubmit() {
    Navigator.of(context).pop(true);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Row(
          children: [
            Icon(Icons.check_circle_rounded, color: Colors.white, size: 20),
            SizedBox(width: 8),
            Text('评价已提交，感谢您的反馈！'),
          ],
        ),
        backgroundColor: const Color(0xFF16A34A),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  void _handleReject() {
    Navigator.of(context).pop(false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Row(
          children: [
            Icon(Icons.info_outline_rounded, color: Colors.white, size: 20),
            SizedBox(width: 8),
            Text('已退回工单，后勤调度中心将安排师傅重新处理'),
          ],
        ),
        backgroundColor: const Color(0xFFD97706),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 12,
        bottom: MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 拖拽指示条 (5:1146 节点：36 × 4，圆角 2)
          Center(
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: const Color(0xFFCBD5E1),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 16),

          // 标题与副标题 (5:1147, 5:1148)
          const Text(
            '服务完工确认与评价',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: Color(0xFF0F172A),
              height: 1.2,
            ),
          ),
          const SizedBox(height: 4),
          const Text(
            '请核对师傅上传的维修凭证，并对本次报修服务进行评价',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              fontWeight: FontWeight.w400,
              color: Color(0xFF64748B),
              height: 1.2,
            ),
          ),
          const SizedBox(height: 16),

          // 师傅完工证明卡片 (5:1149 节点)
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: const Color(0xFFF8FAFC),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                // 修好照片略缩图
                Container(
                  width: 60,
                  height: 60,
                  decoration: BoxDecoration(
                    color: const Color(0xFFE2E8F0),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  alignment: Alignment.center,
                  child: const Text(
                    '修好照片',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 10,
                      color: Color(0xFF64748B),
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.check_rounded, size: 14, color: Color(0xFF16A34A)),
                          SizedBox(width: 4),
                          Text(
                            '师傅已拍照上传完工证明',
                            style: TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 13,
                              fontWeight: FontWeight.bold,
                              color: Color(0xFF16A34A),
                            ),
                          ),
                        ],
                      ),
                      SizedBox(height: 3),
                      Text(
                        '已更换主板电源保险丝，信号投屏完全正常',
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 11,
                          color: Color(0xFF64748B),
                          height: 1.3,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // 整体满意度与星级评分 (5:1156 节点)
          Row(
            children: [
              const Text(
                '整体满意度',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF0F172A),
                ),
              ),
              const SizedBox(width: 12),
              Row(
                children: List.generate(5, (index) {
                  final starIndex = index + 1;
                  return GestureDetector(
                    onTap: () => setState(() => _rating = starIndex),
                    behavior: HitTestBehavior.opaque,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 2),
                      child: Icon(
                        Icons.star_rounded,
                        size: 24,
                        color: starIndex <= _rating
                            ? const Color(0xFFF59E0B)
                            : const Color(0xFFE2E8F0),
                      ),
                    ),
                  );
                }),
              ),
              const SizedBox(width: 8),
              Text(
                '$_rating.0 ${_rating == 5 ? "非常满意" : _rating >= 4 ? "满意" : "一般"}',
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFFF59E0B),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // 快捷标签选择组 (5:1164 节点)
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: _tags.map((tag) {
              final isSelected = _selectedTags.contains(tag);
              return GestureDetector(
                onTap: () => _handleTagToggle(tag),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 150),
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  decoration: BoxDecoration(
                    color: isSelected ? const Color(0xFFEFF6FF) : const Color(0xFFF8FAFC),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: isSelected ? const Color(0xFFBFDBFE) : const Color(0xFFE2E8F0),
                      width: 1,
                    ),
                  ),
                  child: Text(
                    tag,
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
                      color: isSelected ? AppColors.brand : const Color(0xFF64748B),
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 12),

          // 评语输入框 (5:1174 节点)
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: const Color(0xFFF8FAFC),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFFE2E8F0), width: 0.8),
            ),
            child: TextField(
              controller: _feedbackController,
              maxLines: 3,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: Color(0xFF0F172A),
                height: 1.4,
              ),
              decoration: const InputDecoration.collapsed(
                hintText: '写下您对本次服务的宝贵评价...',
                hintStyle: TextStyle(color: Color(0xFF94A3B8), fontSize: 12),
              ),
            ),
          ),
          const SizedBox(height: 20),

          // 底部双按钮 (5:1186 节点)
          Row(
            children: [
              Expanded(
                flex: 2,
                child: SizedBox(
                  height: 46,
                  child: OutlinedButton(
                    onPressed: _handleReject,
                    style: OutlinedButton.styleFrom(
                      foregroundColor: const Color(0xFF64748B),
                      side: const BorderSide(color: Color(0xFFCBD5E1)),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(23)),
                    ),
                    child: const Text('未修好，退回', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                flex: 3,
                child: SizedBox(
                  height: 46,
                  child: ElevatedButton(
                    onPressed: _handleSubmit,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.brand,
                      foregroundColor: Colors.white,
                      elevation: 0,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(23)),
                    ),
                    child: const Text('确认完工并提交评价', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
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
