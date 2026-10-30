import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/community_api.dart';
import '../../models/community_models.dart';
import '../../widgets/user_avatar.dart';
import 'author_profile_page.dart';
import 'community_report_sheet.dart';
import 'community_share_sheet.dart';
import 'content_status_pages.dart';
import 'post_unavailable_page.dart';
import '../../services/recently_viewed_store.dart';
import 'post_appeal_page.dart';
import '../../models/user_session.dart';
import '../../services/community_interaction_store.dart';

/// APP-帖子详情 (对应画布 5:316 节点，375 × 812)
/// 严格依据 Ardot 设计规范实现长文排版、热度/时间评论筛选、自然键盘顶起评论栏及规范操作菜单
class PostDetailPage extends StatefulWidget {
  final CommunityPost post;
  final CommunityInteractionStore? interactionStore;

  const PostDetailPage({super.key, required this.post, this.interactionStore});

  @override
  State<PostDetailPage> createState() => _PostDetailPageState();
}

class _PostDetailPageState extends State<PostDetailPage> {
  late CommunityPost _post;
  late List<CommunityComment> _comments;
  bool _isSortByHot = true; // true: 按热度, false: 按时间
  final TextEditingController _commentInputController = TextEditingController();
  final FocusNode _commentFocusNode = FocusNode();
  bool _isComposing = false;
  /// 当前正在回复的楼中楼父评论；null = 发表顶层评论
  CommunityComment? _replyTo;
  final Map<String, int> _replyDisplayCounts = <String, int>{};
  bool _loadingComments = false;
  bool _sendingComment = false;
  bool _unavailable = false;
  bool _loadingBookmark = true;
  late final CommunityInteractionStore _interactions;

  @override
  void initState() {
    super.initState();
    _post = widget.post;
    _interactions =
        widget.interactionStore ??
        CommunityInteractionScope.acquire(UserSession.currentUserId ?? 'guest');
    _post = _interactions.apply(_post);
    _interactions.addListener(_onInteractionsChanged);
    _initializeBookmark();
    RecentlyViewedStore.add(_post);
    // mock 帖子保留内置评论用于设计预览/测试；真实帖子走详情接口
    _comments = _post.id.startsWith('post_')
        ? CommunityMockData.getPostComments(_post.id)
        : [];
    _interactions.refreshFollowing([_post.authorId]);
    _loadDetail();
    _commentInputController.addListener(() {
      final isNowComposing = _commentInputController.text.trim().isNotEmpty;
      if (_isComposing != isNowComposing) {
        setState(() => _isComposing = isNowComposing);
      }
    });
  }

  Future<void> _initializeBookmark() async {
    await _interactions.initialize();
    if (!mounted) return;
    setState(() {
      _loadingBookmark = false;
      _post = _interactions.apply(_post);
    });
  }

  void _onInteractionsChanged() {
    if (!mounted) return;
    setState(() {
      _post = _interactions.apply(_post);
    });
  }

  Future<void> _loadDetail() async {
    if (!RegExp(r'^\d+$').hasMatch(_post.id)) return;
    setState(() => _loadingComments = true);
    final startedAt = _interactions.revision;
    final result = await CommunityApi.fetchPostDetailResult(_post.id);
    if (!mounted) return;
    if (result.notFound) {
      setState(() {
        _loadingComments = false;
        _unavailable = true;
      });
      return;
    }
    final detail = result.detail;
    setState(() {
      _loadingComments = false;
      if (detail != null) {
        _unavailable = false;
        _interactions.acceptPage([detail.post], startedAt);
        _post = _interactions.apply(
          detail.post.copyWith(isFollowed: _post.isFollowed),
        );
        _comments = detail.comments;
      }
    });
  }

  @override
  void dispose() {
    _interactions.removeListener(_onInteractionsChanged);
    if (widget.interactionStore == null) {
      CommunityInteractionScope.release(_interactions);
    }
    _commentInputController.dispose();
    _commentFocusNode.dispose();
    super.dispose();
  }

