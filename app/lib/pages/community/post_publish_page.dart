import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../api/api_client.dart';
import '../../api/community_api.dart';
import '../../models/community_models.dart';
import '../../api/location_api.dart';
import 'location_picker_page.dart';
import '../../theme/app_theme.dart';

/// APP-发布帖子（画布 5:560 的动态模式 + 文章图文混排扩展）
class PostPublishPage extends StatefulWidget {
  const PostPublishPage({super.key});

  @override
  State<PostPublishPage> createState() => _PostPublishPageState();
}

enum _UploadState { uploading, done, failed }

class _PickedImage {
  final Uint8List bytes;
  final String name;
  _UploadState state;
  String? fileId;
  String? url;

  _PickedImage({required this.bytes, required this.name})
    : state = _UploadState.uploading;
}

class _ArticleBlock {
  final String type; // TEXT / IMAGE
  TextEditingController? textController;
  FocusNode? focusNode;
  _PickedImage? image;

  _ArticleBlock.text()
    : type = 'TEXT',
      textController = TextEditingController(),
      focusNode = FocusNode();

  _ArticleBlock.image(_PickedImage img) : type = 'IMAGE', image = img;

  void dispose() {
    textController?.dispose();
    focusNode?.dispose();
  }
}

class _PostPublishPageState extends State<PostPublishPage> {
  final ImagePicker _picker = ImagePicker();
  final TextEditingController _titleController = TextEditingController();
  final TextEditingController _contentController = TextEditingController();

  int _modeIndex = 0; // 0 动态 / 1 文章
  final List<_PickedImage> _images = [];
  final List<_ArticleBlock> _blocks = [_ArticleBlock.text()];
  int _activeBlockIndex = 0;
  bool _submitting = false;
  PostLocation? _selectedLocation;

  List<CommunitySection> _sections = const [];
  List<String> get _partitions => _sections.isEmpty
      ? const ['校园日常', '二手闲置', '学习互助', '失物招领']
      : _sections.map((s) => s.name).toList();
  int _selectedPartitionIndex = 0;

  @override
  void initState() {
    super.initState();
    _loadSections();
  }

  Future<void> _loadSections() async {
    final sections = await CommunityApi.fetchSections();
    if (!mounted) return;
    setState(() {
      _sections = sections;
      if (_selectedPartitionIndex >= _partitions.length) {
        _selectedPartitionIndex = 0;
      }
    });
  }

  final List<String> _topics = ['校园生活', '期末备战', '食堂测评'];
  final Set<String> _selectedTopics = {'校园生活'};

  @override
  void dispose() {
    _titleController.dispose();
    _contentController.dispose();
    for (final block in _blocks) {
      block.dispose();
    }
    super.dispose();
  }

  bool get _isArticle => _modeIndex == 1;

  Future<void> _pickDynamicImages() async {
    if (_images.length >= 9) {
      _toast('最多支持上传 9 张图片');
      return;
    }
    try {
      final files = await _picker.pickMultiImage(imageQuality: 88);
      final remaining = 9 - _images.length;
      for (final file in files.take(remaining)) {
        final bytes = await file.readAsBytes();
        final image = _PickedImage(bytes: bytes, name: file.name);
        setState(() => _images.add(image));
        _upload(image);
      }
    } catch (_) {
      _toast('选择图片失败，请重试');
    }
  }

  Future<void> _pickArticleImage() async {
    final used = _blocks.where((b) => b.type == 'IMAGE').length;
    if (used >= 9) {
      _toast('图片最多 9 张');
      return;
    }
    try {
      final file = await _picker.pickImage(
        source: ImageSource.gallery,
        imageQuality: 88,
      );
      if (file == null) return;
      final bytes = await file.readAsBytes();
      final image = _PickedImage(bytes: bytes, name: file.name);
      final anchor = _activeBlockIndex.clamp(0, _blocks.length - 1);
      var insertAt = anchor + 1;
      setState(() {
        _blocks.insert(insertAt, _ArticleBlock.image(image));
        // 图片后保证有一个文字段落，便于继续输入
        if (insertAt == _blocks.length - 1 ||
            _blocks[insertAt + 1].type == 'IMAGE') {
          _blocks.insert(insertAt + 1, _ArticleBlock.text());
          insertAt += 1;
        }
        _activeBlockIndex = insertAt;
      });
      _upload(image);
      _focusBlock(insertAt);
    } catch (_) {
      _toast('选择图片失败，请重试');
    }
  }

