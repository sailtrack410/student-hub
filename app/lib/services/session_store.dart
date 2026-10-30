import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/user_session.dart';

/// 绑定登录请求开始时的凭证版本与全局尝试序号。
class AuthenticationAttempt {
  final int _sessionRevision;
  final int _generation;

  const AuthenticationAttempt._(this._sessionRevision, this._generation);
}

/// 所有 API 实例共用一份凭证；用户资料只属于对应凭证与服务地址。
abstract final class SessionStore {
  static const tokenKey = 'auth_token_key';
  static const profileKey = 'auth_session_profile_v1';

  static String? _token;
  static int _revision = 0;
  static int _authenticationGeneration = 0;
  static Future<void> _pendingWrite = Future<void>.value();

  /// 清理内存中的账号状态；凭证与资料的落盘由本类统一处理。
  static Future<void> Function()? onSessionCleared;

  static String? get token => _token;

  /// 密码、注册和 QQ 授权共享顺序；新请求会立即替代旧登录尝试。
  static AuthenticationAttempt beginAuthentication() =>
      AuthenticationAttempt._(_revision, ++_authenticationGeneration);

  /// 校验与内存凭证更新之间没有 await，旧响应不会覆盖新会话。
  static Future<bool> establishSession(
    AuthenticationAttempt attempt,
    UserSession session,
    String baseUrl,
  ) async {
    if (_revision != attempt._sessionRevision ||
        _authenticationGeneration != attempt._generation) {
      return false;
    }
    final committedRevision = _revision + 1;
    bool isCurrent() =>
        _revision == committedRevision &&
        _authenticationGeneration == attempt._generation &&
        _token == session.token;

    await setToken(session.token);
    if (!isCurrent()) return false;
    await saveSession(session, baseUrl);
    if (!isCurrent()) return false;
    session.activate();
    return true;
  }

  static Future<void> init() async {
    final revision = _revision;
    await _pendingWrite;
    final prefs = await SharedPreferences.getInstance();
    if (_revision != revision) return;
    final saved = prefs.getString(tokenKey)?.trim();
    final next = saved == null || saved.isEmpty ? null : saved;
    if (_token != next) {
      UserSession.clearCurrentUser();
      _token = next;
      _revision++;
    }
  }

  static Future<void> setToken(String value) async {
    final next = value.trim();
    if (next.isEmpty) throw ArgumentError.value(value, 'token');
    final changed = _token != next;
    _token = next;
    final revision = ++_revision;
    Future<void>? cleanup;
    if (changed) {
      UserSession.clearCurrentUser();
      cleanup = onSessionCleared?.call();
    }
    await _write(revision, (prefs) async {
      if (changed) await prefs.remove(profileKey);
      await prefs.setString(tokenKey, next);
    });
    await cleanup;
  }

  /// expectedToken 阻止旧账号的延迟 401 清除随后建立的新会话。
  static Future<bool> clearToken({String? expectedToken}) async {
    if (expectedToken != null && _token != expectedToken) return false;
    _token = null;
    final revision = ++_revision;
    UserSession.clearCurrentUser();
    final cleanup = onSessionCleared?.call();
    await _write(revision, (prefs) async {
      await prefs.remove(tokenKey);
      await prefs.remove(profileKey);
    });
    await cleanup;
    return true;
  }

  static Future<void> saveSession(UserSession session, String baseUrl) async {
    if (_token != session.token || session.userId.trim().isEmpty) return;
    final revision = _revision;
    final cached = jsonEncode({
      'version': 1,
      'tokenFingerprint': _fingerprint(session.token),
      'baseUrl': baseUrl,
      'profile': session.toProfileJson(),
    });
    await _write(revision, (prefs) => prefs.setString(profileKey, cached));
  }

  static Future<UserSession?> cachedSession(String baseUrl) async {
    final activeToken = _token;
    final revision = _revision;
    if (activeToken == null) return null;
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(profileKey);
      if (raw == null || _revision != revision) return null;
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic> ||
          decoded['version'] != 1 ||
          decoded['tokenFingerprint'] != _fingerprint(activeToken) ||
          decoded['baseUrl'] != baseUrl ||
          decoded['profile'] is! Map<String, dynamic>) {
        return null;
      }
      final session = UserSession.fromJson(
        decoded['profile'] as Map<String, dynamic>,
        activeToken,
      );
      return session.userId.trim().isEmpty ? null : session;
    } catch (_) {
      return null;
    }
  }

  static String _fingerprint(String token) =>
      sha256.convert(utf8.encode(token)).toString();

  /// 保持存储操作有序，并跳过已经被登录/退出替代的写入。
  static Future<void> _write(
    int revision,
    Future<dynamic> Function(SharedPreferences prefs) operation,
  ) {
    final next = _pendingWrite.then((_) async {
      final prefs = await SharedPreferences.getInstance();
      if (_revision != revision) return;
      await operation(prefs);
    });
    _pendingWrite = next.catchError((Object _) {});
    return next;
  }
}
