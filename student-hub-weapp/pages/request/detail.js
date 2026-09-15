const api = require('../../utils/api');
const { resourceUrl } = require('../../utils/resource');
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
  questionType: '咨询类型',
  contactPhone: '联系方式',
  address: '报修地址'
};

Page({
  data: {
    detail: null,
    formPairs: [],
    confirming: false
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
              && (value[0].indexOf('/api/') === 0 || value[0].indexOf('https://') === 0)) {
            formPairs.push({
              key,
              label: FIELD_LABELS[key] || key,
              images: value.map(resourceUrl),
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
        timeline: Array.isArray(raw.timeline) ? raw.timeline : [],
        statusText: status.text,
        statusTheme: status.theme,
        workOrder: raw.workOrder ? Object.assign({}, raw.workOrder, {
          status: String(raw.workOrder.status || '').trim().toUpperCase(),
          statusText: format.orderStatus(raw.workOrder.status).text,
          statusTheme: format.orderStatus(raw.workOrder.status).theme,
          arrivalPhotoUrl: raw.workOrder.arrivalPhotoUrl ? resourceUrl(raw.workOrder.arrivalPhotoUrl) : '',
          completionPhotoUrl: raw.workOrder.completionPhotoUrl ? resourceUrl(raw.workOrder.completionPhotoUrl) : '',
          completionPhotoUrls: (raw.workOrder.completionPhotoUrls || []).map(resourceUrl)
        }) : null,
        processNodes: buildProcessNodes(raw),
        canConfirmWorkOrder: Boolean(raw.workOrder
          && String(raw.workOrder.status || '').trim().toUpperCase() === 'PENDING_CONFIRMATION')
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
  },

  confirmWorkOrder() {
    if (this.data.confirming) return;
    const order = this.data.detail && this.data.detail.workOrder;
    if (!order || String(order.status || '').trim().toUpperCase() !== 'PENDING_CONFIRMATION') {
      wx.showToast({ title: '当前工单还未到确认节点', icon: 'none' });
      return;
    }
    wx.showModal({ title: '确认完成', content: '确认维修已完成吗？确认后工单将正式办结。', confirmText: '确认', success: async (res) => {
      if (!res.confirm) return;
      this.setData({ confirming: true });
      try {
        await api.confirmWorkOrder(String(order.orderId));
        wx.showToast({ title: '已确认办结', icon: 'success' });
        await this.loadDetail();
      } catch (error) { /* 请求层已提示 */ }
      finally { this.setData({ confirming: false }); }
    } });
  },

  rejectWorkOrder() {
    if (this.data.confirming) return;
    const order = this.data.detail && this.data.detail.workOrder;
    if (!order || String(order.status || '').trim().toUpperCase() !== 'PENDING_CONFIRMATION') {
      wx.showToast({ title: '当前工单还未到确认节点', icon: 'none' });
      return;
    }
    wx.showModal({ title: '退回处理', editable: true, placeholderText: '请填写需要重新处理的原因', success: async (res) => {
      if (!res.confirm || !res.content || !res.content.trim()) {
        if (res.confirm) wx.showToast({ title: '请填写退回原因', icon: 'none' });
        return;
      }
      this.setData({ confirming: true });
      try {
        await api.rejectWorkOrderConfirmation(String(order.orderId), res.content.trim());
        wx.showToast({ title: '已退回处理', icon: 'success' });
        await this.loadDetail();
      } catch (error) { /* 请求层已提示 */ }
      finally { this.setData({ confirming: false }); }
    } });
  }
});

function buildProcessNodes(raw) {
  const logs = Array.isArray(raw.timeline) ? raw.timeline : [];
  const order = raw.workOrder;
  const photoRequired = Boolean(order && order.photoWorkflowRequired);
  const definitions = order
    ? (photoRequired
      ? [
        ['SUBMIT', '提交申请', ['SUBMIT']],
        ['DISPATCH', '已派单', ['DISPATCH']],
        ['START', '已接单', ['START']],
        ['ARRIVAL_PHOTO', '现场拍照', ['ARRIVAL_PHOTO']],
        ['COMPLETION_PHOTO', '完工拍照', ['COMPLETION_PHOTO']],
        ['CONFIRM', '报修人确认', ['CONFIRM', 'COMPLETE']]
      ]
      : [
        ['SUBMIT', '提交申请', ['SUBMIT']],
        ['DISPATCH', '已派单', ['DISPATCH']],
        ['START', '开始处理', ['START']],
        ['COMPLETE', '已办结', ['COMPLETE']]
      ])
    : [
      ['SUBMIT', '提交申请', ['SUBMIT']],
      ['PROCESSING', '处理中', ['DISPATCH', 'START']],
      ['COMPLETE', '已完成', ['COMPLETE']]
    ];

  const latestLogs = {};
  logs.forEach((log) => {
    const action = String(log.action || '').toUpperCase();
    latestLogs[action] = log;
  });

  const status = order ? String(order.status || '').toUpperCase() : String(raw.status || '').toUpperCase();
  const currentIndex = order
    ? ({ PENDING_DISPATCH: 0, ASSIGNED: 2, PROCESSING: 2, ONSITE_PHOTO_RECORDED: 4,
      PENDING_CONFIRMATION: 5, COMPLETED: definitions.length - 1, REJECTED: 0, CANCELLED: 0 }[status] ?? 0)
    : ({ SUBMITTED: 0, PROCESSING: 1, COMPLETED: definitions.length - 1, REJECTED: 1, CANCELLED: 1 }[status] ?? 0);
  const completed = status === 'COMPLETED';

  return definitions.map((definition, index) => {
    const log = definition[2].map((action) => latestLogs[action]).find(Boolean);
    return {
      key: definition[0],
      title: definition[1],
      done: completed || index < currentIndex,
      current: !completed && index === currentIndex,
      timeText: log ? format.formatTime(log.createdAt) : '',
      operatorName: log ? (log.operatorName || '') : '',
      comment: log ? (log.comment || '') : ''
    };
  });
}
