import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/auth_service.dart';
import '../../models/user_session.dart';
import '../../theme/app_theme.dart';
import '../home/home_page.dart';

/// 注册账号：登录 ID + 密码 + 邮箱验证码
class RegisterPage extends StatefulWidget {
  final AuthService authService;

  const RegisterPage({super.key, required this.authService});

  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

enum _SubmitPhase { idle, loading, success, failure }

class _RegisterPageState extends State<RegisterPage>
    with TickerProviderStateMixin {
  final _accountController = TextEditingController();
  final _passwordController = TextEditingController();
  final _emailController = TextEditingController();
  final _codeController = TextEditingController();

  bool _sendingCode = false;
  _SubmitPhase _phase = _SubmitPhase.idle;

  late final AnimationController _confettiController = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1800),
  );
  late final AnimationController _shakeController = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 500),
  );

  @override
  void dispose() {
    _accountController.dispose();
    _passwordController.dispose();
    _emailController.dispose();
    _codeController.dispose();
    _confettiController.dispose();
    _shakeController.dispose();
    super.dispose();
  }

  Future<void> _sendCode() async {
    final email = _emailController.text.trim();
    if (!RegExp(r'^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
        .hasMatch(email)) {
      _toast('请输入正确的邮箱地址');
      return;
    }
    setState(() => _sendingCode = true);
    try {
      final code = await widget.authService.sendEmailCode(
        email: email,
        purpose: 'REGISTER',
      );
      if (!mounted) return;
      setState(() => _sendingCode = false);
      if (code != null) _codeController.text = code;
      _toast(code == null ? '验证码已发送，请查收邮箱' : '验证码已发送（开发环境：$code）');
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _sendingCode = false);
      _toast(e.message);
    } catch (_) {
      if (!mounted) return;
      setState(() => _sendingCode = false);
      _toast('验证码发送失败，请稍后重试');
    }
  }

  Future<void> _submit() async {
    if (_phase != _SubmitPhase.idle) return;
    final account = _accountController.text.trim();
    final password = _passwordController.text;
    final email = _emailController.text.trim();
    final code = _codeController.text.trim();
    if (account.isEmpty || password.isEmpty || email.isEmpty || code.isEmpty) {
      _toast('请填写登录 ID、密码、邮箱和验证码');
      return;
    }
    if (!RegExp(r'^[A-Za-z0-9_]{4,20}$').hasMatch(account)) {
      _toast('登录 ID 需为 4-20 位字母、数字或下划线');
      return;
    }
    if (!RegExp(r'^(?=.*[A-Za-z])(?=.*\d)[A-Za-z0-9!@#$%^&*._-]{8,20}$')
        .hasMatch(password)) {
      _toast('密码需为 8-20 位且同时包含字母和数字');
      return;
    }
    setState(() => _phase = _SubmitPhase.loading);
    try {
      final session = await widget.authService.register(
        accountId: account,
        email: email,
        password: password,
        code: code,
      );
      if (!mounted) return;
      UserSession.currentUserId = session.userId;
      UserSession.currentUserName = session.displayName;
      setState(() => _phase = _SubmitPhase.success);
      if (!MediaQuery.disableAnimationsOf(context)) {
        _confettiController.forward(from: 0);
        await Future<void>.delayed(const Duration(milliseconds: 1800));
      }
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(
          builder: (_) =>
              HomePage(session: session, authService: widget.authService),
        ),
        (route) => false,
      );
    } on ApiException catch (e) {
      if (!mounted) return;
      _fail(e.message);
    } catch (_) {
      if (!mounted) return;
      _fail('注册失败，请稍后重试');
    }
  }

  Future<void> _fail(String message) async {
    setState(() => _phase = _SubmitPhase.failure);
    if (!MediaQuery.disableAnimationsOf(context)) _shakeController.forward(from: 0);
    _toast(message);
    await Future<void>.delayed(const Duration(milliseconds: 1200));
    if (!mounted) return;
    setState(() => _phase = _SubmitPhase.idle);
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
          '注册账号',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: Color(0xFF0F172A),
          ),
        ),
        centerTitle: true,
      ),
      body: Stack(
        children: [
          ListView(
            padding: const EdgeInsets.all(20),
            children: [
              const Text(
                '创建登录账号',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 22,
                  fontWeight: FontWeight.bold,
                  color: AppColors.textPrimary,
                ),
              ),
              const SizedBox(height: 6),
              const Text(
                '填写登录 ID、密码，再验证邮箱即可',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: Color(0xFF64748B),
                ),
              ),
              const SizedBox(height: 22),
              _field(_accountController, '登录 ID', '4-20 位字母、数字或下划线'),
              _field(
                _passwordController,
                '登录密码',
                '8-20 位，须含字母和数字',
                obscure: true,
              ),
              _field(
                _emailController,
                '邮箱',
                'name@example.com',
                keyboardType: TextInputType.emailAddress,
              ),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: _field(
                      _codeController,
                      '邮箱验证码',
                      '6 位验证码',
                      keyboardType: TextInputType.number,
                      bottomPadding: 0,
                    ),
                  ),
                  const SizedBox(width: 8),
                  SizedBox(
                    height: 48,
                    child: OutlinedButton(
                      onPressed: _sendingCode ? null : _sendCode,
                      style: OutlinedButton.styleFrom(
                        foregroundColor: AppColors.brand,
                        side: const BorderSide(color: Color(0xFFBFDBFE)),
                        padding: const EdgeInsets.symmetric(horizontal: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: _sendingCode
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
              const SizedBox(height: 18),
              LayoutBuilder(
                builder: (context, constraints) =>
                    _buildSubmitButton(constraints.maxWidth),
              ),
              const SizedBox(height: 12),
              Center(
                child: TextButton(
                  onPressed: _phase == _SubmitPhase.idle
                      ? () => Navigator.of(context).pop()
                      : null,
                  child: const Text(
                    '已有账号？返回登录',
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
          if (_phase == _SubmitPhase.success)
            Positioned.fill(
              child: IgnorePointer(
                child: _ConfettiBurst(animation: _confettiController),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildSubmitButton(double maxWidth) {
    final idle = _phase == _SubmitPhase.idle;
    return AnimatedBuilder(
      animation: _shakeController,
      builder: (context, _) {
        final shakeValue = _phase == _SubmitPhase.failure
            ? math.sin(_shakeController.value * math.pi * 8) *
                  8 *
                  (1 - _shakeController.value)
            : 0.0;
        return Transform.translate(
          offset: Offset(shakeValue, 0),
          child: Center(
            child: AnimatedContainer(
              duration: MediaQuery.disableAnimationsOf(context) ? Duration.zero : const Duration(milliseconds: 420),
              curve: Curves.easeInOutCubic,
              width: idle ? maxWidth : 56,
              height: idle ? 48 : 56,
              decoration: BoxDecoration(
                color: _phase == _SubmitPhase.failure
                    ? const Color(0xFFEF4444)
                    : AppColors.brand,
                borderRadius: BorderRadius.circular(idle ? 24 : 28),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x1F2563EB),
                    blurRadius: 12,
                    offset: Offset(0, 4),
                  ),
                ],
              ),
              clipBehavior: Clip.antiAlias,
              child: Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: idle ? _submit : null,
                  child: Center(
                    child: AnimatedSwitcher(
                      duration: MediaQuery.disableAnimationsOf(context) ? Duration.zero : const Duration(milliseconds: 220),
                      transitionBuilder: (child, animation) =>
                          ScaleTransition(scale: animation, child: child),
                      child: switch (_phase) {
                        _SubmitPhase.idle => const Text(
                          '完成注册',
                          key: ValueKey('idle'),
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 15,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                        _SubmitPhase.loading => const SizedBox(
                          key: ValueKey('loading'),
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(
                            strokeWidth: 2.4,
                            color: Colors.white,
                          ),
                        ),
                        _SubmitPhase.success => const Icon(
                          Icons.check_rounded,
                          key: ValueKey('success'),
                          color: Colors.white,
                          size: 30,
                        ),
                        _SubmitPhase.failure => const Icon(
                          Icons.close_rounded,
                          key: ValueKey('failure'),
                          color: Colors.white,
                          size: 30,
                        ),
                      },
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _field(
    TextEditingController controller,
    String label,
    String hint, {
    TextInputType? keyboardType,
    bool obscure = false,
    double bottomPadding = 12,
  }) {
    return Padding(
      padding: EdgeInsets.only(bottom: bottomPadding),
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

class _ConfettiBurst extends StatelessWidget {
  final Animation<double> animation;

  const _ConfettiBurst({required this.animation});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: animation,
      builder: (context, _) => CustomPaint(
        painter: _ConfettiPainter(animation.value),
        size: Size.infinite,
      ),
    );
  }
}

class _ConfettiPainter extends CustomPainter {
  final double progress;

  _ConfettiPainter(this.progress);

  static final List<_ConfettiParticle> _particles = _buildParticles();

  static List<_ConfettiParticle> _buildParticles() {
    final random = math.Random(20261019);
    const colors = [
      Color(0xFF2563EB),
      Color(0xFF10B981),
      Color(0xFFF59E0B),
      Color(0xFFEF4444),
      Color(0xFF8B5CF6),
      Color(0xFF06B6D4),
    ];
    return List.generate(54, (index) {
      final angle = -math.pi / 2 + (random.nextDouble() - 0.5) * math.pi * 1.5;
      return _ConfettiParticle(
        angle: angle,
        speed: 0.35 + random.nextDouble() * 0.85,
        size: 4 + random.nextDouble() * 5,
        color: colors[index % colors.length],
        rotation: random.nextDouble() * math.pi,
        spin: (random.nextDouble() - 0.5) * 8,
      );
    });
  }

  @override
  void paint(Canvas canvas, Size size) {
    if (progress >= 1) return;
    final center = Offset(size.width / 2, size.height * 0.56);
    final opacity = (1 - progress).clamp(0.0, 1.0);
    final paint = Paint()..style = PaintingStyle.fill;
    for (final particle in _particles) {
      final distance = particle.speed * progress;
      final dx =
          center.dx + math.cos(particle.angle) * distance * size.width * 0.62;
      final dy =
          center.dy +
          math.sin(particle.angle) * distance * size.height * 0.42 +
          200 * progress * progress;
      paint.color = particle.color.withValues(alpha: opacity);
      canvas.save();
      canvas.translate(dx, dy);
      canvas.rotate(particle.rotation + particle.spin * progress);
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromCenter(
            center: Offset.zero,
            width: particle.size * 1.6,
            height: particle.size,
          ),
          const Radius.circular(2),
        ),
        paint,
      );
      canvas.restore();
    }
  }

  @override
  bool shouldRepaint(covariant _ConfettiPainter oldDelegate) =>
      oldDelegate.progress != progress;
}

class _ConfettiParticle {
  final double angle;
  final double speed;
  final double size;
  final Color color;
  final double rotation;
  final double spin;

  const _ConfettiParticle({
    required this.angle,
    required this.speed,
    required this.size,
    required this.color,
    required this.rotation,
    required this.spin,
  });
}
