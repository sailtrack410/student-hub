const api = require('../../utils/api');
const app = getApp();

const SUGGESTIONS = ['宿舍报修怎么办理？', '在读证明去哪开？', '请假流程是什么？', '平台能帮我做什么？'];

let messageSeq = 0;

Page({
  data: {
    messages: [],
    suggestions: SUGGESTIONS,
    draft: '',
    thinking: false,
    scrollInto: ''
  },

  conversationId: null,

  onShow() {
    if (!app.ensureLogin()) {
      return;
    }
    if (!this.conversationId && !this.data.messages.length) {
      this.createConversation();
    }
  },

  async createConversation() {
    try {
      const conversation = await api.createConversation();
      this.conversationId = conversation.conversationId;
    } catch (error) {
      // 请求层已提示
    }
  },

  onDraftInput(event) {
    this.setData({ draft: event.detail.value });
  },

  pickSuggest(event) {
    this.setData({ draft: event.currentTarget.dataset.text }, () => this.onSend());
  },

  onSend() {
    const content = this.data.draft.trim();
    if (!content || this.data.thinking) {
      return;
    }
    this.appendMessage('USER', content);
    this.setData({ draft: '', thinking: true, scrollInto: `msg-${this.data.messages.length - 1}` });
    this.requestReply(content);
  },

  appendMessage(role, content, degraded = false, actions = null) {
    messageSeq += 1;
    const messages = this.data.messages.concat([
      { key: `m${messageSeq}`, role, content, degraded, actions }
    ]);
    this.setData({
      messages,
      scrollInto: `msg-${messages.length - 1}`
    });
  },

  onActionTap(event) {
    const url = event.currentTarget.dataset.url;
    if (!url) {
      return;
    }
    wx.navigateTo({ url, fail: () => wx.showToast({ title: '页面打开失败', icon: 'none' }) });
  },

  async requestReply(content) {
    try {
      if (!this.conversationId) {
        await this.createConversation();
      }
      const reply = await api.sendAiMessage(this.conversationId, content);
      this.appendMessage(reply.role, reply.content, reply.degraded, reply.actions);
    } catch (error) {
      const message = error && error.code === 'AI_429_01'
        ? '你今天提问有点多啦，休息一下再聊～'
        : '消息发送失败，请稍后再试';
      this.appendMessage('ASSISTANT', message, true);
    } finally {
      this.setData({ thinking: false });
    }
  }
});
