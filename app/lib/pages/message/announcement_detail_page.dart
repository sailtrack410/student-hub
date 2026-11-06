import 'package:flutter/material.dart';

import '../../api/p1_api.dart';
import '../../models/home_dashboard_model.dart';
import '../../theme/app_theme.dart';
import 'announcement_list_page.dart';

class AnnouncementDetailPage extends StatefulWidget {
  const AnnouncementDetailPage({
    super.key,
    required this.id,
    this.initialAnnouncement,
    this.showAllAnnouncementsAction = false,
  });
  final String id;
  final HomeAnnouncementModel? initialAnnouncement;
  final bool showAllAnnouncementsAction;
  @override
  State<AnnouncementDetailPage> createState() => _AnnouncementDetailPageState();
}

class _AnnouncementDetailPageState extends State<AnnouncementDetailPage> {
  Map<String, dynamic>? item;
  String? error;
  bool loading = true;
  @override
  void initState() {
    super.initState();
    final seed = widget.initialAnnouncement;
    if (seed != null && seed.title.isNotEmpty) {
      item = <String, dynamic>{
        'id': seed.id,
        'title': seed.title,
        'typeText': seed.tag,
        if (seed.summary != null && seed.summary!.isNotEmpty)
          'summary': seed.summary,
        if (seed.publishDate != null && seed.publishDate!.isNotEmpty)
          'publishDate': seed.publishDate,
        if (seed.author != null && seed.author!.isNotEmpty)
          'author': seed.author,
      };
    }
    load();
  }

  Future<void> load() async {
    setState(() {
      loading = item == null;
      error = null;
    });
    try {
      final detail = await P1Api.announcement(widget.id);
      if (!mounted) return;
      setState(() {
        item = detail;
        loading = false;
      });
      // Only the successfully rendered detail is acknowledged; an HTTP failure does not consume unread.
      await P1Api.readAnnouncement(widget.id);
    } catch (e) {
      if (mounted) {
        setState(() {
          if (item == null) error = e.toString();
          loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('公告详情'),
      actions: [
        if (widget.showAllAnnouncementsAction)
          TextButton(
            key: const ValueKey('announcement-detail-open-all'),
            onPressed: () {
              Navigator.of(context).pushReplacement(
                MaterialPageRoute(builder: (_) => const AnnouncementListPage()),
              );
            },
            child: const Text('全部公告'),
          ),
      ],
    ),
    body: loading && item == null
        ? const Center(child: CircularProgressIndicator())
        : item == null
        ? Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(error ?? '公告暂不可用'),
                TextButton(onPressed: load, child: const Text('重试')),
              ],
            ),
          )
        : ListView(
            padding: const EdgeInsets.all(AppSpacing.homeHorizontal),
            children: [
              Text(
                '${item!['title'] ?? ''}',
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  color: AppColors.textPrimary,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: AppSpacing.cardGroupGap),
              Text(
                [
                  if ('${item!['typeText'] ?? ''}'.isNotEmpty)
                    '【${item!['typeText']}】',
                  item!['author'],
                  item!['publishDate'],
                ].where((e) => e != null && '$e'.isNotEmpty).join(' · '),
                style: const TextStyle(
                  fontSize: AppTypography.metaSize,
                  color: AppColors.textSecondary,
                ),
              ),
              const SizedBox(height: AppSpacing.homeHorizontal),
              SelectableText(
                '${item!['content'] ?? item!['summary'] ?? item!['title'] ?? ''}',
                style: const TextStyle(
                  fontSize: AppTypography.announcementBodySize,
                  height: AppTypography.announcementLineHeight,
                  color: AppColors.textBody,
                ),
              ),
              if (error != null)
                TextButton(onPressed: load, child: const Text('已读同步失败，重试')),
            ],
          ),
  );
}
