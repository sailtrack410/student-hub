const directory = require('../../utils/directory');
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
    scheduleCount: 0,
    scheduleSemesterName: '',
    loaded: false
  },

  onShow() {
    if (!app.ensureLogin()) {
      return;
    }
    const isLogistics = directory.isLogisticsUser();
    this.setData({ isLogistics });
    this.loadCatalog();
    if (!isLogistics) {
      this.loadScheduleSummary();
    } else {
      this.setData({ scheduleCount: 0, scheduleSemesterName: '' });
    }
  },

  /** 课表摘要：供顶部"我的课表"分组展示 */
  loadScheduleSummary() {
    api.getScheduleView()
      .then((schedule) => {
        if (schedule && schedule.courses && schedule.courses.length) {
          this.setData({
            scheduleCount: schedule.courses.length,
            scheduleSemesterName: schedule.semesterName || '我的课表'
          });
        } else {
          this.setData({ scheduleCount: 0, scheduleSemesterName: '' });
        }
      })
      .catch(() => {});
  },

  async loadCatalog() {
    try {
      const catalog = await directory.ensureCatalog();
      const visibleCatalog = directory.filterCatalogForUser(catalog);
      const allItems = directory.flatten(visibleCatalog);
      this.setData({ categories: visibleCatalog, allItems, loaded: true });
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

  goSchedule() {
    if (!app.ensureLogin()) {
      return;
    }
    wx.navigateTo({ url: '/pages/schedule/index' });
  },

  /** 功能项点击分发：课表等特殊功能跳专页，其余跳办事指南 */
  onItemTap(event) {
    const itemCode = event.currentTarget.dataset.code;
    const itemId = event.currentTarget.dataset.id;
    if (itemCode === 'SCHEDULE-VIEW') {
      if (directory.isLogisticsUser()) {
        return;
      }
      if (!app.ensureLogin()) {
        return;
      }
      wx.navigateTo({ url: '/pages/schedule/index' });
      return;
    }
    if (!itemId || itemId === 'undefined') {
      wx.showToast({ title: '服务信息加载异常，请重新进入', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: `/pages/service/detail?id=${itemId}` });
  }
});
