const api = require('../../utils/api');
const app = getApp();

Page({
  data: {
    categories: [],
    allItems: [],
    visibleItems: [],
    activeCategoryId: 0,
    keyword: ''
  },

  onShow() {
    if (!app.ensureLogin()) {
      return;
    }
    this.loadCatalog();
  },

  async loadCatalog() {
    try {
      let catalog = app.globalData.catalog;
      if (!catalog) {
        catalog = await api.getCatalog();
        app.globalData.catalog = catalog;
      }
      const allItems = [];
      catalog.forEach((category) => {
        (category.items || []).forEach((item) => allItems.push(item));
      });
      this.setData({
        categories: catalog,
        allItems,
        visibleItems: this.filterItems(allItems, this.data.activeCategoryId, this.data.keyword)
      });
    } catch (error) {
      // 请求层已提示
    }
  },

  filterItems(items, categoryId, keyword) {
    return items.filter((item) => {
      const matchCategory = !categoryId || item.categoryId === categoryId;
      const text = `${item.itemName}${item.summary || ''}`;
      const matchKeyword = !keyword || text.indexOf(keyword) >= 0;
      return matchCategory && matchKeyword;
    });
  },

  applyFilter() {
    this.setData({
      visibleItems: this.filterItems(this.data.allItems, this.data.activeCategoryId, this.data.keyword)
    });
  },

  pickCategory(event) {
    const id = Number(event.currentTarget.dataset.id) || 0;
    this.setData({ activeCategoryId: id });
    this.applyFilter();
  },

  onSearchInput(event) {
    this.setData({ keyword: event.detail.value.trim() });
    this.applyFilter();
  },

  goDetail(event) {
    wx.navigateTo({ url: `/pages/service/detail?id=${event.currentTarget.dataset.itemId}` });
  }
});
