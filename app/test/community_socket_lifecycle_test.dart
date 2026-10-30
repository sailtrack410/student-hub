import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/services/community_socket.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

class _ControlledSink implements WebSocketSink {
  final sent = <dynamic>[];
  final _done = Completer<void>();
  int closeCalls = 0;

  @override
  void add(dynamic data) => sent.add(data);

  @override
  void addError(Object error, [StackTrace? stackTrace]) {}

  @override
  Future<void> addStream(Stream<dynamic> stream) async {
    await for (final value in stream) {
      add(value);
    }
  }

  @override
  Future<void> close([int? closeCode, String? closeReason]) async {
    closeCalls++;
    if (!_done.isCompleted) _done.complete();
  }

  @override
  Future<void> get done => _done.future;
}

class _ControlledChannel implements WebSocketChannel {
  _ControlledChannel({Completer<void>? cancelGate})
    : incoming = StreamController<dynamic>(
        sync: true,
        onCancel: () => cancelGate?.future,
      );

  final handshake = Completer<void>();
  final StreamController<dynamic> incoming;

  @override
  final _ControlledSink sink = _ControlledSink();

  @override
  Future<void> get ready => handshake.future;

  @override
  Stream<dynamic> get stream => incoming.stream;

  @override
  String? get protocol => null;

  @override
  int? get closeCode => null;

  @override
  String? get closeReason => null;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<void> _flush() => Future<void>.delayed(Duration.zero);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late ApiClient client;
  late CommunitySocket socket;
  late List<_ControlledChannel> channels;
  late List<Uri> requests;
  late List<Completer<void>> cancellationGates;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    client = ApiClient();
    await client.init();
    await client.setToken('token-a');
    channels = [];
    requests = [];
    cancellationGates = [];
    socket = CommunitySocket.forTesting(
      channelFactory: (uri) {
        requests.add(uri);
        final channel = _ControlledChannel(
          cancelGate: cancellationGates.isEmpty
              ? null
              : cancellationGates.removeAt(0),
        );
        channels.add(channel);
        return channel;
      },
    );
  });

  tearDown(() async {
    for (final gate in cancellationGates) {
      if (!gate.isCompleted) gate.complete();
    }
    for (final channel in channels) {
      if (!channel.handshake.isCompleted) channel.handshake.complete();
    }
    await _flush();
    await socket.dispose();
    for (final channel in channels) {
      unawaited(channel.incoming.close());
    }
    await client.clearToken();
  });

  test('退出登录后迟到的握手不会恢复旧连接或转发旧消息', () async {
    final statuses = <bool>[];
    final messages = <String>[];
    final statusSubscription = socket.connectionStatus.listen(statuses.add);
    final messageSubscription = socket.messages.listen(
      (message) => messages.add(message.id),
    );
    final connecting = socket.connect();
    await _flush();
    expect(channels, hasLength(1));

    await socket.disconnect();
    await client.clearToken();
    channels.single.handshake.complete();
    await connecting;
    channels.single.incoming.add(
      jsonEncode({'id': 'old-message', 'content': 'old'}),
    );
    await _flush();

    expect(socket.isConnected, isFalse);
    expect(statuses, isEmpty);
    expect(messages, isEmpty);
    expect(channels.single.sink.closeCalls, 1);
    await statusSubscription.cancel();
    await messageSubscription.cancel();
  });

  test('新账号可以立即连接，旧握手迟到不会替换新连接', () async {
    final oldAttempt = socket.connect();
    await _flush();
    await socket.disconnect();
    await client.setToken('token-b');
    final newAttempt = socket.connect();
    await _flush();
    expect(channels, hasLength(2));
    expect(requests[1].queryParameters['token'], 'token-b');

    channels[1].handshake.complete();
    await newAttempt;
    channels[0].handshake.complete();
    await oldAttempt;
    socket.sendPing();

    expect(socket.isConnected, isTrue);
    expect(channels[0].sink.closeCalls, 1);
    expect(channels[0].sink.sent, isEmpty);
    expect(channels[1].sink.sent.single, contains('PING'));
  });

  test('旧握手失败不会释放新握手的连接锁或清除新状态', () async {
    final oldAttempt = socket.connect();
    await _flush();
    await socket.disconnect();
    await client.setToken('token-b');
    final newAttempt = socket.connect();
    await _flush();
    expect(channels, hasLength(2));

    channels[0].handshake.completeError(StateError('old handshake failed'));
    await oldAttempt;
    await socket.connect();
    expect(channels, hasLength(2));
    channels[1].handshake.complete();
    await newAttempt;
    expect(socket.isConnected, isTrue);
  });

  test('凭证改变后旧连接不会收发，新账号不受旧连接阻塞', () async {
    final messages = <String>[];
    final subscription = socket.messages.listen(
      (message) => messages.add(message.id),
    );
    final firstAttempt = socket.connect();
    await _flush();
    channels.single.handshake.complete();
    await firstAttempt;
    await client.setToken('token-b');

    socket.sendPing();
    socket.sendTyping('peer', true);
    channels[0].incoming.add(
      jsonEncode({'id': 'old-message', 'content': 'old'}),
    );
    await _flush();
    expect(messages, isEmpty);
    expect(channels[0].sink.sent, isEmpty);

    final secondAttempt = socket.connect();
    await _flush();
    expect(channels, hasLength(2));
    channels[1].handshake.complete();
    await secondAttempt;
    channels[1].incoming.add(
      jsonEncode({'id': 'new-message', 'content': 'new'}),
    );
    await _flush();
    expect(messages, ['new-message']);
    await subscription.cancel();
  });

  test('旧连接的异步关闭完成后不会关闭或重置新连接', () async {
    final cancelGate = Completer<void>();
    cancellationGates.add(cancelGate);
    addTearDown(() {
      if (!cancelGate.isCompleted) cancelGate.complete();
    });
    final firstAttempt = socket.connect();
    await _flush();
    channels.single.handshake.complete();
    await firstAttempt;
    final closingOld = socket.disconnect();
    await _flush();
    await client.setToken('token-b');
    final secondAttempt = socket.connect();
    await _flush();
    expect(channels, hasLength(2));
    channels[1].handshake.complete();
    await secondAttempt;

    cancelGate.complete();
    await closingOld;
    socket.sendPing();
    expect(socket.isConnected, isTrue);
    expect(channels[0].sink.closeCalls, 1);
    expect(channels[1].sink.closeCalls, 0);
    expect(channels[1].sink.sent.single, contains('PING'));
  });

  test('当前握手失败后会按退避计时重新连接', () async {
    final connecting = socket.connect();
    await _flush();
    channels.single.handshake.completeError(StateError('network unavailable'));
    await connecting;
    expect(socket.isConnected, isFalse);

    await Future<void>.delayed(const Duration(milliseconds: 1050));
    expect(channels, hasLength(2));
    channels[1].handshake.complete();
    await _flush();
    expect(socket.isConnected, isTrue);
  });

  test('主动断开取消已排定的重连，释放后也不会重新启用实例', () async {
    final connecting = socket.connect();
    await _flush();
    channels.single.handshake.completeError(StateError('network unavailable'));
    await connecting;
    await socket.disconnect();

    await Future<void>.delayed(const Duration(milliseconds: 1050));
    expect(channels, hasLength(1));
    await socket.dispose();
    await socket.disconnect();
    await socket.connect();
    expect(channels, hasLength(1));
    expect(socket.isConnected, isFalse);
  });
}
