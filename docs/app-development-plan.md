# App 端开发清单（Flutter + QQ OAuth）

> 制定于 2026-10-16。已定决策：App 复用 `SmartCampus/app` 工程位置、连 student-hub 后端（9000，通过 `--dart-define=API_BASE` 传入，不改默认值）；登录组合为 QQ 主登录 + 学号密码兜底（密码登录后端有 dev 开关，天然契合）。
> 页面清单事实基准：`docs/mobile-pages-and-features.md`（22 页，与小程序同后端同数据）。
> 现状核对结论：App 现有 10 个接口调用中 6 个在 student-hub 不存在（`/api/app/auth/register`、`/api/app/profile/nickname`、`/api/app/services/orders*`、`/urge` 等），且 `register` 与 student-hub「第三方登录自动建号」的架构矛盾——这正是 P2 全部删除重建的原因。

---

## P1 QQ OAuth 后端接入（student-hub）

> 目标：mock provider 本地全流程可验收（建号、发 token），不依赖 QQ 互联审核；审核下来后仅切配置。

- [x] **P1.1 DB 迁移 V39**：`sys_user` 加 `qq_open_id VARCHAR(128)` + `qq_union_id VARCHAR(128)`，`qq_open_id` 加唯一索引（NULL 允许重复，与 `uk_sys_user_wechat_open_id` 一致，"只有 QQ 没有微信"的用户可共存）。unionid 列现在建成本为零——QQ 互联移动应用与网站应用 openid 不同，没有 unionid 以后加网站登录会账号分裂。
- [x] **P1.2 `integration/qq` 包**：照抄 `integration/wechat` 结构——`QqIdentity`（openId + unionId 可空）、`QqIdentityProvider` 接口、`MockQqIdentityProvider`（默认）、`QqApiIdentityProvider`（`@ConditionalOnProperty(prefix="studenthub.qq", name="mode", havingValue="real")`）、`QqProperties`（clientId/clientSecret/defaultCampusId，环境变量注入）。
- [x] **P1.3 real provider 安全要点**：**绝不能信客户端传的 openId**（可伪造）。凭客户端传来的 accessToken 反查 `GET https://graph.qq.com/oauth2.0/me?access_token=&unionid=1`，反查结果与提交值一致才认。解析坑：`/me` 返回 JSONP `callback( {...} );` 需剥壳；`/oauth2.0/token` 默认返回 query string 非 JSON（App 端 SDK 直拿 token 不碰它，留注释给未来网站登录用）。
- [x] **P1.4 服务层**：`IdentityAuthService.qqLogin(String accessToken, String openId)`；把现有 `loginOrCreateByOpenId` 抽成 `findByProviderOpenId(provider, openId)` 复用；QQ 新建号 username 前缀 `qq_`，其余（BASIC 授权态、REQUESTER 默认角色、`isNewUser`）全复用。
- [x] **P1.5 控制器**：`POST /api/common/auth/qq-login`，body `{ accessToken, openId }`（openId 仅做一致性比对用），返回复用 `LoginResponse`。
- [x] **P1.6 配套**：`SecurityConfig` 白名单加 `/api/common/auth/qq-login`；`ErrorCode` 加 `QQ_LOGIN_FAILED` / `QQ_NOT_CONFIGURED`。
- [x] **P1.7 验收**：`.env` 不配 QQ mode → mock 生效；curl 两次调用第二次 `isNewUser=false`、token 可访问 `/api/common/auth/me`。
  - ⚠️ P1 产物（`integration/qq/`、V39、SecurityConfig/ErrorCode 等）截至 2026-10-17 仍未提交 git——P2.1 第②步落袋。

## P2 App 工程删除重建（SmartCampus/app）

> 用户决策：现有 App 项目全部删除。删除前按项目惯例先 git 落袋，防误删不可回退。

