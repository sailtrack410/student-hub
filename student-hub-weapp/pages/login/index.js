const api = require('../../utils/api');
const app = getApp();

Page({
  data: {
    loading: false
  },

  async handleLogin() {
    if (this.data.loading) {
      return;
    }
    this.setData({ loading: true });
    try {
      const { code } = await new Promise((resolve, reject) => {
        wx.login({ success: resolve, fail: reject });
      });
      const data = await api.wechatLogin(code);
      app.setSession(data.accessToken, data.user);
      wx.showToast({ title: data.isNewUser ? '欢迎加入！' : '欢迎回来！', icon: 'success' });
      setTimeout(() => {
        wx.switchTab({ url: '/pages/home/index' });
      }, 600);
    } catch (error) {
      const message = error && error.message ? error.message : '登录失败，请稍后再试';
      wx.showToast({ title: message, icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  }
});