  void _showInteractionMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..removeCurrentSnackBar()
      ..showSnackBar(
        SnackBar(content: Text(message), behavior: SnackBarBehavior.floating),
      );
  }

  Future<void> _handleToggleFollow() async {
    if (_interactions.followPending(_post.authorId)) return;
    final confirmed = await _interactions.toggleFollowing(_post);
    if (!mounted) return;
    _showInteractionMessage(
      confirmed
          ? (_post.isFollowed ? '已关注 ${_post.authorName}' : '已取消关注')
          : '关注操作失败，已恢复显示，请重试',
    );
  }

  Future<void> _handleToggleLike() async {
    if (_interactions.likePending(_post.id)) return;
    final confirmed = await _interactions.toggleLike(_post);
    if (!confirmed) _showInteractionMessage('点赞结果未确认，已恢复显示，请刷新后重试');
  }

  Future<void> _handleToggleBookmark() async {
    if (_loadingBookmark || _interactions.bookmarkPending(_post.id)) return;
    final saved = await _interactions.toggleBookmark(_post);
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..removeCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(
            saved
                ? (_post.isBookmarked ? '已收藏' : '已取消收藏')
                : '收藏操作失败，请重试',
          ),
          duration: const Duration(seconds: 1),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      );
  }

  void _handleToggleCommentLike(int index) async {
    final c = _sortedComments[index];
    setState(() => _replaceComment(c, _optimisticLike(c)));
    final confirmed = await CommunityApi.toggleCommentLike(
      postId: _post.id,
      commentId: c.id,
    );
    if (!confirmed && mounted) {
      setState(() => _replaceComment(c, c));
      _showInteractionMessage('点赞失败，已恢复原状态，请重试');
    }
  }

  void _replaceComment(CommunityComment old, CommunityComment replacement) {
    final index = _comments.indexWhere((comment) => comment.id == old.id);
    if (index < 0) return;
    _comments[index] = replacement;
  }

  /// 当前排序下的顶层评论视图：最热 = 点赞数降序（同数按 id 数值新在前），
  /// 最新 = 按服务端时间序倒转。原 _comments 保持时间正序不变。
  List<CommunityComment> get _sortedComments {
    if (!_isSortByHot) {
      return _comments.reversed.toList(growable: false);
    }
    final sorted = _comments.toList()
      ..sort((a, b) {
        final byLikes = b.likeCount.compareTo(a.likeCount);
        if (byLikes != 0) return byLikes;
        // 同点赞数：id 是服务端自增 long，按数值比较避免字符串位数错序。
        return (int.tryParse(b.id) ?? 0).compareTo(int.tryParse(a.id) ?? 0);
      });
    return sorted;
  }

  void _toggleReplyLike(CommunityComment reply) async {
    final index = _comments.indexWhere(
      (comment) => comment.replies.any((item) => item.id == reply.id),
    );
    if (index < 0) return;
    final parent = _comments[index];
    final replyIndex = parent.replies.indexWhere((item) => item.id == reply.id);
    setState(() {
      _comments[index] = parent.copyWith(
        replies: [
          ...parent.replies.sublist(0, replyIndex),
          _optimisticLike(reply),
          ...parent.replies.sublist(replyIndex + 1),
        ],
      );
    });
    final confirmed = await CommunityApi.toggleCommentLike(
      postId: _post.id,
      commentId: reply.id,
    );
    if (!confirmed && mounted) {
      setState(() {
        final current = _comments[index];
        final currentIndex = current.replies.indexWhere(
          (item) => item.id == reply.id,
        );
        if (currentIndex >= 0) {
          _comments[index] = current.copyWith(
            replies: [
              ...current.replies.sublist(0, currentIndex),
              reply,
              ...current.replies.sublist(currentIndex + 1),
            ],
          );
        }
      });
      _showInteractionMessage('点赞失败，已恢复原状态，请重试');
    }
  }

  CommunityComment _optimisticLike(CommunityComment comment) => comment.copyWith(
    isLiked: !comment.isLiked,
    likeCount: comment.likeCount + (comment.isLiked ? -1 : 1),
  );

  /// 「不感兴趣」：仅把此帖从当前设备的信息流隐藏（首页/分区列表过滤）。
  /// 不影响分享、作者主页、搜索等其他入口进入本帖——这是本地隐藏的边界。
  /// 写入成功才提示，失败如实报错，可撤销。
  Future<void> _hideFromFeed() async {
    final saved = await _interactions.setHidden(_post.id, true);
    if (!mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    if (!saved) {
      messenger
        ..removeCurrentSnackBar()
        ..showSnackBar(
          const SnackBar(
            content: Text('隐藏设置保存失败，请重试'),
            behavior: SnackBarBehavior.floating,
          ),
        );
      return;
    }
    messenger
      ..removeCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: const Text('已在信息流隐藏此帖（仅本机，不影响分享与主页查看）'),
          behavior: SnackBarBehavior.floating,
          duration: const Duration(seconds: 4),
          action: SnackBarAction(
            label: '撤销',
            onPressed: () async {
              final restored = await _interactions.setHidden(_post.id, false);
              if (!mounted) return;
              if (!restored) {
                _showInteractionMessage('撤销失败，请重试');
              }
            },
          ),
        ),
      );
  }

  /// 点击评论的"回复"：输入框进入楼中楼模式并聚焦
  void _startReply(CommunityComment comment) {
    setState(() => _replyTo = comment);
    _commentFocusNode.requestFocus();
  }

  void _cancelReply() {
    setState(() => _replyTo = null);
  }

  void _openAuthorProfile() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => AuthorProfilePage(
          authorId: _post.authorId,
          authorName: _post.authorName,
          avatarBgColor: _post.avatarBgColor,
          authorLevel: _post.authorLevel,
          authorOfficial: _post.authorOfficial,
          authorOfficialTitle: _post.authorOfficialTitle,
          badgeAsset: _post.badgeAsset,
          sourcePost: _post,
          interactionStore: _interactions,
        ),
      ),
    );
  }

  Future<void> _deletePost() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('删除帖子'),
        content: const Text('删除后帖子和全部评论将不可见，确定继续吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('取消'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFFEF4444),
            ),
            child: const Text('确认删除'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    final ok = await CommunityApi.deletePost(_post.id);
    if (!mounted) return;
    if (!ok) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('删除失败，请稍后重试')));
      return;
    }
    Navigator.of(context).pop(true);
  }

  void _openShare() {
    CommunityShareSheet.show(context, _post);
  }

  void _openReport() {
    CommunityReportSheet.show(context, _post);
  }

  void _showMoreActions() {
    final isAuthor =
        UserSession.currentUserId != null &&
        UserSession.currentUserId == _post.authorId;
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Material(
        color: Colors.white,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
        clipBehavior: Clip.antiAlias,
        child: Padding(
          padding: EdgeInsets.only(
            top: 16,
            bottom: MediaQuery.of(ctx).padding.bottom + 12,
            left: 16,
            right: 16,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: const Color(0xFFE2E8F0),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 16),
              ListTile(
                leading: const Icon(
                  Icons.share_outlined,
                  color: Color(0xFF334155),
                ),
                title: const Text(
                  '分享给同学 / 社交平台',
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
                ),
                onTap: () {
                  Navigator.of(ctx).pop();
                  _openShare();
                },
              ),
              ListTile(
                leading: const Icon(
                  Icons.link_rounded,
                  color: Color(0xFF334155),
                ),
                title: const Text(
                  '复制帖子内容',
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
                ),
                onTap: () async {
                  final text = [
                    if (_post.title.trim().isNotEmpty) _post.title.trim(),
                    if (_post.content.trim().isNotEmpty) _post.content.trim(),
                    '帖子 ID：${_post.id}',
                  ].join('\n');
                  await Clipboard.setData(ClipboardData(text: text));
                  if (!ctx.mounted) return;
                  Navigator.of(ctx).pop();
                  if (!mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('帖子内容已复制到剪贴板'),
                      behavior: SnackBarBehavior.floating,
                    ),
                  );
                },
              ),
              ListTile(
                leading: const Icon(
                  Icons.visibility_off_outlined,
                  color: Color(0xFF334155),
                ),
                title: const Text(
                  '不感兴趣（在信息流隐藏此帖）',
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
                ),
                onTap: () {
                  Navigator.of(ctx).pop();
                  _hideFromFeed();
                },
              ),
              if (isAuthor)
                ListTile(
                  leading: const Icon(
                    Icons.delete_outline_rounded,
                    color: Color(0xFFEF4444),
                  ),
                  title: const Text(
                    '删除帖子',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w500,
                      color: Color(0xFFEF4444),
                    ),
                  ),
                  onTap: () {
                    Navigator.of(ctx).pop();
                    _deletePost();
                  },
                ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(
                  Icons.report_problem_outlined,
                  color: Color(0xFFEF4444),
                ),
                title: const Text(
                  '举报违规内容',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w500,
                    color: Color(0xFFEF4444),
                  ),
                ),
                onTap: () {
                  Navigator.of(ctx).pop();
                  _openReport(); // 仅当用户点击“举报”时才打开举报弹窗
                },
              ),
              const SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                height: 44,
                child: TextButton(
                  onPressed: () => Navigator.of(ctx).pop(),
                  style: TextButton.styleFrom(
                    backgroundColor: const Color(0xFFF1F5F9),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(22),
                    ),
                  ),
                  child: const Text(
                    '取消',
                    style: TextStyle(
                      fontSize: 14,
                      color: Color(0xFF64748B),
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _submitComment() async {
    if (_sendingComment) return;
    final text = _commentInputController.text.trim();
    if (text.isEmpty) return;

    final replyParent = _replyTo;
    setState(() => _sendingComment = true);
    final comment = await CommunityApi.createComment(
      postId: _post.id,
      content: text,
      parentCommentId: replyParent?.id,
    );

    if (!mounted) return;
    setState(() => _sendingComment = false);

    // 接口失败：不插入评论、不增加计数、保留输入内容，仅提示。
    if (comment == null) {
      _showInteractionMessage('发送失败，请重试');
      return;
    }

    setState(() {
      if (replyParent != null) {
        // 服务端以根评论归组；回复楼中楼时也挂在同一组下。
        final index = _comments.indexWhere(
          (c) => c.id == replyParent.id ||
              c.replies.any((reply) => reply.id == replyParent.id),
        );
        if (index >= 0) {
          final parent = _comments[index];
          _comments[index] = parent.copyWith(
            replies: [...parent.replies, comment],
          );
        }
        _replyTo = null;
      } else {
        _comments.insert(0, comment);
      }
      _post = _post.copyWith(commentCount: _post.commentCount + 1);
    });

    _commentInputController.clear();
    _commentFocusNode.unfocus();

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(replyParent != null ? '回复发表成功' : '评论发表成功'),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  Widget _buildArticleBlocks() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: _post.contentBlocks.map((block) {
        if (block.type == 'TEXT') {
          return Padding(
            padding: const EdgeInsets.only(bottom: 14),
            child: Text(
              block.text ?? '',
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 14,
                height: 1.75,
                color: Color(0xFF475569),
              ),
            ),
          );
        }
        return Padding(
          padding: const EdgeInsets.only(bottom: 14),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: Image.network(
              block.url ?? '',
              width: double.infinity,
              fit: BoxFit.fitWidth,
              errorBuilder: (_, _, _) => Container(
                height: 180,
                color: const Color(0xFFF1F5F9),
                alignment: Alignment.center,
                child: const Icon(
                  Icons.broken_image_outlined,
                  color: Color(0xFF94A3B8),
                ),
              ),
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildDetailImages() {
    final urls = _post.images;
    final labels = _post.imageLabels;
    final count = urls.isNotEmpty ? urls.length : labels.length;
    if (count == 0) return const SizedBox.shrink();
    final visible = count.clamp(1, 3);
    return LayoutBuilder(
      builder: (context, constraints) {
        final gap = 8.0;
        final width = visible == 1
            ? 160.0
            : (constraints.maxWidth - gap * (visible - 1)) / visible;
        return Row(
          children: List.generate(visible, (index) {
            final url = index < urls.length ? urls[index] : null;
            final label = index < labels.length ? labels[index] : '校园图片';
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
              child: url != null
                  ? Image.network(
                      url,
                      width: width,
                      height: 104,
                      fit: BoxFit.cover,
                      errorBuilder: (_, _, _) => _detailImageLabel(label),
                    )
                  : _detailImageLabel(label),
            );
          }),
        );
      },
    );
  }

  Widget _detailImageLabel(String label) {
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

  @override
  Widget build(BuildContext context) {
    if (_unavailable) {
      final isAuthor =
          UserSession.currentUserId != null &&
          UserSession.currentUserId == _post.authorId;
      return PostUnavailablePage(
        postId: _post.id,
        onRetry: _loadDetail,
        canAppeal: isAuthor,
        onAppeal: isAuthor
            ? () => Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) =>
                      PostAppealPage(postId: _post.id, postTitle: _post.title),
                ),
              )
            : null,
      );
    }
    return Scaffold(
      backgroundColor: Colors.white,
      resizeToAvoidBottomInset: true, // 核心：底部输入框自然跟随系统软键盘顶起，无需弹出多余模态弹窗
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
          '帖子详情',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: Color(0xFF0F172A),
          ),
        ),
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(
              Icons.share_outlined,
              size: 22,
              color: Color(0xFF0F172A),
            ),
            onPressed: _openShare,
          ),
          IconButton(
            icon: const Icon(
              Icons.more_horiz_rounded,
              size: 24,
              color: Color(0xFF0F172A),
            ),
            onPressed: _showMoreActions,
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: RefreshIndicator(
              color: const Color(0xFF2563EB),
              onRefresh: _loadDetail,
              child: SingleChildScrollView(
                physics: const AlwaysScrollableScrollPhysics(
                  parent: BouncingScrollPhysics(),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 1. 作者信息栏 (5:345 节点) - 点击头像/姓名跳转对方个人主页
                    Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 12,
                      ),
                      child: Row(
                        children: [
                          GestureDetector(
                            onTap: _openAuthorProfile,
                            child: Container(
                              width: 44,
                              height: 44,
                              decoration: BoxDecoration(
                                color: _post.avatarBgColor,
                                shape: BoxShape.circle,
                              ),
                              alignment: Alignment.center,
                              child: Text(
                                _post.authorName.isNotEmpty
                                    ? _post.authorName.substring(0, 1)
                                    : '学',
                                style: const TextStyle(
                                  fontFamily: 'Noto Sans SC',
                                  fontSize: 18,
                                  fontWeight: FontWeight.bold,
                                  color: Color(0xFF2563EB),
                                ),
                              ),
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
                                      onTap: _openAuthorProfile,
                                      child: Text(
                                        _post.authorName,
                                        style: const TextStyle(
                                          fontFamily: 'Noto Sans SC',
                                          fontSize: 14,
                                          fontWeight: FontWeight.bold,
                                          color: Color(0xFF0F172A),
                                        ),
                                      ),
                                    ),

                                    // 等级徽章 (纯 Lv.X)
                                    Container(
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 5,
                                        vertical: 1.5,
                                      ),
                                      decoration: BoxDecoration(
                                        color: _post.authorOfficial
                                            ? const Color(0xFFEFF6FF)
                                            : _post.levelBgColor,
                                        borderRadius: BorderRadius.circular(4),
                                      ),
                                      child: Text(
                                        _post.authorOfficial
                                            ? '官方 · ${_post.authorOfficialTitle ?? "官方"}'
                                            : '${_post.authorLevel} ${communityLevelStyle(_post.authorLevel).title}',
                                        style: TextStyle(
                                          fontFamily: 'Noto Sans SC',
                                          fontSize: 12,
                                          fontWeight: FontWeight.bold,
                                          color: _post.authorOfficial
                                              ? const Color(0xFF1D4ED8)
                                              : _post.levelTextColor,
                                        ),
                                      ),
                                    ),

                                    // 佩戴勋章素材 (有佩戴才显示真实素材，无佩戴不占位)
                                    if (_post.badgeAsset != null &&
                                        _post.badgeAsset!.isNotEmpty) ...[
                                      Image.asset(
                                        _post.badgeAsset!,
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
                                  '${_post.timeAgo} · ${_post.location}',
                                  style: const TextStyle(
                                    fontFamily: 'Noto Sans SC',
                                    fontSize: 12,
                                    color: Color(0xFF64748B),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          // 关注按钮
                          if (_post.authorId != _interactions.accountId)
                            Semantics(
                              button: true,
                              label: _post.isFollowed ? '取消关注作者' : '关注作者',
                              enabled: !_interactions.followPending(
                                _post.authorId,
                              ),
                              child: GestureDetector(
                                key: const ValueKey('detail-follow'),
                                onTap:
                                    _interactions.followPending(_post.authorId)
                                    ? null
                                    : _handleToggleFollow,
                                child: Container(
                                  constraints: const BoxConstraints(
                                    minHeight: 48,
                                    minWidth: 64,
                                  ),
                                  alignment: Alignment.center,
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 14,
                                    vertical: 6,
                                  ),
                                  decoration: BoxDecoration(
                                    color: _post.isFollowed
                                        ? const Color(0xFFF1F5F9)
                                        : const Color(0xFFEFF6FF),
                                    borderRadius: BorderRadius.circular(16),
                                  ),
                                  child: Text(
                                    _interactions.followPending(_post.authorId)
                                        ? '处理中'
                                        : (_post.isFollowed ? '已关注' : '+ 关注'),
                                    style: TextStyle(
                                      fontFamily: 'Noto Sans SC',
                                      fontSize: 12,
                                      fontWeight: FontWeight.bold,
                                      color: _post.isFollowed
                                          ? const Color(0xFF64748B)
                                          : const Color(0xFF2563EB),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),

                    // 2. 帖子标题
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      child: Text(
                        _post.title,
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF0F172A),
                          height: 1.35,
                        ),
                      ),
                    ),
                    if (_post.status == 'REVIEWING' ||
                        _post.status == 'PENDING')
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: ContentUnderReviewCard(
                          reviewReason: _post.hiddenReason == 'BANNED_WORD'
                              ? '内容命中敏感词，人工安全审核中，目前仅自己可见'
                              : '内容正在人工安全审核中，目前仅自己可见',
                        ),
                      ),
                    const SizedBox(height: 10),

                    // 3. 正文：文章按图文混排块渲染，动态展示纯文本
                    if (_post.postType == 'ARTICLE' &&
                        _post.contentBlocks.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: _buildArticleBlocks(),
                      )
                    else
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: Text(
                          _post.content,
                          style: const TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 14,
                            color: Color(0xFF64748B),
                            height: 1.6,
                          ),
                        ),
                      ),
                    const SizedBox(height: 14),

                    // 4. 多图三列网格（文章图片已由内容块承载，避免重复）
                    if ((_post.postType != 'ARTICLE' ||
                            _post.contentBlocks.isEmpty) &&
                        (_post.images.isNotEmpty ||
                            _post.imageLabels.isNotEmpty)) ...[
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: _buildDetailImages(),
                      ),
                      const SizedBox(height: 10),
                    ],

                    // 5. 话题标签与阅读量 (5:407 节点)
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Wrap(
                            spacing: 6,
                            runSpacing: 6,
                            children: _post.topics.map((t) {
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
                                  '# $t',
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
                          Text(
                            '${_post.viewsCount} 次浏览',
                            style: const TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 12,
                              color: Color(0xFF64748B),
                            ),
                          ),
                        ],
                      ),
                    ),

                    const SizedBox(height: 16),
                    Container(height: 8, color: const Color(0xFFF8FAFC)),

                    // 6. 评论区头部 (5:420 节点: 全部评论数与热度/最新排序切换)
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Row(
                            children: [
                              const Text(
                                '全部评论',
                                style: TextStyle(
                                  fontFamily: 'Noto Sans SC',
                                  fontSize: 15,
                                  fontWeight: FontWeight.bold,
                                  color: Color(0xFF0F172A),
                                ),
                              ),
                              const SizedBox(width: 6),
                              Text(
                                '${_post.commentCount}',
                                style: const TextStyle(
                                  fontFamily: 'Noto Sans SC',
                                  fontSize: 13,
                                  color: Color(0xFF64748B),
                                ),
                              ),
                            ],
                          ),
                          // 排序切换 (最热 / 最新)
                          Row(
                            children: [
                              GestureDetector(
                                onTap: () =>
                                    setState(() => _isSortByHot = true),
                                child: Text(
                                  '最热',
                                  style: TextStyle(
                                    fontFamily: 'Noto Sans SC',
                                    fontSize: 12,
                                    fontWeight: _isSortByHot
                                        ? FontWeight.bold
                                        : FontWeight.normal,
                                    color: _isSortByHot
                                        ? const Color(0xFF2563EB)
                                        : const Color(0xFF64748B),
                                  ),
                                ),
                              ),
                              const Padding(
                                padding: EdgeInsets.symmetric(horizontal: 6),
                                child: Text(
                                  '|',
                                  style: TextStyle(
                                    color: Color(0xFFCBD5E1),
                                    fontSize: 12,
                                  ),
                                ),
                              ),
                              GestureDetector(
                                onTap: () =>
                                    setState(() => _isSortByHot = false),
                                child: Text(
                                  '最新',
                                  style: TextStyle(
                                    fontFamily: 'Noto Sans SC',
                                    fontSize: 12,
                                    fontWeight: !_isSortByHot
                                        ? FontWeight.bold
                                        : FontWeight.normal,
                                    color: !_isSortByHot
                                        ? const Color(0xFF2563EB)
                                        : const Color(0xFF64748B),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),

                    // 7. 评论列表展示
                    if (_loadingComments)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 32),
                        child: Center(
                          child: SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                        ),
                      )
                    else if (_comments.isEmpty)
                      _buildEmptyComments()
                    else
                      ListView.separated(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        itemCount: _comments.length,
                        separatorBuilder: (_, _) =>
                            const Divider(height: 16, color: Color(0xFFF1F5F9)),
                        itemBuilder: (context, index) {
                          final c = _sortedComments[index];
                          return GestureDetector(
                            onTap: () => _startReply(c),
                            behavior: HitTestBehavior.opaque,
                            child: _buildCommentItem(c, index),
                          );
                        },
                      ),

                    const SizedBox(height: 40),
                  ],
                ),
              ),
            ),
          ),

          // 8. 自然被键盘顶起的吸底互动评论栏
          _buildBottomBar(),
        ],
      ),
    );
  }

  Widget _buildEmptyComments() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 36, horizontal: 16),
      alignment: Alignment.center,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: const [
          Icon(
            Icons.chat_bubble_outline_rounded,
            size: 32,
            color: Color(0xFFCBD5E1),
          ),
          SizedBox(height: 10),
          Text(
            '还没有评论，来抢沙发吧',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCommentItem(CommunityComment c, int index) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            UserAvatar(
              name: c.authorName,
              avatarPath: c.avatarPath,
              size: 32,
              fontSize: 13,
              gradientColors: [c.avatarBgColor, c.levelTextColor],
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Row(
                children: [
                  Text(
                    c.authorName,
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF0F172A),
                    ),
                  ),
                  const SizedBox(width: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 4,
                      vertical: 1,
                    ),
                    decoration: BoxDecoration(
                      color: c.authorOfficial
                          ? const Color(0xFFEFF6FF)
                          : c.levelBgColor,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      c.authorOfficial
                          ? '官方 · ${c.authorOfficialTitle ?? "官方"}'
                          : (c.authorLevel.isEmpty ? '社区成员' : c.authorLevel),
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                        color: c.authorOfficial
                            ? const Color(0xFF1D4ED8)
                            : c.levelTextColor,
                      ),
                    ),
                  ),
                  if (c.isHot) ...[
                    const SizedBox(width: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 4,
                        vertical: 1,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFFFEF2F2),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: const Text(
                        '热评',
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFFEF4444),
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
            // 评论点赞
            GestureDetector(
              onTap: () => _handleToggleCommentLike(index),
              child: Row(
                children: [
                  Icon(
                    c.isLiked
                        ? Icons.thumb_up_alt_rounded
                        : Icons.thumb_up_alt_outlined,
                    size: 14,
                    color: c.isLiked
                        ? const Color(0xFF2563EB)
                        : const Color(0xFF94A3B8),
                  ),
                  const SizedBox(width: 4),
                  Text(
                    '${c.likeCount}',
                    style: TextStyle(
                      fontSize: 12,
                      color: c.isLiked
                          ? const Color(0xFF2563EB)
                          : const Color(0xFF64748B),
                      fontWeight: c.isLiked
                          ? FontWeight.bold
                          : FontWeight.normal,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        Padding(
          padding: const EdgeInsets.only(left: 40, top: 4),
          child: Text(
            c.content,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 13,
              color: Color(0xFF334155),
              height: 1.45,
            ),
          ),
        ),
        // 回复统一使用下方楼中楼列表展示，避免摘要卡和嵌套回复重复。
        Padding(
          padding: const EdgeInsets.only(left: 40, top: 4),
          child: Text(
            c.timeAgo,
            style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
          ),
        ),
        if (c.replies.isNotEmpty) ...[
          // 先轻量展示 2 条；点击"更多"每次追加 5 条，列表尾部统一收起。
          Builder(builder: (context) {
            final visibleCount = (_replyDisplayCounts[c.id] ?? 2)
                .clamp(0, c.replies.length);
            final visibleReplies = c.replies.take(visibleCount);
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(left: 40, top: 8),
                  child: Column(
                    children: [
                      for (final reply in visibleReplies)
                  GestureDetector(
                    onTap: () => _startReply(c),
                    behavior: HitTestBehavior.opaque,
                    child: Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          UserAvatar(
                            name: reply.authorName,
                            avatarPath: reply.avatarPath,
                            size: 26,
                            fontSize: 11,
                            gradientColors: [
                              reply.avatarBgColor,
                              reply.levelTextColor,
                            ],
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  reply.authorName,
                                  style: const TextStyle(
                                    fontFamily: 'Noto Sans SC',
                                    fontSize: 12,
                                    fontWeight: FontWeight.bold,
                                    color: Color(0xFF475569),
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  reply.content,
                                  style: const TextStyle(
                                    fontFamily: 'Noto Sans SC',
                                    fontSize: 12,
                                    color: Color(0xFF334155),
                                    height: 1.4,
                                  ),
                                ),
                                const SizedBox(height: 3),
                                GestureDetector(
                                  onTap: () => _toggleReplyLike(reply),
                                  behavior: HitTestBehavior.opaque,
                                  child: Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Icon(
                                        reply.isLiked
                                            ? Icons.thumb_up_alt_rounded
                                            : Icons.thumb_up_alt_outlined,
                                        size: 13,
                                        color: reply.isLiked
                                            ? const Color(0xFF2563EB)
                                            : const Color(0xFF94A3B8),
                                      ),
                                      const SizedBox(width: 4),
                                      Text(
                                        '${reply.likeCount}',
                                        style: TextStyle(
                                          fontSize: 11,
                                          color: reply.isLiked
                                              ? const Color(0xFF2563EB)
                                              : const Color(0xFF64748B),
                                        ),
                                      ),
                                      const Padding(
                                        padding: EdgeInsets.only(left: 12),
                                        child: Text(
                                          '回复',
                                          style: TextStyle(
                                            fontSize: 11,
                                            color: Color(0xFF64748B),
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(left: 40, top: 2, bottom: 4),
                  child: Row(
                    children: [
                      if (visibleCount < c.replies.length)
                        GestureDetector(
                          onTap: () => setState(() {
                            _replyDisplayCounts[c.id] = visibleCount + 5;
                          }),
                          behavior: HitTestBehavior.opaque,
                          child: Padding(
                            padding: const EdgeInsets.symmetric(vertical: 6),
                            child: Text(
                              c.replies.length - visibleCount > 5
                                  ? '更多回复'
                                  : '展开剩余 ${c.replies.length - visibleCount} 条回复',
                              style: const TextStyle(
                                fontFamily: 'Noto Sans SC',
                                fontSize: 12,
                                color: Color(0xFF2563EB),
                              ),
                            ),
                          ),
                        ),
                      if (visibleCount < c.replies.length &&
                          c.replies.length > 2)
                        const SizedBox(width: 16),
                      if (c.replies.length > 2)
                        GestureDetector(
                          onTap: () => setState(
                            () => _replyDisplayCounts[c.id] = 0,
                          ),
                          behavior: HitTestBehavior.opaque,
                          child: const Padding(
                            padding: EdgeInsets.symmetric(vertical: 6),
                            child: Text(
                              '收起',
                              style: TextStyle(
                                fontFamily: 'Noto Sans SC',
                                fontSize: 12,
                                color: Color(0xFF64748B),
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            );
          }),
        ],
      ],
    );
  }

  Widget _buildBottomBar() {
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: Color(0xFFF1F5F9))),
      ),
      padding: EdgeInsets.only(
        left: 14,
        right: 14,
        top: 8,
        bottom: MediaQuery.of(context).padding.bottom + 8,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 回复目标横条
          if (_replyTo != null)
            Container(
              margin: const EdgeInsets.only(bottom: 6),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: const Color(0xFFEFF6FF),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      '回复 @${_replyTo!.authorName}',
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        color: Color(0xFF1D4ED8),
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  GestureDetector(
                    onTap: _cancelReply,
                    behavior: HitTestBehavior.opaque,
                    child: const Icon(
                      Icons.close_rounded,
                      size: 16,
                      color: Color(0xFF64748B),
                    ),
                  ),
                ],
              ),
            ),
          // 输入框独占一行，让大字体下的互动操作保留完整触达空间。
          Container(
            constraints: const BoxConstraints(minHeight: 48),
            decoration: BoxDecoration(
              color: const Color(0xFFF1F5F9),
              borderRadius: BorderRadius.circular(19),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Row(
              children: [
                const Icon(
                  Icons.edit_outlined,
                  size: 16,
                  color: Color(0xFF94A3B8),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: TextField(
                    controller: _commentInputController,
                    focusNode: _commentFocusNode,
                    textInputAction: TextInputAction.send,
                    onSubmitted: (_) => _submitComment(),
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 13,
                      color: Color(0xFF0F172A),
                    ),
                    decoration: InputDecoration(
                      hintText: _replyTo == null
                          ? '友善评论，说点什么吧...'
                          : '回复 @${_replyTo!.authorName}...',
                      hintStyle: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        color: Color(0xFF64748B),
                      ),
                      // 胶囊容器自带灰底，关掉主题的 filled 白底避免双层底纹
                      filled: false,
                      border: InputBorder.none,
                      isDense: true,
                      contentPadding: const EdgeInsets.symmetric(vertical: 8),
                    ),
                  ),
                ),
                if (_isComposing)
                  GestureDetector(
                    onTap: _sendingComment ? null : _submitComment,
                    child: Container(
                      constraints: const BoxConstraints(
                        minWidth: 48,
                        minHeight: 36,
                      ),
                      alignment: Alignment.center,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        // 发送中降为浅色且不可点，防止连点重复提交
                        color: _sendingComment
                            ? const Color(0xFF93B4F5)
                            : const Color(0xFF2563EB),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(
                        _sendingComment ? '发送中' : '发送',
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          color: Colors.white,
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),

          // 未输入时显示互动组件快捷动作
          if (!_isComposing) ...[
            const SizedBox(height: 8),
            Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 6,
              runSpacing: 6,
              children: [
                // 评论数 (点击聚焦输入框)
                GestureDetector(
                  onTap: () => _commentFocusNode.requestFocus(),
                  behavior: HitTestBehavior.opaque,
                  child: Container(
                    constraints: const BoxConstraints(
                      minWidth: 48,
                      minHeight: 48,
                    ),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 4,
                      vertical: 4,
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(
                          Icons.chat_bubble_outline_rounded,
                          size: 20,
                          color: Color(0xFF64748B),
                        ),
                        const SizedBox(width: 4),
                        Text(
                          '${_post.commentCount}',
                          style: const TextStyle(
                            fontSize: 12,
                            color: Color(0xFF64748B),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),

                // 点赞
                Semantics(
                  label: _post.isLiked ? '取消点赞' : '点赞',
                  button: true,
                  selected: _post.isLiked,
                  enabled: !_interactions.likePending(_post.id),
                  child: GestureDetector(
                    key: const ValueKey('detail-like'),
                    onTap: _interactions.likePending(_post.id)
                        ? null
                        : _handleToggleLike,
                    behavior: HitTestBehavior.opaque,
                    child: Container(
                      constraints: const BoxConstraints(
                        minWidth: 48,
                        minHeight: 48,
                      ),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 4,
                        vertical: 4,
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            _post.isLiked
                                ? Icons.favorite_rounded
                                : Icons.favorite_border_rounded,
                            size: 20,
                            color: _post.isLiked
                                ? const Color(0xFFEF4444)
                                : const Color(0xFF64748B),
                          ),
                          const SizedBox(width: 4),
                          Text(
                            '${_post.likeCount}',
                            style: TextStyle(
                              fontSize: 12,
                              color: _post.isLiked
                                  ? const Color(0xFFEF4444)
                                  : const Color(0xFF64748B),
                              fontWeight: _post.isLiked
                                  ? FontWeight.bold
                                  : FontWeight.normal,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),

                // 收藏入口，与首页共用当前账号的持久化状态。
                Tooltip(
                  message: '收藏',
                  child: GestureDetector(
                    key: const ValueKey('detail-local-bookmark'),
                    onTap:
                        _loadingBookmark ||
                            _interactions.bookmarkPending(_post.id)
                        ? null
                        : _handleToggleBookmark,
                    behavior: HitTestBehavior.opaque,
                    child: Container(
                      constraints: const BoxConstraints(
                        minWidth: 48,
                        minHeight: 48,
                      ),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 4,
                        vertical: 4,
                      ),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          if (_loadingBookmark || _interactions.bookmarkPending(_post.id))
                            const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 1.5),
                            )
                          else
                            Icon(
                              _post.isBookmarked
                                  ? Icons.star_rounded
                                  : Icons.star_border_rounded,
                              size: 22,
                              color: _post.isBookmarked
                                  ? const Color(0xFFF59E0B)
                                  : const Color(0xFF64748B),
                            ),
                        ],
                      ),
                    ),
                  ),
                ),

                // 分享
                GestureDetector(
                  onTap: _openShare,
                  behavior: HitTestBehavior.opaque,
                  child: Container(
                    constraints: const BoxConstraints(
                      minWidth: 48,
                      minHeight: 48,
                    ),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 4,
                      vertical: 4,
                    ),
                    child: Icon(
                      Icons.share_outlined,
                      size: 20,
                      color: Color(0xFF64748B),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
