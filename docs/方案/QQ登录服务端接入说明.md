# QQ 登录服务端接入说明（2026-10-20）

> 适用范围：Flutter/Android/iOS 客户端通过 QQ SDK 拿到 `accessToken` 后，调用本平台
> `POST /api/common/auth/qq-login` 完成登录。
> 本文只讲服务端与 QQ 互联配置；网站/H5 的 authorization_code 重定向流程尚未实现，见 §6。
> 本机状态（2026-10-20）：服务端 real 模式、Android 客户端 `tencent_qq` 1.0.0、release 签名均已打通。
> 已在模拟器用真实 QQ 账号完成完整授权：服务端反查 openid 成功并首次建号，第二次授权复用同一用户；
> 授权页显示应用名「绵城一码通」。QQ 授权与业务登录成功路径已验证，剩余工作是应用审核/正式发布，见 §5/§7。

## 1. 当前服务端已具备的能力

| 能力 | 位置/接口 | 状态 |
|---|---|---|
| QQ 登录接口 | `POST /api/common/auth/qq-login`，body `{accessToken, openId?}` | 已存在 |
| 服务端反查 openId | `GET https://graph.qq.com/oauth2.0/me?access_token=...&unionid=1&fmt=json` | 已实现 |
| token 归属应用校验 | 优先校验 `/me` 返回的 `client_id == APP ID`；缺失时用 `get_user_info` 的 APP ID 三元组兜底 | 已实现 |
| QQ 昵称/头像默认展示 | `get_user_info` 读取 `nickname`/`figureurl_qq_2`；首登默认用 QQ 昵称+头像 | 本次新增 |
| 已登录账号绑定 QQ | `POST /api/app/profile/qq`，body `{accessToken, openId?}` | 本次新增 |
| 仅覆盖未自定义资料 | `sys_user.display_name_customized`/`avatar_customized` 标记；自定义后 QQ 不再覆盖 | 本次新增 |
| 客户端 openId 一致性校验 | 只接受与服务端反查一致的值，可省略 | 已实现 |
| 首次登录自动建号 | `sys_user.qq_open_id`，默认 `REQUESTER` 角色、`BASIC` 授权态 | 已实现 |
| UnionID | `sys_user.qq_union_id`，仅应用开通 UnionID 机制时才返回 | 已实现 |
| mock/real 切换 | `QQ_MODE=mock/real` | 已实现 |
| 错误码 | `QQ_LOGIN_FAILED`（401）、`QQ_NOT_CONFIGURED`（503） | 已实现 |

服务端不会信任客户端提交的 `openId`；`accessToken` 不落库、不写日志。real 模式下会校验
`/me` 返回的 `client_id`（或兜底调用 `get_user_info` 的 APP ID 三元组），拒绝其他 QQ 应用签发的 token。

### 1.2 管理端配置与热更新

QQ 的 `mode`、APP ID、APP KEY、API 地址、默认校区和超时均已纳入管理端
「系统配置 → 运行配置 → QQ 登录」：

- 首次启动把非空环境变量迁移到 `sys_config` 种子行；
- 页面保存后事务提交即热更新 `QqProperties` 和 RestClient，`mock`/`real` 可随时切换，无需重启；
- APP KEY 使用 `CONFIG_SECRET_KEY` 派生的 AES-256-GCM 加密落库，页面/API 不回显；
- 「外部集成 → QQ 登录 → 测试连通」只探测 API 主机可达性，真实身份仍需用户 accessToken；
- 清空配置后不会回退环境变量，避免“清空后又悄悄生效”。

### 1.1 QQ 昵称与头像规则

- 首次 QQ 登录：`displayName` 优先取 QQ `nickname`，取不到时回退 `QQ用户` + openid 前 4 位；`avatar_url` 取 `figureurl_qq_2`（100×100），回退 `figureurl_qq_1` 等，并统一把 `http://` 升级为 `https://`。
- 邮箱注册账号默认昵称为 `用户` + 6 位数字，头像为空；App 用昵称首字 + 渐变底色显示默认头像，不伪造图片。
- 用户主动改过昵称或头像后，`display_name_customized` / `avatar_customized` 置 1；之后 QQ 登录或绑定只补空字段，不覆盖自定义值。
- 已登录账号可调 `POST /api/app/profile/qq` 绑定 QQ；同一 QQ 已绑定其他账号、或当前账号已绑定其他 QQ 时明确报错，不静默换绑。
- QQ 移动 SDK 只提供 `openid`，不提供数字 QQ 号；平台 `username` 为 `qq_<openid 片段>`，App 展示 QQ 昵称和该平台账号 ID。

