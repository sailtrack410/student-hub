import 'package:flutter/material.dart';

import '../models/community_models.dart';
import '../theme/app_theme.dart';
import 'community_action_bar.dart';
import 'user_avatar.dart';

/// 卡片只展示数据和发出动作；请求与分页由外部控制器管理。
///
/// V4 画布 30:1 口径（2026-11-05 修订，逐节点对照 30:130/30:157）：
/// - 头部行 32（头像 32 + 昵称 14 + 等级徽章 16 + 分区·时间 11），无关注入口；
/// - 预览格一律 1:1 方形（92×92、圆角 8、间距 8、左对齐）；
/// - 互动条高 20（图标 20 + 文字 12），非激活色 #94A3B8，分享纯图标；
/// - 标准字号下固定两型：无图 162 / 有图 262（=162+8+92 图集，画布标 254 为
///   hug 字高差，取不裁切值）；大字号（textScaler > 1.15）回退自然高度。
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

  /// 标准字号下两型固定高度（画布 30:1 口径：无图 162；有图 = 162 + 8 + 92 图集）；
  /// 大字号（textScaler > 1.15）回退自然高度，保证长文本可读。
  static const double _heightTextOnly = 162;
  static const double _heightWithMedia = 262;

  bool get _hasMedia => post.images.isNotEmpty || post.imageLabels.isNotEmpty;

  bool _useFixedHeight(BuildContext context) =>
      MediaQuery.textScalerOf(context).scale(14) <= 14 * 1.15;

  /// B1 验收钩子：debug 下的构建计数（assert 内自增，release 零成本）。
  /// 互动重建粒度测试据此断言「点赞只重建 1 张卡」。
  static int debugBuildCount = 0;
  static final Map<String, int> debugBuildsByPostId = <String, int>{};

  @override
  Widget build(BuildContext context) {
    assert(() {
      debugBuildCount++;
      debugBuildsByPostId[post.id] = (debugBuildsByPostId[post.id] ?? 0) + 1;
      return true;
    }());
    final fixedHeight = _useFixedHeight(context)
        ? (_hasMedia ? _heightWithMedia : _heightTextOnly)
        : null;
    return GestureDetector(
      onTap: () => onOpen(),
      behavior: HitTestBehavior.opaque,
      child: Container(
        height: fixedHeight,
        clipBehavior: Clip.hardEdge,
        decoration: const BoxDecoration(color: Colors.white),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildHeaderRow(),
            const SizedBox(height: 8),
            _buildTitleRow(),
            const SizedBox(height: 6),
            _buildSummary(),
            if (_hasMedia) ...[
              const SizedBox(height: 8),
              SizedBox(height: 92, width: double.infinity, child: _buildImageRow(context, post)),
            ],
            const SizedBox(height: 8),
            _buildTopicRow(),
            const SizedBox(height: 8),
            SizedBox(
              height: 20,
              width: double.infinity,
              child: CommunityPostActionBar(
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
            ),
          ],
        ),
      ),
    );
  }

  /// 头部行：头像 32 内联，昵称/徽章/分区·时间一行排完，右侧更多（画布 30:131，行高 32，无关注入口）。
  Widget _buildHeaderRow() {
    final metaText = [
      if (post.partition.isNotEmpty) post.partition,
      post.timeAgo,
    ].join(' · ');
    return SizedBox(
      height: 32,
      child: Row(
        children: [
          GestureDetector(
            onTap: () => onAuthor(),
            behavior: HitTestBehavior.opaque,
            child: UserAvatar(
              name: post.authorName,
              avatarPath: post.authorAvatar,
              size: 32,
              fontSize: 13,
              gradientColors: [post.avatarBgColor, post.levelTextColor],
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            // 昵称封顶 45% 兜住超长名，meta 作唯一 flex 子项吃真剩余，
            // 保证「分区 · 时间」完整显示；禁用多 Flexible 抢份额（会按比例预分空间）。
            child: LayoutBuilder(
              builder: (context, constraints) {
                final maxNameWidth = constraints.maxWidth * 0.45;
                return Row(
                  children: [
                    ConstrainedBox(
                      constraints: BoxConstraints(maxWidth: maxNameWidth),
                      child: GestureDetector(
                        onTap: () => onAuthor(),
                        behavior: HitTestBehavior.opaque,
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
                    ),
                    const SizedBox(width: 6),
                    // 徽章内容恒短（官方/Lv.X），不参与抢占，避免挤压缩略
                    _buildLevelBadge(),
                    if (post.badgeAsset != null && post.badgeAsset!.isNotEmpty) ...[
                      const SizedBox(width: 4),
                      Image.asset(
                        post.badgeAsset!,
                        width: 18,
                        height: 18,
                        errorBuilder: (_, _, _) => const SizedBox.shrink(),
                      ),
                    ],
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        metaText,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 11,
                          color: AppColors.textTertiary,
                        ),
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
          _buildMoreButton(),
        ],
      ),
    );
  }

  /// 等级/官方徽章：画布 30:136 裹内容短胶囊（高 16、水平 pad 6、r8、字号 10）。
  /// 注意不可用 Container+alignment：有界约束下会撑满行宽/行高而非裹内容。
  Widget _buildLevelBadge() {
    return Container(
      key: const ValueKey('community-level-badge'),
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
      decoration: BoxDecoration(
        color: post.authorOfficial ? const Color(0xFFEFF6FF) : post.levelBgColor,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        post.authorOfficial ? '官方' : post.authorLevel,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          fontFamily: 'Noto Sans SC',
          fontSize: 10,
          fontWeight: FontWeight.bold,
          color: post.authorOfficial
              ? const Color(0xFF1D4ED8)
              : post.levelTextColor,
        ),
      ),
    );
  }

  /// 更多按钮：画布 30:139 为 20×20 裸图标实例，行高 32 内右对齐。
  Widget _buildMoreButton() {
    return Tooltip(
      message: '帖子更多操作',
      child: GestureDetector(
        onTap: () => onMore(),
        behavior: HitTestBehavior.opaque,
        child: const SizedBox(
          width: 20,
          height: 32,
          child: Icon(
            Icons.more_horiz_rounded,
            size: 20,
            color: AppColors.textTertiary,
          ),
        ),
      ),
    );
  }

  /// 标题行：文章帖在标题前内联「文章」小标，保持单行高度。
  Widget _buildTitleRow() {
    return SizedBox(
      height: 20,
      width: double.infinity,
      child: Row(
        children: [
          if (post.postType == 'ARTICLE') ...[
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
              decoration: BoxDecoration(
                color: const Color(0xFFF3E8FF),
                borderRadius: BorderRadius.circular(4),
              ),
              child: const Text(
                '文章',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 10,
                  color: Color(0xFF7C3AED),
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
            const SizedBox(width: 6),
          ],
          Expanded(
            child: Text(
              post.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 15,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
                height: 1.3,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSummary() {
    return SizedBox(
      height: 18,
      width: double.infinity,
      child: Align(
        alignment: Alignment.centerLeft,
        child: Text(
          post.content,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 13,
            color: AppColors.textBody,
            height: 1.35,
          ),
        ),
      ),
    );
  }

  /// 话题行：单行纯文字蓝标，超出裁切（高度固定不换行）。
  Widget _buildTopicRow() {
    if (post.topics.isEmpty) return const SizedBox(height: 18);
    return SizedBox(
      height: 18,
      width: double.infinity,
      child: Wrap(
        clipBehavior: Clip.hardEdge,
        spacing: 8,
        children: post.topics
            .map(
              (topic) => Text(
                '# $topic',
                maxLines: 1,
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: AppColors.brand,
                  fontWeight: FontWeight.w500,
                  height: 1.4,
                ),
              ),
            )
            .toList(),
      ),
    );
  }

  /// 图集：画布 30:142 口径（高 92、间距 8、圆角 8），预览格一律 1:1 正方形，
  /// 左对齐不摊满整行。
  Widget _buildImageRow(BuildContext context, CommunityPost post) {
    final labels = post.imageLabels;
    final urls = post.images;
    final count = urls.isNotEmpty ? urls.length : labels.length;
    if (count == 0) return const SizedBox.shrink();
    final visible = count.clamp(1, 3);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: List.generate(visible, (index) {
        final label = index < labels.length ? labels[index] : '校园图片';
        final url = index < urls.length ? urls[index] : null;
        return Container(
          key: ValueKey('community-image-cell-$index'),
          width: 92,
          height: 92,
          margin: EdgeInsets.only(right: index == visible - 1 ? 0 : 8),
          decoration: BoxDecoration(
            color: const Color(0xFFF1F5F9),
            borderRadius: BorderRadius.circular(8),
          ),
          clipBehavior: Clip.antiAlias,
          alignment: Alignment.center,
          child: url != null
              ? Image.network(
                  url,
                  width: 92,
                  height: 92,
                  fit: BoxFit.cover,
                  // B3：按显示尺寸×像素密度解码，避免整图解码后再缩放的内存/耗时
                  cacheWidth: (92 * MediaQuery.devicePixelRatioOf(context)).round(),
                  frameBuilder: communityImageFrameBuilder,
                  errorBuilder: (_, _, _) => _imageLabel(label),
                )
              : _imageLabel(label),
        );
      }),
    );
  }

  Widget _imageLabel(String label) {
    return Text(
      label,
      textAlign: TextAlign.center,
      style: const TextStyle(
        fontFamily: 'Noto Sans SC',
        fontSize: 12,
        color: AppColors.brand,
        fontWeight: FontWeight.w500,
      ),
    );
  }
}

/// B3 图片加载淡入：首帧未就绪时全透明，解码完成后 200ms easeOutCubic 淡入，
/// 避免图片突兀闪现；内存缓存命中（wasSynchronouslyLoaded）与禁用动画时直接显示。
/// 信息流图集与详情页图集共用（回归测试直接断言本函数行为）。
Widget communityImageFrameBuilder(
  BuildContext context,
  Widget child,
  int? frame,
  bool wasSynchronouslyLoaded,
) {
  if (wasSynchronouslyLoaded) return child;
  return AnimatedOpacity(
    opacity: frame == null ? 0 : 1,
    duration: MediaQuery.disableAnimationsOf(context)
        ? Duration.zero
        : const Duration(milliseconds: 200),
    curve: Curves.easeOutCubic,
    child: child,
  );
}
