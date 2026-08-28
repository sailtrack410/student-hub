const api = require('../../utils/api');
const format = require('../../utils/format');

const FILTERS = [
  { key: '', label: '全部' },
  { key: 'SUBMITTED', label: '待受理' },
  { key: 'PROCESSING', label: '处理中' },
  { key: 'COMPLETED', label: '已完成' },
  { key: 'REJECTED', label: '未受理' },
  { key: 'CANCELLED', label: '已撤销' }
];

Page({
  data: {
    filters: FILTERS,
    activeStatus: '',
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
      const page = await api.pageMyRequests({
        pageNum,
        pageSize: 10,
        status: this.data.activeStatus || undefined
      });
      const records = page.records.map((record) => {
        const status = format.requestStatus(record.status);
        return Object.assign({}, record, {
          statusText: status.text,
          statusTheme: status.theme,
          submittedAtText: format.formatTime(record.submittedAt)
        });
      });
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

  pickFilter(event) {
    this.setData({ activeStatus: event.currentTarget.dataset.key }, () => this.reload());
  },

  goDetail(event) {
    wx.navigateTo({ url: `/pages/request/detail?id=${event.currentTarget.dataset.id}` });
  }
});