## 2. 你已完成“认证”后，还需要在 QQ 互联完成的配置

“实名认证”只是开发者账号层面；QQ 登录能否授权，取决于**应用本身**是否审核通过并配置了平台信息。

### 2.1 创建正确的应用类型

- Android/iOS：创建 **移动应用**。
- 网站/H5：创建 **网站应用**（需要网站域名、备案信息与回调地址，流程不同）。
- 不能拿“网站应用”的 APP ID 直接走移动 SDK 的 accessToken 流程。

### 2.2 完善平台信息（必须与安装包一致）

Android：
- 包名：`com.studenthub.app`（`app/android/app/build.gradle.kts`），与 QQ 互联后台「平台信息」一致。
- 应用签名：QQ 后台当前登记了两条，分别对应 release/debug：
  - release：`23:28:1B:DD:FF:43:E8:5A:DB:C9:05:45:5E:C8:E9:E2`（正式发布包，已验证）
  - debug：`18:E4:A2:57:93:1F:0D:D8:E4:DD:C9:14:98:65:32:CF`（本地调试包，已验证）
- release 签名已由 `app/android/key.properties` + `artifacts/keystore/smartcampus-release.jks` 接入；
  `key.properties` 不入 Git，密码在 macOS Keychain（见 `artifacts/keystore/README.md`）。
- 如果 QQ 后台只保留一套签名，发布前务必用 release MD5，否则正式包无法调起授权。

iOS：
- Bundle ID、URL Scheme（QQ 互联后台配置的值必须与 App 工程一致）。
- iOS 还要在 `Info.plist`/URL Types 注册 QQ 回调 Scheme；这部分属于客户端工作。

网站/H5：
- 授权回调地址（必须与请求中的 `redirect_uri` 完全一致）、域名归属/备案、网站审核。

### 2.3 申请并审核“QQ 登录”权限

- 在应用详情中申请 QQ 登录能力，提交应用信息、隐私政策、功能截图等，等待审核通过。
- QQ 互联官方说明：申请 QQ 登录需要审核，约 5 个工作日；创建应用后可立即拿到 appid/appkey，
  但**未审核通过时只有注册的开发者 QQ 号能测试登录**，审核通过后才能全量使用。未审核/信息不完整时
  常见现象是授权页报错或回调无 code。
- 如需多端统一账号，再申请开通 **UnionID 机制**；不开通时 `unionId` 为空，不影响单应用登录。

### 2.4 客户端合规

- 隐私政策中加入 QQ SDK/第三方登录说明，并在用户同意隐私政策后再初始化 QQ SDK。
- 不要把 APP KEY 写进客户端；客户端只持有 APP ID（QQ SDK 公开参数）。
- 应用商店上架时按平台要求声明第三方 SDK 与个人信息收集范围。

## 3. 服务端需要的环境变量

在未跟踪的 `.env` 中配置（不要提交真实值）：

```bash
QQ_MODE=real
QQ_CLIENT_ID=你的 QQ 互联 APP ID
QQ_CLIENT_SECRET=你的 QQ 互联 APP KEY   # 移动端 token 反查暂不使用，预留给网站授权码流程
QQ_DEFAULT_CAMPUS_ID=1
QQ_API_BASE_URL=https://graph.qq.com
QQ_CONNECT_TIMEOUT_SECONDS=5
QQ_READ_TIMEOUT_SECONDS=10
```

说明：
- **移动端 SDK 流程服务端只需要 `QQ_CLIENT_ID`**；`QQ_CLIENT_SECRET` 不能下发客户端，当前也不会参与 accessToken 反查。
- 缺少 `QQ_CLIENT_ID` 时 real 模式返回 `AUTH_503_02`，不会静默退回 mock。
- mock 模式只用于本地联调，不能用于验收真实 QQ 登录。