  void _addParagraph() {
    final index = _blocks.length;
    setState(() {
      _blocks.add(_ArticleBlock.text());
      _activeBlockIndex = index;
    });
    _focusBlock(index);
  }

  void _focusBlock(int index) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || index < 0 || index >= _blocks.length) return;
      final node = _blocks[index].focusNode;
      if (node != null) FocusScope.of(context).requestFocus(node);
    });
  }

  Future<void> _upload(_PickedImage image) async {
    final data = await ApiClient().uploadImage(
      image.bytes,
      filename: image.name,
    );
    if (!mounted) return;
    setState(() {
      if (data != null && data['fileId'] != null) {
        image.fileId = data['fileId'].toString();
        image.url = data['viewPath']?.toString();
        image.state = _UploadState.done;
      } else {
        image.state = _UploadState.failed;
      }
    });
  }

  void _retry(_PickedImage image) {
    setState(() => image.state = _UploadState.uploading);
    _upload(image);
  }

  bool get _hasUploading =>
      _images.any((i) => i.state == _UploadState.uploading) ||
      _blocks.any((b) => b.image?.state == _UploadState.uploading);

  bool get _hasFailed =>
      _images.any((i) => i.state == _UploadState.failed) ||
      _blocks.any((b) => b.image?.state == _UploadState.failed);

  List<Map<String, dynamic>> get _articleContentBlocks {
    final blocks = <Map<String, dynamic>>[];
    for (final block in _blocks) {
      if (block.type == 'TEXT') {
        final text = block.textController?.text.trim() ?? '';
        if (text.isNotEmpty) blocks.add({'type': 'TEXT', 'text': text});
      } else {
        final image = block.image;
        if (image != null &&
            image.state == _UploadState.done &&
            image.fileId != null) {
          blocks.add({'type': 'IMAGE', 'fileId': image.fileId});
        }
      }
    }
    return blocks;
  }

  String get _articleSummary {
    final texts = _blocks
        .where((b) => b.type == 'TEXT')
        .map((b) => b.textController?.text.trim() ?? '')
        .where((t) => t.isNotEmpty)
        .toList();
    return texts.join('\n');
  }

  Future<void> _handlePublish() async {
    final title = _titleController.text.trim();
    if (title.isEmpty) {
      _toast('请填写帖子标题');
      return;
    }
    if (_hasUploading) {
      _toast('图片上传中，请稍候');
      return;
    }
    if (_hasFailed) {
      _toast('有图片上传失败，请先重试');
      return;
    }

    final List<String> imageFileIds;
    final List<Map<String, dynamic>> blocks;
    final String content;
    if (_isArticle) {
      blocks = _articleContentBlocks;
      if (blocks.isEmpty) {
        _toast('文章正文不能为空');
        return;
      }
      content = _articleSummary;
      imageFileIds = const [];
    } else {
      content = _contentController.text.trim();
      if (content.isEmpty) {
        _toast('请填写正文内容');
        return;
      }
      imageFileIds = _images
          .where((i) => i.state == _UploadState.done && i.fileId != null)
          .map((i) => i.fileId!)
          .toList();
      blocks = const [];
    }

    setState(() => _submitting = true);
    final post = await CommunityApi.createPost(
      title: title,
      content: content,
      category: _partitions[_selectedPartitionIndex],
      sectionId: _sections.isEmpty
          ? null
          : int.tryParse(_sections[_selectedPartitionIndex].id),
      postType: _isArticle ? 'ARTICLE' : 'DYNAMIC',
      contentBlocks: blocks,
      imageFileIds: imageFileIds,
      tags: _selectedTopics.toList(),
      location: _selectedLocation,
    );
    if (!mounted) return;
    setState(() => _submitting = false);
    if (post == null) {
      _toast('发布失败，请检查网络后重试');
      return;
    }
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(_isArticle ? '文章已发布' : '动态已发布'),
        backgroundColor: AppColors.accent,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
    Navigator.of(context).pop(post);
  }

  Future<void> _pickLocation() async {
    final place = await LocationPickerPage.show(context);
    if (!mounted || place == null) return;
    setState(() => _selectedLocation = place);
  }

  void _toast(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: AppColors.danger,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  void _showCustomTopicDialog() {
    final topicInput = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('创建话题'),
        content: TextField(
          controller: topicInput,
          autofocus: true,
          decoration: const InputDecoration(
            hintText: '输入话题名称，如 绵城好书推荐',
            border: OutlineInputBorder(),
            isDense: true,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('取消'),
          ),
          ElevatedButton(
            onPressed: () {
              final text = topicInput.text.trim();
              if (text.isNotEmpty) {
                setState(() {
                  if (!_topics.contains(text)) _topics.add(text);
                  _selectedTopics.add(text);
                });
              }
              Navigator.of(ctx).pop();
            },
            child: const Text('添加'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: _buildAppBar(),
      body: _isArticle ? _buildArticleBody() : _buildDynamicBody(),
      bottomNavigationBar: _buildBottomBar(),
    );
  }

  PreferredSizeWidget _buildAppBar() {
    return AppBar(
      backgroundColor: Colors.white,
      elevation: 0,
      leading: TextButton(
        onPressed: () => Navigator.of(context).pop(),
        child: const Text(
          '取消',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 14,
            color: Color(0xFF64748B),
          ),
        ),
      ),
      title: Container(
        height: 32,
        decoration: BoxDecoration(
          color: const Color(0xFFF1F5F9),
          borderRadius: BorderRadius.circular(16),
        ),
        padding: const EdgeInsets.all(2),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [_buildSegmentBtn('动态', 0), _buildSegmentBtn('文章', 1)],
        ),
      ),
      centerTitle: true,
      actions: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          child: SizedBox(
            height: 32,
            child: ElevatedButton(
              onPressed: _submitting ? null : _handlePublish,
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.brand,
                foregroundColor: Colors.white,
                disabledBackgroundColor: const Color(0xFF93C5FD),
                elevation: 0,
                padding: const EdgeInsets.symmetric(horizontal: 16),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                ),
              ),
              child: Text(
                _submitting ? '发布中…' : '发布',
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildSegmentBtn(String text, int index) {
    final selected = _modeIndex == index;
    return GestureDetector(
      onTap: () => setState(() => _modeIndex = index),
      child: AnimatedContainer(
        duration: MediaQuery.disableAnimationsOf(context)
            ? Duration.zero
            : const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(horizontal: 14),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: selected ? Colors.white : Colors.transparent,
          borderRadius: BorderRadius.circular(14),
          boxShadow: selected
              ? const [
                  BoxShadow(
                    color: Color(0x140F172A),
                    blurRadius: 4,
                    offset: Offset(0, 1),
                  ),
                ]
              : null,
        ),
        child: Text(
          text,
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 13,
            fontWeight: selected ? FontWeight.bold : FontWeight.w500,
            color: selected ? AppColors.textPrimary : const Color(0xFF64748B),
          ),
        ),
      ),
    );
  }

  Widget _buildDynamicBody() {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      children: [
        _buildImageGrid(),
        const SizedBox(height: 14),
        _buildTitleField(),
        const SizedBox(height: 8),
        _buildContentField(),
      ],
    );
  }

  Widget _buildArticleBody() {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      children: [
        _buildTitleField(placeholder: '填写文章标题 *必填'),
        const SizedBox(height: 14),
        for (int i = 0; i < _blocks.length; i++) _buildArticleBlock(i),
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(
            onPressed: _addParagraph,
            icon: const Icon(Icons.add_rounded, size: 16),
            label: const Text(
              '添加段落',
              style: TextStyle(fontFamily: 'Noto Sans SC', fontSize: 12),
            ),
            style: TextButton.styleFrom(
              foregroundColor: AppColors.brand,
              padding: const EdgeInsets.symmetric(horizontal: 4),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildImageGrid() {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        for (int i = 0; i < _images.length; i++)
          _buildImageTile(_images[i], () {
            setState(() => _images.removeAt(i));
          }),
        if (_images.length < 9) _buildAddImageTile(),
      ],
    );
  }

  Widget _buildImageTile(_PickedImage image, VoidCallback onRemove) {
    return SizedBox(
      width: 104,
      height: 104,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(10),
        child: Stack(
          fit: StackFit.expand,
          children: [
            Image.memory(image.bytes, fit: BoxFit.cover),
            if (image.state == _UploadState.uploading)
              Container(
                color: const Color(0x66000000),
                child: const Center(
                  child: SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  ),
                ),
              ),
            if (image.state == _UploadState.failed)
              GestureDetector(
                onTap: () => _retry(image),
                child: Container(
                  color: const Color(0x99000000),
                  padding: const EdgeInsets.all(6),
                  child: const Center(
                    child: Text(
                      '上传失败\n点击重试',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 12, color: Colors.white),
                    ),
                  ),
                ),
              ),
            Positioned(
              top: 4,
              right: 4,
              child: GestureDetector(
                onTap: onRemove,
                child: Container(
                  width: 20,
                  height: 20,
                  decoration: const BoxDecoration(
                    color: Color(0x99000000),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.close_rounded,
                    size: 14,
                    color: Colors.white,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAddImageTile() {
    return GestureDetector(
      onTap: _pickDynamicImages,
      child: Container(
        width: 104,
        height: 104,
        decoration: BoxDecoration(
          color: const Color(0xFFF8FAFC),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: const Color(0xFFE2E8F0)),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              Icons.add_a_photo_outlined,
              size: 24,
              color: Color(0xFF94A3B8),
            ),
            const SizedBox(height: 6),
            Text(
              '添加图片\n${_images.length}/9',
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: Color(0xFF64748B),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTitleField({String placeholder = '填写标题 *必填'}) {
    return TextField(
      controller: _titleController,
      maxLength: 80,
      style: const TextStyle(
        fontFamily: 'Noto Sans SC',
        fontSize: 20,
        fontWeight: FontWeight.bold,
        color: AppColors.textPrimary,
      ),
      decoration: InputDecoration(
        hintText: placeholder,
        hintStyle: const TextStyle(
          fontFamily: 'Noto Sans SC',
          fontSize: 20,
          fontWeight: FontWeight.bold,
          color: Color(0xFFCBD5E1),
        ),
        counterText: '',
        border: InputBorder.none,
        contentPadding: EdgeInsets.zero,
      ),
    );
  }

  Widget _buildContentField() {
    return TextField(
      controller: _contentController,
      maxLines: null,
      minLines: 5,
      maxLength: 2000,
      style: const TextStyle(
        fontFamily: 'Noto Sans SC',
        fontSize: 14,
        height: 1.6,
        color: AppColors.textBody,
      ),
      decoration: const InputDecoration(
        hintText: '添加正文，分享校园新鲜事或互助求助...',
        hintStyle: TextStyle(
          fontFamily: 'Noto Sans SC',
          fontSize: 14,
          color: Color(0xFF64748B),
        ),
        border: InputBorder.none,
        contentPadding: EdgeInsets.zero,
      ),
    );
  }

  Widget _buildArticleBlock(int index) {
    final block = _blocks[index];
    if (block.type == 'TEXT') {
      return Padding(
        padding: const EdgeInsets.only(bottom: 2),
        child: TextField(
          focusNode: block.focusNode,
          controller: block.textController,
          maxLines: null,
          minLines: 1,
          maxLength: 5000,
          onTap: () => setState(() => _activeBlockIndex = index),
          style: const TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 14,
            height: 1.75,
            color: AppColors.textBody,
          ),
          decoration: InputDecoration(
            hintText: index == 0 ? '输入正文段落...' : '继续输入...',
            hintStyle: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 14,
              color: Color(0xFF64748B),
            ),
            border: InputBorder.none,
            counterText: '',
            contentPadding: const EdgeInsets.symmetric(vertical: 8),
          ),
        ),
      );
    }
    final image = block.image;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (image != null)
            ClipRRect(
              borderRadius: BorderRadius.circular(10),
              child: Stack(
                children: [
                  Image.memory(
                    image.bytes,
                    width: double.infinity,
                    height: 180,
                    fit: BoxFit.cover,
                  ),
                  if (image.state == _UploadState.uploading)
                    const Positioned.fill(
                      child: ColoredBox(
                        color: Color(0x66000000),
                        child: Center(
                          child: SizedBox(
                            width: 22,
                            height: 22,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          ),
                        ),
                      ),
                    ),
                  if (image.state == _UploadState.failed)
                    Positioned.fill(
                      child: GestureDetector(
                        onTap: () => _retry(image),
                        child: const ColoredBox(
                          color: Color(0x99000000),
                          child: Center(
                            child: Text(
                              '上传失败，点击重试',
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 12,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          Row(
            children: [
              const Text(
                '图片块',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: Color(0xFF64748B),
                ),
              ),
              const Spacer(),
              _buildBlockActions(index),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildBlockActions(int index) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (index > 0)
          IconButton(
            onPressed: () => setState(() {
              final block = _blocks.removeAt(index);
              _blocks.insert(index - 1, block);
            }),
            icon: const Icon(
              Icons.arrow_upward_rounded,
              size: 16,
              color: Color(0xFF94A3B8),
            ),
            constraints: const BoxConstraints(),
            padding: const EdgeInsets.all(4),
          ),
        if (index < _blocks.length - 1)
          IconButton(
            onPressed: () => setState(() {
              final block = _blocks.removeAt(index);
              _blocks.insert(index + 1, block);
            }),
            icon: const Icon(
              Icons.arrow_downward_rounded,
              size: 16,
              color: Color(0xFF94A3B8),
            ),
            constraints: const BoxConstraints(),
            padding: const EdgeInsets.all(4),
          ),
        IconButton(
          onPressed: () => setState(() {
            final block = _blocks.removeAt(index);
            block.dispose();
            if (_blocks.isEmpty) _blocks.add(_ArticleBlock.text());
          }),
          icon: const Icon(
            Icons.close_rounded,
            size: 16,
            color: Color(0xFF94A3B8),
          ),
          constraints: const BoxConstraints(),
          padding: const EdgeInsets.all(4),
        ),
      ],
    );
  }

  Widget _buildPartitionSection() {
    return SizedBox(
      height: 34,
      child: Row(
        children: [
          const Text(
            '发布到',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: List.generate(_partitions.length, (index) {
                  final selected = index == _selectedPartitionIndex;
                  return Padding(
                    padding: const EdgeInsets.only(right: 6),
                    child: GestureDetector(
                      onTap: () =>
                          setState(() => _selectedPartitionIndex = index),
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: selected
                              ? const Color(0xFFEFF6FF)
                              : const Color(0xFFF8FAFC),
                          borderRadius: BorderRadius.circular(13),
                          border: Border.all(
                            color: selected
                                ? const Color(0xFFBFDBFE)
                                : const Color(0xFFE2E8F0),
                          ),
                        ),
                        child: Text(
                          _partitions[index],
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 12,
                            fontWeight: selected
                                ? FontWeight.bold
                                : FontWeight.normal,
                            color: selected
                                ? AppColors.brand
                                : const Color(0xFF64748B),
                          ),
                        ),
                      ),
                    ),
                  );
                }),
              ),
            ),
          ),
          const SizedBox(width: 6),
          const Icon(
            Icons.chevron_right_rounded,
            size: 16,
            color: Color(0xFFCBD5E1),
          ),
        ],
      ),
    );
  }

  Widget _buildTopicSection() {
    return SizedBox(
      height: 34,
      child: Row(
        children: [
          const Text(
            '话题',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  for (final topic in _topics)
                    Padding(
                      padding: const EdgeInsets.only(right: 6),
                      child: GestureDetector(
                        onTap: () => setState(() {
                          if (_selectedTopics.contains(topic)) {
                            _selectedTopics.remove(topic);
                          } else {
                            _selectedTopics.add(topic);
                          }
                        }),
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 9,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            color: _selectedTopics.contains(topic)
                                ? const Color(0xFFEFF6FF)
                                : const Color(0xFFF8FAFC),
                            borderRadius: BorderRadius.circular(13),
                            border: Border.all(
                              color: _selectedTopics.contains(topic)
                                  ? const Color(0xFFBFDBFE)
                                  : const Color(0xFFE2E8F0),
                            ),
                          ),
                          child: Text(
                            '# $topic',
                            style: TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 12,
                              color: _selectedTopics.contains(topic)
                                  ? AppColors.brand
                                  : const Color(0xFF64748B),
                            ),
                          ),
                        ),
                      ),
                    ),
                  GestureDetector(
                    onTap: _showCustomTopicDialog,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 9,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFFECFDF5),
                        borderRadius: BorderRadius.circular(13),
                        border: Border.all(color: const Color(0xFFA7F3D0)),
                      ),
                      child: const Text(
                        '自建话题',
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 12,
                          color: Color(0xFF059669),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBottomBar() {
    return Container(
      color: Colors.white,
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).padding.bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Divider(height: 1, thickness: 1, color: Color(0xFFF1F5F9)),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
            child: _buildPartitionSection(),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 0),
            child: _buildTopicSection(),
          ),
          if (_selectedLocation != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(
                children: [
                  const Icon(
                    Icons.location_on_rounded,
                    size: 14,
                    color: Color(0xFF2563EB),
                  ),
                  const SizedBox(width: 3),
                  Expanded(
                    child: Text(
                      _selectedLocation!.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        color: Color(0xFF2563EB),
                      ),
                    ),
                  ),
                  GestureDetector(
                    onTap: () => setState(() => _selectedLocation = null),
                    child: const Icon(
                      Icons.close_rounded,
                      size: 14,
                      color: Color(0xFF94A3B8),
                    ),
                  ),
                ],
              ),
            ),
          const SizedBox(height: 2),
          Row(
            children: [
              IconButton(
                onPressed: _isArticle ? _pickArticleImage : _pickDynamicImages,
                icon: const Icon(
                  Icons.image_outlined,
                  size: 20,
                  color: Color(0xFF64748B),
                ),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 40),
                tooltip: _isArticle ? '在光标段落后插入图片' : '添加图片',
              ),
              IconButton(
                onPressed: _showCustomTopicDialog,
                icon: const Icon(
                  Icons.tag_rounded,
                  size: 20,
                  color: Color(0xFF64748B),
                ),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 32),
              ),
              IconButton(
                onPressed: _pickLocation,
                icon: Icon(
                  Icons.location_on_outlined,
                  size: 20,
                  color: _selectedLocation == null
                      ? const Color(0xFF64748B)
                      : const Color(0xFF2563EB),
                ),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 32),
                tooltip: '添加定位',
              ),
              const Spacer(),
              const SizedBox(width: 12),
            ],
          ),
        ],
      ),
    );
  }
}
