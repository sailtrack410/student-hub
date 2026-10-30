import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../api/api_client.dart';
import '../../api/service_catalog_api.dart';
import '../../api/service_request_api.dart';
import '../../services/image_compressor.dart';
import '../../theme/app_theme.dart';
import '../../widgets/image_upload_grid.dart';
import 'my_requests_page.dart';

/// 通用服务事项申请页：按 /api/app/services/{itemId} 的 formSchema 动态渲染
class ServiceApplyPage extends StatefulWidget {
  final String itemId;
  final String itemName;

  const ServiceApplyPage({
    super.key,
    required this.itemId,
    this.itemName = '服务申请',
  });

  @override
  State<ServiceApplyPage> createState() => _ServiceApplyPageState();
}

class _ServiceApplyPageState extends State<ServiceApplyPage> {
  final _formKey = GlobalKey<FormState>();
  final Map<String, TextEditingController> _controllers = {};
  final Map<String, String?> _selectValues = {};
  final Map<String, String?> _dateValues = {};
  final Map<String, List<ImageUploadItem>> _imageValues = {};
  final Set<String> _imageErrors = {};
  final ImagePicker _picker = ImagePicker();
  Map<String, dynamic>? _detail;
  bool _loading = true;
  bool _error = false;
  bool _submitting = false;

  String get _title => _detail?['itemName']?.toString().isNotEmpty == true
      ? _detail!['itemName'].toString()
      : widget.itemName;

