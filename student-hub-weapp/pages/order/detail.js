const api = require('../../utils/api');
const format = require('../../utils/format');
const { uploadImage } = require('../../utils/request');
const { resourceUrl } = require('../../utils/resource');
const app = getApp();

const FIELD_LABELS = {
  contactPhone: '联系方式', address: '报修地址', location: '报修地址', faultType: '故障类型',
  description: '问题描述', expectDate: '期望上门日期', photos: '故障照片', purpose: '用途',
  copies: '份数', remark: '备注'
};

Page({
  data: { orderId: '', detail: null, formRows: [], progress: [], loading: true, submitting: false },

  onLoad(options) { this.setData({ orderId: String((options && options.id) || '') }); },

  onShow() {
    if (app.ensureLogin() && this.data.orderId) this.loadDetail();
  },

  async loadDetail() {
    this.setData({ loading: true });
    try {
      const raw = await api.getWorkOrderDetail(this.data.orderId);
      const status = format.orderStatus(raw.status);
      const schemaLabels = {};
      (Array.isArray(raw.formSchema) ? raw.formSchema : []).forEach((field) => { schemaLabels[field.key] = field.label; });
      const formRows = Object.keys(raw.formData || {}).map((key) => {
        const value = raw.formData[key];
        const images = isImageList(value) ? value.map(resourceUrl) : [];
        return { key, label: schemaLabels[key] || FIELD_LABELS[key] || key, valueText: images.length ? '' : formatValue(value), images };
      });
      const detail = Object.assign({}, raw, {
        statusText: status.text, statusTheme: status.theme,
        arrivalPhotoUrl: raw.arrivalPhotoUrl ? resourceUrl(raw.arrivalPhotoUrl) : '',
        completionPhotoUrl: raw.completionPhotoUrl ? resourceUrl(raw.completionPhotoUrl) : '',
        completionPhotoUrls: (raw.completionPhotoUrls || []).map(resourceUrl),
        assignedAtText: format.formatTime(raw.assignedAt), startedAtText: format.formatTime(raw.startedAt),
        completionSubmittedAtText: format.formatTime(raw.completionSubmittedAt), confirmedAtText: format.formatTime(raw.confirmedAt)
      });
      this.setData({ detail, formRows, progress: buildProgress(detail.status, detail.photoWorkflowRequired), loading: false });
    } catch (error) { this.setData({ loading: false }); }
  },

  start() {
    if (this.data.submitting) return;
    this.setData({ submitting: true });
    api.startWorkOrder(this.data.orderId).then(() => { wx.showToast({ title: '已接单', icon: 'success' }); this.loadDetail(); })
      .finally(() => this.setData({ submitting: false }));
  },

  recordArrival() {
    if (this.data.submitting) return;
    this.choosePhoto(async (fileId) => {
      this.setData({ submitting: true });
      try { await api.recordWorkOrderArrivalPhoto(this.data.orderId, fileId); wx.showToast({ title: '现场照片已记录', icon: 'success' }); this.loadDetail(); }
      finally { this.setData({ submitting: false }); }
    });
  },

  submitCompletion() {
    if (this.data.submitting) return;
    wx.showModal({ title: '提交完工照片', editable: true, placeholderText: '请填写办理结果说明', success: (result) => {
      if (!result.confirm || !result.content || !result.content.trim()) {
        if (result.confirm) wx.showToast({ title: '请填写办理结果说明', icon: 'none' });
        return;
      }
      this.choosePhoto(async (fileId) => {
        this.setData({ submitting: true });
        try { await api.submitWorkOrderCompletion(this.data.orderId, fileId, result.content.trim()); wx.showToast({ title: '已提交报修人确认', icon: 'success' }); this.loadDetail(); }
        finally { this.setData({ submitting: false }); }
      });
    } });
  },

  choosePhoto(callback) {
    wx.chooseMedia({ count: 1, mediaType: ['image'], sourceType: ['camera'], sizeType: ['compressed'], success: async (result) => {
      const file = result.tempFiles && result.tempFiles[0];
      if (!file) return;
      try { const stored = await uploadImage(file.tempFilePath); callback(String(stored.fileId)); }
      catch (error) { /* 上传失败已由统一上传封装提示 */ }
    } });
  }
});

function formatValue(value) {
  if (value === null || value === undefined || value === '') return '-';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

function isImageList(value) {
  return Array.isArray(value) && value.length > 0 && value.every((item) => typeof item === 'string'
    && (item.indexOf('/api/common/files/view/') >= 0 || item.indexOf('/static/') >= 0 || item.indexOf('https://') === 0));
}

function buildProgress(status, photoRequired) {
  const steps = photoRequired
    ? [['ASSIGNED', '待接单', '确认接单'], ['PROCESSING', '已接单', '到现场后上传现场照片'], ['ONSITE_PHOTO_RECORDED', '现场已拍照', '处理完成后上传完工照片'], ['PENDING_CONFIRMATION', '已完成待确认', '等待报修人确认'], ['COMPLETED', '已办结', '流程完成']]
    : [['ASSIGNED', '待接单', '确认接单'], ['PROCESSING', '处理中', '完成处理后办结'], ['COMPLETED', '已办结', '流程完成']];
  const current = Math.max(0, steps.findIndex((step) => step[0] === status));
  return steps.map((step, index) => ({ key: step[0], title: step[1], action: step[2], done: index < current || status === 'COMPLETED', current: index === current }));
}
