const api = require('../../utils/api');

const TYPE_TEXT = {
  input: '单行文本',
  textarea: '多行文本',
  select: '选择',
  date: '日期',
  number: '数字',
  image: '图片'
};

Page({
  data: {
    item: {},
    fields: []
  },

  onLoad(options) {
    this.itemId = options.id;
  },

  onShow() {
    this.loadItem();
  },

  async loadItem() {
    try {
      const item = await api.getServiceItem(this.itemId);
      const schema = item.formSchema || [];
      this.setData({
        item,
        fields: Array.isArray(schema)
          ? schema.map((field) => Object.assign({}, field, { typeText: TYPE_TEXT[field.type] || field.type }))
          : []
      });
    } catch (error) {
      // 请求层已提示
    }
  },

  goApply() {
    wx.navigateTo({ url: `/pages/service/apply?itemId=${this.itemId}` });
  }
});
