import 'dart:typed_data';

import 'package:flutter/material.dart';

/// 待上传 / 已上传图片的展示状态。
class ImageUploadItem {
  final String name;
  final Uint8List bytes;
  final String? fileId;
  final bool uploading;
  final bool failed;

  const ImageUploadItem({
    required this.name,
    required this.bytes,
    this.fileId,
    this.uploading = false,
    this.failed = false,
  });

  ImageUploadItem copyWith({String? fileId, bool? uploading, bool? failed}) {
    return ImageUploadItem(
      name: name,
      bytes: bytes,
      fileId: fileId ?? this.fileId,
      uploading: uploading ?? this.uploading,
      failed: failed ?? this.failed,
    );
  }

  bool get isDone =>
      !uploading && !failed && fileId != null && fileId!.isNotEmpty;
}

/// 通用图片上传网格：服务申请和报修表单共用。
///
/// 不负责选择/上传，只负责预览、删除、失败重试与“添加”入口。
class ImageUploadGrid extends StatelessWidget {
  final List<ImageUploadItem> items;
  final int maxCount;
  final bool enabled;
  final VoidCallback? onAdd;
  final ValueChanged<int>? onRemove;
  final ValueChanged<int>? onRetry;

  const ImageUploadGrid({
    super.key,
    required this.items,
    required this.maxCount,
    this.enabled = true,
    this.onAdd,
    this.onRemove,
    this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 10,
      runSpacing: 10,
      children: [
        for (var i = 0; i < items.length; i++) _buildItem(context, i),
        if (items.length < maxCount) _buildAddTile(),
      ],
    );
  }

  Widget _buildItem(BuildContext context, int index) {
    final item = items[index];
    return SizedBox(
      width: 72,
      height: 72,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: Image.memory(
              item.bytes,
              width: 72,
              height: 72,
              fit: BoxFit.cover,
              gaplessPlayback: true,
            ),
          ),
          if (item.uploading)
            Positioned.fill(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: Colors.black.withValues(alpha: 0.42),
                  borderRadius: BorderRadius.circular(10),
                ),
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
            ),
          if (item.failed)
            Positioned.fill(
              child: GestureDetector(
                onTap: onRetry == null ? null : () => onRetry!(index),
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.48),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        Icons.refresh_rounded,
                        color: Colors.white,
                        size: 20,
                      ),
                      SizedBox(height: 2),
                      Text(
                        '重试',
                        style: TextStyle(color: Colors.white, fontSize: 12),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          Positioned(
            top: -6,
            right: -6,
            child: GestureDetector(
              onTap: enabled && onRemove != null
                  ? () => onRemove!(index)
                  : null,
              child: Container(
                width: 20,
                height: 20,
                decoration: const BoxDecoration(
                  color: Color(0xFF0F172A),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.close_rounded,
                  size: 13,
                  color: Colors.white,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAddTile() {
    return GestureDetector(
      onTap: enabled ? onAdd : null,
      child: Container(
        width: 72,
        height: 72,
        decoration: BoxDecoration(
          color: const Color(0xFFF8FAFC),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: const Color(0xFFCBD5E1)),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              Icons.add_photo_alternate_outlined,
              size: 22,
              color: Color(0xFF94A3B8),
            ),
            const SizedBox(height: 4),
            Text(
              '${items.length}/$maxCount',
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
}
