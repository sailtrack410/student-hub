const api = require('../../utils/api');
const config = require('../../utils/config');
const { getToken, saveSession } = require('../../utils/request');
const app = getApp();

const JW_STATUS_TEXT = {
  UNBOUND: '未认证',
  AUTHENTICATED: '已认证',
  EXPIRED: '已失效'
};

// 身份标识：名称与图标（身份由教务认证驱动，后勤由后台分配）
const IDENTITY = {
  STUDENT: { text: '学生', icon: 'icon-student' },
  TEACHER: { text: '教师', icon: 'icon-teacher' },
  LOGISTICS: { text: '后勤职工', icon: 'icon-logistics' },
  ADMIN: { text: '管理员', icon: 'icon-logistics' }
};

Page({
  data: {
    user: null,
    identityText: '未认证',
    identityIcon: 'icon-student',
    avatarUrl: '',
    isWorker: false,
    jwStatusText: '未认证',
    processingCount: 0,
    completedCount: 0,
    unreadCount: 0
  },

  onShow() {
    const user = app.isLoggedIn() ? app.globalData.user : null;
    if (user) {
      this.applyUser(user);
      this.loadStats();
      this.loadJwStatus();
    } else {
      this.setData({
        user: null,
        processingCount: 0,
        completedCount: 0,
        unreadCount: app.globalData.unreadCount || 0,
        jwStatusText: '未认证'
      });
    }
  },

  applyUser(user) {
    const identity = IDENTITY[user.identityType] || { text: '同学', icon: 'icon-student' };
    this.setData({
      user,
      identityText: identity.text,
      identityIcon: identity.icon,
      isWorker: (user.roles || []).indexOf('WORKER') >= 0
    });
    this.refreshAvatar();
  },

  /** 头像文件按需换取签名地址 */
  refreshAvatar() {
    const user = this.data.user;
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

  loadJwStatus() {
    api.jwStatus()
      .then((status) => {
        const text = JW_STATUS_TEXT[status.status] || status.status;
        this.setData({ jwStatusText: text });
        if (status.status === 'AUTHENTICATED' || status.status === 'EXPIRED') {
          const identity = IDENTITY[status.identityKind] || null;
          if (identity) {
            this.setData({ identityText: identity.text, identityIcon: identity.icon });
          }
        }
        app.globalData.unreadCount = app.globalData.unreadCount || 0;
      })
      .catch(() => {});
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

  goCenter() {
    if (!app.ensureLogin()) return;
    wx.navigateTo({ url: '/pages/profile/center' });
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
