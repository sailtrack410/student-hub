const api = require('../../utils/api');
const app = getApp();

Page({
  data: {
    item: {},
    schema: [],
    formData: {},
    loading: false,
    submitted: null
  },

  onLoad(options) {
    const itemId = Number(options.itemId);
    if (!options.itemId || Number.isNaN(itemId) || itemId <= 0) {
      wx.showToast({ title: '服务参数缺失，请从服务列表重新进入', icon: 'none' });
      setTimeout(() => {
        wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/service/index' }) });
      }, 900);
      this.itemId = null;
      return;
    }
    this.itemId = itemId;
    this.itemLoaded = false;
  },

  onShow() {
    if (!app.ensureLogin()) {
      return;
    }
    if (!this.itemId || this.data.submitted || this.itemLoaded) {
      return;
    }
    this.loadItem();
  },

  async loadItem() {
    try {
      const item = await api.getServiceItem(this.itemId);
      this.setData({ item, schema: item.formSchema || [] });
      this.itemLoaded = true;
    } catch (error) {
      // 请求层已提示
    }
  },

  /** 查看该服务的办事指南 */
  goGuide() {
    if (this.itemId) {
      wx.navigateTo({ url: `/pages/service/detail?id=${this.itemId}` });
    }
  },

  onFormChange(event) {
    this.formData = event.detail.value;
    this.setData({ formData: this.formData });
  },

  async handleSubmit() {
    if (!this.itemId || this.data.loading) {
      return;
    }
    // 必填项前置校验，给出更友好的提示
    const formData = this.formData || {};
    for (const field of this.data.schema) {
      const fieldValue = formData[field.key];
      const empty = fieldValue === undefined || fieldValue === null || fieldValue === '';
      if (field.required && empty) {
        wx.showToast({ title: `请填写「${field.label}」`, icon: 'none' });
        return;
      }
    }
    this.setData({ loading: true });
    try {
      // 幂等键防止双击/弱网重试造成重复申请
      const idempotencyKey = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
      const detail = await api.submitRequest({ itemId: Number(this.itemId), formData }, idempotencyKey);
      this.setData({ submitted: detail });
      wx.showToast({ title: '提交成功', icon: 'success' });
    } catch (error) {
      // 请求层已提示
    } finally {
      this.setData({ loading: false });
    }
  },

  goDetail() {
    wx.redirectTo({ url: `/pages/request/detail?id=${this.data.submitted.requestId}` });
  },

  again() {
    this.formData = {};
    this.itemLoaded = false;
    this.setData({ submitted: null, formData: {}, schema: [] });
    this.loadItem();
  }
});
