import 'package:flutter/material.dart';
import '../../api/auth_service.dart';
import '../../models/user_session.dart';
import '../../theme/app_theme.dart';
import '../login/login_page.dart';

/// 登录成功落地测试页（准备衔接 APP-首页）
class HomePlaceholderPage extends StatefulWidget {
  final UserSession session;
  final AuthService authService;

  const HomePlaceholderPage({
    super.key,
    required this.session,
    required this.authService,
  });

  @override
  State<HomePlaceholderPage> createState() => _HomePlaceholderPageState();
}

class _HomePlaceholderPageState extends State<HomePlaceholderPage> {
  Map<String, dynamic>? _userInfo;
  bool _loadingMe = false;

  @override
  void initState() {
    super.initState();
    _fetchMe();
  }

  Future<void> _fetchMe() async {
    setState(() => _loadingMe = true);
    try {
      final data = await widget.authService.getCurrentUser();
      if (mounted) setState(() => _userInfo = data);
    } catch (_) {
      // 忽略
    } finally {
      if (mounted) setState(() => _loadingMe = false);
    }
  }

  Future<void> _handleLogout() async {
    await widget.authService.logout();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(
        builder: (_) => LoginPage(authService: widget.authService),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.session;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('绵城校园 · 主工作台'),
        actions: [
          IconButton(
            tooltip: '退出登录',
            onPressed: _handleLogout,
            icon: const Icon(Icons.logout_rounded, color: AppColors.textSecondary),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // 登录成功卡片
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [AppColors.brand, Color(0xFF3B82F6)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(20),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x332563EB),
                    offset: Offset(0, 8),
                    blurRadius: 20,
                  ),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.2),
                          shape: BoxShape.circle,
                        ),
                        child: const Center(
                          child: Icon(
                            Icons.check_circle_rounded,
                            color: Colors.white,
                            size: 28,
                          ),
                        ),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              s.displayName.isNotEmpty ? s.displayName : s.username,
                              style: const TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                                color: Colors.white,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              '身份：${s.identityType} · 授权：${s.authorizationStatus}',
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.white.withValues(alpha: 0.85),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.link, color: Colors.white, size: 14),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            '后端连通: ${widget.authService.client.baseUrl}',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 11,
                              fontFamily: 'monospace',
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 24),

            // 会话详情数据
            Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.border),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        '当前会话凭据 (/auth/me 校验)',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                          color: AppColors.textPrimary,
                        ),
                      ),
                      if (_loadingMe)
                        const SizedBox(
                          width: 14,
                          height: 14,
                          child: CircularProgressIndicator(strokeWidth: 1.5),
                        )
                      else
                        GestureDetector(
                          onTap: _fetchMe,
                          child: const Icon(
                            Icons.refresh_rounded,
                            size: 18,
                            color: AppColors.brand,
                          ),
                        ),
                    ],
                  ),
                  const Divider(height: 24),
                  _buildDetailRow('用户 ID', s.userId),
                  _buildDetailRow('用户名', s.username),
                  _buildDetailRow('新注册用户', s.isNewUser ? '是 (首次登录建号)' : '否'),
                  _buildDetailRow(
                    'Token 片段',
                    s.token.length > 20
                        ? '${s.token.substring(0, 10)}...${s.token.substring(s.token.length - 8)}'
                        : s.token,
                  ),
                  if (_userInfo != null) ...[
                    const Divider(height: 24),
                    _buildDetailRow('手机号', _userInfo!['phone']?.toString() ?? '未绑定'),
                    _buildDetailRow('当前等级', 'Lv.${_userInfo!['level'] ?? 1}'),
                  ],
                ],
              ),
            ),

            const SizedBox(height: 24),

            // 下一步提示
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.brandSoft,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: const Color(0xFFBFDBFE)),
              ),
              child: const Row(
                children: [
                  Icon(Icons.palette_outlined, color: AppColors.brand, size: 22),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      '登录链路与会话保持已全通！\n下一步可继续依照画布实现 APP-首页 与服务大厅。',
                      style: TextStyle(
                        fontSize: 12,
                        color: AppColors.brandDeep,
                        height: 1.4,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDetailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
          ),
          Text(
            value,
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w500,
              color: AppColors.textPrimary,
              fontFamily: 'monospace',
            ),
          ),
        ],
      ),
    );
  }
}
