import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/auth_service.dart';
import 'package:smartcampus_app/models/user_session.dart';
import 'package:smartcampus_app/services/session_store.dart';

const _baseUrl = 'https://campus.test';

http.Response _sessionResponse(String account) => http.Response(
  jsonEncode({
    'success': true,
    'data': {
      'accessToken': 'token-$account',
      'userId': 'user-$account',
      'displayName': '同学 $account',
    },
  }),
  200,
  headers: {'content-type': 'application/json; charset=utf-8'},
);

Future<AuthService> _auth(
  Future<http.Response> Function(http.Request) handler,
) async {
  final client = ApiClient(baseUrl: _baseUrl, client: MockClient(handler));
  await client.init();
  return AuthService(client);
}

Future<UserSession> _authenticate(AuthService auth, String method) {
  return switch (method) {
    'password' => auth.passwordLogin(username: 'student', password: 'password'),
    'register' => auth.register(
      accountId: 'student',
      email: 'student@example.test',
      password: 'password',
      code: '123456',
    ),
    'qq' => auth.qqLogin(accessToken: 'qq-access', openId: 'qq-open'),
    _ => throw ArgumentError.value(method, 'method'),
  };
}

Future<void> _expectAccount(String account) async {
  expect(ApiClient().token, 'token-$account');
  expect(UserSession.currentUserId, 'user-$account');
  expect((await SessionStore.cachedSession(_baseUrl))?.userId, 'user-$account');
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    ApiClient.onUnauthorized = null;
  });

  tearDown(() async {
    await ApiClient().clearToken();
  });

  for (final methods in [
    ('register', 'password'),
    ('password', 'qq'),
    ('qq', 'register'),
  ]) {
    test('${methods.$1} 的迟到响应不会覆盖其他实例的 ${methods.$2} 新账号', () async {
      final oldResponse = Completer<http.Response>();
      final oldStarted = Completer<void>();
      final oldAuth = await _auth((_) {
        oldStarted.complete();
        return oldResponse.future;
      });
      final newAuth = await _auth((_) async => _sessionResponse('b'));

      final oldLogin = _authenticate(oldAuth, methods.$1);
      await oldStarted.future;
      final rejected = expectLater(
        oldLogin,
        throwsA(
          isA<ApiException>().having((e) => e.code, 'code', 'SESSION_CHANGED'),
        ),
      );
      expect((await _authenticate(newAuth, methods.$2)).userId, 'user-b');
      await _expectAccount('b');

      oldResponse.complete(_sessionResponse('a'));
      await rejected;
      await _expectAccount('b');
    });
  }

  for (final method in ['password', 'register', 'qq']) {
    test('退出登录使待完成的 $method 尝试失效', () async {
      final response = Completer<http.Response>();
      final started = Completer<void>();
      final auth = await _auth((_) {
        started.complete();
        return response.future;
      });
      final pending = _authenticate(auth, method);
      await started.future;
      final rejected = expectLater(
        pending,
        throwsA(
          isA<ApiException>().having((e) => e.code, 'code', 'SESSION_CHANGED'),
        ),
      );

      await auth.logout();
      response.complete(_sessionResponse('a'));
      await rejected;

      expect(auth.client.isAuthenticated, isFalse);
      expect(UserSession.currentUserId, isNull);
      expect(await SessionStore.cachedSession(_baseUrl), isNull);
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.containsKey(SessionStore.tokenKey), isFalse);
      expect(prefs.containsKey(SessionStore.profileKey), isFalse);
    });
  }

  test('后发尝试尚未完成时，旧响应也不提前建立会话', () async {
    final firstResponse = Completer<http.Response>();
    final secondResponse = Completer<http.Response>();
    final started = <Completer<void>>[Completer<void>(), Completer<void>()];
    var requests = 0;
    final auth = await _auth((_) {
      final index = requests++;
      started[index].complete();
      return index == 0 ? firstResponse.future : secondResponse.future;
    });
    final first = auth.passwordLogin(username: 'a', password: 'password');
    await started[0].future;
    final rejected = expectLater(
      first,
      throwsA(
        isA<ApiException>().having((e) => e.code, 'code', 'SESSION_CHANGED'),
      ),
    );
    final second = auth.passwordLogin(username: 'b', password: 'password');
    await started[1].future;

    firstResponse.complete(_sessionResponse('a'));
    await rejected;
    expect(auth.client.token, isNull);
    expect(UserSession.currentUserId, isNull);
    secondResponse.complete(_sessionResponse('b'));
    await second;
    await _expectAccount('b');
  });

  test('其他 API 实例读取相同凭证不会取消当前有效登录', () async {
    final response = Completer<http.Response>();
    final started = Completer<void>();
    final auth = await _auth((_) {
      started.complete();
      return response.future;
    });
    final pending = auth.passwordLogin(username: 'a', password: 'password');
    await started.future;

    await ApiClient().init();
    response.complete(_sessionResponse('a'));

    expect((await pending).userId, 'user-a');
    await _expectAccount('a');
  });

  test('外部更新凭证也使尚未完成的登录尝试失效', () async {
    final response = Completer<http.Response>();
    final started = Completer<void>();
    final auth = await _auth((_) {
      started.complete();
      return response.future;
    });
    final pending = auth.passwordLogin(username: 'a', password: 'password');
    await started.future;
    final rejected = expectLater(
      pending,
      throwsA(
        isA<ApiException>().having((e) => e.code, 'code', 'SESSION_CHANGED'),
      ),
    );

    await ApiClient().setToken('token-external');
    response.complete(_sessionResponse('a'));
    await rejected;

    expect(auth.client.token, 'token-external');
    expect(UserSession.currentUserId, isNull);
    expect(await SessionStore.cachedSession(_baseUrl), isNull);
  });
}
