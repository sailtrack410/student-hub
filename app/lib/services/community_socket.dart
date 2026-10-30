import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../api/api_client.dart';
import '../models/community_models.dart';

/// 社区私信 WebSocket 客户端。
/// 后端 `/ws/community?token=` 已按 userId 推送 DirectMessageResponse，
/// 这里负责连接、重连、JSON 解析与事件分发。
class ChatTypingEvent {
  final String fromUserId;
  final bool typing;
  const ChatTypingEvent(this.fromUserId, this.typing);
}

class ChatPresenceEvent {
  final String userId;
  final bool online;
  const ChatPresenceEvent(this.userId, this.online);
}

class AppNotificationEvent {
  final String notificationId;
  final String type;
  final String title;
  final String content;
  final String? relatedType;
  final String? relatedId;
  const AppNotificationEvent({
    required this.notificationId,
    required this.type,
    required this.title,
    required this.content,
    this.relatedType,
    this.relatedId,
  });
}

class ChatReceiptEvent {
  final String type;
  final String userId;
  final List<String> messageIds;
  const ChatReceiptEvent({
    required this.type,
    required this.userId,
    this.messageIds = const [],
  });
}

class CommunitySocket {
  CommunitySocket._() : _channelFactory = WebSocketChannel.connect;

  CommunitySocket._withChannelFactory(this._channelFactory);

  @visibleForTesting
  factory CommunitySocket.forTesting({
    required WebSocketChannel Function(Uri) channelFactory,
  }) => CommunitySocket._withChannelFactory(channelFactory);

  final WebSocketChannel Function(Uri) _channelFactory;

  /// App 生命周期内共享一条连接，避免私聊页与首页各建一条
  static final CommunitySocket shared = CommunitySocket._();

  final StreamController<DirectMessage> _messages =
      StreamController<DirectMessage>.broadcast();
  final StreamController<bool> _status = StreamController<bool>.broadcast();
  final StreamController<ChatTypingEvent> _typing =
      StreamController<ChatTypingEvent>.broadcast();
  final StreamController<ChatPresenceEvent> _presence =
      StreamController<ChatPresenceEvent>.broadcast();
  final StreamController<ChatReceiptEvent> _receipts =
      StreamController<ChatReceiptEvent>.broadcast();
  final StreamController<AppNotificationEvent> _notifications =
      StreamController<AppNotificationEvent>.broadcast();
  WebSocketChannel? _channel;
  StreamSubscription? _subscription;
  Timer? _reconnectTimer;
  bool _disposed = false;
  int _retry = 0;
  bool _connected = false;
  bool _connecting = false;
  int _generation = 0;
  String? _connectionToken;

  Stream<DirectMessage> get messages => _messages.stream;
  Stream<bool> get connectionStatus => _status.stream;
  Stream<ChatTypingEvent> get typingEvents => _typing.stream;
  Stream<ChatPresenceEvent> get presenceEvents => _presence.stream;
  Stream<ChatReceiptEvent> get receiptEvents => _receipts.stream;
  Stream<AppNotificationEvent> get notificationEvents => _notifications.stream;
  bool get isConnected =>
      _connected && !_disposed && _connectionToken == ApiClient().token;

  static String get _wsBase {
    final base = ApiClient.defaultBaseUrl;
    if (base.startsWith('https://')) {
      return base.replaceFirst('https://', 'wss://');
    }
    return base.replaceFirst('http://', 'ws://');
  }

  Future<void> connect() async {
    if (_disposed) return;
    final token = ApiClient().token;
    if (token == null || token.isEmpty) return;
    if (_connectionToken == token &&
        (_connecting || (_connected && _channel != null))) {
      return;
    }
    final generation = ++_generation;
    _connectionToken = token;
    _connecting = true;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _setConnected(false);
    WebSocketChannel? channel;
    try {
      await _closeChannel();
      if (!_isCurrent(generation, token)) return;
      final uri = Uri.parse(
        '$_wsBase/ws/community?token=${Uri.encodeQueryComponent(token)}',
      );
      channel = _channelFactory(uri);
      await channel.ready;
      if (!_isCurrent(generation, token)) {
        await channel.sink.close();
        return;
      }
      _channel = channel;
      _retry = 0;
      _setConnected(true);
      _subscription = channel.stream.listen(
        (raw) {
          if (_isCurrent(generation, token)) _handleData(raw);
        },
        onError: (_) => _onDisconnected(generation, token),
        onDone: () => _onDisconnected(generation, token),
        cancelOnError: true,
      );
    } catch (_) {
      if (channel != null && !identical(channel, _channel)) {
        try {
          await channel.sink.close();
        } catch (_) {}
      }
      _onDisconnected(generation, token);
    } finally {
      if (generation == _generation) _connecting = false;
    }
  }

