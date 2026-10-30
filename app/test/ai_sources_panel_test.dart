import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/widgets/ai_sources_panel.dart';

void main() {
  testWidgets('AiSourcesPanel renders citations and opens all sources', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: AiSourcesPanel(
              sources: [
                AiSource(
                  fileName: 'kb-test-doc.md',
                  titlePath: '3. 报修时限规定',
                  score: 1.0,
                  snippet: '宿舍报修：一般报修 3 天内处理完毕；紧急报修（水电类）不受 3 天限制。',
                ),
                AiSource(
                  fileName: 'kb-test-doc.md',
                  titlePath: '1. 明确社团定位与目标',
                  score: 0.49,
                  snippet: '核心理念：社团的宗旨、价值观（兴趣交流、技能提升）。',
                ),
                AiSource(
                  fileName: '校园服务手册.pdf',
                  page: 2,
                  score: 0.4,
                  snippet: '教室报修一般 5 天内处理完毕。',
                ),
              ],
            ),
          ),
        ),
      ),
    );

    expect(find.text('引用来源 · 3'), findsOneWidget);
    expect(find.text('查看全部 3 条来源'), findsOneWidget);
    expect(find.textContaining('kb-test-doc.md'), findsWidgets);
    expect(find.textContaining('3. 报修时限规定'), findsOneWidget);
    expect(find.textContaining('宿舍报修'), findsOneWidget);

    await tester.tap(find.text('查看全部 3 条来源'));
    await tester.pumpAndSettle();
    expect(find.text('引用来源'), findsOneWidget);
    expect(find.textContaining('校园服务手册.pdf'), findsOneWidget);
    expect(find.textContaining('教室报修'), findsOneWidget);
  });

  testWidgets('AiSourcesPanel shows all sources when under maxVisible', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AiSourcesPanel(
            sources: [
              AiSource(fileName: 'only.md', snippet: '只有一条引用。'),
            ],
          ),
        ),
      ),
    );

    expect(find.text('引用来源 · 1'), findsOneWidget);
    expect(find.text('查看全部 1 条来源'), findsNothing);
    expect(find.textContaining('only.md'), findsOneWidget);
    expect(find.textContaining('只有一条引用'), findsOneWidget);
  });
}
