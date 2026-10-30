import 'package:flutter/material.dart';

/// 全页与局部区域共用的加载、空态和失败反馈；旧内容可由调用方继续展示。
class AppStatusPanel extends StatelessWidget {
  const AppStatusPanel({
    super.key,
    required this.title,
    this.message,
    this.onRetry,
    this.loading = false,
    this.compact = false,
    this.retryLabel = '重试',
  });

  final String title;
  final String? message;
  final VoidCallback? onRetry;
  final bool loading;
  final bool compact;
  final String retryLabel;

  @override
  Widget build(BuildContext context) => Semantics(
    liveRegion: true,
    child: Padding(
      padding: EdgeInsets.all(compact ? 12 : 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (loading)
            const SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          else
            Icon(
              onRetry == null ? Icons.inbox_outlined : Icons.cloud_off_outlined,
              color: Theme.of(context).colorScheme.primary,
            ),
          const SizedBox(height: 12),
          Text(
            title,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleSmall,
          ),
          if (message != null) ...[
            const SizedBox(height: 8),
            Text(
              message!,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ],
          if (onRetry != null) ...[
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: loading ? null : onRetry,
              icon: const Icon(Icons.refresh),
              label: Text(retryLabel),
            ),
          ],
        ],
      ),
    ),
  );
}

class AppRefreshStatus extends StatelessWidget {
  const AppRefreshStatus({
    super.key,
    this.refreshing = false,
    this.stale = false,
    this.updatedAt,
  });
  final bool refreshing;
  final bool stale;
  final DateTime? updatedAt;

  @override
  Widget build(BuildContext context) {
    if (!refreshing && !stale && updatedAt == null) {
      return const SizedBox.shrink();
    }
    final time = updatedAt?.toLocal().toString().split('.').first;
    return Semantics(
      liveRegion: true,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Text(
          refreshing
              ? '正在刷新，当前显示上次数据'
              : '${stale ? '离线缓存 · ' : ''}${time == null ? '显示上次数据' : '上次同步 $time'}',
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ),
    );
  }
}
