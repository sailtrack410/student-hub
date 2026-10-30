import '../models/user_session.dart';
import '../services/community_socket.dart';
import '../services/recently_viewed_store.dart';
import '../services/session_store.dart';
import 'api_client.dart';

class SessionRestoreResult {
  final UserSession? session;
  final bool needsRetry;
  final bool fromCache;

  const SessionRestoreResult({
    this.session,
    this.needsRetry = false,
    this.fromCache = false,
  });
}

class AuthService {
  final ApiClient client;

  AuthService(this.client) {
    SessionStore.onSessionCleared = _clearAccountState;
  }

  static Future<void> _clearAccountState() {
    RecentlyViewedStore.clear();
    return CommunitySocket.shared.disconnect();
  }

  /// 短暂网络故障保留凭证；只使用同一凭证下缓存过的真实用户资料。
  Future<SessionRestoreResult> restoreSession({
    Duration timeout = const Duration(seconds: 5),
  }) async {
    final token = client.token;
    if (token == null || token.isEmpty) {
      return const SessionRestoreResult();
    }
    final cached = await SessionStore.cachedSession(client.baseUrl);
    try {
      final me = await getCurrentUser().timeout(timeout);
      if (client.token != token) return const SessionRestoreResult();
      final session = _parseSession(me, token);
      await SessionStore.saveSession(session, client.baseUrl);
      if (client.token != token) return const SessionRestoreResult();
      session.activate();
      return SessionRestoreResult(session: session);
    } catch (_) {
      // ApiClient 只在实际 HTTP 401 时清会话；超时、5xx 与资料异常不退出。
      if (client.token != token) return const SessionRestoreResult();
      if (cached != null) {
        cached.activate();
        return SessionRestoreResult(session: cached, fromCache: true);
      }
      return const SessionRestoreResult(needsRetry: true);
    }
  }

  UserSession _parseSession(Map<String, dynamic> data, String token) {
    final session = UserSession.fromJson(data, token);
    if (token.trim().isEmpty || session.userId.trim().isEmpty) {
      throw const ApiException(code: 'PARSE_ERROR', message: '用户资料暂时不可用，请重试');
    }
    return session;
  }

  Future<UserSession> _establishSession(
    Map<String, dynamic> data,
    AuthenticationAttempt attempt,
  ) async {
    final token =
        (data['accessToken']?.toString() ?? data['token']?.toString() ?? '')
            .trim();
    final session = _parseSession(data, token);
    final established = await SessionStore.establishSession(
      attempt,
      session,
      client.baseUrl,
    );
    if (!established) {
      throw const ApiException(code: 'SESSION_CHANGED', message: '登录状态已更新，请重试');
    }
    return session;
  }

  /// QQ 授权登录 (POST /api/common/auth/qq-login)
  /// accessToken 来自原生 QQ SDK；服务端会反查 openId 并校验 token 归属应用。
  Future<UserSession> qqLogin({
    required String accessToken,
    required String openId,
  }) async {
    final attempt = SessionStore.beginAuthentication();
    final data = await client.post(
      '/api/common/auth/qq-login',
      body: {'accessToken': accessToken, 'openId': openId},
    );

    if (data is Map<String, dynamic>) {
      return _establishSession(data, attempt);
    }
    throw const ApiException(code: 'PARSE_ERROR', message: '登录响应格式错误');
  }

  /// 学号 / 密码 兜底登录 (POST /api/common/auth/login)
  Future<UserSession> passwordLogin({
    required String username,
    required String password,
  }) async {
    final attempt = SessionStore.beginAuthentication();
    final data = await client.post(
      '/api/common/auth/login',
      body: {'username': username, 'password': password},
    );

    if (data is Map<String, dynamic>) {
      return _establishSession(data, attempt);
    }
    throw const ApiException(code: 'PARSE_ERROR', message: '登录响应格式错误');
  }

  /// 获取当前登录用户信息 (GET /api/common/auth/me)
  Future<Map<String, dynamic>> getCurrentUser() async {
    final data = await client.get('/api/common/auth/me');
    if (data is Map<String, dynamic>) {
      return data;
    }
    throw const ApiException(code: 'PARSE_ERROR', message: '用户资料暂时不可用，请重试');
  }

  /// 发送校园邮箱验证码；开发环境返回 devCode
  Future<String?> sendEmailCode({
    required String email,
    required String purpose,
  }) async {
    final data = await client.post(
      '/api/common/auth/email-code',
      body: {'email': email, 'purpose': purpose},
    );
    if (data is Map<String, dynamic>) {
      return data['devCode']?.toString();
    }
    return null;
  }

  /// 注册并直接返回登录会话
  Future<UserSession> register({
    required String accountId,
    required String email,
    required String password,
    required String code,
  }) async {
    final attempt = SessionStore.beginAuthentication();
    final data = await client.post(
      '/api/common/auth/register',
      body: {
        'accountId': accountId,
        'email': email,
        'password': password,
        'code': code,
      },
    );
    if (data is Map<String, dynamic>) {
      return _establishSession(data, attempt);
    }
    throw const ApiException(code: 'PARSE_ERROR', message: '注册响应格式错误');
  }

  /// 找回密码
  Future<bool> resetPassword({
    required String accountId,
    required String email,
    required String newPassword,
    required String code,
  }) async {
    await client.post(
      '/api/common/auth/password/reset',
      body: {
        'accountId': accountId,
        'email': email,
        'newPassword': newPassword,
        'code': code,
      },
    );
    return true;
  }

  Future<void> logout() async {
    await client.clearToken();
  }
}