  List<Map<String, dynamic>> get _schema {
    final raw = _detail?['formSchema'];
    if (raw is List) {
      return raw.whereType<Map<String, dynamic>>().toList();
    }
    return const [];
  }

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    for (final c in _controllers.values) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = false;
    });
    final api = ServiceCatalogApi(ApiClient());
    final detail = await api.fetchServiceDetail(widget.itemId);
    if (!mounted) return;
    setState(() {
      _detail = detail;
      _loading = false;
      _error = detail == null;
      if (detail != null) {
        for (final field in _schema) {
          final key = field['key']?.toString() ?? '';
          if (key.isEmpty) continue;
          if (field['type'] == 'input' ||
              field['type'] == 'textarea' ||
              field['type'] == 'number') {
            _controllers.putIfAbsent(key, () => TextEditingController());
          }
          if (field['type'] == 'select') {
            _selectValues.putIfAbsent(key, () => null);
          }
          if (field['type'] == 'date') {
            _dateValues.putIfAbsent(key, () => null);
          }
          if (field['type'] == 'image') {
            _imageValues.putIfAbsent(key, () => []);
          }
        }
      }
    });
  }

  Future<void> _pickDate(Map<String, dynamic> field) async {
    final key = field['key']?.toString() ?? '';
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: now,
      firstDate: now.subtract(const Duration(days: 7)),
      lastDate: now.add(const Duration(days: 180)),
      builder: (context, child) => Theme(
        data: Theme.of(context).copyWith(
          colorScheme: const ColorScheme.light(primary: AppColors.brand),
        ),
        child: child!,
      ),
    );
    if (picked != null) {
      setState(() {
        _dateValues[key] =
            '${picked.year}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
      });
    }
  }

  int _maxCount(Map<String, dynamic> field) {
    final raw = field['maxCount'];
    final value = raw is num
        ? raw.toInt()
        : int.tryParse(raw?.toString() ?? '');
    return (value == null || value <= 0) ? 3 : value;
  }

  Future<void> _pickImages(Map<String, dynamic> field) async {
    final key = field['key']?.toString() ?? '';
    if (key.isEmpty || _submitting) return;
    final items = _imageValues.putIfAbsent(key, () => []);
    final remaining = _maxCount(field) - items.length;
    if (remaining <= 0) return;
    try {
      final source = await _chooseImageSource();
      if (source == null) return;
      final List<XFile> files;
      if (source == ImageSource.camera) {
        final photo = await _picker.pickImage(
          source: ImageSource.camera,
          imageQuality: 88,
        );
        files = photo == null ? <XFile>[] : <XFile>[photo];
      } else {
        files = await _picker.pickMultiImage(imageQuality: 88);
      }
      if (files.isEmpty) return;
      if (files.length > remaining) {
        _toast('最多上传 ${_maxCount(field)} 张图片');
      }
      final selected = files.take(remaining).toList();
      for (final file in selected) {
        final raw = await file.readAsBytes();
        final bytes = compressImageBytes(raw);
        final item = ImageUploadItem(
          name: file.name,
          bytes: bytes,
          uploading: true,
        );
        if (!mounted) return;
        setState(() {
          items.add(item);
          _imageErrors.remove(key);
        });
        _uploadImage(key, item);
      }
    } catch (_) {
      _toast('无法打开相册或相机，请检查权限');
    }
  }

  Future<ImageSource?> _chooseImageSource() {
    return showModalBottomSheet<ImageSource>(
      context: context,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_library_outlined),
              title: const Text('从相册选择'),
              onTap: () => Navigator.of(ctx).pop(ImageSource.gallery),
            ),
            ListTile(
              leading: const Icon(Icons.photo_camera_outlined),
              title: const Text('拍照'),
              onTap: () => Navigator.of(ctx).pop(ImageSource.camera),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _uploadImage(String key, ImageUploadItem item) async {
    final data = await ApiClient().uploadImage(item.bytes, filename: item.name);
    if (!mounted) return;
    final items = _imageValues[key];
    if (items == null) return;
    final index = items.indexOf(item);
    if (index < 0) return;
    final fileId = data?['fileId']?.toString();
    setState(() {
      items[index] = item.copyWith(
        fileId: fileId,
        uploading: false,
        failed: fileId == null || fileId.isEmpty,
      );
    });
  }

  void _removeImage(String key, int index) {
    final items = _imageValues[key];
    if (items == null || index < 0 || index >= items.length) return;
    setState(() => items.removeAt(index));
  }

  void _retryImage(String key, int index) {
    final items = _imageValues[key];
    if (items == null || index < 0 || index >= items.length) return;
    final item = items[index];
    setState(() {
      items[index] = item.copyWith(uploading: true, failed: false);
    });
    _uploadImage(key, items[index]);
  }

  Future<void> _submit() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    setState(() => _imageErrors.clear());
    for (final field in _schema) {
      final key = field['key']?.toString() ?? '';
      final type = field['type']?.toString();
      if (type == 'image') {
        final items = _imageValues[key] ?? const <ImageUploadItem>[];
        if (field['required'] == true && items.isEmpty) {
          setState(() => _imageErrors.add(key));
          _toast('请上传${field['label'] ?? key}');
          return;
        }
        if (items.any((item) => item.uploading)) {
          _toast('图片正在上传，请稍候');
          return;
        }
        if (items.any((item) => item.failed)) {
          _toast('有图片上传失败，请重试或删除');
          return;
        }
      }
      if (field['required'] != true) continue;
      if (type == 'select' &&
          (_selectValues[key] == null || _selectValues[key]!.isEmpty)) {
        _toast('请选择${field['label'] ?? key}');
        return;
      }
      if (type == 'date' &&
          (_dateValues[key] == null || _dateValues[key]!.isEmpty)) {
        _toast('请选择${field['label'] ?? key}');
        return;
      }
    }

    setState(() => _submitting = true);
    final formData = <String, dynamic>{};
    for (final field in _schema) {
      final key = field['key']?.toString() ?? '';
      final type = field['type']?.toString();
      if (key.isEmpty) continue;
      if (type == 'select') {
        formData[key] = _selectValues[key];
      } else if (type == 'date') {
        formData[key] = _dateValues[key];
      } else if (type == 'image') {
        final fileIds = (_imageValues[key] ?? const <ImageUploadItem>[])
            .where((item) => item.isDone)
            .map((item) => item.fileId!)
            .toList();
        if (fileIds.isNotEmpty) formData[key] = fileIds;
      } else {
        formData[key] = _controllers[key]?.text.trim() ?? '';
      }
    }

    final result = await ServiceRequestApi.submitRequest(
      itemId: widget.itemId,
      title: _title,
      formData: formData,
    );
    if (!mounted) return;
    setState(() => _submitting = false);
    if (result == null) {
      _toast('提交失败，请检查网络后重试');
      return;
    }
    _showSuccess();
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

  void _showSuccess() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 56,
              height: 56,
              decoration: const BoxDecoration(
                color: Color(0xFFECFDF5),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.check_rounded,
                size: 32,
                color: AppColors.accent,
              ),
            ),
            const SizedBox(height: 14),
            const Text(
              '申请提交成功',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 17,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              '可在「我的申请」查看流转进度',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 18),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () {
                      Navigator.of(ctx).pop();
                      Navigator.of(context).pop();
                    },
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.textSecondary,
                      side: const BorderSide(color: AppColors.border),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(20),
                      ),
                    ),
                    child: const Text('完成'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: ElevatedButton(
                    onPressed: () {
                      Navigator.of(ctx).pop();
                      Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => const MyRequestsPage(),
                        ),
                      );
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.brand,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(20),
                      ),
                    ),
                    child: const Text('查看申请'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
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
        title: Text(
          _title,
          style: const TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        centerTitle: true,
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(strokeWidth: 2))
          : _error
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(
                    Icons.error_outline_rounded,
                    size: 48,
                    color: Color(0xFFCBD5E1),
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    '服务信息加载失败',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      color: AppColors.textSecondary,
                    ),
                  ),
                  const SizedBox(height: 16),
                  TextButton(onPressed: _load, child: const Text('重新加载')),
                ],
              ),
            )
          : Column(
              children: [
                Expanded(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _buildHeaderCard(),
                        const SizedBox(height: 14),
                        _buildFormCard(),
                      ],
                    ),
                  ),
                ),
                _buildBottomBar(),
              ],
            ),
    );
  }

  Widget _buildHeaderCard() {
    final summary = _detail?['summary']?.toString() ?? '';
    final description = _detail?['description']?.toString() ?? '';
    final sla = _detail?['slaHours'];
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: const Color(0xFFEFF6FF),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(
                  Icons.assignment_rounded,
                  size: 24,
                  color: AppColors.brand,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _title,
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    if (summary.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        summary,
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 12,
                          color: AppColors.textSecondary,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              if (sla != null)
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xFFECFDF5),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    '${sla}h响应',
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.accent,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
            ],
          ),
          if (description.isNotEmpty) ...[
            const SizedBox(height: 12),
            Text(
              description,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                height: 1.6,
                color: Color(0xFF64748B),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildFormCard() {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '填写申请信息',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 15,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 8),
            ..._schema.map(_buildField),
          ],
        ),
      ),
    );
  }

  Widget _buildField(Map<String, dynamic> field) {
    final key = field['key']?.toString() ?? '';
    final label = field['label']?.toString() ?? key;
    final type = field['type']?.toString() ?? 'input';
    final required = field['required'] == true;
    final hint = field['placeholder']?.toString();

    Widget child;
    switch (type) {
      case 'textarea':
        child = TextFormField(
          controller: _controllers[key],
          maxLines: 4,
          maxLength: (field['max'] as num?)?.toInt() ?? 500,
          validator: required
              ? (v) => (v == null || v.trim().isEmpty) ? '请填写$label' : null
              : null,
          decoration: InputDecoration(
            hintText: hint ?? '请输入$label',
            hintStyle: const TextStyle(fontSize: 13, color: Color(0xFF64748B)),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
          ),
        );
      case 'select':
        final options = (field['options'] as List? ?? const [])
            .map((e) => e.toString())
            .toList();
        child = DropdownButtonFormField<String>(
          initialValue: _selectValues[key],
          decoration: InputDecoration(
            hintText: '请选择$label',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
          ),
          items: options
              .map(
                (e) => DropdownMenuItem(
                  value: e,
                  child: Text(e, style: const TextStyle(fontSize: 13)),
                ),
              )
              .toList(),
          onChanged: (value) => setState(() => _selectValues[key] = value),
        );
      case 'date':
        child = GestureDetector(
          onTap: () => _pickDate(field),
          child: Container(
            width: double.infinity,
            height: 48,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            decoration: BoxDecoration(
              border: Border.all(color: const Color(0xFFCBD5E1)),
              borderRadius: BorderRadius.circular(10),
            ),
            alignment: Alignment.centerLeft,
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    _dateValues[key] ?? '请选择$label',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 13,
                      color: _dateValues[key] == null
                          ? const Color(0xFF64748B)
                          : AppColors.textPrimary,
                    ),
                  ),
                ),
                const Icon(
                  Icons.calendar_today_rounded,
                  size: 16,
                  color: Color(0xFF94A3B8),
                ),
              ],
            ),
          ),
        );
      case 'number':
        child = TextFormField(
          controller: _controllers[key],
          keyboardType: TextInputType.number,
          validator: required
              ? (v) => (v == null || v.trim().isEmpty) ? '请填写$label' : null
              : null,
          decoration: InputDecoration(
            hintText: hint ?? '请输入$label',
            hintStyle: const TextStyle(fontSize: 13, color: Color(0xFF64748B)),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
          ),
        );
      case 'image':
        final items = _imageValues[key] ?? const <ImageUploadItem>[];
        child = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ImageUploadGrid(
              items: items,
              maxCount: _maxCount(field),
              enabled: !_submitting,
              onAdd: () => _pickImages(field),
              onRemove: (index) => _removeImage(key, index),
              onRetry: (index) => _retryImage(key, index),
            ),
            const SizedBox(height: 6),
            Text(
              hint ?? '最多 ${_maxCount(field)} 张，支持 jpg/png/webp',
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: Color(0xFF64748B),
              ),
            ),
            if (_imageErrors.contains(key)) ...[
              const SizedBox(height: 4),
              Text(
                '请上传$label',
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: AppColors.danger,
                ),
              ),
            ],
          ],
        );
      default:
        child = TextFormField(
          controller: _controllers[key],
          validator: required
              ? (v) => (v == null || v.trim().isEmpty) ? '请填写$label' : null
              : null,
          decoration: InputDecoration(
            hintText: hint ?? '请输入$label',
            hintStyle: const TextStyle(fontSize: 13, color: Color(0xFF64748B)),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
          ),
        );
    }

    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                label,
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textPrimary,
                ),
              ),
              if (required)
                const Text(
                  ' *',
                  style: TextStyle(color: AppColors.danger, fontSize: 13),
                ),
            ],
          ),
          const SizedBox(height: 6),
          child,
        ],
      ),
    );
  }

  Widget _buildBottomBar() {
    return Container(
      color: Colors.white,
      padding: EdgeInsets.fromLTRB(
        16,
        10,
        16,
        10 + MediaQuery.of(context).padding.bottom,
      ),
      child: SizedBox(
        width: double.infinity,
        height: 46,
        child: ElevatedButton(
          onPressed: _submitting ? null : _submit,
          style: ElevatedButton.styleFrom(
            backgroundColor: AppColors.brand,
            foregroundColor: Colors.white,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(23),
            ),
          ),
          child: _submitting
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: Colors.white,
                  ),
                )
              : const Text(
                  '提交申请',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                  ),
                ),
        ),
      ),
    );
  }
}
