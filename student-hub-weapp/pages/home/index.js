const api = require('../../utils/api');
const format = require('../../utils/format');
const app = getApp();

const HOT_ITEM_CODES = ['REPAIR-DORM', 'AFFAIR-LEAVE', 'CERT-ENROLL', 'AFFAIR-VENUE'];

// 快捷入口：4 列两行，六个常用服务，最后一格为「更多」
const QUICK_SERVICES = [
  { code: 'REPAIR-DORM', label: '一键报修', icon: 'cat-repair' },
  { code: 'AFFAIR-LEAVE', label: '请假申请', icon: 'cat-leave' },
  { code: 'CERT-ENROLL', label: '证明开具', icon: 'cat-certificate' },
  { code: 'AFFAIR-VENUE', label: '场地申请', icon: 'cat-activity' },
  { code: 'GUIDE-CONSULT', label: '校园咨询', icon: 'cat-consult' },
  { code: 'REPAIR-PUBLIC', label: '教室报修', icon: 'cat-repair' }
];

Page({
  data: {
    loggedIn: false,
    userName: '同学',
    latestRequest: null,
    hotItems: [],
    quickServices: QUICK_SERVICES
  },

  onShow() {
    const loggedIn = app.isLoggedIn();
    this.setData({
      loggedIn,
      userName: loggedIn && app.globalData.user ? app.globalData.user.displayName : '同学'
    });
    if (!loggedIn) {
      this.setData({ latestRequest: null });
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
      // 首页进展卡加载失败不阻塞
    }
  },

  async loadCatalog() {
    try {
      const catalog = await this.ensureCatalog();
      const items = [];
      catalog.forEach((category) => {
        (category.items || []).forEach((item) => items.push(item));
      });
      const hot = HOT_ITEM_CODES.map((code) => items.find((item) => item.itemCode === code))
        .filter(Boolean)
        .slice(0, 4);
      this.setData({ hotItems: hot });
    } catch (error) {
      // 静默
    }
  },

  async ensureCatalog() {
    if (app.globalData.catalog && app.globalData.catalog.length) {
      return app.globalData.catalog;
    }
    const catalog = await api.getCatalog();
    app.globalData.catalog = catalog;
    return catalog;
  },

  refreshUnread() {
    api.unreadCount()
      .then((count) => {
        app.globalData.unreadCount = count;
      })
      .catch(() => {});
  },

  /** 快捷入口：按服务编码定位服务项并直达申请表单 */
  async goQuickService(event) {
    if (!app.ensureLogin()) {
      return;
    }
    const service = this.data.quickServices[Number(event.currentTarget.dataset.index)];
    if (!service) {
      return;
    }
    try {
      const catalog = await this.ensureCatalog();
      let itemId = null;
      catalog.forEach((category) => {
        (category.items || []).forEach((item) => {
          if (item.itemCode === service.code) {
            itemId = item.itemId;
          }
        });
      });
      if (itemId) {
        wx.navigateTo({ url: `/pages/service/apply?itemId=${itemId}` });
      } else {
        wx.switchTab({ url: '/pages/service/index' });
      }
    } catch (error) {
      console.error('快捷入口跳转失败', error);
      wx.switchTab({ url: '/pages/service/index' });
    }
  },

  goMoreServices() {
    wx.switchTab({ url: '/pages/service/index' });
  },

  goAssistant() {
    wx.switchTab({ url: '/pages/assistant/index' });
  },

  goRequestList() {
    wx.navigateTo({ url: '/pages/request/list' });
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/index' });
  },

  goRequestDetail() {
    if (this.data.latestRequest) {
      wx.navigateTo({ url: `/pages/request/detail?id=${this.data.latestRequest.requestId}` });
    }
  },

  goServiceDetail(event) {
    const item = this.data.hotItems[Number(event.currentTarget.dataset.index)];
    if (item && item.itemId) {
      wx.navigateTo({ url: `/pages/service/detail?id=${item.itemId}` });
    }
  }
});
