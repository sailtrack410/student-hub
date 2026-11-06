import 'package:flutter/material.dart';

import '../../api/message_api.dart';
import '../../models/message_models.dart';
import '../../theme/app_theme.dart';
import '../../services/notification_navigation.dart';

/// 社区互动通知列表（消息中心「互动提醒」聚合条目点入）
/// 数据源：GET /api/app/notifications?communityOnly=true
class CommunityNotificationListPage extends StatefulWidget {
  final bool communityOnly;
  const CommunityNotificationListPage({super.key, this.communityOnly = true});

  @override
  State<CommunityNotificationListPage> createState() =>
      _CommunityNotificationListPageState();
}

class _CommunityNotificationListPageState
    extends State<CommunityNotificationListPage> {
  final ScrollController _scrollController = ScrollController();
  List<CommunityNotificationItem> _items = [];
  bool _loading = true;
  bool _error = false;
  bool _loadingMore = false;
  bool _hasMore = true;
  int _pageNum = 1;
  int _generation = 0;
  bool _opening = false;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
    _load(reset: true);
  }

  @override
  void dispose() {
    _generation++;
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _load({bool reset = false}) async {
    if (!reset && (_loading || _loadingMore || !_hasMore)) return;
    final generation = ++_generation;
    if (reset) {
      _pageNum = 1;
      _hasMore = true;
      setState(() => _loading = true);
    } else {
      if (_loadingMore || !_hasMore) return;
      setState(() => _loadingMore = true);
    }
    final page = await MessageApi.fetchCommunityNotifications(
      pageNum: _pageNum,
      communityOnly: widget.communityOnly,
    );
    if (!mounted || generation != _generation) return;
    if (page == null) {
      setState(() {
        _loading = false;
        _loadingMore = false;
        _error = true;
      });
      return;
    }
    setState(() {
      _loading = false;
      _loadingMore = false;
      _error = false;
      if (reset) {
        _items = page;
      } else {
        _items.addAll(page);
      }
      _hasMore = page.length >= 20;
      if (_hasMore) _pageNum++;
    });
  }

  void _onScroll() {
    if (!_scrollController.hasClients) return;
    final position = _scrollController.position;
    if (position.pixels >= position.maxScrollExtent - 240) {
      _load();
    }
  }

  Future<void> _markAllRead() async {
    final ok = await MessageApi.markAllNotificationsRead(
      communityOnly: widget.communityOnly,
    );
    if (!mounted) return;
    if (!ok) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('标记已读失败，请重试')));
      return;
    }
    await _load(reset: true);
  }

  Future<void> _open(CommunityNotificationItem item) async {
    if (_opening) return;
    _opening = true;
    try {
      await NotificationNavigation.open(context, item);
      if (mounted) await _load(reset: true);
    } finally {
      _opening = false;
    }
  }

  String _formatTime(String raw) {
    final dt = DateTime.tryParse(raw);
    if (dt == null) return '';
    final now = DateTime.now();
    final diff = now.difference(dt);
    if (diff.inMinutes < 1) return '刚刚';
    if (diff.inMinutes < 60) return '${diff.inMinutes}分钟前';
    if (diff.inHours < 24) return '${diff.inHours}小时前';
    if (diff.inDays < 7) return '${diff.inDays}天前';
    return '${dt.month}月${dt.day}日';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
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
        title: Text(
          widget.communityOnly ? '互动提醒' : '流程办理通知',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: Color(0xFF0F172A),
          ),
        ),
        centerTitle: true,
        actions: [
          TextButton(
            onPressed: _loading ? null : _markAllRead,
            child: const Text(
              '全部已读',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                color: Color(0xFF2563EB),
              ),
            ),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(strokeWidth: 2))
          : _error
          ? Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('加载失败，请重试'),
                  TextButton(
                    onPressed: () => _load(reset: true),
                    child: const Text('重新加载'),
                  ),
                ],
              ),
            )
          : _items.isEmpty
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(
                    Icons.favorite_border_rounded,
                    size: 48,
                    color: Color(0xFFCBD5E1),
                  ),
                  SizedBox(height: 12),
                  Text(
                    widget.communityOnly ? '暂无互动提醒' : '暂无流程通知',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      color: Color(0xFF64748B),
                      fontSize: 13,
                    ),
                  ),
                ],
              ),
            )
          : RefreshIndicator(
              onRefresh: () => _load(reset: true),
              child: ListView.separated(
                controller: _scrollController,
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                itemCount: _items.length + (_hasMore ? 1 : 0),
                separatorBuilder: (_, _) => const SizedBox(height: 8),
                itemBuilder: (context, index) {
                  if (index >= _items.length) {
                    return const Padding(
                      padding: EdgeInsets.symmetric(vertical: 16),
                      child: Center(
                        child: SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        ),
                      ),
                    );
                  }
                  final item = _items[index];
                  return _buildTile(item);
                },
              ),
            ),
    );
  }

  Widget _buildTile(CommunityNotificationItem item) {
    return GestureDetector(
      onTap: () => _open(item),
      behavior: HitTestBehavior.opaque,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: const BoxDecoration(
                color: Color(0xFFECFDF5),
                shape: BoxShape.circle,
              ),
              alignment: Alignment.center,
              child: const Icon(
                Icons.favorite_rounded,
                size: 20,
                color: Color(0xFF10B981),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          item.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: item.read
                                ? const Color(0xFF334155)
                                : const Color(0xFF0F172A),
                          ),
                        ),
                      ),
                      Text(
                        _formatTime(item.createdAt),
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 11,
                          color: Color(0xFF94A3B8),
                        ),
                      ),
                      const SizedBox(width: 6),
                      if (!item.read)
                        Container(
                          width: 8,
                          height: 8,
                          decoration: const BoxDecoration(
                            color: Color(0xFFEF4444),
                            shape: BoxShape.circle,
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    item.content,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 13,
                      color: Color(0xFF64748B),
                    ),
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
