import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../api/api_client.dart';
import '../../api/community_api.dart';
import '../../models/community_models.dart';
import '../../models/user_session.dart';
import '../../services/community_socket.dart';
import '../../theme/app_theme.dart';
import '../../widgets/badge_icon.dart';
import 'post_detail_page.dart';

/// APP-私信对话页 (画布 5:2560)
/// 关联帖子引用卡、左右气泡、图片消息、发送失败重试与真实私信接口。
class ChatDetailPage extends StatefulWidget {
  final String peerId;
  final String peerName;
  final String peerMajor;
  final CommunityPost? relatedPost;

  const ChatDetailPage({
    super.key,
    this.peerId = '',
    this.peerName = '苏晴晴',
    this.peerMajor = '电子信息学院 · 通信工程 · 在线',
    this.relatedPost,
  });

  @override
  State<ChatDetailPage> createState() => _ChatDetailPageState();
}

enum _PendingImageState { uploading, done, failed }

class _PendingChatImage {
  final Uint8List bytes;
  final String name;
  String? fileId;
  String? url;
  _PendingImageState state;

  _PendingChatImage({required this.bytes, required this.name})
    : state = _PendingImageState.uploading;
}

class _ChatDetailPageState extends State<ChatDetailPage> {
  final TextEditingController _textController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  final ImagePicker _picker = ImagePicker();
  final List<_PendingChatImage> _pendingImages = [];

  late List<ChatMessage> _messages;
  bool _loading = false;
  bool _sending = false;
  UserCardBadge? _peerBadge;
  StreamSubscription<DirectMessage>? _socketSubscription;
  StreamSubscription<bool>? _socketStatusSubscription;
  StreamSubscription<ChatTypingEvent>? _typingSubscription;
  StreamSubscription<ChatPresenceEvent>? _presenceSubscription;
  StreamSubscription<ChatReceiptEvent>? _receiptSubscription;
  bool _hasSocketConnected = false;
  bool _peerOnline = false;
  bool _peerTyping = false;
  Timer? _typingResetTimer;
  bool _typingSent = false;

  bool get _isRealChat => widget.peerId.isNotEmpty;

  @override
  void initState() {
    super.initState();
    if (_isRealChat) {
      _messages = [];
      _loadHistory();
      _loadPeerBadge();
      _connectSocket();
    } else {
      _messages = CommunityMockData.getInitialChatMessages();
      _peerBadge = const UserCardBadge(
        badgeCode: 'JW_FIRST_AUTH',
        name: '教务初认证',
      );
    }
  }

