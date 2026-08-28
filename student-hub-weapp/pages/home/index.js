const api = require('../../utils/api');
const format = require('../../utils/format');
const app = getApp();

const HOT_ITEM_CODES = ['REPAIR-DORM', 'AFFAIR-LEAVE', 'CERT-ENROLL', 'AFFAIR-VENUE', 'GUIDE-CONSULT', 'REPAIR-PUBLIC'];

Page({
  data: {
    loggedIn: false,
    userName: '同学',
    latestRequest: null,
    hotItems: []
  },

  onShow() {
    const loggedIn = app.isLoggedIn();
    this.setData({
      loggedIn,
      userName: loggedIn && app.globalData.user ? app.globalData.user.displayName : '同学'
    });
    if (!loggedIn) {
      this.setData({ latestRequest: null, hotItems: this.data.hotItems });
      return;
    }
    this.loadLatestRequest();
    this.loadCatalog();
    this.refreshUnread();
  },

  async loadLatestRequest() {
    try {
      const page = await api.pageMyRequests({ pageNum: 1, pageSize: 1 });
      const record = page.records && page.records[0];
      if (!record) {
        this.setData({ latestRequest: null });
        return;
      }
      const status = format.requestStatus(record.status);
      this.setData({
        latestRequest: Object.assign({}, record, {
          statusText: status.text,
          statusTheme: status.theme,
          submittedAtText: format.formatTime(record.submittedAt)
        })
      });
    } catch (error) {
      // 静默失败，首页不阻塞
    }
  },

  async loadCatalog() {
    try {
      let catalog = app.globalData.catalog;
      if (!catalog) {
        catalog = await api.getCatalog();
        app.globalData.catalog = catalog;
      }
      const items = [];
      catalog.forEach((category) => {
        (category.items || []).forEach((item) => items.push(item));
      });
      const hot = HOT_ITEM_CODES.map((code) => items.find((item) => item.itemCode === code))
        .filter(Boolean)
        .slice(0, 4);
      this.setData({ hotItems: hot });
    } catch (error) {
      // 静默失败
    }
  },

  refreshUnread() {
    api.unreadCount()
      .then((count) => {
        app.globalData.unreadCount = count;
      })
      .catch(() => {});
  },

  goRepair() { this.jumpWithItem('REPAIR-DORM'); },
  goLeave() { this.jumpWithItem('AFFAIR-LEAVE'); },
  goCertificate() { this.jumpWithItem('CERT-ENROLL'); },
  goAssistant() { wx.switchTab({ url: '/pages/assistant/index' }); },
  goRequestList() { wx.navigateTo({ url: '/pages/request/list' }); },
  goService() { wx.switchTab({ url: '/pages/service/index' }); },
  goLogin() { wx.navigateTo({ url: '/pages/login/index' }); },

  goRequestDetail() {
    if (this.data.latestRequest) {
      wx.navigateTo({ url: `/pages/request/detail?id=${this.data.latestRequest.requestId}` });
    }
  },

  goServiceDetail(event) {
    wx.navigateTo({ url: `/pages/service/detail?id=${event.currentTarget.dataset.itemId}` });
  },

  /** 快捷入口直接落到对应服务项；未登录先去登录 */
  jumpWithItem(itemCode) {
    if (!app.ensureLogin()) {
      return;
    }
    const load = app.globalData.catalog
      ? Promise.resolve(app.globalData.catalog)
      : api.getCatalog().then((catalog) => {
          app.globalData.catalog = catalog;
          return catalog;
        });
    load.then((catalog) => {
      let itemId = null;
      catalog.forEach((category) => {
        (category.items || []).forEach((item) => {
          if (item.itemCode === itemCode) {
            itemId = item.itemId;
          }
        });
      });
      if (itemId) {
        wx.navigateTo({ url: `/pages/service/apply?itemId=${itemId}` });
      } else {
        wx.switchTab({ url: '/pages/service/index' });
      }
    }).catch(() => {});
  }
});
