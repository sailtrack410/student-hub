import 'package:flutter/material.dart';

import '../models/community_models.dart';
import '../theme/app_theme.dart';
import 'community_action_bar.dart';
import 'user_avatar.dart';

/// 卡片只展示数据和发出动作；请求与分页由外部控制器管理。
class CommunityPostCard extends StatelessWidget {
  const CommunityPostCard({
    super.key,
    required this.post,
    required this.accountId,
    required this.likePending,
    required this.followPending,
    required this.bookmarkPending,
    required this.onOpen,
    required this.onAuthor,
    required this.onFollow,
    required this.onMore,
    required this.onShare,
    required this.onBookmark,
    required this.onLike,
  });
  final CommunityPost post;
  final String accountId;
  final bool likePending, followPending, bookmarkPending;
  final VoidCallback onOpen,
      onAuthor,
      onFollow,
      onMore,
      onShare,
      onBookmark,
      onLike;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => onOpen(),
      behavior: HitTestBehavior.opaque,
      child: Container(
        color: Colors.white,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 发帖人行
            Row(
              children: [
                GestureDetector(
                  onTap: () => onAuthor(),
                  child: UserAvatar(
                    name: post.authorName,
                    avatarPath: post.authorAvatar,
                    size: 40,
                    fontSize: 16,
                    gradientColors: [post.avatarBgColor, post.levelTextColor],
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Wrap(
                        spacing: 6,
                        runSpacing: 4,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          GestureDetector(
                            onTap: () => onAuthor(),
                            child: Text(
                              post.authorName,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontFamily: 'Noto Sans SC',
                                fontSize: 14,
                                fontWeight: FontWeight.bold,
                                color: AppColors.textPrimary,
                              ),
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 5,
                              vertical: 1.5,
                            ),
                            decoration: BoxDecoration(
                              color: post.authorOfficial
                                  ? const Color(0xFFEFF6FF)
                                  : post.levelBgColor,
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Text(
                              post.authorOfficial
                                  ? '官方 · ${post.authorOfficialTitle ?? "官方"}'
                                  : post.authorLevel,
                              style: TextStyle(
                                fontFamily: 'Noto Sans SC',
                                fontSize: 12,
                                fontWeight: FontWeight.bold,
                                color: post.authorOfficial
                                    ? const Color(0xFF1D4ED8)
                                    : post.levelTextColor,
                              ),
                            ),
                          ),
                          if (post.badgeAsset != null &&
                              post.badgeAsset!.isNotEmpty) ...[
                            const SizedBox(width: 4),
                            Image.asset(
                              post.badgeAsset!,
                              width: 18,
                              height: 18,
                              errorBuilder: (_, _, _) =>
                                  const SizedBox.shrink(),
                            ),
                          ],
                        ],
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${post.timeAgo} · ${post.location}',
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 12,
                          color: Color(0xFF64748B),
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
                if (post.authorId != accountId)
                  GestureDetector(
                    onTap: followPending ? null : () => onFollow(),
                    child: Container(
                      constraints: const BoxConstraints(
                        minWidth: 64,
                        minHeight: 48,
                      ),
                      alignment: Alignment.center,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 5,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFFEFF6FF),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Text(
                        followPending
                            ? '处理中'
                            : (post.isFollowed ? '已关注' : '+ 关注'),
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: AppColors.brand,
                        ),
                      ),
                    ),
                  ),
                const SizedBox(width: 6),
                IconButton(
                  icon: const Icon(
                    Icons.more_horiz_rounded,
                    size: 20,
                    color: Color(0xFF64748B),
                  ),
                  onPressed: () => onMore(),
                  tooltip: '帖子更多操作',
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(
                    minWidth: 48,
                    minHeight: 48,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),

            // 标题
            if (post.postType == 'ARTICLE')
              Padding(
                padding: const EdgeInsets.only(bottom: 4),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 6,
                    vertical: 1,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF3E8FF),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: const Text(
                    '文章',
                    style: TextStyle(
                      fontSize: 12,
                      color: Color(0xFF7C3AED),
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ),
            Text(
              post.title,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 15,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
                height: 1.4,
              ),
            ),
            const SizedBox(height: 6),

            // 正文摘要
            Text(
              post.content,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                color: Color(0xFF64748B),
                height: 1.5,
              ),
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
            ),

            // 图片区
            if (post.images.isNotEmpty || post.imageLabels.isNotEmpty) ...[
              const SizedBox(height: 10),
              _buildImageRow(post),
            ],

            // 话题标签
            if (post.topics.isNotEmpty) ...[
              const SizedBox(height: 10),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: post.topics.map((topic) {
                  return Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 3,
                    ),
                    decoration: BoxDecoration(
                      color: const Color(0xFFEFF6FF),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      '# $topic',
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        color: Color(0xFF2563EB),
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
            const SizedBox(height: 12),

            // 互动栏
            CommunityPostActionBar(
              shareCount: post.shareCount,
              commentCount: post.commentCount,
              likeCount: post.likeCount,
              isLiked: post.isLiked,
              isBookmarked: post.isBookmarked,
              likePending: likePending,
              bookmarkPending: bookmarkPending,
              onShare: () => onShare(),
              onComment: () => onOpen(),
              onBookmark: bookmarkPending ? null : () => onBookmark(),
              onLike: likePending ? null : () => onLike(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildImageRow(CommunityPost post) {
    final labels = post.imageLabels;
    final urls = post.images;
    final count = urls.isNotEmpty ? urls.length : labels.length;
    if (count == 0) return const SizedBox.shrink();
    final visible = count.clamp(1, 3);
    return LayoutBuilder(
      builder: (context, constraints) {
        final gap = 8.0;
        final width = visible == 1
            ? 104.0
            : (constraints.maxWidth - gap * (visible - 1)) / visible;
        return Row(
          children: List.generate(visible, (index) {
            final label = index < labels.length ? labels[index] : '校园图片';
            final url = index < urls.length ? urls[index] : null;
            return Container(
              width: width,
              height: 104,
              margin: EdgeInsets.only(right: index == visible - 1 ? 0 : gap),
              decoration: BoxDecoration(
                color: const Color(0xFFF1F5F9),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              clipBehavior: Clip.antiAlias,
              alignment: Alignment.center,
              padding: const EdgeInsets.all(4),
              child: url != null
                  ? Image.network(
                      url,
                      width: width,
                      height: 104,
                      fit: BoxFit.cover,
                      errorBuilder: (_, _, _) => _imageLabel(label),
                    )
                  : _imageLabel(label),
            );
          }),
        );
      },
    );
  }

  Widget _imageLabel(String label) {
    return Text(
      label,
      textAlign: TextAlign.center,
      style: const TextStyle(
        fontFamily: 'Noto Sans SC',
        fontSize: 12,
        color: Color(0xFF2563EB),
        fontWeight: FontWeight.w500,
      ),
    );
  }
}
