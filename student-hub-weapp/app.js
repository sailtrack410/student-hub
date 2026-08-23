App({
  globalData: {
    baseUrl: 'http://localhost:9000',
    accessToken: null,
    user: null
  },

  onLaunch() {
    // 检查本地缓存的登录状态
    const token = wx.getStorageSync('accessToken')
    const user = wx.getStorageSync('user')
    if (token && user) {
      this.globalData.accessToken = token
      this.globalData.user = user
    }
  }
})
