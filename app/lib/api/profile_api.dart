import 'api_client.dart';

/// 个人中心聚合：当前用户、社交统计、作者资料。
class ProfileApi {
  static final ApiClient _client = ApiClient();

  static Future<Map<String, dynamic>?> _getMap(String path) async {
    try {
      final res = await _client.get(path);
      if (res is Map<String, dynamic>) return res;
      if (res is Map && res['data'] is Map<String, dynamic>) {
        return res['data'] as Map<String, dynamic>;
      }
    } catch (_) {}
    return null;
  }

  static Future<Map<String, dynamic>?> fetchMe() =>
      _getMap('/api/common/auth/me');

  static Future<Map<String, dynamic>?> fetchSocialStats() =>
      _getMap('/api/app/profile/stats');

  static Future<Map<String, dynamic>?> fetchAuthorProfile(String userId) =>
      _getMap('/api/app/community/authors/$userId');

  static Future<Map<String, dynamic>?> fetchPrivacy() =>
      _getMap('/api/app/profile/privacy');

  static Future<Map<String, dynamic>?> updatePrivacy({
    required bool showAcademicInfo,
    required bool showMedals,
  }) async {
    try {
      final res = await _client.put(
        '/api/app/profile/privacy',
        body: {'showAcademicInfo': showAcademicInfo, 'showMedals': showMedals},
      );
      return res is Map<String, dynamic> ? res : null;
    } catch (_) {
      return null;
    }
  }

  static Future<Map<String, dynamic>?> fetchCredentials() =>
      _getMap('/api/app/profile/credentials');

  static Future<Map<String, dynamic>?> updateCredentials({
    required String username,
    required String password,
    String? currentPassword,
  }) async {
    try {
      final res = await _client.post(
        '/api/app/profile/credentials',
        body: {
          'username': username,
          'password': password,
          if (currentPassword != null && currentPassword.isNotEmpty)
            'currentPassword': currentPassword,
        },
      );
      return res is Map<String, dynamic> ? res : null;
    } on ApiException {
      rethrow;
    } catch (_) {
      return null;
    }
  }

  static Future<Map<String, dynamic>?> bindEmail({
    required String email,
    required String code,
  }) async {
    try {
      final res = await _client.post(
        '/api/app/profile/email',
        body: {'email': email, 'code': code},
      );
      return res is Map<String, dynamic> ? res : null;
    } on ApiException {
      rethrow;
    } catch (_) {
      return null;
    }
  }

  static Future<Map<String, dynamic>?> bindQq({
    required String accessToken,
    String? openId,
  }) async {
    try {
      final res = await _client.post(
        '/api/app/profile/qq',
        body: {
          'accessToken': accessToken,
          if (openId != null && openId.isNotEmpty) 'openId': openId,
        },
      );
      return res is Map<String, dynamic> ? res : null;
    } on ApiException {
      rethrow;
    } catch (_) {
      return null;
    }
  }

  static Future<bool> updatePhone(String phone) async {
    try {
      await _client.post('/api/app/profile/phone', body: {'phone': phone});
      return true;
    } catch (_) {
      return false;
    }
  }

  static Future<Map<String, dynamic>?> fetchPreferences() async {
    try {
      final res = await _client.get('/api/app/users/me/preferences');
      if (res is Map<String, dynamic>) return res;
    } catch (_) {}
    return null;
  }

  static Future<Map<String, dynamic>?> updatePreferences(
    Map<String, bool> values,
  ) async {
    try {
      final res = await _client.put(
        '/api/app/users/me/preferences',
        body: values,
      );
      if (res is Map<String, dynamic>) return res;
    } catch (_) {}
    return null;
  }
}
