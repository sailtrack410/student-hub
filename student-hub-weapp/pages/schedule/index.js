const api = require('../../utils/api');
const app = getApp();

/** 周网格：7 天 × 11 节（1-11，上午1-4 下午5-8 晚上9-11） */
const DAYS = ['一', '二', '三', '四', '五', '六', '日'];
const MAX_SECTION = 11;

Page({
  data: {
    loading: false,
    importing: false,
    loaded: false,
    semesterName: '',
    className: '',
    courses: [],
    days: DAYS,
    cells: [],
    hasData: false
  },

  onShow() {
    if (!app.ensureLogin()) {
      return;
    }
    this.loadSchedule();
  },

  async loadSchedule() {
    this.setData({ loading: true });
    try {
      const data = await api.getScheduleView();
      this.render(data);
      this.setData({ loaded: true, hasData: !!data.courses && data.courses.length > 0 });
    } catch (error) {
      this.setData({ loaded: true, hasData: false });
    } finally {
      this.setData({ loading: false });
    }
  },

  /** 将课表条目铺进 11×7 网格，每格可能是多门课 */
  render(data) {
    const grid = [];
    for (let section = 1; section <= MAX_SECTION; section++) {
      const row = [];
      for (let day = 0; day < 7; day++) {
        row.push([]);
      }
      grid.push(row);
    }
    (data.courses || []).forEach((course) => {
      const day = Number(course.dayOfWeek);
      const start = Number(course.startSection);
      const end = Number(course.endSection) || start;
      if (day >= 1 && day <= 7) {
        for (let s = start; s <= end && s <= MAX_SECTION; s++) {
          const items = grid[s - 1][day - 1];
          if (!items.some((it) => it.courseName === course.courseName && it.teacher === course.teacher && it.weeks === course.weeks)) {
            items.push({
              courseName: course.courseName,
              teacher: course.teacher,
              location: course.location,
              weeks: course.weeks,
              weekParity: course.weekParity,
              parityText: course.weekParity === 1 ? '单周' : course.weekParity === 2 ? '双周' : ''
            });
          }
        }
      }
    });

    const cells = grid.map((row, sectionIndex) => {
      const section = sectionIndex + 1;
      const period = section <= 4 ? '上午' : section <= 8 ? '下午' : '晚上';
      return {
        section,
        period,
        periodStart: (section === 1 || section === 5 || section === 9),
        days: row.map((items, dayIndex) => ({
          day: dayIndex + 1,
          items
        }))
      };
    });

    this.setData({
      semesterName: data.semesterName || '',
      className: data.className || '',
      courses: data.courses || [],
      cells,
      hasData: !!data.courses && data.courses.length > 0
    });
  },

  async importSchedule() {
    if (!app.ensureLogin()) {
      return;
    }
    this.setData({ importing: true });
    try {
      const data = await api.importSchedule();
      wx.showToast({ title: `导入成功（${data.personalCount} 门）`, icon: 'success' });
      await this.loadSchedule();
    } catch (error) {
      wx.showToast({ title: (error && error.message) || '导入失败', icon: 'none' });
    } finally {
      this.setData({ importing: false });
    }
  },

  goJwAuth() {
    wx.navigateTo({ url: '/pages/jw/auth' });
  }
});