- [ ] **P2.1 落袋（两侧）**：①SmartCampus 仓库当前未提交改动先提交，确认 `app/` 已在 git 历史里；②student-hub 侧 P1 产物分批提交（`integration/qq/`、`V39__add_qq_identity_fields.sql`、SecurityConfig/ErrorCode/IdentityAuth 等改动现全为 untracked/未提交混在工作区）——此步同时关闭重构计划刀 4 的窗口期前提（先落袋再动刀 4）。
- [ ] **P2.2 删除 `SmartCampus/app/`**，保留参考物：`README.md`（玻璃导航配方 v2 记录）、`docs/`。
- [ ] **P2.3 重建**：`flutter create --platforms android studenthub_app`（仅 Android）。⚠️ 默认产出 `com.example.studenthub_app`，`--org` 也只能钉前两段（如 `com.studenthub` → `com.studenthub.studenthub_app`）——最终 applicationId 须手改三处：`build.gradle` 的 `applicationId` 与 `namespace`、Kotlin 包路径（`MainActivity` 所在目录）。**applicationId 目标值见 P6.1（`com.studenthub.app` 或核实后的包名，与平台信息核对一致）**。
- [ ] **P2.4 基础件移植**（从 git 历史抄，不重写）：`theme/glass.dart` 液态玻璃配方、`widgets/glass_nav.dart` 底部玻璃导航、`widgets/frosted_panel.dart`、API 客户端骨架（token 自动带 Bearer + 401 处理，但**接口路径按 student-hub 重写**）。
- [ ] **P2.5 依赖收敛**：`http` + `shared_preferences` + `flutter_animate` 起步，不预装未用到的包。
- [ ] **P2.6 验收**：模拟器（emulator-5554）`--dart-define=API_BASE=http://10.0.2.2:9000` 空壳跑通。

## P3 App 端 QQ 登录 + 登录页

- [ ] **P3.1 SDK 选型实测**（2026-10-17 pub.dev 实测）：主选 **`tencent_qq` v1.0.0**（2 个月前发布，即 `tencent_kit` 1.0.0 起的改名延续线，Android/iOS/HarmonyOS 登录全支持；注意 `tencent_q` 这个名字不存在，勿搜错）；`tencent_kit` v6.2.0（16 个月前）为同名旧线，兜底意义有限，真兜底考虑 `flutter_qq_login`（3 年，仅作记录）。先 `tencent_qq` 过编译 + 模拟器授权链路。
- [ ] **P3.2 Android 侧**：`AndroidManifest` 加 `tencent<APPID>` scheme intent-filter；QQ 互联「移动应用」申请需要 Android 包名 + **签名 MD5**（`keytool -list -v` 取）。坑：审核用 release 签名，模拟器跑 debug 签名，两套 MD5 在 QQ 互联是分开的——申请时用 release，本地联调先用 mock。
- [ ] **P3.3 登录页**：QQ 一键登录为主入口；学号/密码为次入口（走 `POST /api/common/auth/login`，dev 开关）；沿用旧版两态恒定卡片 + 玻璃 UI。注册流程不要了（QQ 首登自动建号，`isNewUser` 后进昵称引导）。
- [ ] **P3.4 会话层**：token 存 `shared_preferences`，启动恢复会话，401 清会话回登录页。

## P4 核心页面 MVP（对应 22 页清单的主流程）

> 数据全来自 student-hub，接口路径以 `docs/mobile-pages-and-features.md` 附表为准。**注意 student-hub 用 `service-requests` 不是 `orders`。**

- [ ] **P4.1 首页**：问候头 + 课表卡 + 进行中申请 + 服务宫格（目录接口下发）+ 社区入口。
- [ ] **P4.2 服务大厅**：分组列表（动态目录）；详情页 + 动态表单申请页（`form_schema` 6 种字段类型）。
- [ ] **P4.3 申请流**：我的申请列表（状态筛选）、申请详情（6 态时间轴、撤销、工单协商确认/驳回）、办结后评价页。
- [ ] **P4.4 通知 + 个人中心**：通知列表/全部已读；我的 tab（统计三格、教务绑定入口、资料）。
- [ ] **P4.5 MVP 验收**：走通「QQ 登录 → 选事项 → 动态表单提交 → 详情看进度 → 办结评价」全链路。

## P5 全量页面

- [ ] **P5.1 课表**：周视图网格、单双周/周次范围视觉区分、并格、学期切换（依赖教务认证）。
- [ ] **P5.2 教务认证**：三态页（未绑/已认证/已失效）、验证码、代查授权说明。
- [ ] **P5.3 社区**：帖子流、详情评论、发帖（图 9 张）、作者主页、私信收件箱 + 会话。
- [ ] **P5.4 AI 助手**：SSE 流式会话、Markdown 渲染、语音/图片提问。
- [ ] **P5.5 状态补齐**：每页的加载/空态/失败重试/未登录态，沿用现有 4 张空态插画风格。

## P6 QQ 互联提审与发布准备

