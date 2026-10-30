import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../../api/jw_api.dart';

/// APP-教务系统绑定与实名认证 (对应移动端功能说明 3.9 节，375 × 812)
class JwAuthPage extends StatefulWidget {
  final bool isReauth;

  const JwAuthPage({super.key, this.isReauth = false});

  @override
  State<JwAuthPage> createState() => _JwAuthPageState();
}

class _JwAuthPageState extends State<JwAuthPage> {
  final TextEditingController _studentIdController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _captchaController = TextEditingController();

  bool _isObscure = true;
  bool _isAgreed = false;
  bool _isLoading = false;
  bool _isBound = false;
  String _captchaUid = '';
  String _captchaImage = '';
  Map<String, dynamic>? _jwStatus;

  @override
  void initState() {
    super.initState();
    _loadStatus();
    _refreshCaptcha();
  }

  @override
  void dispose() {
    _studentIdController.dispose();
    _passwordController.dispose();
    _captchaController.dispose();
    super.dispose();
  }

  Future<void> _refreshCaptcha() async {
    final captcha = await JwApi.fetchCaptcha();
    if (!mounted) return;
    if (captcha == null) {
      _showToast('验证码加载失败，请重试');
      return;
    }
    setState(() {
      _captchaUid = captcha.uid;
      _captchaImage = captcha.imageBase64;
      _captchaController.clear();
    });
  }

  Future<void> _loadStatus() async {
    final status = await JwApi.status();
    if (!mounted || status == null) return;
    setState(() {
      _jwStatus = status;
      _isBound = !widget.isReauth && status['status'] == 'AUTHENTICATED';
      if (status['ssoAccount'] != null) {
        _studentIdController.text = status['ssoAccount'].toString();
      }
    });
  }

  Future<void> _handleSubmit() async {
    final studentId = _studentIdController.text.trim();
    final pwd = _passwordController.text.trim();
    final captcha = _captchaController.text.trim();

    if (studentId.isEmpty) {
      _showToast('请输入教务系统学号');
      return;
    }
    if (pwd.isEmpty) {
      _showToast('请输入教务系统密码');
      return;
    }
    if (captcha.isEmpty) {
      _showToast('请输入验证码');
      return;
    }
    if (!_isAgreed) {
      _showToast('请阅读并勾选教务数据安全同步授权');
      return;
    }
    if (_captchaUid.isEmpty) {
      _showToast('验证码已失效，请刷新');
      await _refreshCaptcha();
      return;
    }

    setState(() => _isLoading = true);
    final result = await JwApi.auth(
      ssoAccount: studentId,
      password: pwd,
      captchaUid: _captchaUid,
      captchaCode: captcha,
    );
    if (!mounted) return;
    setState(() => _isLoading = false);
    if (result == null) {
      _showToast('教务认证失败，请核对账号、密码和验证码');
      await _refreshCaptcha();
      return;
    }
    setState(() => _isBound = true);
    await _loadStatus();
    _showToast('教务认证成功，课表与成绩已可实时查询');
  }

