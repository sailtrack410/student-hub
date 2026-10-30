import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/auth_service.dart';
import '../../api/profile_api.dart';
import '../../theme/app_theme.dart';

/// 绑定邮箱（用于找回密码）
class EmailBindingPage extends StatefulWidget {
  final AuthService authService;
  final String? currentEmail;

  const EmailBindingPage({
    super.key,
    required this.authService,
    this.currentEmail,
  });

  @override
  State<EmailBindingPage> createState() => _EmailBindingPageState();
}

class _EmailBindingPageState extends State<EmailBindingPage> {
  final _emailController = TextEditingController();
  final _codeController = TextEditingController();
  bool _sending = false;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    if (widget.currentEmail != null)
      _emailController.text = widget.currentEmail!;
  }

  @override
  void dispose() {
    _emailController.dispose();
    _codeController.dispose();
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
        purpose: 'BIND',
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

  Future<void> _save() async {
    final email = _emailController.text.trim();
    final code = _codeController.text.trim();
    if (email.isEmpty || code.isEmpty) {
      _toast('请填写邮箱和验证码');
      return;
    }
    setState(() => _saving = true);
    try {
      final result = await ProfileApi.bindEmail(email: email, code: code);
      if (!mounted) return;
      setState(() => _saving = false);
      if (result == null) {
        _toast('绑定失败，请稍后重试');
        return;
      }
      _toast('邮箱绑定成功');
      Navigator.of(context).pop(true);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      _toast(e.message);
    } catch (_) {
      if (!mounted) return;
      setState(() => _saving = false);
      _toast('绑定失败，请稍后重试');
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
          '邮箱',
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
            '绑定邮箱',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 6),
          const Text(
            '绑定后可用于找回密码',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              height: 1.5,
              color: Color(0xFF64748B),
            ),
          ),
          const SizedBox(height: 20),
          TextField(
            controller: _emailController,
            keyboardType: TextInputType.emailAddress,
            decoration: InputDecoration(
              labelText: '邮箱',
              hintText: 'name@example.com',
              filled: true,
              fillColor: Colors.white,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
              ),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _codeController,
                  keyboardType: TextInputType.number,
                  decoration: InputDecoration(
                    labelText: '邮箱验证码',
                    filled: true,
                    fillColor: Colors.white,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                    ),
                  ),
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
          const SizedBox(height: 20),
          SizedBox(
            height: 48,
            child: ElevatedButton(
              onPressed: _saving ? null : _save,
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.brand,
                foregroundColor: Colors.white,
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(24),
                ),
              ),
              child: _saving
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Text(
                      '保存',
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
}
