const config = require('./config');
const { compressImage } = require('./image');

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

// 微信会把对象中的 undefined 查询值序列化成字符串 "undefined"，导致后端枚举参数解析失败。
function cleanRequestData(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    return data;
  }
  const cleaned = {};
  Object.keys(data).forEach((key) => {
    if (data[key] !== undefined) {
      cleaned[key] = data[key];
    }
  });
  return cleaned;
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
  const { url, method = 'GET', data, header = {}, silent = false, timeout = 15000 } = options;
  return new Promise((resolve, reject) => {
    const token = getToken();
    wx.request({
      url: `${config.baseUrl}${url}`,
      method,
      data: cleanRequestData(data),
      timeout,
      header: Object.assign(
        { 'Content-Type': 'application/json' },
        token ? { Authorization: `Bearer ${token}` } : {},
        header
      ),
      success(res) {
        const body = res.data;
        // 平台会话失效：仅识别平台自身的未认证码，业务型 401（如教务认证失败）按业务错误提示
        if (res.statusCode === 401 && (!body || !body.code || body.code === 'AUTH_401')) {
          clearSession();
          wx.navigateTo({ url: '/pages/login/index' });
          reject(new Error('请先登录'));
          return;
        }
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

async function uploadImage(filePath) {
  const compressedPath = await compressImage(filePath);
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${config.baseUrl}/api/app/files`,
      filePath: compressedPath,
      name: 'file',
      header: { Authorization: `Bearer ${getToken()}` },
      success(res) {
        let body = {};
        try {
          body = JSON.parse(res.data);
        } catch (error) {
          body = {};
        }
        if (res.statusCode === 200 && body.success) {
          resolve(body.data);
          return;
        }
        const message = (body && body.message) || '上传失败';
        wx.showToast({ title: message, icon: 'none' });
        reject({ message });
      },
      fail() {
        wx.showToast({ title: '上传失败，请稍后再试', icon: 'none' });
        reject(new Error('upload failed'));
      }
    });
  });
}

function uploadAudio(filePath) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${config.baseUrl}/api/app/files/audio`,
      filePath,
      name: 'file',
      header: { Authorization: `Bearer ${getToken()}` },
      timeout: 60000,
      success(res) {
        let body = {};
        try {
          body = JSON.parse(res.data);
        } catch (error) {
          body = {};
        }
        if (res.statusCode === 200 && body.success) {
          resolve(body.data);
          return;
        }
        const message = (body && body.message) || '音频上传失败';
        wx.showToast({ title: message, icon: 'none' });
        reject({ message });
      },
      fail() {
        wx.showToast({ title: '音频上传失败，请稍后再试', icon: 'none' });
        reject(new Error('upload audio failed'));
      }
    });
  });
}

module.exports = { request, uploadImage, uploadAudio, getToken, getUser, saveSession, clearSession, ensureLogin };
