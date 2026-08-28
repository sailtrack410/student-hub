const api = require('../../utils/api');
const app = getApp();

const SORT_OPTIONS = ['默认排序', '办结最快', '名称排序'];

Page({
  data: {
    categories: [],
    allItems: [],
    visibleItems: [],
    activeCategoryId: 0,
    keyword: '',
    sortOptions: SORT_OPTIONS,
    sortIndex: 0,
    loaded: false
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
      if (!catalog || !catalog.length) {
        catalog = await api.getCatalog();
        app.globalData.catalog = catalog;
      }
      const allItems = [];
      catalog.forEach((category) => {
        (category.items || []).forEach((item) => allItems.push(item));
      });
      this.setData({ categories: catalog, allItems, loaded: true });
      this.applyFilter();
    } catch (error) {
      this.setData({ loaded: true });
    }
  },

  applyFilter() {
    const { allItems, activeCategoryId, keyword, sortIndex } = this.data;
    let items = allItems.filter((item) => {
      const matchCategory = !activeCategoryId || Number(item.categoryId) === Number(activeCategoryId);
      const text = `${item.itemName}${item.summary || ''}`;
      const matchKeyword = !keyword || text.indexOf(keyword) >= 0;
      return matchCategory && matchKeyword;
    });
    if (sortIndex === 1) {
      items = items.slice().sort((a, b) => (a.slaHours || 9999) - (b.slaHours || 9999));
    } else if (sortIndex === 2) {
      items = items.slice().sort((a, b) => a.itemName.localeCompare(b.itemName, 'zh'));
    }
    this.setData({ visibleItems: items });
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

  onSortChange(event) {
    this.setData({ sortIndex: Number(event.detail.value) });
    this.applyFilter();
  },

  goDetail(event) {
    wx.navigateTo({ url: `/pages/service/detail?id=${event.currentTarget.dataset.itemId}` });
  }
});
