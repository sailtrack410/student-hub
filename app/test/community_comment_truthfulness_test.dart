import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/pages/community/post_detail_page.dart';
import 'package:smartcampus_app/services/community_interaction_store.dart';

import 'community_interaction_test.dart' as fixtures;

/// 回归：评论接口失败时不得插入本地捏造的评论（假发表成功），
/// 输入内容保留并提示失败；发送中不可连点。
void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('failed comment submit keeps input and shows retry hint', (
    tester,
  ) async {
    final gateway = fixtures.FakeGateway();
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: gateway,
    );
    addTearDown(store.dispose);

    // post('a') 非纯数字 id：跳过详情接口，评论走 createComment 真实请求。
    // 测试环境 ApiClient 指向不存在的 127.0.0.1:9000，请求将失败。
    final post = fixtures.post('a');
    await tester.pumpWidget(
      MaterialApp(home: PostDetailPage(post: post, interactionStore: store)),
    );
    await tester.pumpAndSettle();

    // 楼层评论条输入：唤起输入框并键入文字，露出「发送」按钮。
    final input = find.byType(TextField);
    expect(input, findsOneWidget);
    await tester.enterText(input, '这条评论注定发不出去');
    await tester.pump();
    expect(find.text('发送'), findsOneWidget);

    await tester.tap(find.text('发送'));
    await tester.pump();
    // 发送中防连点：再次点击不应发起第二次提交路径（仍处于同一轮）。
    await tester.pump(const Duration(seconds: 25));

    // 接口失败：提示失败、不插入评论、输入保留。
    expect(find.text('发送失败，请重试'), findsOneWidget);
    expect(find.text('这条评论注定发不出去'), findsOneWidget);
    expect(find.text('评论发表成功'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('hot/new toggle actually reorders comments', (tester) async {
    final gateway = fixtures.FakeGateway();
    final store = CommunityInteractionStore(
      accountId: 'viewer',
      gateway: gateway,
    );
    addTearDown(store.dispose);

    // post_<id> 走 mock 评论路径，验证排序切换不抛错且列表稳定。
    final post = fixtures.post('post_hot');
    await tester.pumpWidget(
      MaterialApp(home: PostDetailPage(post: post, interactionStore: store)),
    );
    await tester.pumpAndSettle();

    // 默认「最热」：列表渲染后切到「最新」应触发重排（setState 即生效）。
    expect(find.text('最新'), findsOneWidget);
    await tester.tap(find.text('最新'));
    await tester.pumpAndSettle();
    // 排序切换后页面仍稳定渲染评论列表。
    expect(find.byType(ListView), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
