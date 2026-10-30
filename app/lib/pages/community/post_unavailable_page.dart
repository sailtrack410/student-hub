import 'package:flutter/material.dart';

import '../../theme/app_theme.dart';

enum PostUnavailableReason { deleted, hidden, unavailable }

/// 帖子被删除/隐藏后的内容不可见页（知乎式提示）
class PostUnavailablePage extends StatelessWidget {
  final String postId;
  final PostUnavailableReason reason;
  final Future<void> Function()? onRetry;

  /// 作者本人可提交申诉
  final bool canAppeal;
  final VoidCallback? onAppeal;

  const PostUnavailablePage({
    super.key,
    this.postId = '',
    this.reason = PostUnavailableReason.unavailable,
    this.onRetry,
    this.canAppeal = false,
    this.onAppeal,
  });

  String get _title {
    switch (reason) {
      case PostUnavailableReason.deleted:
        return '帖子已被删除';
      case PostUnavailableReason.hidden:
        return '帖子已被隐藏';
      case PostUnavailableReason.unavailable:
        return '该内容暂时不可见';
    }
  }

  String get _description {
    switch (reason) {
      case PostUnavailableReason.deleted:
        return '该帖子已被作者删除，无法再查看。';
      case PostUnavailableReason.hidden:
        return '该内容因违反社区规范被隐藏，仅作者本人可见。';
      case PostUnavailableReason.unavailable:
        return '帖子可能已被作者删除、被社区隐藏，或你没有访问权限。';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(
            Icons.arrow_back_ios_new_rounded,
            size: 20,
            color: Color(0xFF0F172A),
          ),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: const Text(
          '帖子详情',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: Color(0xFF0F172A),
          ),
        ),
        centerTitle: true,
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 36, vertical: 24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _buildIllustration(),
                const SizedBox(height: 26),
                Text(
                  _title,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    color: AppColors.textPrimary,
                  ),
                ),
                const SizedBox(height: 10),
                Text(
                  _description,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 13,
                    height: 1.7,
                    color: Color(0xFF64748B),
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  '如果你认为内容被误处理，可联系社区管理员申诉',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF64748B),
                  ),
                ),
                if (postId.isNotEmpty) ...[
                  const SizedBox(height: 6),
                  Text(
                    '帖子编号：$postId',
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      color: Color(0xFFCBD5E1),
                    ),
                  ),
                ],
                if (canAppeal) ...[
                  const SizedBox(height: 16),
                  SizedBox(
                    width: double.infinity,
                    height: 44,
                    child: OutlinedButton.icon(
                      onPressed: onAppeal,
                      icon: const Icon(Icons.gavel_rounded, size: 18),
                      label: const Text(
                        '我要申诉',
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: AppColors.brand,
                        side: const BorderSide(color: Color(0xFFBFDBFE)),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(22),
                        ),
                      ),
                    ),
                  ),
                ],
                const SizedBox(height: 28),
                SizedBox(
                  width: double.infinity,
                  height: 46,
                  child: ElevatedButton(
                    onPressed: () => Navigator.of(context).maybePop(),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.brand,
                      foregroundColor: Colors.white,
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(23),
                      ),
                    ),
                    child: const Text(
                      '返回社区',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
                if (onRetry != null) ...[
                  const SizedBox(height: 10),
                  SizedBox(
                    width: double.infinity,
                    height: 44,
                    child: OutlinedButton(
                      onPressed: onRetry,
                      style: OutlinedButton.styleFrom(
                        foregroundColor: AppColors.brand,
                        side: const BorderSide(color: Color(0xFFBFDBFE)),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(22),
                        ),
                      ),
                      child: const Text(
                        '刷新试试',
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildIllustration() {
    return SizedBox(
      width: 148,
      height: 132,
      child: Stack(
        alignment: Alignment.center,
        children: [
          Container(
            width: 120,
            height: 120,
            decoration: const BoxDecoration(
              color: Color(0xFFF1F5F9),
              shape: BoxShape.circle,
            ),
          ),
          Container(
            width: 76,
            height: 88,
            padding: const EdgeInsets.fromLTRB(12, 14, 12, 12),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: const Color(0xFFE2E8F0)),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x080F172A),
                  blurRadius: 10,
                  offset: Offset(0, 4),
                ),
              ],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _line(width: 38, height: 6),
                const SizedBox(height: 7),
                _line(width: 52, height: 5),
                const SizedBox(height: 5),
                _line(width: 44, height: 5),
                const SizedBox(height: 5),
                _line(width: 34, height: 5),
              ],
            ),
          ),
          Positioned(
            right: 10,
            bottom: 6,
            child: Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: Colors.white,
                shape: BoxShape.circle,
                border: Border.all(color: const Color(0xFFE2E8F0)),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x0A0F172A),
                    blurRadius: 8,
                    offset: Offset(0, 2),
                  ),
                ],
              ),
              child: const Icon(
                Icons.visibility_off_rounded,
                size: 20,
                color: Color(0xFF64748B),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _line({required double width, required double height}) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: const Color(0xFFE2E8F0),
        borderRadius: BorderRadius.circular(height / 2),
      ),
    );
  }
}
