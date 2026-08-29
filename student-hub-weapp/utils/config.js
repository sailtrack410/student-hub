// 服务端地址：真机调试时改为 Mac 的局域网 IP（终端执行 ipconfig getifaddr en0 查看）
const env = {
  baseUrl: 'http://192.168.31.248:9000',
  // 静态图片放在 MinIO 的公开只读前缀，避免占用小程序源码包体积
  assetBaseUrl: 'https://student.sailtrack.cn/student-hub/static/assets'
};

module.exports = env;
