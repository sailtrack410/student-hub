const api = require('../../utils/api');

Page({
  data: {
    item: {},
    schema: [],
    formData: {},
    loading: false,
    submitted: null
  },

  onLoad(options) {
    this.itemId = options.id;
  },

  onShow() {
    if (this.data.submitted) {
      return;
    }
    this.loadItem();
  },

  async loadItem() {
    try {
      const item = await api.getServiceItem(this.itemId);
      this.setData({ item, schema: item.formSchema || [], formData: {} });
    } catch (error) {
      // 请求层已提示
    }
  },

  onFormChange(event) {
    this.setData({ formData: event.detail.value });
  },

  async handleSubmit() {
    if (this.data.loading) {
      return;
    }
    // 必填项前置校验，给出更友好的提示
    for (const field of this.data.schema) {
      const fieldValue = this.data.formData[field.key];
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
      const detail = await api.submitRequest({ itemId: Number(this.itemId), formData: this.data.formData }, idempotencyKey);
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
    this.setData({ submitted: null, formData: {} });
    this.loadItem();
  }
});
