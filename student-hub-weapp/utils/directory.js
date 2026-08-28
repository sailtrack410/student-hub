/**
 * 服务目录统一访问层：首页与大厅共用同一份目录缓存与导航逻辑。
 * 首页的快捷入口、常用服务都是大厅目录的衍生视图，禁止各自维护一套服务定义。
 */
const api = require('./api');
const app = getApp();

// 首页快捷入口固定的服务编码（顺序即展示顺序），名称图标以目录数据为准
const PINNED_CODES = ['REPAIR-DORM', 'AFFAIR-LEAVE', 'CERT-ENROLL', 'AFFAIR-VENUE', 'GUIDE-CONSULT', 'REPAIR-PUBLIC'];
// 首页常用服务展示的服务编码
const HOT_CODES = ['REPAIR-DORM', 'AFFAIR-LEAVE', 'CERT-ENROLL', 'AFFAIR-VENUE'];

/** 获取目录（带缓存），失败时抛出由调用方处理 */
async function ensureCatalog() {
  if (app.globalData.catalog && app.globalData.catalog.length) {
    return app.globalData.catalog;
  }
  const catalog = await api.getCatalog();
  app.globalData.catalog = catalog;
  return catalog;
}

/** 目录拍平为服务项列表 */
function flatten(catalog) {
  const items = [];
  (catalog || []).forEach((category) => {
    (category.items || []).forEach((item) => items.push(item));
  });
  return items;
}

/** 按编码查服务项 */
async function findItemByCode(code) {
  const catalog = await ensureCatalog();
  return flatten(catalog).find((item) => item.itemCode === code) || null;
}

/** 首页快捷入口：按固定编码顺序返回目录中的服务项 */
async function pinnedServices() {
  const catalog = await ensureCatalog();
  const items = flatten(catalog);
  return PINNED_CODES.map((code) => items.find((item) => item.itemCode === code)).filter(Boolean);
}

/** 首页常用服务 */
async function hotServices() {
  const catalog = await ensureCatalog();
  const items = flatten(catalog);
  return HOT_CODES.map((code) => items.find((item) => item.itemCode === code)).filter(Boolean);
}

/** 跳转办事指南详情 */
function toItemDetail(itemId) {
  if (!itemId) return;
  wx.navigateTo({ url: `/pages/service/detail?id=${itemId}` });
}

/** 跳转申请表单（登录与参数校验由目标页负责） */
function toItemApply(itemId) {
  if (!itemId) return;
  wx.navigateTo({ url: `/pages/service/apply?itemId=${itemId}` });
}

/** 跳转服务大厅 */
function toHall() {
  wx.switchTab({ url: '/pages/service/index' });
}

module.exports = { ensureCatalog, flatten, findItemByCode, pinnedServices, hotServices, toItemDetail, toItemApply, toHall };
