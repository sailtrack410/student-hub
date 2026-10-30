import 'package:flutter/material.dart';

/// APP-内容治理占位卡与违规提示组件 (对应移动端功能说明 4.1 节)
/// 严格依据 Ardot 设计规范实现违规占位、审核中占位与禁言处罚横幅

/// 1. 内容已被下架/删除占位卡 (他人视角或公开列表)
class ContentRemovedPlaceholder extends StatelessWidget {
  final String message;

  const ContentRemovedPlaceholder({
    super.key,
    this.message = '该内容因违反《绵城校园社区文明公约》已被管理员清理下架',
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: const BoxDecoration(
              color: Color(0xFFF1F5F9),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.visibility_off_rounded,
              size: 22,
              color: Color(0xFF94A3B8),
            ),
          ),
          const SizedBox(height: 10),
          const Text(
            '内容不可见',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 14,
              fontWeight: FontWeight.bold,
              color: Color(0xFF475569),
            ),
          ),
          const SizedBox(height: 6),
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
              height: 1.4,
            ),
          ),
        ],
      ),
    );
  }
}

/// 2. 仅自己可见/人工审核中提示卡 (作者视角)
class ContentUnderReviewCard extends StatelessWidget {
  final String reviewReason;

  const ContentUnderReviewCard({
    super.key,
    this.reviewReason = '系统检测到敏感广告特征，人工安全审核中，目前仅自己可见',
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: const Color(0xFFFEF3C7),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFFDE68A)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            Icons.warning_amber_rounded,
            size: 18,
            color: Color(0xFFD97706),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '审核中 (仅自己可见)',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFFB45309),
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  reviewReason,
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF92400E),
                    height: 1.35,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// 3. 用户被禁言处罚横幅 (发帖/评论入口置顶提示)
class UserPenaltyBanner extends StatelessWidget {
  final String durationText;
  final String reason;

  const UserPenaltyBanner({
    super.key,
    this.durationText = '截至 2026-10-24 12:00',
    this.reason = '发布虚假代考诈骗信息',
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      color: const Color(0xFFFEF2F2),
      child: Row(
        children: [
          const Icon(Icons.block_rounded, size: 18, color: Color(0xFFEF4444)),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              '因[$reason]，账号已被限制在社区公开发言 ($durationText)',
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: Color(0xFFB91C1C),
                fontWeight: FontWeight.w500,
              ),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}
