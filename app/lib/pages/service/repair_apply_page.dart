import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../api/api_client.dart';
import '../../api/dict_api.dart';
import '../../api/profile_api.dart';
import '../../api/service_request_api.dart';
import '../../services/image_compressor.dart';
import '../../widgets/image_upload_grid.dart';
import 'my_requests_page.dart';
import 'work_order_detail_page.dart';

/// APP-教室报修申请 (对应画布 5:1049 节点，375 × 812)
/// 严格依据 Ardot 设计规范实现报修位置选择、故障多分类切换、问题描述输入、现场照片上传与提交流转
class RepairApplyPage extends StatefulWidget {
  final String title;

  /// 服务目录项 ID（宿舍 1001 / 教室与公共区域 1002）
  final String itemId;

  const RepairApplyPage({
    super.key,
    this.title = '教室与公共区域报修',
    this.itemId = '1002',
  });

  @override
  State<RepairApplyPage> createState() => _RepairApplyPageState();
}

class _RepairApplyPageState extends State<RepairApplyPage> {
  String _selectedLocation = '';
  String _selectedFaultCategory = '';
  late final TextEditingController _descriptionController;
  final TextEditingController _contactPhoneController = TextEditingController();
  final TextEditingController _roomController = TextEditingController();
  final ImagePicker _picker = ImagePicker();

  final List<ImageUploadItem> _photos = [];
  bool _isSubmitting = false;
  String? _submittedRequestId;
  String? _submittedRequestNo;

  // 字典首次加载失败保留的兼容兜底，不以示例房间代替用户选择。
  static const _classroomFaultCategories = [
    '多媒体设备', '桌椅损坏', '照明电路', '门窗损坏', '其他',
  ];
  static const _dormFaultCategories = [
    '门窗', '墙皮', '地砖', '桌子', '椅子', '床',
    '风扇', '空调', '灯', '停电', '停水', '漏水',
  ];
  static const _classroomLocations = [
    '明德楼 (教学楼A栋) · 302 多媒体教室',
    '明德楼 (教学楼A栋) · 101 大阶梯教室',
    '明德楼 (教学楼A栋) · 204 智慧研讨室',
    '笃学楼 (教学楼B栋) · 405 计算机网络室',
    '实验实训楼 · 501 语音实验室',
    '综合图书馆 · 2楼 社科阅览室',
  ];

  final List<Map<String, dynamic>> _buildings = [];
  List<String> _faultTypes = const [];
  String? _community;
  String? _building;
  bool _dictLoading = true;
  String? _dictError;

  bool get _isDorm => widget.title.contains('宿舍');

  List<String> get _communities => {
    for (final item in _buildings)
      '${item['extra'] ?? ''}'.contains('community')
          ? RegExp(r'"community"\s*:\s*"([^"]+)"')
                  .firstMatch('${item['extra'] ?? ''}')
                  ?.group(1) ??
              ''
          : '',
  }.where((name) => name.isNotEmpty).toList();

  List<Map<String, dynamic>> get _communityBuildings => _buildings.where((item) {
    final extra = '${item['extra'] ?? ''}';
    return extra.contains('"community":"$_community"') ||
        extra.contains('"community": "$_community"');
  }).toList();

  List<String> get _candidateLocations => _classroomLocations;

  @override
  void initState() {
    super.initState();
    // 不写死任何演示信息；姓名/电话仅从当前登录用户资料异步带出。
    _descriptionController = TextEditingController();
    _loadContact();
    _loadDictionaries();
  }

  Future<void> _loadDictionaries() async {
    setState(() {
      _dictLoading = true;
      _dictError = null;
    });
    try {
      final futures = await Future.wait([
        DictApi.fetchItems('REPAIR_FAULT_TYPE'),
        if (_isDorm) DictApi.fetchItems('DORM_BUILDING'),
      ]);
      if (!mounted) return;
      setState(() {
        _faultTypes = futures[0]
            .map((item) => '${item['itemLabel'] ?? item['itemValue'] ?? ''}')
            .where((label) => label.isNotEmpty)
            .toList();
        if (_isDorm) {
          _buildings
            ..clear()
            ..addAll(futures[1]);
        }
        _dictLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _dictLoading = false;
        _dictError = '字典加载失败，点击重试';
        _faultTypes = _isDorm ? _dormFaultCategories : _classroomFaultCategories;
      });
    }
  }

