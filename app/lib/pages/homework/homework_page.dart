import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/homework_api.dart';
import '../../models/homework_models.dart';
import '../../theme/app_theme.dart';
import '../../widgets/skeleton_shimmer.dart';
import 'homework_detail_page.dart';

/// APP-作业中心（列表）：课程、标题、截止时间与「已交/未交」状态。
/// 数据全部来自 /api/app/homework，支持下拉刷新与分页加载，无本地 mock 数据。
class HomeworkPage extends StatefulWidget {
  const HomeworkPage({super.key});

  @override
  State<HomeworkPage> createState() => _HomeworkPageState();
}

class _HomeworkPageState extends State<HomeworkPage> {
  static const int _pageSize = 10;

  final HomeworkApi _api = HomeworkApi(ApiClient());
  final ScrollController _scrollController = ScrollController();
  final List<HomeworkSummary> _records = [];

  int _pageNum = 1;
  int _total = 0;
  bool _loading = true;
  bool _loadingMore = false;
  String? _error;
  String? _loadMoreError;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
    _loadFirstPage();
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (!_scrollController.hasClients) return;
    final position = _scrollController.position;
    if (position.pixels >= position.maxScrollExtent - 160) {
      _loadMore();
    }
  }

  Future<void> _loadFirstPage() async {
    setState(() {
      _loading = true;
      _error = null;
      _loadMoreError = null;
    });
    await _fetchFirstPage(showLoading: true);
  }

  Future<void> _fetchFirstPage({required bool showLoading}) async {
    if (showLoading && !_loading) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final page = await _api.fetchHomeworks(pageNum: 1, pageSize: _pageSize);
      if (!mounted) return;
      setState(() {
        _records
          ..clear()
          ..addAll(page.records);
        _pageNum = 1;
        _total = page.total;
        _loading = false;
        _error = null;
        _loadMoreError = null;
      });
    } on ApiException catch (error) {
      if (!mounted) return;
      if (showLoading) {
        setState(() {
          _loading = false;
          _error = error.message;
        });
      } else {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('刷新失败：${error.message}')));
      }
    } catch (_) {
      if (!mounted) return;
      if (showLoading) {
        setState(() {
          _loading = false;
          _error = '作业加载失败，请稍后重试';
        });
      } else {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('刷新失败，请稍后重试')));
      }
    }
  }

  Future<void> _loadMore() async {
    if (_loading || _loadingMore || _loadMoreError != null) return;
    if (_records.length >= _total) return;
    setState(() => _loadingMore = true);
    try {
      final page = await _api.fetchHomeworks(
        pageNum: _pageNum + 1,
        pageSize: _pageSize,
      );
      if (!mounted) return;
      setState(() {
        _pageNum = page.pageNum;
        _total = page.total;
        final existingIds = _records.map((item) => item.homeworkId).toSet();
        _records.addAll(
          page.records.where((item) => !existingIds.contains(item.homeworkId)),
        );
        _loadingMore = false;
      });
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() {
        _loadingMore = false;
        _loadMoreError = error.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loadingMore = false;
        _loadMoreError = '加载失败，请稍后重试';
      });
    }
  }

  void _retryLoadMore() {
    setState(() => _loadMoreError = null);
    _loadMore();
  }

  Future<void> _openDetail(HomeworkSummary homework) async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => HomeworkDetailPage(homeworkId: homework.homeworkId),
      ),
    );
    if (!mounted || changed != true) return;
    await _fetchFirstPage(showLoading: false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        bottom: true,
        child: Column(
          children: [
            _buildHeader(),
            Expanded(child: _buildBody()),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      color: Colors.white,
      padding: const EdgeInsets.fromLTRB(8, 6, 12, 6),
      child: Row(
        children: [
          IconButton(
            onPressed: () => Navigator.of(context).maybePop(),
            icon: const Icon(
              Icons.arrow_back_ios_new_rounded,
              size: 18,
              color: AppColors.textPrimary,
            ),
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
          ),
          const Expanded(
            child: Text(
              '作业中心',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 17,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
          ),
          SizedBox(
            width: 64,
            child: _loading || _error != null
                ? null
                : Text(
                    '共 $_total 份',
                    textAlign: TextAlign.right,
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      color: AppColors.textMuted,
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return SkeletonShimmer(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
          children: List.generate(
            4,
            (index) => Container(
              height: 112,
              margin: const EdgeInsets.only(bottom: 12),
              decoration: BoxDecoration(
                color: const Color(0xFFE2E8F0),
                borderRadius: BorderRadius.circular(16),
              ),
            ),
          ),
        ),
      );
    }
    if (_error != null) {
      return _buildErrorState();
    }
    if (_records.isEmpty) {
      return RefreshIndicator(
        onRefresh: () => _fetchFirstPage(showLoading: false),
        color: AppColors.brand,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(
            parent: BouncingScrollPhysics(),
          ),
          children: [const SizedBox(height: 96), _buildEmptyState()],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: () => _fetchFirstPage(showLoading: false),
      color: AppColors.brand,
      child: ListView.builder(
        controller: _scrollController,
        physics: const AlwaysScrollableScrollPhysics(
          parent: BouncingScrollPhysics(),
        ),
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
        itemCount: _records.length + 1,
        itemBuilder: (context, index) {
          if (index == _records.length) return _buildFooter();
          return Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: _buildCard(_records[index]),
          );
        },
      ),
    );
  }

  Widget _buildErrorState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              Icons.cloud_off_rounded,
              size: 48,
              color: Color(0xFFCBD5E1),
            ),
            const SizedBox(height: 12),
            Text(
              _error ?? '作业加载失败',
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 16),
            TextButton(onPressed: _loadFirstPage, child: const Text('重新加载')),
          ],
        ),
      ),
    );
  }

  Widget _buildEmptyState() {
    return Column(
      children: [
        const Icon(
          Icons.assignment_outlined,
          size: 56,
          color: Color(0xFFCBD5E1),
        ),
        const SizedBox(height: 12),
        const Text(
          '暂无作业',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 15,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        const SizedBox(height: 6),
        const Text(
          '老师发布作业后会在这里展示',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 12,
            color: AppColors.textMuted,
          ),
        ),
        const SizedBox(height: 16),
        TextButton(onPressed: _loadFirstPage, child: const Text('刷新')),
      ],
    );
  }

  Widget _buildCard(HomeworkSummary homework) {
    final status = _statusStyle(homework);
    return GestureDetector(
      onTap: () => _openDetail(homework),
      behavior: HitTestBehavior.opaque,
      child: Container(
        padding: const EdgeInsets.fromLTRB(14, 14, 12, 14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFFF1F5F9)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Flexible(
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 3,
                    ),
                    decoration: BoxDecoration(
                      color: AppColors.brandSoft,
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      homework.courseName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: AppColors.brand,
                      ),
                    ),
                  ),
                ),
                const Spacer(),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: status.$2,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    status.$1,
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: status.$3,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Text(
              homework.title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 15,
                fontWeight: FontWeight.bold,
                height: 1.4,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 8),
            _buildMetaRow(
              icon: Icons.schedule_rounded,
              text: '截止 ${_formatDateTime(homework.dueAt)}',
              color: homework.isOverdue && !homework.submitted
                  ? AppColors.danger
                  : AppColors.textSecondary,
            ),
            if (homework.submitted && homework.submittedAt != null) ...[
              const SizedBox(height: 6),
              _buildMetaRow(
                icon: Icons.check_circle_outline_rounded,
                text: '提交于 ${_formatDateTime(homework.submittedAt)}',
                color: AppColors.accentDeep,
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildMetaRow({
    required IconData icon,
    required String text,
    required Color color,
  }) {
    return Row(
      children: [
        Icon(icon, size: 14, color: color),
        const SizedBox(width: 4),
        Expanded(
          child: Text(
            text,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: color,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildFooter() {
    if (_loadMoreError != null) {
      return Center(
        child: TextButton(
          onPressed: _retryLoadMore,
          child: Text('加载失败，点击重试：$_loadMoreError'),
        ),
      );
    }
    if (_loadingMore) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 12),
        child: Center(
          child: SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: AppColors.brand,
            ),
          ),
        ),
      );
    }
    if (_records.length >= _total) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 8),
        child: Center(
          child: Text(
            '没有更多作业了',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: AppColors.textMuted,
            ),
          ),
        ),
      );
    }
    return const SizedBox(height: 24);
  }

  /// 返回（文案, 背景色, 文字色）
  (String, Color, Color) _statusStyle(HomeworkSummary homework) {
    if (homework.submitted) {
      return ('已交', AppColors.accentSoft, AppColors.accentDeep);
    }
    if (homework.isClosed) {
      return ('已关闭', const Color(0xFFF1F5F9), AppColors.textMuted);
    }
    if (homework.isOverdue) {
      return ('已截止', const Color(0xFFF1F5F9), AppColors.textMuted);
    }
    return ('未交', AppColors.warningSoft, AppColors.warning);
  }

  String _formatDateTime(DateTime? value) {
    if (value == null) return '待定';
    String two(int number) => number.toString().padLeft(2, '0');
    return '${value.year}-${two(value.month)}-${two(value.day)} '
        '${two(value.hour)}:${two(value.minute)}';
  }
}
