import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'api/api_client.dart';
import 'api/auth_service.dart';
import 'models/user_session.dart';
import 'pages/home/home_page.dart';
import 'pages/login/login_page.dart';
import 'theme/app_theme.dart';

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

  // 启动时检查是否有本地保存的会话，若无则默认注入李同学会话以便即时预览 5:1 画布还原
  UserSession? initialSession;
  if (apiClient.isAuthenticated) {
    try {
      final me = await authService.getCurrentUser();
      if (me.isNotEmpty) {
        initialSession = UserSession(
          token: apiClient.token!,
          userId: me['userId']?.toString() ?? '',
          username: me['username']?.toString() ?? '',
          displayName: me['displayName']?.toString() ?? '',
          identityType: me['identityType']?.toString() ?? 'STUDENT',
          authorizationStatus: me['authorizationStatus']?.toString() ?? 'BASIC',
          isNewUser: false,
        );
      }
    } catch (_) {
      // Token 无效或过期，已在 apiClient 中清理
    }
  }

  // 默认使用画布对应的李同学身份直达主页，亦可在主页一键返回登录页
  initialSession ??= const UserSession(
    token: 'dev_mock_token_student01',
    userId: '1001',
    username: 'student01',
    displayName: '李同学',
    identityType: 'STUDENT',
    authorizationStatus: 'BASIC',
    isNewUser: false,
  );

  runApp(SmartCampusApp(
    authService: authService,
    initialSession: initialSession,
  ));
}

class SmartCampusApp extends StatelessWidget {
  final AuthService authService;
  final UserSession? initialSession;
  final bool forceLogin;

  const SmartCampusApp({
    super.key,
    required this.authService,
    this.initialSession,
    this.forceLogin = false,
  });

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '绵城校园',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      home: forceLogin
          ? LoginPage(authService: authService)
          : HomePage(
              session: initialSession ??
                  const UserSession(
                    token: 'dev_mock_token_student01',
                    userId: '1001',
                    username: 'student01',
                    displayName: '李同学',
                    identityType: 'STUDENT',
                    authorizationStatus: 'BASIC',
                    isNewUser: false,
                  ),
              authService: authService,
            ),
    );
  }
}
