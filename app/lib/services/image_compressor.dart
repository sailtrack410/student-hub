import 'dart:typed_data';

import 'package:image/image.dart' as img;

/// 本地压缩图片：限制最长边并转 JPEG，减少上传体积。
Uint8List compressImageBytes(
  Uint8List bytes, {
  int maxSide = 1600,
  int quality = 80,
}) {
  try {
    final decoded = img.decodeImage(bytes);
    if (decoded == null) return bytes;
    var image = decoded;
    final longest = image.width > image.height ? image.width : image.height;
    if (longest > maxSide) {
      final ratio = maxSide / longest;
      image = img.copyResize(
        image,
        width: (image.width * ratio).round(),
        height: (image.height * ratio).round(),
        interpolation: img.Interpolation.linear,
      );
    }
    return Uint8List.fromList(img.encodeJpg(image, quality: quality));
  } catch (_) {
    return bytes;
  }
}
