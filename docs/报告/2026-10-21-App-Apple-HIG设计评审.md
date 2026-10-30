# App 端 Apple HIG 设计评审与优化清单（2026-10-21）

> **评审对象**：`app/`（Flutter，当前仅 Android + Web 目标）  
> **评审基准**：Apple Human Interface Guidelines（Layout / Tab bars / Accessibility / Typography / Color）+ App Review Guidelines 4.8、5.1.1 + 项目《移动端设计规范》v2.2/v2.13  
> **取证范围**：2026-10-18～10-20 的验收截图（`artifacts/app-shots*`）+ 当前源码静态盘点；本轮未重新打包 Flutter，未重跑 Flutter 测试（本机 Flutter 缓存写权限受限）。设计结论可直接用于排期，发布前仍需真机复验。  
> **结论先行**：App 的视觉完成度和业务覆盖面已经达到可评审/可试用水平，**主要问题不再是“配色好不好看”，而是三件事：合规门禁、系统级适配、可访问性**。当前版本可以继续做 Android 优化；若要上 iOS，登录合规和隐私政策是独立上架门禁。

---

## 一、先对齐 Apple 的基准

Apple HIG 是平台设计指导，不是法律；真正决定 iOS 能否上架的是 App Review Guidelines。两者要分开看。

