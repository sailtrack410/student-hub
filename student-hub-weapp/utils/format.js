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
  COMPLETED: { text: '已办结', theme: 'green' },
  REJECTED: { text: '已驳回', theme: 'red' },
  CANCELLED: { text: '已撤销', theme: 'gray' }
};

const ACTION_TEXT = {
  SUBMIT: '提交申请',
  DISPATCH: '调度派单',
  START: '开始处理',
  COMPLETE: '办结',
  REJECT: '驳回',
  CANCEL: '撤销申请',
  EVALUATE: '服务评价'
};

function requestStatus(status) {
  return REQUEST_STATUS[status] || { text: status, theme: 'gray' };
}

function orderStatus(status) {
  return ORDER_STATUS[status] || { text: status, theme: 'gray' };
}

function actionText(action) {
  return ACTION_TEXT[action] || action;
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
