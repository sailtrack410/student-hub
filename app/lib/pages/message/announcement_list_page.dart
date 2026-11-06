import 'package:flutter/material.dart';

import '../../api/p1_api.dart';
import 'announcement_detail_page.dart';
import '../../models/home_dashboard_model.dart';
import '../../theme/app_theme.dart';

/// 校园公告列表（消息中心「校园公告」快捷入口）
class AnnouncementListPage extends StatefulWidget {
  const AnnouncementListPage({super.key});

  @override
  State<AnnouncementListPage> createState() => _AnnouncementListPageState();
}

class _AnnouncementListPageState extends State<AnnouncementListPage> {
  List<HomeAnnouncementModel> _items = [];
  bool _loading = true;
  bool _error = false;
  bool _moreBusy = false;
  int _page = 1;
  int _total = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = false;
    });
    try {
      final result = await P1Api.announcements(1);
      if (!mounted) return;
      setState(() {
        _items = (result['records'] as List)
            .map(
              (e) => HomeAnnouncementModel.fromJson(
                Map<String, dynamic>.from(e as Map),
              ),
            )
            .toList();
        _total = int.tryParse('${result['total']}') ?? 0;
        _page = 1;
        _loading = false;
      });
    } catch (_) {
      if (mounted) {
        setState(() {
          _error = true;
          _loading = false;
        });
      }
    }
  }

  Future<void> _more() async {
    if (_moreBusy) return;
    setState(() => _moreBusy = true);
    try {
      final result = await P1Api.announcements(_page + 1);
      if (!mounted) return;
      setState(() {
        _items.addAll(
          (result['records'] as List).map(
            (e) => HomeAnnouncementModel.fromJson(
              Map<String, dynamic>.from(e as Map),
            ),
          ),
        );
        _page++;
        _total = int.tryParse('${result['total']}') ?? 0;
      });
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => _moreBusy = false);
    }
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
        title: const Text(
          '校园公告',
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
            onPressed: () async {
              try {
                await P1Api.readAnnouncements();
                if (mounted) await _load();
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context)
                      .showSnackBar(SnackBar(content: Text('$e')));
                }
              }
            },
            child: const Text('全部已读'),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(strokeWidth: 2))
          : _error || _items.isEmpty
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(
                    Icons.campaign_outlined,
                    size: 48,
                    color: Color(0xFFCBD5E1),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    _error ? '公告加载失败' : '暂无公告',
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      color: Color(0xFF64748B),
                    ),
                  ),
                  const SizedBox(height: 16),
                  TextButton(onPressed: _load, child: const Text('重新加载')),
                ],
              ),
            )
          : RefreshIndicator(
              onRefresh: _load,
              color: AppColors.brand,
              child: ListView.separated(
                padding: const EdgeInsets.only(bottom: 24),
                physics: const AlwaysScrollableScrollPhysics(),
                itemCount: _items.length + (_items.length < _total ? 1 : 0),
                separatorBuilder: (_, _) => const Divider(
                  height: 1,
                  thickness: 1,
                  color: Color(0xFFF1F5F9),
                ),
                itemBuilder: (context, index) {
                  if (index == _items.length) {
                    return TextButton(
                      onPressed: _moreBusy ? null : _more,
                      child: Text(_moreBusy ? '加载中' : '加载更多'),
                    );
                  }
                  final item = _items[index];
                  return GestureDetector(
                    onTap: () => _showDetail(item),
                    child: Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: const Color(0xFFF1F5F9)),
                      ),
                      child: Row(
                        children: [
                          Container(
                            width: 40,
                            height: 40,
                            decoration: const BoxDecoration(
                              color: Color(0xFFFFFBEB),
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.campaign_rounded,
                              size: 20,
                              color: Color(0xFFF59E0B),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  item.title,
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(
                                    fontFamily: 'Noto Sans SC',
                                    fontSize: 14,
                                    fontWeight: FontWeight.bold,
                                    color: AppColors.textPrimary,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  '【${item.tag}】${item.read ? '已读' : '未读'}',
                                  style: const TextStyle(
                                    fontFamily: 'Noto Sans SC',
                                    fontSize: 12,
                                    color: Color(0xFF64748B),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const Icon(
                            Icons.chevron_right_rounded,
                            color: Color(0xFFCBD5E1),
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
    );
  }

  Future<void> _showDetail(HomeAnnouncementModel item) async {
    await Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => AnnouncementDetailPage(id: item.id)),
    );
    if (mounted) await _load();
  }
}
