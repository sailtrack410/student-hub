import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../../api/p1_api.dart';

import '../../services/ai_chat_controller.dart';
import '../../services/ai_chat_gateway.dart';
import '../../widgets/ai_sources_panel.dart';
import '../../widgets/markdown_text.dart';
import '../schedule/schedule_page.dart';
import '../schedule/grade_query_page.dart';
import '../service/service_hall_page.dart';
import '../service/my_requests_page.dart';
import '../service/work_order_detail_page.dart';
import '../service/service_apply_page.dart';
import 'voice_input_sheet.dart';

class _AiAttachment {
  final String name;
  final Uint8List? bytes;
  final bool isImage;
  final int? fileId;
  final bool uploading;

  const _AiAttachment({
    required this.name,
    this.bytes,
    this.isImage = false,
    this.fileId,
    this.uploading = false,
  });

  _AiAttachment copyWith({int? fileId, bool? uploading}) {
    return _AiAttachment(
      name: name,
      bytes: bytes,
      isImage: isImage,
      fileId: fileId ?? this.fileId,
      uploading: uploading ?? this.uploading,
    );
  }
}

/// APP-AI助手主页 (对应画布 Tab 3，375 × 812)
/// 严格依据 Ardot 设计规范与移动端功能说明 3.21 节实现：
/// - 顶部学长数字人状态与清空会话操作
/// - 首次进入 2×2 场景推荐指令问答卡
/// - 用户与 AI 消息气泡流（支持 Markdown 排版风格、思考状态打字机动效）
/// - 底部输入栏（语音按压模拟、快捷建议词、直通办事与课表）
class AIAssistantPage extends StatefulWidget {
  final ValueChanged<int>? onTabChanged;
  final int currentTabIndex;
  final AiChatGateway gateway;

  const AIAssistantPage({
    super.key,
    this.onTabChanged,
    this.currentTabIndex = 2,
    this.gateway = const AiChatGateway(),
  });

  @override
  State<AIAssistantPage> createState() => _AIAssistantPageState();
}

class _AIAssistantPageState extends State<AIAssistantPage> {
  final TextEditingController _textController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  final FocusNode _focusNode = FocusNode();

  late final AiChatController _chat;
  bool get _isThinking => _chat.isThinking;
  bool get _isSending => _chat.isSending;
  int get _conversationGeneration => _chat.generation;
  List<AiChatMessage> get _messages => _chat.messages;
  int _attachmentGeneration = 0;
  bool _attachmentPicking = false;
  _AiAttachment? _attachment;
  bool _voiceSheetOpen = false;

  final List<Map<String, String>> _recommendCards = [
    {
      'title': '查明天下节课',
      'desc': '智能解析教务系统本周课程',
      'query': '查一下我明天的课程安排与教室地点',
      'icon': 'calendar',
    },
    {
      'title': '宿舍空调报修',
      'desc': '查看报修流程与办理入口',
      'query': '宿舍空调制冷失常怎么申请报修？',
      'icon': 'repair',
    },
    {
      'title': '补办校园一卡通',
      'desc': '挂失、补卡与充值全攻略',
      'query': '校园卡丢失了怎么挂失和补办？',
      'icon': 'card',
    },
    {
      'title': '学期成绩与GPA',
      'desc': '学分绩点与挂科情况查询',
      'query': '查看我本学期的已修学分和平均绩点',
      'icon': 'grade',
    },
  ];

  @override
  void initState() {
    super.initState();
    _chat = AiChatController(widget.gateway)..addListener(_chatChanged);
    _loadRecommendations();
  }

  Future<void> _loadRecommendations() async {
    final remote = await widget.gateway.fetchRecommendations();
    if (!mounted || remote.isEmpty) return;
    setState(() {
      _recommendCards
        ..clear()
        ..addAll(
          remote.map(
            (item) => {
              'title': item['title'] ?? '',
              'desc': item['subtitle'] ?? '',
              'query': item['promptText'] ?? '',
              'icon': _recommendIcon(item['category'] ?? ''),
            },
          ),
        );
    });
  }

