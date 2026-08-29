const api = require('../../utils/api');
const config = require('../../utils/config');
const { resourceUrl, thumbnailUrl } = require('../../utils/resource');
const format = require('../../utils/format');
const app = getApp();

Page({
  data: {
    sections: [], activeSectionId: '', keyword: '', posts: [], pageNum: 1, hasMore: true, loaded: false,
    bannerTitle: '校园社区', bannerSubtitle: '分享校园生活，也听见彼此的声音', bannerImage: `${config.assetBaseUrl}/community/community-banner.jpg`
  },

  onShow() {
    if (!app.ensureLogin()) return;
    app.refreshCommunityUnread();
    if (this.needReload !== false) this.reload();
    this.needReload = true;
  },

  onPullDownRefresh() {
    this.reload().finally(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore) this.loadPosts(this.data.pageNum + 1);
  },

  async reload() {
    this.setData({ posts: [], pageNum: 1, hasMore: true });
    try {
      const sections = await api.communitySections();
      const first = (sections || [])[0] || {};
      this.setData({ sections: sections || [], bannerTitle: first.bannerTitle || '校园社区',
        bannerSubtitle: first.bannerSubtitle || '分享校园生活，也听见彼此的声音',
        bannerImage: this.resourceUrl(first.bannerImage) || `${config.assetBaseUrl}/community/community-banner.jpg` });
      await this.loadPosts(1);
    } catch (error) {
      this.setData({ loaded: true });
    }
  },

  async loadPosts(pageNum) {
    try {
      const query = { pageNum, pageSize: 10 };
      if (this.data.activeSectionId) query.sectionId = this.data.activeSectionId;
      if (this.data.keyword) query.keyword = this.data.keyword;
      const page = await api.pageCommunityPosts(query);
      const posts = (page.records || []).map((post) => {
        const imageUrls = (post.imageUrls || []).map(resourceUrl);
        const thumbnailUrls = (post.thumbnailUrls || []).map(resourceUrl);
        return Object.assign({}, post, {
        avatarSrc: post.avatarUrl ? resourceUrl(post.avatarUrl) : `${config.assetBaseUrl}/mascot/mascot-avatar.jpg`,
        imageUrls,
        previewImages: (thumbnailUrls.length ? thumbnailUrls : imageUrls.slice(0, 3).map(thumbnailUrl)),
        createdAtText: format.formatTime(post.createdAt)
        });
      });
      this.setData({
        posts: pageNum === 1 ? posts : this.data.posts.concat(posts),
        pageNum,
        hasMore: posts.length >= 10,
        loaded: true
      });
    } catch (error) {
      this.setData({ loaded: true });
    }
  },

  pickSection(event) {
    const id = event.currentTarget.dataset.id || '';
    if (String(id) === String(this.data.activeSectionId)) return;
    const section = this.data.sections.find((item) => String(item.sectionId) === String(id));
    this.setData({ activeSectionId: id,
      bannerTitle: section ? (section.bannerTitle || section.sectionName) : '校园社区',
      bannerSubtitle: section ? (section.bannerSubtitle || section.description || '') : '分享校园生活，也听见彼此的声音',
        bannerImage: section && section.bannerImage ? this.resourceUrl(section.bannerImage) : `${config.assetBaseUrl}/community/community-banner.jpg`
    }, () => this.reload());
  },

  onSearchInput(event) {
    this.setData({ keyword: event.detail.value.trim() }, () => this.reload());
  },

  onSearchConfirm() { this.reload(); },

  clearSearch() { this.setData({ keyword: '' }, () => this.reload()); },

  async toggleLike(event) {
    const index = Number(event.currentTarget.dataset.index);
    const post = this.data.posts[index];
    if (!post) return;
    try {
      const updated = await api.likeCommunityPost(post.postId);
      const posts = this.data.posts.slice();
      posts[index] = Object.assign({}, post, updated, {
        avatarSrc: post.avatarSrc, previewImages: post.previewImages, imageUrls: post.imageUrls, thumbnailUrls: post.thumbnailUrls,
        createdAtText: post.createdAtText
      });
      this.setData({ posts });
    } catch (error) {}
  },

  resourceUrl,

  prepareShare(event) {
    const post = this.data.posts[Number(event.currentTarget.dataset.index)];
    this.shareTargetPost = post || null;
  },

  onShareAppMessage() {
    const post = this.shareTargetPost;
    return {
      title: post ? post.title : '校园社区',
      path: post ? `/pages/community/detail?id=${post.postId}` : '/pages/community/index'
    };
  },

  onThumbError(event) {
    const postIndex = Number(event.currentTarget.dataset.index);
    const imageIndex = Number(event.currentTarget.dataset.imageIndex);
    const post = this.data.posts[postIndex];
    if (!post || !post.imageUrls[imageIndex]) return;
    const posts = this.data.posts.slice();
    const previewImages = (post.previewImages || []).slice();
    previewImages[imageIndex] = post.imageUrls[imageIndex];
    posts[postIndex] = Object.assign({}, post, { previewImages });
    this.setData({ posts });
  },

  goPublish() {
    if (!app.ensureLogin()) return;
    wx.navigateTo({ url: `/pages/community/publish?sectionId=${this.data.activeSectionId || ''}` });
  },

  goDetail(event) {
    this.needReload = false;
    wx.navigateTo({ url: `/pages/community/detail?id=${event.currentTarget.dataset.id}` });
  },

  previewImages(event) {
    const post = this.data.posts[Number(event.currentTarget.dataset.index)];
    if (!post || !post.imageUrls.length) return;
    wx.previewImage({ current: event.currentTarget.dataset.url, urls: post.imageUrls });
  }
});
