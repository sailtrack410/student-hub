import 'package:flutter/material.dart';

import '../../api/p1_api.dart';
import '../../services/ai_chat_controller.dart';
import '../schedule/schedule_page.dart';
import '../schedule/grade_query_page.dart';
import '../service/my_requests_page.dart';
import '../service/work_order_detail_page.dart';
import '../service/service_apply_page.dart';

class AiTodoPage extends StatefulWidget {
  const AiTodoPage({super.key});
  @override
  State<AiTodoPage> createState() => _AiTodoPageState();
}

class _AiTodoPageState extends State<AiTodoPage> {
  List<Map<String, dynamic>>? items;
  String? error;
  bool busy = false;
  bool moreBusy = false;
  bool hasMore = false;
  int page = 1;
  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final list = await P1Api.todos();
      if (mounted) {
        setState(() {
          items = list;
          page = 1;
          hasMore = list.length == 200;
          error = null;
        });
      }
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    }
  }

  Future<void> more() async {
    if (moreBusy) return;
    setState(() => moreBusy = true);
    try {
      final list = await P1Api.todos(page: page + 1);
      if (mounted) {
        setState(() {
          items!.addAll(list);
          page++;
          hasMore = list.length == 200;
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => moreBusy = false);
    }
  }

  Future<void> change(Map<String, dynamic> item) async {
    if (busy) return;
    setState(() => busy = true);
    try {
      await P1Api.todoState(
        '${item['id']}',
        item['state'] == 'OPEN' ? 'DONE' : 'OPEN',
      );
      await load();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  void open(Map<String, dynamic> item) {
    final action = AiChatAction.fromJson(item['action'] as Map);
    final page = switch (action.kind) {
      AiActionKind.schedule => const SchedulePage(),
      AiActionKind.grades => const GradeQueryPage(),
      AiActionKind.requestList => const MyRequestsPage(),
      AiActionKind.requestDetail when action.targetId != null =>
        WorkOrderDetailPage(requestId: action.targetId!),
      AiActionKind.serviceApply when action.targetId != null =>
        ServiceApplyPage(itemId: action.targetId!, itemName: action.label),
      _ => null,
    };
    if (page != null) {
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => page));
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('AI待办')),
    body: error != null
        ? Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(error!),
                TextButton(onPressed: load, child: const Text('重试')),
              ],
            ),
          )
        : items == null
        ? const Center(child: CircularProgressIndicator())
        : RefreshIndicator(
            onRefresh: load,
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                if (items!.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(32),
                    child: Center(child: Text('暂无待办')),
                  ),
                for (final item in items!)
                  ListTile(
                    title: Text('${item['title']}'),
                    subtitle: Text(item['state'] == 'OPEN' ? '待完成' : '已完成'),
                    onTap: () => open(item),
                    trailing: TextButton(
                      onPressed: busy ? null : () => change(item),
                      child: Text(item['state'] == 'OPEN' ? '完成' : '重新打开'),
                    ),
                  ),
                if (hasMore)
                  TextButton(
                    onPressed: moreBusy ? null : more,
                    child: Text(moreBusy ? '加载中' : '加载更多'),
                  ),
              ],
            ),
          ),
  );
}
