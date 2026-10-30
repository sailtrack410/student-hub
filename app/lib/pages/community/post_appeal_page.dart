import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../api/api_client.dart';
import '../../api/community_api.dart';
import '../../theme/app_theme.dart';

/// 内容申诉表单：作者本人对被删除/隐藏的帖子提交人工复核
class PostAppealPage extends StatefulWidget {
  final String postId;
  final String postTitle;

  const PostAppealPage({super.key, required this.postId, this.postTitle = ''});

  @override
  State<PostAppealPage> createState() => _PostAppealPageState();
}

enum _EvidenceState { uploading, done, failed }

class _EvidenceImage {
  final Uint8List bytes;
  final String name;
  _EvidenceState state;
  String? fileId;

  _EvidenceImage({required this.bytes, required this.name})
    : state = _EvidenceState.uploading;
}

class _PostAppealPageState extends State<PostAppealPage> {
  final ImagePicker _picker = ImagePicker();
  final TextEditingController _descController = TextEditingController();
  final List<_EvidenceImage> _evidence = [];
  String? _reasonCode;
  bool _submitting = false;

  static const List<({String code, String label})> _reasons = [
    (code: 'MISJUDGED', label: '内容被误判'),
    (code: 'FIXED', label: '已修改违规内容'),
    (code: 'ACCOUNT', label: '账号被盗用'),
    (code: 'OTHER', label: '其他'),
  ];

  @override
  void dispose() {
    _descController.dispose();
    super.dispose();
  }

  Future<void> _pickEvidence() async {
    if (_evidence.length >= 3) {
      _toast('最多上传 3 张证明材料');
      return;
    }
    try {
      final files = await _picker.pickMultiImage(imageQuality: 85);
      for (final file in files.take(3 - _evidence.length)) {
        final bytes = await file.readAsBytes();
        final image = _EvidenceImage(bytes: bytes, name: file.name);
        setState(() => _evidence.add(image));
        _upload(image);
      }
    } catch (_) {
      _toast('选择图片失败，请重试');
    }
  }

  Future<void> _upload(_EvidenceImage image) async {
    final data = await ApiClient().uploadImage(
      image.bytes,
      filename: image.name,
    );
    if (!mounted) return;
    setState(() {
      image.fileId = data?['fileId']?.toString();
      image.state = data == null ? _EvidenceState.failed : _EvidenceState.done;
    });
  }

