const { getToken, getUser, saveSession, clearSession, ensureLogin } = require('./utils/request');

App({
  globalData: {
    user: null,
    unreadCount: 0,
    communityUnreadCount: 0,
    catalog: null
  },

  onLaunch() {
    if (getToken()) {
      this.globalData.user = getUser();
      this.refreshCurrentUser();
      this.refreshCommunityUnread();
      this.startUnreadPolling();
    }
  },

  isLoggedIn() {
    return Boolean(getToken());
  },

  /** 未登录时跳转登录页并返回 false，已登录返回 true */
  ensureLogin() {
    return ensureLogin();
  },

  setSession(token, user) {
    saveSession(token, user);
    this.globalData.user = user;
    this.refreshCommunityUnread();
    this.startUnreadPolling();
  },

  logout() {
    clearSession();
    this.globalData.user = null;
    this.globalData.catalog = null;
    this.globalData.unreadCount = 0;
    this.globalData.communityUnreadCount = 0;
    this.stopUnreadPolling();
    if (wx.removeTabBarBadge) {
      wx.removeTabBarBadge({ index: 3 });
      wx.removeTabBarBadge({ index: 4 });
    }
  },

  onShow() {
    if (getToken()) {
      this.refreshCommunityUnread();
      this.startUnreadPolling();
    }
  },

  onHide() {
    this.stopUnreadPolling();
  },

  startUnreadPolling() {
    if (this._unreadTimer || !getToken()) return;
    this._unreadTimer = setInterval(() => {
      if (getToken()) this.refreshCommunityUnread();
    }, 15000);
  },

  stopUnreadPolling() {
    if (!this._unreadTimer) return;
    clearInterval(this._unreadTimer);
    this._unreadTimer = null;
  },

  refreshCommunityUnread() {
    if (!getToken()) return;
    const { communityUnreadCount, unreadCount } = require('./utils/api');
    const currentCommunityCount = this.globalData.communityUnreadCount || 0;
    const currentMessageCount = this.globalData.unreadCount || 0;
    Promise.all([
      communityUnreadCount().catch(() => currentCommunityCount),
      unreadCount().catch(() => currentMessageCount)
    ]).then(([communityCount, messageCount]) => {
      if (!getToken()) return;
      const communityValue = Number(communityCount) || 0;
      const messageValue = Number(messageCount) || 0;
      const total = communityValue + messageValue;
      this.globalData.communityUnreadCount = communityValue;
      this.globalData.unreadCount = messageValue;
      if (wx.removeTabBarBadge) wx.removeTabBarBadge({ index: 3 });
      if (total > 0 && wx.setTabBarBadge) {
        wx.setTabBarBadge({ index: 4, text: total > 99 ? '99+' : String(total) });
      } else if (wx.removeTabBarBadge) {
        wx.removeTabBarBadge({ index: 4 });
      }
    }).catch(() => {});
  },

  /** 会话可能已在服务端失效，静默刷新一次用户信息 */
  refreshCurrentUser() {
    const { getCurrentUser } = require('./utils/api');
    getCurrentUser()
      .then((user) => {
        this.globalData.user = user;
        saveSession(getToken(), user);
      })
      .catch(() => {});
  }
});
