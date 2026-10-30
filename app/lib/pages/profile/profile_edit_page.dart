import 'package:flutter/material.dart';

import '../../api/auth_service.dart';
import '../../api/device_api.dart';
import '../../api/profile_api.dart';
import '../../services/community_socket.dart';
import '../../theme/app_theme.dart';
import '../login/login_page.dart';
import 'device_management_page.dart';

/// APP-个人资料与隐私安全
///
/// 姓名、账号、身份、手机号来自 `GET /api/common/auth/me`；
/// 隐私开关来自 `GET/PUT /api/app/profile/privacy`；
/// 登录设备来自 `GET /api/app/devices`，不再使用任何演示资料。
class ProfileEditPage extends StatefulWidget {
  final AuthService? authService;

  const ProfileEditPage({super.key, this.authService});

  @override
  State<ProfileEditPage> createState() => _ProfileEditPageState();
}

class _ProfileEditPageState extends State<ProfileEditPage> {
  Map<String, dynamic>? _me;
  List<ManagedDevice> _devices = const [];
  bool _loading = true;
  bool _saving = false;
  bool _showAcademicInfo = true;
  bool _showMedals = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final results = await Future.wait<Object?>([
        ProfileApi.fetchMe(),
        ProfileApi.fetchPrivacy(),
        DeviceApi.fetchDevices(),
      ]);
      if (!mounted) return;
      final privacy = results[1] as Map<String, dynamic>?;
      setState(() {
        _me = results[0] as Map<String, dynamic>?;
        _devices = (results[2] as List).whereType<ManagedDevice>().toList();
        _showAcademicInfo = privacy?['showAcademicInfo'] != false;
        _showMedals = privacy?['showMedals'] != false;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loading = false);
    }
  }

  Map<String, dynamic>? get _user {
    final raw = _me?['user'];
    return raw is Map<String, dynamic> ? raw : _me;
  }

  String get _name {
    final value = _user?['displayName']?.toString().trim() ?? '';
    return value.isEmpty ? '同学' : value;
  }

  String get _uid {
    final value =
        _user?['userId']?.toString() ?? _user?['username']?.toString() ?? '';
    return value.isEmpty ? '未绑定' : value;
  }

  String get _phone {
    final value = _user?['phone']?.toString() ?? '';
    if (value.isEmpty) return '未绑定';
    if (value.length >= 11) {
      return '${value.substring(0, 3)}****${value.substring(7)}';
    }
    return value;
  }

  String get _identity {
    return switch (_user?['identityType']?.toString()) {
      'STUDENT' => '学生',
      'TEACHER' => '教师',
      'LOGISTICS' => '后勤人员',
      'ADMIN' => '管理员',
      _ => '未设置',
    };
  }

  Future<void> _save() async {
    if (_saving) return;
    setState(() => _saving = true);
    final ok = await ProfileApi.updatePrivacy(
      showAcademicInfo: _showAcademicInfo,
      showMedals: _showMedals,
    );
    if (!mounted) return;
    setState(() => _saving = false);
    if (ok == null) {
      _toast('保存失败，请稍后重试');
      return;
    }
    _toast('隐私设置已保存');
    Navigator.of(context).pop();
  }

  Future<void> _kickDevice(ManagedDevice device) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('下线设备'),
        content: Text('确定要下线「${device.displayName}」吗？该设备将立即退出登录。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('取消'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.danger),
            child: const Text('确认下线'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    final ok = await DeviceApi.kickDevice(device.deviceId);
    if (!mounted) return;
    if (!ok) {
      _toast('下线失败，请稍后重试');
      return;
    }
    setState(() {
      _devices = _devices
          .where((item) => item.deviceId != device.deviceId)
          .toList();
    });
    _toast('设备已下线');
  }

  Future<void> _logout() async {
    final authService = widget.authService;
    if (authService == null) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('退出登录'),
        content: const Text('确定要退出当前账号吗？退出后需重新登录。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('取消'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.danger),
            child: const Text('确认退出'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await CommunitySocket.shared.disconnect();
    await authService.logout();
    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => LoginPage(authService: authService)),
      (route) => false,
    );
  }

  void _toast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), behavior: SnackBarBehavior.floating),
    );
  }

  @override
  Widget build(BuildContext context) {
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
          '账号资料与隐私安全',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: Color(0xFF0F172A),
          ),
        ),
        centerTitle: true,
        actions: [
          TextButton(
            onPressed: _saving ? null : _save,
            child: Text(
              _saving ? '保存中' : '保存',
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: Color(0xFF2563EB),
              ),
            ),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(strokeWidth: 2))
          : RefreshIndicator(
              color: AppColors.brand,
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  _card([
                    _infoRow('姓名', _name),
                    const Divider(height: 20),
                    _infoRow('账号 ID', _uid),
                    const Divider(height: 20),
                    _infoRow('身份', _identity),
                    const Divider(height: 20),
                    _infoRow('绑定手机', _phone),
                  ]),
                  const SizedBox(height: 16),
                  const Text(
                    '隐私偏好设置',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF475569),
                    ),
                  ),
                  const SizedBox(height: 8),
                  _card([
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text(
                        '展示学业信息',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      subtitle: const Text(
                        '在个人主页展示院系、年级等学业信息',
                        style: TextStyle(
                          fontSize: 12,
                          color: Color(0xFF64748B),
                        ),
                      ),
                      value: _showAcademicInfo,
                      activeThumbColor: AppColors.brand,
                      onChanged: (value) =>
                          setState(() => _showAcademicInfo = value),
                    ),
                    const Divider(height: 1),
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text(
                        '展示佩戴勋章',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      subtitle: const Text(
                        '在社区头像旁展示当前佩戴的勋章',
                        style: TextStyle(
                          fontSize: 12,
                          color: Color(0xFF64748B),
                        ),
                      ),
                      value: _showMedals,
                      activeThumbColor: AppColors.brand,
                      onChanged: (value) => setState(() => _showMedals = value),
                    ),
                  ]),
                  const SizedBox(height: 20),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        '登录设备',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF475569),
                        ),
                      ),
                      Text(
                        '${_devices.length} 台在线',
                        style: const TextStyle(
                          fontSize: 12,
                          color: Color(0xFF64748B),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  _card([
                    if (_devices.isEmpty)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 12),
                        child: Text(
                          '暂无登录设备记录',
                          style: TextStyle(
                            fontSize: 12,
                            color: Color(0xFF64748B),
                          ),
                        ),
                      )
                    else
                      for (var i = 0; i < _devices.length; i++) ...[
                        if (i > 0) const Divider(height: 1),
                        _deviceRow(_devices[i]),
                      ],
                    const SizedBox(height: 6),
                    Align(
                      alignment: Alignment.centerRight,
                      child: TextButton(
                        onPressed: () => Navigator.of(context).push(
                          MaterialPageRoute(
                            builder: (_) => const DeviceManagementPage(),
                          ),
                        ),
                        child: const Text('管理全部登录设备'),
                      ),
                    ),
                  ]),
                  if (widget.authService != null) ...[
                    const SizedBox(height: 24),
                    SizedBox(
                      width: double.infinity,
                      height: 46,
                      child: OutlinedButton(
                        onPressed: _logout,
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppColors.danger,
                          side: const BorderSide(color: Color(0xFFFECACA)),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(23),
                          ),
                        ),
                        child: const Text(
                          '退出当前账号',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                  ],
                  const SizedBox(height: 28),
                ],
              ),
            ),
    );
  }

  Widget _card(List<Widget> children) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(children: children),
    );
  }

  Widget _infoRow(String label, String value) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: const TextStyle(fontSize: 13, color: Color(0xFF64748B)),
        ),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.right,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: Color(0xFF0F172A),
            ),
          ),
        ),
      ],
    );
  }

  Widget _deviceRow(ManagedDevice device) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Icon(
        device.isCurrent ? Icons.phone_iphone_rounded : Icons.devices_other,
        color: device.isCurrent ? AppColors.brand : const Color(0xFF64748B),
      ),
      title: Text(
        device.displayName,
        style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
      ),
      subtitle: Text(
        device.isCurrent ? '当前设备' : '最近活跃 ${_formatTime(device.lastActiveAt)}',
        style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
      ),
      trailing: device.isCurrent
          ? const Text(
              '本机',
              style: TextStyle(
                fontSize: 12,
                color: Color(0xFF16A34A),
                fontWeight: FontWeight.bold,
              ),
            )
          : TextButton(
              onPressed: () => _kickDevice(device),
              child: const Text(
                '下线',
                style: TextStyle(color: Color(0xFFEF4444), fontSize: 12),
              ),
            ),
    );
  }

  String _formatTime(DateTime? time) {
    if (time == null) return '未知';
    final local = time.toLocal();
    return '${local.month.toString().padLeft(2, '0')}-${local.day.toString().padLeft(2, '0')} '
        '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
  }
}
