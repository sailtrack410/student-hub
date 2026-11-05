import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/auth_service.dart';
import 'package:smartcampus_app/main.dart';
import 'package:smartcampus_app/models/user_session.dart';
import 'package:smartcampus_app/pages/login/login_page.dart';
import 'package:smartcampus_app/pages/login/session_recovery_page.dart';
import 'package:smartcampus_app/services/session_store.dart';

const _baseUrl = 'https://campus.test';
const _session = UserSession(
  token: 'token-a',
  userId: 'user-a',
  username: 'student-a',
  displayName: '甲同学',
  identityType: 'STUDENT',
  authorizationStatus: 'BASIC',
  isNewUser: false,
);

http.Response _success(Object data) => http.Response(
  jsonEncode({'success': true, 'data': data}),
  200,
  headers: {'content-type': 'application/json; charset=utf-8'},
);

Future<ApiClient> _client(
  Future<http.Response> Function(http.Request) handler,
) async {
  final client = ApiClient(baseUrl: _baseUrl, client: MockClient(handler));
  await client.init();
  return client;
}

Future<void> _seed(ApiClient client) async {
  await client.setToken(_session.token);
  await SessionStore.saveSession(_session, client.baseUrl);
  _session.activate();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    ApiClient.onUnauthorized = null;
  });

  tearDown(() async {
    ApiClient.onUnauthorized = null;
    await ApiClient().clearToken();
  });

  test('断网恢复同一凭证的真实资料，保留 token 与缓存', () async {
    final client = await _client((_) async {
      throw http.ClientException('offline');
    });
    final auth = AuthService(client);
    await _seed(client);

    final result = await auth.restoreSession();

    expect(result.session?.userId, 'user-a');
    expect(result.fromCache, isTrue);
    expect(result.needsRetry, isFalse);
    expect(client.token, 'token-a');
    expect(UserSession.currentUserId, 'user-a');
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString(SessionStore.tokenKey), 'token-a');
    final cache = prefs.getString(SessionStore.profileKey)!;
    expect(cache, isNot(contains('token-a')));
    expect(jsonDecode(cache)['profile']['token'], isNull);
  });

  test('5xx 与异常用户资料均使用缓存，不退出登录', () async {
    var malformed = false;
    final client = await _client((_) async {
      return malformed
          ? _success({'displayName': '无 ID'})
          : http.Response('', 503);
    });
    final auth = AuthService(client);
    await _seed(client);

    expect((await auth.restoreSession()).fromCache, isTrue);
    malformed = true;
    expect((await auth.restoreSession()).session?.userId, 'user-a');
    expect(client.token, 'token-a');
  });

  test('短超时使用缓存，延迟的成功响应不会覆盖恢复结果', () async {
    final response = Completer<http.Response>();
    final client = await _client((_) => response.future);
    final auth = AuthService(client);
    await _seed(client);

    final result = await auth.restoreSession(
      timeout: const Duration(milliseconds: 10),
    );
    expect(result.fromCache, isTrue);
    response.complete(_success({'userId': 'unexpected-user'}));
    await Future<void>.delayed(Duration.zero);
    expect(UserSession.currentUserId, 'user-a');
    expect((await SessionStore.cachedSession(_baseUrl))?.userId, 'user-a');
  });

  test('只有旧 token 时断网保留凭证并要求重试', () async {
    final client = await _client(
      (_) async => throw http.ClientException('offline'),
    );
    await client.setToken('legacy-token');

    final result = await AuthService(client).restoreSession();

    expect(result.needsRetry, isTrue);
    expect(result.session, isNull);
    expect(client.token, 'legacy-token');
    expect(UserSession.currentUserId, isNull);
  });

  test('超时后的迟到 401 仍清除同一失效会话', () async {
    final response = Completer<http.Response>();
    final invalidated = Completer<void>();
    final client = await _client((_) => response.future);
    final auth = AuthService(client);
    await _seed(client);
    ApiClient.onUnauthorized = invalidated.complete;

    expect(
      (await auth.restoreSession(timeout: const Duration(milliseconds: 10)))
          .fromCache,
      isTrue,
    );
    response.complete(
      http.Response(
        jsonEncode({'code': 'AUTH_401'}),
        401,
        headers: {'content-type': 'application/json'},
      ),
    );
    await invalidated.future;

    expect(client.token, isNull);
    expect(UserSession.currentUserId, isNull);
    expect(await SessionStore.cachedSession(_baseUrl), isNull);
  });

  test('切换账号后迟到的成功恢复不会覆盖新身份或缓存', () async {
    final response = Completer<http.Response>();
    final started = Completer<void>();
    final client = await _client((_) {
      started.complete();
      return response.future;
    });
    final auth = AuthService(client);
    await _seed(client);
    final restoring = auth.restoreSession();
    await started.future;
    await client.setToken('token-b');
    final newSession = UserSession.fromJson({'userId': 'user-b'}, 'token-b');
    await SessionStore.saveSession(newSession, _baseUrl);
    newSession.activate();

    response.complete(_success({'userId': 'user-a'}));
    expect((await restoring).session, isNull);
    expect(UserSession.currentUserId, 'user-b');
    expect((await SessionStore.cachedSession(_baseUrl))?.userId, 'user-b');
  });

  test('成功校验刷新缓存并激活当前身份', () async {
    final client = await _client(
      (_) async => _success({
        'userId': 'user-a',
        'username': 'student-a',
        'displayName': '更新后的名字',
      }),
    );
    final auth = AuthService(client);
    await _seed(client);

    final result = await auth.restoreSession();

    expect(result.fromCache, isFalse);
    expect(result.session?.displayName, '更新后的名字');
    expect(UserSession.currentUserName, '更新后的名字');
    expect((await SessionStore.cachedSession(_baseUrl))?.displayName, '更新后的名字');
  });

  test('明确 401 清 token、缓存与身份，并且并发 401 只通知一次', () async {
    final client = await _client(
      (_) async => http.Response(
        jsonEncode({'code': 'AUTH_401'}),
        401,
        headers: {'content-type': 'application/json'},
      ),
    );
    final auth = AuthService(client);
    await _seed(client);
    var calls = 0;
    ApiClient.onUnauthorized = () => calls++;

    await Future.wait([
      client.get('/private').catchError((_) => null),
      client.get('/private-again').catchError((_) => null),
    ]);

    expect(calls, 1);
    expect(client.isAuthenticated, isFalse);
    expect(UserSession.currentUserId, isNull);
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.containsKey(SessionStore.tokenKey), isFalse);
    expect(prefs.containsKey(SessionStore.profileKey), isFalse);
    expect((await auth.restoreSession()).needsRetry, isFalse);
  });

  test('只有实际 HTTP 401 触发退出，业务错误码不会清凭证', () async {
    final client = await _client(
      (_) async => http.Response(
        jsonEncode({
          'success': false,
          'code': 'UNAUTHORIZED',
          'message': '暂不可用',
        }),
        200,
      ),
    );
    final auth = AuthService(client);
    await _seed(client);

    expect((await auth.restoreSession()).fromCache, isTrue);
    expect(client.token, 'token-a');
  });

  test('旧账号延迟 401 不清新账号，旧 ApiClient 使用新 token', () async {
    final response = Completer<http.Response>();
    final started = Completer<void>();
    final oldClient = await _client((_) {
      started.complete();
      return response.future;
    });
    final auth = AuthService(oldClient);
    await _seed(oldClient);
    final oldRequest = oldClient.get('/private');
    await started.future;

    String? authorization;
    final newClient = ApiClient(
      baseUrl: _baseUrl,
      client: MockClient((request) async {
        authorization = request.headers['Authorization'];
        return _success({'ok': true});
      }),
    );
    await newClient.setToken('token-b');
    const newSession = UserSession(
      token: 'token-b',
      userId: 'user-b',
      username: 'student-b',
      displayName: '乙同学',
      identityType: 'STUDENT',
      authorizationStatus: 'BASIC',
      isNewUser: false,
    );
    await SessionStore.saveSession(newSession, _baseUrl);
    newSession.activate();
    var calls = 0;
    ApiClient.onUnauthorized = () => calls++;
    response.complete(
      http.Response(
        jsonEncode({'code': 'AUTH_401'}),
        401,
        headers: {'content-type': 'application/json'},
      ),
    );
    await expectLater(oldRequest, throwsA(isA<ApiException>()));

    expect(oldClient.token, 'token-b');
    expect(UserSession.currentUserId, 'user-b');
    expect((await SessionStore.cachedSession(_baseUrl))?.userId, 'user-b');
    expect(calls, 0);
    await newClient.get('/private');
    expect(authorization, 'Bearer token-b');

    await auth.logout();
    expect(oldClient.token, isNull);
    expect(newClient.token, isNull);
    await newClient.get('/public');
    expect(authorization, isNull);
  });

  test('登录另一账号先清旧资料，响应缺少用户 ID 不继承旧身份', () async {
    var valid = true;
    final client = await _client(
      (_) async => _success({
        'accessToken': 'token-b',
        'user': valid
            ? {'userId': 'user-b', 'displayName': '乙同学'}
            : <String, dynamic>{},
      }),
    );
    final auth = AuthService(client);
    await _seed(client);
    final session = await auth.passwordLogin(username: 'b', password: 'p');
    expect(session.userId, 'user-b');
    expect(UserSession.currentUserId, 'user-b');
    expect((await SessionStore.cachedSession(_baseUrl))?.userId, 'user-b');

    valid = false;
    await expectLater(
      auth.passwordLogin(username: 'bad', password: 'p'),
      throwsA(isA<ApiException>()),
    );
    expect(UserSession.currentUserId, 'user-b');
    expect(client.token, 'token-b');
  });

  test('另一个 API 实例初始化等待当前凭证写入，不恢复旧 token', () async {
    final client = await _client((_) async => _success({}));
    await _seed(client);
    final changing = client.setToken('token-b');
    final another = ApiClient(baseUrl: _baseUrl);
    await another.init();
    await changing;
    expect(client.token, 'token-b');
    expect(another.token, 'token-b');
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString(SessionStore.tokenKey), 'token-b');
    expect(prefs.containsKey(SessionStore.profileKey), isFalse);
  });

  test('损坏、其他凭证或服务地址的缓存不会恢复成当前身份', () async {
    final client = await _client(
      (_) async => throw http.ClientException('offline'),
    );
    final auth = AuthService(client);
    await _seed(client);
    final prefs = await SharedPreferences.getInstance();
    final original = prefs.getString(SessionStore.profileKey)!;
    await prefs.setString(SessionStore.profileKey, '{broken');
    expect((await auth.restoreSession()).needsRetry, isTrue);
    await prefs.setString(SessionStore.profileKey, original);
    expect(await SessionStore.cachedSession('https://other.test'), isNull);
    final mismatched = jsonDecode(original) as Map<String, dynamic>;
    mismatched['tokenFingerprint'] = 'other-token';
    await prefs.setString(SessionStore.profileKey, jsonEncode(mismatched));
    expect((await auth.restoreSession()).session, isNull);
    expect(client.token, 'token-a');
  });

  test('SSE 与上传的明确 401 都使用统一凭证清理', () async {
    final client = await _client(
      (_) async => http.Response(
        jsonEncode({'code': 'AUTH_401'}),
        401,
        headers: {'content-type': 'application/json'},
      ),
    );
    AuthService(client);
    await _seed(client);
    await expectLater(
      client.streamSse('/stream').toList(),
      throwsA(isA<ApiException>()),
    );
    expect(client.token, isNull);
    await _seed(client);
    expect(await client.uploadImage(Uint8List.fromList([1, 2, 3])), isNull);
    expect(client.token, isNull);
    expect(await SessionStore.cachedSession(_baseUrl), isNull);
  });

  testWidgets('恢复页重试实际请求，失败保留凭证，成功返回真实会话', (tester) async {
    var online = false;
    var requests = 0;
    late ApiClient client;
    late AuthService auth;
    await tester.runAsync(() async {
      client = await _client((_) async {
        requests++;
        if (!online) throw http.ClientException('offline');
        return _success({'userId': 'user-a', 'displayName': '甲同学'});
      });
      auth = AuthService(client);
      await client.setToken('token-a');
    });
    UserSession? resolved;
    await tester.pumpWidget(
      MaterialApp(
        home: SessionRecoveryPage(
          authService: auth,
          onResolved: (session) {
            resolved = session;
          },
        ),
      ),
    );
    await tester.runAsync(() async {
      await tester.tap(find.text('重试连接'));
      await Future<void>.delayed(Duration.zero);
    });
    await tester.pumpAndSettle();
    expect(requests, 1);
    expect(client.token, 'token-a');
    expect(find.textContaining('连接仍未恢复'), findsOneWidget);
    online = true;
    await tester.runAsync(() async {
      await tester.tap(find.text('重试连接'));
      await Future<void>.delayed(Duration.zero);
    });
    await tester.pumpAndSettle();
    expect(requests, 2);
    expect(resolved?.userId, 'user-a');
  });

  testWidgets('恢复页切换账号主动清理凭证和缓存', (tester) async {
    late ApiClient client;
    late AuthService auth;
    await tester.runAsync(() async {
      client = await _client(
        (_) async => throw http.ClientException('offline'),
      );
      auth = AuthService(client);
      await _seed(client);
    });
    var resolved = false;
    await tester.pumpWidget(
      MaterialApp(
        home: SessionRecoveryPage(
          authService: auth,
          onResolved: (session) {
            expect(session, isNull);
            resolved = true;
          },
        ),
      ),
    );

    await tester.runAsync(() async {
      await tester.tap(find.text('切换账号'));
      await Future<void>.delayed(Duration.zero);
    });
    await tester.pumpAndSettle();
    expect(resolved, isTrue);
    expect(client.token, isNull);
    expect(UserSession.currentUserId, isNull);
    expect(await SessionStore.cachedSession(_baseUrl), isNull);
  });

  testWidgets('首帧前的 401 在 Navigator 创建后进入登录页', (tester) async {
    late ApiClient client;
    late AuthService auth;
    await tester.runAsync(() async {
      client = await _client(
        (_) async => http.Response(
          jsonEncode({'code': 'AUTH_401'}),
          401,
          headers: {'content-type': 'application/json'},
        ),
      );
      auth = AuthService(client);
      await _seed(client);
      configureUnauthorizedNavigation(auth);
      await expectLater(client.get('/private'), throwsA(isA<ApiException>()));
    });

    await tester.pumpWidget(
      MaterialApp(
        navigatorKey: appNavigatorKey,
        home: const Scaffold(body: Text('缓存首页')),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(LoginPage), findsOneWidget);
    expect(find.text('缓存首页'), findsNothing);
    expect(client.isAuthenticated, isFalse);
  });
}
