import '../models/user_session.dart';
import 'api_client.dart';

class AuthService {
  final ApiClient client;

  AuthService(this.client);

  /// QQ 授权登录 (POST /api/common/auth/qq-login)
  /// 在 mock 模式下，accessToken 传任意 mock 字符串即可完成建号与发 token
  Future<UserSession> qqLogin({
    required String accessToken,
    required String openId,
  }) async {
    final data = await client.post(
      '/api/common/auth/qq-login',
      body: {
        'accessToken': accessToken,
        'openId': openId,
      },
    );

    if (data is Map<String, dynamic>) {
      final token = data['token']?.toString() ?? '';
      await client.setToken(token);
      return UserSession.fromJson(data, token);
    }
    throw const ApiException(
      code: 'PARSE_ERROR',
      message: '登录响应格式错误',
    );
  }

  /// 学号 / 密码 兜底登录 (POST /api/common/auth/login)
  Future<UserSession> passwordLogin({
    required String username,
    required String password,
  }) async {
    final data = await client.post(
      '/api/common/auth/login',
      body: {
        'username': username,
        'password': password,
      },
    );

    if (data is Map<String, dynamic>) {
      final token = data['token']?.toString() ?? '';
      await client.setToken(token);
      return UserSession.fromJson(data, token);
    }
    throw const ApiException(
      code: 'PARSE_ERROR',
      message: '登录响应格式错误',
    );
  }

  /// 获取当前登录用户信息 (GET /api/common/auth/me)
  Future<Map<String, dynamic>> getCurrentUser() async {
    final data = await client.get('/api/common/auth/me');
    if (data is Map<String, dynamic>) {
      return data;
    }
    return {};
  }

  Future<void> logout() async {
    await client.clearToken();
  }
}
