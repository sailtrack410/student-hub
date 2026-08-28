const { request } = require('./request');

module.exports = {
  // 认证
  wechatLogin: (code) => request({ url: '/api/common/auth/wechat-login', method: 'POST', data: { code }, silent: true }),
  logout: () => request({ url: '/api/common/auth/logout', method: 'POST', silent: true }),
  getCurrentUser: () => request({ url: '/api/common/auth/me' }),

  // 个人中心
  updateProfile: (displayName) => request({ url: '/api/app/profile', method: 'POST', data: { displayName } }),
  updateIdentity: (identityType) =>
    request({ url: '/api/app/profile/identity', method: 'POST', data: { identityType } }),

  // 服务目录
  getCatalog: () => request({ url: '/api/app/services' }),
  getServiceItem: (itemId) => request({ url: `/api/app/services/${itemId}` }),

  // 服务申请
  submitRequest: (data, idempotencyKey) =>
    request({
      url: '/api/app/service-requests',
      method: 'POST',
      data,
      header: { 'Idempotency-Key': idempotencyKey }
    }),
  pageMyRequests: (query) =>
    request({ url: '/api/app/service-requests', data: query }),
  getRequestDetail: (requestId) => request({ url: `/api/app/service-requests/${requestId}` }),
  cancelRequest: (requestId, reason) =>
    request({ url: `/api/app/service-requests/${requestId}/cancel`, method: 'POST', data: { reason } }),

  // 评价
  submitEvaluation: (requestId, data) =>
    request({ url: `/api/app/service-requests/${requestId}/evaluation`, method: 'POST', data }),

  // 通知
  pageNotifications: (query) => request({ url: '/api/app/notifications', data: query }),
  unreadCount: () => request({ url: '/api/app/notifications/unread-count', silent: true }),
  markNotificationRead: (id) => request({ url: `/api/app/notifications/${id}/read`, method: 'POST', silent: true }),
  markAllNotificationsRead: () => request({ url: '/api/app/notifications/read-all', method: 'POST' }),

  // AI 助手
  createConversation: (title) => request({ url: '/api/app/ai/conversations', method: 'POST', data: { title } }),
  listAiMessages: (conversationId) => request({ url: `/api/app/ai/conversations/${conversationId}/messages` }),
  sendAiMessage: (conversationId, content) =>
    request({ url: `/api/app/ai/conversations/${conversationId}/messages`, method: 'POST', data: { content }, silent: true }),

  // 我的工单（服务人员角色）
  pageAssignedOrders: (query) => request({ url: '/api/app/work-orders/assigned', data: query }),
  startWorkOrder: (orderId) => request({ url: `/api/app/work-orders/${orderId}/start`, method: 'POST' }),
  completeWorkOrder: (orderId, remark) =>
    request({ url: `/api/app/work-orders/${orderId}/complete`, method: 'POST', data: { remark } })
};
