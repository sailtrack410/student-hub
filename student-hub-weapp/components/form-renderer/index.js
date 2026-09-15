/**
 * 动态表单渲染器：由服务项 formSchema 驱动。
 * 字段状态只在组件内部维护，输入时按路径更新单个字段并对外发出 change 事件，
 * 不回写 value 属性，避免观察器循环与输入焦点丢失。
 * 图片字段：选择后立即上传，字段值为文件 ID 数组，缩略图用签名访问路径。
 */
const { uploadImage } = require('../../utils/request');
const config = require('../../utils/config');
const { resourceUrl } = require('../../utils/resource');

Component({
  properties: {
    schema: {
      type: Array,
      value: [],
      observer(schema) {
        this.buildFields(schema || []);
      }
    }
  },

  data: {
    fields: []
  },

  lifetimes: {
    attached() {
      // 组件晚于 schema 属性挂载时，observer 可能未触发，这里兜底构建一次
      if (!this.data.fields.length && (this.data.schema || []).length) {
        this.buildFields(this.data.schema);
      }
    }
  },

  methods: {
    buildFields(schema) {
      if (!schema || !schema.length) {
        this.value = {};
        this.setData({ fields: [] });
        this.emitChange();
        return;
      }
      const previousValue = this.value || {};
      const previousFields = this.data.fields || [];
      this.value = Object.assign({}, previousValue);
      const fields = schema.map((field) => {
        const previous = previousFields.find((item) => item.key === field.key);
        const savedValue = previousValue[field.key];
        if (field.type === 'image') {
          const value = Array.isArray(savedValue)
            ? savedValue.slice()
            : (previous && Array.isArray(previous.value) ? previous.value.slice() : []);
          return Object.assign({}, field, {
            display: '',
            urls: previous && Array.isArray(previous.urls) ? previous.urls.slice() : [],
            value
          });
        }
        const display = previous && previous.display !== undefined
          ? previous.display
          : (savedValue === undefined || savedValue === null ? '' : String(savedValue));
        return Object.assign({}, field, { display });
      });
      this.setData({ fields });
      this.emitChange();
    },

    emitChange() {
      this.triggerEvent('change', { value: Object.assign({}, this.value) });
    },

    updateField(index, rawValue, display) {
      const field = this.data.fields[index];
      if (!field) {
        return;
      }
      let finalValue = rawValue;
      if (field.type === 'number') {
        finalValue = rawValue === '' || rawValue === null ? null : Number(rawValue);
      }
      this.value[field.key] = finalValue;
      const patch = {};
      patch[`fields[${index}].display`] = display;
      this.setData(patch);
      this.emitChange();
    },

    onInput(event) {
      const index = Number(event.currentTarget.dataset.index);
      this.updateField(index, event.detail.value, event.detail.value);
    },

    onPick(event) {
      const index = Number(event.currentTarget.dataset.index);
      const field = this.data.fields[index];
      if (!field) {
        return;
      }
      const picked = event.detail.value;
      if (field.type === 'select') {
        const option = field.options[Number(picked)];
        this.updateField(index, option, option);
      } else {
        this.updateField(index, picked, picked);
      }
    },

    /** 拍照/选图后逐张上传，字段值保存文件 ID 数组 */
    onAddImage(event) {
      const index = Number(event.currentTarget.dataset.index);
      const field = this.data.fields[index];
      if (!field || field.uploading) {
        return;
      }
      const maxCount = field.maxCount || 3;
      const remaining = maxCount - field.urls.length;
      if (remaining <= 0) {
        wx.showToast({ title: '已达上传数量上限', icon: 'none' });
        return;
      }
      wx.chooseMedia({
        count: remaining,
        mediaType: ['image'],
        sizeType: ['compressed'],
        success: async (res) => {
          if (!res.tempFiles || !res.tempFiles.length) {
            return;
          }
          this.setData({ [`fields[${index}].uploading`]: true });
          try {
            for (const tempFile of res.tempFiles) {
              const stored = await uploadImage(tempFile.tempFilePath);
              const current = this.data.fields[index];
              if (!current || current.urls.length >= maxCount) {
                break;
              }
              this.setData({
                [`fields[${index}].urls`]: current.urls.concat(resourceUrl(stored.viewPath)),
                [`fields[${index}].value`]: current.value.concat(String(stored.fileId))
              });
              this.value[field.key] = this.data.fields[index].value.slice();
              this.emitChange();
            }
          } catch (error) {
            // 上传失败已提示
          } finally {
            this.setData({ [`fields[${index}].uploading`]: false });
          }
        },
        fail: (error) => {
          // 用户取消不提示；权限被拒等情况给出可操作的指引
          const errorMessage = error && error.errMsg ? error.errMsg : '';
          if (errorMessage.indexOf('cancel') >= 0) {
            return;
          }
          console.error('选择图片失败', error);
          wx.showModal({
            title: '无法选择图片',
            content: '请在设置中允许小程序使用相机和相册后重试',
            confirmText: '去设置',
            success: (res) => {
              if (res.confirm) {
                wx.openSetting();
              }
            }
          });
        }
      });
    },

    onRemoveImage(event) {
      const index = Number(event.currentTarget.dataset.field);
      const imgIndex = Number(event.currentTarget.dataset.img);
      const field = this.data.fields[index];
      if (!field) {
        return;
      }
      const urls = field.urls.slice();
      const value = (field.value || []).slice();
      urls.splice(imgIndex, 1);
      if (value.length > imgIndex) {
        value.splice(imgIndex, 1);
      }
      this.setData({ [`fields[${index}].urls`]: urls, [`fields[${index}].value`]: value });
      this.value[field.key] = value;
      this.emitChange();
    },

    onPreviewImage(event) {
      const index = Number(event.currentTarget.dataset.field);
      const imgIndex = Number(event.currentTarget.dataset.img);
      const field = this.data.fields[index];
      if (!field) {
        return;
      }
      wx.previewImage({ urls: field.urls, current: field.urls[imgIndex] });
    }
  }
});
