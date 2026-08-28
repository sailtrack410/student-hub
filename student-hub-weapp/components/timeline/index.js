const format = require('../../utils/format');

Component({
  properties: {
    logs: {
      type: Array,
      value: [],
      observer(logs) {
        this.setData({
          viewLogs: (logs || []).map((log) => ({
            actionText: format.actionText(log.action),
            operatorName: log.operatorName || '',
            comment: log.comment || '',
            timeText: format.formatTime(log.createdAt)
          }))
        });
      }
    }
  },

  data: {
    viewLogs: []
  }
});
