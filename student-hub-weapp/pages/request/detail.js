const api = require('../../utils/api');
const config = require('../../utils/config');
const format = require('../../utils/format');

// 表单字段 key → 展示名
const FIELD_LABELS = {
  faultType: '故障类型',
  description: '问题描述',
  expectDate: '期望上门日期',
  photos: '现场照片',
  location: '具体位置',
  leaveType: '请假类型',
  startDate: '开始日期',
  endDate: '结束日期',
  reason: '请假事由',
  materials: '请假材料',
  activityName: '活动名称',
  venue: '申请场地',
  useDate: '使用日期',
  purpose: '用途',
  copies: '份数',
  remark: '备注',
  questionType: '咨询类型'
};

Page({
  data: {
    detail: null,
    formPairs: []
  },

  onLoad(options) {
    this.requestId = options.id;
  },

  onShow() {
    this.loadDetail();
  },

  async loadDetail() {
    try {
      const raw = await api.getRequestDetail(this.requestId);
      const status = format.requestStatus(raw.status);
      const formPairs = [];
      if (raw.formData && typeof raw.formData === 'object') {
        Object.keys(raw.formData).forEach((key) => {
          const value = raw.formData[key];
          // 图片字段：后端已替换为带签名的访问路径
          if (Array.isArray(value) && value.length && typeof value[0] === 'string'
              && value[0].indexOf('/api/') === 0) {
            formPairs.push({
              key,
              label: FIELD_LABELS[key] || key,
              images: value.map((path) => config.baseUrl + path),
              valueText: ''
            });
            return;
          }
          formPairs.push({
            key,
            label: FIELD_LABELS[key] || key,
            images: [],
            valueText: Array.isArray(value) ? value.join('、') : String(value)
          });
        });
      }
      const detail = Object.assign({}, raw, {
        statusText: status.text,
        statusTheme: status.theme
      });
      this.setData({ detail, formPairs });
    } catch (error) {
      // 请求层已提示
    }
  },

  previewFormImage(event) {
    const key = event.currentTarget.dataset.key;
    const pair = this.data.formPairs.find((item) => item.key === key);
    if (pair && pair.images.length) {
      wx.previewImage({ urls: pair.images });
    }
  },

  onCancel() {
    wx.showModal({
      title: '撤销申请',
      content: '撤销后本次办理将终止，确定继续吗？',
      confirmText: '撤销',
      confirmColor: '#DC2626',
      success: (res) => {
        if (res.confirm) {
          this.doCancel();
        }
      }
    });
  },

  async doCancel() {
    try {
      await api.cancelRequest(this.requestId, '用户主动撤销');
      wx.showToast({ title: '已撤销', icon: 'success' });
      this.loadDetail();
    } catch (error) {
      // 请求层已提示
    }
  },

  goEvaluate() {
    wx.navigateTo({ url: `/pages/evaluation/index?requestId=${this.requestId}` });
  }
});
