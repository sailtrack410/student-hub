const api = require('../../utils/api');
const { uploadImage, uploadAudio } = require('../../utils/request');
const { resourceUrl } = require('../../utils/resource');
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
  voiceTouchActive: false,

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
    if (this.audioContext) {
      this.audioContext.destroy();
      this.audioContext = null;
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
            uploaded.push({ fileId: String(stored.fileId), path: resourceUrl(stored.viewPath) });
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
    if (this.data.thinking || this.data.recording || this.voiceTouchActive) {
      return;
    }
    this.voiceTouchActive = true;
    const start = () => {
      if (!this.voiceTouchActive || this.data.thinking || this.data.recording) return;
      this.voiceStartTs = Date.now();
      const recorder = this.getRecorder();
      recorder.start({
        duration: 60000,
        sampleRate: 16000,
        numberOfChannels: 1,
        encodeBitRate: 48000,
        format: 'mp3'
      });
      this.setData({ recording: true });
    };
    wx.authorize({
      scope: 'scope.record',
      success: start,
      fail: () => {
        this.voiceTouchActive = false;
        wx.showToast({ title: '请允许使用麦克风', icon: 'none' });
      }
    });
  },

  getRecorder() {
    if (this.recorder) return this.recorder;
    const recorder = wx.getRecorderManager();
    recorder.onStop((res) => this.handleVoiceStop(res));
    recorder.onError(() => {
      this.voiceTouchActive = false;
      this.setData({ recording: false, thinking: false });
      wx.showToast({ title: '录音失败，请重试', icon: 'none' });
    });
    this.recorder = recorder;
    return recorder;
  },

  onVoiceEnd() {
    this.voiceTouchActive = false;
    if (!this.data.recording || !this.recorder) return;
    const duration = Date.now() - this.voiceStartTs;
    this.voiceDurationMs = duration;
    const recorder = this.recorder;
    this.setData({ recording: false });
    try { recorder.stop(); } catch (error) { /* ignore */ }
    if (duration < MIN_VOICE_MS) {
      wx.showToast({ title: '说话时间太短', icon: 'none' });
    }
  },

  onVoiceCancel() {
    this.voiceTouchActive = false;
    this.stopRecorder();
  },

  stopRecorder() {
    this.voiceTouchActive = false;
    if (this.recorder) {
      try {
        if (this.data.recording) this.recorder.stop();
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
    let submitted = false;
    try {
      const stored = await uploadAudio(res.tempFilePath);
      const durationMs = Number(res.duration) || this.voiceDurationMs || 0;
      const audio = {
        url: resourceUrl(stored.viewPath),
        durationText: formatVoiceDuration(durationMs)
      };
      this.appendMessage('USER', ' ', false, null, [], audio);
      this.setData({ scrollInto: `msg-${this.data.messages.length - 1}` });
      const text = await api.transcribeAudio(stored.fileId);
      if (!text || !text.trim()) {
        wx.showToast({ title: '没有听清，请再说一次', icon: 'none' });
        this.appendMessage('ASSISTANT', '这条语音没有识别清楚，请再试一次。', true);
        return;
      }
      this.requestReply(text.trim(), []);
      submitted = true;
    } catch (error) {
      wx.showToast({ title: error && error.message ? error.message : '语音识别失败', icon: 'none' });
    } finally {
      wx.hideLoading();
      if (!submitted) this.setData({ thinking: false });
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

  appendMessage(role, content, degraded = false, actions = null, images = [], audio = null) {
    messageSeq += 1;
    const messages = this.data.messages.concat([
      {
        key: `m${messageSeq}`,
        role,
        content,
        degraded,
        actions,
        images,
        audio,
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

  playAudio(event) {
    const url = event.currentTarget.dataset.url;
    if (!url) return;
    if (this.audioContext) {
      this.audioContext.stop();
      this.audioContext.destroy();
    }
    const context = wx.createInnerAudioContext();
    context.src = url;
    context.autoplay = true;
    context.onError(() => wx.showToast({ title: '语音播放失败', icon: 'none' }));
    this.audioContext = context;
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

function formatVoiceDuration(durationMs) {
  const seconds = Math.max(1, Math.round(Number(durationMs || 0) / 1000));
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}
