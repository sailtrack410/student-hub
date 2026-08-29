const api = require('../../utils/api');
const config = require('../../utils/config');
const { resourceUrl, thumbnailUrl } = require('../../utils/resource');
const format = require('../../utils/format');
const { uploadImage } = require('../../utils/request');
const app = getApp();

Page({
  data: { post: null, comments: [], commentText: '', submitting: false, commentSort: 'hot', commentImages: [], replyTarget: null, expandedReplies: {}, expandedContents: {} },

  onLoad(options) { this.postId = options.id; },
  onShow() { if (app.ensureLogin()) this.loadDetail(); },

  async loadDetail() {
    try {
      const detail = await api.getCommunityPost(this.postId);
      const post = this.formatPost(detail.post);
      const comments = (detail.comments || []).map((item) => Object.assign({}, item, {
        avatarSrc: item.avatarUrl ? resourceUrl(item.avatarUrl) : `${config.assetBaseUrl}/mascot/mascot-avatar.jpg`,
        imageUrls: (item.imageUrls || []).map(resourceUrl),
        thumbnailUrls: (item.thumbnailUrls || []).map(resourceUrl),
        createdAtText: format.formatTime(item.createdAt)
      }));
      this.allComments = comments;
      this.setData({ post, comments: this.buildCommentList() });
    } catch (error) {}
  },

  formatPost(post) {
    const imageUrls = (post.imageUrls || []).map(resourceUrl);
    const thumbnailUrls = (post.thumbnailUrls || []).map(resourceUrl);
    return Object.assign({}, post, {
      avatarSrc: post.avatarUrl ? resourceUrl(post.avatarUrl) : `${config.assetBaseUrl}/mascot/mascot-avatar.jpg`,
      imageUrls,
      thumbUrls: thumbnailUrls.length ? thumbnailUrls : imageUrls.map(thumbnailUrl),
      createdAtText: format.formatTime(post.createdAt)
    });
  },

  onCommentInput(event) { this.setData({ commentText: event.detail.value }); },

  sortComments(comments) {
    const list = (comments || []).slice();
    if (this.data.commentSort === 'asc') return list.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
    if (this.data.commentSort === 'desc') return list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    return list.sort((a, b) => (b.likeCount || 0) - (a.likeCount || 0) || new Date(a.createdAt) - new Date(b.createdAt));
  },

  buildCommentList() {
    const all = this.allComments || [];
    const roots = this.sortComments(all.filter((item) => !item.parentCommentId));
    const childrenOf = (parentId) => this.sortComments(all.filter((item) => String(item.parentCommentId) === String(parentId)));
    const result = [];
    roots.forEach((root) => {
      result.push(Object.assign({}, root, { longText: this.isLongComment(root.content), expanded: !!this.data.expandedContents[root.commentId] }));
      const replies = childrenOf(root.commentId);
      const expanded = !!this.data.expandedReplies[root.commentId];
      const visible = expanded ? replies : replies.slice(0, 3);
      const appendReply = (reply, depth) => {
        result.push(Object.assign({}, reply, { longText: this.isLongComment(reply.content), expanded: !!this.data.expandedContents[reply.commentId], replyDepth: depth }));
        childrenOf(reply.commentId).forEach((child) => appendReply(child, depth + 1));
      };
      visible.forEach((reply) => appendReply(reply, 1));
      if (replies.length > 3 && !expanded) result.push({ commentId: `more-${root.commentId}`, isMoreReplies: true, parentCommentId: root.commentId, hiddenReplyCount: replies.length - 3 });
    });
    return result;
  },

  isLongComment(content) {
    const text = String(content || '');
    return text.split('\n').length > 5 || text.length > 180;
  },

  setCommentSort(event) {
    const commentSort = event.currentTarget.dataset.sort;
    if (!['hot', 'asc', 'desc'].includes(commentSort)) return;
    this.setData({ commentSort }, () => this.setData({ comments: this.buildCommentList() }));
  },

  expandReplies(event) {
    const parentCommentId = event.currentTarget.dataset.id;
    const expandedReplies = Object.assign({}, this.data.expandedReplies, { [parentCommentId]: true });
    this.setData({ expandedReplies, comments: this.buildCommentList() });
  },

  toggleCommentContent(event) {
    const commentId = event.currentTarget.dataset.id;
    const expandedContents = Object.assign({}, this.data.expandedContents, { [commentId]: !this.data.expandedContents[commentId] });
    const comments = this.data.comments.map((item) => item.commentId === commentId ? Object.assign({}, item, { expanded: expandedContents[commentId] }) : item);
    this.setData({ expandedContents, comments });
  },

  replyComment(event) {
    const comment = this.data.comments[Number(event.currentTarget.dataset.index)];
    if (!comment || comment.isMoreReplies) return;
    if (!comment) return;
    this.setData({ replyTarget: comment, commentText: `@${comment.authorName} ` });
  },

  async chooseCommentImage() {
    if (this.data.commentImages.length >= 3) return wx.showToast({ title: '最多添加 3 张图片', icon: 'none' });
    wx.chooseImage({ count: 3 - this.data.commentImages.length, sizeType: ['compressed'], sourceType: ['album', 'camera'],
      success: async ({ tempFilePaths }) => {
        try {
          const added = [];
          for (const path of tempFilePaths) {
            const stored = await uploadImage(path);
            added.push({ fileId: String(stored.fileId), path });
          }
          this.setData({ commentImages: this.data.commentImages.concat(added) });
        } catch (error) {}
      }
    });
  },

  removeCommentImage(event) {
    const index = Number(event.currentTarget.dataset.index);
    this.setData({ commentImages: this.data.commentImages.filter((_, i) => i !== index) });
  },

  async togglePostLike() {
    try { this.setData({ post: this.formatPost(await api.likeCommunityPost(this.postId)) }); } catch (error) {}
  },

  async toggleCommentLike(event) {
    const index = Number(event.currentTarget.dataset.index);
    try {
      const updated = await api.likeCommunityComment(event.currentTarget.dataset.id);
      this.allComments = (this.allComments || []).map((item) => item.commentId === updated.commentId ? Object.assign({}, item, updated) : item);
      this.setData({ comments: this.buildCommentList() });
    } catch (error) {}
  },

  async pinComment(event) {
    try {
      const updated = await api.pinCommunityComment(event.currentTarget.dataset.id);
      this.allComments = (this.allComments || []).map((item) => Object.assign({}, item, { pinned: item.commentId === updated.commentId ? updated.pinned : false }));
      this.setData({ comments: this.buildCommentList() });
    } catch (error) {}
  },

  onCommentLongPress(event) {
    const index = Number(event.currentTarget.dataset.index);
    const comment = this.data.comments[index];
    const user = app.globalData.user || {};
    const userId = user.userId || user.id;
    if (!comment || !this.data.post || String(userId) !== String(this.data.post.authorId)) return;
    wx.showActionSheet({ itemList: [comment.pinned ? '取消置顶' : '置顶评论', '删除评论'], success: async ({ tapIndex }) => {
      try {
        if (tapIndex === 0) {
          await api.pinCommunityComment(comment.commentId);
        } else {
          await api.deleteCommunityComment(comment.commentId);
        }
        await this.loadDetail();
      } catch (error) {}
    }});
  },

  goAuthor(event) { wx.navigateTo({ url: `/pages/community/author?id=${event.currentTarget.dataset.id}` }); },

  onShareAppMessage() {
    const post = this.data.post;
    return {
      title: post ? post.title : '校园社区',
      path: post ? `/pages/community/detail?id=${post.postId}` : '/pages/community/index'
    };
  },

  async submitComment() {
    const content = this.data.commentText.trim();
    if (!content) return wx.showToast({ title: '请输入评论内容', icon: 'none' });
    if (this.data.submitting) return;
    this.setData({ submitting: true });
    try {
      await api.commentCommunityPost(this.postId, content, this.data.replyTarget && this.data.replyTarget.commentId,
        this.data.commentImages.map((item) => item.fileId));
      this.setData({ commentText: '', commentImages: [], replyTarget: null });
      wx.showToast({ title: '评论成功', icon: 'success' });
      await this.loadDetail();
    } catch (error) {
      // 请求层已提示
    } finally {
      this.setData({ submitting: false });
    }
  },

  previewImages(event) {
    wx.previewImage({ current: event.currentTarget.dataset.url, urls: this.data.post.imageUrls });
  },

  onThumbError(event) {
    const index = Number(event.currentTarget.dataset.index);
    const post = this.data.post;
    if (!post || !post.imageUrls[index]) return;
    const thumbUrls = post.thumbUrls.slice();
    thumbUrls[index] = post.imageUrls[index];
    this.setData({ post: Object.assign({}, post, { thumbUrls }) });
  }
});