  @override
  void dispose() {
    _descriptionController.dispose();
    _contactPhoneController.dispose();
    _roomController.dispose();
    super.dispose();
  }

  void _showGuideSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        padding: const EdgeInsets.all(24),
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: const Color(0xFFEFF6FF),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(
                    Icons.menu_book_rounded,
                    color: Color(0xFF2563EB),
                    size: 20,
                  ),
                ),
                const SizedBox(width: 12),
                const Text(
                  '校园公共设施报修指南',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 17,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            const Text(
              '1. 受理范围：全校教学楼公用多媒体设备、课桌椅、门窗电路照明及暖通设施。\n'
              '2. 响应时效：日常工作日承诺 2 小时内接单响应，24 小时内核验上门排除故障。\n'
              '3. 紧急教学保障：若授课遇设备突发严重故障，可点击紧急热线直通调度中心。',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                color: Color(0xFF475569),
                height: 1.6,
              ),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () => Navigator.of(ctx).pop(),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF2563EB),
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                padding: const EdgeInsets.symmetric(vertical: 12),
              ),
              child: const Text('我知道了'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _loadContact() async {
    final me = await ProfileApi.fetchMe();
    if (!mounted || me == null) return;
    final phone = me['phone']?.toString();
    if (_contactPhoneController.text.isEmpty &&
        phone != null &&
        phone.isNotEmpty) {
      _contactPhoneController.text = phone;
    }
    if (mounted) setState(() {});
  }

  Future<void> _pickPhotos() async {
    if (_photos.length >= 3 || _isSubmitting) return;
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
      final remaining = 3 - _photos.length;
      if (files.length > remaining) {
        _toast('最多上传 3 张故障照片');
      }
      for (final file in files.take(remaining)) {
        final raw = await file.readAsBytes();
        final item = ImageUploadItem(
          name: file.name,
          bytes: compressImageBytes(raw),
          uploading: true,
        );
        if (!mounted) return;
        setState(() => _photos.add(item));
        _uploadPhoto(item);
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

  Future<void> _uploadPhoto(ImageUploadItem item) async {
    final data = await ApiClient().uploadImage(item.bytes, filename: item.name);
    if (!mounted) return;
    final index = _photos.indexOf(item);
    if (index < 0) return;
    final fileId = data?['fileId']?.toString();
    setState(() {
      _photos[index] = item.copyWith(
        fileId: fileId,
        uploading: false,
        failed: fileId == null || fileId.isEmpty,
      );
    });
  }

  void _removePhoto(int index) {
    if (index < 0 || index >= _photos.length) return;
    setState(() => _photos.removeAt(index));
  }

  void _retryPhoto(int index) {
    if (index < 0 || index >= _photos.length) return;
    final item = _photos[index];
    setState(() {
      _photos[index] = item.copyWith(uploading: true, failed: false);
    });
    _uploadPhoto(_photos[index]);
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

  Map<String, dynamic> _buildFormData() {
    final data = <String, dynamic>{
      'contactPhone': _contactPhoneController.text.trim(),
      'faultType': _selectedFaultCategory,
      'description': _descriptionController.text.trim(),
    };
    if (_isDorm) {
      data['dormBuilding'] = _building ?? '';
      data['address'] = _roomController.text.trim();
    } else {
      data['address'] = _selectedLocation;
    }
    final fileIds = _photos
        .where((item) => item.isDone)
        .map((e) => e.fileId!)
        .toList();
    if (fileIds.isNotEmpty) data['photos'] = fileIds;
    return data;
  }

  Future<void> _handleSubmit() async {
    if (_isDorm && (_community == null || _building == null)) {
      _toast('请选择宿舍社区和楼栋');
      return;
    }
    if (_isDorm && _roomController.text.trim().isEmpty) {
      _toast('请输入房间号');
      return;
    }
    if (!_isDorm && _selectedLocation.isEmpty) {
      _toast('请选择报修位置');
      return;
    }
    if (_selectedFaultCategory.isEmpty) {
      _toast('请选择故障分类');
      return;
    }
    final text = _descriptionController.text.trim();
    if (text.isEmpty) {
      _toast('请填写详细的问题描述以便维修师傅备件');
      return;
    }
    if (_contactPhoneController.text.trim().isEmpty) {
      _toast('请填写联系电话');
      return;
    }
    if (_photos.any((item) => item.uploading)) {
      _toast('照片正在上传，请稍候');
      return;
    }
    if (_photos.any((item) => item.failed)) {
      _toast('有照片上传失败，请重试或删除');
      return;
    }

    setState(() => _isSubmitting = true);
    Map<String, dynamic>? result;
    try {
      result = await ServiceRequestApi.submitRequest(
        itemId: widget.itemId,
        title: widget.title,
        formData: _buildFormData(),
      );
    } catch (_) {
      result = null;
    }
    if (!mounted) return;
    setState(() => _isSubmitting = false);
    if (result == null) {
      _toast('提交失败，请检查网络后重试');
      return;
    }
    _submittedRequestId = result['requestId']?.toString();
    _submittedRequestNo = result['requestNo']?.toString() ?? '';
    _showSuccessDialog();
  }

  void _showSuccessDialog() {
    final requestNo = _submittedRequestNo;
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        contentPadding: const EdgeInsets.fromLTRB(24, 24, 24, 16),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 56,
              height: 56,
              decoration: const BoxDecoration(
                color: Color(0xFFEFF6FF),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.check_rounded,
                color: Color(0xFF2563EB),
                size: 32,
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              '报修申请已提交',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: Color(0xFF0F172A),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              requestNo == null || requestNo.isEmpty
                  ? '申请已受理，可在「我的申请」查看流转进度。'
                  : '申请编号：$requestNo\n可在「我的申请」查看流转进度。',
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 13,
                color: Color(0xFF64748B),
                height: 1.5,
              ),
            ),
          ],
        ),
        actions: [
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () {
                    Navigator.of(ctx).pop();
                    Navigator.of(context).pushReplacement(
                      MaterialPageRoute(builder: (_) => const MyRequestsPage()),
                    );
                  },
                  style: OutlinedButton.styleFrom(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                    side: const BorderSide(color: Color(0xFFE2E8F0)),
                  ),
                  child: const Text(
                    '我的申请',
                    style: TextStyle(color: Color(0xFF475569)),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: ElevatedButton(
                  onPressed: () {
                    Navigator.of(ctx).pop();
                    final requestId = _submittedRequestId;
                    Navigator.of(context).pushReplacement(
                      MaterialPageRoute(
                        builder: (_) => requestId == null || requestId.isEmpty
                            ? const MyRequestsPage()
                            : WorkOrderDetailPage(requestId: requestId),
                      ),
                    );
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF2563EB),
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  child: const Text('查看进度'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildDropdown<T>({
    required T? value,
    required String hint,
    required List<DropdownMenuItem<T>> items,
    required ValueChanged<T?>? onChanged,
  }) {
    return DropdownButtonFormField<T>(
      initialValue: value,
      isExpanded: true,
      items: items,
      onChanged: onChanged,
      icon: const Icon(Icons.keyboard_arrow_down_rounded, size: 20),
      decoration: InputDecoration(
        hintText: hint,
        filled: true,
        isDense: true,
        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 13),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final faultCategories = _faultTypes.isEmpty
        ? (_isDorm ? _dormFaultCategories : _classroomFaultCategories)
        : _faultTypes;

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            // 1. 顶部导航栏 (节点 5:1050: 375 × 88)
            Container(
              color: Colors.white,
              padding: const EdgeInsets.only(
                left: 12,
                right: 16,
                top: 10,
                bottom: 10,
              ),
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(
                      Icons.arrow_back_ios_new_rounded,
                      size: 20,
                      color: Color(0xFF0F172A),
                    ),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                  Expanded(
                    child: Center(
                      child: Text(
                        widget.title,
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 17,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF0F172A),
                        ),
                      ),
                    ),
                  ),
                  InkWell(
                    onTap: _showGuideSheet,
                    borderRadius: BorderRadius.circular(6),
                    child: const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 4, vertical: 4),
                      child: Text(
                        '办事指南',
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: Color(0xFF2563EB),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // 2. 表单内容可滚动区域 (节点 5:1055)
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                children: [
                  // 提示横条 (节点 5:1056)
                  Container(
                    decoration: BoxDecoration(
                      color: const Color(0xFFEFF6FF),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 9,
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Icon(
                          Icons.info_outline_rounded,
                          size: 16,
                          color: Color(0xFF2563EB),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            widget.title.contains('宿舍')
                                ? '提示：适用于学生宿舍楼设施故障，宿管阿姨核验后 24 小时内上门处置'
                                : '提示：适用于教学楼、多媒体教室等公共设施故障，承诺 24 小时内上门处置',
                            style: const TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 12,
                              color: Color(0xFF2563EB),
                              height: 1.4,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 12),

                  // 卡片 1: 报修位置与故障分类 (节点 5:1058)
                  _buildCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '报修位置 *',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF0F172A),
                          ),
                        ),
                        const SizedBox(height: 8),
                        if (_isDorm) ...[
                          _buildDropdown<String>(
                            value: _community,
                            hint: '请选择宿舍社区',
                            items: _communities
                                .map((value) => DropdownMenuItem(
                                    value: value, child: Text(value)))
                                .toList(),
                            onChanged: _dictLoading
                                ? null
                                : (value) => setState(() {
                                      _community = value;
                                      _building = null;
                                    }),
                          ),
                          const SizedBox(height: 10),
                          _buildDropdown<String>(
                            value: _building,
                            hint: _community == null ? '请先选择社区' : '请选择楼栋',
                            items: _communityBuildings
                                .map((item) => DropdownMenuItem(
                                    value: '${item['itemLabel']}',
                                    child: Text('${item['itemLabel']}')))
                                .toList(),
                            onChanged: _community == null
                                ? null
                                : (value) => setState(() => _building = value),
                          ),
                          const SizedBox(height: 10),
                          TextField(
                            controller: _roomController,
                            keyboardType: TextInputType.number,
                            maxLength: 12,
                            decoration: const InputDecoration(
                              labelText: '房间号 *',
                              hintText: '如：302',
                              counterText: '',
                              filled: true,
                            ),
                          ),
                        ] else
                          _buildDropdown<String>(
                            value: _selectedLocation.isEmpty
                                ? null
                                : _selectedLocation,
                            hint: '请选择报修位置',
                            items: _candidateLocations
                                .map((value) => DropdownMenuItem(
                                    value: value, child: Text(value)))
                                .toList(),
                            onChanged: (value) =>
                                setState(() => _selectedLocation = value ?? ''),
                          ),
                        if (_dictError != null)
                          TextButton.icon(
                            onPressed: _loadDictionaries,
                            icon: const Icon(Icons.refresh, size: 16),
                            label: Text(_dictError!),
                          ),
                        const SizedBox(height: 16),

                        const Text(
                          '故障分类 *',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF0F172A),
                          ),
                        ),
                        const SizedBox(height: 10),
                        _buildDropdown<String>(
                          value: _selectedFaultCategory.isEmpty
                              ? null
                              : _selectedFaultCategory,
                          hint: _dictLoading ? '故障分类加载中…' : '请选择故障分类',
                          items: faultCategories
                              .map((value) => DropdownMenuItem(
                                  value: value, child: Text(value)))
                              .toList(),
                          onChanged: _dictLoading
                              ? null
                              : (value) => setState(
                                  () => _selectedFaultCategory = value ?? ''),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 12),

                  // 卡片 2: 问题描述与现场照片 (节点 5:1074)
                  _buildCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '问题描述 *',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF0F172A),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Container(
                          decoration: BoxDecoration(
                            color: const Color(0xFFF8FAFC),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: const Color(0xFFE2E8F0)),
                          ),
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              TextField(
                                controller: _descriptionController,
                                maxLines: 4,
                                maxLength: 500,
                                buildCounter: (
                                  _, {
                                  required currentLength,
                                  required isFocused,
                                  maxLength,
                                }) => null,
                                onChanged: (_) => setState(() {}),
                                style: const TextStyle(
                                  fontFamily: 'Noto Sans SC',
                                  fontSize: 13,
                                  color: Color(0xFF0F172A),
                                  height: 1.5,
                                ),
                                decoration: const InputDecoration(
                                  hintText: '请详细描述具体故障现象、影响范围及具体急需情况...',
                                  hintStyle: TextStyle(
                                    fontFamily: 'Noto Sans SC',
                                    fontSize: 13,
                                    color: Color(0xFF64748B),
                                  ),
                                  border: InputBorder.none,
                                  isDense: true,
                                  contentPadding: EdgeInsets.zero,
                                ),
                              ),
                              Align(
                                alignment: Alignment.bottomRight,
                                child: Text(
                                  '${_descriptionController.text.length} / 500',
                                  style: const TextStyle(
                                    fontFamily: 'Noto Sans SC',
                                    fontSize: 12,
                                    color: Color(0xFF64748B),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),

                        const SizedBox(height: 14),

                        const Text(
                          '现场照片 (有助于师傅快速排查备件)',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 13,
                            color: Color(0xFF334155),
                          ),
                        ),
                        const SizedBox(height: 10),
                        ImageUploadGrid(
                          items: _photos,
                          maxCount: 3,
                          enabled: !_isSubmitting,
                          onAdd: _pickPhotos,
                          onRemove: _removePhoto,
                          onRetry: _retryPhoto,
                        ),
                        const SizedBox(height: 6),
                        const Text(
                          '最多 3 张，支持 jpg/png/webp；上传失败可点图重试',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 12,
                            color: Color(0xFF64748B),
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 12),

                  // 卡片 3: 联系与期望 (节点 5:1087)
                  _buildCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '联系电话 *',
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF0F172A),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Container(
                          decoration: BoxDecoration(
                            color: const Color(0xFFF8FAFC),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: const Color(0xFFE2E8F0)),
                          ),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 10,
                          ),
                          child: TextField(
                            controller: _contactPhoneController,
                            keyboardType: TextInputType.phone,
                            style: const TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 13,
                              color: Color(0xFF0F172A),
                            ),
                            decoration: const InputDecoration(
                              // 关闭主题 filled 白底，避免胶囊内双层底纹
                              filled: false,
                              border: InputBorder.none,
                              isDense: true,
                              contentPadding: EdgeInsets.zero,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 20),

                  // 提交按钮 (节点 5:1094: 343 × 46, 圆角 23, 浅蓝渐变实色)
                  ElevatedButton(
                    onPressed: _isSubmitting ? null : _handleSubmit,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF2563EB),
                      foregroundColor: Colors.white,
                      elevation: 0,
                      minimumSize: const Size(double.infinity, 46),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(23),
                      ),
                    ),
                    child: _isSubmitting
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : Text(
                            widget.title.contains('宿舍')
                                ? '立即提交宿舍报修申请'
                                : '立即提交教室报修申请',
                            style: const TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 15,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCard({required Widget child}) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFF1F5F9)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x060F172A),
            blurRadius: 10,
            offset: Offset(0, 4),
          ),
        ],
      ),
      padding: const EdgeInsets.all(14),
      child: child,
    );
  }
}
