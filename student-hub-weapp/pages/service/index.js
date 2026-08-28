const api = require('../../utils/api');
const app = getApp();

const SORT_OPTIONS = ['默认排序', '办结最快', '名称排序'];

Page({
  data: {
    categories: [],
    allItems: [],
    groups: [],
    keyword: '',
    sortOptions: SORT_OPTIONS,
    sortIndex: 0,
    totalCount: 0,
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
      this.rebuild();
    } catch (error) {
      this.setData({ loaded: true });
    }
  },

  /** 按关键词过滤后按分类分组，组内按所选方式排序 */
  rebuild() {
    const { allItems, categories, keyword, sortIndex } = this.data;
    const matched = allItems.filter((item) => {
      const text = `${item.itemName}${item.summary || ''}`;
      return !keyword || text.indexOf(keyword) >= 0;
    });
    const sortItem = (items) => {
      if (sortIndex === 1) {
        return items.slice().sort((a, b) => (a.slaHours || 9999) - (b.slaHours || 9999));
      }
      if (sortIndex === 2) {
        return items.slice().sort((a, b) => a.itemName.localeCompare(b.itemName, 'zh'));
      }
      return items;
    };
    const groups = categories
      .map((category) => ({
        categoryId: category.categoryId,
        categoryName: category.categoryName,
        icon: category.icon,
        description: category.description,
        items: sortItem(matched.filter((item) => Number(item.categoryId) === Number(category.categoryId)))
      }))
      .filter((group) => group.items.length > 0);
    this.setData({ groups, totalCount: matched.length });
  },

  onSearchInput(event) {
    this.setData({ keyword: event.detail.value.trim() });
    this.rebuild();
  },

  onSortChange(event) {
    this.setData({ sortIndex: Number(event.detail.value) });
    this.rebuild();
  },

  goDetail(event) {
    const group = this.data.groups[Number(event.currentTarget.dataset.group)];
    const item = group && group.items[Number(event.currentTarget.dataset.index)];
    if (item && item.itemId) {
      wx.navigateTo({ url: `/pages/service/detail?id=${item.itemId}` });
    }
  }
});
