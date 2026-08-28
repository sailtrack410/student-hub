const { getToken, getUser, saveSession, clearSession } = require('./utils/request');

App({
  globalData: {
    user: null,
    unreadCount: 0,
    catalog: null
  },

  onLaunch() {
    if (getToken()) {
      this.globalData.user = getUser();
      this.refreshCurrentUser();
    }
  },

  isLoggedIn() {
    return Boolean(getToken());
  },

  setSession(token, user) {
    saveSession(token, user);
    this.globalData.user = user;
  },

  logout() {
    clearSession();
    this.globalData.user = null;
    this.globalData.catalog = null;
    this.globalData.unreadCount = 0;
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
