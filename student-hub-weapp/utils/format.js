// 展示格式化与状态映射

const REQUEST_STATUS = {
  SUBMITTED: { text: '待受理', theme: 'blue' },
  PROCESSING: { text: '处理中', theme: 'amber' },
  COMPLETED: { text: '已完成', theme: 'green' },
  REJECTED: { text: '未受理', theme: 'red' },
  CANCELLED: { text: '已撤销', theme: 'gray' }
};

const ORDER_STATUS = {
  PENDING_DISPATCH: { text: '待派单', theme: 'blue' },
  ASSIGNED: { text: '待处理', theme: 'amber' },
  PROCESSING: { text: '处理中', theme: 'amber' },
  ONSITE_PHOTO_RECORDED: { text: '现场已拍照', theme: 'blue' },
  PENDING_CONFIRMATION: { text: '已完成待确认', theme: 'amber' },
  COMPLETED: { text: '已办结', theme: 'green' },
  REJECTED: { text: '已驳回', theme: 'red' },
  CANCELLED: { text: '已撤销', theme: 'gray' }
};

const ACTION_TEXT = {
  SUBMIT: '提交申请',
  DISPATCH: '调度派单',
  START: '开始处理',
  COMPLETE: '办结',
  ARRIVAL_PHOTO: '现场拍照',
  COMPLETION_PHOTO: '上传完工照片',
  CONFIRM: '报修人确认',
  REOPEN: '退回重新处理',
  REJECT: '驳回',
  CANCEL: '撤销申请',
  EVALUATE: '服务评价'
};

function requestStatus(status) {
  const key = String(status || '').trim().toUpperCase();
  return REQUEST_STATUS[key] || { text: key || '待处理', theme: 'gray' };
}

function orderStatus(status) {
  const key = String(status || '').trim().toUpperCase();
  const aliases = { PENDING: 'PENDING_DISPATCH', IN_PROGRESS: 'PROCESSING', DONE: 'COMPLETED' };
  const normalized = aliases[key] || key;
  return ORDER_STATUS[normalized] || { text: normalized || '待处理', theme: 'gray' };
}

function actionText(action) {
  const key = String(action || '').trim().toUpperCase();
  return ACTION_TEXT[key] || key;
}

/** "2026-08-28T23:38:33" → "2026-08-28 23:38" */
function formatTime(iso) {
  if (!iso) {
    return '';
  }
  return iso.replace('T', ' ').slice(0, 16);
}

function formatDate(iso) {
  if (!iso) {
    return '';
  }
  return iso.slice(0, 10);
}

module.exports = { requestStatus, orderStatus, actionText, formatTime, formatDate };