  bool _isCurrent(int generation, String token) =>
      !_disposed && generation == _generation && ApiClient().token == token;

  void _handleData(dynamic raw) {
    if (raw is! String) return;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic>) return;
      final type = decoded['type']?.toString();
      final data = decoded['data'];
      if (type == null) {
        final message = DirectMessage.fromJson(decoded);
        if (message.id.isNotEmpty && !_messages.isClosed)
          _messages.add(message);
        return;
      }
      if (data is! Map<String, dynamic>) return;
      switch (type) {
        case 'MESSAGE':
          final message = DirectMessage.fromJson(data);
          if (message.id.isNotEmpty && !_messages.isClosed)
            _messages.add(message);
        case 'TYPING':
          if (!_typing.isClosed) {
            _typing.add(
              ChatTypingEvent(
                data['fromUserId']?.toString() ?? '',
                data['typing'] == true,
              ),
            );
          }
        case 'PRESENCE':
          if (!_presence.isClosed) {
            _presence.add(
              ChatPresenceEvent(
                data['userId']?.toString() ?? '',
                data['online'] == true,
              ),
            );
          }
        case 'NOTIFICATION':
          if (!_notifications.isClosed) {
            _notifications.add(
              AppNotificationEvent(
                notificationId: data['notificationId']?.toString() ?? '',
                type: data['notificationType']?.toString() ?? '',
                title: data['title']?.toString() ?? '新通知',
                content: data['content']?.toString() ?? '',
                relatedType: data['relatedType']?.toString(),
                relatedId: data['relatedId']?.toString(),
              ),
            );
          }
        case 'DELIVERED':
        case 'READ':
          if (!_receipts.isClosed) {
            final ids = (data['messageIds'] as List? ?? const [])
                .map((e) => e.toString())
                .toList();
            _receipts.add(
              ChatReceiptEvent(
                type: type,
                userId: (data['readerId'] ?? data['senderId'] ?? '').toString(),
                messageIds: ids,
              ),
            );
          }
      }
    } catch (_) {
      // 忽略非私信或格式异常帧
    }
  }

  void _onDisconnected(int generation, String token) {
    if (!_isCurrent(generation, token)) return;
    _setConnected(false);
    _scheduleReconnect(generation, token);
  }

  void _scheduleReconnect(int generation, String token) {
    _reconnectTimer?.cancel();
    final seconds = (1 << _retry).clamp(1, 30);
    if (_retry < 5) _retry++;
    _reconnectTimer = Timer(Duration(seconds: seconds), () {
      if (_isCurrent(generation, token)) unawaited(connect());
    });
  }

  void _setConnected(bool value) {
    if (_connected == value) return;
    _connected = value;
    if (!_status.isClosed) _status.add(value);
  }

  void sendTyping(String receiverId, bool typing) {
    final channel = _channel;
    if (channel == null || !isConnected || receiverId.isEmpty) return;
    try {
      channel.sink.add(
        jsonEncode({
          'type': 'TYPING',
          'data': {'receiverId': receiverId, 'typing': typing},
        }),
      );
    } catch (_) {}
  }

  void sendPing() {
    if (!isConnected) return;
    try {
      _channel?.sink.add(
        jsonEncode({'type': 'PING', 'data': <String, dynamic>{}}),
      );
    } catch (_) {}
  }

  Future<void> _closeChannel() async {
    // 先分离旧对象，异步关闭完成后不会误清刚建立的新账号连接。
    final subscription = _subscription;
    final channel = _channel;
    _subscription = null;
    _channel = null;
    try {
      await subscription?.cancel();
    } catch (_) {}
    try {
      await channel?.sink.close();
    } catch (_) {}
  }

  /// 主动断开（如退出登录），保留单例以便重新登录后再次 connect
  Future<void> disconnect() async {
    // 在首次 await 前作废旧握手、监听回调与重连计时器。
    _generation++;
    _connectionToken = null;
    _connecting = false;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _setConnected(false);
    _retry = 0;
    await _closeChannel();
  }

  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    await disconnect();
    await _messages.close();
    await _status.close();
    await _typing.close();
    await _presence.close();
    await _receipts.close();
    await _notifications.close();
  }
}
