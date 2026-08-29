/**
 * 图片体积压缩模块。所有图片上传统一调用这里，避免各业务页面重复实现。
 * 微信端压缩失败时安全回退原图，服务端仍会做第二道尺寸/质量压缩。
 */
function compressImage(filePath, quality = 78) {
  if (!filePath || typeof wx.compressImage !== 'function') {
    return Promise.resolve(filePath);
  }
  return new Promise((resolve) => {
    wx.compressImage({
      src: filePath,
      quality,
      success: (res) => resolve(res.tempFilePath || filePath),
      fail: () => resolve(filePath)
    });
  });
}

module.exports = { compressImage };
