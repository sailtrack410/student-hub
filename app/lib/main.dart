import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'api/ad_slot_api.dart';
import 'api/api_client.dart';
import 'api/auth_service.dart';
import 'api/feature_flags.dart';
import 'models/community_models.dart';
import 'models/user_session.dart';
import 'pages/home/home_page.dart';
import 'pages/login/login_page.dart';
import 'pages/login/session_recovery_page.dart';
import 'pages/common/maintenance_page.dart';
import 'services/community_deep_link_service.dart';
import 'theme/app_theme.dart';

/// 全局导航键：401 时从任意页面统一回登录页。
final GlobalKey<NavigatorState> appNavigatorKey = GlobalKey<NavigatorState>();

/// 迟到的启动请求也可能在首帧前返回 401，此时等待 Navigator 就绪再跳转。
void configureUnauthorizedNavigation(AuthService authService) {
  void showLoginIfStillSignedOut() {
    if (authService.client.isAuthenticated) return;
    appNavigatorKey.currentState?.pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => LoginPage(authService: authService)),
      (route) => false,
    );
  }

  ApiClient.onUnauthorized = () {
    if (appNavigatorKey.currentState == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        showLoginIfStillSignedOut();
      });
    } else {
      showLoginIfStillSignedOut();
    }
  };
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // 沉浸式透明状态栏配置
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
      systemNavigationBarColor: Colors.transparent,
      systemNavigationBarIconBrightness: Brightness.dark,
    ),
  );

  final apiClient = ApiClient();
  await apiClient.init();

  final authService = AuthService(apiClient);

  // 任意接口返回 401：清空本地会话并回到登录页，避免继续带着失效 Token 操作。
  configureUnauthorizedNavigation(authService);

  // 校验最多等待 5 秒，网络故障使用真实资料缓存；没有资料时保留凭证供重试。
  final restored = await authService.restoreSession();

  // 登录/会话建立后读取功能开关：失败保留默认值，不阻塞启动。
  unawaited(FeatureFlags.load(apiClient));

  // D-Share：登录会话恢复后启用帖子深链路由（未登录时收到链接静默忽略）
  if (restored.session != null) {
    CommunityDeepLinkService.ensureInitialized();
  }

  runApp(
    SmartCampusApp(
      authService: authService,
      initialSession: restored.session,
      sessionRecoveryRequired: restored.needsRetry,
    ),
  );
}

class SmartCampusApp extends StatelessWidget {
  final AuthService authService;
  final UserSession? initialSession;
  final bool forceLogin;
  final bool sessionRecoveryRequired;

  const SmartCampusApp({
    super.key,
    required this.authService,
    this.initialSession,
    this.forceLogin = false,
    this.sessionRecoveryRequired = false,
  });

  Widget _destination(UserSession? session) {
    if (session == null) return LoginPage(authService: authService);
    return MaintenanceGate(
      child: SplashAdGate(
        child: HomePage(session: session, authService: authService),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      navigatorKey: appNavigatorKey,
      title: '绵城校园',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      home: forceLogin
          ? _destination(null)
          : sessionRecoveryRequired
          ? SessionRecoveryPage(
              authService: authService,
              onResolved: (session) {
                appNavigatorKey.currentState?.pushAndRemoveUntil(
                  MaterialPageRoute(builder: (_) => _destination(session)),
                  (route) => false,
                );
              },
            )
          : _destination(initialSession),
    );
  }
}

/// 开屏广告位（SPLASH）：冷启动后若 SPLASH 有数据，展示 3 秒可跳过的覆盖层。
///
/// - 开关 `feature.community_ads=false`、无数据或请求失败时静默不展示；
/// - 展示时上报一次曝光；点击复用统一广告点击/落地链路后关闭；
/// - 每次冷启动只尝试一次，不影响 App 主流程。
class SplashAdGate extends StatefulWidget {
  const SplashAdGate({super.key, required this.child, this.debugInitialAd});

  final Widget child;

