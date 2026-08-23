const app = getApp()

function loginWithWechat() {
  return new Promise((resolve, reject) => {
    wx.login({ success: resolve, fail: reject })
  })
}

function request(options) {
  return new Promise((resolve, reject) => {
    wx.request({ ...options, success: resolve, fail: reject })
  })
}

Page({
  data: {
    loading: false,
    logged: false,
    user: null,
    result: ''
  },

  onLoad() {
    // 检查是否已登录
    if (app.globalData.accessToken) {
      this.setData({
        logged: true,
        user: app.globalData.user
      })
    }
  },

  // 微信登录
  async handleLogin() {
    if (this.data.loading) return

    this.setData({ loading: true, result: '' })

    try {
      // 1. 获取微信登录 code
      const { code } = await loginWithWechat()

      // 2. 调用后端登录接口
      const res = await request({
        url: `${app.globalData.baseUrl}/api/common/auth/wechat-login`,
        method: 'POST',
        header: {
          'Content-Type': 'application/json'
        },
        data: { code }
      })

      const response = res && res.data
      if (response && response.success && response.data) {
        const { accessToken, user, isNewUser } = response.data

        // 保存登录状态
        app.globalData.accessToken = accessToken
        app.globalData.user = user
        wx.setStorageSync('accessToken', accessToken)
        wx.setStorageSync('user', user)

        this.setData({
          logged: true,
          user: user,
          result: `登录成功！${isNewUser ? '(新用户)' : '(老用户)'}\nToken: ${accessToken}`
        })

        wx.showToast({
          title: '登录成功',
          icon: 'success'
        })
      } else {
        this.setData({
          result: `登录失败: ${(response && response.message) || '服务返回异常'}`
        })
        wx.showToast({
          title: (response && response.message) || '登录失败',
          icon: 'none'
        })
      }
    } catch (err) {
      console.error('登录错误:', err)
      this.setData({
        result: `请求错误: ${err.errMsg || err.message}`
      })
      wx.showToast({
        title: '网络错误',
        icon: 'none'
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  // 退出登录
  handleLogout() {
    const token = app.globalData.accessToken

    // 调用后端退出接口
    if (token) {
      request({
        url: `${app.globalData.baseUrl}/api/common/auth/logout`,
        method: 'POST',
        header: {
          'Authorization': `Bearer ${token}`
        }
      })
    }

    // 清除本地状态
    app.globalData.accessToken = null
    app.globalData.user = null
    wx.removeStorageSync('accessToken')
    wx.removeStorageSync('user')

    this.setData({
      logged: false,
      user: null,
      result: '已退出登录'
    })

    wx.showToast({
      title: '已退出',
      icon: 'success'
    })
  },

  // 测试获取当前用户信息
  async handleGetUser() {
    const token = app.globalData.accessToken
    if (!token) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      return
    }

    try {
      const res = await request({
        url: `${app.globalData.baseUrl}/api/common/auth/me`,
        method: 'GET',
        header: {
          'Authorization': `Bearer ${token}`
        }
      })

      this.setData({
        result: `当前用户信息:\n${JSON.stringify(res.data, null, 2)}`
      })
    } catch (err) {
      this.setData({
        result: `获取用户信息失败: ${err.errMsg}`
      })
    }
  }
})
