import 'package:flutter/material.dart';

/// 一条知识库引用来源。
class AiSource {
  final String fileName;
  final int? page;
  final String? titlePath;
  final double score;
  final String snippet;

  const AiSource({
    required this.fileName,
    this.page,
    this.titlePath,
    this.score = 0,
    this.snippet = '',
  });

  /// 例如「第 2 页 · 3. 报修时限规定」；没有页码/章节时为空。
  String get location {
    final parts = <String>[];
    if (page != null && page! > 0) parts.add('第 $page 页');
    if (titlePath != null && titlePath!.trim().isNotEmpty) {
      parts.add(titlePath!.trim());
    }
    return parts.join(' · ');
  }

  String get displayTitle =>
      location.isEmpty ? fileName : '$fileName · $location';
}

/// AI 回答下方的「引用来源」面板：
/// - 默认展示前 [maxVisible] 条，超出显示「查看全部 N 条来源」；
/// - 点击单条来源或「查看全部」打开底部弹层，展示完整引用片段。
class AiSourcesPanel extends StatelessWidget {
  final List<AiSource> sources;
  final int maxVisible;

  const AiSourcesPanel({super.key, required this.sources, this.maxVisible = 2});

  @override
  Widget build(BuildContext context) {
    if (sources.isEmpty) return const SizedBox.shrink();
    final shown = sources.take(maxVisible).toList();
    return Container(
      padding: const EdgeInsets.fromLTRB(10, 8, 10, 8),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(
                Icons.menu_book_rounded,
                size: 13,
                color: Color(0xFF64748B),
              ),
              const SizedBox(width: 4),
              Text(
                '引用来源 · ${sources.length}',
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF64748B),
                ),
              ),
            ],
          ),
          const SizedBox(height: 5),
          ...shown.map((source) => _buildRow(context, source)),
          if (sources.length > shown.length)
            GestureDetector(
              onTap: () => _showAll(context),
              behavior: HitTestBehavior.opaque,
              child: Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(
                  '查看全部 ${sources.length} 条来源',
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF2563EB),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildRow(BuildContext context, AiSource source) {
    return GestureDetector(
      onTap: () => _showDetail(context, source),
      behavior: HitTestBehavior.opaque,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 22,
              height: 22,
              decoration: BoxDecoration(
                color: const Color(0xFFEFF6FF),
                borderRadius: BorderRadius.circular(6),
              ),
              child: const Icon(
                Icons.description_outlined,
                size: 13,
                color: Color(0xFF2563EB),
              ),
            ),
            const SizedBox(width: 6),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    source.displayTitle,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF334155),
                    ),
                  ),
                  if (source.snippet.trim().isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(
                      source.snippet.trim(),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        height: 1.45,
                        color: Color(0xFF64748B),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showDetail(BuildContext context, AiSource source) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (ctx) => SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 14, 20, 18),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(
                    Icons.menu_book_rounded,
                    size: 18,
                    color: Color(0xFF2563EB),
                  ),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      source.displayTitle,
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFF0F172A),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Text(
                source.snippet.trim().isEmpty
                    ? '暂无引用片段'
                    : source.snippet.trim(),
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  height: 1.6,
                  color: Color(0xFF334155),
                ),
              ),
              const SizedBox(height: 12),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  onPressed: () => Navigator.of(ctx).pop(),
                  child: const Text('关闭'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showAll(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (ctx) => SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.of(ctx).size.height * 0.7,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 14, 12, 8),
                child: Row(
                  children: [
                    const Expanded(
                      child: Text(
                        '引用来源',
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                          color: Color(0xFF0F172A),
                        ),
                      ),
                    ),
                    IconButton(
                      onPressed: () => Navigator.of(ctx).pop(),
                      icon: const Icon(Icons.close_rounded, size: 20),
                    ),
                  ],
                ),
              ),
              Flexible(
                child: ListView.separated(
                  padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
                  itemCount: sources.length,
                  separatorBuilder: (_, __) =>
                      const Divider(height: 18, color: Color(0xFFE2E8F0)),
                  itemBuilder: (_, index) {
                    final source = sources[index];
                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          source.displayTitle,
                          style: const TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 12.5,
                            fontWeight: FontWeight.w600,
                            color: Color(0xFF334155),
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          source.snippet.trim().isEmpty
                              ? '暂无引用片段'
                              : source.snippet.trim(),
                          style: const TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 12,
                            height: 1.55,
                            color: Color(0xFF64748B),
                          ),
                        ),
                      ],
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
