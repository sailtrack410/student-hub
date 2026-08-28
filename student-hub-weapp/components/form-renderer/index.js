/**
 * 动态表单渲染器：由服务项 formSchema 驱动。
 * 字段状态只在组件内部维护，输入时按路径更新单个字段并对外发出 change 事件，
 * 不回写 value 属性，避免观察器循环与输入焦点丢失。
 */
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
      this.value = {};
      const fields = schema.map((field) => Object.assign({}, field, { display: '' }));
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
    }
  }
});
