import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/auth_service.dart';
import '../../models/user_session.dart';
import '../../services/consent_store.dart';
import '../../services/qq_auth_service.dart';
import '../../theme/app_theme.dart';
import '../../widgets/toast.dart';
import '../home/home_page.dart';
import '../legal/legal_document_content.dart';
import '../legal/legal_document_page.dart';
import 'forgot_password_page.dart';
import 'register_page.dart';

/// 移动端 APP-登录页
/// 安全区与滚动约束保证小屏、键盘和系统大字号下所有操作可达。
class LoginPage extends StatefulWidget {
  final AuthService authService;

  const LoginPage({super.key, required this.authService});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  bool _agreed = false; // 协议勾选状态：默认不勾选，登录前必须主动同意
  bool _isLoading = false;
  String? _errorMessage;

  final QqAuthService _qqAuthService = QqAuthService();

  // 开发/测试兜底表单控制器（仅 debug 构建的开发者入口使用）
  final TextEditingController _usernameController = TextEditingController(
    text: 'student01',
  );
  final TextEditingController _passwordController = TextEditingController(
    text: '123456',
  );

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    unawaited(_qqAuthService.dispose());
    super.dispose();
  }

  void _showError(String message) {
    setState(() {
      _errorMessage = message;
    });
  }

  void _clearError() {
    if (_errorMessage != null) {
      setState(() {
        _errorMessage = null;
      });
    }
  }

  /// 处理 QQ 一键登录
  Future<void> _handleQqLogin() async {
    if (!_agreed) {
      _showError('请先阅读并同意《用户协议》与《隐私政策》');
      return;
    }

    _clearError();
    setState(() => _isLoading = true);

    try {
      // tencent_qq：用户同意隐私政策后初始化 SDK，再拿 QQ accessToken。
      final qqResult = await _qqAuthService.login(privacyAccepted: _agreed);
      final session = await widget.authService.qqLogin(
        accessToken: qqResult.accessToken,
        openId: qqResult.openId,
      );

      if (!mounted) return;
      _onLoginSuccess(session);
    } on QqAuthException catch (e) {
      if (mounted && !e.cancelled) _showError(e.message);
    } on ApiException catch (e) {
      if (mounted) _showError(e.message);
    } catch (_) {
      if (mounted) _showError('QQ 授权失败，请检查网络后重试');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  /// 兜底学号密码登录
  Future<void> _handlePasswordLogin() async {
    final username = _usernameController.text.trim();
    final password = _passwordController.text.trim();

    if (username.isEmpty || password.isEmpty) {
      _showError('请输入账号 ID 和密码');
      return;
    }

    if (!_agreed) {
      _showError('请先阅读并同意《用户协议》与《隐私政策》');
      return;
    }

    _clearError();
    setState(() => _isLoading = true);

    try {
      final session = await widget.authService.passwordLogin(
        username: username,
        password: password,
      );

      if (!mounted) return;
      Navigator.of(context).pop(); // 关闭弹层
      _onLoginSuccess(session);
    } on ApiException catch (e) {
      _showError(e.message);
    } catch (_) {
      _showError('登录失败，请检查账号 ID 与密码');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _openRegister() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => RegisterPage(authService: widget.authService),
      ),
    );
  }

  void _openForgotPassword() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ForgotPasswordPage(authService: widget.authService),
      ),
    );
  }

  void _openLegalDocument(LegalDocumentType type) {
    LegalDocumentPage.open(context, type);
  }

  /// 协议链接：单独响应点击，只打开文档，不切换整行勾选状态。
  Widget _buildAgreementLink(String label, LegalDocumentType type) {
    return TextButton(
      onPressed: () => _openLegalDocument(type),
      style: TextButton.styleFrom(
        padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 12),
        minimumSize: const Size(48, 48),
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
      ),
      child: Text(label, maxLines: 1),
    );
  }

  void _onLoginSuccess(UserSession session) {
    // 登录成功后记录本次同意的协议版本与时间（本地合规留痕，失败不影响登录）。
    unawaited(ConsentStore.recordConsent());
    Navigator.of(context).pushReplacement(
      PageRouteBuilder(
        pageBuilder: (_, _, _) =>
            HomePage(session: session, authService: widget.authService),
        transitionsBuilder: (context, animation, _, child) =>
            MediaQuery.disableAnimationsOf(context)
            ? child
            : FadeTransition(opacity: animation, child: child),
      ),
    );
  }

  /// 开发者/测试模式弹层（保留兜底登录，不破坏画布极简设计）。
  ///
  /// 仅 debug 构建可用：release 构建不展示入口，直接调用也会被拦截。
  void _openDevLoginDialog() {
    if (!kDebugMode) return;
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => SingleChildScrollView(
        child: Container(
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 20,
            bottom: MediaQuery.of(ctx).viewInsets.bottom + 24,
          ),
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    '账号 ID / 学号登录 (Dev)',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: AppColors.textPrimary,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, size: 20),
                    onPressed: () => Navigator.of(ctx).pop(),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _usernameController,
                decoration: InputDecoration(
                  hintText: '账号 ID / 学号 (如 debug_student)',
                  prefixIcon: const Icon(Icons.person_outline, size: 20),
                  filled: true,
                  fillColor: AppColors.background,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide.none,
                  ),
                ),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: _passwordController,
                obscureText: true,
                decoration: InputDecoration(
                  hintText: '登录密码',
                  prefixIcon: const Icon(Icons.lock_outline, size: 20),
                  filled: true,
                  fillColor: AppColors.background,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide.none,
                  ),
                ),
              ),
              const SizedBox(height: 18),
              SizedBox(
                height: 48,
                child: ElevatedButton(
                  onPressed: _isLoading ? null : _handlePasswordLogin,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.brand,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(24),
                    ),
                    elevation: 0,
                  ),
                  child: const Text(
                    '立即登录',
                    style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) => SingleChildScrollView(
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            child: Center(
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  maxWidth: 480,
                  minHeight: constraints.maxHeight,
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 24,
                  ),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Column(
                        children: [
                          const SizedBox(height: 24),
                          GestureDetector(
                            onLongPress: kDebugMode
                                ? _openDevLoginDialog
                                : null,
                            child: Semantics(
                              image: true,
                              label: '绵阳城市学院校徽',
                              child: Container(
                                width: 112,
                                height: 112,
                                padding: const EdgeInsets.all(6),
                                decoration: const BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: Colors.white,
                                  boxShadow: [
                                    BoxShadow(
                                      color: Color(0x202563EB),
                                      blurRadius: 24,
                                      offset: Offset(0, 8),
                                    ),
                                  ],
                                ),
                                child: Image.asset(
                                  'assets/images/school_logo.png',
                                  excludeFromSemantics: true,
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(height: 24),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 12,
                              vertical: 8,
                            ),
                            decoration: BoxDecoration(
                              color: AppColors.brandSoft,
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: const Text(
                              '绵阳城市学院 · 智慧校园',
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColors.brand,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                          const SizedBox(height: 16),
                          GestureDetector(
                            onDoubleTap: kDebugMode
                                ? _openDevLoginDialog
                                : null,
                            child: Text(
                              '绵城校园',
                              textAlign: TextAlign.center,
                              style: Theme.of(context).textTheme.headlineMedium
                                  ?.copyWith(fontWeight: FontWeight.bold),
                            ),
                          ),
                          const SizedBox(height: 8),
                          const Text(
                            'MIANYANG CITY COLLEGE',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              fontSize: 12,
                              color: AppColors.textBody,
                              letterSpacing: 1.2,
                            ),
                          ),
                          const SizedBox(height: 12),
                          const Text(
                            '连接师生生活 · 共享多元校园空间',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              fontSize: 14,
                              color: AppColors.textBody,
                            ),
                          ),
                        ],
                      ),
                      Padding(
                        padding: const EdgeInsets.only(top: 48),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            if (_errorMessage != null) ...[
                              Semantics(
                                liveRegion: true,
                                child: ErrorToastBanner(
                                  message: _errorMessage!,
                                ),
                              ),
                              const SizedBox(height: 16),
                            ],
                            FilledButton.icon(
                              onPressed: _isLoading ? null : _handleQqLogin,
                              icon: _isLoading
                                  ? const SizedBox(
                                      width: 22,
                                      height: 22,
                                      child: CircularProgressIndicator(
                                        color: Colors.white,
                                        strokeWidth: 2.2,
                                      ),
                                    )
                                  : Image.asset(
                                      'assets/images/qq_icon.png',
                                      width: 22,
                                      height: 22,
                                      excludeFromSemantics: true,
                                    ),
                              label: Text(_isLoading ? '正在登录' : 'QQ 一键登录'),
                              style: FilledButton.styleFrom(
                                minimumSize: const Size(48, 52),
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 16,
                                  vertical: 16,
                                ),
                              ),
                            ),
                            const SizedBox(height: 12),
                            const Text(
                              '面向全校师生开放 · 支持账号 ID / 邮箱注册',
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColors.textBody,
                              ),
                            ),
                            Wrap(
                              alignment: WrapAlignment.center,
                              children: [
                                TextButton(
                                  onPressed: _isLoading ? null : _openRegister,
                                  child: const Text('注册账号'),
                                ),
                                TextButton(
                                  onPressed: _isLoading
                                      ? null
                                      : _openForgotPassword,
                                  child: const Text('找回密码'),
                                ),
                              ],
                            ),
                            const SizedBox(height: 20),
                            FittedBox(
                              fit: BoxFit.scaleDown,
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                crossAxisAlignment: CrossAxisAlignment.center,
                                children: [
                                  Checkbox(
                                    value: _agreed,
                                    onChanged: _isLoading
                                        ? null
                                        : (value) => setState(
                                            () => _agreed = value ?? false,
                                          ),
                                    semanticLabel: '同意用户协议和隐私政策',
                                  ),
                                  GestureDetector(
                                    onTap: _isLoading
                                        ? null
                                        : () => setState(
                                            () => _agreed = !_agreed,
                                          ),
                                    child: const SizedBox(
                                      height: 48,
                                      child: Center(
                                        child: Text(
                                          '我已阅读并同意',
                                          style: TextStyle(
                                            fontSize: 12,
                                            color: AppColors.textBody,
                                          ),
                                        ),
                                      ),
                                    ),
                                  ),
                                  _buildAgreementLink(
                                    '《用户协议》',
                                    LegalDocumentType.userAgreement,
                                  ),
                                  const Text(
                                    '和',
                                    style: TextStyle(fontSize: 12),
                                  ),
                                  _buildAgreementLink(
                                    '《隐私政策》',
                                    LegalDocumentType.privacyPolicy,
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(height: 16),
                            const Text(
                              '绵阳城市学院校园服务平台 · 安全技术保障',
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColors.textBody,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
