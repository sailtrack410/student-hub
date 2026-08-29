const api = require('../../utils/api');
const { uploadImage, uploadAudio } = require('../../utils/request');
const app = getApp();
const { markdownToHtml } = require('../../utils/markdown');

const SUGGESTIONS = ['宿舍报修怎么办理？', '在读证明去哪开？', '请假流程是什么？', '平台能帮我做什么？'];
const MAX_IMAGES = 4;
const MIN_VOICE_MS = 400;

let messageSeq = 0;

Page({
  data: {
    messages: [],
    suggestions: SUGGESTIONS,
    draft: '',
    pendingImages: [],
    inputMode: 'text',
    recording: false,
    thinking: false,
    scrollInto: ''
  },

  conversationId: null,
  recorder: null,
  voiceStartTs: 0,

  onShow() {
    if (!app.ensureLogin()) {
      return;
    }
    if (!this.conversationId && !this.data.messages.length) {
      this.createConversation();
    }
  },

  onUnload() {
    this.stopRecorder();
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

  toggleInputMode() {
    if (this.data.thinking) {
      return;
    }
    this.stopRecorder();
    this.setData({ inputMode: this.data.inputMode === 'text' ? 'voice' : 'text', recording: false });
  },

  chooseImage() {
    const remain = MAX_IMAGES - this.data.pendingImages.length;
    if (remain <= 0) {
      wx.showToast({ title: `最多添加 ${MAX_IMAGES} 张图片`, icon: 'none' });
      return;
    }
    wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: async (res) => {
        const files = res.tempFiles || [];
        wx.showLoading({ title: '上传中…', mask: true });
        try {
          const uploaded = [];
          for (const file of files) {
            const stored = await uploadImage(file.tempFilePath);
            uploaded.push({ fileId: stored.fileId, path: stored.viewPath });
          }
          this.setData({ pendingImages: this.data.pendingImages.concat(uploaded) });
        } catch (error) {
          // 上传层已提示
        } finally {
          wx.hideLoading();
        }
      }
    });
  },

  removeImage(event) {
    const index = event.currentTarget.dataset.index;
    const pendingImages = this.data.pendingImages.slice();
    pendingImages.splice(index, 1);
    this.setData({ pendingImages });
  },

  onVoiceStart() {
    if (this.data.thinking) {
      return;
    }
    this.voiceStartTs = Date.now();
    const recorder = wx.getRecorderManager();
    this.recorder = recorder;
    recorder.onStop((res) => this.handleVoiceStop(res));
    recorder.onError(() => {
      this.setData({ recording: false });
      wx.showToast({ title: '录音失败，请重试', icon: 'none' });
    });
    recorder.start({
      duration: 60000,
      sampleRate: 16000,
      numberOfChannels: 1,
      encodeBitRate: 48000,
      format: 'mp3'
    });
    this.setData({ recording: true });
  },

  onVoiceEnd() {
    if (!this.recorder) {
      return;
    }
    const duration = Date.now() - this.voiceStartTs;
    const recorder = this.recorder;
    this.recorder = null;
    this.setData({ recording: false });
    recorder.stop();
    if (duration < MIN_VOICE_MS) {
      wx.showToast({ title: '说话时间太短', icon: 'none' });
    }
  },

  onVoiceCancel() {
    this.stopRecorder();
  },

  stopRecorder() {
    if (this.recorder) {
      try {
        this.recorder.stop();
      } catch (error) {
        // ignore
      }
      this.recorder = null;
    }
    this.setData({ recording: false });
  },

  async handleVoiceStop(res) {
    if (!res.tempFilePath) {
      return;
    }
    if (Date.now() - this.voiceStartTs < MIN_VOICE_MS) {
      return;
    }
    wx.showLoading({ title: '识别中…', mask: true });
    this.setData({ thinking: true });
    try {
      const stored = await uploadAudio(res.tempFilePath);
      const text = await api.transcribeAudio(stored.fileId);
      if (!text || !text.trim()) {
        wx.showToast({ title: '没有听清，请再说一次', icon: 'none' });
        return;
      }
      this.submitMessage(text.trim(), []);
    } catch (error) {
      wx.showToast({ title: error && error.message ? error.message : '语音识别失败', icon: 'none' });
    } finally {
      wx.hideLoading();
      this.setData({ thinking: false });
    }
  },

  onSend() {
    const content = this.data.draft.trim();
    const images = this.data.pendingImages;
    if ((!content && !images.length) || this.data.thinking) {
      return;
    }
    this.submitMessage(content, images.slice());
  },

  submitMessage(content, images) {
    this.appendMessage('USER', content || ' ', false, null, images);
    this.setData({
      draft: '',
      pendingImages: [],
      thinking: true,
      scrollInto: `msg-${this.data.messages.length - 1}`
    });
    this.requestReply(content, images.map((item) => item.fileId));
  },

  appendMessage(role, content, degraded = false, actions = null, images = []) {
    messageSeq += 1;
    const messages = this.data.messages.concat([
      {
        key: `m${messageSeq}`,
        role,
        content,
        degraded,
        actions,
        images,
        html: role === 'ASSISTANT' ? markdownToHtml(content) : ''
      }
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

  previewImage(event) {
    const url = event.currentTarget.dataset.url;
    if (!url) {
      return;
    }
    wx.previewImage({ current: url, urls: [url] });
  },

  async requestReply(content, images) {
    try {
      if (!this.conversationId) {
        await this.createConversation();
      }
      const reply = await api.sendAiMessage(this.conversationId, content, images);
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