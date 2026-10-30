import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../models/community_models.dart';
import 'community_report_sheet.dart';

/// APP-社区分享弹层 (对应画布 5:1370 节点，375 × 812)
/// 严格依据 Ardot 设计规范实现社媒渠道行、功能操作行与快捷举报入口
class CommunityShareSheet extends StatelessWidget {
  final CommunityPost post;
  final VoidCallback? onReport;

  const CommunityShareSheet({super.key, required this.post, this.onReport});

  static Future<void> show(BuildContext context, CommunityPost post) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => CommunityShareSheet(post: post),
    );
  }

  Future<void> _copyPost(BuildContext context, {String? platform}) async {
    final text = [
      if (post.title.trim().isNotEmpty) post.title.trim(),
      if (post.content.trim().isNotEmpty) post.content.trim(),
      '帖子 ID：${post.id}',
    ].join('\n');
    await Clipboard.setData(ClipboardData(text: text));
    if (!context.mounted) return;
    Navigator.of(context).pop();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          platform == null ? '帖子内容已复制到剪贴板' : '帖子内容已复制，可粘贴到$platform分享',
        ),
        duration: const Duration(seconds: 1),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  void _handleOpenReport(BuildContext context) {
    Navigator.of(context).pop();
    if (onReport != null) {
      onReport!();
    } else {
      CommunityReportSheet.show(context, post);
    }
  }

  @override
  Widget build(BuildContext context) {
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
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 顶部拖拽指示条 (5:1372 节点)
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

          // 面板标题行 (5:1374 节点)
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: const [
              Text(
                '分享帖子至',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF0F172A),
                ),
              ),
              Text(
                '快喊校园搭子一起来围观',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  fontWeight: FontWeight.w400,
                  color: Color(0xFF64748B),
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),

          // 1. 社媒渠道行 (5:1377 节点)
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _buildShareChannelBtn(
                context: context,
                label: '微信',
                color: const Color(0xFF07C160),
                icon: Icons.chat_bubble_rounded,
                onTap: () => _copyPost(context, platform: '微信'),
              ),
              _buildShareChannelBtn(
                context: context,
                label: '朋友圈',
                color: const Color(0xFF10B981),
                icon: Icons.donut_large_rounded,
                onTap: () => _copyPost(context, platform: '朋友圈'),
              ),
              _buildShareChannelBtn(
                context: context,
                label: 'QQ',
                color: const Color(0xFF0284C7),
                icon: Icons.forum_rounded,
                onTap: () => _copyPost(context, platform: 'QQ'),
              ),
              _buildShareChannelBtn(
                context: context,
                label: 'QQ空间',
                color: const Color(0xFFF59E0B),
                icon: Icons.star_rounded,
                onTap: () => _copyPost(context, platform: 'QQ空间'),
              ),
              _buildShareChannelBtn(
                context: context,
                label: '微博',
                color: const Color(0xFFEF4444),
                icon: Icons.remove_red_eye_rounded,
                onTap: () => _copyPost(context, platform: '微博'),
              ),
            ],
          ),
          const SizedBox(height: 22),

          // 2. 功能操作行 (5:1401 节点)
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _buildActionToolBtn(
                label: '复制内容',
                icon: Icons.link_rounded,
                bgColor: const Color(0xFFF1F5F9),
                iconColor: const Color(0xFF475569),
                textColor: const Color(0xFF475569),
                onTap: () => _copyPost(context),
              ),
              _buildActionToolBtn(
                label: '违规举报',
                icon: Icons.warning_amber_rounded,
                bgColor: const Color(0xFFFEF2F2),
                iconColor: const Color(0xFFEF4444),
                textColor: const Color(0xFFEF4444),
                onTap: () => _handleOpenReport(context),
              ),
            ],
          ),
          const SizedBox(height: 22),

          // 3. 取消按钮
          SizedBox(
            height: 44,
            child: ElevatedButton(
              onPressed: () => Navigator.of(context).pop(),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFFF8FAFC),
                foregroundColor: const Color(0xFF64748B),
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(22),
                ),
              ),
              child: const Text(
                '取消',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildShareChannelBtn({
    required BuildContext context,
    required String label,
    required Color color,
    required IconData icon,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
            child: Icon(icon, color: Colors.white, size: 24),
          ),
          const SizedBox(height: 8),
          Text(
            label,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: Color(0xFF334155),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActionToolBtn({
    required String label,
    required IconData icon,
    required Color bgColor,
    required Color iconColor,
    required Color textColor,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(color: bgColor, shape: BoxShape.circle),
            child: Icon(icon, color: iconColor, size: 22),
          ),
          const SizedBox(height: 8),
          Text(
            label,
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: textColor,
            ),
          ),
        ],
      ),
    );
  }
}