  Future<void> _handleUnbind() async {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text(
          '解除教务绑定',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
        ),
        content: const Text('解绑后，App 将无法自动刷新课表、查询考试日程和同步成绩。确定要解绑吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('取消', style: TextStyle(color: Color(0xFF64748B))),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.of(ctx).pop();
              _confirmUnbind();
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFFEF4444),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
              ),
            ),
            child: const Text('确认解绑', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  Future<void> _confirmUnbind() async {
    setState(() => _isLoading = true);
    final ok = await JwApi.unbind();
    if (!mounted) return;
    setState(() => _isLoading = false);
    if (!ok) {
      _showToast('解绑失败，请稍后重试');
      await _loadStatus();
      return;
    }
    setState(() {
      _isBound = false;
      _jwStatus = null;
      _passwordController.clear();
      _captchaController.clear();
    });
    await _refreshCaptcha();
    _showToast('已解除教务系统绑定');
  }

  void _showToast(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(msg),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
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
          '教务系统实名认证',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: Color(0xFF0F172A),
          ),
        ),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: _isBound ? _buildBoundCard() : _buildLoginForm(),
      ),
    );
  }

  Widget _buildBoundCard() {
    return Column(
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: const Color(0xFFE2E8F0)),
            boxShadow: const [
              BoxShadow(
                color: Color(0x0A0F172A),
                blurRadius: 10,
                offset: Offset(0, 4),
              ),
            ],
          ),
          child: Column(
            children: [
              Container(
                width: 56,
                height: 56,
                decoration: const BoxDecoration(
                  color: Color(0xFFDCFCE7),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.verified_rounded,
                  size: 32,
                  color: Color(0xFF16A34A),
                ),
              ),
              const SizedBox(height: 12),
              const Text(
                '教务系统已绑定',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF0F172A),
                ),
              ),
              const SizedBox(height: 4),
              const Text(
                '课表自动同步与学籍考务认证正常生效中',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: Color(0xFF64748B),
                ),
              ),
              const SizedBox(height: 20),
              const Divider(height: 1),
              const SizedBox(height: 16),
              _buildInfoRow('学生姓名', _jwStatus?['jwName']?.toString() ?? '—'),
              const SizedBox(height: 10),
              _buildInfoRow(
                '学号 (UID)',
                _jwStatus?['ssoAccount']?.toString() ?? '—',
              ),
              const SizedBox(height: 10),
              _buildInfoRow('所属学院', _jwStatus?['college']?.toString() ?? '—'),
              const SizedBox(height: 10),
              _buildInfoRow('修读专业', _jwStatus?['major']?.toString() ?? '—'),
              const SizedBox(height: 10),
              _buildInfoRow('同步状态', '教务会话已认证，课表/成绩可实时查询'),
            ],
          ),
        ),
        const SizedBox(height: 24),
        SizedBox(
          width: double.infinity,
          height: 46,
          child: OutlinedButton(
            onPressed: _handleUnbind,
            style: OutlinedButton.styleFrom(
              side: const BorderSide(color: Color(0xFFEF4444)),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(23),
              ),
            ),
            child: const Text(
              '解除教务绑定',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 14,
                color: Color(0xFFEF4444),
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: const TextStyle(fontSize: 13, color: Color(0xFF64748B)),
        ),
        Text(
          value,
          style: const TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: Color(0xFF0F172A),
          ),
        ),
      ],
    );
  }

  Widget _buildLoginForm() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // 顶部说明卡片
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: const Color(0xFFEFF6FF),
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: const Color(0xFFBFDBFE)),
          ),
          child: const Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                Icons.info_outline_rounded,
                color: Color(0xFF2563EB),
                size: 18,
              ),
              SizedBox(width: 8),
              Expanded(
                child: Text(
                  '认证教务系统账号将为您自动同步个人课表、考务日程、学分绩点与在读档案。账号信息经端到端加密流转，绝不留存外部。',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF1E40AF),
                    height: 1.45,
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),

        // 表单区
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: const Color(0xFFE2E8F0)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '教务系统学号',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF334155),
                ),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _studentIdController,
                keyboardType: TextInputType.number,
                style: const TextStyle(fontSize: 14),
                decoration: InputDecoration(
                  hintText: '请输入本科/专科学号',
                  prefixIcon: const Icon(
                    Icons.badge_outlined,
                    size: 20,
                    color: Color(0xFF94A3B8),
                  ),
                  filled: true,
                  fillColor: const Color(0xFFF8FAFC),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide.none,
                  ),
                ),
              ),
              const SizedBox(height: 16),

              const Text(
                '教务系统密码',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF334155),
                ),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _passwordController,
                obscureText: _isObscure,
                style: const TextStyle(fontSize: 14),
                decoration: InputDecoration(
                  hintText: '请输入学校统一身份认证密码',
                  prefixIcon: const Icon(
                    Icons.lock_outline_rounded,
                    size: 20,
                    color: Color(0xFF94A3B8),
                  ),
                  suffixIcon: IconButton(
                    icon: Icon(
                      _isObscure
                          ? Icons.visibility_off_outlined
                          : Icons.visibility_outlined,
                      size: 20,
                      color: const Color(0xFF94A3B8),
                    ),
                    onPressed: () => setState(() => _isObscure = !_isObscure),
                  ),
                  filled: true,
                  fillColor: const Color(0xFFF8FAFC),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide.none,
                  ),
                ),
              ),
              const SizedBox(height: 16),

              const Text(
                '图形验证码',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF334155),
                ),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _captchaController,
                      style: const TextStyle(fontSize: 14),
                      decoration: InputDecoration(
                        hintText: '输入右图验证码',
                        prefixIcon: const Icon(
                          Icons.shield_outlined,
                          size: 20,
                          color: Color(0xFF94A3B8),
                        ),
                        filled: true,
                        fillColor: const Color(0xFFF8FAFC),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide.none,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  GestureDetector(
                    onTap: _refreshCaptcha,
                    child: Container(
                      width: 110,
                      height: 48,
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: const Color(0xFFCBD5E1)),
                      ),
                      alignment: Alignment.center,
                      child: _captchaImage.isEmpty
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : Image.memory(
                              Uint8List.fromList(base64Decode(_captchaImage)),
                              fit: BoxFit.contain,
                            ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // 协议授权勾选
        Row(
          children: [
            Checkbox(
              value: _isAgreed,
              activeColor: const Color(0xFF2563EB),
              onChanged: (val) => setState(() => _isAgreed = val ?? false),
            ),
            const Expanded(
              child: Text(
                '我已阅读并同意《教务数据安全同步授权》及学生信息合规保护条例',
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

        // 提交按钮
        SizedBox(
          height: 48,
          child: ElevatedButton(
            onPressed: _isLoading ? null : _handleSubmit,
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF2563EB),
              foregroundColor: Colors.white,
              elevation: 0,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(24),
              ),
            ),
            child: _isLoading
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Text(
                    '立即绑定并同步课表',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 15,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
          ),
        ),
      ],
    );
  }
}
