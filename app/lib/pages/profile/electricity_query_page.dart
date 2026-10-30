import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;
import 'package:image_picker/image_picker.dart';
import 'package:zxing2/qrcode.dart';
import 'package:zxing2/zxing2.dart';

import '../../api/electricity_api.dart';
import '../../theme/app_theme.dart';

/// 宿舍电费查询：扫码/粘贴二维码链接提取 SN，再查云端余额。
class ElectricityQueryPage extends StatefulWidget {
  const ElectricityQueryPage({super.key});

  @override
  State<ElectricityQueryPage> createState() => _ElectricityQueryPageState();
}

class _ElectricityQueryPageState extends State<ElectricityQueryPage> {
  final TextEditingController _snController = TextEditingController(
    text: 'YM3022333CDD',
  );
  final ImagePicker _picker = ImagePicker();
  ElectricityBill? _bill;
  bool _loading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _query();
  }

  @override
  void dispose() {
    _snController.dispose();
    super.dispose();
  }

  String? _extractSn(String text) {
    final match = RegExp(r'YM[0-9A-Za-z]{9,12}').firstMatch(text.trim());
    return match?.group(0)?.toUpperCase();
  }

  Future<void> _query() async {
    final sn = _extractSn(_snController.text);
    if (sn == null) {
      setState(() => _error = '未识别到 SN，请粘贴二维码链接或输入 YM 开头的设备号');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _snController.text = sn;
    });
    final bill = await ElectricityApi.query(sn);
    if (!mounted) return;
    setState(() {
      _loading = false;
      _bill = bill;
      _error = bill == null ? '未查询到该电表，请核对 SN' : null;
    });
  }

  Future<void> _scanFromGallery() async {
    try {
      final file = await _picker.pickImage(source: ImageSource.gallery);
      if (file == null) return;
      final bytes = await file.readAsBytes();
      final qrText = _decodeQr(bytes);
      if (qrText == null) {
        if (mounted) setState(() => _error = '图片中未识别到二维码');
        return;
      }
      final sn = _extractSn(qrText);
      if (sn == null) {
        if (mounted) setState(() => _error = '二维码中没有 YM 开头的设备 SN');
        return;
      }
      _snController.text = sn;
      await _query();
    } catch (_) {
      if (mounted) setState(() => _error = '识别二维码失败，请重试');
    }
  }

  String? _decodeQr(Uint8List bytes) {
    try {
      final image = img.decodeImage(bytes);
      if (image == null) return null;
      final rgba = image.convert(numChannels: 4);
      final pixels = Int32List(rgba.width * rgba.height);
      var offset = 0;
      for (var y = 0; y < rgba.height; y++) {
        for (var x = 0; x < rgba.width; x++) {
          final pixel = rgba.getPixel(x, y);
          pixels[offset++] =
              (pixel.a.toInt() << 24) |
              (pixel.r.toInt() << 16) |
              (pixel.g.toInt() << 8) |
              pixel.b.toInt();
        }
      }
      final source = RGBLuminanceSource(rgba.width, rgba.height, pixels);
      final bitmap = BinaryBitmap(HybridBinarizer(source));
      return QRCodeReader().decode(bitmap).text;
    } catch (_) {
      return null;
    }
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
        title: const Text(
          '宿舍电费查询',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: Color(0xFF0F172A),
          ),
        ),
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: const Color(0xFFE2E8F0)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '扫码或粘贴二维码链接',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                ),
                const SizedBox(height: 4),
                const Text(
                  '自动提取 YM 开头设备 SN，例：YM3022333CDD',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF64748B),
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _snController,
                  textInputAction: TextInputAction.done,
                  onSubmitted: (_) => _query(),
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 13,
                    color: Color(0xFF0F172A),
                  ),
                  decoration: InputDecoration(
                    hintText: '粘贴二维码链接或 SN',
                    isDense: true,
                    hintStyle: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      color: Color(0xFF64748B),
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: SizedBox(
                        height: 44,
                        child: OutlinedButton.icon(
                          onPressed: _scanFromGallery,
                          icon: const Icon(
                            Icons.qr_code_scanner_rounded,
                            size: 18,
                          ),
                          label: const Text(
                            '从相册识别二维码',
                            style: TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 12,
                            ),
                          ),
                          style: OutlinedButton.styleFrom(
                            foregroundColor: AppColors.brand,
                            side: const BorderSide(color: Color(0xFFBFDBFE)),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(10),
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: SizedBox(
                        height: 44,
                        child: ElevatedButton(
                          onPressed: _loading ? null : _query,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: AppColors.brand,
                            foregroundColor: Colors.white,
                            elevation: 0,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(10),
                            ),
                          ),
                          child: _loading
                              ? const SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : const Text(
                                  '查询电费',
                                  style: TextStyle(
                                    fontFamily: 'Noto Sans SC',
                                    fontSize: 13,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                        ),
                      ),
                    ),
                  ],
                ),
                if (_error != null) ...[
                  const SizedBox(height: 10),
                  Text(
                    _error!,
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      color: Color(0xFFEF4444),
                    ),
                  ),
                ],
              ],
            ),
          ),
          if (_bill != null) ...[
            const SizedBox(height: 14),
            Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF0D9488), Color(0xFF0F766E)],
                ),
                borderRadius: BorderRadius.circular(16),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x330D9488),
                    blurRadius: 16,
                    offset: Offset(0, 6),
                  ),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          _bill!.name,
                          style: const TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 13,
                            color: Color(0xCCFFFFFF),
                          ),
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 6,
                          vertical: 2,
                        ),
                        decoration: BoxDecoration(
                          color: const Color(0x33FFFFFF),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          _bill!.switchStatus == 1 ? '通电中' : '已断电',
                          style: const TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 12,
                            color: Colors.white,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.baseline,
                    textBaseline: TextBaseline.alphabetic,
                    children: [
                      Text(
                        '¥ ${_bill!.surplusValue.toStringAsFixed(2)}',
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 30,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                      ),
                      const SizedBox(width: 6),
                      const Text(
                        '剩余电费',
                        style: TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 12,
                          color: Color(0xCCFFFFFF),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '数据时间 ${_bill!.readTime} · ${_bill!.payee}',
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      color: Color(0x99FFFFFF),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 14),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 14),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              child: Row(
                children: [
                  _metric('累计用电', '${_bill!.totalValue.toStringAsFixed(2)} 度'),
                  _divider(),
                  _metric('电价', '¥ ${_bill!.unitPrice.toStringAsFixed(2)}/度'),
                  _divider(),
                  _metric('设备 SN', _bill!.sn),
                ],
              ),
            ),
            const SizedBox(height: 10),
            Text(
              '通信协议：${_bill!.protocols} · ${_bill!.deviceType} · ${_bill!.payEnable ? "可在线缴费" : "暂不可在线缴费"}',
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: Color(0xFF64748B),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _metric(String label, String value) {
    return Expanded(
      child: Column(
        children: [
          Text(
            label,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
            ),
          ),
          const SizedBox(height: 5),
          Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 13,
              fontWeight: FontWeight.bold,
              color: Color(0xFF0F172A),
            ),
          ),
        ],
      ),
    );
  }

  Widget _divider() =>
      Container(width: 1, height: 30, color: const Color(0xFFE2E8F0));
}
