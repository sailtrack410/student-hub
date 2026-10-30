import 'dart:convert';
import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/api_client.dart';

class AbortClient extends http.BaseClient {
  bool aborted = false;
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    await (request as http.Abortable).abortTrigger;
    aborted = true;
    throw http.RequestAbortedException(request.url);
  }
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));
  tearDown(() async {
    await ApiClient().clearToken();
    ApiClient.onUnauthorized = null;
  });
  test(
    'JW session expiration preserves platform token and reports reauth',
    () async {
      ApiClient.onUnauthorized = () => fail('教务失效不得触发平台退出');
      final client = ApiClient(
        client: MockClient(
          (_) async => http.Response(
            jsonEncode({
              'success': false,
              'code': 'JW_401_02',
              'message': '教务认证已失效，请重新认证',
            }),
            401,
            headers: {'content-type': 'application/json; charset=utf-8'},
          ),
        ),
      );
      await client.init();
      await client.setToken('platform-token');
      await expectLater(
        client.post('/api/app/schedule/import', body: {}),
        throwsA(
          isA<ApiException>()
              .having((error) => error.code, 'code', 'JW_401_02')
              .having((error) => error.kind, 'kind', ApiErrorKind.unauthorized),
        ),
      );
      expect(client.token, 'platform-token');
    },
  );

  test(
    'QQ login rejection keeps existing session and reports QQ failure',
    () async {
      ApiClient.onUnauthorized = () => fail('QQ 登录失败不得触发平台退出');
      String? clientType;
      final client = ApiClient(
        client: MockClient((request) async {
          clientType = request.headers['x-client-type'];
          return http.Response(
            jsonEncode({
              'success': false,
              'code': 'AUTH_401_03',
              'message': 'QQ 登录失败，请稍后重试',
            }),
            401,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );
      await client.init();
      await client.setToken('existing-platform-token');
      await expectLater(
        client.post('/api/common/auth/qq-login', body: {}),
        throwsA(
          isA<ApiException>()
              .having((error) => error.code, 'code', 'AUTH_401_03')
              .having((error) => error.message, 'message', 'QQ 登录失败，请稍后重试'),
        ),
      );
      expect(client.token, 'existing-platform-token');
      expect(clientType, 'app');
    },
  );

  test(
    'JW credential failure preserves platform token and original code',
    () async {
      ApiClient.onUnauthorized = () => fail('教务登录失败不得触发平台退出');
      final client = ApiClient(
        client: MockClient(
          (_) async => http.Response(
            jsonEncode({
              'success': false,
              'code': 'JW_401_01',
              'message': '账号、密码或验证码错误',
            }),
            401,
            headers: {'content-type': 'application/json; charset=utf-8'},
          ),
        ),
      );
      await client.init();
      await client.setToken('existing-platform-token');
      await expectLater(
        client.post('/api/app/jw/auth', body: {}),
        throwsA(
          isA<ApiException>().having(
            (error) => error.code,
            'code',
            'JW_401_01',
          ),
        ),
      );
      expect(client.token, 'existing-platform-token');
    },
  );

  test('platform 401 still clears only the matching session', () async {
    var logoutCalls = 0;
    ApiClient.onUnauthorized = () => logoutCalls++;
    final client = ApiClient(
      client: MockClient(
        (_) async => http.Response(
          jsonEncode({'success': false, 'code': 'AUTH_401'}),
          401,
        ),
      ),
    );
    await client.init();
    await client.setToken('expired-token');
    await expectLater(client.get('/private'), throwsA(isA<ApiException>()));
    expect(client.token, isNull);
    expect(logoutCalls, 1);
  });

  test(
    'concurrent reads share a request while writes are never replayed',
    () async {
      var gets = 0;
      var writes = 0;
      final gate = Completer<void>();
      final client = ApiClient(
        client: MockClient((request) async {
          if (request.method == 'GET') {
            gets++;
            await gate.future;
          } else {
            writes++;
          }
          return http.Response('{"success":true,"data":{"value":1}}', 200);
        }),
      );
      final first = client.get('/value');
      final second = client.get('/value');
      await Future<void>.delayed(Duration.zero);
      expect(gets, 1);
      gate.complete();
      expect(await first, await second);
      await client.get('/value');
      expect(gets, 2);
      await Future.wait([client.post('/toggle'), client.post('/toggle')]);
      expect(writes, 2);
    },
  );
  test('timeout aborts the underlying request with a typed error', () async {
    final transport = AbortClient();
    final client = ApiClient(
      client: transport,
      requestTimeout: const Duration(milliseconds: 20),
    );
    await expectLater(
      client.get('/silent'),
      throwsA(
        isA<ApiException>().having((e) => e.kind, 'kind', ApiErrorKind.timeout),
      ),
    );
    await Future<void>.delayed(Duration.zero);
    expect(transport.aborted, isTrue);
  });
  test('explicit cancellation aborts without clearing the session', () async {
    final transport = AbortClient();
    final client = ApiClient(client: transport);
    await client.init();
    await client.setToken('retained');
    final cancel = RequestCancellation();
    final result = client.get('/silent', cancellation: cancel);
    cancel.cancel();
    await expectLater(
      result,
      throwsA(
        isA<ApiException>().having(
          (e) => e.kind,
          'kind',
          ApiErrorKind.cancelled,
        ),
      ),
    );
    expect(transport.aborted, isTrue);
    expect(client.token, 'retained');
  });
  test('cancelling SSE during handshake aborts immediately', () async {
    final transport = AbortClient();
    final client = ApiClient(client: transport);
    final subscription = client.streamSse('/stream').listen((_) {});
    await Future<void>.delayed(Duration.zero);
    await subscription.cancel();
    await Future<void>.delayed(Duration.zero);
    expect(transport.aborted, isTrue);
  });
}
