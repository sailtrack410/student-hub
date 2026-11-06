import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/api_client.dart';

/// Assigned order notifications use the worker endpoint, retaining its ownership checks.
class AssignedWorkOrderDetailPage extends StatefulWidget {
  final String orderId;
  const AssignedWorkOrderDetailPage({super.key, required this.orderId});
  @override
  State<AssignedWorkOrderDetailPage> createState() =>
      _AssignedWorkOrderDetailPageState();
}

class _AssignedWorkOrderDetailPageState
    extends State<AssignedWorkOrderDetailPage> {
  Map<String, dynamic>? _detail;
  bool _loading = true;
  String? _error;
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await ApiClient().get(
        '/api/app/work-orders/${widget.orderId}',
      );
      if (!mounted) return;
      if (data is! Map<String, dynamic>) {
        throw Exception('工单详情暂时不可用');
      }
      setState(() {
        _detail = data;
        _loading = false;
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = e.toString();
        });
      }
    }
  }

  String _date(dynamic raw) {
    final date = DateTime.tryParse('$raw');
    if (date == null) return raw?.toString() ?? '';
    String two(int value) => value.toString().padLeft(2, '0');
    return '${date.year}-${two(date.month)}-${two(date.day)} ${two(date.hour)}:${two(date.minute)}';
  }

  String _action(dynamic action) =>
      const {
        'SUBMIT': '提交申请',
        'DISPATCH': '分配工单',
        'REASSIGN': '重新分配',
        'START': '开始处理',
        'COMPLETE': '处理完成',
        'REJECT': '驳回申请',
        'CANCEL': '撤销申请',
        'EVALUATE': '服务评价',
        'SUBMIT_COMPLETION': '提交完工',
        'CONFIRM_COMPLETION': '确认完工',
        'REJECT_COMPLETION': '退回处理',
      }[action] ??
      '处理更新';

  @override
  Widget build(BuildContext context) {
    final d = _detail;
    const statuses = {
      'PENDING': '待派单',
      'ASSIGNED': '已派单',
      'PROCESSING': '处理中',
      'PENDING_CONFIRMATION': '待确认',
      'COMPLETED': '已完成',
      'CANCELLED': '已撤销',
      'REJECTED': '已驳回',
    };
    return Scaffold(
      appBar: AppBar(
        title: const Text('工单详情'),
        systemOverlayStyle: SystemUiOverlayStyle.dark,
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(_error!),
                  TextButton(onPressed: _load, child: const Text('重新加载')),
                ],
              ),
            )
          : ListView(
              padding: const EdgeInsets.all(20),
              children: [
                Text(
                  '${d?['title'] ?? ''}',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 16),
                for (final field in {
                  '工单编号': d?['orderNo'],
                  '状态': statuses[d?['status']] ?? d?['status'],
                  '申请编号': d?['requestNo'],
                  '申请人': d?['requesterName'],
                  '联系电话': d?['requesterPhone'],
                  '报修地址': d?['repairAddress'],
                  '处理人': d?['assigneeName'],
                  '派单时间': d?['assignedAt'],
                  '开始时间': d?['startedAt'],
                  '完成时间': d?['completedAt'],
                  '完成说明': d?['completeRemark'],
                }.entries)
                  if (field.value != null && '${field.value}'.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      child: Text(
                        '${field.key}：${field.key.endsWith('时间') ? _date(field.value) : field.value}',
                      ),
                    ),
                if (d?['timeline'] is List) ...[
                  const Divider(),
                  const Text('处理记录'),
                  for (final entry in d!['timeline'] as List)
                    if (entry is Map)
                      ListTile(
                        title: Text(_action(entry['action'])),
                        subtitle: Text(
                          '${entry['operatorName'] ?? ''} ${entry['comment'] ?? ''}\n${_date(entry['createdAt'])}',
                        ),
                      ),
                ],
              ],
            ),
    );
  }
}
