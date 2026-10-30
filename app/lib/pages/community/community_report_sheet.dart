import 'package:flutter/material.dart';

import '../../api/community_api.dart';
import 'community_report_success_sheet.dart';
import '../../models/community_models.dart';

/// APP-社区举报弹层 (对应画布 5:1427 节点，375 × 812)
/// 严格依据 Ardot 设计规范实现违规理由单选、事实描述、截图凭据上传与隐私合规提交
class CommunityReportSheet extends StatefulWidget {
  final CommunityPost post;

  const CommunityReportSheet({super.key, required this.post});

  static Future<void> show(BuildContext context, CommunityPost post) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => CommunityReportSheet(post: post),
    );
  }

  @override
  State<CommunityReportSheet> createState() => _CommunityReportSheetState();
}

class _CommunityReportSheetState extends State<CommunityReportSheet> {
  final TextEditingController _descController = TextEditingController();
  bool _submitting = false;
  bool _submitted = false;
  String _reportNo = '';
  int _selectedReasonIndex = 1; // 默认选中"涉假诈骗/商业广告"（与设计稿一致）
  final List<String> _evidenceList = ['涉诈私聊截图'];

  static const List<({String label, String code})> _reasonOptions = [
    (label: '色情低俗', code: 'PORN'),
    (label: '涉假诈骗/商业广告', code: 'AD'),
    (label: '泄露隐私(学号/手机)', code: 'PRIVACY'),
    (label: '辱骂攻击/造谣', code: 'ABUSE'),
    (label: '其他违规', code: 'OTHER'),
  ];

  List<String> get _reasons => _reasonOptions.map((e) => e.label).toList();

  @override
  void initState() {
    super.initState();
    _descController.text = '该帖子含有虚假代考兼职信息，诱导私聊添加外部联系方式，涉嫌刷单诈骗...';
  }

  @override
  void dispose() {
    _descController.dispose();
    super.dispose();
  }

