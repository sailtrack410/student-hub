import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import '../../api/api_client.dart';
import '../../api/auth_service.dart';
import '../../models/user_session.dart';
import '../../theme/app_theme.dart';
import '../../widgets/toast.dart';
import '../home/home_page.dart';

/// 移动端 APP-登录页
/// 严格依据 Ardot 画布节点 APP-登录 (4:3) 构造，精确还原 375 × 812 布局
class LoginPage extends StatefulWidget {
  final AuthService authService;

  const LoginPage({super.key, required this.authService});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  bool _agreed = true; // 协议勾选状态，对齐画布默认勾选
  bool _isLoading = false;
  String? _errorMessage;

  // 开发/测试兜底表单控制器
  final TextEditingController _usernameController =
      TextEditingController(text: 'student01');
  final TextEditingController _passwordController =
      TextEditingController(text: '123456');

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
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
      final session = await widget.authService.qqLogin(
        accessToken:
            'mock_qq_access_token_${DateTime.now().millisecondsSinceEpoch}',
        openId: 'mock_qq_openid_9001',
      );

      if (!mounted) return;
      _onLoginSuccess(session);
    } on ApiException catch (e) {
      _showError(e.message);
    } catch (_) {
      _showError('QQ 授权失败，请检查网络后重试');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  /// 兜底学号密码登录
  Future<void> _handlePasswordLogin() async {
    final username = _usernameController.text.trim();
    final password = _passwordController.text.trim();

    if (username.isEmpty || password.isEmpty) {
      _showError('请输入学号和密码');
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
      _showError('登录失败，请检查学号与密码');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _onLoginSuccess(UserSession session) {
    Navigator.of(context).pushReplacement(
      PageRouteBuilder(
        pageBuilder: (_, __, ___) => HomePage(
          session: session,
          authService: widget.authService,
        ),
        transitionsBuilder: (_, animation, __, child) =>
            FadeTransition(opacity: animation, child: child),
      ),
    );
  }

  /// 开发者/测试模式弹层（保留兜底登录，不破坏画布极简设计）
  void _openDevLoginDialog() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
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
                  '学号 / 工号兜底登录 (Dev)',
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
                hintText: '学号/工号 (如 student01)',
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
                hintText: '密码 (如 123456)',
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
                child: const Text('立即登录', style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;
    final topPadding = MediaQuery.of(context).padding.top;
    final isDesktop = size.width > 600;

    return Scaffold(
      backgroundColor: isDesktop ? const Color(0xFFF1F5F9) : AppColors.background,
      body: Center(
        child: Container(
          width: isDesktop ? 375 : double.infinity,
          height: isDesktop ? 812 : double.infinity,
          clipBehavior: isDesktop ? Clip.antiAlias : Clip.none,
          decoration: BoxDecoration(
            color: AppColors.background,
            borderRadius: isDesktop ? BorderRadius.circular(24) : BorderRadius.zero,
            border: isDesktop ? Border.all(color: const Color(0xFFE2E8F0), width: 1) : null,
          ),
          child: Stack(
            children: [
              // 1. 顶部氛围光晕背景（对齐画布 4:39 节点）
              Positioned(
                top: 0,
                left: 0,
                right: 0,
                height: 420,
                child: Stack(
                  clipBehavior: Clip.none,
                  children: [
                    // 线性渐变基底
                    Container(
                      decoration: const BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                          colors: [
                            Color(0x382563EB), // 宝蓝 22%
                            Color(0x1F14B8A6), // 青绿 12%
                            Color(0x00F7FAF9), // 渐隐透明
                          ],
                          stops: [0.0, 0.55, 1.0],
                        ),
                      ),
                    ),
                    // 宝蓝主光晕（左上方：x: -50, y: -50, 260x260）
                    Positioned(
                      left: -50,
                      top: -50,
                      width: 260,
                      height: 260,
                      child: Container(
                        decoration: const BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: RadialGradient(
                            colors: [
                              Color(0x522563EB), // 32%
                              Color(0x002563EB),
                            ],
                          ),
                        ),
                      ),
                    ),
                    // 青绿副光晕（右上方：x: 170, y: 0, 230x230）
                    Positioned(
                      left: 170,
                      top: 0,
                      width: 230,
                      height: 230,
                      child: Container(
                        decoration: const BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: RadialGradient(
                            colors: [
                              Color(0x4714B8A6), // 28%
                              Color(0x0014B8A6),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),


              // 3. 校徽悬浮台座（对齐画布 4:40 节点：x: 131, y: 92, 112x112）
              Positioned(
                top: 92,
                left: 131,
                width: 112,
                height: 112,
                child: GestureDetector(
                  onLongPress: _openDevLoginDialog,
                  child: Container(
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: Colors.white,
                      boxShadow: const [
                        BoxShadow(
                          color: Color(0x382563EB), // 22% 弥散投影
                          offset: Offset(0, 10),
                          blurRadius: 24,
                        ),
                      ],
                    ),
                    padding: const EdgeInsets.all(6),
                    child: ClipOval(
                      child: Image.asset(
                        'assets/images/school_logo.png',
                        width: 100,
                        height: 100,
                        fit: BoxFit.contain,
                      ),
                    ),
                  ),
                ),
              ),

              // 4. 校名徽章（对齐画布 4:8 节点：x: 97, y: 226, 180x26）
              Positioned(
                top: 226,
                left: 97,
                width: 180,
                height: 26,
                child: Container(
                  decoration: BoxDecoration(
                    color: const Color(0xFFE8F0FC),
                    borderRadius: BorderRadius.circular(13),
                  ),
                  alignment: Alignment.center,
                  child: const Text(
                    '绵阳城市学院 · 智慧校园',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: AppColors.brand,
                      height: 1.0,
                    ),
                  ),
                ),
              ),

              // 5. 大标题（对齐画布 4:10 节点：x: 24, y: 266, 327x41）
              Positioned(
                top: 266,
                left: 24,
                width: 327,
                child: GestureDetector(
                  onDoubleTap: _openDevLoginDialog,
                  child: const Text(
                    '绵城校园',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF0F172A),
                      letterSpacing: -0.5,
                      height: 1.2,
                    ),
                  ),
                ),
              ),

              // 6. 英文校名（对齐画布 4:42 节点：x: 24, y: 310, 327x12）
              const Positioned(
                top: 310,
                left: 24,
                width: 327,
                child: Text(
                  'MIANYANG CITY COLLEGE',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontFamily: 'Inter',
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF94A3B8),
                    letterSpacing: 1.2,
                    height: 1.0,
                  ),
                ),
              ),

              // 7. 副标语（对齐画布 4:11 节点：x: 24, y: 334, 327x19）
              const Positioned(
                top: 334,
                left: 24,
                width: 327,
                child: Text(
                  '连接师生生活 · 共享多元校园空间',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 13,
                    fontWeight: FontWeight.w400,
                    color: Color(0xFF64748B),
                    height: 1.2,
                  ),
                ),
              ),

              // 8. 异常状态 Toast（对齐画布 4:33 节点：x: 24, y: 436, 327x38）
              if (_errorMessage != null)
                Positioned(
                  top: 436,
                  left: 24,
                  width: 327,
                  child: ErrorToastBanner(message: _errorMessage!)
                      .animate()
                      .fadeIn(duration: 200.ms)
                      .slideY(begin: -0.15, end: 0),
                ),

              // 9. QQ 一键登录按钮（对齐画布 4:22 节点：x: 24, y: 496, 327x52）
              Positioned(
                top: 496,
                left: 24,
                width: 327,
                height: 52,
                child: Container(
                  decoration: BoxDecoration(
                    color: AppColors.brand,
                    borderRadius: BorderRadius.circular(26),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x472563EB), // 28% alpha
                        offset: Offset(0, 8),
                        blurRadius: 20,
                      ),
                    ],
                  ),
                  child: Material(
                    color: Colors.transparent,
                    child: InkWell(
                      borderRadius: BorderRadius.circular(26),
                      onTap: _isLoading ? null : _handleQqLogin,
                      child: Center(
                        child: _isLoading
                            ? const SizedBox(
                                width: 22,
                                height: 22,
                                child: CircularProgressIndicator(
                                  color: Colors.white,
                                  strokeWidth: 2.2,
                                ),
                              )
                            : Row(
                                mainAxisSize: MainAxisSize.min,
                                mainAxisAlignment: MainAxisAlignment.center,
                                crossAxisAlignment: CrossAxisAlignment.center,
                                children: [
                                  Image.asset(
                                    'assets/images/qq_icon.png',
                                    width: 22,
                                    height: 22,
                                    fit: BoxFit.contain,
                                  ),
                                  const SizedBox(width: 8),
                                  const Text(
                                    'QQ 一键登录',
                                    style: TextStyle(
                                      fontFamily: 'Noto Sans SC',
                                      fontSize: 16,
                                      fontWeight: FontWeight.bold,
                                      color: Colors.white,
                                      letterSpacing: 0.5,
                                      height: 1.0,
                                    ),
                                  ),
                                ],
                              ),
                      ),
                    ),
                  ),
                ),
              ),

              // 10. 友好说明（对齐画布 4:25 节点：x: 24, y: 560, 327x17）
              const Positioned(
                top: 560,
                left: 24,
                width: 327,
                child: Text(
                  '面向全校师生开放 · 授权即自动建号，免注册',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    fontWeight: FontWeight.w400,
                    color: Color(0xFF94A3B8),
                    height: 1.0,
                  ),
                ),
              ),

              // 11. 协议行（对齐画布 4:26 节点：x: 24, y: 686, 327x24）
              Positioned(
                top: 686,
                left: 24,
                width: 327,
                height: 24,
                child: GestureDetector(
                  onTap: () => setState(() => _agreed = !_agreed),
                  behavior: HitTestBehavior.opaque,
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      // 勾选框（4:27 节点：16x16，圆角 8，#2563EB）
                      AnimatedContainer(
                        duration: const Duration(milliseconds: 150),
                        width: 16,
                        height: 16,
                        decoration: BoxDecoration(
                          color: _agreed ? AppColors.brand : Colors.white,
                          borderRadius: BorderRadius.circular(5),
                          border: Border.all(
                            color: _agreed ? AppColors.brand : const Color(0xFFCBD5E1),
                            width: 1.2,
                          ),
                        ),
                        child: _agreed
                            ? const Icon(
                                Icons.check,
                                size: 12,
                                color: Colors.white,
                              )
                            : null,
                      ),
                      const SizedBox(width: 6),
                      // 前缀（4:30 节点）
                      const Text(
                        '登录即代表已阅读并同意',
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 12,
                          color: Color(0xFF64748B),
                          fontWeight: FontWeight.w400,
                        ),
                      ),
                      // 协议链接（4:31 节点）
                      const Text(
                        '《用户协议》与《隐私政策》',
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 12,
                          color: AppColors.brand,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),
              ),

              // 12. 安全背书（对齐画布 4:32 节点：x: 24, y: 742, 327x16）
              const Positioned(
                top: 742,
                left: 24,
                width: 327,
                child: Text(
                  '绵阳城市学院校园服务平台 · 安全技术保障',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 11,
                    fontWeight: FontWeight.w400,
                    color: Color(0xFF94A3B8),
                    height: 1.0,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
