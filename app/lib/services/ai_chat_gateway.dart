import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:image_picker/image_picker.dart';

import '../api/ai_api.dart';
import '../api/api_client.dart';
import 'image_compressor.dart';

class AiPickedAttachment {
  const AiPickedAttachment({
    required this.name,
    required this.bytes,
    this.isImage = false,
  });

  final String name;
  final Uint8List bytes;
  final bool isImage;
}

/// AI 请求边界，页面仅负责消息状态；测试可替换传输。
class AiChatGateway {
  const AiChatGateway();

  Future<String?> createConversation() => AiApi.createConversation();

  Future<List<Map<String, String>>> fetchRecommendations() =>
      AiApi.fetchRecommendations();

  Future<AiPickedAttachment?> pickImageAttachment() async {
    final file = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      imageQuality: 85,
    );
    if (file == null) return null;
    return AiPickedAttachment(
      name: file.name,
      bytes: compressImageBytes(await file.readAsBytes()),
      isImage: true,
    );
  }

  Future<AiPickedAttachment?> pickDocumentAttachment() async {
    final files = await FilePicker.pickFiles(
      type: FileType.custom,
      allowedExtensions: const ['pdf', 'docx', 'txt', 'md'],
    );
    final file = files.firstOrNull;
    if (file == null) return null;
    return AiPickedAttachment(name: file.name, bytes: await file.readAsBytes());
  }

  Future<Map<String, dynamic>?> uploadAttachment(AiPickedAttachment file) =>
      file.isImage
      ? ApiClient().uploadImage(file.bytes, filename: file.name)
      : ApiClient().uploadDocument(file.bytes, filename: file.name);

  Stream<AiStreamEvent> streamMessage({
    required String conversationId,
    required String content,
    String? clientRequestId,
    required List<Map<String, dynamic>> attachments,
  }) => AiApi.streamMessage(
    conversationId: conversationId,
    content: content,
    clientRequestId: clientRequestId,
    attachments: attachments,
  );

  Future<Map<String, dynamic>?> sendMessage({
    required String conversationId,
    required String content,
    String? clientRequestId,
    required List<Map<String, dynamic>> attachments,
  }) => AiApi.sendMessage(
    conversationId: conversationId,
    content: content,
    clientRequestId: clientRequestId,
    attachments: attachments,
  );
}
