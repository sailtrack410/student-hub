const api = require('../../utils/api');
const format = require('../../utils/format');

const FILTERS = [
  { key: '', label: '全部' },
  { key: 'ASSIGNED', label: '待处理' },
  { key: 'PROCESSING', label: '进行中' },
  { key: 'COMPLETED', label: '已办结' }
];

Page({
  data: {
    filters: FILTERS,
    activeStatus: '',
    records: [],
    loaded: false
  },

  onShow() {
    this.reload();
  },

  onPullDownRefresh() {
    this.reload().finally(() => wx.stopPullDownRefresh());
  },

  async reload() {
    try {
      const page = await api.pageAssignedOrders({
        pageNum: 1,
        pageSize: 50,
        status: this.data.activeStatus || undefined
      });
      const records = page.records.map((record) => {
        const status = format.orderStatus(record.status);
        return Object.assign({}, record, { statusText: status.text, statusTheme: status.theme });
      });
      this.setData({ records, loaded: true });
    } catch (error) {
      this.setData({ loaded: true });
    }
  },

  pickFilter(event) {
    this.setData({ activeStatus: event.currentTarget.dataset.key }, () => this.reload());
  },

  onStart(event) {
    const orderId = event.currentTarget.dataset.id;
    wx.showModal({
      title: '开始处理',
      content: '确认开始处理该工单？',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await api.startWorkOrder(orderId);
          wx.showToast({ title: '已开始', icon: 'success' });
          this.reload();
        } catch (error) {
          // 请求层已提示
        }
      }
    });
  },

  onComplete(event) {
    const orderId = event.currentTarget.dataset.id;
    wx.showModal({
      title: '办结工单',
      editable: true,
      placeholderText: '请填写办理结果说明',
      success: async (res) => {
        if (!res.confirm) return;
        const remark = res.content || '';
        if (!remark.trim()) {
          wx.showToast({ title: '请填写办理结果说明', icon: 'none' });
          return;
        }
        try {
          await api.completeWorkOrder(orderId, remark.trim());
          wx.showToast({ title: '已办结', icon: 'success' });
          this.reload();
        } catch (error) {
          // 请求层已提示
        }
      }
    });
  }
});
