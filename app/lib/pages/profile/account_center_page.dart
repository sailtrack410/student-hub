import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/api_client.dart';
import '../../api/auth_service.dart';
import '../../api/profile_api.dart';
import '../../services/community_socket.dart';
import '../../services/qq_auth_service.dart';
import '../../theme/app_theme.dart';
import '../../widgets/user_avatar.dart';
import '../login/login_page.dart';
import 'device_management_page.dart';
import 'email_binding_page.dart';
import 'profile_edit_page.dart';
import 'jw_auth_page.dart';

/// 账号中心：账号资料、绑定状态、安全与设备入口
class AccountCenterPage extends StatefulWidget {
  final AuthService authService;
  final Map<String, dynamic>? initialMe;
  final Map<String, dynamic>? initialAuthor;

  const AccountCenterPage({
    super.key,
    required this.authService,
    this.initialMe,
    this.initialAuthor,
  });

  @override
  State<AccountCenterPage> createState() => _AccountCenterPageState();
}

class _AccountCenterPageState extends State<AccountCenterPage> {
  Map<String, dynamic>? _me;
  Map<String, dynamic>? _author;
  String _username = '';
  bool _passwordSet = false;
  bool _qqBound = false;
  String _email = '';
  bool _loading = true;
  bool _qqBinding = false;
  final QqAuthService _qqAuthService = QqAuthService();

  @override
  void initState() {
    super.initState();
    if (widget.initialMe != null) {
      _me = widget.initialMe;
      _author = widget.initialAuthor;
      _loading = false;
      _loadCredentials();
    } else {
      _load();
    }
  }

  Future<void> _loadCredentials() async {
    final credentials = await ProfileApi.fetchCredentials();
    if (!mounted) return;
    setState(() {
      _username = credentials?['username']?.toString() ?? '';
      _passwordSet = credentials?['passwordSet'] == true;
      _qqBound = credentials?['qqBound'] == true;
      _email = credentials?['email']?.toString() ?? '';
    });
  }

  Future<void> _load() async {
    final me = await ProfileApi.fetchMe();
    final user = me?['user'];
    final userId = user is Map<String, dynamic>
        ? user['userId']?.toString()
        : me?['userId']?.toString();
    final author = userId == null
        ? null
        : await ProfileApi.fetchAuthorProfile(userId);
    if (!mounted) return;
    setState(() {
      _me = me;
      _author = author;
      _loading = false;
    });
    _loadCredentials();
  }

  Map<String, dynamic>? get _user {
    final user = _me?['user'];
    return user is Map<String, dynamic> ? user : _me;
  }

  String get _name {
    final name = _user?['displayName']?.toString();
    return name == null || name.isEmpty ? '同学' : name;
  }

  String? get _avatarPath {
    final path = _user?['avatarPath']?.toString();
    return path == null || path.isEmpty ? null : path;
  }

  String get _qqAccountLabel {
    if (!_qqBound) return '未绑定';
    if (_username.startsWith('qq_')) return _username;
    return '已绑定';
  }

  String get _loginMethodLabel {
    final methods = <String>[];
    if (_qqBound) methods.add('QQ 授权');
    if (_passwordSet) methods.add('账号密码');
    return methods.isEmpty ? '微信授权' : methods.join(' + ');
  }

  String get _accountId {
    if (_username.isNotEmpty) return _username;
    return _user?['username']?.toString() ??
        _user?['userId']?.toString() ??
        _author?['userId']?.toString() ??
        '';
  }

  String get _phone {
    final phone = _user?['phone']?.toString() ?? '';
    if (phone.isEmpty) return '未绑定';
    if (phone.length >= 11)
      return '${phone.substring(0, 3)}****${phone.substring(7)}';
    return phone;
  }

  bool get _jwBound {
    final growth = _user?['growth'];
    return growth is Map<String, dynamic> &&
        (growth['uid']?.toString().isNotEmpty ?? false);
  }

  int get _level {
    final growth = _user?['growth'];
    final value = growth is Map<String, dynamic> ? growth['level'] : null;
    return value is num ? value.toInt() : 1;
  }

  String get _authorizationLabel {
    switch (_user?['authorizationStatus']?.toString()) {
      case 'AUTHORIZED':
        return '已实名认证';
      case 'PENDING':
        return '认证审核中';
      case 'REJECTED':
        return '认证未通过';
      default:
        return '基础认证';
    }
  }

  Color get _authorizationColor {
    return _user?['authorizationStatus']?.toString() == 'AUTHORIZED'
        ? const Color(0xFF16A34A)
        : const Color(0xFFF59E0B);
  }

