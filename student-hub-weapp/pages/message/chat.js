const api = require('../../utils/api');
const config = require('../../utils/config');
const { uploadImage, getToken } = require('../../utils/request');
const { resourceUrl } = require('../../utils/resource');
const app = getApp();

Page({
  data: { peerId: '', peerName: '私信', peerAvatar: '', messages: [], content: '', imageFiles: [], loading: true, sending: false },
  onLoad(options) {
    this.setData({ peerId: String(options.userId || ''), peerName: options.name ? decodeURIComponent(options.name) : '私信', peerAvatar: options.avatar ? decodeURIComponent(options.avatar) : `${config.assetBaseUrl}/mascot/mascot-avatar.jpg` });
  },
  onShow() { if (app.ensureLogin() && this.data.peerId) { this.loadMessages(); this.connectSocket(); } },
  onHide() { this.disconnectSocket(); },
  onUnload() { this.disconnectSocket(); },
  connectSocket() {
    this.disconnectSocket();
    const token = getToken();
    if (!token) return;
    const wsBase = config.baseUrl.replace(/^http/, 'ws');
    this.socketOpen = false;
    this.socketClosing = false;
    wx.connectSocket({ url: `${wsBase}/ws/community?token=${encodeURIComponent(token)}` });
    this.socketHandlers = {
      open: () => { this.socketOpen = true; },
      message: (event) => {
      let message;
      try { message = JSON.parse(event.data); } catch (error) { return; }
      if (!message || String(message.senderId) !== String(this.data.peerId)) return;
      const currentId = app.globalData.user ? String(app.globalData.user.userId) : '';
      if ((this.data.messages || []).some(item => String(item.messageId) === String(message.messageId))) return;
      this.setData({ messages: this.decorateMessages(this.data.messages.concat([message]), currentId), peerAvatar: message.senderAvatarUrl ? resourceUrl(message.senderAvatarUrl) : this.data.peerAvatar });
      api.markDirectMessagesRead(this.data.peerId).then(() => app.refreshCommunityUnread()).catch(() => {});
      wx.showToast({ title: '收到新消息', icon: 'none', duration: 1200 });
      wx.nextTick(() => wx.pageScrollTo({ scrollTop: 99999, duration: 0 }));
      },
      close: () => {
      this.socketOpen = false;
      if (!this.socketClosing && this.data.peerId) this.reconnectTimer = setTimeout(() => this.connectSocket(), 3000);
      }
    };
    wx.onSocketOpen(this.socketHandlers.open);
    wx.onSocketMessage(this.socketHandlers.message);
    wx.onSocketClose(this.socketHandlers.close);
  },
  disconnectSocket() {
    this.socketClosing = true;
    if (this.reconnectTimer) { clearTimeout(this.reconnectTimer); this.reconnectTimer = null; }
    if (this.socketOpen) wx.closeSocket();
    if (this.socketHandlers) {
      if (wx.offSocketOpen) wx.offSocketOpen(this.socketHandlers.open);
      if (wx.offSocketMessage) wx.offSocketMessage(this.socketHandlers.message);
      if (wx.offSocketClose) wx.offSocketClose(this.socketHandlers.close);
      this.socketHandlers = null;
    }
    this.socketOpen = false;
  },
  async loadMessages() {
    try {
      const messages = await api.listDirectMessages(this.data.peerId);
      api.markDirectMessagesRead(this.data.peerId).then(() => app.refreshCommunityUnread()).catch(() => {});
      const currentId = app.globalData.user ? String(app.globalData.user.userId) : '';
      const decorated = this.decorateMessages(messages || [], currentId);
      const firstPeerAvatar = decorated.find(item => !item.mine && item.senderAvatarUrl);
      this.setData({ messages: decorated, peerAvatar: firstPeerAvatar ? resourceUrl(firstPeerAvatar.senderAvatarUrl) : this.data.peerAvatar, loading: false });
      wx.nextTick(() => wx.pageScrollTo({ scrollTop: 99999, duration: 0 }));
    } catch (error) { this.setData({ loading: false }); }
  },
  decorateMessages(messages, currentId) {
    let previousAt = null;
    return messages.map((item) => {
      const currentAt = new Date(item.createdAt);
      const showTime = !previousAt || Number.isNaN(currentAt.getTime())
        || currentAt.getTime() - previousAt.getTime() > 10 * 60 * 1000;
      const decorated = Object.assign({}, item, {
        mine: String(item.senderId) === currentId,
        senderAvatarUrl: item.senderAvatarUrl ? resourceUrl(item.senderAvatarUrl) : '',
        imageUrls: (item.imageUrls || []).map(resourceUrl),
        thumbnailUrls: (item.thumbnailUrls || []).map(resourceUrl),
        showTime,
        timeLabel: showTime ? this.formatTimeLabel(item.createdAt) : ''
      });
      if (!Number.isNaN(currentAt.getTime())) previousAt = currentAt;
      return decorated;
    });
  },
  formatTimeLabel(value) {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    const pad = (number) => String(number).padStart(2, '0');
    const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`;
    const now = new Date();
    const startOfDay = (item) => new Date(item.getFullYear(), item.getMonth(), item.getDate()).getTime();
    const dayGap = Math.floor((startOfDay(now) - startOfDay(date)) / 86400000);
    if (dayGap === 0) return time;
    if (dayGap === 1) return `昨天 ${time}`;
    const dateText = `${date.getMonth() + 1}月${date.getDate()}日`;
    return date.getFullYear() === now.getFullYear() ? `${dateText} ${time}` : `${date.getFullYear()}年${dateText} ${time}`;
  },
  onInput(event) { this.setData({ content: event.detail.value }); },
  chooseImages() {
    const remain = 9 - this.data.imageFiles.length;
    if (remain <= 0 || this.data.sending) return;
    wx.chooseMedia({ count: remain, mediaType: ['image'], sourceType: ['album', 'camera'], sizeType: ['compressed'], success: async (result) => {
      try {
        const added = [];
        for (const file of (result.tempFiles || [])) {
          const stored = await uploadImage(file.tempFilePath);
          added.push({ fileId: String(stored.fileId), url: resourceUrl(stored.viewPath) });
        }
        this.setData({ imageFiles: this.data.imageFiles.concat(added).slice(0, 9) });
      } catch (error) {}
    } });
  },
  removeImage(event) {
    const index = Number(event.currentTarget.dataset.index);
    this.setData({ imageFiles: this.data.imageFiles.filter((_, current) => current !== index) });
  },
  previewMessageImages(event) {
    const item = this.data.messages[Number(event.currentTarget.dataset.messageIndex)];
    if (item && item.imageUrls && item.imageUrls.length) wx.previewImage({ current: event.currentTarget.dataset.url, urls: item.imageUrls });
  },
  async send() {
    const content = this.data.content.trim();
    if ((!content && !this.data.imageFiles.length) || this.data.sending) return;
    this.setData({ sending: true });
    try {
      const item = await api.sendDirectMessage(this.data.peerId, content, this.data.imageFiles.map(file => file.fileId));
      const currentId = app.globalData.user ? String(app.globalData.user.userId) : '';
      this.setData({ messages: this.decorateMessages(this.data.messages.concat([item]), currentId), content: '', imageFiles: [] });
      wx.nextTick(() => wx.pageScrollTo({ scrollTop: 99999, duration: 0 }));
    } catch (error) {} finally { this.setData({ sending: false }); }
  }
});