  String _recommendIcon(String category) {
    return switch (category) {
      'SCHEDULE' => 'calendar',
      'WORK_ORDER' => 'repair',
      'GUIDE' => 'card',
      _ => 'grade',
    };
  }

  @override
  void dispose() {
    _chat.removeListener(_chatChanged);
    _chat.dispose();
    _textController.dispose();
    _scrollController.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    Future.delayed(const Duration(milliseconds: 100), () {
      if (mounted && _scrollController.hasClients) {
        if (MediaQuery.disableAnimationsOf(context)) {
          _scrollController.jumpTo(_scrollController.position.maxScrollExtent);
          return;
        }
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  Future<void> _openVoiceInput() async {
    if (_voiceSheetOpen) return;
    _voiceSheetOpen = true;
    final text = await VoiceInputSheet.show(context);
    _voiceSheetOpen = false;
    if (!mounted || text == null) return;
    _handleSend(text);
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
              leading: const Icon(
                Icons.image_outlined,
                color: Color(0xFF2563EB),
              ),
              title: const Text(
                '图片',
                style: TextStyle(fontFamily: 'Noto Sans SC'),
              ),
              onTap: () {
                Navigator.of(ctx).pop();
                _pickImageAttachment();
              },
            ),
            ListTile(
              leading: const Icon(
                Icons.description_outlined,
                color: Color(0xFF2563EB),
              ),
              title: const Text(
                '文档',
                style: TextStyle(fontFamily: 'Noto Sans SC'),
              ),
              onTap: () {
                Navigator.of(ctx).pop();
                _pickDocumentAttachment();
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

  Future<void> _pickImageAttachment() => _pickAttachment(isImage: true);

  Future<void> _pickDocumentAttachment() => _pickAttachment(isImage: false);

  Future<void> _pickAttachment({required bool isImage}) async {
    final generation = ++_attachmentGeneration;
    final conversationGeneration = _conversationGeneration;
    bool isCurrent() =>
        mounted &&
        generation == _attachmentGeneration &&
        conversationGeneration == _conversationGeneration;
    // 选择新附件即取代旧操作；迟到的选择、上传和异常都不能改写新状态。
    setState(() {
      _attachmentPicking = true;
      _attachment = null;
    });
    bool uploading = false;
    try {
      final file = await (isImage
          ? widget.gateway.pickImageAttachment()
          : widget.gateway.pickDocumentAttachment());
      if (!isCurrent()) return;
      if (file == null) {
        setState(() => _attachmentPicking = false);
        return;
      }
      setState(() {
        _attachmentPicking = false;
        _attachment = _AiAttachment(
          name: file.name,
          bytes: file.bytes,
          isImage: file.isImage,
          uploading: true,
        );
      });
      uploading = true;
      final uploaded = await widget.gateway.uploadAttachment(file);
      if (!mounted || !isCurrent()) return;
      final fileId = int.tryParse('${uploaded?['fileId']}');
      if (fileId == null || fileId <= 0) {
        setState(() => _attachment = null);
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('附件上传失败，请重试')));
        return;
      }
      setState(() {
        _attachment = _attachment!.copyWith(fileId: fileId, uploading: false);
      });
    } catch (_) {
      if (!mounted || !isCurrent()) return;
      setState(() {
        _attachmentPicking = false;
        _attachment = null;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(uploading ? '附件上传失败，请重试' : '选择附件失败，请重试')),
      );
    }
  }

  void _removeAttachment() {
    _attachmentGeneration++;
    setState(() {
      _attachmentPicking = false;
      _attachment = null;
    });
  }

  void _handleSend([String? presetText]) {
    final query = (presetText ?? _textController.text).trim();
    if (query.isEmpty || _isSending) return;
    final attachment = _attachment;
    if (attachment?.uploading == true) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('附件正在上传，请稍候')));
      return;
    }
    final attachments = attachment?.fileId == null
        ? const <Map<String, dynamic>>[]
        : <Map<String, dynamic>>[
            {
              'type': attachment!.isImage ? 'IMAGE' : 'DOCUMENT',
              'fileId': attachment.fileId,
            },
          ];
    final request = AiChatRequest(
      query,
      attachment == null ? query : '$query\n[附件] ${attachment.name}',
      attachments,
    );
    if (presetText == null) _textController.clear();
    _attachmentGeneration++;
    _attachment = null;
    _submitRequest(request);
  }

  void _chatChanged() {
    if (!mounted) return;
    setState(() {});
    _scrollToBottom();
  }

  Future<void> _submitRequest(
    AiChatRequest request, {
    String? retryMessageId,
  }) => _chat.submit(request, retryMessageId: retryMessageId);

  Future<void> _saveTodo(AiChatMessage msg) async {
    final actions = msg.actionButtons ?? [];
    final index = await showModalBottomSheet<int>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var i = 0; i < actions.length; i++)
              ListTile(
                title: Text(actions[i].label),
                onTap: () => Navigator.of(ctx).pop(i),
              ),
          ],
        ),
      ),
    );
    if (index == null) return;
    try {
      await P1Api.addTodo(
        msg.serverMessageId!,
        actions[index].sourceIndex ?? index,
      );
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('已加入AI待办')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('$e')));
      }
    }
  }

  void _handleActionButton(AiChatAction action) {
    final page = switch (action.kind) {
      AiActionKind.schedule => const SchedulePage(),
      AiActionKind.grades => const GradeQueryPage(),
      AiActionKind.services => const ServiceHallPage(showBack: true),
      AiActionKind.requestList => const MyRequestsPage(),
      AiActionKind.requestDetail when action.targetId != null =>
        WorkOrderDetailPage(requestId: action.targetId!),
      AiActionKind.serviceApply when action.targetId != null =>
        ServiceApplyPage(itemId: action.targetId!, itemName: action.label),
      _ => null,
    };
    if (page != null) {
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => page));
    } else if (action.kind == AiActionKind.prompt) {
      _handleSend(action.label);
    } else {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('该入口暂未提供，请从服务大厅查看')));
    }
  }

  void _clearChat() {
    if (_messages.length <= 1 &&
        !_isSending &&
        !_isThinking &&
        !_attachmentPicking &&
        _attachment == null) {
      setState(() {
        _attachmentGeneration++;
        _attachmentPicking = false;
        _attachment = null;
        _chat.reset();
      });
      return;
    }
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text(
          '开始新对话',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
        ),
        content: const Text('将开始独立的新对话，历史内容不会作为新对话的上下文。此操作不会删除服务端历史记录。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('取消', style: TextStyle(color: Color(0xFF64748B))),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.of(ctx).pop();
              setState(() {
                _attachmentGeneration++;
                _attachmentPicking = false;
                _attachment = null;
                _chat.reset();
              });
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF2563EB),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
              ),
            ),
            child: const Text('开始新对话', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final hasOnlyWelcome = _messages.length <= 1;

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF2563EB), Color(0xFF38BDF8)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(16),
              ),
              child: const Icon(
                Icons.smart_toy_rounded,
                color: Colors.white,
                size: 18,
              ),
            ),
            const SizedBox(width: 8),
            const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  '绵宝AI',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                ),
                Text(
                  '课程 · 办事 · 校园问答',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF16A34A),
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ],
            ),
          ],
        ),
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(
              Icons.add_comment_outlined,
              color: Color(0xFF64748B),
              size: 20,
            ),
            tooltip: '开始新对话',
            onPressed: _clearChat,
          ),
        ],
      ),
      body: SizedBox.expand(
        child: SafeArea(
          bottom: widget.onTabChanged == null,
          child: Column(
            children: [
              // 1. 聊天气泡列表
              Expanded(
                child: ListView(
                  controller: _scrollController,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 12,
                  ),
                  children: [
                    // 首次或空态展示 2×2 场景推荐问答
                    if (hasOnlyWelcome) ...[
                      _buildWelcomeHero(),
                      const SizedBox(height: 16),
                      _buildRecommendGrid(),
                      const SizedBox(height: 20),
                    ],

                    // 消息气泡流
                    ..._messages.map((msg) => _buildMessageBubble(msg)),

                    // 思考中打字机动画态
                    if (_isThinking) _buildThinkingBubble(),

                    const SizedBox(height: 12),
                  ],
                ),
              ),

              // 2. 快捷问答胶囊栏（仅首次欢迎态展示，进入多轮对话后收起释放阅读高度）
              if (hasOnlyWelcome) _buildQuickPromptBar(),

              // 3. 底部输入栏
              _buildInputBar(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildWelcomeHero() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x0A0F172A),
            offset: Offset(0, 4),
            blurRadius: 12,
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Image.asset(
            'assets/images/home/campus-ai-companion.png',
            width: 52,
            height: 52,
            fit: BoxFit.contain,
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '随时问我校园大小事',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                ),
                SizedBox(height: 4),
                Text(
                  '搭载学校综合管理规章与后勤办事全量知识库，支持自然语言查课表、查成绩与办事一键导流。',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF64748B),
                    height: 1.4,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildRecommendGrid() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '常见问答场景推荐',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 12,
            fontWeight: FontWeight.bold,
            color: Color(0xFF475569),
          ),
        ),
        const SizedBox(height: 10),
        LayoutBuilder(
          builder: (context, constraints) {
            final scale = MediaQuery.textScalerOf(context).scale(14) / 14;
            final columns = constraints.maxWidth >= 280 * scale ? 2 : 1;
            final cardWidth =
                (constraints.maxWidth - (columns - 1) * 10) / columns;
            return Wrap(
              spacing: 10,
              runSpacing: 10,
              children: List<Widget>.generate(_recommendCards.length, (index) {
                final card = _recommendCards[index];
                IconData icon = Icons.help_outline_rounded;
                Color iconColor = const Color(0xFF2563EB);
                Color bgColor = const Color(0xFFEFF6FF);

                if (card['icon'] == 'calendar') {
                  icon = Icons.calendar_month_rounded;
                  iconColor = const Color(0xFF2563EB);
                  bgColor = const Color(0xFFEFF6FF);
                } else if (card['icon'] == 'repair') {
                  icon = Icons.handyman_rounded;
                  iconColor = const Color(0xFFD97706);
                  bgColor = const Color(0xFFFEF3C7);
                } else if (card['icon'] == 'card') {
                  icon = Icons.credit_card_rounded;
                  iconColor = const Color(0xFF0D9488);
                  bgColor = const Color(0xFFCCFBF1);
                } else if (card['icon'] == 'grade') {
                  icon = Icons.military_tech_rounded;
                  iconColor = const Color(0xFF9333EA);
                  bgColor = const Color(0xFFF3E8FF);
                }

                return SizedBox(
                  width: cardWidth,
                  child: Semantics(
                    button: true,
                    child: InkWell(
                      onTap: () => _handleSend(card['query']),
                      borderRadius: BorderRadius.circular(12),
                      child: Container(
                        constraints: const BoxConstraints(minHeight: 96),
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: const Color(0xFFE2E8F0)),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Row(
                              children: [
                                Container(
                                  width: 24,
                                  height: 24,
                                  decoration: BoxDecoration(
                                    color: bgColor,
                                    borderRadius: BorderRadius.circular(6),
                                  ),
                                  child: Icon(icon, size: 14, color: iconColor),
                                ),
                                const SizedBox(width: 6),
                                Expanded(
                                  child: Text(
                                    card['title']!,
                                    style: const TextStyle(
                                      fontFamily: 'Noto Sans SC',
                                      fontSize: 12,
                                      fontWeight: FontWeight.bold,
                                      color: Color(0xFF0F172A),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 6),
                            Text(
                              card['desc']!,
                              style: const TextStyle(
                                fontFamily: 'Noto Sans SC',
                                fontSize: 12,
                                color: Color(0xFF64748B),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                );
              }),
            );
          },
        ),
      ],
    );
  }

  Widget _buildMessageBubble(AiChatMessage msg) {
    if (msg.isUser) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 14),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.end,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Flexible(
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 10,
                ),
                decoration: const BoxDecoration(
                  color: Color(0xFF2563EB),
                  borderRadius: BorderRadius.only(
                    topLeft: Radius.circular(16),
                    topRight: Radius.circular(4),
                    bottomLeft: Radius.circular(16),
                    bottomRight: Radius.circular(16),
                  ),
                ),
                child: MarkdownText(data: msg.text, isUser: true),
              ),
            ),
            const SizedBox(width: 8),
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                color: const Color(0xFFEFF6FF),
                shape: BoxShape.circle,
                border: Border.all(color: const Color(0xFFBFDBFE)),
              ),
              child: const Icon(
                Icons.person_rounded,
                size: 18,
                color: Color(0xFF2563EB),
              ),
            ),
          ],
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [Color(0xFF2563EB), Color(0xFF38BDF8)],
              ),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.smart_toy_rounded,
              size: 16,
              color: Colors.white,
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: const BorderRadius.only(
                      topLeft: Radius.circular(4),
                      topRight: Radius.circular(16),
                      bottomLeft: Radius.circular(16),
                      bottomRight: Radius.circular(16),
                    ),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: MarkdownText(data: msg.text),
                ),

                // 知识库引用来源
                if (msg.sources.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  AiSourcesPanel(sources: msg.sources),
                ],

                // 快捷操作按钮行
                if (msg.statusText != null) ...[
                  const SizedBox(height: 8),
                  Text(
                    msg.statusText!,
                    style: const TextStyle(
                      fontSize: 13,
                      color: Color(0xFFB45309),
                    ),
                  ),
                ],
                if (msg.retryRequest != null) ...[
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    onPressed: _isSending
                        ? null
                        : () => _submitRequest(
                            msg.resendAsNew
                                ? AiChatRequest(
                                    msg.retryRequest!.query,
                                    msg.retryRequest!.displayText,
                                    msg.retryRequest!.attachments,
                                  )
                                : msg.retryRequest!,
                            retryMessageId: msg.id,
                          ),
                    icon: const Icon(Icons.refresh_rounded, size: 18),
                    label: Text(msg.resendAsNew ? '作为新问题发送' : '重试此问题'),
                  ),
                ],
                if (msg.actionButtons != null &&
                    msg.actionButtons!.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  if (!msg.isUser &&
                      !msg.isStreaming &&
                      msg.serverMessageId != null)
                    TextButton.icon(
                      onPressed: () => _saveTodo(msg),
                      icon: const Icon(Icons.playlist_add),
                      label: const Text('加入AI待办'),
                    ),
                  Wrap(
                    spacing: 8,
                    runSpacing: 6,
                    children: msg.actionButtons!.map((btn) {
                      return GestureDetector(
                        onTap: () => _handleActionButton(btn),
                        child: Container(
                          constraints: const BoxConstraints(minHeight: 48),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 5,
                          ),
                          decoration: BoxDecoration(
                            color: const Color(0xFFEFF6FF),
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(color: const Color(0xFFBFDBFE)),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                btn.label,
                                style: const TextStyle(
                                  fontFamily: 'Noto Sans SC',
                                  fontSize: 12,
                                  color: Color(0xFF2563EB),
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              const SizedBox(width: 2),
                              const Icon(
                                Icons.arrow_forward_ios_rounded,
                                size: 10,
                                color: Color(0xFF2563EB),
                              ),
                            ],
                          ),
                        ),
                      );
                    }).toList(),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildThinkingBubble() {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: const BoxDecoration(
              color: Color(0xFF2563EB),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.smart_toy_rounded,
              size: 16,
              color: Colors.white,
            ),
          ),
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: const Color(0xFFE2E8F0)),
            ),
            child: const Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                SizedBox(
                  width: 12,
                  height: 12,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: Color(0xFF2563EB),
                  ),
                ),
                SizedBox(width: 8),
                Text(
                  '学长正在检索绵城校园知识库...',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF64748B),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildQuickPromptBar() {
    final quickPrompts = ['查今天课表', '宿舍水压太小', '教室投影故障', '教务密码忘了'];
    return Container(
      height: 32,
      margin: const EdgeInsets.only(bottom: 6),
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        itemCount: quickPrompts.length,
        separatorBuilder: (_, _) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          final prompt = quickPrompts[index];
          return GestureDetector(
            onTap: () => _handleSend(prompt),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFFCBD5E1)),
              ),
              alignment: Alignment.center,
              child: Text(
                prompt,
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: Color(0xFF475569),
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildInputBar() {
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: Color(0xFFE2E8F0))),
      ),
      padding: EdgeInsets.only(left: 12, right: 12, top: 8, bottom: 8),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (_attachment != null) _buildAttachmentChip(_attachment!),
          Row(
            children: [
              IconButton(
                tooltip: '添加附件',
                onPressed: _showAttachSheet,
                constraints: const BoxConstraints.tightFor(
                  width: 44,
                  height: 40,
                ),
                padding: const EdgeInsets.all(2),
                icon: Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    color: const Color(0xFFF1F5F9),
                    shape: BoxShape.circle,
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: const Icon(
                    Icons.add_rounded,
                    semanticLabel: '添加附件',
                    size: 20,
                    color: Color(0xFF64748B),
                  ),
                ),
              ),
              const SizedBox(width: 7),
              Expanded(
                child: Container(
                  height: 40,
                  decoration: BoxDecoration(
                    color: const Color(0xFFF1F5F9),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  child: TextField(
                    controller: _textController,
                    focusNode: _focusNode,
                    textInputAction: TextInputAction.send,
                    onSubmitted: (_) => _handleSend(),
                    style: const TextStyle(
                      fontSize: 13,
                      color: Color(0xFF0F172A),
                    ),
                    decoration: const InputDecoration(
                      hintText: '发消息或上传文档、图片...',
                      hintStyle: TextStyle(
                        fontSize: 12,
                        color: Color(0xFF64748B),
                      ),
                      // 胶囊容器自带灰底，关掉主题的 filled 白底避免双层底纹
                      filled: false,
                      border: InputBorder.none,
                      isDense: true,
                      contentPadding: EdgeInsets.symmetric(vertical: 10),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 4),
              IconButton(
                tooltip: '语音输入',
                onPressed: _isSending ? null : _openVoiceInput,
                constraints: const BoxConstraints.tightFor(
                  width: 44,
                  height: 40,
                ),
                padding: const EdgeInsets.all(3),
                icon: Container(
                  width: 34,
                  height: 34,
                  alignment: Alignment.center,
                  child: const Icon(
                    Icons.mic_none_rounded,
                    semanticLabel: '语音输入',
                    size: 21,
                    color: Color(0xFF64748B),
                  ),
                ),
              ),
              const SizedBox(width: 2),
              IconButton(
                tooltip: '发送消息',
                onPressed: _isSending || _attachment?.uploading == true
                    ? null
                    : () => _handleSend(),
                constraints: const BoxConstraints.tightFor(
                  width: 44,
                  height: 40,
                ),
                padding: const EdgeInsets.all(1),
                icon: Container(
                  width: 38,
                  height: 38,
                  decoration: BoxDecoration(
                    color: _isSending || _attachment?.uploading == true
                        ? const Color(0xFF94A3B8)
                        : const Color(0xFF2563EB),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.arrow_upward_rounded,
                    semanticLabel: '发送消息',
                    color: Colors.white,
                    size: 20,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildAttachmentChip(_AiAttachment attachment) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 8),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: attachment.isImage
                  ? const Color(0xFFEFF6FF)
                  : const Color(0xFFECFDF5),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                color: attachment.isImage
                    ? const Color(0xFFBFDBFE)
                    : const Color(0xFFA7F3D0),
              ),
            ),
            child: attachment.isImage
                ? ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: Image.memory(
                      attachment.bytes!,
                      width: 36,
                      height: 36,
                      fit: BoxFit.cover,
                    ),
                  )
                : const Icon(
                    Icons.description_rounded,
                    size: 28,
                    color: Color(0xFF059669),
                  ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  attachment.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF0F172A),
                  ),
                ),
                Text(
                  attachment.uploading ? '附件上传中…' : '待发送 · 仅用于本次提问',
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF64748B),
                  ),
                ),
              ],
            ),
          ),
          IconButton(
            tooltip: '移除附件',
            onPressed: _removeAttachment,
            constraints: const BoxConstraints.tightFor(width: 48, height: 48),
            icon: const Icon(
              Icons.close_rounded,
              semanticLabel: '移除附件',
              size: 18,
              color: Color(0xFF94A3B8),
            ),
          ),
        ],
      ),
    );
  }
}