## 4. 服务端调用链

```text
App(QQ SDK) --accessToken--> POST /api/common/auth/qq-login
                               |
                               +--> graph.qq.com/oauth2.0/me
                               |      返回 openid / unionid / client_id
                               |      client_id != APP ID -> 拒绝
                               |      client_id 缺失 -> 再调 get_user_info 兜底
                               |
                               +--> graph.qq.com/user/get_user_info（仅兜底）
                                      access_token + oauth_consumer_key(APP ID) + openid
                                      ret != 0 -> 拒绝（token 不属于本应用/已过期）
                               |
                               +--> sys_user 按 qq_open_id 查找/建号
                               +--> 签发平台不透明 Session Token
```

本次新增应用归属校验，是为了解决仅调用 `/oauth2.0/me` 时不比对应用信息的问题。QQ 官方文档的
`/me` 示例包含 `client_id`，正常情况下一次请求即可完成 openid 与应用归属校验；移动端响应若未带
`client_id`，再用 `get_user_info` 的 APP ID 三元组兜底。如果 QQ 返回 `ret=100014`（token 过期）
或其他非 0 值，统一返回 `AUTH_401_03`。

## 5. 验证步骤（拿到 APP ID/KEY 后）

1. `.env` 设置 `QQ_MODE=real`、`QQ_CLIENT_ID`、`QQ_CLIENT_SECRET`，重启后端。
2. 管理端“系统配置 → 集成状态”应显示 QQ `CONFIGURED`（real）。
3. 用**正式签名包**在真机走 QQ 授权，拿到 SDK 的 accessToken。
4. 调接口：
   ```bash
   curl -X POST http://localhost:9000/api/common/auth/qq-login \
     -H 'Content-Type: application/json' \
     -d '{"accessToken":"<QQ_SDK_ACCESS_TOKEN>"}'
   ```
   首次应返回 `isNewUser=true`、平台 accessToken；再次登录同一 QQ 应返回同一 `userId`、`isNewUser=false`。
   2026-10-20 已用 release 签名包 + 真实 QQ 账号在模拟器完成这一步：首次登录服务端日志出现
   `首次登录自动创建用户, provider=QQ`，第二次授权后仅出现 `用户登录成功` 且 `userId` 相同。
5. 负向验证：
   - 缺少/伪造 accessToken → 401 `AUTH_401_03`；
   - 用其他 QQ 应用的 token → `get_user_info` 非 0，401；
   - 未配置 APP ID 的 real 模式 → 503 `AUTH_503_02`。
6. 不要打印完整 accessToken 或 openid；排障只看错误码和 QQ `ret`。

## 6. 尚未实现：网站/H5 授权码流程

当前接口是**移动 SDK accessToken 模式**。如果客户端是网站/H5，QQ 登录需要：

1. 前端跳转 `https://graph.qq.com/oauth2.0/authorize?...&redirect_uri=...&state=...`；
2. 服务端接收回调 code，用 APP ID + APP KEY + redirect_uri 调 `/oauth2.0/token` 换 accessToken；
3. 再走本文的 me/get_user_info 校验；
4. 增加 state 生成/校验、回调白名单和前端登录按钮。

这部分需要新增接口与页面，当前未做；若你的“QQ 登录”是网站应用，请明确，我再按网站授权码流程补齐。

## 7. App 端接入现状（2026-10-20）

Android 客户端已接入，不再是 mock：

- 依赖：`app/pubspec.yaml` 增加 `tencent_qq: ^1.0.0`，并在同文件顶层配置
  `tencent_qq.app_id: "1905799882"`（APP ID 是公开参数；App Key 只在服务端）。
- 授权封装：`app/lib/services/qq_auth_service.dart`
  - 用户同意隐私政策后才调用 `setIsPermissionGranted(true)`，再 `registerApp`；
  - 监听 `respStream()`，把 `TencentLoginResp.accessToken/openid` 交给登录页；
  - 未安装 QQ 时退化为 SDK 二维码/网页授权（`qrcode: true`）；
  - 取消、超时、无插件、平台异常都有明确提示。
