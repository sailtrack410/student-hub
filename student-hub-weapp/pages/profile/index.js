const api = require('../../utils/api');
const app = getApp();

const ROLE_TEXT = {
  PLATFORM_ADMIN: '平台管理员',
  REQUESTER: '服务申请人',
  WORKER: '服务人员',
  DISPATCHER: '调度员'
};

const AUTH_TEXT = {
  BASIC: '基础认证',
  AUTHORIZED: '已认证'
};

Page({
  data: {
    user: null,
    roleText: '',
    authText: '',
    isWorker: false,
    processingCount: 0,
    completedCount: 0,
    unreadCount: 0
  },

  onShow() {
    const user = app.isLoggedIn() ? app.globalData.user : null;
    if (user) {
      this.setData({
        user,
        roleText: this.resolveRoleText(user.roles),
        authText: AUTH_TEXT[user.authorizationStatus] || user.authorizationStatus,
        isWorker: (user.roles || []).indexOf('WORKER') >= 0
      });
      this.loadStats();
    } else {
      this.setData({ user: null, processingCount: 0, completedCount: 0, unreadCount: app.globalData.unreadCount || 0 });
    }
  },

  resolveRoleText(roles) {
    if (!roles || !roles.length) {
      return '同学';
    }
    return roles.map((role) => ROLE_TEXT[role] || role).join(' / ');
  },

  async loadStats() {
    try {
      const [processing, completed, unread] = await Promise.all([
        api.pageMyRequests({ pageNum: 1, pageSize: 1, status: 'PROCESSING' }),
        api.pageMyRequests({ pageNum: 1, pageSize: 1, status: 'COMPLETED' }),
        api.unreadCount()
      ]);
      this.setData({
        processingCount: processing.total || 0,
        completedCount: completed.total || 0,
        unreadCount: unread || 0
      });
      app.globalData.unreadCount = unread || 0;
    } catch (error) {
      // 静默
    }
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/index' });
  },

  goRequests() {
    if (!app.ensureLogin()) return;
    wx.navigateTo({ url: '/pages/request/list' });
  },

  goNotifications() {
    if (!app.ensureLogin()) return;
    wx.navigateTo({ url: '/pages/notification/list' });
  },

  goMyOrders() {
    if (!app.ensureLogin()) return;
    wx.navigateTo({ url: '/pages/order/list' });
  },

  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      confirmText: '退出',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await api.logout();
        } catch (error) {
          // 即使服务端失败也清理本地会话
        }
        app.logout();
        this.setData({ user: null, processingCount: 0, completedCount: 0, unreadCount: 0 });
        wx.showToast({ title: '已退出', icon: 'success' });
      }
    });
  }
});
