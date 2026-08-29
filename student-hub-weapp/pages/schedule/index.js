const api = require('../../utils/api');
const app = getApp();

const DAY_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
const MAX_SECTION = 11;
/** 单节高度（rpx），列高 = MAX_SECTION * ROW_H */
const ROW_H = 120;

/** 每节次的上课时间（青果作息） */
const SECTION_TIMES = {
  1: '08:30', 2: '09:20', 3: '10:25', 4: '11:15',
  5: '14:10', 6: '15:00', 7: '16:05', 8: '16:55',
  9: '18:50', 10: '19:40', 11: '20:30'
};

/** 课程配色池：按课程名哈希取色，同一门课恒定同色（白字） */
const COURSE_COLORS = [
  '#5B8DEF',
  '#FF9F5A',
  '#F2699C',
  '#46C4B7',
  '#9B8AFB',
  '#F2B33D',
  '#67C87A',
  '#FF8F8F'
];

function colorFor(name) {
  let hash = 0;
  const text = name || '';
  for (let i = 0; i < text.length; i++) {
    hash = (hash * 31 + text.charCodeAt(i)) & 0x7fffffff;
  }
  return COURSE_COLORS[hash % COURSE_COLORS.length];
}

/** 解析教务周次串（如 "1-16周" / "1-8,10-16"）为周次集合，叠加单双周过滤 */
function weekSetOf(course) {
  const set = new Set();
  const raw = String(course.weeks || '').replace(/周/g, '').replace(/\s/g, '');
  raw.split(/[,，;；]+/).forEach((part) => {
    const m = /^(\d+)(?:[-–~](\d+))?$/.exec(part);
    if (!m) return;
    let start = Number(m[1]);
    let end = m[2] ? Number(m[2]) : start;
    if (end < start) {
      const tmp = start;
      start = end;
      end = tmp;
    }
    for (let w = start; w <= end; w++) {
      if (course.weekParity === 1 && w % 2 === 0) continue;
      if (course.weekParity === 2 && w % 2 === 1) continue;
      set.add(w);
    }
  });
  return set;
}

/** 短教室名：取 [] 内部，截掉括号补充，保留 "信-302"/"博雅-204" 形式的房间号 */
function shortRoom(location) {
  if (!location) return '';
  let text = String(location);
  const bracket = text.match(/[[【]([^\]】]+)[]】]/);
  if (bracket) text = bracket[1];
  text = text.split(/[（(]/)[0].trim();
  const code = text.match(/[\u4e00-\u9fa5A-Za-z]{1,4}-?\d{2,4}/);
  if (code) return code[0];
  return text.slice(0, 6);
}

function pad2(n) {
  return n < 10 ? `0${n}` : `${n}`;
}

/** 取 date 所在周的周一（无时间部分） */
function mondayOf(date) {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const day = d.getDay() === 0 ? 7 : d.getDay();
  d.setDate(d.getDate() - (day - 1));
  return d;
}

/** 按学期编码/名称估算第一周周一：第一学期约 9 月 1 日，第二学期约次年 3 月 1 日 */
function estimateSemesterStart(semesterCode, semesterName) {
  let year = null;
  let term = null;
  const codeMatch = semesterCode ? /^(\d{4})\s*[,，-]\s*(\d)$/.exec(String(semesterCode).trim()) : null;
  if (codeMatch) {
    year = Number(codeMatch[1]);
    term = Number(codeMatch[2]);
  }
  if (term == null && semesterName) {
    const nameMatch = /(\d{4})[-–—]\d{4}学年第([一二三四五六]+|\d)学期/.exec(String(semesterName));
    if (nameMatch) {
      year = Number(nameMatch[1]);
      const termMap = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6 };
      term = termMap[nameMatch[2]] || Number(nameMatch[2]);
    }
  }
  if (!year || !term) return null;
  const month = term === 1 ? 8 : 2;
  const anchorYear = term === 1 ? year : year + 1;
  // 开学日落在周六/周日时，第一周从下一个周一开始
  const anchorDay = new Date(anchorYear, month, 1);
  const weekday = anchorDay.getDay();
  if (weekday === 0 || weekday === 6) {
    anchorDay.setDate(anchorDay.getDate() + (weekday === 6 ? 2 : 1));
  }
  return mondayOf(anchorDay);
}

