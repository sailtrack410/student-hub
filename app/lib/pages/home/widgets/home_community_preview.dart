import 'package:flutter/material.dart';

import '../../../models/community_models.dart';
import '../../../theme/app_theme.dart';

class HomeCommunityPreview extends StatelessWidget {
  final CommunityPost? post;
  final String baseUrl;
  final bool liked;
  final int likeCount;
  final bool likePending;
  final VoidCallback onOpen;
  final VoidCallback onLike;
  final List<CommunityPost> morePosts;
  final ValueChanged<CommunityPost>? onOpenPost;

  const HomeCommunityPreview({
    super.key,
    this.post,
    required this.baseUrl,
    required this.liked,
    required this.likeCount,
    required this.likePending,
    required this.onOpen,
    required this.onLike,
    this.morePosts = const [],
    this.onOpenPost,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(22),
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFFE7F2FF), Color(0xFFF0EDFF)],
          ),
          border: Border.all(color: const Color(0xFFD9E7FF)),
        ),
        clipBehavior: Clip.antiAlias,
        child: Stack(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
              child: post == null
                  ? _EmptyFeed(onOpen: onOpen)
                  : Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        _LeadPost(
                          post: post!,
                          liked: liked,
                          likeCount: likeCount,
                          likePending: likePending,
                          onOpen: onOpen,
                          onLike: onLike,
                        ),
                        for (final item in morePosts.take(2))
                          _MorePostTile(
                            key: ValueKey('home-post-${item.id}'),
                            post: item,
                            onOpen: () => onOpenPost?.call(item),
                          ),
                      ],
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LeadPost extends StatelessWidget {
  final CommunityPost post;
  final bool liked;
  final int likeCount;
  final bool likePending;
  final VoidCallback onOpen;
  final VoidCallback onLike;

  const _LeadPost({
    required this.post,
    required this.liked,
    required this.likeCount,
    required this.likePending,
    required this.onOpen,
    required this.onLike,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(13, 7, 5, 3),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.9),
        borderRadius: BorderRadius.circular(17),
        border: Border.all(color: Colors.white),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: InkWell(
              onTap: onOpen,
              borderRadius: BorderRadius.circular(10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '${post.authorName} · ${post.commentCount} 条讨论',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 11,
                      color: AppColors.textSecondary,
                    ),
                  ),
                  const SizedBox(height: 7),
                  Text(
                    post.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: AppColors.textPrimary,
                      height: 1.4,
                    ),
                  ),
                ],
              ),
            ),
          ),
          Semantics(
            button: true,
            selected: liked,
            label: liked ? '取消点赞' : '点赞',
            child: InkWell(
              key: const ValueKey('home-community-like'),
              onTap: likePending ? null : onLike,
              borderRadius: BorderRadius.circular(12),
              child: SizedBox(
                width: 48,
                height: 48,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      liked
                          ? Icons.favorite_rounded
                          : Icons.favorite_border_rounded,
                      size: 18,
                      color: liked ? AppColors.danger : AppColors.textSecondary,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '$likeCount',
                      style: TextStyle(
                        fontSize: 11,
                        color: liked
                            ? AppColors.danger
                            : AppColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _MorePostTile extends StatelessWidget {
  final CommunityPost post;
  final VoidCallback onOpen;

  const _MorePostTile({super.key, required this.post, required this.onOpen});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onOpen,
      borderRadius: BorderRadius.circular(12),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 36),
        child: Row(
          children: [
            Container(
              width: 4,
              height: 18,
              decoration: BoxDecoration(
                color: AppColors.brand.withValues(alpha: 0.45),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                post.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textPrimary,
                ),
              ),
            ),
            const SizedBox(width: 8),
            Text(
              '${post.commentCount} 讨论',
              style: const TextStyle(
                fontSize: 11,
                color: AppColors.textSecondary,
              ),
            ),
            const Icon(
              Icons.chevron_right_rounded,
              size: 19,
              color: AppColors.textSecondary,
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyFeed extends StatelessWidget {
  final VoidCallback onOpen;
  const _EmptyFeed({required this.onOpen});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onOpen,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        height: 64,
        padding: const EdgeInsets.symmetric(horizontal: 14),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.86),
          borderRadius: BorderRadius.circular(16),
        ),
        child: const Row(
          children: [
            Icon(Icons.forum_outlined, color: AppColors.brand),
            SizedBox(width: 10),
            Expanded(
              child: Text(
                '去社区看看最新分享',
                style: TextStyle(fontSize: 13, color: AppColors.textBody),
              ),
            ),
            Icon(Icons.chevron_right_rounded, color: AppColors.textSecondary),
          ],
        ),
      ),
    );
  }
}