- 登录页：`app/lib/pages/login/login_page.dart` 的 QQ 一键登录已改为真实 SDK 调用，
  不再发送 `mock_qq_access_token_...`；服务端 real 模式会拒绝伪造 token。
- Android 签名：`app/android/app/build.gradle.kts` 从 `key.properties` 读取 release keystore；
  release 包签名已由 `apksigner` 核对为 `23281bddff43e85adbc905455ec8e9e2`。
- 构建验证：
  - `flutter test`：96 个测试全部通过；
  - `flutter build apk --debug` 成功；
  - `flutter build apk --release` 成功（R8 保留 QQ SDK，OkHttp 3.x 已补依赖）；
  - 模拟器实测（release 包）：点击 QQ 一键登录能调起 QQ 授权页；取消回调被正确接收；
    **同意授权后服务端建号成功并进入首页，二次授权复用同一用户**（详见 §7.2）。

### 7.1 这个插件当前的 Android 构建兼容问题

`tencent_qq` 1.0.0 的 Android 代码按旧版 SDK 编译，直接构建会遇到：

1. QQ SDK 含 `android.support.v4.app.Fragment` 的 login 重载，而本项目使用 AndroidX，缺该类导致
   `TencentQQPlugin.java:186` 编译失败；
2. 插件的 `compileSdkVersion 31` 与当前 Flutter embedding 的 AndroidX 依赖 AAR metadata 不兼容；
3. QQ SDK 3.5.19 内部引用 OkHttp 3.x，release R8 需要显式补 `okhttp` 依赖。

当前的处理（均在 `app/android/build.gradle.kts` / `app/android/app/build.gradle.kts`）：

- 用 `app/android/support-v4-stub.jar` 提供**仅编译期**的 `android.support.v4.app.Fragment` 空类，
  不打包进 APK；SDK 实际走 `Activity` 登录重载，不依赖 Fragment；
- 在 Gradle 配置阶段把 `tencent_qq` 的 `compileSdkVersion` 提升到 36；
- 在 app 依赖中加入 `com.squareup.okhttp3:okhttp:3.12.13`。

这是插件 1.0.0 的临时兼容层，不是业务代码。升级 `tencent_qq` 或 Flutter/AGP 后应先尝试删除这三处
兼容配置，若上游已修复则以插件自身配置为准。若不想保留本地 stub，也可以给插件提交
`android.app.Activity` 登录重载的反射补丁或换用维护中的 SDK 封装。

### 7.2 成功路径验证记录（2026-10-20）

使用 **release 签名 APK**（`app-release.apk`，MD5 `23281bddff43e85adbc905455ec8e9e2`）在模拟器
+ 已登录 QQ 的账号上完成：

1. 登录页点击 QQ 一键登录，SDK 拉起 QQ 授权页，标题显示 `绵城一码通 申请使用`；
2. 点击“同意”后，SDK 回传 accessToken/openid，App 调用 `POST /api/common/auth/qq-login`；
3. 服务端 real 模式反查 QQ openid 成功，日志：
   `首次登录自动创建用户, userId=..., provider=QQ` → `用户登录成功, roles=[REQUESTER]`；
4. App 进入首页，显示新账号的默认昵称“QQ用户”，并出现“教务待办·待认证”引导；
5. 退出后再次 QQ 授权，服务端仅记录 `用户登录成功`，`userId` 与首次一致，没有重复建号。

这证明 APP ID、App Key、包名、release 签名 MD5、SDK scheme/Manifest、服务端反查链路全部一致。
仍未做的是：QQ 互联应用审核通过、正式发布包上架和审核材料提交；这些不阻塞代码联调。

## 8. 参考

- [QQ 互联 OpenAPI 调用说明](https://wiki.connect.qq.com/OpenAPI%E8%B0%83%E7%94%A8%E8%AF%B4%E6%98%8E_OAuth2.0)
- [QQ 互联应用审核规范](https://wiki.connect.qq.com/%E7%BD%91%E7%AB%99%E5%AE%A1%E6%A0%B8%E8%A7%84%E8%8C%83)
- 项目内接口文档：[API 接口参考](../规范/API接口参考.md)
