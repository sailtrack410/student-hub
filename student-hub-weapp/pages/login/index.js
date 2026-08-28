const api = require('../../utils/api');
const app = getApp();

Page({
  data: {
    loading: false,
    isDevEnv: false
  },

  onLoad() {
    // 开发者工具 envVersion 为 develop，仅在该环境展示固定账号调试入口
    try {
      const info = wx.getAccountInfoSync ? wx.getAccountInfoSync() : null;
      this.setData({ isDevEnv: info && info.miniProgram && info.miniProgram.envVersion === 'develop' });
    } catch (error) {
      this.setData({ isDevEnv: false });
    }
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
  },

  /** 开发调试：固定账号，每次进入都是同一个用户 */
  async handleDevLogin() {
    if (this.data.loading) {
      return;
    }
    this.setData({ loading: true });
    try {
      const data = await api.devWechatLogin();
      app.setSession(data.accessToken, data.user);
      wx.showToast({ title: '调试账号登录成功', icon: 'success' });
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