  @override
  void dispose() {
    _socketSubscription?.cancel();
    _socketStatusSubscription?.cancel();
    _typingSubscription?.cancel();
    _presenceSubscription?.cancel();
    _receiptSubscription?.cancel();
    _typingResetTimer?.cancel();
    _textController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _connectSocket() {
    final socket = CommunitySocket.shared;
    _socketSubscription = socket.messages.listen((message) {
      if (message.senderId != widget.peerId) return;
      if (_messages.any((m) => m.id == message.id)) return;
      setState(() {
        _messages.add(
          ChatMessage(
            id: message.id,
            isMe: false,
            text: message.content,
            time: _formatTime(message.createdAt),
            imageUrls: message.imageUrls,
          ),
        );
      });
      CommunityApi.markMessagesDelivered([message.id]);
      CommunityApi.markConversationRead(
        widget.peerId,
        messageIds: [message.id],
      );
      _scrollToBottom();
    });
    _typingSubscription = socket.typingEvents.listen((event) {
      if (!mounted || event.fromUserId != widget.peerId) return;
      setState(() => _peerTyping = event.typing);
    });
    _presenceSubscription = socket.presenceEvents.listen((event) {
      if (!mounted || event.userId != widget.peerId) return;
      setState(() => _peerOnline = event.online);
    });
    _receiptSubscription = socket.receiptEvents.listen((event) {
      if (!mounted || event.messageIds.isEmpty && event.type != 'READ') return;
      setState(() {
        _messages = _messages.map((message) {
          if (!message.isMe) return message;
          if (event.messageIds.isNotEmpty &&
              !event.messageIds.contains(message.id))
            return message;
          if (event.type == 'READ') return _copyWithStatus(message, 'READ');
          if (event.type == 'DELIVERED' && message.status != 'READ') {
            return _copyWithStatus(message, 'DELIVERED');
          }
          return message;
        }).toList();
      });
    });
    _socketStatusSubscription = socket.connectionStatus.listen((connected) {
      if (!connected) return;
      // 断线重连后补拉一次历史，补偿断线期间漏掉的消息；首次连接由 init 拉取
      if (_hasSocketConnected && _isRealChat) _loadHistory(silent: true);
      _hasSocketConnected = true;
    });
    socket.connect();
    _refreshPresence();
  }

  Future<void> _refreshPresence() async {
    final presence = await CommunityApi.fetchPresence([widget.peerId]);
    if (!mounted) return;
    setState(() => _peerOnline = presence[widget.peerId] == true);
  }

  ChatMessage _copyWithStatus(ChatMessage message, String status) {
    return ChatMessage(
      id: message.id,
      isMe: message.isMe,
      text: message.text,
      time: message.time,
      imageUrls: message.imageUrls,
      imageNames: message.imageNames,
      fileIds: message.fileIds,
      failed: message.failed,
      status: status,
    );
  }

  void _onInputChanged(String value) {
    if (!_isRealChat || value.trim().isEmpty) {
      _sendTyping(false);
      return;
    }
    _sendTyping(true);
    _typingResetTimer?.cancel();
    _typingResetTimer = Timer(
      const Duration(milliseconds: 1500),
      () => _sendTyping(false),
    );
  }

  void _sendTyping(bool typing) {
    if (!_isRealChat) return;
    if (typing == _typingSent) return;
    _typingSent = typing;
    CommunitySocket.shared.sendTyping(widget.peerId, typing);
  }

  Future<void> _loadHistory({bool silent = false}) async {
    if (!silent) setState(() => _loading = true);
    final history = await CommunityApi.fetchMessages(withUserId: widget.peerId);
    if (!mounted) return;
    final incoming = history.where((m) => m.senderId == widget.peerId).toList();
    final unreadIds = incoming
        .where((m) => m.readAt == null)
        .map((m) => m.id)
        .toList();
    final undeliveredIds = incoming
        .where((m) => m.deliveredAt == null)
        .map((m) => m.id)
        .toList();
    if (undeliveredIds.isNotEmpty)
      CommunityApi.markMessagesDelivered(undeliveredIds);
    CommunityApi.markConversationRead(widget.peerId, messageIds: unreadIds);
    setState(() {
      _loading = false;
      _messages = history
          .map(
            (m) => ChatMessage(
              id: m.id,
              isMe: m.senderId != widget.peerId,
              text: m.content,
              time: _formatTime(m.createdAt),
              imageUrls: m.imageUrls,
              status: m.status,
            ),
          )
          .toList();
    });
    _scrollToBottom();
  }

  Future<void> _loadPeerBadge() async {
    final worn = await CommunityApi.fetchWornBadge(widget.peerId);
    if (!mounted) return;
    setState(() {
      // 未佩戴勋章时不展示任何符号
      _peerBadge = worn == null
          ? null
          : UserCardBadge(badgeCode: worn, name: '佩戴勋章');
    });
  }

  String _formatTime(DateTime? time) {
    if (time == null) return '';
    final now = DateTime.now();
    final diff = now.difference(time);
    if (diff.inMinutes < 1) return '刚刚';
    if (diff.inMinutes < 60) return '${diff.inMinutes}分钟前';
    final h = time.hour.toString().padLeft(2, '0');
    final m = time.minute.toString().padLeft(2, '0');
    if (time.year == now.year &&
        time.month == now.month &&
        time.day == now.day) {
      return '$h:$m';
    }
    return '${time.month}-${time.day.toString().padLeft(2, '0')} $h:$m';
  }

  Future<void> _pickImages() async {
    if (_pendingImages.length >= 9) {
      _toast('最多选择 9 张图片');
      return;
    }
    try {
      final files = await _picker.pickMultiImage(imageQuality: 85);
      for (final file in files.take(9 - _pendingImages.length)) {
        final bytes = await file.readAsBytes();
        final pending = _PendingChatImage(bytes: bytes, name: file.name);
        setState(() => _pendingImages.add(pending));
        _upload(pending);
      }
    } catch (_) {
      _toast('选择图片失败');
    }
  }

  Future<void> _upload(_PendingChatImage pending) async {
    final data = await ApiClient().uploadImage(
      pending.bytes,
      filename: pending.name,
    );
    if (!mounted) return;
    setState(() {
      pending.fileId = data?['fileId']?.toString();
      pending.url = data?['viewPath']?.toString();
      pending.state = data == null
          ? _PendingImageState.failed
          : _PendingImageState.done;
    });
  }

  Future<void> _sendMessage() async {
    final text = _textController.text.trim();
    final ready = _pendingImages.where((p) => p.fileId != null).toList();
    if (_pendingImages.any((p) => p.state == _PendingImageState.uploading)) {
      _toast('图片上传中，请稍候');
      return;
    }
    if (text.isEmpty && ready.isEmpty) return;
    if (_sending) return;

    final fileIds = ready.map((p) => p.fileId!).toList();
    final localUrls = ready
        .map((p) => p.url ?? '')
        .where((e) => e.isNotEmpty)
        .map(
          (url) =>
              url.startsWith('http') ? url : '${ApiClient.defaultBaseUrl}$url',
        )
        .toList();
    final names = ready.map((p) => p.name).toList();

    final message = ChatMessage(
      id: 'local_${DateTime.now().millisecondsSinceEpoch}',
      isMe: true,
      text: text,
      time: '刚刚',
      imageUrls: localUrls,
      imageNames: names,
      fileIds: fileIds,
    );
    _sendTyping(false);
    setState(() {
      _messages.add(message);
      _pendingImages.clear();
      _textController.clear();
      _sending = true;
    });
    _scrollToBottom();

    final ok = await _sendToApi(message);
    if (!mounted) return;
    if (ok && _isRealChat) {
      await _loadHistory(silent: true);
      if (!mounted) return;
    }
    setState(() {
      _sending = false;
      if (!ok) {
        final index = _messages.indexOf(message);
        if (index >= 0) {
          _messages[index] = ChatMessage(
            id: message.id,
            isMe: true,
            text: message.text,
            time: message.time,
            imageUrls: message.imageUrls,
            imageNames: message.imageNames,
            fileIds: message.fileIds,
            failed: true,
          );
        }
      }
    });
  }

  Future<bool> _sendToApi(ChatMessage message) async {
    if (!_isRealChat) {
      try {
        await CommunityApi.sendMessage(
          receiverId: widget.peerName,
          content: message.text,
        );
        return true;
      } catch (_) {
        return true; // mock 会话不阻断 UI
      }
    }
    final result = await CommunityApi.sendDirectMessage(
      receiverId: widget.peerId,
      content: message.text,
      imageFileIds: message.fileIds,
    );
    return result != null;
  }

  Future<void> _retry(ChatMessage message) async {
    setState(() {
      final index = _messages.indexOf(message);
      if (index < 0) return;
      _messages[index] = ChatMessage(
        id: message.id,
        isMe: true,
        text: message.text,
        time: message.time,
        imageUrls: message.imageUrls,
        imageNames: message.imageNames,
        fileIds: message.fileIds,
      );
    });
    final ok = await _sendToApi(message);
    if (!mounted) return;
    if (ok && _isRealChat) {
      await _loadHistory(silent: true);
      if (!mounted) return;
    }
    setState(() {
      if (!ok) {
        final index = _messages.indexOf(message);
        if (index >= 0) {
          _messages[index] = ChatMessage(
            id: message.id,
            isMe: true,
            text: message.text,
            time: message.time,
            imageUrls: message.imageUrls,
            imageNames: message.imageNames,
            fileIds: message.fileIds,
            failed: true,
          );
        }
      }
    });
  }

  void _scrollToBottom() {
    Future.delayed(const Duration(milliseconds: 120), () {
      if (!mounted) return;
      if (_scrollController.hasClients) {
        if (MediaQuery.disableAnimationsOf(context)) {
          _scrollController.jumpTo(_scrollController.position.maxScrollExtent);
          return;
        }
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  void _toast(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  void _openPostDetail() {
    final post = widget.relatedPost;
    if (post == null) return;
    Navigator.of(context)
        .push(MaterialPageRoute(builder: (_) => PostDetailPage(post: post)));
  }

  void _showAttachSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (ctx) => SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.image_outlined, color: AppColors.brand),
              title: const Text(
                '图片',
                style: TextStyle(fontFamily: 'Noto Sans SC'),
              ),
              onTap: () {
                Navigator.of(ctx).pop();
                _pickImages();
              },
            ),
            ListTile(
              leading: const Icon(
                Icons.close_rounded,
                color: Color(0xFF94A3B8),
              ),
              title: const Text(
                '取消',
                style: TextStyle(fontFamily: 'Noto Sans SC'),
              ),
              onTap: () => Navigator.of(ctx).pop(),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(
            Icons.arrow_back_ios_new_rounded,
            size: 20,
            color: Color(0xFF0F172A),
          ),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(
              child: Text(
                widget.peerName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF0F172A),
                ),
              ),
            ),
            if (_peerOnline) ...[
              const SizedBox(width: 5),
              Container(
                width: 7,
                height: 7,
                decoration: const BoxDecoration(
                  color: Color(0xFF10B981),
                  shape: BoxShape.circle,
                ),
              ),
            ],
            if (_peerBadge != null && _peerBadge!.badgeCode.isNotEmpty) ...[
              const SizedBox(width: 5),
              _buildWornBadge(_peerBadge!),
            ],
          ],
        ),
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(
              Icons.more_horiz_rounded,
              size: 22,
              color: Color(0xFF0F172A),
            ),
            onPressed: () => _toast('会话设置与安全保护'),
          ),
        ],
      ),
      body: Column(
        children: [
          if (widget.relatedPost != null) _buildPostReference(),
          Expanded(child: _buildMessageList()),
          if (_pendingImages.isNotEmpty) _buildPendingImages(),
          if (_peerTyping)
            Container(
              width: double.infinity,
              color: Colors.white,
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 0),
              child: const Text(
                '对方正在输入…',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: Color(0xFF64748B),
                ),
              ),
            ),
          _buildInputBar(),
        ],
      ),
    );
  }

  /// 对方佩戴勋章：服务端 iconUrl 优先，本地素材兜底。
  Widget _buildWornBadge(UserCardBadge badge) {
    return BadgeIcon(
      code: badge.badgeCode,
      iconUrl: badge.iconUrl,
      size: 18,
      placeholderSize: 18,
      placeholderColor: const Color(0xFFD97706),
    );
  }

  Widget _buildPostReference() {
    final post = widget.relatedPost!;
    return GestureDetector(
      onTap: _openPostDetail,
      child: Container(
        margin: const EdgeInsets.fromLTRB(16, 10, 16, 0),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xFFE2E8F0)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Text(
                  '来自校园社区的热门帖子',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: AppColors.brand,
                  ),
                ),
                const Spacer(),
                const Text(
                  '查看原帖',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF64748B),
                  ),
                ),
                const Icon(
                  Icons.chevron_right_rounded,
                  size: 14,
                  color: Color(0xFFCBD5E1),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              post.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              post.content,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                height: 1.4,
                color: Color(0xFF64748B),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMessageList() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator(strokeWidth: 2));
    }
    if (_messages.isEmpty) {
      return const Center(
        child: Text(
          '开始和同学聊天吧',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 12,
            color: Color(0xFF64748B),
          ),
        ),
      );
    }
    return ListView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      itemCount: _messages.length + 1,
      itemBuilder: (context, index) {
        if (index == 0) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Center(
              child: Text(
                _messages.first.time,
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: Color(0xFF64748B),
                ),
              ),
            ),
          );
        }
        return _buildMessageItem(_messages[index - 1]);
      },
    );
  }

  Widget _buildMessageItem(ChatMessage msg) {
    final myName = (UserSession.currentUserName ?? '').trim();
    final avatarText = msg.isMe
        ? (myName.isNotEmpty ? myName.substring(0, 1) : '我')
        : (widget.peerName.isNotEmpty ? widget.peerName.substring(0, 1) : '同');
    final avatar = Container(
      width: 34,
      height: 34,
      decoration: BoxDecoration(
        color: msg.isMe ? const Color(0xFFDBEAFE) : const Color(0xFFFFE4E6),
        shape: BoxShape.circle,
      ),
      alignment: Alignment.center,
      child: Text(
        avatarText,
        style: TextStyle(
          fontFamily: 'Noto Sans SC',
          fontSize: 13,
          fontWeight: FontWeight.bold,
          color: msg.isMe ? AppColors.brand : const Color(0xFFE11D48),
        ),
      ),
    );
    final bubble = Container(
      constraints: const BoxConstraints(maxWidth: 258),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
      decoration: BoxDecoration(
        color: msg.isMe ? AppColors.brand : Colors.white,
        borderRadius: BorderRadius.only(
          topLeft: const Radius.circular(14),
          topRight: const Radius.circular(14),
          bottomLeft: Radius.circular(msg.isMe ? 14 : 4),
          bottomRight: Radius.circular(msg.isMe ? 4 : 14),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x080F172A),
            blurRadius: 6,
            offset: Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (msg.imageUrls.isNotEmpty)
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Image.network(
                msg.imageUrls.first,
                width: 180,
                height: 130,
                fit: BoxFit.cover,
                errorBuilder: (_, _, _) => _imageFallback(msg),
              ),
            ),
          if (msg.imageUrls.isEmpty && msg.imageNames.isNotEmpty)
            _imageFallback(msg),
          if (msg.text.isNotEmpty) ...[
            if (msg.imageUrls.isNotEmpty || msg.imageNames.isNotEmpty)
              const SizedBox(height: 6),
            Text(
              msg.text,
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                height: 1.45,
                color: msg.isMe ? Colors.white : const Color(0xFF0F172A),
              ),
            ),
          ],
        ],
      ),
    );

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: msg.isMe
            ? CrossAxisAlignment.end
            : CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: msg.isMe
                ? MainAxisAlignment.end
                : MainAxisAlignment.start,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: msg.isMe
                ? [bubble, const SizedBox(width: 8), avatar]
                : [avatar, const SizedBox(width: 8), bubble],
          ),
          if (msg.failed)
            GestureDetector(
              onTap: () => _retry(msg),
              child: Padding(
                padding: EdgeInsets.only(
                  top: 4,
                  right: msg.isMe ? 42 : 0,
                  left: msg.isMe ? 0 : 42,
                ),
                child: const Text(
                  '发送失败，点击重试',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFFEF4444),
                  ),
                ),
              ),
            )
          else if (msg.isMe &&
              (msg.status == 'DELIVERED' || msg.status == 'READ'))
            Padding(
              padding: const EdgeInsets.only(top: 3, right: 42),
              child: Text(
                msg.status == 'READ' ? '已读' : '已送达',
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: Color(0xFF64748B),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _imageFallback(ChatMessage msg) {
    final name = msg.imageNames.isNotEmpty ? msg.imageNames.first : '图片';
    return Container(
      width: 180,
      height: 90,
      decoration: BoxDecoration(
        color: msg.isMe ? const Color(0x33FFFFFF) : const Color(0xFFF1F5F9),
        borderRadius: BorderRadius.circular(8),
      ),
      alignment: Alignment.center,
      child: Text(
        '[图片] $name',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          fontFamily: 'Noto Sans SC',
          fontSize: 12,
          color: msg.isMe ? Colors.white70 : const Color(0xFF64748B),
        ),
      ),
    );
  }

  Widget _buildPendingImages() {
    return Container(
      width: double.infinity,
      color: Colors.white,
      padding: const EdgeInsets.fromLTRB(12, 6, 12, 0),
      child: Wrap(
        spacing: 6,
        runSpacing: 6,
        children: [
          for (int i = 0; i < _pendingImages.length; i++)
            Stack(
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: Image.memory(
                    _pendingImages[i].bytes,
                    width: 54,
                    height: 54,
                    fit: BoxFit.cover,
                  ),
                ),
                if (_pendingImages[i].state == _PendingImageState.uploading)
                  const Positioned.fill(
                    child: ColoredBox(
                      color: Color(0x66000000),
                      child: Center(
                        child: SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        ),
                      ),
                    ),
                  ),
                if (_pendingImages[i].state == _PendingImageState.failed)
                  Positioned.fill(
                    child: GestureDetector(
                      onTap: () => setState(() {
                        final pending = _pendingImages[i];
                        pending.state = _PendingImageState.uploading;
                        _upload(pending);
                      }),
                      child: const ColoredBox(
                        color: Color(0x99000000),
                        child: Center(
                          child: Text(
                            '重试',
                            style: TextStyle(color: Colors.white, fontSize: 12),
                          ),
                        ),
                      ),
                    ),
                  ),
                Positioned(
                  top: 2,
                  right: 2,
                  child: GestureDetector(
                    onTap: () => setState(() => _pendingImages.removeAt(i)),
                    child: Container(
                      width: 16,
                      height: 16,
                      decoration: const BoxDecoration(
                        color: Color(0x99000000),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.close_rounded,
                        size: 11,
                        color: Colors.white,
                      ),
                    ),
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }

  Widget _buildInputBar() {
    return Container(
      color: Colors.white,
      padding: EdgeInsets.only(
        left: 12,
        right: 12,
        top: 8,
        bottom: MediaQuery.of(context).padding.bottom + 8,
      ),
      child: Row(
        children: [
          Expanded(
            child: Container(
              height: 38,
              decoration: BoxDecoration(
                color: const Color(0xFFF1F5F9),
                borderRadius: BorderRadius.circular(19),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 14),
              alignment: Alignment.centerLeft,
              child: TextField(
                controller: _textController,
                onChanged: _onInputChanged,
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  color: Color(0xFF0F172A),
                ),
                decoration: InputDecoration(
                  hintText: '发送消息...',
                  hintStyle: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 13,
                    color: Color(0xFF64748B),
                  ),
                  // 胶囊容器自带灰底，关掉主题的 filled 白底避免双层底纹
                  filled: false,
                  border: InputBorder.none,
                  isDense: true,
                  contentPadding: EdgeInsets.zero,
                ),
                onSubmitted: (_) => _sendMessage(),
              ),
            ),
          ),
          const SizedBox(width: 8),
          SizedBox(
            height: 34,
            child: ElevatedButton(
              onPressed: _sending ? null : _sendMessage,
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.brand,
                foregroundColor: Colors.white,
                disabledBackgroundColor: const Color(0xFF93C5FD),
                elevation: 0,
                padding: const EdgeInsets.symmetric(horizontal: 14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(17),
                ),
              ),
              child: _sending
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Text(
                      '发送',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
            ),
          ),
          const SizedBox(width: 6),
          GestureDetector(
            onTap: _showAttachSheet,
            child: Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: const Color(0xFFF1F5F9),
                shape: BoxShape.circle,
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              child: const Icon(
                Icons.add_rounded,
                size: 20,
                color: Color(0xFF64748B),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
