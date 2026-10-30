import 'dart:async';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:record/record.dart';

import 'package:flutter/material.dart';

import '../../api/ai_api.dart';
import '../../api/api_client.dart';
import '../../theme/app_theme.dart';

/// APP-绵宝AI-语音输入声波弹层 (画布 5:2816)
class VoiceInputSheet extends StatefulWidget {
  final String question;

  const VoiceInputSheet({super.key, this.question = '今天下午第二节课在哪个教室？'});

  static Future<String?> show(
    BuildContext context, {
    String question = '今天下午第二节课在哪个教室？',
  }) {
    return showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => VoiceInputSheet(question: question),
    );
  }

  @override
  State<VoiceInputSheet> createState() => _VoiceInputSheetState();
}

class _VoiceInputSheetState extends State<VoiceInputSheet>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1200),
  );
  final AudioRecorder _recorder = AudioRecorder();
  final List<int> _audioBytes = [];
  StreamSubscription<Uint8List>? _subscription;
  bool _recording = false;
  bool _processing = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _startRecording();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (MediaQuery.disableAnimationsOf(context)) { _controller.stop(); }
    else if (!_controller.isAnimating) { _controller.repeat(); }
  }

  Future<void> _startRecording() async {
    setState(() {
      _error = null;
      _processing = false;
      _audioBytes.clear();
    });
    try {
      final allowed = await _recorder.hasPermission();
      if (!allowed) {
        if (mounted) setState(() => _error = '未获得麦克风权限');
        return;
      }
      if (!mounted) return;
      final config = RecordConfig(
        encoder: kIsWeb ? AudioEncoder.opus : AudioEncoder.aacLc,
        sampleRate: 16000,
        numChannels: 1,
      );
      final stream = await _recorder.startStream(config);
      _subscription = stream.listen(
        (chunk) => _audioBytes.addAll(chunk),
        onError: (e) { if (mounted) setState(() => _error = '录音失败：$e'); },
      );
      if (mounted) setState(() => _recording = true);
    } catch (e) {
      if (mounted) setState(() => _error = '录音启动失败：$e');
    }
  }

  Future<void> _stopRecording() async {
    await _subscription?.cancel();
    _subscription = null;
    try {
      await _recorder.stop();
    } catch (_) {}
    if (mounted) setState(() => _recording = false);
  }

  Future<void> _finish() async {
    if (_processing) return;
    setState(() => _processing = true);
    await _stopRecording();
    if (_audioBytes.isEmpty) {
      if (mounted) {
        setState(() {
          _processing = false;
          _error = '没有录到声音，请重试';
        });
      }
      return;
    }
    final bytes = Uint8List.fromList(_audioBytes);
    final uploaded = await ApiClient().uploadAudio(
      bytes,
      filename: kIsWeb ? 'voice.opus' : 'voice.m4a',
    );
    final fileId = int.tryParse('${uploaded?['fileId']}');
    if (fileId == null) {
      if (mounted) {
        setState(() {
          _processing = false;
          _error = '语音上传失败，请重试';
        });
      }
      return;
    }
    final transcript = await AiApi.transcribe(fileId);
    if (!mounted) return;
    if (transcript == null || transcript.trim().isEmpty) {
      setState(() {
        _processing = false;
        _error = '没有识别到文字，请重试';
      });
      return;
    }
    Navigator.of(context).pop(transcript.trim());
  }

  void _cancel() {
    Navigator.of(context).pop();
    _stopRecording();
  }

  Future<void> _retry() async {
    await _stopRecording();
    await _startRecording();
  }

  @override
  void dispose() {
    _subscription?.cancel();
    _recorder.dispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFFF8FAFC),
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 10,
        bottom: MediaQuery.of(context).padding.bottom + 16,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Center(
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: const Color(0xFFCBD5E1),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 26),
          Text(
            widget.question,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            _error ??
                (_processing
                    ? '正在识别语音…'
                    : (_recording ? '正在聆听中 · 松开立即发送' : '准备录音…')),
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: _error == null
                  ? const Color(0xFF64748B)
                  : const Color(0xFFEF4444),
            ),
          ),
          const SizedBox(height: 24),
          SizedBox(
            height: 54,
            child: AnimatedBuilder(
              animation: _controller,
              builder: (context, _) {
                return Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: List.generate(17, (index) {
                    final phase = _controller.value * math.pi * 2 + index * 0.7;
                    final height = 10 + (math.sin(phase).abs() * 34);
                    final isCenter = (index - 8).abs() <= 2;
                    return Container(
                      width: 3,
                      height: height,
                      margin: const EdgeInsets.symmetric(horizontal: 2),
                      decoration: BoxDecoration(
                        color: isCenter
                            ? AppColors.brand
                            : const Color(0xFF93C5FD),
                        borderRadius: BorderRadius.circular(2),
                      ),
                    );
                  }),
                );
              },
            ),
          ),
          const SizedBox(height: 26),
          Row(
            children: [
              SizedBox(
                width: 70,
                child: TextButton(
                  onPressed: _cancel,
                  child: const Text(
                    '取消',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 13,
                      color: Color(0xFF64748B),
                    ),
                  ),
                ),
              ),
              Expanded(
                child: SizedBox(
                  height: 46,
                  child: ElevatedButton(
                    onPressed: _processing ? null : _finish,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.brand,
                      foregroundColor: Colors.white,
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(23),
                      ),
                    ),
                    child: Text(
                      _processing ? '识别中…' : '松开发送',
                      style: const TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 14,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
              ),
              SizedBox(
                width: 70,
                child: TextButton(
                  onPressed: _processing ? null : _retry,
                  child: const Text(
                    '重录',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 13,
                      color: Color(0xFF64748B),
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
}