/** 节次区间的时间文本，如 "08:30-10:05" */
function sectionTimeRange(start, end) {
  const beginText = SECTION_TIMES[start] || '';
  if (!beginText) return '';
  const parts = beginText.split(':');
  const total = Number(parts[0]) * 60 + Number(parts[1]) + (end - start + 1) * 45;
  return `${beginText}-${pad2(Math.floor(total / 60) % 24)}:${pad2(total % 60)}`;
}

Page({
  data: {
    loading: false,
    importing: false,
    loaded: false,
    hasData: false,
    semesterName: '',
    className: '',
    weekLabel: '第1周',
    monthLabel: '',
    headerDays: [],
    columns: [],
    sectionRows: [],
    showWeekPicker: false,
    weekOptions: [],
    selectedWeek: 1,
    todayWeek: 0,
    detail: null
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
      this.rawCourses = data.courses || [];
      this.semesterCode = data.semesterCode || '';
      this.applySchedule();
    } catch (error) {
      this.rawCourses = [];
      this.setData({ loaded: true, hasData: false });
    } finally {
      this.setData({ loading: false });
    }
  },

  applySchedule() {
    const courses = this.rawCourses || [];
    let maxWeek = 1;
    this.weekSets = courses.map((course) => {
      const set = weekSetOf(course);
      set.forEach((w) => {
        if (w > maxWeek) maxWeek = w;
      });
      return set;
    });
    maxWeek = Math.min(maxWeek, 30);

    const anchor = estimateSemesterStart(this.semesterCode, this.semesterNameOf(courses));
    let todayWeek = 0;
    if (anchor) {
      // 仅当今天落在学期周次范围内才标记“今天”，假期不冒充学期周
      const diff = Math.floor((mondayOf(new Date()) - anchor) / 604800000) + 1;
      if (diff >= 1 && diff <= maxWeek) {
        todayWeek = diff;
      }
    }
    const selectedWeek = todayWeek || 1;

    const weekOptions = [];
    for (let w = 1; w <= maxWeek; w++) weekOptions.push(w);

    this.anchor = anchor;
    this.setData({
      loaded: true,
      hasData: courses.length > 0,
      semesterName: this.semesterNameOf(courses),
      todayWeek,
      selectedWeek,
      weekOptions
    });
    this.applyWeek(selectedWeek);
  },

  semesterNameOf(courses) {
    if (this.semesterNameText) return this.semesterNameText;
    this.semesterNameText = courses.length ? courses[0].semesterName || '' : '';
    return this.semesterNameText;
  },

  /** 按选中周次重建表头与课程块 */
  applyWeek(week) {
    const courses = this.rawCourses || [];
    const weekSets = this.weekSets || [];
    const anchor = this.anchor;
    const monday = anchor ? new Date(anchor.getTime() + (week - 1) * 7 * 86400000) : null;
    const todayKey = this.todayKey();
    const todayWeek = this.data.todayWeek;

    const headerDays = DAY_LABELS.map((label, index) => {
      const dateText = monday
        ? `${pad2(new Date(monday.getTime() + index * 86400000).getDate())}日`
        : '';
      const dateKey = monday ? this.keyOf(new Date(monday.getTime() + index * 86400000)) : '';
      return {
        day: index + 1,
        label,
        dateText,
        isToday: week === todayWeek && dateKey === todayKey
      };
    });

    // 过滤出本周有效的课程，按身份分组后合并连堂节次
    const groups = new Map();
    courses.forEach((course, index) => {
      if (!weekSets[index] || !weekSets[index].has(week)) return;
      const key = `${course.courseName}|${course.teacher}|${course.weeks}|${course.weekParity}|${course.location}`;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(course);
    });

    const blocks = [];
    const decorated = [];
    let blockId = 0;
    const columns = [1, 2, 3, 4, 5, 6, 7].map((day) => ({ day, blocks: [] }));
    groups.forEach((list) => {
      list.sort((a, b) => Number(a.startSection) - Number(b.startSection));
      const day = Number(list[0].dayOfWeek);
      if (!(day >= 1 && day <= 7)) return;
      let run = null;
      list.forEach((course) => {
        const start = Math.max(Number(course.startSection) || 1, 1);
        const end = Math.min(Number(course.endSection) || start, MAX_SECTION);
        if (run && start <= run.endSection + 1) {
          run.endSection = Math.max(run.endSection, end);
          return;
        }
        run = {
          course,
          startSection: start,
          endSection: end
        };
        blocks.push(run);
      });
    });

    blocks.forEach((run) => {
      const course = run.course;
      const day = Number(course.dayOfWeek);
      blockId += 1;
      const block = {
        id: blockId,
        top: (run.startSection - 1) * ROW_H + 4,
        height: (run.endSection - run.startSection + 1) * ROW_H - 8,
        bg: colorFor(course.courseName),
        name: course.courseName || '',
        room: shortRoom(course.location),
        dayLabel: DAY_LABELS[day - 1],
        startSection: run.startSection,
        endSection: run.endSection,
        teacher: course.teacher || '',
        location: course.location || '',
        weeksText: this.weeksTextOf(course),
        timeText: sectionTimeRange(run.startSection, run.endSection)
      };
      columns[day - 1].blocks.push(block);
      decorated.push(block);
    });

    const sectionRows = [];
    for (let s = 1; s <= MAX_SECTION; s++) {
      sectionRows.push({ section: s, time: SECTION_TIMES[s] || '' });
    }

    this.blockList = decorated;
    this.setData({
      selectedWeek: week,
      weekLabel: `第${week}周`,
      monthLabel: monday ? `${monday.getMonth() + 1}月` : '',
      headerDays,
      columns,
      sectionRows
    });
  },

  weeksTextOf(course) {
    const base = String(course.weeks || '').replace(/周/g, '');
    const parity = course.weekParity === 1 ? '（单）' : course.weekParity === 2 ? '（双）' : '';
    return `${base}周${parity}`;
  },

  todayKey() {
    return this.keyOf(new Date());
  },

  keyOf(date) {
    return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
  },

  onOpenWeekPicker() {
    if (!this.data.hasData) return;
    this.setData({ showWeekPicker: true });
  },

  onCloseWeekPicker() {
    this.setData({ showWeekPicker: false });
  },

  onPickWeek(event) {
    const week = Number(event.currentTarget.dataset.week);
    if (!(week >= 1)) return;
    this.setData({ showWeekPicker: false });
    this.applyWeek(week);
  },

  onBlockTap(event) {
    const id = Number(event.currentTarget.dataset.id);
    const block = (this.blockList || []).find((item) => item.id === id);
    if (!block) return;
    this.setData({
      detail: {
        name: block.name,
        teacher: block.teacher,
        room: block.location,
        timeText: `${block.dayLabel} 第${block.startSection}-${block.endSection}节 ${block.timeText}`,
        weeksText: block.weeksText
      }
    });
  },

  onCloseDetail() {
    this.setData({ detail: null });
  },

  noop() {},

  async importSchedule() {
    if (!app.ensureLogin()) {
      return;
    }
    this.setData({ importing: true });
    try {
      const data = await api.importSchedule();
      wx.showToast({ title: `已同步 ${data.personalCount} 门课`, icon: 'success' });
      this.semesterNameText = '';
      await this.loadSchedule();
    } catch (error) {
      wx.showToast({ title: (error && error.message) || '同步失败', icon: 'none' });
    } finally {
      this.setData({ importing: false });
    }
  }
});