  Future<void> _submit() async {
    if (_submitting) return;
    final reason = _descController.text.trim();
    if ((_reasonCode == null || _reasonCode!.isEmpty) && reason.isEmpty) {
      _toast('请选择复核原因或填写补充说明');
      return;
    }
    if (_evidence.any((e) => e.state == _EvidenceState.uploading)) {
      _toast('证明材料上传中，请稍候');
      return;
    }
    if (_evidence.any((e) => e.state == _EvidenceState.failed)) {
      _toast('有证明材料上传失败，请重试');
      return;
    }
    setState(() => _submitting = true);
    try {
      await CommunityApi.submitAppeal(
        postId: widget.postId,
        reasonCode: _reasonCode,
        reason: reason,
        evidenceFileIds: _evidence
            .map((e) => e.fileId)
            .whereType<String>()
            .toList(),
      );
      if (!mounted) return;
      setState(() => _submitting = false);
      _showSuccess();
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _submitting = false);
      _toast(e.message);
    } catch (_) {
      if (!mounted) return;
      setState(() => _submitting = false);
      _toast('提交失败，请稍后重试');
    }
  }

  void _toast(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  void _showSuccess() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 56,
              height: 56,
              decoration: const BoxDecoration(
                color: Color(0xFFECFDF5),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.check_rounded,
                size: 32,
                color: AppColors.accent,
              ),
            ),
            const SizedBox(height: 14),
            const Text(
              '申诉已提交',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 17,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              '社区管理员将在 1-3 个工作日内完成人工复核，结果会通过消息通知你。',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                height: 1.6,
                color: Color(0xFF64748B),
              ),
            ),
            const SizedBox(height: 18),
            SizedBox(
              width: double.infinity,
              height: 42,
              child: ElevatedButton(
                onPressed: () {
                  Navigator.of(ctx).pop();
                  Navigator.of(context).pop(true);
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.brand,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(21),
                  ),
                ),
                child: const Text('知道了'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
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
          '内容申诉',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
        children: [
          _buildInfoCard(),
          const SizedBox(height: 16),
          _buildSectionTitle('复核原因', hint: '可选项'),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: _reasons.map((reason) {
              final selected = _reasonCode == reason.code;
              return GestureDetector(
                onTap: () =>
                    setState(() => _reasonCode = selected ? null : reason.code),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 7,
                  ),
                  decoration: BoxDecoration(
                    color: selected ? const Color(0xFFEFF6FF) : Colors.white,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: selected
                          ? const Color(0xFFBFDBFE)
                          : const Color(0xFFE2E8F0),
                    ),
                  ),
                  child: Text(
                    reason.label,
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      fontWeight: selected
                          ? FontWeight.bold
                          : FontWeight.normal,
                      color: selected
                          ? AppColors.brand
                          : const Color(0xFF64748B),
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 18),
          _buildSectionTitle('补充说明', hint: '选填'),
          const SizedBox(height: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFFE2E8F0)),
            ),
            child: TextField(
              controller: _descController,
              maxLines: 5,
              maxLength: 500,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                height: 1.6,
              ),
              decoration: const InputDecoration(
                hintText: '请补充说明情况，便于管理员核实（选填）',
                hintStyle: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: Color(0xFF64748B),
                ),
                border: InputBorder.none,
              ),
            ),
          ),
          const SizedBox(height: 6),
          _buildSectionTitle('证明材料', hint: '选填，最多 3 张'),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (int i = 0; i < _evidence.length; i++) _buildEvidenceTile(i),
              if (_evidence.length < 3) _buildAddEvidenceTile(),
            ],
          ),
          const SizedBox(height: 10),
          const Text(
            '证明材料仅用于本次申诉复核，管理员不会对外展示。',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
            ),
          ),
        ],
      ),
      bottomNavigationBar: Container(
        color: Colors.white,
        padding: EdgeInsets.fromLTRB(
          16,
          10,
          16,
          10 + MediaQuery.of(context).padding.bottom,
        ),
        child: SizedBox(
          width: double.infinity,
          height: 46,
          child: ElevatedButton(
            onPressed: _submitting ? null : _submit,
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.brand,
              foregroundColor: Colors.white,
              disabledBackgroundColor: const Color(0xFF93C5FD),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(23),
              ),
            ),
            child: _submitting
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Text(
                    '提交申诉',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 15,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
          ),
        ),
      ),
    );
  }

  Widget _buildInfoCard() {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFF1F5F9)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: const BoxDecoration(
                  color: Color(0xFFEFF6FF),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.gavel_rounded,
                  size: 18,
                  color: AppColors.brand,
                ),
              ),
              const SizedBox(width: 10),
              const Expanded(
                child: Text(
                  '申请人工复核',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: AppColors.textPrimary,
                  ),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFFFEF2F2),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: const Text(
                  '内容不可见',
                  style: TextStyle(fontSize: 12, color: Color(0xFFEF4444)),
                ),
              ),
            ],
          ),
          if (widget.postTitle.isNotEmpty) ...[
            const SizedBox(height: 10),
            Text(
              widget.postTitle,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: AppColors.textPrimary,
              ),
            ),
          ],
          const SizedBox(height: 6),
          Text(
            '帖子编号：${widget.postId}',
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(String title, {String? hint}) {
    return Row(
      children: [
        Text(
          title,
          style: const TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 13,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        if (hint != null) ...[
          const SizedBox(width: 6),
          Text(
            hint,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildEvidenceTile(int index) {
    final image = _evidence[index];
    return SizedBox(
      width: 88,
      height: 88,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(10),
        child: Stack(
          fit: StackFit.expand,
          children: [
            Image.memory(image.bytes, fit: BoxFit.cover),
            if (image.state == _EvidenceState.uploading)
              const ColoredBox(
                color: Color(0x66000000),
                child: Center(
                  child: SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  ),
                ),
              ),
            if (image.state == _EvidenceState.failed)
              GestureDetector(
                onTap: () => setState(() {
                  image.state = _EvidenceState.uploading;
                  _upload(image);
                }),
                child: const ColoredBox(
                  color: Color(0x99000000),
                  child: Center(
                    child: Text(
                      '点击重试',
                      style: TextStyle(color: Colors.white, fontSize: 12),
                    ),
                  ),
                ),
              ),
            Positioned(
              top: 4,
              right: 4,
              child: GestureDetector(
                onTap: () => setState(() => _evidence.removeAt(index)),
                child: Container(
                  width: 18,
                  height: 18,
                  decoration: const BoxDecoration(
                    color: Color(0x99000000),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.close_rounded,
                    size: 12,
                    color: Colors.white,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAddEvidenceTile() {
    return GestureDetector(
      onTap: _pickEvidence,
      child: Container(
        width: 88,
        height: 88,
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: const Color(0xFFE2E8F0)),
        ),
        child: const Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.add_photo_alternate_outlined,
              size: 22,
              color: Color(0xFF94A3B8),
            ),
            SizedBox(height: 4),
            Text(
              '上传证明',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: Color(0xFF64748B),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