  void _handleAddEvidence() {
    if (_evidenceList.length >= 3) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('最多支持上传 3 张证据截图')));
      return;
    }
    setState(() {
      _evidenceList.add('证据截图_${_evidenceList.length + 1}.png');
    });
  }

  Future<void> _handleSubmit() async {
    if (_submitting) return;
    setState(() => _submitting = true);
    final ok = await CommunityApi.submitReport(
      postId: widget.post.id,
      targetType: 'POST',
      reasonCode: _reasonOptions[_selectedReasonIndex].code,
      reason: _reasons[_selectedReasonIndex],
      description: _descController.text.trim(),
      evidenceUrls: _evidenceList,
    );
    if (!mounted) return;
    if (!ok) {
      setState(() => _submitting = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('举报提交失败，请稍后重试')));
      return;
    }
    final now = DateTime.now();
    setState(() {
      _submitting = false;
      _submitted = true;
      _reportNo =
          'RP-${now.year}${now.month.toString().padLeft(2, '0')}${now.day.toString().padLeft(2, '0')}${now.hour.toString().padLeft(2, '0')}${now.minute.toString().padLeft(2, '0')}';
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_submitted) {
      return CommunityReportSuccessSheet(
        reportNo: _reportNo,
        reason: _reasons[_selectedReasonIndex],
      );
    }
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 12,
        bottom: MediaQuery.of(context).padding.bottom + 16,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // 1. 顶部拖拽指示条
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

            // 2. 头部标题与社区公约
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  '违规内容举报',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                ),
                GestureDetector(
                  onTap: () {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('已打开《绵城校园社区文明公约》')),
                    );
                  },
                  child: const Text(
                    '社区公约 >',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                      color: Color(0xFF64748B),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),

            // 3. 被举报帖子引用卡 (5:1428 节点)
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFFF8FAFC),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFFF1F5F9)),
              ),
              child: Row(
                children: [
                  Container(
                    width: 80,
                    height: 56,
                    decoration: BoxDecoration(
                      color: const Color(0xFFEFF6FF),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    alignment: Alignment.center,
                    child: Text(
                      '# ${widget.post.topics.isNotEmpty ? widget.post.topics.first : widget.post.partition}',
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        color: Color(0xFF2563EB),
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      widget.post.title,
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        fontWeight: FontWeight.w500,
                        color: Color(0xFF334155),
                        height: 1.35,
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 18),

            // 4. 选择举报理由 *
            const Text(
              '选择举报理由 *',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                fontWeight: FontWeight.bold,
                color: Color(0xFF0F172A),
              ),
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: List.generate(_reasons.length, (index) {
                final isSelected = _selectedReasonIndex == index;
                final reason = _reasons[index];

                return GestureDetector(
                  onTap: () => setState(() => _selectedReasonIndex = index),
                  child: AnimatedContainer(
                    duration: MediaQuery.disableAnimationsOf(context)
                        ? Duration.zero
                        : const Duration(milliseconds: 150),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 8,
                    ),
                    decoration: BoxDecoration(
                      color: isSelected
                          ? const Color(0xFFEFF6FF)
                          : const Color(0xFFF1F5F9),
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(
                        color: isSelected
                            ? const Color(0xFF2563EB)
                            : Colors.transparent,
                        width: 1.2,
                      ),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          reason,
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 12,
                            fontWeight: isSelected
                                ? FontWeight.bold
                                : FontWeight.normal,
                            color: isSelected
                                ? const Color(0xFF2563EB)
                                : const Color(0xFF475569),
                          ),
                        ),
                        if (isSelected) ...[
                          const SizedBox(width: 4),
                          const Icon(
                            Icons.check,
                            size: 14,
                            color: Color(0xFF2563EB),
                          ),
                        ],
                      ],
                    ),
                  ),
                );
              }),
            ),
            const SizedBox(height: 18),

            // 5. 补充事实描述与证据 (选填)
            const Text(
              '补充事实描述与证据 (选填)',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                fontWeight: FontWeight.bold,
                color: Color(0xFF0F172A),
              ),
            ),
            const SizedBox(height: 10),
            Container(
              decoration: BoxDecoration(
                color: const Color(0xFFF8FAFC),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              padding: const EdgeInsets.all(12),
              child: Column(
                children: [
                  TextField(
                    controller: _descController,
                    maxLines: 3,
                    maxLength: 200,
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      color: Color(0xFF0F172A),
                    ),
                    decoration: const InputDecoration(
                      hintText: '请详细描述违规事实，便于管理员核实...',
                      hintStyle: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        color: Color(0xFF64748B),
                      ),
                      border: InputBorder.none,
                      isDense: true,
                      contentPadding: EdgeInsets.zero,
                    ),
                    onChanged: (_) => setState(() {}),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),

            // 6. 证据截图展示区
            Row(
              children: [
                ..._evidenceList.map(
                  (ev) => Container(
                    width: 64,
                    height: 64,
                    margin: const EdgeInsets.only(right: 10),
                    decoration: BoxDecoration(
                      color: const Color(0xFFE2E8F0),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    alignment: Alignment.center,
                    padding: const EdgeInsets.all(4),
                    child: Text(
                      ev,
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        color: Color(0xFF475569),
                      ),
                    ),
                  ),
                ),
                if (_evidenceList.length < 3)
                  GestureDetector(
                    onTap: _handleAddEvidence,
                    child: Container(
                      width: 64,
                      height: 64,
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
                          const Icon(
                            Icons.add,
                            size: 20,
                            color: Color(0xFF64748B),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            '${_evidenceList.length}/3',
                            style: const TextStyle(
                              fontSize: 12,
                              color: Color(0xFF64748B),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 14),

            // 7. 隐私说明
            const Text(
              '绵城校园严格保障举报人隐私，信息将加密提交至学生工作处审核',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: Color(0xFF64748B),
              ),
            ),
            const SizedBox(height: 14),

            // 8. 红色提交按钮 (高度 48, 圆角 24)
            SizedBox(
              height: 48,
              child: ElevatedButton(
                onPressed: _handleSubmit,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFFEF4444),
                  foregroundColor: Colors.white,
                  elevation: 0,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(24),
                  ),
                ),
                child: const Text(
                  '提交违规举报',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
