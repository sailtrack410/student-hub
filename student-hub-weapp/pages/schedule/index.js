const api = require('../../utils/api');
const app = getApp();

const DAYS = [
  { label: '周一', short: '一' },
  { label: '周二', short: '二' },
  { label: '周三', short: '三' },
  { label: '周四', short: '四' },
  { label: '周五', short: '五' },
  { label: '周六', short: '六' },
  { label: '周日', short: '日' }
];
const MAX_SECTION = 11;

/** 每节次的上课时间（青果作息） */
const SECTION_TIMES = {
  1: '08:30', 2: '09:20', 3: '10:25', 4: '11:15',
  5: '14:10', 6: '15:00', 7: '16:05', 8: '16:55',
  9: '18:50', 10: '19:40', 11: '20:30'
};

/** 课程配色池：按课程名哈希取色，同一门课恒定同色 */
const COURSE_COLORS = [
  { bg: '#DBEAFE', text: '#1D4ED8', room: '#64748B' },
  { bg: '#D1FAE5', text: '#059669', room: '#6B7280' },
  { bg: '#EDE9FE', text: '#7C3AED', room: '#6B7280' },
  { bg: '#FFEDD5', text: '#C2410C', room: '#78716C' },
  { bg: '#FCE7F3', text: '#BE185D', room: '#6B7280' },
  { bg: '#CFFAFE', text: '#0E7490', room: '#64748B' },
  { bg: '#FEF3C7', text: '#B45309', room: '#78716C' },
  { bg: '#DCFCE7', text: '#15803D', room: '#6B7280' }
];

function colorIndexFor(name) {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 31 + name.charCodeAt(i)) & 0x7fffffff;
  }
  return hash % COURSE_COLORS.length;
}

Page({
  data: {
    loading: false,
    importing: false,
    loaded: false,
    semesterName: '',
    className: '',
    monthLabel: '',
    days: DAYS.map((d) => d.label),
    rows: [],
    courses: [],
    hasData: false
  },

  onShow() {
    if (!app.ensureLogin()) {
      return;
    }
    const now = new Date();
    this.setData({ monthLabel: `${now.getMonth() + 1}月` });
    this.loadSchedule();
  },

  async loadSchedule() {
    this.setData({ loading: true });
    try {
      const data = await api.getScheduleView();
      this.applyData(data);
    } catch (error) {
      this.setData({ loaded: true, hasData: false });
    } finally {
      this.setData({ loading: false });
    }
  },

  applyData(data) {
    const courses = data.courses || [];
    // 给每门课分配颜色与周次文本
    const decorated = courses.map((course) => {
      const colorIndex = colorIndexFor(course.courseName || '');
      const color = COURSE_COLORS[colorIndex];
      const parityText = course.weekParity === 1 ? '(单)' : course.weekParity === 2 ? '(双)' : '';
      return Object.assign({}, course, {
        colorIndex,
        bg: color.bg,
        nameColor: color.text,
        roomColor: color.room,
        weeksText: `#${course.weeks}周${parityText}`
      });
    });

    // 铺进 11×7 网格
    const grid = [];
    for (let s = 1; s <= MAX_SECTION; s++) {
      const row = [];
      for (let d = 0; d < 7; d++) row.push([]);
      grid.push(row);
    }
    decorated.forEach((course) => {
      const day = Number(course.dayOfWeek);
      const start = Number(course.startSection);
      const end = Math.min(Number(course.endSection) || start, MAX_SECTION);
      if (day >= 1 && day <= 7) {
        for (let s = start; s <= end; s++) {
          const items = grid[s - 1][day - 1];
          if (!items.some((it) => it.courseName === course.courseName
              && it.teacher === course.teacher && it.weeks === course.weeks
              && it.location === course.location)) {
            items.push(course);
          }
        }
      }
    });

    // 时段分组：1-4 上午，5-8 下午，9-11 晚上
    const rows = grid.map((row, index) => {
      const section = index + 1;
      const periodStart = section === 1 || section === 5 || section === 9;
      const period = section <= 4 ? '上午' : section <= 8 ? '下午' : '晚上';
      return {
        section,
        period,
        periodStart,
        timeRange: SECTION_TIMES[section] || '',
        days: row.map((items, dayIndex) => ({ day: dayIndex + 1, items }))
      };
    });

    const month = new Date();
    this.setData({
      semesterName: data.semesterName || '',
      className: data.className || '',
      monthLabel: `${month.getMonth() + 1}月`,
      courses,
      rows,
      hasData: courses.length > 0
    });
  },

  async importSchedule() {
    if (!app.ensureLogin()) {
      return;
    }
    this.setData({ importing: true });
    try {
      const data = await api.importSchedule();
      wx.showToast({ title: `已同步 ${data.personalCount} 门课`, icon: 'success' });
      await this.loadSchedule();
    } catch (error) {
      wx.showToast({ title: (error && error.message) || '同步失败', icon: 'none' });
    } finally {
      this.setData({ importing: false });
    }
  },

  goJwAuth() {
    wx.navigateTo({ url: '/pages/jw/auth' });
  }
});