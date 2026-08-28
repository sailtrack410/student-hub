const api = require('../../utils/api');
const { getToken, saveSession } = require('../../utils/request');
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

const IDENTITY_TEXT = {
  STUDENT: '学生',
  TEACHER: '教师',
  ADMIN: '管理员',
  LOGISTICS: '后勤职工'
};

const JW_STATUS_TEXT = {
  UNBOUND: '未认证',
  AUTHENTICATED: '已认证',
  EXPIRED: '已失效'
};

// 允许自行申报的身份；其余身份由平台分配
const SELF_IDENTITIES = [
  { type: 'STUDENT', label: '学生' },
  { type: 'TEACHER', label: '教师' }
];

Page({
  data: {
    user: null,
    roleText: '',
    authText: '',
    identityText: '',
    jwStatusText: '未认证',
    jwStatusTheme: 'UNBOUND',
    isWorker: false,
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
      this.setData({ user: null, processingCount: 0, completedCount: 0, unreadCount: app.globalData.unreadCount || 0 });
    }
  },

  applyUser(user) {
    this.setData({
      user,
      roleText: this.resolveRoleText(user.roles),
      authText: AUTH_TEXT[user.authorizationStatus] || user.authorizationStatus,
      identityText: IDENTITY_TEXT[user.identityType] || user.identityType || '未设置',
      isWorker: (user.roles || []).indexOf('WORKER') >= 0
    });
  },

  /** 更新后写回本地会话，保证重启后仍生效 */
  persistUser(user) {
    saveSession(getToken(), user);
    app.globalData.user = user;
    this.applyUser(user);
  },

  /** 教务认证三态：未认证/已认证/已失效，点击进入认证页 */
  loadJwStatus() {
    api.jwStatus()
      .then((status) => {
        let text = JW_STATUS_TEXT[status.status] || status.status;
        if (status.status === 'AUTHENTICATED' && status.jwName) {
          text = `已认证 · ${status.jwName}`;
        }
        this.setData({
          jwStatusText: text,
          jwStatusTheme: status.status
        });
      })
      .catch(() => {});
  },

  goJwAuth() {
    if (!app.ensureLogin()) return;
    wx.navigateTo({ url: '/pages/jw/auth' });
  },

  editDisplayName() {
    if (!this.data.user) {
      return;
    }
    wx.showModal({
      title: '修改昵称',
      editable: true,
      placeholderText: '请输入新昵称',
      content: this.data.user.displayName || '',
      success: async (res) => {
        if (!res.confirm) return;
        const displayName = (res.content || '').trim();
        if (!displayName) {
          wx.showToast({ title: '昵称不能为空', icon: 'none' });
          return;
        }
        try {
          const user = await api.updateProfile(displayName);
          this.persistUser(user);
          wx.showToast({ title: '已保存', icon: 'success' });
        } catch (error) {
          // 请求层已提示
        }
      }
    });
  },

  chooseIdentity() {
    if (!this.data.user) {
      return;
    }
    if (SELF_IDENTITIES.every((item) => item.type !== this.data.user.identityType)) {
      wx.showToast({ title: '当前身份由平台统一管理', icon: 'none' });
      return;
    }
    wx.showActionSheet({
      itemList: SELF_IDENTITIES.map((item) => item.label),
      success: async (res) => {
        const target = SELF_IDENTITIES[res.tapIndex];
        if (!target || target.type === this.data.user.identityType) {
          return;
        }
        try {
          const user = await api.updateIdentity(target.type);
          this.persistUser(user);
          wx.showToast({ title: `身份已设为${target.label}`, icon: 'success' });
        } catch (error) {
          // 请求层已提示
        }
      }
    });
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