  void _copyAccountId() {
    Clipboard.setData(ClipboardData(text: _accountId));
    ScaffoldMessenger.of(context)
        .showSnackBar(const SnackBar(content: Text('账号 ID 已复制')));
  }

  Future<void> _bindQq() async {
    if (_qqBound || _qqBinding) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('绑定 QQ 账号'),
        content: const Text('绑定后将使用 QQ 昵称和头像；已自定义的昵称、头像不会被覆盖。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('去授权'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _qqBinding = true);
    try {
      final result = await _qqAuthService.login(privacyAccepted: true);
      final updated = await ProfileApi.bindQq(
        accessToken: result.accessToken,
        openId: result.openId.isEmpty ? null : result.openId,
      );
      if (!mounted) return;
      if (updated == null) {
        _showSnack('绑定失败，请稍后重试');
        return;
      }
      setState(() {
        _me = {...?_me, ...updated};
        _qqBound = true;
      });
      _showSnack('QQ 账号已绑定');
    } on QqAuthException catch (error) {
      if (!mounted || error.cancelled) return;
      _showSnack(error.message);
    } on ApiException catch (error) {
      if (!mounted) return;
      _showSnack(error.message);
    } catch (_) {
      if (!mounted) return;
      _showSnack('绑定失败，请稍后重试');
    } finally {
      if (mounted) setState(() => _qqBinding = false);
    }
  }

  void _showSnack(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _editCredentials() async {
    final initialUsername = RegExp(r'^[A-Za-z0-9_]{4,20}$').hasMatch(_username)
        ? _username
        : '';
    final usernameController = TextEditingController(text: initialUsername);
    final passwordController = TextEditingController();
    final confirmController = TextEditingController();
    final currentController = TextEditingController();
    final result = await showModalBottomSheet<Map<String, String>>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.of(ctx).viewInsets.bottom),
        child: Container(
          decoration: const BoxDecoration(
            color: Color(0xFFF8FAFC),
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: EdgeInsets.fromLTRB(
            20,
            10,
            20,
            MediaQuery.of(ctx).padding.bottom + 20,
          ),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 36,
                    height: 4,
                    decoration: BoxDecoration(
                      color: const Color(0xFFCBD5E1),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                const Text(
                  '账号与登录密码',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 19,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                ),
                const SizedBox(height: 4),
                const Text(
                  '账号 ID 用于登录，修改密码前需要验证当前密码。',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF64748B),
                  ),
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: Column(
                    children: [
                      TextField(
                        controller: usernameController,
                        maxLength: 20,
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 14,
                        ),
                        decoration: _accountInput(
                          '账号 ID',
                          '4-20 位字母、数字或下划线',
                          Icons.alternate_email_rounded,
                        ),
                      ),
                      const SizedBox(height: 10),
                      TextField(
                        controller: currentController,
                        obscureText: true,
                        enabled: _passwordSet,
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 14,
                        ),
                        decoration: _accountInput(
                          '当前密码',
                          _passwordSet ? '请输入当前密码以验证身份' : '当前账号尚未设置密码',
                          Icons.lock_clock_outlined,
                        ),
                      ),
                      const SizedBox(height: 10),
                      TextField(
                        controller: passwordController,
                        obscureText: true,
                        maxLength: 20,
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 14,
                        ),
                        decoration: _accountInput(
                          '新密码',
                          '8-20 位，须同时包含字母和数字',
                          Icons.lock_outline_rounded,
                        ),
                      ),
                      const SizedBox(height: 10),
                      TextField(
                        controller: confirmController,
                        obscureText: true,
                        maxLength: 20,
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 14,
                        ),
                        decoration: _accountInput(
                          '确认新密码',
                          '再次输入新密码',
                          Icons.verified_user_outlined,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  children: const [
                    Icon(
                      Icons.security_rounded,
                      size: 15,
                      color: Color(0xFF16A34A),
                    ),
                    SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        '修改后将立即生效，其他设备不会自动退出。',
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 12,
                          color: Color(0xFF64748B),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: SizedBox(
                        height: 46,
                        child: OutlinedButton(
                          onPressed: () => Navigator.of(ctx).pop(),
                          style: OutlinedButton.styleFrom(
                            side: const BorderSide(color: Color(0xFFE2E8F0)),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          child: const Text(
                            '取消',
                            style: TextStyle(
                              fontFamily: 'Noto Sans SC',
                              color: Color(0xFF64748B),
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      flex: 2,
                      child: SizedBox(
                        height: 46,
                        child: ElevatedButton(
                          onPressed: () => Navigator.of(ctx).pop({
                            'username': usernameController.text,
                            'password': passwordController.text,
                            'confirm': confirmController.text,
                            'current': currentController.text,
                          }),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xFF2563EB),
                            foregroundColor: Colors.white,
                            elevation: 0,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          child: const Text(
                            '验证并保存',
                            style: TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 14,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
    usernameController.dispose();
    passwordController.dispose();
    confirmController.dispose();
    currentController.dispose();
    if (result == null || !mounted) return;

    final username = (result['username'] ?? '').trim();
    final password = result['password'] ?? '';
    final confirm = result['confirm'] ?? '';
    final current = result['current'] ?? '';
    if (!RegExp(r'^[A-Za-z0-9_]{4,20}$').hasMatch(username)) {
      _toast('账号 ID 需为 4-20 位字母、数字或下划线');
      return;
    }
    if (!RegExp(r'^(?=.*[A-Za-z])(?=.*\d)[A-Za-z0-9!@#$%^&*._-]{8,20}$')
        .hasMatch(password)) {
      _toast('密码需为 8-20 位且同时包含字母和数字');
      return;
    }
    if (password != confirm) {
      _toast('两次输入的新密码不一致');
      return;
    }
    if (_passwordSet && current.isEmpty) {
      _toast('请输入当前密码进行验证');
      return;
    }
    try {
      final response = await ProfileApi.updateCredentials(
        username: username,
        password: password,
        currentPassword: current,
      );
      if (!mounted) return;
      if (response == null) {
        _toast('保存失败，请稍后重试');
        return;
      }
      setState(() {
        _username = response['username']?.toString() ?? username;
        _passwordSet = true;
      });
      _toast('账号与密码已更新');
    } on ApiException catch (e) {
      if (!mounted) return;
      _toast(e.message);
    } catch (_) {
      if (!mounted) return;
      _toast('保存失败，请稍后重试');
    }
  }

  InputDecoration _accountInput(String label, String hint, IconData icon) {
    return InputDecoration(
      labelText: label,
      hintText: hint,
      prefixIcon: Icon(icon, size: 19, color: const Color(0xFF94A3B8)),
      counterText: '',
      labelStyle: const TextStyle(
        fontFamily: 'Noto Sans SC',
        fontSize: 12,
        color: Color(0xFF64748B),
      ),
      hintStyle: const TextStyle(
        fontFamily: 'Noto Sans SC',
        fontSize: 12,
        color: Color(0xFF64748B),
      ),
      filled: true,
      fillColor: const Color(0xFFF8FAFC),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: BorderSide.none,
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: BorderSide.none,
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: Color(0xFF2563EB)),
      ),
    );
  }

  void _toast(String text) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _logout() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        title: const Text(
          '退出登录',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
        ),
        content: const Text('确定要退出当前账号吗？退出后需重新登录。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('取消'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFFEF4444),
            ),
            child: const Text('确认退出', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await CommunitySocket.shared.disconnect();
    await widget.authService.logout();
    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(
        builder: (_) => LoginPage(authService: widget.authService),
      ),
      (route) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF6F7F9),
      appBar: AppBar(
        backgroundColor: const Color(0xFFF6F7F9),
        elevation: 0,
        scrolledUnderElevation: 0,
        foregroundColor: const Color(0xFF20221F),
        title: const Text(
          '账号中心',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
        ),
        centerTitle: true,
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(strokeWidth: 2))
          : ListView(
              padding: const EdgeInsets.fromLTRB(22, 12, 22, 30),
              children: [
                _buildHeader(),
                const SizedBox(height: 14),
                _buildInfoCard(),
                const SizedBox(height: 14),
                _buildSecurityCard(),
                const SizedBox(height: 14),
                _buildEditCard(),
                const SizedBox(height: 22),
                SizedBox(
                  height: 46,
                  child: OutlinedButton(
                    onPressed: _logout,
                    style: OutlinedButton.styleFrom(
                      foregroundColor: const Color(0xFFEF4444),
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
                const SizedBox(height: 20),
              ],
            ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: Row(
        children: [
          UserAvatar(
            name: _name,
            avatarPath: _avatarPath,
            size: 62,
            baseUrl: widget.authService.client.baseUrl,
            fontSize: 24,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Flexible(
                      child: Text(
                        _name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF0F172A),
                        ),
                      ),
                    ),
                    const SizedBox(width: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFFEFF6FF),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: const Color(0xFFBFDBFE)),
                      ),
                      child: Text(
                        'Lv.$_level',
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: AppColors.brand,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 5),
                GestureDetector(
                  onTap: _copyAccountId,
                  child: Row(
                    children: [
                      Text(
                        '账号 ID：$_accountId',
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 12,
                          color: Color(0xFF64748B),
                        ),
                      ),
                      const SizedBox(width: 4),
                      const Icon(
                        Icons.copy_rounded,
                        size: 12,
                        color: Color(0xFF94A3B8),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoCard() {
    return _buildCard('账号信息', [
      _infoRow('账号 ID', _accountId, onTap: _editCredentials),
      _infoRow(
        '登录密码',
        _passwordSet ? '已设置' : '未设置',
        valueColor: _passwordSet
            ? const Color(0xFF16A34A)
            : const Color(0xFFF59E0B),
        onTap: _editCredentials,
      ),
      _infoRow(
        '手机号',
        _phone,
        valueColor: _phone == '未绑定' ? const Color(0xFFF59E0B) : null,
      ),
      _infoRow(
        '邮箱',
        _email.isEmpty ? '未绑定' : _email,
        valueColor: _email.isEmpty ? const Color(0xFFF59E0B) : null,
        onTap: () async {
          await Navigator.of(context).push(
            MaterialPageRoute(
              builder: (_) => EmailBindingPage(
                authService: widget.authService,
                currentEmail: _email.isEmpty ? null : _email,
              ),
            ),
          );
          _loadCredentials();
        },
      ),
      _infoRow(
        'QQ 账号',
        _qqBinding ? '授权中…' : _qqAccountLabel,
        valueColor: _qqBound
            ? const Color(0xFF16A34A)
            : const Color(0xFFF59E0B),
        onTap: _qqBound || _qqBinding ? null : _bindQq,
      ),
      _infoRow('登录方式', _loginMethodLabel),
      _infoRow(
        '身份类型',
        _user?['identityType']?.toString() == 'STUDENT' ? '学生' : '校园用户',
      ),
      _infoRow('实名认证', _authorizationLabel, valueColor: _authorizationColor),
      _infoRow(
        '教务绑定',
        _jwBound ? '已绑定（课表/成绩同步）' : '未绑定',
        valueColor: _jwBound
            ? const Color(0xFF16A34A)
            : const Color(0xFFF59E0B),
        onTap: () async {
          await Navigator.of(context)
              .push(MaterialPageRoute(builder: (_) => const JwAuthPage()));
          _load();
        },
      ),
    ]);
  }

  Widget _buildSecurityCard() {
    return _buildCard('账号安全', [
      _linkRow(
        '登录设备管理',
        onTap: () {
          Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const DeviceManagementPage()),
          );
        },
      ),
    ]);
  }

  Widget _buildEditCard() {
    return _buildCard('账号资料', [
      _linkRow(
        '编辑个人资料',
        onTap: () async {
          await Navigator.of(context).push(
            MaterialPageRoute(
              builder: (_) => ProfileEditPage(authService: widget.authService),
            ),
          );
          _load();
        },
      ),
    ]);
  }

  Widget _buildCard(String title, List<Widget> children) {
    return Container(
      padding: const EdgeInsets.fromLTRB(0, 12, 0, 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              fontWeight: FontWeight.bold,
              color: Color(0xFF64748B),
            ),
          ),
          const SizedBox(height: 4),
          for (int i = 0; i < children.length; i++) ...[
            if (i > 0) const Divider(height: 1),
            children[i],
          ],
        ],
      ),
    );
  }

  Widget _infoRow(
    String label,
    String value, {
    Color? valueColor,
    VoidCallback? onTap,
  }) {
    final row = Padding(
      padding: const EdgeInsets.symmetric(vertical: 11),
      child: Row(
        children: [
          Text(
            label,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 13,
              color: Color(0xFF334155),
            ),
          ),
          const Spacer(),
          Expanded(
            child: Text(
              value,
              textAlign: TextAlign.right,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: valueColor ?? const Color(0xFF0F172A),
              ),
            ),
          ),
          if (onTap != null) ...[
            const SizedBox(width: 4),
            const Icon(
              Icons.chevron_right_rounded,
              size: 16,
              color: Color(0xFFCBD5E1),
            ),
          ],
        ],
      ),
    );
    return onTap == null ? row : InkWell(onTap: onTap, child: row);
  }

  Widget _linkRow(String title, {required VoidCallback onTap}) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Row(
          children: [
            Expanded(
              child: Text(
                title,
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  color: Color(0xFF0F172A),
                ),
              ),
            ),
            const Icon(
              Icons.chevron_right_rounded,
              size: 18,
              color: Color(0xFFCBD5E1),
            ),
          ],
        ),
      ),
    );
  }
}
