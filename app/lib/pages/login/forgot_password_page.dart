import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/auth_service.dart';
import '../../theme/app_theme.dart';

/// 找回密码：账号 ID + 校园邮箱验证码
class ForgotPasswordPage extends StatefulWidget {
  final AuthService authService;

  const ForgotPasswordPage({super.key, required this.authService});

  @override
  State<ForgotPasswordPage> createState() => _ForgotPasswordPageState();
}

class _ForgotPasswordPageState extends State<ForgotPasswordPage> {
  final _accountController = TextEditingController();
  final _emailController = TextEditingController();
  final _codeController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _loading = false;
  bool _sending = false;
  bool _obscure = true;

  @override
  void dispose() {
    _accountController.dispose();
    _emailController.dispose();
    _codeController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _sendCode() async {
    final email = _emailController.text.trim();
    if (!RegExp(r'^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
        .hasMatch(email)) {
      _toast('请输入正确的邮箱地址');
      return;
    }
    setState(() => _sending = true);
    try {
      final code = await widget.authService.sendEmailCode(
        email: email,
        purpose: 'RESET',
      );
      if (!mounted) return;
      setState(() => _sending = false);
      if (code != null) _codeController.text = code;
      _toast(code == null ? '验证码已发送' : '验证码已发送（开发环境：$code）');
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _sending = false);
      _toast(e.message);
    } catch (_) {
      if (!mounted) return;
      setState(() => _sending = false);
      _toast('验证码发送失败');
    }
  }

  Future<void> _submit() async {
    if (_loading) return;
    final account = _accountController.text.trim();
    final email = _emailController.text.trim();
    final code = _codeController.text.trim();
    final password = _passwordController.text;
    if (account.isEmpty || email.isEmpty || code.isEmpty || password.isEmpty) {
      _toast('请完整填写信息');
      return;
    }
    setState(() => _loading = true);
    try {
      await widget.authService.resetPassword(
        accountId: account,
        email: email,
        newPassword: password,
        code: code,
      );
      if (!mounted) return;
      setState(() => _loading = false);
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
          title: const Text('密码已重置'),
          content: const Text('请返回登录页，使用新密码重新登录。'),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('去登录'),
            ),
          ],
        ),
      );
      if (mounted) Navigator.of(context).pop();
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _loading = false);
      _toast(e.message);
    } catch (_) {
      if (!mounted) return;
      setState(() => _loading = false);
      _toast('重置失败，请稍后重试');
    }
  }

  void _toast(String text) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
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
          '找回密码',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: Color(0xFF0F172A),
          ),
        ),
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Text(
            '通过校园邮箱重置密码',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 6),
          const Text(
            '需要账号已绑定邮箱',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
            ),
          ),
          const SizedBox(height: 20),
          _field(_accountController, '账号 ID', '请输入登录账号'),
          _field(
            _emailController,
            '校园邮箱',
            'xxx@stu.mycc.edu.cn',
            keyboardType: TextInputType.emailAddress,
          ),
          Row(
            children: [
              Expanded(
                child: _field(
                  _codeController,
                  '邮箱验证码',
                  '6 位验证码',
                  keyboardType: TextInputType.number,
                ),
              ),
              const SizedBox(width: 8),
              SizedBox(
                height: 48,
                child: OutlinedButton(
                  onPressed: _sending ? null : _sendCode,
                  child: _sending
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text(
                          '发送验证码',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 12,
                          ),
                        ),
                ),
              ),
            ],
          ),
          _field(
            _passwordController,
            '新密码',
            '8-20 位，须含字母和数字',
            obscure: _obscure,
          ),
          Row(
            children: [
              Checkbox(
                value: _obscure,
                onChanged: (v) => setState(() => _obscure = v ?? true),
              ),
              const Text(
                '隐藏密码',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: Color(0xFF64748B),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          SizedBox(
            height: 48,
            child: ElevatedButton(
              onPressed: _loading ? null : _submit,
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.brand,
                foregroundColor: Colors.white,
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(24),
                ),
              ),
              child: _loading
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Text(
                      '重置密码',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _field(
    TextEditingController controller,
    String label,
    String hint, {
    TextInputType? keyboardType,
    bool obscure = false,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextField(
        controller: controller,
        keyboardType: keyboardType,
        obscureText: obscure,
        style: const TextStyle(fontFamily: 'Noto Sans SC', fontSize: 13),
        decoration: InputDecoration(
          labelText: label,
          hintText: hint,
          filled: true,
          fillColor: Colors.white,
          labelStyle: const TextStyle(fontFamily: 'Noto Sans SC', fontSize: 13),
          hintStyle: const TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 12,
            color: Color(0xFF64748B),
          ),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
          ),
        ),
      ),
    );
  }
}
