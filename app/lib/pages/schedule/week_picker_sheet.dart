import 'package:flutter/material.dart';

class WeekPickerSheet extends StatelessWidget {
  const WeekPickerSheet({
    super.key,
    required this.currentWeek,
    this.actualCurrentWeek,
    this.totalWeeks = 20,
  });
  final int currentWeek;
  final int? actualCurrentWeek;
  final int totalWeeks;

  static Future<int?> show(
    BuildContext context, {
    required int currentWeek,
    int? actualCurrentWeek,
    int totalWeeks = 20,
  }) => showModalBottomSheet<int>(
    context: context,
    isScrollControlled: true,
    builder: (_) => WeekPickerSheet(
      currentWeek: currentWeek,
      actualCurrentWeek: actualCurrentWeek,
      totalWeeks: totalWeeks,
    ),
  );

  @override
  Widget build(BuildContext context) => SafeArea(
    child: SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  '选择周次',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
              ),
              IconButton(
                tooltip: '关闭',
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.close),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (var week = 1; week <= totalWeeks; week++)
                ConstrainedBox(
                  constraints: const BoxConstraints(
                    minWidth: 56,
                    minHeight: 48,
                  ),
                  child: OutlinedButton(
                    style: week == currentWeek
                        ? OutlinedButton.styleFrom(
                            backgroundColor: Theme.of(context)
                                .colorScheme
                                .primaryContainer,
                          )
                        : null,
                    onPressed: () => Navigator.pop(context, week),
                    child: Text(
                      '$week${week == actualCurrentWeek ? ' 本周' : ''}',
                    ),
                  ),
                ),
            ],
          ),
        ],
      ),
    ),
  );
}
