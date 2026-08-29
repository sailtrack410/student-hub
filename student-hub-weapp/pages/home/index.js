const format = require('../../utils/format');
const directory = require('../../utils/directory');
const api = require('../../utils/api');
const app = getApp();

Page({
  data: {
    loggedIn: false,
    userName: '同学',
    latestRequest: null,
    hotItems: [],
    quickServices: [],
    scheduleBrief: ''
  },

  onShow() {
    const loggedIn = app.isLoggedIn();
    this.setData({
      loggedIn,
      userName: loggedIn && app.globalData.user ? app.globalData.user.displayName : '同学'
    });
    if (!loggedIn) {
      this.setData({ latestRequest: null, scheduleBrief: '' });
      return;
    }
    this.loadLatestRequest();
    this.loadServices();
    this.loadScheduleBrief();
    this.refreshUnread();
  },

  /** 课表入口：静默拉取视图，仅用于首页摘要展示 */
  async loadScheduleBrief() {
    try {
      const schedule = await api.getScheduleView();
      if (schedule && schedule.courses && schedule.courses.length) {
        this.setData({
          scheduleBrief: `${schedule.semesterName || '本学期'} · ${schedule.courses.length} 门课`
        });
      } else {
        this.setData({ scheduleBrief: '' });
      }
    } catch (error) {
      this.setData({ scheduleBrief: '' });
    }
  },

  /** 快捷入口与常用服务都从目录数据派生（与大厅同一数据源） */
  async loadServices() {
    try {
      const [pinned, hot] = await Promise.all([directory.pinnedServices(), directory.hotServices()]);
      this.setData({ quickServices: pinned, hotItems: hot });
    } catch (error) {
      // 目录加载失败不打断首页
    }
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

  refreshUnread() {
    api.unreadCount()
      .then((count) => {
        app.globalData.unreadCount = count;
      })
      .catch(() => {});
  },

  /** 快捷入口：直达申请表单，登录与参数守卫在目标页 */
  goQuickService(event) {
    if (!app.ensureLogin()) {
      return;
    }
    const service = this.data.quickServices[Number(event.currentTarget.dataset.index)];
    if (service) {
      directory.toItemApply(service.itemId);
    }
  },

  goMoreServices() {
    directory.toHall();
  },

  goSchedule() {
    if (!app.ensureLogin()) {
      return;
    }
    wx.navigateTo({ url: '/pages/schedule/index' });
  },

  goCommunity() {
    if (!app.ensureLogin()) return;
    wx.navigateTo({ url: '/pages/community/index' });
  },

  goServiceDetail(event) {
    const item = this.data.hotItems[Number(event.currentTarget.dataset.index)];
    if (item) {
      directory.toItemDetail(item.itemId);
    }
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
  }
});
