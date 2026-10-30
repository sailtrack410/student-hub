import 'package:flutter/material.dart';

import '../../../widgets/app_status_panel.dart';

class HomeSectionStatus extends StatelessWidget {
  const HomeSectionStatus({
    super.key,
    required this.title,
    required this.loading,
    required this.failed,
    required this.hasData,
    required this.onRetry,
  });
  final String title;
  final bool loading;
  final bool failed;
  final bool hasData;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    if (!loading && !failed) return const SizedBox.shrink();
    if (loading && hasData) {
      return const Padding(
        padding: EdgeInsets.symmetric(horizontal: 16),
        child: AppRefreshStatus(refreshing: true),
      );
    }
    return AppStatusPanel(
      title: loading ? '$title加载中…' : '$title加载失败',
      message: loading
          ? null
          : hasData
          ? '当前显示上次内容，请重试更新。'
          : '网络开小差了，请检查网络后重试',
      compact: true,
      loading: loading,
      onRetry: loading ? null : onRetry,
    );
  }
}
