const api = require('../../utils/api');
const config = require('../../utils/config');
const { resourceUrl } = require('../../utils/resource');
const { uploadImage } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    sections: [], sectionIndex: -1, title: '', content: '', images: [], topics: [], hotTopics: [], filteredTopics: [],
    topicKeyword: '', imageCompact: false, showMetaSheet: false, topicDraft: '', uploading: false, submitting: false
  },

  onLoad(options) {
    this.preferredSectionId = options.sectionId || '';
  },

  onShow() {
    if (!app.ensureLogin()) return;
    if (!this.data.sections.length) this.loadSections();
  },

  async loadSections() {
    try {
      const sections = await api.communitySections();
      let sectionIndex = sections.length ? 0 : -1;
      if (this.preferredSectionId) {
        const matched = sections.findIndex((item) => String(item.sectionId) === String(this.preferredSectionId));
        if (matched >= 0) sectionIndex = matched;
      }
      this.setData({ sections, sectionIndex });
      api.hotCommunityTopics().then((hotTopics) => this.setData({ hotTopics: hotTopics || [], filteredTopics: hotTopics || [] })).catch(() => {});
    } catch (error) {}
  },

  onSectionChange(event) {
    const value = event.detail && event.detail.value !== undefined ? event.detail.value : event.currentTarget.dataset.value;
    this.setData({ sectionIndex: Number(value) });
  },
  onTitleInput(event) { this.setData({ title: event.detail.value }); },
  onContentInput(event) { this.setData({ content: event.detail.value }); },
  onEditorFocus() { this.setData({ imageCompact: true }); },
  onImagesTap() { this.setData({ imageCompact: false }); },
  openMetaSheet() { this.setData({ showMetaSheet: true }); },
  closeMetaSheet() { this.setData({ showMetaSheet: false, topicDraft: '' }); },
  onTopicInput(event) { this.setData({ topicDraft: event.detail.value }); },
  onTopicSearch(event) {
    const topicKeyword = event.detail.value.trim();
    const filteredTopics = (this.data.hotTopics || []).filter((item) => !topicKeyword || item.name.indexOf(topicKeyword) >= 0);
    this.setData({ topicKeyword, filteredTopics });
  },
  toggleTopic(event) {
    const name = event.currentTarget.dataset.name;
    const topics = this.data.topics.slice();
    const index = topics.indexOf(name);
    if (index >= 0) topics.splice(index, 1);
    else if (topics.length < 8) topics.push(name);
    else return wx.showToast({ title: '最多添加 8 个话题', icon: 'none' });
    this.setData({ topics });
  },
  addDraftTopic() {
    const name = this.data.topicDraft.trim().replace(/^#/, '');
    if (!name) return wx.showToast({ title: '请输入话题名称', icon: 'none' });
    this.toggleTopic({ currentTarget: { dataset: { name } } });
    this.setData({ topicDraft: '' });
  },

  chooseImages() {
    const remain = 9 - this.data.images.length;
    if (remain <= 0 || this.data.uploading) return;
    wx.chooseMedia({
      count: remain, mediaType: ['image'], sourceType: ['album', 'camera'], sizeType: ['compressed'],
      success: async (res) => {
        this.setData({ uploading: true });
        try {
          const added = [];
          for (const file of (res.tempFiles || [])) {
            const stored = await uploadImage(file.tempFilePath);
            // 雪花 ID 超过 JS Number 安全范围，保留后端返回的字符串精度
            added.push({ fileId: String(stored.fileId), url: resourceUrl(stored.viewPath) });
          }
          this.setData({ images: this.data.images.concat(added).slice(0, 9) });
        } catch (error) {
          // 上传层已提示
        } finally {
          this.setData({ uploading: false });
        }
      }
    });
  },

  removeImage(event) {
    const images = this.data.images.slice();
    images.splice(Number(event.currentTarget.dataset.index), 1);
    this.setData({ images });
  },

  previewImage(event) {
    const urls = this.data.images.map((item) => item.url);
    wx.previewImage({ current: event.currentTarget.dataset.url, urls });
  },

  async submit() {
    const section = this.data.sections[this.data.sectionIndex];
    const title = this.data.title.trim();
    const content = this.data.content.trim();
    if (!section) return wx.showToast({ title: '请选择分区', icon: 'none' });
    if (!title) return wx.showToast({ title: '请输入标题', icon: 'none' });
    if (!content) return wx.showToast({ title: '请输入正文', icon: 'none' });
    if (this.data.uploading || this.data.submitting) return;
    this.setData({ submitting: true });
    try {
      const post = await api.publishCommunityPost({
        sectionId: section.sectionId, title, content,
        imageFileIds: this.data.images.map((item) => item.fileId), topics: this.data.topics
      });
      wx.showToast({ title: '发布成功', icon: 'success' });
      setTimeout(() => wx.redirectTo({ url: `/pages/community/detail?id=${post.postId}` }), 450);
    } catch (error) {
      this.setData({ submitting: false });
    }
  }
});