  /// 仅测试使用：注入开屏广告，跳过网络请求。
  @visibleForTesting
  final CommunityAd? debugInitialAd;

  @override
  State<SplashAdGate> createState() => _SplashAdGateState();
}

class _SplashAdGateState extends State<SplashAdGate> {
  CommunityAd? _ad;
  bool _visible = false;
  bool _impressionReported = false;
  Timer? _autoDismissTimer;

  @override
  void initState() {
    super.initState();
    if (widget.debugInitialAd != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _showAd(widget.debugInitialAd!, reportImpression: false);
      });
      return;
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      unawaited(_loadSplashAd());
    });
  }

  Future<void> _loadSplashAd() async {
    try {
      await FeatureFlags.waitReady();
      if (!mounted || !FeatureFlags.communityAdsEnabled) return;
      final ads = await AdSlotApi.fetchAds(AdSlot.splash);
      if (!mounted || ads.isEmpty) return;
      _showAd(ads.first);
    } catch (_) {
      // 开屏广告失败静默，不阻塞 App 启动。
    }
  }

  void _showAd(CommunityAd ad, {bool reportImpression = true}) {
    if (!mounted) return;
    setState(() {
      _ad = ad;
      _visible = true;
    });
    _autoDismissTimer = Timer(const Duration(seconds: 3), _dismiss);
    if (reportImpression && !_impressionReported) {
      _impressionReported = true;
      unawaited(AdSlotApi.reportImpression(ad));
    }
  }

  void _dismiss() {
    _autoDismissTimer?.cancel();
    if (!mounted || !_visible) return;
    setState(() => _visible = false);
  }

  @override
  void dispose() {
    _autoDismissTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final ad = _ad;
    return Stack(
      children: [
        widget.child,
        if (_visible && ad != null)
          Positioned.fill(
            child: _SplashAdOverlay(
              ad: ad,
              onSkip: _dismiss,
              onTap: () {
                _dismiss();
                unawaited(AdSlotApi.openLanding(context, ad));
              },
            ),
          ),
      ],
    );
  }
}

class _SplashAdOverlay extends StatelessWidget {
  const _SplashAdOverlay({
    required this.ad,
    required this.onSkip,
    required this.onTap,
  });

  final CommunityAd ad;
  final VoidCallback onSkip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final coverUrl = ad.coverUrl ?? '';
    return Material(
      color: Colors.white,
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (coverUrl.isNotEmpty)
            Image.network(
              coverUrl,
              fit: BoxFit.cover,
              errorBuilder: (_, _, _) => _buildFallback(),
            )
          else
            _buildFallback(),
          GestureDetector(behavior: HitTestBehavior.opaque, onTap: onTap),
          SafeArea(
            child: Stack(
              children: [
                Positioned(
                  top: 8,
                  right: 12,
                  child: GestureDetector(
                    onTap: onSkip,
                    behavior: HitTestBehavior.opaque,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 6,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.45),
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: const Text(
                        '跳过',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),
                ),
                Positioned(
                  left: 20,
                  right: 20,
                  bottom: 32,
                  child: IgnorePointer(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 6,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.black.withValues(alpha: 0.45),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            ad.sponsoredLabel.isEmpty
                                ? '推广'
                                : ad.sponsoredLabel,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          ad.title,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            height: 1.3,
                            shadows: [
                              Shadow(color: Colors.black54, blurRadius: 8),
                            ],
                          ),
                        ),
                        if (ad.brandName.isNotEmpty) ...[
                          const SizedBox(height: 4),
                          Text(
                            ad.brandName,
                            style: const TextStyle(
                              color: Colors.white70,
                              fontSize: 12,
                              shadows: [
                                Shadow(color: Colors.black54, blurRadius: 6),
                              ],
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFallback() {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF2563EB), Color(0xFF14B8A6)],
        ),
      ),
      alignment: Alignment.center,
      child: const Icon(
        Icons.auto_awesome_rounded,
        size: 56,
        color: Colors.white,
      ),
    );
  }
}