| 基准 | 关键要求 | 对当前 App 的含义 |
|---|---|---|
| [Layout](https://developer.apple.com/design/human-interface-guidelines/layout) | 按重要性排序内容；用对齐/分组/渐进披露建立层级；尊重 safe area；适配不同屏幕、方向、窗口和**文字大小变化** | 首页首屏层级、登录页固定坐标、课表 7 列、底栏安全区都要按“可适配”重审 |
| [Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars) | 用于导航而非动作；标签短；图标 + 文字；选中态清楚；控制在 5 个以内；保留各 tab 导航状态 | 5 个 tab 的数量是对的；但标签 10pt、选中态没有无障碍语义、底栏未吃系统 safe area |
| [Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility) | 支持放大文字（理想 200%）；iOS 默认字号 17pt、最小 11pt；控件默认 44×44pt、最小 28×28pt；对比度按 WCAG AA；不能只靠颜色传达信息 | 当前有 110 处字号 <11pt、`#94A3B8` 文本对比度 2.56:1、登录协议行点击高度仅 24pt |
| [Typography](https://developer.apple.com/design/human-interface-guidelines/typography) | 使用可读字号和系统字体；避免裁切/重叠；适配 Dynamic Type；保持清晰层级 | 735 处显式 `fontSize`，且大量固定高度容器；需要按大字体重排而不是只换字号 |
| [Color](https://developer.apple.com/design/human-interface-guidelines/color) | 用语义色；检查浅色/深色/增强对比度；不要只靠颜色传达状态 | 目前只有浅色主题，无 `darkTheme`；弱化灰大量用于小字，对比度不足 |
| [App Review 5.1.1](https://developer.apple.com/app-store/review/guidelines/) | App 内必须有**易于访问**的隐私政策链接；收集数据前取得同意；提供撤回同意/删除方式 | 登录页协议文字不可单独点击，仓库中没有隐私政策页面或入口 |
| [App Review 4.8](https://developer.apple.com/app-store/review/guidelines/) | 用第三方/社交登录建立主账号时，通常必须提供等价登录选项（如 Sign in with Apple）；“要求使用既有教育/企业账号”属于例外 | 当前 QQ 是主登录，且没有“学校统一身份登录”作为既有教育账号入口；上 iOS 前必须处理 |

> 重要限定：当前工程没有 `ios/` 目录（只有 Android/Web）。**4.8 和 5.1.1 不是当前 Android 包会被 App Store 拒绝的理由，而是未来 iOS 发布门禁**。如果近期不做 iOS，可以先按 HIG 做设计优化、把隐私链接补齐；如果计划上 iOS，则这两项必须进排期。

---

## 二、当前 App 的实际效果

### 做得好的地方

- 视觉语言统一：品牌蓝 `#2563EB` + 青绿 `#10B981`，卡片圆角、浅灰底、状态胶囊整体一致，比早期评审时的“拼页面”状态成熟很多。
- 信息层级基本成立：首页“问候 → 通知 → 课表 → 服务 → 社区”、服务大厅“搜索 → 广告 → 快捷办事 → 分类列表”、工单详情“状态 → 师傅 → 时间轴 → 吸底操作”都能一眼看懂。
- 核心流程完整：QQ 登录、首页、服务大厅、动态表单、我的申请、工单时间轴、成绩、消息中心、社区帖子、AI 助手均有可用页面，不是空壳。
- 广告有明确“广告/推广”标识，曝光/点击链路也已经存在；这是商业化合规的基础。
- 消息中心的快捷入口（图标 + 短标签、44pt 圆形底、未读红点、搜索框）已经比旧版筛选 chip 更接近 HIG 的导航模式；当前源码保留流程/互动/公告 3 个入口，v13 截图中还出现过 AI 待办入口，需确认最终产品口径。
- 表单、开关、吸底主按钮等控件尺寸总体不差，问题主要集中在“小字/弱灰/固定布局”三类系统性细节。

### 关键截图

| 页面 | 截图 | 观察 |
|---|---|---|
| 首页 | [newhome2.png](../../artifacts/app-shots/newhome2.png) | 首屏被广播、广告、课表占据较多；服务宫格标签出现截断 |
| 课表 | [01-schedule-main.png](../../artifacts/app-shots-v14/01-schedule-main.png) | 7 天 × 10 节全部塞进 375pt，课程名换行、地点被省略 |
| 登录 | [33-login.png](../../artifacts/app-shots/33-login.png) | 协议默认勾选；协议文字不可点；整体按 375×812 固定坐标绘制 |
| 服务大厅 | [service-hall-new.png](../../artifacts/app-shots/service-hall-new.png) | 广告卡位于快捷办事之前；核心服务被向下推 |
| 帖子详情 | [04-post-detail.png](../../artifacts/app-shots/04-post-detail.png) | 图片加载失败/无图时出现大面积灰块 + 说明文字 |
| 消息中心 | [03-message-center-after.png](../../artifacts/app-shots-v13/03-message-center-after.png) | 快捷入口结构好；时间/辅助文字对比度偏低 |
| 个人中心 | [07-profile-final.png](../../artifacts/app-shots-v15/07-profile-final.png) | 卡片层级清楚；底部菜单在视口边缘被裁，需要滚动 |
| 成绩 | [10-grades.png](../../artifacts/app-shots/10-grades.png) | 数据卡片清楚；`挂科科目 0门通过` 文案自相矛盾 |

---

## 三、问题清单（按优先级）

### P0：发布/合规门禁

#### P0-1 登录协议默认勾选，且协议链接不可点击、无隐私政策页面

**证据**

- `app/lib/pages/login/login_page.dart:28`：`bool _agreed = true; // 协议勾选状态，对齐画布默认勾选`。
- 同文件协议行：16×16 复选框，整行 `GestureDetector` 点击切换勾选；《用户协议》《隐私政策》只是 `Text`，没有单独的点击识别器。
- 仓库内检索不到隐私政策页面/路由；`用户协议`/`隐私政策` 只出现在登录页和错误提示中。

**为什么是 P0**

- App Review 5.1.1(i)：App 内必须提供易于访问的隐私政策链接；5.1.1(ii)：收集数据前必须取得用户同意，并允许撤回。
- 默认勾选会让“同意”不成立；用户也无法在登录页打开协议正文，等于没有“可访问的链接”。

**建议**

1. 默认不勾选；未勾选时点击登录，显示明确提示并高亮协议行，而不是只弹 Toast。
2. 两个协议名分别可点，打开应用内 WebView/页面；隐私政策页必须包含收集字段、用途、第三方 SDK（QQ/广告/推送）、保留期限、撤回与删除入口。
3. 首次启动/首次登录记录同意版本、时间、渠道（Android/iOS），支持后续撤回；不要只在前端做勾选。
4. 隐私政策页入口同时放到“我的 → 设置/关于”中，保证随时可达。

#### P0-2 iOS 上架前的登录合规（当前无 iOS 工程，属于条件性门禁）

**证据**

- `app/` 只有 Android/Web 目标，没有 `ios/`；登录页只有 QQ 一键登录 + 隐藏的 dev 学号密码兜底。
- 项目计划 P8 已明确写明“iOS 接 QQ/微信后苹果强制要求 Sign in with Apple”，但当前代码还没有 Apple 登录入口。

**判断**

- QQ 是当前主账号登录方式；如果未来 iOS 版仍以 QQ 作为主登录、且不要求“既有学校教育账号”，App Review 4.8 默认要求提供等价的低数据收集登录方式（通常就是 Sign in with Apple）。
- 如果学校统一身份认证成为主入口、且明确要求既有教育账号，才可能落入 4.8 的教育例外；不要把例外当成默认。

**建议**

- 上 iOS 前：增加“通过 Apple 登录”，后端 `APPLE` provider 复用现有 `loginOrCreateByIdentity`；准备 Apple 开发者账号、AASA/Universal Link、隐私清单。
- 如果短期不做 iOS：把这项写进 iOS 发布清单，不要用“Flutter 一套代码可以直接上架”误导排期。

#### P0-3 Release 启动路径仍会自动 dev 登录/注入 mock 会话

**证据**

- `app/lib/main.dart:57-72`：没有本地会话时先调 `authService.devWechatLogin()`；失败后直接注入 `dev_mock_token_student01` 的“李同学”会话。
- `app/lib/main.dart:103-117`：`HomePage` 在没有 session 时还有第二层 mock 兜底。
- 截图中首页出现“示例大学”“调试同学”，说明当前验收环境仍是演示数据。

**影响**

- 新安装用户可能绕过登录直接进入首页，看到伪造的已登录身份；线上后端一旦关闭 dev 登录，mock token 的请求会 401，形成“看似登录、实际全失败”的状态。
- 这既是安全问题，也是 App Review 2.1（App Completeness）风险：审核员看到 demo 数据/假账号会质疑功能真实性。

**建议**

- 将 dev 登录和 mock session 收进 `kDebugMode` 或显式 `--dart-define=ENABLE_DEV_LOGIN=true`；release 构建没有会话时进入登录页，不渲染首页。
- 删除 `HomePage` 内部的 mock 兜底，session 作为必需参数；401 统一清会话回登录。
- 上线前清理“示例大学/调试同学/李同学”等种子数据，或明确标记为演示环境。

### P1：HIG 系统级适配与可访问性

#### P1-1 底栏没有使用系统 safe area，iOS 上会被 Home Indicator 挤压

**证据**

- `app/lib/pages/home/home_page.dart:2467-2469`：底栏固定 `height: 84`、`padding: EdgeInsets.only(..., bottom: 20)`。
- 项目《移动端设计规范》明确写的是 iOS 34pt 底部安全区；当前 20pt 不够。
- 自定义 `_buildBottomTabBar` 没有读取 `MediaQuery.padding.bottom`；同样模式在其他页面也存在。

**建议**

- 底栏高度改为 `64 + MediaQuery.padding.bottom`（或 `SafeArea(top: false)` + 固定内容高度），不要写死 84。
- 所有吸底操作条（帖子详情评论栏、工单详情按钮、AI 输入栏）统一走一个 `BottomActionBar` 组件，内部处理 safe area + 键盘 inset。
- 真机至少验 iPhone 15/SE、Android 手势导航/三键导航四种组合。

#### P1-2 小字号过多，且没有按 Dynamic Type 做布局重排

**证据**

- 全仓 735 处显式 `fontSize`；其中 **110 处 <11pt**（8pt 4 处、9pt 22 处、10pt 80 处、10.5pt 3 处），另有 135 处正好 11pt。
- 底部 tab 标签 10pt、未读徽标 8–9pt、课表日期/节次 10–11pt、多处辅助信息 10–11pt。
- HIG：iOS 默认 17pt、最小 11pt；支持 Dynamic Type 时布局必须能容纳放大后的文字。
- 项目没有 `textScaler` 相关适配逻辑；大量固定高度（课表 56、底栏 84、卡片 180 等）意味着系统字号放大后容易裁切/重叠。

**建议**

- 建立最小字号阶梯：辅助信息 11pt、正文 14–17pt、标题 17–22pt；8/9/10pt 只允许用于非关键装饰（且不建议）。
- 用 `TextTheme`/设计 token 替代逐页 `TextStyle(fontSize: ...)`；先修底栏、课表、消息时间、订单状态这些高频区域。
- 在 iPhone 设置里把文字调到 200%，逐页走查：底栏是否溢出、课表卡片是否还能读、按钮文字是否被裁；必要时用 `LayoutBuilder`/`Flexible`/可增长高度替代固定高度。
- 不要简单 `TextScaler.noScaling` 关掉放大，这会直接违反 HIG 的可用性目标。

#### P1-3 弱化灰对比度不达标，且被大量用于小字

**证据**

- `AppColors.textMuted = #94A3B8`；按 WCAG AA 公式计算：白底 **2.56:1**、`#F8FAFC` 底 **2.45:1**。
- HIG 引用 WCAG AA：18pt 以下普通文字要求 **4.5:1**；当前大量 10–12pt 的辅助文字使用该颜色，明显不达标。
- `#FFFFFF` 在 `#10B981` 上只有 **2.54:1**；AI tab 的白色图标、绿色按钮上的白字存在非文本对比度风险（非文本一般要求 3:1）。
- 全仓 `#94A3B8` / `textMuted` 相关引用约 **175 处**。

**建议**

- 把 `textMuted` 至少加深到 `#64748B`（白底 4.76:1，刚过 AA）或 `#475569`（7.58:1）；小字场景直接用 `#475569`。
- 重新定义 `accent` 的“可承载白字”深色版本（例如 `#047857` 一类），或把绿底图标改为深色图标；不要用 `#10B981` 直接配白字。
- 用对比度检查工具逐页扫：登录说明、课表日期、消息时间、成绩“正常通过”、个人中心 UID、表单 helper text。
- 在“增强对比度”/深色模式下提供更高对比变体；HIG 明确要求检查浅色/深色两套。

#### P1-4 触控目标与无障碍语义不足

**证据**

- 登录协议复选框 16×16，整行高度约 24pt；小于 HIG 最小 28×28pt，更远低于默认 44×44pt。
- AI 助手、语音输入等页面存在 32/34/36/38pt 的圆形按钮；视觉上精致，但点击热区不足 44pt。
- 全仓仅 1 个 `Semantics(...)`，却有 114 个 `GestureDetector`、0 个 `Tooltip`；自定义卡片、底栏、服务宫格大多没有 button/selected 语义。
- 底栏选中态只靠颜色 + 图标变化，读屏不会播报“当前选中”。

**建议**

- 所有可点元素保证至少 44×44pt 热区（视觉可以 32，外面包 padding/`SizedBox`）；登录协议行高度至少 44。
- 给底栏加 `Semantics(button: true, selected: active, label: label)`；给服务宫格、帖子卡片、消息条目加 `button`/`label`；给图片加语义标签或 `excludeSemantics`。
- 用 TalkBack（Android）和 VoiceOver（iOS）各走一遍登录 → 首页 → 服务 → 工单 → 消息；检查焦点顺序、重复朗读、无标签按钮。
- 不仅靠颜色区分状态：选中 tab 同时用填充图标/字重/下划线或指示条；错误状态同时给图标 + 文案。

#### P1-5 课表 7 列硬塞，文字裁切、地点省略、空网格浪费

**证据**

- `app/lib/pages/schedule/schedule_page.dart:910`：`colWidthRatio = 1.0 / 7.0`；左侧节次栏 36pt。
- 375pt 宽下每天约 `(375 - 36) / 7 ≈ 48.4pt`；课程卡 `maxLines: 2` + `TextOverflow.ellipsis`，地点 `maxLines: 1`，截图出现 `教1-1...`。
- 项目《移动端设计规范》写的是“周一至周五 5 工作日网格，每列 ≥66pt；周末课程通过切换日视图”；实现和规范不一致。
- 截图中大量空白网格 + 拥挤文字同时存在，信息密度和可读性都不理想。

**建议**

- 默认周视图只展示周一至周五，每列 ≥66pt；周末用“周末/日视图”切换，或提供横向滚动周视图。
- 单日模式展示完整课程名、教师、地点、时间；点击课程卡打开详情，不要在主网格里硬塞全部文本。
- 空节次可压缩（例如 1–2 节、3–4 节合并视觉行），把纵向空间让给有课区间；至少不要让 10 节等高空网格占满首屏。
- 课程卡地点用两行或可展开；重要信息不依赖省略号。

#### P1-6 登录页按 375×812 固定坐标绘制，屏幕一变就偏/裁

**证据**

- `app/lib/pages/login/login_page.dart` 有 14 个 `Positioned`；校徽 `left: 131, top: 92`，表单/按钮 `width: 327`，大量绝对坐标来自画布。
- 在 393/430pt 宽设备上，固定 left 会导致内容不居中；在 320pt 宽设备上 327pt 宽度直接溢出。
- 顶部只取了 `topPadding` 变量但未参与主要定位；HIG 要求适配不同屏幕、方向和文字大小。

**建议**

- 改为 `SafeArea` + `LayoutBuilder` + `ConstrainedBox(maxWidth: 327)` + `Center`/`Column`；logo、标题、表单用相对布局，不写死 x/y。
- 用 `SingleChildScrollView` 包住表单区，键盘弹出/大字体时可滚动；底部主按钮用 `bottomNavigationBar` 或 `SafeArea` 吸底。
- 至少在 320/375/393/430 四个宽度和 200% 字体下截图验收。

#### P1-7 首页服务标签截断、图片占位块过大，内容态不够“真”

**证据**

- 首页服务宫格标签出现“教室与公共...”这类省略；HIG 明确要求避免文字裁切/重叠，尤其是导航和操作标签。
- 帖子详情/社区在图片缺失或加载失败时渲染大面积灰块 + “图片说明”文字（`errorBuilder` 返回 180pt 灰块）；用户会以为是坏图，而不是内容态。
- 首页首屏依次是广播、广告、课表、服务，核心服务被推到中段；服务大厅的广告卡也位于快捷办事之前。

**建议**

- 服务名优先用短标签（“教室报修”“公共报修”），或允许两行；不要让用户猜“教室与公共...”是什么。
- 图片组件统一：固定宽高比 + 骨架屏 + 失败重试图标 + 无图时收起图片区，不要用大灰块占位；帖子正文无图时直接展示文字。
- 首页把广告卡下移到服务宫格之后或做成可折叠；服务大厅把“快捷办事”放在广告之前，至少保证核心任务在首屏可点。
- 社区帖子卡、服务卡、消息条目统一 12–16pt 圆角和间距，避免“每个页面一套圆角”。

### P2：设计系统与打磨项

#### P2-1 设计 token 覆盖率低，且没有深色模式

**证据**

- 全仓约 **1498 处 `Color(0x...)` 硬编码**，`AppColors.*` 引用约 325 处；颜色 token 覆盖不足 20%。
- `app/lib/theme/app_theme.dart` 只有 `lightTheme`，没有 `darkTheme`/`themeMode`；`main.dart` 状态栏图标固定 `Brightness.dark`。
- 页面中大量 `fontFamily: 'Noto Sans SC'`，但 `pubspec.yaml` 没有字体资产；主题里又写 `fontFamily: '-apple-system'`，实际依赖系统回退，跨平台字重/字宽不可控。

**建议**

- 先收口 20 个高频语义 token（品牌、成功、警告、危险、正文、次要、弱化、边框、背景、卡片），页面只允许用 token；用脚本统计硬编码颜色，目标降到 <300 处。
- 增加 `darkTheme` + `themeMode: ThemeMode.system`，至少覆盖首页、消息、服务大厅、个人中心；状态栏图标随主题切换。
- 字体二选一：用系统字体（iOS SF、Android Roboto/Noto）并删除无效 `fontFamily`；或真正打包 Noto Sans SC 子集。不要保留“声明了但不存在”的字体族。

#### P2-2 首页/服务大厅的商业内容优先级偏高

**证据**

- 首页首屏：广播 → BANNER 广告 → 课表 → 服务宫格；广告卡高度约 160pt，且有两处“推广”标识。
- 服务大厅：搜索 → 广告卡 → 快捷办事 → 分类列表；用户来办事时先看到广告。

**建议**

- 遵守 HIG“按重要性排序内容”：核心任务（课表、服务、待办）优先，广告放在任务之后或折叠；广告高度不超过一屏的 15%。
- 广告频控/关闭入口要可发现；不要用“推广”重复标签制造视觉噪音。
- 如果广告是收入刚需，至少区分“首页品牌广告”和“服务大厅信息型广告”，后者应更接近服务卡片而不是促销卡。

#### P2-3 文案与数据表达问题

- 成绩页汇总卡写“挂科科目 / 0门通过”，语义矛盾；应改为“挂科科目 / 0 门”或“全部通过”。
- 首页问候出现“调试同学/示例大学”，属于演示数据，发布前必须清理（见 P0-3）。
- “我的申请”状态、时间格式、工单号等在截图中风格不完全统一；建议统一为“相对时间 + 绝对时间”二级展示，避免只有 `2026-10-18T12:07` 这类机器格式。

#### P2-4 页面级细节建议

| 页面 | 建议 |
|---|---|
| 首页 | 服务宫格标签两行/短名；广告下移；通知条只保留一条可展开；骨架屏宽度跟随容器而不是固定 343 |
| 课表 | 5 日默认 + 周末切换；课程详情底部弹层；空节压缩；周次切换放入顶部工具条 |
| 登录 | 协议默认不勾选、链接可点、隐私页可达；Apple 登录位预留 |
| 社区/帖子详情 | 图片 loading/error/无图三态；头像与关注按钮 44pt；评论输入栏 safe area |
| 消息中心 | 快捷入口保持当前 4 个；时间/摘要用更深灰；搜索无结果提供空态而不是空白 |
| 个人中心 | 底部菜单与底栏之间增加 safe area；UID/学校等弱信息提升对比度；勋章标签 11pt 起 |
| 服务大厅 | 广告后置；服务卡点击区 ≥44pt；分类标题与卡片间距统一 12/16 |
| 表单/工单 | 字段错误就近展示；键盘弹出时吸底按钮上移；状态时间轴当前节点用图标 + 文字双重标识 |
| AI 助手 | 建议 chip 可横向滚动且有边缘渐隐；语音/发送按钮 44pt；输入栏处理键盘 + safe area |

---

## 四、建议的优化批次

### 批次 A：合规与安全（先做，0.5–1 天）

1. 登录协议默认不勾选；协议名可点；新增隐私政策页 + “我的”入口；记录同意版本。
2. 移除 release 路径的 dev 登录和 mock session；无会话进登录页。
3. 底栏/吸底栏接 safe area；登录协议行 44pt。
4. 把 `textMuted` 加深，先解决最刺眼的小字对比度。

**验收**：新安装无会话 → 登录页；不勾协议不能登录；能打开两份协议；iPhone 15/SE + Android 手势导航底栏不压 Home Indicator。

### 批次 B：可访问性与适配（1–2 天）

1. 建立字号/颜色/间距 token；替换底栏、课表、消息、成绩四个高频页面。
2. 全量检查 44pt 热区；给底栏/卡片/服务宫格补 Semantics。
3. 登录页从绝对定位改为响应式布局；320/375/393/430 + 200% 字体走查。
4. 对比度扫描；修复所有 <4.5:1 的小字。

**验收**：TalkBack/VoiceOver 能读完首页 → 服务 → 工单；200% 字体下无裁切/重叠；底栏选中态可被读屏播报。

### 批次 C：核心体验重做（3–5 天）

1. 课表 5 日默认 + 周末切换 + 课程详情；地点不再省略。
2. 首页信息层级/广告位置/服务标签优化。
3. 社区图片三态、帖子详情无图态；服务大厅广告后置。
4. 深色模式第一版（首页/消息/服务/我的）。

### 批次 D：iOS 发布前（独立排期）

1. `flutter create --platforms android,ios` 补 iOS 壳；Apple 开发者账号、证书、AASA/Universal Link。
2. Sign in with Apple + 后端 `APPLE` provider + 4.8 等价登录评审。
3. 隐私政策 URL、App Store Connect 隐私问卷、隐私清单、数据删除入口。
4. iOS 真机走查 safe area / Dynamic Type / VoiceOver / 深色模式。

---

## 五、验收清单（可直接贴到测试用例）

- [ ] 320/375/393/430pt 宽度下，首页、登录、课表、服务大厅无横向溢出/裁切。
- [ ] 系统字号 200% 下，底栏标签、课表课程卡、消息时间、工单状态不重叠、不裁切。
- [ ] 登录页默认不勾选；协议链接可点；隐私政策在 App 内可随时打开；撤回同意路径存在。
- [ ] release 包无“示例大学/调试同学/李同学”mock 会话；无会话必须进登录页。
- [ ] 所有可点元素热区 ≥44×44pt；登录协议行 ≥44pt。
- [ ] 底栏/吸底栏在 iPhone 15、iPhone SE、Android 手势导航/三键导航下不被系统 UI 遮挡。
- [ ] 小字对比度全部 ≥4.5:1；绿色/品牌色承载白字时 ≥3:1（非文本）/4.5:1（文本）。
- [ ] 底栏 5 个 tab 选中态同时有颜色 + 图标/字重变化；读屏播报“已选中”。
- [ ] 课表默认 5 日、每列 ≥66pt；课程名/地点完整或可展开。
- [ ] 帖子图片有 loading/失败/无图三态，不出现大面积灰块占位。
- [ ] 若发 iOS：Sign in with Apple 可用；隐私政策链接在 App 内和 App Store Connect 均存在。

---

## 六、取证命令与指标复现

```bash
# 字号分布（当前 735 处；<11pt 110 处）
python3 - <<'PY'
import re,glob,collections
sizes=collections.Counter()
for f in glob.glob('app/lib/**/*.dart',recursive=True):
    s=open(f,encoding='utf-8').read()
    for m in re.finditer(r'fontSize:\s*([0-9.]+)',s):
        sizes[float(m.group(1))]+=1
print(sorted(sizes.items()))
PY

# 硬编码颜色与 token 引用
grep -RIn "Color(0x" app/lib | wc -l     # 约 1498
grep -RIn "AppColors\." app/lib | wc -l  # 约 325

# 无障碍/交互组件
grep -RIn "Semantics(" app/lib | wc -l        # 1
grep -RIn "GestureDetector(" app/lib | wc -l  # 114
grep -RIn "darkTheme\|themeMode" app/lib      # 无
```

对比度按 WCAG 相对亮度公式计算：`#94A3B8` on `#FFFFFF` = 2.56:1，on `#F8FAFC` = 2.45:1；`#FFFFFF` on `#10B981` = 2.54:1。

---

## 七、最终判断

- **现在能不能继续做 App 优化？** 能；而且应该先做，不必等外部 U06/H02/OS 推送集成。
- **最值得先做的不是换配色**，而是：①登录合规 + 移除 mock 启动；②safe area + 响应式；③字号/对比度/热区/语义。这四类做完，App 的“专业度”会有明显提升。
- **距离 iOS 上架还差一个独立批次**：iOS 壳、Sign in with Apple、隐私政策/隐私清单、真机走查；不能把“Flutter 一套代码”当成“iOS 已就绪”。
- 视觉评分（HIG 维度，粗评供排序）：布局适配 5.5/10、排版可读性 5.0/10、色彩对比 5.5/10、可访问性 4.0/10、导航一致性 7.5/10、发布准备 3.5/10。整体不是“设计不好看”，而是“系统级适配还没做完”。
