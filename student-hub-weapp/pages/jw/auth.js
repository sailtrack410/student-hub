const api = require('../../utils/api');
const format = require('../../utils/format');

const STATUS_TEXT = {
  UNBOUND: '未认证',
  AUTHENTICATED: '已认证',
  EXPIRED: '已失效'
};

const STATUS_THEME = {
  UNBOUND: 'UNBOUND',
  AUTHENTICATED: 'AUTHENTICATED',
  EXPIRED: 'EXPIRED'
};

Page({
  data: {
    statusText: '加载中',
    statusTheme: 'UNBOUND',
    bound: false,
    ssoAccount: '',
    jwName: '',
    expiresText: '',
    captchaSrc: '',
    captchaUid: '',
    ssoAccountInput: '',
    password: '',
    captchaCode: '',
    authing: false
  },

  onShow() {
    this.loadStatus();
    this.refreshCaptcha();
  },

  async loadStatus() {
    try {
      const status = await api.jwStatus();
      const bound = status.status !== 'UNBOUND';
      this.setData({
        statusText: STATUS_TEXT[status.status] || status.status,
        statusTheme: STATUS_THEME[status.status] || 'UNBOUND',
        bound,
        ssoAccount: status.ssoAccount || '',
        jwName: status.jwName || '',
        expiresText: status.sessionExpiresAt ? format.formatTime(status.sessionExpiresAt) : ''
      });
    } catch (error) {
      this.setData({ statusText: '未认证', statusTheme: 'UNBOUND', bound: false });
    }
  },

  async refreshCaptcha() {
    this.setData({ captchaSrc: '' });
    try {
      const captcha = await api.jwCaptcha();
      this.setData({
        captchaUid: captcha.uid,
        captchaSrc: `data:image/png;base64,${captcha.imageBase64}`
      });
    } catch (error) {
      wx.showToast({ title: '验证码加载失败', icon: 'none' });
    }
  },

  onAccountInput(event) {
    this.setData({ ssoAccountInput: event.detail.value });
  },

  onPasswordInput(event) {
    this.setData({ password: event.detail.value });
  },

  onCaptchaInput(event) {
    this.setData({ captchaCode: event.detail.value });
  },

  async handleSubmit() {
    if (this.data.authing) {
      return;
    }
    const account = this.data.ssoAccountInput.trim() || this.data.ssoAccount;
    const captchaCode = this.data.captchaCode.trim();
    if (!account) {
      wx.showToast({ title: '请输入统一身份账号', icon: 'none' });
      return;
    }
    if (!this.data.password) {
      wx.showToast({ title: '请输入密码', icon: 'none' });
      return;
    }
    if (!captchaCode) {
      wx.showToast({ title: '请输入验证码', icon: 'none' });
      return;
    }
    this.setData({ authing: true });
    try {
      const result = await api.jwAuth({
        ssoAccount: account,
        password: this.data.password,
        captchaUid: this.data.captchaUid,
        captchaCode
      });
      const identityText = result.identityKind === 'TEACHER' ? '教师' : '学生';
      wx.showToast({ title: `认证成功，身份已设为${identityText}`, icon: 'success' });
      this.setData({ password: '', captchaCode: '' });
      this.loadStatus();
      this.refreshCaptcha();
    } catch (error) {
      // 请求层已提示；验证码一次性，失败后刷新
      this.refreshCaptcha();
    } finally {
      this.setData({ authing: false });
    }
  },

  onUnbind() {
    wx.showModal({
      title: '解除教务绑定',
      content: '解绑后将清除教务会话，课表等功能需要重新认证，确定继续吗？',
      confirmText: '解绑',
      confirmColor: '#DC2626',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await api.jwUnbind();
          wx.showToast({ title: '已解绑', icon: 'success' });
          this.loadStatus();
          this.refreshCaptcha();
        } catch (error) {
          // 请求层已提示
        }
      }
    });
  }
});
