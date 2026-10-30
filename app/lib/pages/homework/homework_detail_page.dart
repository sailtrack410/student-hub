import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/homework_api.dart';
import '../../models/homework_models.dart';
import '../../theme/app_theme.dart';
import '../../widgets/skeleton_shimmer.dart';

/// APP-作业详情与在线提交：展示作业内容、截止时间与我的提交，可提交/更新提交。
/// 提交成功后 pop(true)，列表页收到结果后刷新「已交/未交」状态。
class HomeworkDetailPage extends StatefulWidget {
  final String homeworkId;

  const HomeworkDetailPage({super.key, required this.homeworkId});

  @override
  State<HomeworkDetailPage> createState() => _HomeworkDetailPageState();
}

class _HomeworkDetailPageState extends State<HomeworkDetailPage> {
  final HomeworkApi _api = HomeworkApi(ApiClient());
  final TextEditingController _contentController = TextEditingController();

  HomeworkDetail? _detail;
  bool _loading = true;
  bool _submitting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _contentController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final detail = await _api.fetchDetail(widget.homeworkId);
      if (!mounted) return;
      setState(() {
        _detail = detail;
        _contentController.text = detail.submissionContent ?? '';
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = error.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = '作业详情加载失败，请稍后重试';
      });
    }
  }

  Future<void> _submit() async {
    final detail = _detail;
    if (detail == null || _submitting) return;
    final content = _contentController.text.trim();
    if (content.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('请输入作业提交内容')));
      return;
    }
    setState(() => _submitting = true);
    try {
      final updated = await _api.submit(detail.homeworkId, content);
      if (!mounted) return;
      setState(() {
        _detail = updated;
        _submitting = false;
      });
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('作业提交成功')));
      Navigator.of(context).pop(true);
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() => _submitting = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error.message)));
    } catch (_) {
      if (!mounted) return;
      setState(() => _submitting = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('提交失败，请稍后重试')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final detail = _detail;
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        bottom: true,
        child: Column(
          children: [
            _buildHeader(),
            Expanded(child: _buildBody()),
          ],
        ),
      ),
      bottomNavigationBar: detail != null && detail.submittable
          ? _buildSubmitBar(detail)
          : null,
    );
  }

  Widget _buildHeader() {
    return Container(
      color: Colors.white,
      padding: const EdgeInsets.fromLTRB(8, 6, 12, 6),
      child: Row(
        children: [
          IconButton(
            onPressed: () => Navigator.of(context).maybePop(),
            icon: const Icon(
              Icons.arrow_back_ios_new_rounded,
              size: 18,
              color: AppColors.textPrimary,
            ),
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
          ),
          const Expanded(
            child: Text(
              '作业详情',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 17,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
          ),
          const SizedBox(width: 36),
        ],
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return SkeletonShimmer(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
          children: List.generate(
            3,
            (index) => Container(
              height: 130,
              margin: const EdgeInsets.only(bottom: 12),
              decoration: BoxDecoration(
                color: const Color(0xFFE2E8F0),
                borderRadius: BorderRadius.circular(16),
              ),
            ),
          ),
        ),
      );
    }
    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(
                Icons.cloud_off_rounded,
                size: 48,
                color: Color(0xFFCBD5E1),
              ),
              const SizedBox(height: 12),
              Text(
                _error ?? '作业详情加载失败',
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  color: AppColors.textSecondary,
                ),
              ),
              const SizedBox(height: 16),
              TextButton(onPressed: _load, child: const Text('重新加载')),
            ],
          ),
        ),
      );
    }
    final detail = _detail;
    if (detail == null) {
      return const SizedBox.shrink();
    }
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 28),
      physics: const BouncingScrollPhysics(),
      children: [
        _buildTitleCard(detail),
        const SizedBox(height: 12),
        _buildContentCard(detail),
        const SizedBox(height: 12),
        _buildSubmissionCard(detail),
      ],
    );
  }

  Widget _buildTitleCard(HomeworkDetail detail) {
    final status = _statusStyle(detail);
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFF1F5F9)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Flexible(
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.brandSoft,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    detail.courseName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: AppColors.brand,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: status.$2,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  status.$1,
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: status.$3,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            detail.title,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 18,
              fontWeight: FontWeight.bold,
              height: 1.4,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 12),
          _buildInfoRow('发布教师', detail.teacherName ?? '待定'),
          const SizedBox(height: 8),
          _buildInfoRow('发布时间', _formatDateTime(detail.publishAt)),
          const SizedBox(height: 8),
          _buildInfoRow('截止时间', _formatDateTime(detail.dueAt)),
          const SizedBox(height: 8),
          _buildInfoRow(
            '提交状态',
            detail.submitted ? '已提交' : '未提交',
            valueColor: detail.submitted ? AppColors.accentDeep : null,
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value, {Color? valueColor}) {
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
              color: AppColors.textMuted,
            ),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: valueColor ?? AppColors.textBody,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildContentCard(HomeworkDetail detail) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFF1F5F9)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '作业内容',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 14,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            detail.content,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 13,
              height: 1.6,
              color: AppColors.textBody,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSubmissionCard(HomeworkDetail detail) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFF1F5F9)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text(
                '我的提交',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: AppColors.textPrimary,
                ),
              ),
              const Spacer(),
              if (detail.submitted && detail.submittedAt != null)
                Text(
                  _formatDateTime(detail.submittedAt),
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: AppColors.textMuted,
                  ),
                ),
            ],
          ),
          const SizedBox(height: 10),
          if (detail.submittable)
            TextField(
              controller: _contentController,
              minLines: 4,
              maxLines: 8,
              maxLength: 5000,
              enabled: !_submitting,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                height: 1.5,
                color: AppColors.textPrimary,
              ),
              decoration: InputDecoration(
                hintText: detail.submitted
                    ? '已提交，可修改后重新提交'
                    : '请输入作业提交内容，如解题过程、代码、报告链接等',
                hintStyle: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: AppColors.textMuted,
                ),
                filled: true,
                fillColor: const Color(0xFFF8FAFC),
                contentPadding: const EdgeInsets.all(12),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
                counterStyle: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: AppColors.textMuted,
                ),
              ),
            )
          else if (detail.submitted)
            Text(
              detail.submissionContent ?? '',
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                height: 1.6,
                color: AppColors.textBody,
              ),
            )
          else
            const Text(
              '未提交',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                color: AppColors.textMuted,
              ),
            ),
          if (!detail.submittable) ...[
            const SizedBox(height: 10),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(
                color: const Color(0xFFF1F5F9),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Text(
                detail.isClosed ? '作业已关闭，无法再提交或修改' : '已过截止时间，无法再提交或修改',
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: AppColors.textSecondary,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildSubmitBar(HomeworkDetail detail) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: Color(0xFFF1F5F9))),
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: 46,
          child: ElevatedButton(
            onPressed: _submitting ? null : _submit,
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.brand,
              foregroundColor: Colors.white,
              disabledBackgroundColor: const Color(0xFFBFDBFE),
              elevation: 0,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
            child: _submitting
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : Text(
                    detail.submitted ? '更新提交' : '提交作业',
                    style: const TextStyle(
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

  /// 返回（文案, 背景色, 文字色）
  (String, Color, Color) _statusStyle(HomeworkDetail detail) {
    if (detail.isClosed) {
      return ('已关闭', const Color(0xFFF1F5F9), AppColors.textMuted);
    }
    if (detail.isOverdue) {
      return ('已截止', const Color(0xFFF1F5F9), AppColors.textMuted);
    }
    if (detail.submitted) {
      return ('已提交', AppColors.accentSoft, AppColors.accentDeep);
    }
    return ('待提交', AppColors.warningSoft, AppColors.warning);
  }

  String _formatDateTime(DateTime? value) {
    if (value == null) return '待定';
    String two(int number) => number.toString().padLeft(2, '0');
    return '${value.year}-${two(value.month)}-${two(value.day)} '
        '${two(value.hour)}:${two(value.minute)}';
  }
}
