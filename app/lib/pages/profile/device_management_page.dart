import 'package:flutter/material.dart';

import '../../api/device_api.dart';
import '../../theme/app_theme.dart';

/// APP-设置-设备管理 (画布 5:2041)
class DeviceManagementPage extends StatefulWidget {
  const DeviceManagementPage({super.key});

  @override
  State<DeviceManagementPage> createState() => _DeviceManagementPageState();
}

class _DeviceManagementPageState extends State<DeviceManagementPage> {
  List<ManagedDevice> _devices = const [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final devices = await DeviceApi.fetchDevices();
    if (!mounted) return;
    setState(() {
      _devices = devices;
      _loading = false;
    });
  }

  Future<void> _kick(ManagedDevice device) async {
    final confirmed = await _confirm(
      title: '下线设备',
      content: '确定要下线「${device.deviceName}」吗？该设备将立即退出登录。',
      confirmText: '确认下线',
    );
    if (confirmed != true) return;
    final ok = await DeviceApi.kickDevice(device.deviceId);
    if (!mounted) return;
    if (ok) {
      setState(
        () => _devices = _devices
            .where((e) => e.deviceId != device.deviceId)
            .toList(),
      );
      _toast('设备已下线');
    } else {
      _toast('下线失败，请稍后重试');
    }
  }

  Future<void> _logoutOthers() async {
    final others = _devices.where((e) => !e.isCurrent).toList();
    if (others.isEmpty) return;
    final confirmed = await _confirm(
      title: '下线所有其他设备',
      content: '将下线其他 ${others.length} 台设备，当前设备保持登录。被下线设备需要重新登录。',
      confirmText: '全部下线',
    );
    if (confirmed != true) return;
    final count = await DeviceApi.logoutOthers();
    if (!mounted) return;
    setState(() => _devices = _devices.where((e) => e.isCurrent).toList());
    _toast('已下线其他 $count 台设备');
  }

  Future<bool?> _confirm({
    required String title,
    required String content,
    required String confirmText,
  }) {
    return showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        title: Text(
          title,
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
        ),
        content: Text(
          content,
          style: const TextStyle(fontSize: 13, height: 1.5),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('取消', style: TextStyle(color: Color(0xFF64748B))),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFFEF4444),
            ),
            child: Text(
              confirmText,
              style: const TextStyle(color: Colors.white),
            ),
          ),
        ],
      ),
    );
  }

  void _toast(String text) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  @override
  Widget build(BuildContext context) {
    final current = _devices.where((e) => e.isCurrent).toList();
    final others = _devices.where((e) => !e.isCurrent).toList();
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(
            Icons.arrow_back_ios_new_rounded,
            size: 20,
            color: Color(0xFF0F172A),
          ),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: const Text(
          '登录设备管理',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: Color(0xFF0F172A),
          ),
        ),
        centerTitle: true,
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(strokeWidth: 2))
          : RefreshIndicator(
              color: AppColors.brand,
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: const Color(0xFFEFF6FF),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      children: [
                        const Icon(
                          Icons.shield_outlined,
                          size: 18,
                          color: AppColors.brand,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            '当前共有 ${_devices.length} 台设备登录，若有异常设备请及时下线',
                            style: const TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 12,
                              color: Color(0xFF1E40AF),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 14),
                  if (current.isNotEmpty) ...[
                    _buildSectionTitle('当前在线设备（本机）'),
                    const SizedBox(height: 8),
                    _buildDeviceCard(current.first, current: true),
                    const SizedBox(height: 14),
                  ],
                  if (others.isNotEmpty) ...[
                    _buildSectionTitle('其他已授权设备（${others.length}台）'),
                    const SizedBox(height: 8),
                    Container(
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: const Color(0xFFE2E8F0)),
                      ),
                      child: Column(
                        children: [
                          for (int i = 0; i < others.length; i++) ...[
                            if (i > 0)
                              const Divider(
                                height: 1,
                                indent: 58,
                                endIndent: 14,
                              ),
                            _buildOtherRow(others[i]),
                          ],
                        ],
                      ),
                    ),
                    const SizedBox(height: 20),
                    SizedBox(
                      height: 46,
                      child: OutlinedButton(
                        onPressed: _logoutOthers,
                        style: OutlinedButton.styleFrom(
                          foregroundColor: const Color(0xFFEF4444),
                          side: const BorderSide(color: Color(0xFFFECACA)),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(23),
                          ),
                        ),
                        child: const Text(
                          '下线所有其他设备',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                  ],
                  if (_devices.isEmpty)
                    const Padding(
                      padding: EdgeInsets.only(top: 60),
                      child: Center(
                        child: Text(
                          '暂无在线设备',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 12,
                            color: Color(0xFF64748B),
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Text(
      title,
      style: const TextStyle(
        fontFamily: 'Noto Sans SC',
        fontSize: 13,
        fontWeight: FontWeight.bold,
        color: Color(0xFF475569),
      ),
    );
  }

  Widget _buildDeviceCard(ManagedDevice device, {required bool current}) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: const BoxDecoration(
              color: Color(0xFFEFF6FF),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.phone_iphone_rounded,
              size: 21,
              color: AppColors.brand,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  device.displayName,
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  current
                      ? '绵城校园 Wi-Fi · ${_maskIp(device.ipAddress)}'
                      : '最近活跃：${_formatTime(device.lastActiveAt)}',
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF64748B),
                  ),
                ),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
            decoration: BoxDecoration(
              color: const Color(0xFFDCFCE7),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Text(
              '● 当前正在使用',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: Color(0xFF16A34A),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildOtherRow(ManagedDevice device) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: const BoxDecoration(
              color: Color(0xFFF1F5F9),
              shape: BoxShape.circle,
            ),
            child: Icon(
              device.osType == 'macOS' || device.osType == 'Windows'
                  ? Icons.laptop_mac_rounded
                  : Icons.tablet_mac_rounded,
              size: 20,
              color: const Color(0xFF64748B),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  device.displayName,
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF0F172A),
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  '最近活跃：${_formatTime(device.lastActiveAt)} · ${_maskIp(device.ipAddress)}',
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF64748B),
                  ),
                ),
              ],
            ),
          ),
          TextButton(
            onPressed: () => _kick(device),
            child: const Text(
              '下线设备',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: Color(0xFFEF4444),
              ),
            ),
          ),
        ],
      ),
    );
  }

  String _maskIp(String ip) {
    if (ip.isEmpty) return 'IP 未知';
    final parts = ip.split('.');
    if (parts.length == 4) return '${parts[0]}.${parts[1]}.*.*';
    return ip;
  }

  String _formatTime(DateTime? time) {
    if (time == null) return '未知';
    final diff = DateTime.now().difference(time);
    if (diff.inMinutes < 1) return '刚刚';
    if (diff.inMinutes < 60) return '${diff.inMinutes} 分钟前';
    if (diff.inHours < 24) return '${diff.inHours} 小时前';
    return '${diff.inDays} 天前';
  }
}
