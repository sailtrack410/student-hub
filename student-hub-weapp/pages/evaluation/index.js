const api = require('../../utils/api');

const RATING_TEXTS = ['很不满意', '不太满意', '一般', '比较满意', '非常满意'];
const TAG_OPTIONS = ['响应快', '态度好', '处理专业', '沟通顺畅', '一次解决', '超预期'];

Page({
  data: {
    rating: 0,
    ratingTexts: RATING_TEXTS,
    tagOptions: TAG_OPTIONS,
    selectedTags: [],
    comment: '',
    loading: false
  },

  onLoad(options) {
    this.requestId = options.requestId;
  },

  pickRating(event) {
    this.setData({ rating: Number(event.currentTarget.dataset.value) });
  },

  toggleTag(event) {
    const tag = event.currentTarget.dataset.tag;
    const selectedTags = this.data.selectedTags.slice();
    const index = selectedTags.indexOf(tag);
    if (index >= 0) {
      selectedTags.splice(index, 1);
    } else {
      selectedTags.push(tag);
    }
    this.setData({ selectedTags });
  },

  onCommentInput(event) {
    this.setData({ comment: event.detail.value });
  },

  async handleSubmit() {
    if (!this.data.rating || this.data.loading) {
      return;
    }
    this.setData({ loading: true });
    try {
      await api.submitEvaluation(this.requestId, {
        rating: this.data.rating,
        tags: this.data.selectedTags.join(','),
        comment: this.data.comment || undefined
      });
      wx.showToast({ title: '感谢你的评价！', icon: 'success' });
      setTimeout(() => {
        wx.redirectTo({ url: `/pages/request/detail?id=${this.requestId}` });
      }, 700);
    } catch (error) {
      // 请求层已提示
    } finally {
      this.setData({ loading: false });
    }
  }
});
