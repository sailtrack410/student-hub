import 'package:flutter/material.dart';

import '../../api/community_api.dart';
import '../../models/community_models.dart';
import '../../models/user_session.dart';
import '../../services/community_interaction_store.dart';
import '../../widgets/user_avatar.dart';
import 'chat_detail_page.dart';
import 'community_share_sheet.dart';
import 'post_detail_page.dart';

/// APP-他人主页 (对应画布 5:1778 节点，375 × 812)
/// 严格依据 Ardot 设计规范实现顶部个人简况、发私信/关注操作、四列数据概览、动态与勋章展示
class AuthorProfilePage extends StatefulWidget {
  final String authorId;
  final String authorName;
  final Color avatarBgColor;
  final String authorLevel;
  final bool authorOfficial;
  final String? authorOfficialTitle;
  final String? badgeAsset;
  final CommunityPost? sourcePost;
  final CommunityInteractionStore? interactionStore;

  const AuthorProfilePage({
    super.key,
    required this.authorId,
    required this.authorName,
    this.avatarBgColor = const Color(0xFFEFF6FF),
    this.authorLevel = 'Lv.4',
    this.authorOfficial = false,
    this.authorOfficialTitle,
    this.badgeAsset,
    this.sourcePost,
    this.interactionStore,
  });

  @override
  State<AuthorProfilePage> createState() => _AuthorProfilePageState();
}

