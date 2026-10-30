import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../api/api_client.dart';
import '../../api/service_request_api.dart';
import '../../models/service_request_models.dart';
import '../../theme/app_theme.dart';
import 'evaluation_sheet.dart';

/// APP-申请/工单详情：全部数据来自 `GET /api/app/service-requests/{requestId}`。
///
/// 不再有演示工单、硬编码师傅电话或假时间线；催单、撤销、确认完工、退回、
/// 评价全部按真实 requestId / workOrderId 调用后端。
class WorkOrderDetailPage extends StatefulWidget {
  final String requestId;

  const WorkOrderDetailPage({super.key, required this.requestId});

  @override
  State<WorkOrderDetailPage> createState() => _WorkOrderDetailPageState();
}

class _WorkOrderDetailPageState extends State<WorkOrderDetailPage> {
  final ServiceRequestApi _api = ServiceRequestApi(ApiClient());
  ServiceRequestDetail? _detail;
  bool _loading = true;
  bool _error = false;
  bool _actionLoading = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (!mounted) return;
    setState(() {
      _loading = true;
      _error = false;
    });
    final detail = await _api.fetchRequestDetail(widget.requestId);
    if (!mounted) return;
    setState(() {
      _detail = detail == null ? null : ServiceRequestDetail.fromJson(detail);
      _loading = false;
      _error = _detail == null;
    });
  }

  void _toast(String message, {Color color = const Color(0xFFDC2626)}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: color,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  Future<void> _urge() async {
    if (_actionLoading) return;
    final detail = _detail;
    if (detail == null) return;
    setState(() => _actionLoading = true);
    final result = await ServiceRequestApi.urge(detail.requestId);
    if (!mounted) return;
    setState(() => _actionLoading = false);
    if (result == null) {
      _toast('催单失败，请稍后重试');
      return;
    }
    if (result['urged'] == false) {
      final seconds = result['nextUrgeAvailableSeconds'] ?? 0;
      _toast('催单过于频繁，请 ${seconds}s 后再试', color: const Color(0xFFD97706));
      return;
    }
    _toast('已向师傅发送催单加急通知', color: const Color(0xFF16A34A));
  }

  Future<void> _cancel() async {
    final detail = _detail;
    if (detail == null || _actionLoading) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('撤销申请'),
        content: const Text('撤销后申请将终止，已派工的工单会同步撤销。确定继续吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('再想想'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.danger),
            child: const Text('确认撤销'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    setState(() => _actionLoading = true);
    final ok = await _api.cancelRequest(detail.requestId);
    if (!mounted) return;
    setState(() => _actionLoading = false);
    if (!ok) {
      _toast('撤销失败，请稍后重试');
      return;
    }
    _toast('申请已撤销', color: const Color(0xFF16A34A));
    await _load();
  }

  Future<void> _confirmCompletion() async {
    final order = _detail?.workOrder;
    if (order == null || _actionLoading) return;
    setState(() => _actionLoading = true);
    final ok = await _api.confirmWorkOrder(order.orderId);
    if (!mounted) return;
    setState(() => _actionLoading = false);
    if (!ok) {
      _toast('确认失败，请稍后重试');
      return;
    }
    _toast('已确认完工，请对本次服务作出评价', color: const Color(0xFF16A34A));
    await _load();
  }

  Future<void> _rejectCompletion() async {
    final order = _detail?.workOrder;
    if (order == null || _actionLoading) return;
    final controller = TextEditingController();
    final reason = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('退回重办'),
        content: TextField(
          controller: controller,
          maxLines: 3,
          maxLength: 200,
          autofocus: true,
          decoration: const InputDecoration(
            hintText: '请说明未修好的原因，便于师傅重新处理',
            border: OutlineInputBorder(),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('取消'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(controller.text.trim()),
            child: const Text('提交退回'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (reason == null) return;
    if (reason.isEmpty) {
      _toast('请填写退回原因');
      return;
    }
    setState(() => _actionLoading = true);
    final ok = await ServiceRequestApi.rejectWorkOrder(
      workOrderId: order.orderId,
      reason: reason,
    );
    if (!mounted) return;
    setState(() => _actionLoading = false);
    if (!ok) {
      _toast('退回失败，请稍后重试');
      return;
    }
    _toast('已退回，后勤调度中心将安排师傅重新处理', color: const Color(0xFFD97706));
    await _load();
  }

  Future<void> _openEvaluation() async {
    final detail = _detail;
    if (detail == null) return;
    final workOrder = detail.workOrder;
    await EvaluationBottomSheet.show(
      context,
      requestId: detail.requestId,
      workOrderId: workOrder?.orderId,
      requestNo: detail.requestNo,
      location: _locationText(detail),
    );
    if (!mounted) return;
    await _load();
  }

  Future<void> _call(String? phone) async {
    final value = phone?.trim() ?? '';
    if (value.isEmpty) {
      _toast('暂未获取到师傅电话');
      return;
    }
    final uri = Uri(scheme: 'tel', path: value);
    final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!ok && mounted) {
      _toast('无法打开电话，请手动拨打 $value');
    }
  }

  String _locationText(ServiceRequestDetail detail) {
    final form = detail.formData;
    final dorm = form['dormBuilding']?.toString().trim() ?? '';
    final room = form['address']?.toString().trim() ?? '';
    if (dorm.isNotEmpty && room.isNotEmpty) return '$dorm · $room';
    for (final key in const ['address', 'repairAddress', 'location']) {
      final value = form[key]?.toString().trim() ?? '';
      if (value.isNotEmpty) return value;
    }
    return detail.workOrder?.repairAddress ?? detail.itemName;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF7FAF9),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        title: const Text(
          '申请详情',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        centerTitle: true,
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(strokeWidth: 2))
          : _error
          ? _buildError()
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
                children: [
                  _buildHeaderCard(_detail!),
                  const SizedBox(height: 12),
                  _buildWorkOrderCard(_detail!),
                  const SizedBox(height: 12),
                  _buildFormCard(_detail!),
                  if (_detail!.timeline.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    _buildTimelineCard(_detail!),
                  ],
                  if (_detail!.evaluation != null) ...[
                    const SizedBox(height: 12),
                    _buildEvaluationCard(_detail!.evaluation!),
                  ],
                ],
              ),
            ),
      bottomNavigationBar: (_loading || _error || _detail == null)
          ? null
          : _buildActionBar(_detail!),
    );
  }

  Widget _buildError() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(
            Icons.error_outline_rounded,
            size: 48,
            color: Color(0xFFCBD5E1),
          ),
          const SizedBox(height: 12),
          const Text(
            '申请详情加载失败',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(height: 12),
          TextButton(onPressed: _load, child: const Text('重新加载')),
        ],
      ),
    );
  }

  Widget _card({required Widget child}) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: child,
    );
  }

  Widget _buildHeaderCard(ServiceRequestDetail detail) {
    final color = _statusColor(detail.status);
    return _card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  detail.requestNo.isEmpty ? '申请编号待生成' : detail.requestNo,
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF64748B),
                  ),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  serviceRequestStatusLabel(detail.status),
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    color: color,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            detail.title,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            '${detail.itemName} · ${_formatTime(detail.submittedAt)}提交',
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
            ),
          ),
          if (detail.rejectReason?.isNotEmpty == true) ...[
            const SizedBox(height: 10),
            _notice('驳回原因：${detail.rejectReason}', const Color(0xFFDC2626)),
          ],
          if (detail.cancelReason?.isNotEmpty == true) ...[
            const SizedBox(height: 10),
            _notice('撤销原因：${detail.cancelReason}', const Color(0xFF64748B)),
          ],
        ],
      ),
    );
  }

  Widget _buildWorkOrderCard(ServiceRequestDetail detail) {
    final order = detail.workOrder;
    return _card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '处理进度',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 10),
          if (order == null)
            const Text(
              '后勤已受理，正在等待派单。派单后可查看师傅信息并催单。',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                height: 1.6,
                color: Color(0xFF64748B),
              ),
            )
          else ...[
            _infoRow('工单状态', workOrderStatusLabel(order.status)),
            _infoRow('工单编号', order.orderNo.isEmpty ? '-' : order.orderNo),
            if (order.assigneeName?.isNotEmpty == true)
              _infoRow('处理师傅', order.assigneeName!),
            if (order.repairAddress?.isNotEmpty == true)
              _infoRow('报修地址', order.repairAddress!),
            if (order.slaHours != null) _infoRow('承诺时限', '${order.slaHours}h'),
            _infoRow('SLA', _slaText(order)),
            if (order.completionPhotoUrls.isNotEmpty ||
                order.arrivalPhotoUrl?.isNotEmpty == true) ...[
              const SizedBox(height: 10),
              const Text(
                '现场与完工照片',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textPrimary,
                ),
              ),
              const SizedBox(height: 8),
              _buildNetworkImages(order.allPhotoUrls),
            ],
            if (order.assigneePhone?.isNotEmpty == true) ...[
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: () => _call(order.assigneePhone),
                icon: const Icon(Icons.phone_outlined, size: 18),
                label: const Text('联系师傅'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.brand,
                  side: const BorderSide(color: AppColors.brand),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                ),
              ),
            ],
          ],
        ],
      ),
    );
  }

  Widget _buildFormCard(ServiceRequestDetail detail) {
    final textEntries = detail.formData.entries
        .where((entry) => entry.value is! List)
        .where((entry) => entry.value?.toString().trim().isNotEmpty == true)
        .toList();
    final imageUrls = <String>[];
    for (final entry in detail.formData.entries) {
      final value = entry.value;
      if (value is List) {
        imageUrls.addAll(
          value.map((e) => e.toString()).where((e) => e.startsWith('http')),
        );
      }
    }
    if (textEntries.isEmpty && imageUrls.isEmpty) {
      return const SizedBox.shrink();
    }
    return _card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '申请信息',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 8),
          for (final entry in textEntries)
            _infoRow(_fieldLabel(entry.key), entry.value.toString()),
          if (imageUrls.isNotEmpty) ...[
            const SizedBox(height: 10),
            const Text(
              '申请材料',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 8),
            _buildNetworkImages(imageUrls),
          ],
        ],
      ),
    );
  }

  Widget _buildTimelineCard(ServiceRequestDetail detail) {
    return _card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '流转记录',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 12),
          for (var i = 0; i < detail.timeline.length; i++)
            _buildTimelineItem(
              detail.timeline[i],
              i == detail.timeline.length - 1,
            ),
        ],
      ),
    );
  }

  Widget _buildTimelineItem(ServiceRequestTimelineItem item, bool isLast) {
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Column(
            children: [
              Container(
                width: 8,
                height: 8,
                margin: const EdgeInsets.only(top: 4),
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  color: AppColors.brand,
                ),
              ),
              if (!isLast)
                Expanded(
                  child: Container(width: 1, color: const Color(0xFFE2E8F0)),
                ),
            ],
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(bottom: 14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          _timelineActionLabel(item.action),
                          style: const TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: AppColors.textPrimary,
                          ),
                        ),
                      ),
                      Text(
                        _formatTime(item.createdAt),
                        style: const TextStyle(
                          fontSize: 12,
                          color: Color(0xFF64748B),
                        ),
                      ),
                    ],
                  ),
                  if (item.operatorName.isNotEmpty)
                    Text(
                      item.operatorName,
                      style: const TextStyle(
                        fontSize: 12,
                        color: Color(0xFF64748B),
                      ),
                    ),
                  if (item.comment?.isNotEmpty == true) ...[
                    const SizedBox(height: 4),
                    Text(
                      item.comment!,
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        height: 1.5,
                        color: Color(0xFF475569),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEvaluationCard(ServiceEvaluationInfo evaluation) {
    return _card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '我的评价',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              for (var i = 0; i < 5; i++)
                Icon(
                  i < evaluation.rating
                      ? Icons.star_rounded
                      : Icons.star_border_rounded,
                  size: 20,
                  color: const Color(0xFFF59E0B),
                ),
              const SizedBox(width: 6),
              Text(
                '${evaluation.rating}.0',
                style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
              ),
            ],
          ),
          if (evaluation.comment?.isNotEmpty == true) ...[
            const SizedBox(height: 6),
            Text(
              evaluation.comment!,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                height: 1.5,
                color: Color(0xFF475569),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildActionBar(ServiceRequestDetail detail) {
    final buttons = <Widget>[];
    if (detail.canConfirm) {
      buttons.add(
        Expanded(
          child: OutlinedButton(
            onPressed: _actionLoading ? null : _rejectCompletion,
            child: const Text('未修好，退回'),
          ),
        ),
      );
      buttons.add(const SizedBox(width: 10));
      buttons.add(
        Expanded(
          child: ElevatedButton(
            onPressed: _actionLoading ? null : _confirmCompletion,
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.brand,
              foregroundColor: Colors.white,
            ),
            child: const Text('确认完工'),
          ),
        ),
      );
    } else if (detail.canEvaluate) {
      buttons.add(
        Expanded(
          child: ElevatedButton(
            onPressed: _actionLoading ? null : _openEvaluation,
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.brand,
              foregroundColor: Colors.white,
            ),
            child: const Text('评价本次服务'),
          ),
        ),
      );
    } else if (detail.canUrge) {
      if (detail.status == 'SUBMITTED') {
        buttons.add(
          Expanded(
            child: OutlinedButton(
              onPressed: _actionLoading ? null : _cancel,
              child: const Text('撤销申请'),
            ),
          ),
        );
        buttons.add(const SizedBox(width: 10));
      }
      buttons.add(
        Expanded(
          child: ElevatedButton(
            onPressed: _actionLoading ? null : _urge,
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.brand,
              foregroundColor: Colors.white,
            ),
            child: _actionLoading
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Text('催单'),
          ),
        ),
      );
    }
    if (buttons.isEmpty) {
      return SafeArea(
        top: false,
        child: Container(
          color: Colors.white,
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
          child: SizedBox(
            width: double.infinity,
            child: OutlinedButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('返回'),
            ),
          ),
        ),
      );
    }
    return SafeArea(
      top: false,
      child: Container(
        color: Colors.white,
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
        child: Row(children: buttons),
      ),
    );
  }

  Widget _infoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 72,
            child: Text(
              label,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: Color(0xFF64748B),
              ),
            ),
          ),
          Expanded(
            child: Text(
              value.isEmpty ? '-' : value,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                fontWeight: FontWeight.w500,
                color: AppColors.textPrimary,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _notice(String text, Color color) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        text,
        style: TextStyle(
          fontFamily: 'Noto Sans SC',
          fontSize: 12,
          height: 1.4,
          color: color,
        ),
      ),
    );
  }

  Widget _buildNetworkImages(List<String> urls) {
    if (urls.isEmpty) return const SizedBox.shrink();
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        for (final url in urls)
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: Image.network(
              url.startsWith('http') ? url : '${ApiClient.defaultBaseUrl}$url',
              width: 88,
              height: 88,
              fit: BoxFit.cover,
              errorBuilder: (_, _, _) => Container(
                width: 88,
                height: 88,
                color: const Color(0xFFF1F5F9),
                alignment: Alignment.center,
                child: const Icon(
                  Icons.broken_image_outlined,
                  size: 22,
                  color: Color(0xFF94A3B8),
                ),
              ),
            ),
          ),
      ],
    );
  }

  Color _statusColor(String status) {
    return switch (status) {
      'COMPLETED' => const Color(0xFF059669),
      'REJECTED' => const Color(0xFFDC2626),
      'CANCELLED' => const Color(0xFF64748B),
      'PROCESSING' => AppColors.brand,
      _ => const Color(0xFFD97706),
    };
  }

  String _slaText(WorkOrderInfo order) {
    if (order.status == 'COMPLETED') return '已完成';
    final remaining = order.remainingHours;
    if (remaining == null) return '-';
    if (remaining < 0) return '已超时 ${remaining.abs().toStringAsFixed(1)}h';
    return '剩余 ${remaining.toStringAsFixed(1)}h';
  }

  String _formatTime(String? raw) {
    if (raw == null || raw.isEmpty) return '-';
    final normalized = raw.replaceFirst('T', ' ');
    return normalized.length > 16 ? normalized.substring(0, 16) : normalized;
  }

  String _fieldLabel(String key) {
    return switch (key) {
      'contactPhone' => '联系电话',
      'address' || 'repairAddress' || 'location' => '报修地址',
      'faultType' || 'category' => '故障类型',
      'description' => '问题描述',
      'dormBuilding' => '宿舍楼',
      'expectDate' => '期望上门日期',
      'contactName' => '联系人',
      _ => key,
    };
  }

  String _timelineActionLabel(String action) {
    return switch (action) {
      'SUBMIT' => '提交申请',
      'DISPATCH' => '已派单',
      'START' => '开始处理',
      'ARRIVAL_PHOTO' => '现场拍照',
      'COMPLETION_PHOTO' => '完工拍照',
      'COMPLETE' => '工单办结',
      'CONFIRM' => '确认完工',
      'REOPEN' => '退回重办',
      'REJECT' => '申请驳回',
      'CANCEL' => '撤销申请',
      'EVALUATE' => '提交评价',
      'URGE' => '催单',
      _ => action.isEmpty ? '状态更新' : action,
    };
  }
}
