import 'package:flutter/material.dart';

class ScheduleAdjustmentSheet extends StatelessWidget {
  const ScheduleAdjustmentSheet({super.key, this.adjustment});
  final Map<String, dynamic>? adjustment;

  static Future<void> show(
    BuildContext context, {
    Map<String, dynamic>? adjustment,
  }) => showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    builder: (_) => ScheduleAdjustmentSheet(adjustment: adjustment),
  );

  @override
  Widget build(BuildContext context) {
    final data = adjustment;
    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              data == null ? '暂无调课通知' : '${data['courseName'] ?? '课程'}调整通知',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            if (data != null) ...[
              for (final entry in <String, String>{
                'summary': '调整摘要',
                'reason': '调整原因',
                'originalDate': '原上课日期',
                'targetDate': '调整后日期',
                'originalSections': '原上课节次',
                'targetSections': '调整后节次',
                'originalLocation': '原上课地点',
                'targetLocation': '调整后地点',
                'noticeTime': '发布时间',
                'actionType': '调整类型',
                'targetClasses': '适用班级',
              }.entries)
                if ('${data[entry.key] ?? ''}'.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    child: Text('${entry.value}：${data[entry.key]}'),
                  ),
            ],
            const SizedBox(height: 16),
            FilledButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('关闭'),
            ),
          ],
        ),
      ),
    );
  }
}