class _AuthorProfilePageState extends State<AuthorProfilePage>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  late AuthorProfileInfo _profile;
  late final CommunityInteractionStore _interactions;
  bool get _isFollowed =>
      _interactions.isFollowing(widget.authorId, fallback: _profile.isFollowed);
  late List<CommunityPost> _authorPosts;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _profile = AuthorProfileInfo(
      authorId: widget.authorId,
      authorName: widget.authorName,
      avatarBgColor: widget.avatarBgColor,
      authorLevel: widget.authorLevel,
      authorOfficial: widget.authorOfficial,
      authorOfficialTitle: widget.authorOfficialTitle,
      badgeAsset: widget.badgeAsset,
      department: '绵阳城市学院',
      uid: widget.authorId,
      postCount: 0,
      likeCount: 0,
      followCount: 0,
      fansCount: 0,
      isFollowed: widget.sourcePost?.isFollowed ?? false,
    );
    _interactions =
        widget.interactionStore ??
        CommunityInteractionScope.acquire(UserSession.currentUserId ?? 'guest');
    _interactions.addListener(_onInteractionsChanged);
    _authorPosts = widget.sourcePost == null ? [] : [widget.sourcePost!];
    _loadProfile();
  }

  Future<void> _loadProfile() async {
    final startedAt = _interactions.revision;
    final results = await Future.wait<Object?>([
      CommunityApi.fetchAuthorProfile(widget.authorId),
      CommunityApi.fetchAuthorPosts(widget.authorId),
    ]);
    if (!mounted) return;
    setState(() {
      if (results[0] is AuthorProfileInfo) {
        _profile = results[0] as AuthorProfileInfo;
        _interactions.acceptAuthor(
          widget.authorId,
          _profile.isFollowed,
          startedAt,
        );
      }
      final livePosts = results[1] as List<CommunityPost>;
      if (livePosts.isNotEmpty) _authorPosts = livePosts;
    });
  }

  @override
  void dispose() {
    _interactions.removeListener(_onInteractionsChanged);
    if (widget.interactionStore == null) {
      CommunityInteractionScope.release(_interactions);
    }
    _tabController.dispose();
    super.dispose();
  }

  void _onInteractionsChanged() {
    if (mounted) setState(() {});
  }

  Future<void> _handleToggleFollow() async {
    if (_interactions.followPending(widget.authorId)) return;
    final confirmed = await _interactions.toggleAuthorFollowing(
      widget.authorId,
      currentFollowing: _isFollowed,
    );
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..removeCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(
            confirmed
                ? (_isFollowed ? '已关注 ${widget.authorName}' : '已取消关注')
                : '关注操作失败，已恢复显示，请重试',
          ),
          behavior: SnackBarBehavior.floating,
        ),
      );
  }

  void _openChat() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ChatDetailPage(
          peerId: widget.authorId,
          peerName: widget.authorName,
          peerMajor: '${_profile.department} · 在线',
          relatedPost:
              widget.sourcePost ??
              (_authorPosts.isNotEmpty ? _authorPosts.first : null),
        ),
      ),
    );
  }

  void _openShareProfile() {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('已复制 ${widget.authorName} 的校园主页链接'),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final equippedBadge = widget.badgeAsset ?? _profile.badgeAsset;

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: CustomScrollView(
        physics: const BouncingScrollPhysics(),
        slivers: [
          // 1. 顶部渐变背景与个人简况区 (5:1779 节点)
          SliverToBoxAdapter(
            child: Container(
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  colors: [Color(0xFF2563EB), Color(0xFF3B82F6)],
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                ),
              ),
              padding: EdgeInsets.only(
                top: MediaQuery.of(context).padding.top + 8,
                left: 16,
                right: 16,
                bottom: 20,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // 顶部导航栏 (返回、标题、分享)
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      IconButton(
                        onPressed: () => Navigator.of(context).pop(),
                        tooltip: '返回',
                        constraints: const BoxConstraints(
                          minWidth: 48,
                          minHeight: 48,
                        ),
                        icon: const Icon(
                          Icons.arrow_back_ios_new_rounded,
                          size: 20,
                          color: Colors.white,
                        ),
                      ),
                      const Text(
                        '个人主页',
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                          color: Colors.white,
                        ),
                      ),
                      IconButton(
                        onPressed: _openShareProfile,
                        tooltip: '分享作者主页',
                        constraints: const BoxConstraints(
                          minWidth: 48,
                          minHeight: 48,
                        ),
                        icon: const Icon(
                          Icons.share_outlined,
                          size: 20,
                          color: Colors.white,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 18),

                  // 用户简况行 (5:1789 节点)
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.center,
                    children: [
                      // 头像外框
                      Container(
                        width: 56,
                        height: 56,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: Colors.white.withOpacity(0.4),
                            width: 2,
                          ),
                        ),
                        alignment: Alignment.center,
                        child: UserAvatar(
                          name: _profile.authorName,
                          avatarPath: _profile.avatarUrl,
                          size: 50,
                          fontSize: 22,
                        ),
                      ),
                      const SizedBox(width: 14),

                      // 信息列 (姓名、纯Lv等级、勋章素材、院系UID)
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Wrap(
                              spacing: 8,
                              runSpacing: 4,
                              crossAxisAlignment: WrapCrossAlignment.center,
                              children: [
                                Text(
                                  widget.authorName,
                                  style: const TextStyle(
                                    fontFamily: 'Noto Sans SC',
                                    fontSize: 18,
                                    fontWeight: FontWeight.bold,
                                    color: Colors.white,
                                  ),
                                ),

                                // 等级徽章 (纯 Lv.X)
                                Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 6,
                                    vertical: 2,
                                  ),
                                  decoration: BoxDecoration(
                                    color: Colors.white.withOpacity(0.2),
                                    borderRadius: BorderRadius.circular(10),
                                    border: Border.all(
                                      color: Colors.white.withOpacity(0.3),
                                    ),
                                  ),
                                  child: Text(
                                    (widget.authorOfficial ||
                                            _profile.authorOfficial)
                                        ? '官方 · ${widget.authorOfficialTitle ?? _profile.authorOfficialTitle ?? "官方"}'
                                        : widget.authorLevel,
                                    style: const TextStyle(
                                      fontFamily: 'Noto Sans SC',
                                      fontSize: 12,
                                      fontWeight: FontWeight.bold,
                                      color: Colors.white,
                                    ),
                                  ),
                                ),

                                // 佩戴勋章素材 (有佩戴才显示真实素材，绝不用 emoji)
                                if (equippedBadge != null &&
                                    equippedBadge.isNotEmpty) ...[
                                  const SizedBox(width: 6),
                                  Image.asset(
                                    equippedBadge,
                                    width: 22,
                                    height: 22,
                                    errorBuilder: (_, _, _) =>
                                        const SizedBox.shrink(),
                                  ),
                                ],
                              ],
                            ),
                            const SizedBox(height: 4),

                            // 院系专业
                            Text(
                              _profile.department,
                              style: TextStyle(
                                fontFamily: 'Noto Sans SC',
                                fontSize: 12,
                                color: Colors.white.withOpacity(0.9),
                              ),
                            ),
                            const SizedBox(height: 4),

                            // UID 与复制
                            Row(
                              children: [
                                Flexible(
                                  child: Text(
                                    'UID: ${_profile.uid}',
                                    overflow: TextOverflow.ellipsis,
                                    maxLines: 1,
                                    style: TextStyle(
                                      fontFamily: 'Noto Sans SC',
                                      fontSize: 12,
                                      color: Colors.white.withOpacity(0.75),
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 4),
                                GestureDetector(
                                  onTap: () {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      const SnackBar(
                                        content: Text('已复制UID'),
                                        duration: Duration(seconds: 1),
                                      ),
                                    );
                                  },
                                  child: Icon(
                                    Icons.copy_rounded,
                                    size: 11,
                                    color: Colors.white.withOpacity(0.75),
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          // 2. 操作与数据卡片 (5:1799 节点)
          SliverToBoxAdapter(
            child: Container(
              margin: const EdgeInsets.fromLTRB(16, 12, 16, 12),
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFFF1F5F9)),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x080D1B33),
                    blurRadius: 10,
                    offset: Offset(0, 4),
                  ),
                ],
              ),
              child: Column(
                children: [
                  // 操作按钮行 (发私聊、关注状态)
                  Row(
                    children: [
                      // 发私聊按钮 (5:1801 节点)
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: _openChat,
                          icon: const Icon(
                            Icons.chat_bubble_outline_rounded,
                            size: 16,
                          ),
                          label: const Text(
                            '私信',
                            style: TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 13,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xFF2563EB),
                            foregroundColor: Colors.white,
                            elevation: 0,
                            padding: const EdgeInsets.symmetric(vertical: 10),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(19),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 10),

                      // 关注状态按钮 (5:1805 节点)
                      if (widget.authorId != _interactions.accountId)
                        Semantics(
                          button: true,
                          label: _isFollowed ? '取消关注作者' : '关注作者',
                          enabled: !_interactions.followPending(
                            widget.authorId,
                          ),
                          child: GestureDetector(
                            key: const ValueKey('author-follow'),
                            onTap: _interactions.followPending(widget.authorId)
                                ? null
                                : _handleToggleFollow,
                            child: Container(
                              constraints: const BoxConstraints(
                                minWidth: 100,
                                minHeight: 48,
                              ),
                              padding: const EdgeInsets.symmetric(
                                horizontal: 12,
                                vertical: 10,
                              ),
                              decoration: BoxDecoration(
                                color: _isFollowed
                                    ? const Color(0xFFF1F5F9)
                                    : const Color(0xFFEFF6FF),
                                borderRadius: BorderRadius.circular(19),
                                border: Border.all(
                                  color: _isFollowed
                                      ? const Color(0xFFE2E8F0)
                                      : const Color(0xFFBFDBFE),
                                ),
                              ),
                              alignment: Alignment.center,
                              child: Text(
                                _interactions.followPending(widget.authorId)
                                    ? '处理中'
                                    : (_isFollowed ? '已关注' : '+ 关注'),
                                style: TextStyle(
                                  fontFamily: 'Noto Sans SC',
                                  fontSize: 13,
                                  fontWeight: FontWeight.w600,
                                  color: _isFollowed
                                      ? const Color(0xFF64748B)
                                      : const Color(0xFF2563EB),
                                ),
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 16),

                  // 四列数据行 (5:1807 节点: 帖子、获赞、关注、粉丝)
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: [
                      _buildStatColumn('帖子动态', '${_profile.postCount}'),
                      _buildDivider(),
                      _buildStatColumn('获赞互动', '${_profile.likeCount}'),
                      _buildDivider(),
                      _buildStatColumn('关注中', '${_profile.followCount}'),
                      _buildDivider(),
                      _buildStatColumn('校园粉丝', '${_profile.fansCount}'),
                    ],
                  ),
                ],
              ),
            ),
          ),

          // 3. 分类标签行 (5:1820 节点: 动态Tab, 二手闲置Tab, 荣誉勋章Tab)
          SliverToBoxAdapter(
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 16),
              decoration: const BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.vertical(top: Radius.circular(12)),
              ),
              child: TabBar(
                onTap: (index) {
                  if (MediaQuery.disableAnimationsOf(context)) {
                    _tabController.animateTo(index, duration: Duration.zero);
                  }
                },
                controller: _tabController,
                labelColor: const Color(0xFF2563EB),
                unselectedLabelColor: const Color(0xFF64748B),
                labelStyle: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                ),
                unselectedLabelStyle: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                ),
                indicatorColor: const Color(0xFF2563EB),
                indicatorSize: TabBarIndicatorSize.label,
                tabs: [
                  Tab(text: '动态 (${_authorPosts.length})'),
                  Tab(text: '荣誉勋章 (${_profile.earnedBadges.length})'),
                ],
              ),
            ),
          ),

          // 4. 列表内容展示
          SliverToBoxAdapter(
            child: Container(
              color: Colors.white,
              margin: const EdgeInsets.symmetric(horizontal: 16),
              padding: const EdgeInsets.all(12),
              child: AnimatedBuilder(
                animation: _tabController,
                builder: (context, _) {
                  final idx = _tabController.index;
                  if (idx == 0) {
                    return _buildPostsList();
                  }
                  return _buildBadgesList();
                },
              ),
            ),
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 40)),
        ],
      ),
    );
  }

  Widget _buildStatColumn(String label, String value) {
    return Expanded(
      child: Column(
        children: [
          Text(
            value,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 16,
              fontWeight: FontWeight.bold,
              color: Color(0xFF0F172A),
            ),
          ),
          const SizedBox(height: 2),
          Text(
            label,
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

  Widget _buildDivider() {
    return Container(width: 1, height: 20, color: const Color(0xFFF1F5F9));
  }

  Widget _buildPostsList() {
    if (_authorPosts.isEmpty) {
      return Container(
        padding: const EdgeInsets.symmetric(vertical: 36),
        alignment: Alignment.center,
        child: const Text(
          '暂未发布公开动态',
          style: TextStyle(color: Color(0xFF64748B), fontSize: 13),
        ),
      );
    }

    return Column(
      children: _authorPosts.map((original) {
        final post = _interactions.apply(original);
        return GestureDetector(
          onTap: () {
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) =>
                    PostDetailPage(post: post, interactionStore: _interactions),
              ),
            );
          },
          behavior: HitTestBehavior.opaque,
          child: Container(
            margin: const EdgeInsets.only(bottom: 12),
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: const Color(0xFFF8FAFC),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFFF1F5F9)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  post.title,
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  post.content,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF475569),
                    height: 1.4,
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Text(
                        '${post.timeAgo} · ${post.location}',
                        style: const TextStyle(
                          fontSize: 12,
                          color: Color(0xFF64748B),
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Row(
                      children: [
                        const Icon(
                          Icons.favorite_outline_rounded,
                          size: 14,
                          color: Color(0xFF94A3B8),
                        ),
                        const SizedBox(width: 3),
                        Text(
                          '${post.likeCount}',
                          style: const TextStyle(
                            fontSize: 12,
                            color: Color(0xFF64748B),
                          ),
                        ),
                        const SizedBox(width: 12),
                        const Icon(
                          Icons.chat_bubble_outline_rounded,
                          size: 14,
                          color: Color(0xFF94A3B8),
                        ),
                        const SizedBox(width: 3),
                        Text(
                          '${post.commentCount}',
                          style: const TextStyle(
                            fontSize: 12,
                            color: Color(0xFF64748B),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildIdleGoodsList() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFF1F5F9)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                '转让正版九成新教材与期末真题笔记',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: const Color(0xFFFEF2F2),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: const Text(
                  '¥ 35',
                  style: TextStyle(
                    color: Color(0xFFEF4444),
                    fontWeight: FontWeight.bold,
                    fontSize: 12,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          const Text(
            '附带手写思维导图和历年复习大纲，校内支持自提面交，支持私聊询问。',
            style: TextStyle(fontSize: 12, color: Color(0xFF475569)),
          ),
        ],
      ),
    );
  }

  Widget _buildBadgesList() {
    if (_profile.earnedBadges.isEmpty) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 24),
        child: Center(
          child: Text(
            '暂无已解锁的荣誉勋章',
            style: TextStyle(color: Color(0xFF64748B), fontSize: 13),
          ),
        ),
      );
    }

    return Column(
      children: _profile.earnedBadges.map((b) {
        return Container(
          margin: const EdgeInsets.only(bottom: 10),
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: const Color(0xFFF8FAFC),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: const Color(0xFFF1F5F9)),
          ),
          child: Row(
            children: [
              Image.asset(
                b.assetPath,
                width: 44,
                height: 44,
                errorBuilder: (_, _, _) => const Icon(
                  Icons.military_tech_rounded,
                  size: 40,
                  color: Color(0xFF2563EB),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          b.name,
                          style: const TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 13,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF0F172A),
                          ),
                        ),
                        if (b.isEquipped) ...[
                          const SizedBox(width: 6),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 5,
                              vertical: 1.5,
                            ),
                            decoration: BoxDecoration(
                              color: const Color(0xFFEFF6FF),
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: const Text(
                              '佩戴中',
                              style: TextStyle(
                                fontFamily: 'Noto Sans SC',
                                fontSize: 12,
                                color: Color(0xFF2563EB),
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 2),
                    Text(
                      b.desc,
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        color: Color(0xFF64748B),
                      ),
                    ),
                  ],
                ),
              ),
              Text(
                b.unlockedDate,
                style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
              ),
            ],
          ),
        );
      }).toList(),
    );
  }
}
