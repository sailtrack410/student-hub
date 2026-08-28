const api = require('../../utils/api');
const format = require('../../utils/format');
const app = getApp();

Page({
  data: {
    records: [],
    pageNum: 1,
    hasMore: true,
    loaded: false
  },

  onShow() {
    this.reload();
  },

  onPullDownRefresh() {
    this.reload().finally(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore) {
      this.loadPage(this.data.pageNum + 1);
    }
  },

  async reload() {
    this.setData({ pageNum: 1, records: [], hasMore: true });
    await this.loadPage(1);
  },

  async loadPage(pageNum) {
    try {
      const page = await api.pageNotifications({ pageNum, pageSize: 10 });
      const records = page.records.map((item) => Object.assign({}, item, {
        unread: !item.read,
        timeText: format.formatTime(item.createdAt)
      }));
      this.setData({
        records: pageNum === 1 ? records : this.data.records.concat(records),
        pageNum,
        hasMore: records.length >= 10,
        loaded: true
      });
    } catch (error) {
      this.setData({ loaded: true });
    }
  },

  async onItemTap(event) {
    const item = this.data.records[event.currentTarget.dataset.index];
    if (!item) {
      return;
    }
    if (item.unread) {
      try {
        await api.markNotificationRead(item.notificationId);
        app.globalData.unreadCount = Math.max(0, (app.globalData.unreadCount || 1) - 1);
      } catch (error) {
        // 静默
      }
    }
    if (item.relatedType === 'SERVICE_REQUEST' && item.relatedId) {
      wx.navigateTo({ url: `/pages/request/detail?id=${item.relatedId}` });
    }
  },

  async markAll() {
    try {
      await api.markAllNotificationsRead();
      wx.showToast({ title: '已全部标记', icon: 'success' });
      this.reload();
    } catch (error) {
      // 请求层已提示
    }
  }
});
