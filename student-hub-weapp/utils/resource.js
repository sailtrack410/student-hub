const config = require('./config');

/** 将后端返回的绝对对象地址原样交给 image 组件；旧相对路径仍走本地 API。 */
function resourceUrl(path) {
  if (!path) return '';
  if (path.indexOf('http://') === 0 || path.indexOf('https://') === 0) return path;
  if (path.indexOf('/assets/') === 0) return config.assetBaseUrl + path.substring('/assets'.length);
  return config.baseUrl + path;
}

// MinIO 预签名地址不能在签名后追加查询参数；绝对对象地址直接作为首屏图，
// 旧的本地签名地址才追加后端支持的 thumb 参数。
function thumbnailUrl(url) {
  if (!url) return '';
  if (url.indexOf('http://') === 0 || url.indexOf('https://') === 0) return url;
  return `${url}${url.indexOf('?') >= 0 ? '&' : '?'}thumb=true`;
}

module.exports = { resourceUrl, thumbnailUrl };
