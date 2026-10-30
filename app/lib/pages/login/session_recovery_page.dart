import 'package:flutter/material.dart';

import '../../api/auth_service.dart';
import '../../models/user_session.dart';
import '../../theme/app_theme.dart';

/// 旧安装只有凭证、尚未缓存用户资料时，保留登录信息并提供真实重试。
class SessionRecoveryPage extends StatefulWidget {
  const SessionRecoveryPage({
    super.key,
    required this.authService,
    required this.onResolved,
  });

  final AuthService authService;
  final ValueChanged<UserSession?> onResolved;

  @override
  State<SessionRecoveryPage> createState() => _SessionRecoveryPageState();
}

class _SessionRecoveryPageState extends State<SessionRecoveryPage> {
  bool _busy = false;
  bool _retried = false;

  Future<void> _retry() async {
    if (_busy) return;
    setState(() => _busy = true);
    final result = await widget.authService.restoreSession();
    if (!mounted) return;
    if (!result.needsRetry) {
      setState(() => _busy = false);
      widget.onResolved(result.session);
      return;
    }
    setState(() {
      _busy = false;
      _retried = true;
    });
  }

  Future<void> _switchAccount() async {
    if (_busy) return;
    setState(() => _busy = true);
    await widget.authService.logout();
    if (mounted) {
      setState(() => _busy = false);
      widget.onResolved(null);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(
                    Icons.wifi_off_rounded,
                    size: 56,
                    color: AppColors.brand,
                  ),
                  const SizedBox(height: 24),
                  const Text(
                    '暂时连接不上校园服务',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w700,
                      color: AppColors.textPrimary,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    _retried
                        ? '连接仍未恢复，请检查网络后重试。你的登录信息已保留。'
                        : '登录信息已保留，连接恢复后可以继续使用。',
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      height: 1.6,
                      color: AppColors.textSecondary,
                    ),
                  ),
                  const SizedBox(height: 32),
                  SizedBox(
                    width: double.infinity,
                    height: 48,
                    child: FilledButton(
                      onPressed: _busy ? null : _retry,
                      child: _busy
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('重试连接'),
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextButton(
                    onPressed: _busy ? null : _switchAccount,
                    style: TextButton.styleFrom(
                      minimumSize: const Size(120, 48),
                    ),
                    child: const Text('切换账号'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