> 代码不被审核阻塞（P1 起全用 mock），但发布前必须完成。
> 官方流程核实（2026-10 wiki）：创建移动应用立即得 APP ID/Key，不用等审核；包名/签名属「平台信息」，创建后可补改，但 2019-05-30 起强制校验——不完善则无法调起登录/分享；审核未通过期间可用「调试者 QQ 号」（须开发者好友）真机联调。

- [ ] **P6.1 QQ 互联开发者实名** → 创建「移动应用」→ 填名称/图标/简介 → 立即记录 APP ID/Key。⚠️ **包名待核实（2026-10-17 评审发现冲突）**：本清单钉 `com.studenthub.app`，但微信开放平台表单 2026-10-17 交底填的是 `com.smartcampus.app`——执行前先登录微信开放平台/QQ 互联核实现值，二选一后同步改 P2.3 与平台信息，核实前勿当「三处一致」处理。
- [ ] **P6.2 签名（已完成，仅余收尾）**：release keystore 已生成（`student-hub/artifacts/keystore/smartcampus-release.jks`，密码在 macOS Keychain），签名 MD5 **`23:28:1B:DD:FF:43:E8:5A:DB:C9:05:45:5E:C8:E9:E2` 已定为平台「应用签名」栏终值——严禁重新生成 keystore（换签名 = 平台信息作废）**。余项：管理中心添加自己 QQ 为调试者（须开发者好友）。注意：平台信息填 release 签名后，debug 签名跑模拟器过不了 QQ 校验，真授权链路联调须用 release 签名构建。
- [ ] **P6.3 real 模式联调**：`QQ_MODE=real` + APP ID/Secret 入 env；release 签名构建验证反查链路（access_token 60 天有效，过期码 100014）。
- [ ] **P6.4 提交审核**（约 5 个工作日）+ **发布件**：混淆配置确认 QQ SDK keep 规则（`-keep class * extends android.app.Dialog`）。

## P7 鸿蒙端（可选，MVP 后评估）

> 定性：高成本另建，非移植。Flutter 官方无 ohos 目标，Dart 业务层可复用但平台壳要重建。

- [ ] 路线决策：华为 OpenHarmony 适配版 SDK（版本落后官方稳定版，但登录 SDK 桥接**无需自建**——`tencent_qq`/`tencent_kit` 均原生支持 HarmonyOS，2026-10-17 实测）vs ArkTS 原生重写（第三个客户端，约 1.5× 工作量；SDK 原生支持鸿蒙后，适配版可行性上升，此估可下调）。若鸿蒙是硬需求，ArkTS 并行版 + 共享后端通常比适配版省事。
- [ ] 微信开放平台鸿蒙页签需 Bundle ID / appIdentifier（来自华为 AGC）；QQ 互联对鸿蒙包的支持需届时核实。

## P8 iOS 端（低成本扩展，代码已就绪）

> 定性：Flutter 官方原生支持 iOS，同一份 Dart 代码零改动能打 iOS 包。缺口全在工具链与账号。

- [ ] 工具链：App Store 装完整 Xcode（现有 flutter doctor 报 incomplete）→ `sudo xcode-select --switch` + `runFirstLaunch` → `brew install cocoapods` → `flutter create --platforms android,ios` 补 iOS 壳。
- [ ] 账号：苹果开发者 $99/年（真机调试 + 上架前提；模拟器免费随便跑）。
- [ ] **登录政策红线**：iOS 接了 QQ/微信登录后，苹果强制要求同时提供「通过 Apple 登录」——P3 登录页设计时预留该按钮位，后端 P1 的 `loginOrCreateByIdentity` 加 `APPLE` provider 即可（无需动会话层）。
- [ ] QQ/微信 SDK 的 iOS 侧配置：URL Scheme / Universal Link。**Universal Links 已定（2026-10-16）：`https://mycc.sailtrack.cn/app/`**（微信开放平台 iPhone 页签已填）；届时需在该域名 `/.well-known/apple-app-site-association` 部署苹果 AASA 校验文件，微信 SDK 校验该链接与应用配置匹配后才放行授权。

---

## 依赖与顺序

```
P1（后端 QQ，mock 可先行）──┐
P2（删重建 + 基础件移植）  ─┴→ P3（App QQ 登录）→ P4（MVP 页面）→ P5（全量）→ P6（提审发布）→ {P7 鸿蒙(可选) / P8 iOS(低成本)}
```

P1 与 P2 无依赖可并行；P3 起 App 依赖 P1 的 `/api/common/auth/qq-login`。
