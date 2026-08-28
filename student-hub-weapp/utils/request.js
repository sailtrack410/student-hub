const config = require('./config');

const TOKEN_KEY = 'accessToken';
const USER_KEY = 'user';

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || null;
}

function getUser() {
  return wx.getStorageSync(USER_KEY) || null;
}

function saveSession(token, user) {
  wx.setStorageSync(TOKEN_KEY, token);
  wx.setStorageSync(USER_KEY, user);
}

function clearSession() {
  wx.removeStorageSync(TOKEN_KEY);
  wx.removeStorageSync(USER_KEY);
}

function ensureLogin() {
  if (getToken()) {
    return true;
  }
  wx.navigateTo({ url: '/pages/login/index' });
  return false;
}

/**
 * 统一请求封装：自动携带 Token、解包 Result<T>、401 时清理会话并跳转登录。
 * 返回 Promise，resolve 为 Result 的 data 字段。
 */
function request(options) {
  const { url, method = 'GET', data, header = {}, silent = false } = options;
  return new Promise((resolve, reject) => {
    const token = getToken();
    wx.request({
      url: `${config.baseUrl}${url}`,
      method,
      data,
      timeout: 15000,
      header: Object.assign(
        { 'Content-Type': 'application/json' },
        token ? { Authorization: `Bearer ${token}` } : {},
        header
      ),
      success(res) {
        if (res.statusCode === 401) {
          clearSession();
          wx.navigateTo({ url: '/pages/login/index' });
          reject(new Error('请先登录'));
          return;
        }
        const body = res.data;
        if (res.statusCode >= 200 && res.statusCode < 300 && body && body.success) {
          resolve(body.data);
          return;
        }
        const message = (body && body.message) || `请求失败（${res.statusCode}）`;
        if (!silent) {
          wx.showToast({ title: message, icon: 'none' });
        }
        reject({ code: body && body.code, message });
      },
      fail(err) {
        if (!silent) {
          wx.showToast({ title: '网络不给力，请稍后再试', icon: 'none' });
        }
        reject(err);
      }
    });
  });
}

function uploadFile(filePath, formData) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${config.baseUrl}/api/common/files/upload`,
      filePath,
      name: 'file',
      formData,
      header: { Authorization: `Bearer ${getToken()}` },
      success: resolve,
      fail: reject
    });
  });
}

module.exports = { request, uploadFile, getToken, getUser, saveSession, clearSession, ensureLogin };
