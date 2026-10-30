import 'package:flutter/material.dart';

/// 社区动态多功能底部互动组件 (转发/评论/收藏/点赞)
/// 严格依据移动端主流社交互动规范设计，支持高频点击动效、防抖微动画与状态响应
class CommunityPostActionBar extends StatelessWidget {
  final int shareCount;
  final int commentCount;
  final int likeCount;
  final bool isLiked;
  final bool isBookmarked;
  final VoidCallback? onShare;
  final VoidCallback? onComment;
  final VoidCallback? onBookmark;
  final VoidCallback? onLike;
  final bool showBookmark;
  final bool likePending;
  final bool bookmarkPending;
  final double iconSize;
  final double fontSize;

  const CommunityPostActionBar({
    super.key,
    required this.shareCount,
    required this.commentCount,
    required this.likeCount,
    this.isLiked = false,
    this.isBookmarked = false,
    this.onShare,
    this.onComment,
    this.onBookmark,
    this.onLike,
    this.showBookmark = true,
    this.likePending = false,
    this.bookmarkPending = false,
    this.iconSize = 18,
    this.fontSize = 12,
  });

  @override
  Widget build(BuildContext context) {
    const defaultColor = Color(0xFF64748B);
    final reducedMotion = MediaQuery.disableAnimationsOf(context);
    final bookmarkLabel = isBookmarked ? '已收藏' : '收藏';

    Widget action({
      Key? key,
      String? label,
      required String semantics,
      required IconData icon,
      required VoidCallback? onTap,
      Color color = defaultColor,
      bool? selected,
      bool pending = false,
    }) {
      return Semantics(
        key: key,
        label: pending ? '$semantics，正在保存' : semantics,
        button: true,
        toggled: selected,
        enabled: onTap != null && !pending,
        child: ExcludeSemantics(
          child: InkWell(
            onTap: pending ? null : onTap,
            borderRadius: BorderRadius.circular(8),
            child: Container(
              constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  AnimatedScale(
                    scale: reducedMotion ? 1 : (selected == true ? 1.15 : 1),
                    duration: reducedMotion
                        ? Duration.zero
                        : const Duration(milliseconds: 150),
                    child: pending && !reducedMotion
                        ? SizedBox(
                            width: iconSize,
                            height: iconSize,
                            child: const CircularProgressIndicator(
                              strokeWidth: 1.5,
                            ),
                          )
                        : Icon(
                            pending ? Icons.hourglass_empty : icon,
                            size: iconSize,
                            color: color,
                          ),
                  ),
                  if (label != null) ...[
                    const SizedBox(width: 5),
                    Text(
                      label,
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: fontSize < 12 ? 12 : fontSize,
                        color: color,
                        fontWeight: selected == true
                            ? FontWeight.bold
                            : FontWeight.normal,
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

    return SizedBox(
      width: double.infinity,
      child: Wrap(
        alignment: WrapAlignment.spaceBetween,
        spacing: 8,
        children: [
          action(
            label: shareCount > 0 ? '$shareCount' : '转发',
            semantics: '转发，$shareCount 次',
            icon: Icons.share_outlined,
            onTap: onShare,
          ),
          action(
            label: commentCount > 0 ? '$commentCount' : '评论',
            semantics: '评论，$commentCount 条',
            icon: Icons.chat_bubble_outline_rounded,
            onTap: onComment,
          ),
          if (showBookmark)
            action(
              key: const ValueKey('community-bookmark'),
              semantics: bookmarkLabel,
              icon: isBookmarked
                  ? Icons.star_rounded
                  : Icons.star_border_rounded,
              color: isBookmarked ? const Color(0xFF92400E) : defaultColor,
              onTap: onBookmark,
              selected: isBookmarked,
              pending: bookmarkPending,
            ),
          action(
            label: likeCount > 0 ? '$likeCount' : '点赞',
            semantics: '${isLiked ? '取消点赞' : '点赞'}，$likeCount 人点赞',
            icon: isLiked
                ? Icons.favorite_rounded
                : Icons.favorite_border_rounded,
            color: isLiked ? const Color(0xFFB91C1C) : defaultColor,
            onTap: onLike,
            selected: isLiked,
            pending: likePending,
          ),
        ],
      ),
    );
  }
}
