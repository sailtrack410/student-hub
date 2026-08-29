const api = require('../../utils/api');
const format = require('../../utils/format');
const config = require('../../utils/config');
const { resourceUrl } = require('../../utils/resource');
const app = getApp();

Page({
  data: { tab: 'notice', notices: [], contacts: [], loaded: false },
  onShow() { if (app.ensureLogin()) { app.refreshCommunityUnread(); this.loadTab(); } },
  switchTab(event) { const tab = event.currentTarget.dataset.tab; if (tab === this.data.tab) return; this.setData({ tab, loaded: false }, () => this.loadTab()); },
  async loadTab() {
    try {
      if (this.data.tab === 'notice') {
        const page = await api.pageNotifications({ pageNum: 1, pageSize: 50, communityOnly: true });
        this.setData({ notices: (page.records || []).map(item => Object.assign({}, item, { timeText: format.formatTime(item.createdAt), unread: !item.read })), loaded: true });
      } else {
        const contacts = await api.listMessageContacts();
        this.setData({ contacts: (contacts || []).map(item => Object.assign({}, item, { avatarSrc: item.avatarUrl ? resourceUrl(item.avatarUrl) : `${config.assetBaseUrl}/mascot/mascot-avatar.jpg`, timeText: format.formatTime(item.lastAt) })), loaded: true });
      }
    } catch (error) { this.setData({ loaded: true }); }
  },
  async openNotice(event) {
    const item = this.data.notices[Number(event.currentTarget.dataset.index)];
    if (!item) return;
    if (item.unread) { try { await api.markNotificationRead(item.notificationId); app.refreshCommunityUnread(); } catch (error) {} }
    if (item.relatedType === 'COMMUNITY_POST' && item.relatedId) wx.navigateTo({ url: `/pages/community/detail?id=${item.relatedId}` });
  },
  markAll() { api.markAllNotificationsRead(true).then(() => { app.refreshCommunityUnread(); this.loadTab(); }).catch(() => {}); },
  openChat(event) { const item = this.data.contacts[Number(event.currentTarget.dataset.index)]; if (item) wx.navigateTo({ url: `/pages/message/chat?userId=${item.peerId}&name=${encodeURIComponent(item.peerName || '')}&avatar=${encodeURIComponent(item.avatarSrc || '')}` }); }
});
