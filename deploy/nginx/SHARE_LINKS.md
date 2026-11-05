# 社区分享部署

App 分享地址为 `https://sailtrack.cn/c/{slug}`。后端公开网页承接和 Android 关联文件，登录后的帖子内容仍通过原有鉴权接口取得。

## 部署

1. 发布包含 `static/share/index.html` 和 `static/.well-known/assetlinks.json` 的后端。
2. 在 **sailtrack.cn 现有 HTTPS server** 内引入 `share-links.locations.conf`，将 `student_hub_backend` 指向对应后端。该文件只包含 location，保留现有站点配置。
3. 执行 `nginx -t` 后重载。确认下列请求直接返回成功，关联文件使用 JSON 类型，不跳转到登录页或其他域名。

```sh
curl -f https://sailtrack.cn/.well-known/assetlinks.json
curl -f https://sailtrack.cn/c/有效分享码
```

关联文件的包名为 `com.studenthub.app`。证书 SHA-256 来自本机现有正式签名 APK，通过 Android SDK `apksigner verify --print-certs app-release.apk` 提取：

```text
CE:16:D1:0D:02:BE:E4:A4:71:2B:4B:D6:67:2E:AB:39:78:CA:33:55:D4:29:2D:E8:E1:10:AB:A5:4B:03:77:E8
```

签名变更或接入应用商店代签时，应先核对实际分发 APK 的证书，再更新指纹。调试签名不会自动获得正式域名关联。

## 验收

- 浏览器打开有效短链，页面提供“打开 App”和下载入口；非法路径不生成 App 跳转。
- 已安装 App 时，分别验证冷启动和热启动；重复系统回调只打开一次。
- 未登录时先登录，随后继续打开原帖；请求中退出或换号时取消旧导航。
- 未安装时进入网页并可下载；帖子失效时 App 给出重试反馈。

```sh
adb shell pm verify-app-links --re-verify com.studenthub.app
adb shell pm get-app-links com.studenthub.app
adb shell am start -W -a android.intent.action.VIEW -d 'https://sailtrack.cn/c/有效分享码'
```

回退部署时移除新增的三个 location 并恢复上一份后端包，再执行 `nginx -t` 和重载。
