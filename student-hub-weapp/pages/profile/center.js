const api = require('../../utils/api');
const config = require('../../utils/config');
const format = require('../../utils/format');
const { uploadImage } = require('../../utils/request');
const app = getApp();

const STATUS_TEXT = {
  UNBOUND: '未认证',
  AUTHENTICATED: '已认证',
  EXPIRED: '已失效'
};

const STATUS_THEME = {
  UNBOUND: 'gray',
  AUTHENTICATED: 'green',
  EXPIRED: 'amber'
};

Page({
  data: {
    avatarUrl: '',
    displayName: '',
    phone: '',
    showManualPhone: false,
    manualPhoneValue: '',
    jwBound: false,
    jwStatusText: '未认证',
    jwStatusTheme: 'gray',
    jwExpired: false,
    ssoAccount: '',
    jwName: '',
    grade: '',
    college: '',
    major: '',
    className: '',
    jwStudentNo: ''
  },

  onShow() {
    this.loadAll();
  },

  loadAll() {
    this.loadAccount();
    this.loadJwStatus();
  },

  loadAccount() {
    const user = app.globalData.user;
    if (!user) {
      return;
    }
    this.setData({ displayName: user.displayName || '', phone: user.phone || '' });
    this.refreshAvatar();
  },

  /** 头像文件按需换取签名地址展示 */
  refreshAvatar() {
    const user = app.globalData.user;
    if (!user || !user.avatarPath) {
      this.setData({ avatarUrl: '' });
      return;
    }
    const fileId = (String(user.avatarPath).match(/view\/(\d+)/) || [])[1];
    if (!fileId) {
      this.setData({ avatarUrl: '' });
      return;
    }
    api.fileViewUrl(fileId)
      .then((path) => this.setData({ avatarUrl: config.baseUrl + path }))
      .catch(() => this.setData({ avatarUrl: '' }));
  },

  async loadJwStatus() {
    try {
      const status = await api.jwStatus();
      const bound = status.status !== 'UNBOUND';
      this.setData({
        jwBound: bound,
        jwExpired: status.status === 'EXPIRED',
        jwStatusText: STATUS_TEXT[status.status] || status.status,
        jwStatusTheme: STATUS_THEME[status.status] || 'gray',
        ssoAccount: status.ssoAccount || '',
        jwName: status.jwName || '',
        grade: status.grade || '',
        college: status.college || '',
        major: status.major || '',
        className: status.className || '',
        jwStudentNo: status.jwStudentNo || ''
      });
    } catch (error) {
      this.setData({ jwBound: false, jwStatusText: '未认证', jwStatusTheme: 'gray' });
    }
  },

  persistUser(user) {
    const { saveSession, getToken } = require('../../utils/request');
    saveSession(getToken(), user);
    app.globalData.user = user;
  },

  onDisplayNameInput(event) {
    this.setData({ displayName: event.detail.value });
  },

  async saveDisplayName() {
    const displayName = this.data.displayName.trim();
    if (!displayName) {
      wx.showToast({ title: '昵称不能为空', icon: 'none' });
      return;
    }
    try {
      const user = await api.updateProfile({ displayName });
      this.persistUser(user);
      wx.showToast({ title: '已保存', icon: 'success' });
    } catch (error) {
      // 请求层已提示
    }
  },

  /** 微信头像选择：上传 MinIO 后绑定 fileId */
  async onChooseAvatar(event) {
    const avatarUrl = event.detail.avatarUrl;
    if (!avatarUrl) {
      return;
    }
    wx.showLoading({ title: '上传中' });
    try {
      const stored = await uploadImage(avatarUrl);
      const user = await api.updateProfile({ avatarFileId: Number(stored.fileId) });
      this.persistUser(user);
      wx.showToast({ title: '头像已更新', icon: 'success' });
      this.refreshAvatar();
    } catch (error) {
      // 请求层已提示
    } finally {
      wx.hideLoading();
    }
  },

  /** 微信绑定手机号一键获取（需小程序完成微信认证） */
  async onWechatPhone(event) {
    const code = event.detail && event.detail.code;
    if (!code) {
      // 用户拒绝授权，引导手动填写
      this.setData({ showManualPhone: true });
      return;
    }
    try {
      const user = await api.updatePhoneWechat(code);
      this.persistUser(user);
      wx.showToast({ title: '手机号已绑定', icon: 'success' });
    } catch (error) {
      // 未认证等场景降级为手动填写
      this.setData({ showManualPhone: true });
      wx.showToast({ title: error.message || '获取失败，请手动填写', icon: 'none' });
    }
  },

  manualPhone() {
    this.setData({ showManualPhone: true });
  },

  onManualPhoneInput(event) {
    this.setData({ manualPhoneValue: event.detail.value });
  },

  async saveManualPhone() {
    const phone = this.data.manualPhoneValue.trim();
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '手机号格式不正确', icon: 'none' });
      return;
    }
    try {
      const user = await api.updatePhoneManual(phone);
      this.persistUser(user);
      this.setData({ showManualPhone: false, manualPhoneValue: '' });
      wx.showToast({ title: '手机号已绑定', icon: 'success' });
    } catch (error) {
      // 请求层已提示
    }
  },

  goReauth() {
    wx.navigateTo({ url: '/pages/jw/auth' });
  }
});
