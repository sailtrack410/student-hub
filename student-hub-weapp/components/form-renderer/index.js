Component({
  properties: {
    schema: {
      type: Array,
      value: [],
      observer(schema) {
        this.applySchema(schema || [], this.data.value || {});
      }
    },
    value: {
      type: Object,
      value: {},
      observer(value) {
        if (value) {
          this.applySchema(this.data.schema || [], value);
        }
      }
    }
  },

  data: {
    fields: []
  },

  methods: {
    /** 将 schema 与当前值合并为可直接渲染的字段视图 */
    applySchema(schema, value) {
      const fields = schema.map((field) => {
        const raw = value[field.key];
        let display = '';
        if (raw !== undefined && raw !== null) {
          display = Array.isArray(raw) ? `${raw.length} 张图片` : String(raw);
        }
        return Object.assign({}, field, { display });
      });
      this.setData({ fields });
    },

    emitValue(key, fieldValue) {
      const value = Object.assign({}, this.data.value || {}, { [key]: fieldValue });
      this.setData({ value });
      this.applySchema(this.data.schema || [], value);
      this.triggerEvent('change', { value });
    },

    onInput(event) {
      const key = event.currentTarget.dataset.key;
      let inputValue = event.detail.value;
      const field = (this.data.schema || []).find((item) => item.key === key);
      if (field && field.type === 'number') {
        inputValue = inputValue === '' ? null : Number(inputValue);
      }
      this.emitValue(key, inputValue);
    },

    onPick(event) {
      const key = event.currentTarget.dataset.key;
      const index = event.detail.value;
      const field = (this.data.schema || []).find((item) => item.key === key);
      if (field && field.type === 'select') {
        this.emitValue(key, field.options[Number(index)]);
      } else {
        this.emitValue(key, index);
      }
    }
  }
});
