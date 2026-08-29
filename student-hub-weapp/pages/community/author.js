const api = require('../../utils/api');
const config = require('../../utils/config');
const { resourceUrl } = require('../../utils/resource');
const format = require('../../utils/format');
const app = getApp();

Page({
  data: { profile: null, posts: [], currentUserId: '' },
  onLoad(options) { this.authorId = options.id; },
  onShow() { if (app.ensureLogin()) { this.setData({ currentUserId: app.globalData.user ? String(app.globalData.user.userId) : '' }); this.loadProfile(); } },
  async loadProfile() {
    try {
      const profile = await api.getCommunityAuthor(this.authorId);
      const posts = (profile.posts || []).map((post) => Object.assign({}, post, {
        avatarSrc: post.avatarUrl ? resourceUrl(post.avatarUrl) : `${config.assetBaseUrl}/mascot/mascot-avatar.jpg`,
        previewImages: (post.thumbnailUrls && post.thumbnailUrls.length ? post.thumbnailUrls : post.imageUrls || []).slice(0, 3).map(resourceUrl),
        createdAtText: format.formatTime(post.createdAt)
      }));
      this.setData({ profile: Object.assign({}, profile, { avatarSrc: profile.avatarUrl ? resourceUrl(profile.avatarUrl) : `${config.assetBaseUrl}/mascot/mascot-avatar.jpg` }), posts });
    } catch (error) {}
  },
  goPost(event) { wx.navigateTo({ url: `/pages/community/detail?id=${event.currentTarget.dataset.id}` }); },
  openChat() { wx.navigateTo({ url: `/pages/message/chat?userId=${this.data.profile.userId}&name=${encodeURIComponent(this.data.profile.displayName || '')}&avatar=${encodeURIComponent(this.data.profile.avatarSrc || '')}` }); }
});
