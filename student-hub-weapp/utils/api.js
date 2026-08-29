const { request } = require('./request');

module.exports = {
  // 认证
  wechatLogin: (code) => request({ url: '/api/common/auth/wechat-login', method: 'POST', data: { code }, silent: true }),
  devWechatLogin: () => request({ url: '/api/common/auth/dev-wechat-login', method: 'POST', silent: true }),
  logout: () => request({ url: '/api/common/auth/logout', method: 'POST', silent: true }),
  getCurrentUser: () => request({ url: '/api/common/auth/me' }),

  // 个人中心
  updateProfile: (data) => request({ url: '/api/app/profile', method: 'POST', data }),
  updatePhoneManual: (phone) => request({ url: '/api/app/profile/phone', method: 'POST', data: { phone } }),
  updatePhoneWechat: (code) =>
    request({ url: '/api/app/profile/phone/wechat', method: 'POST', data: { code }, silent: true }),
  fileViewUrl: (fileId) => request({ url: '/api/app/files/view-url', data: { fileId }, silent: true }),

  // 教务认证
  jwCaptcha: () => request({ url: '/api/app/jw/captcha', silent: true }),
  jwAuth: (data) => request({ url: '/api/app/jw/auth', method: 'POST', data, silent: true }),
  jwStatus: () => request({ url: '/api/app/jw/status', silent: true }),
  jwUnbind: () => request({ url: '/api/app/jw/unbind', method: 'POST' }),
  jwProfile: () => request({ url: '/api/app/jw/profile', silent: true }),

  // 课表
  importSchedule: (semesterCode) =>
    request({ url: '/api/app/schedule/import', method: 'POST', data: semesterCode ? { semesterCode } : {} }),
  getScheduleView: (semesterCode) =>
    request({ url: '/api/app/schedule/view', data: semesterCode ? { semesterCode } : {}, silent: true }),

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
  unreadCount: (communityOnly) => request({ url: '/api/app/notifications/unread-count', data: communityOnly ? { communityOnly: true } : {}, silent: true }),
  markNotificationRead: (id) => request({ url: `/api/app/notifications/${id}/read`, method: 'POST', silent: true }),
  markAllNotificationsRead: (communityOnly) => request({ url: `/api/app/notifications/read-all${communityOnly ? '?communityOnly=true' : ''}`, method: 'POST' }),

  // AI 助手
  createConversation: (title) => request({ url: '/api/app/ai/conversations', method: 'POST', data: { title } }),
  listAiMessages: (conversationId) => request({ url: `/api/app/ai/conversations/${conversationId}/messages` }),
  sendAiMessage: (conversationId, content, images) =>
    request({
      url: `/api/app/ai/conversations/${conversationId}/messages`,
      method: 'POST',
      data: { content, images: images && images.length ? images : undefined },
      silent: true,
      timeout: 90000
    }),
  transcribeAudio: (fileId) =>
    request({
      url: '/api/app/ai/speech/transcribe',
      method: 'POST',
      data: { fileId },
      timeout: 60000
    }),

  // 校园社区
  communitySections: () => request({ url: '/api/app/community/sections', silent: true }),
  hotCommunityTopics: () => request({ url: '/api/app/community/topics/hot', silent: true }),
  pageCommunityPosts: (query) => request({ url: '/api/app/community/posts', data: query, silent: true }),
  getCommunityPost: (postId) => request({ url: `/api/app/community/posts/${postId}`, silent: true }),
  publishCommunityPost: (data) => request({ url: '/api/app/community/posts', method: 'POST', data }),
  commentCommunityPost: (postId, content, parentCommentId, imageFileIds) =>
    request({ url: `/api/app/community/posts/${postId}/comments`, method: 'POST', data: { content, parentCommentId, imageFileIds } }),
  likeCommunityPost: (postId) => request({ url: `/api/app/community/posts/${postId}/like`, method: 'POST', silent: true }),
  likeCommunityComment: (commentId) => request({ url: `/api/app/community/comments/${commentId}/like`, method: 'POST', silent: true }),
  pinCommunityComment: (commentId) => request({ url: `/api/app/community/comments/${commentId}/pin`, method: 'POST' }),
  deleteCommunityComment: (commentId) => request({ url: `/api/app/community/comments/${commentId}`, method: 'DELETE' }),
  getCommunityAuthor: (authorId) => request({ url: `/api/app/community/authors/${authorId}`, silent: true }),
  listDirectMessages: (withUserId) => request({ url: '/api/app/community/messages', data: { withUserId }, silent: true }),
  sendDirectMessage: (receiverId, content, imageFileIds) => request({ url: '/api/app/community/messages', method: 'POST', data: { receiverId, content, imageFileIds } }),
  listMessageContacts: () => request({ url: '/api/app/community/messages/contacts', silent: true }),
  communityUnreadCount: () => request({ url: '/api/app/community/unread-count', silent: true }),
  markDirectMessagesRead: (withUserId) => request({ url: `/api/app/community/messages/read?withUserId=${encodeURIComponent(withUserId)}`, method: 'POST', silent: true }),

  // 我的工单（服务人员角色）
  pageAssignedOrders: (query) => request({ url: '/api/app/work-orders/assigned', data: query }),
  startWorkOrder: (orderId) => request({ url: `/api/app/work-orders/${orderId}/start`, method: 'POST' }),
  completeWorkOrder: (orderId, remark) =>
    request({ url: `/api/app/work-orders/${orderId}/complete`, method: 'POST', data: { remark } })
};
