import 'api_client.dart';

class JwCaptcha {
  final String uid;
  final String imageBase64;
  const JwCaptcha({required this.uid, required this.imageBase64});
}

class JwApi {
  static final ApiClient _client = ApiClient();

  static Future<JwCaptcha?> fetchCaptcha() async {
    try {
      final res = await _client.get('/api/app/jw/captcha');
      if (res is Map<String, dynamic>) {
        return JwCaptcha(
          uid: res['uid']?.toString() ?? '',
          imageBase64: res['imageBase64']?.toString() ?? '',
        );
      }
    } catch (_) {}
    return null;
  }

  static Future<Map<String, dynamic>?> status() async {
    try {
      final res = await _client.get('/api/app/jw/status');
      if (res is Map<String, dynamic>) return res;
    } catch (_) {}
    return null;
  }

  static Future<Map<String, dynamic>?> auth({
    required String ssoAccount,
    required String password,
    required String captchaUid,
    required String captchaCode,
  }) async {
    try {
      final res = await _client.post(
        '/api/app/jw/auth',
        body: {
          'ssoAccount': ssoAccount,
          'password': password,
          'captchaUid': captchaUid,
          'captchaCode': captchaCode,
        },
      );
      if (res is Map<String, dynamic>) return res;
    } catch (_) {}
    return null;
  }

  static Future<bool> unbind() async {
    try {
      await _client.post('/api/app/jw/unbind', body: {});
      return true;
    } catch (_) {
      return false;
    }
  }
}
