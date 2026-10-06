# student-hub 战术编程腐化诊断报告

> 诊断日期：2026-10-17
> 范围：`src/main/java/com/studenthub` 全部 15 个模块（364 个 Java 文件），含 git 历史佐证
> 方法：四路并行扫描（功能重复实现 / 接口分裂与条件堆积 / 数据格式混乱 / 会话与安全集成层对比）+ 关键嫌疑点人工复核

## 总体结论

全库基线比预期健康：无 BeanUtils 反射拷贝、主键全 `Long`、时间命名统一 `xxxAt`、当前用户获取统一走 `CurrentUserProvider`（约 17 个 controller 零分叉）、`Result/PageResult` 覆盖 32/35 个 controller、全仓 grep FIXME/TODO/HACK 零命中。

但存在 **6 处结构性腐化**，集中在三条战线：

1. **servicerequest / workorder 双模块缠绕**——同一状态机出现三套写法；
2. **aichat 管理端绕过服务层**——AI 绑定解析出现两套规则；
3. **dashboard / screen 同表双套聚合**——工单状态字符串三处各写一份。

最危险的不是重复本身，而是这三处已具备产生**线上数据不一致**的条件。另有一个时效性机会：`integration/qq/` 目前是 untracked 未提交目录，是最新一起照抄 wechat 骨架的复制事件，提交前合并成本最低。

---

## 高风险（已具备产生数据不一致的条件，立即处理）

### [F-01] 条件堆积 + 接口分裂：申请单状态流转存在三套并行写法

📍 位置：
- `modules/workorder/service/impl/WorkOrderServiceImpl.java:553-579`（第一套：跨模块直改）
- `modules/servicerequest/service/AdminServiceRequestService.java:49-77`（第二套：绕过状态机手写 if/else）
- `modules/servicerequest/statemachine/RequestTransitions.java:13-66`（第三套：状态机本体，本应是唯一入口）

🔍 问题描述：工单模块的 `transitionRequest` 直接注入 `ServiceRequestMapper`、直接改写 `ServiceRequestEntity` 的 status/rejectReason/completedAt/version，并复用对方模块的 `RequestLogRecorder`——两模块已互相缠绕（servicerequest 侧也反向依赖 workorder）。同时 `AdminServiceRequestService.review` 不调用 `RequestTransitions.target()`，自行 `Status.valueOf` + 手写 if/else 决定迁移，等于在状态机之外复制了一份流转规则。review 处直接 `updateById(entity)` 全量更新，与 workorder 侧「new 一个只带变更字段的实体再 updateById」的做法不一致——前者会把并发竞争窗口放大（不带 version 条件覆盖）。

💡 影响评估：状态机存在的意义是「迁移规则单点收敛」，现在管理端可绕开它。任何对 `RequestTransitions` 的修改（加状态、改允许迁移）都要人肉回忆三处；review 路径的并发更新不带乐观锁条件，与 workorder 路径语义不同，可能出现状态被静默覆盖。

🛠️ 重构建议：渐进式三步——
1. 在 `RequestTransitions` 上增加 `validate(Action, Status) → Status` 单一入口（`target()` 已有 90% 逻辑，review 只需改调它）；
2. 把 `WorkOrderServiceImpl.transitionRequest` 上移为 servicerequest 模块内的公开服务（如 `ServiceRequestTransitionsService.apply(requestId, action, operator, reason)`），workorder 只调用不直改；
3. 统一两条路径都走「带 version 的条件更新」（MyBatis-Plus `@Version` 已在实体上，补齐即可）。

⚠️ 风险等级：**高（立即处理）**

---

### [F-02] 功能重复实现：AI 绑定解析两套规则并存，管理端 controller 绕过服务层

📍 位置：
- `modules/aichat/controller/admin/AdminAiStreamController.java:190-213`（`resolveBinding`：直接注入 `AiProviderMapper/AiModelMapper`）
- `modules/aichat/service/impl/AiConfigServiceImpl.java:57-77`（`resolveBinding`：走 default-model 表解析）
- `integration/ai/AiProvider.java:16-31`（`chatStream` 与 `chatStreamWithBinding` 两条 default 方法并存）

🔍 问题描述：同一个「供应商+模型 → `AiModelBinding`」解析，controller 里手写了一套（启用校验、apiKey 校验、streaming 兜底 `getStreaming() == null || getStreaming()`），service 里另一套，streaming 兜底三元表达式在两处逐字重复。`AiModelBinding.java:25-28` 还有标注「兼容旧调用点」的遗留 4 参构造器。controller 直接持有 mapper 已越过模块分层。

💡 影响评估：将来修绑定规则（如增加协议校验、调整 streaming 语义）只改其中一处，管理端测试流与用户端真实聊天行为分叉——这正是「测试通过但线上不对」的典型温床。

🛠️ 重构建议：把 `resolveBinding(Long providerId, String modelId)` 下沉到 `AiConfigService`（新增重载：显式指定型 vs 按类型码解析型），`AdminAiStreamController` 删掉私有方法与 mapper 注入；`chatStreamWithBinding` 确认全部调用点迁移后删除 `chatStream` 旧 default 方法与兼容构造器。

⚠️ 风险等级：**高（立即处理）**

---

### [F-03] 功能重复实现：dashboard 与 screen 对同一批表两套聚合，工单状态文案/集合三处各写一份

📍 位置：
- `modules/dashboard/service/DashboardService.java:39-81,110-125` vs `modules/screen/service/impl/ScreenServiceImpl.java:102-236`
- 状态字符串清单：`DashboardService.java:47` vs `ScreenServiceImpl.java:159-161` vs 文案 Map `AiAssistantDataServiceImpl.java:41-46` vs `ScreenServiceImpl.java:367-382`（switch 8 case）

🔍 问题描述：两套服务互不共享任何聚合代码：近 7 天趋势一处循环 7 次每天 2 条 count SQL、另一处拉全量内存分桶；AI 会话统计口径不同（按 created_at 当日 count vs 全量 ASSISTANT 消息内存过滤）；工单「进行中」状态集合两处手写字符串数组（且内容不一致——screen 侧多两个状态），均未引用 `WorkOrderTransitions.Status` 枚举。DashboardService 还有两处占位硬编码（`todayNewUsers = 0`、把绝对值当增量的 `pendingLeavesDelta`，`:52-64`）。

💡 影响评估：大屏与仪表盘对同一指标显示不同数字是最容易被业务方抓住的「数据不对」类 bug；状态字符串脱离枚举后，新增状态时这两处编译期毫无提示。

🛠️ 重构建议：
1. 工单状态集合与文案收敛到 `WorkOrderTransitions.Status` 枚举本身（枚举挂 `display` 字段与 `inProgressSet()` 静态方法，三处引用改一处维护）；
2. 抽一个 `WorkOrderStatsQuery` 聚合组件供两处共用趋势查询；
3. 顺手删掉 dashboard 的 0 占位并换成真实 identity 统计或明确标注 TODO。

⚠️ 风险等级：**高（立即处理）**（数据口径不一致 + 编译期无保护）

---

## 中风险（计划重构，趁热修）

### [F-04] 功能重复实现：QQ 集成照抄微信包（正在发生的复制事件）

📍 位置：
- `integration/qq/QqApiIdentityProvider.java:42-56` vs `integration/wechat/WechatApiIdentityProvider.java:28-42`（HTTP 客户端构造逐行同构）
- `integration/qq/MockQqIdentityProvider.java:37-49` vs `MockWechatIdentityProvider.java:33-45`（SHA-256→hex 逐字相同，含异常文案）
- 异常收敛模板 `catch (BusinessException e) throw e; catch (Exception e) {log; throw new BusinessException(...)}` 全仓 6 处同形

🔍 问题描述：`integration/qq/` 目录目前是 untracked（git 尚未提交），是最新一起「照着 wechat 抄骨架」事件。全库 SHA-256-hex 实现已有 4 份（两 Mock + `SecureTokenSupport.java:23-31`（包私有别人用不了）+ `KnowledgeBaseService.java:105` 单行变体）。

💡 影响评估：现在正是合并成本最低的时刻——qq 还没提交，wechat/qq 两套 Properties 字段完全同构（mode/apiBaseUrl/connectTimeoutSeconds/readTimeoutSeconds），将来接第三个登录渠道（钉钉/支付宝校园版）时会复制第三份。

🛠️ 重构建议：
1. 提交前先动手：抽 `AbstractOAuthIdentityProvider`（模板方法：构造 RestClient → exchange → parse → 异常收敛，子类只给 baseUrl/端点/解析差异如 JSONP 剥离）；
2. SHA-256-hex 收敛进 `common` 包工具类（把 `SecureTokenSupport.hash` 提升为 public），4 处引用替换；
3. 异常收敛模板抽静态 helper `IntegrationExceptions.wrap(ErrorCode, supplier)`。

⚠️ 风险等级：**中（趁 qq 未提交先做，否则每加一个渠道复制一次）**

---

### [F-05] 数据格式混乱：Admin 端 DTO 时间字段降级为 String，App/Admin 双格式

📍 位置：
- `servicerequest/controller/dto/AdminServiceRequestSummaryResponse.java:13-14` ← `AdminServiceRequestService.java:87-88`（`toString()` 降级）
- `community/controller/dto/AdminCommunityReportResponse.java:21-22` ← `CommunityModerationServiceImpl.java:459-460`
- `evaluation/controller/dto/AdminEvaluationSummaryResponse.java:14,17` ← `AdminEvaluationService.java:91,94`
- 假时区转换死代码：`AdminEvaluationService.java:94`（`LocalDateTime.atZone(zone).toLocalDateTime()` 数值不变）

🔍 问题描述：同一资源 App 端 DTO 用 `LocalDateTime`、Admin 端 DTO 用 `String` 手工 toString，JSON 时间格式两套；其中一处还有无效时区转换，暴露对时间类型理解混乱。

💡 影响评估：前端 App/管理端解析逻辑被迫分叉；JacksonConfig 全局已配置 ObjectMapper，LocalDateTime 直接可序列化，String 降级纯属多余代码。

🛠️ 重构建议：三个 Admin DTO 的 String 字段改回 `LocalDateTime`，删 3 处 `toString()` 与 1 处假时区转换；JacksonConfig 确认 Admin 端反序列化路径无消费方依赖旧字符串格式（管理端是自己前端，一次同步改掉即可）。

⚠️ 风险等级：**中**

---

### [F-06] 功能重复实现：分页默认值逻辑 8 份复制 + 全仓 4 套分页参数约定

📍 位置：
- `pageNumOrDefault/pageSizeOrDefault` 完全相同的两方法 ×7 个 DTO（`AdminUserPageQuery.java:17-22`、`WorkOrderPageQuery.java:14-19`、`MyNotificationQuery.java:14-19`、`AdminEvaluationPageQuery.java:16-21`、`AdminCommunityReportPageQuery.java:14-19`、`MyServiceRequestPageQuery.java:14-19`、`AdminServiceRequestPageQuery.java:14-19`）+ `ServiceRequestServiceImpl.java:242-248` 私有方法版 + `AiConversationServiceImpl.java:80` 内联版
- 参数约定分裂：community 用 `@RequestParam long` + service 内 clamp、aichat 用裸 `Integer` 可空、servicerequest 系用 Query 对象

💡 影响评估：新增分页接口时 8 选 1 随机抄，clamp 规则（如 community ≤20）不会跟着走。

🛠️ 重构建议：`common/persistence` 下新增 `PageQuery` 基类（`pageNum/pageSize` + `pageNumOrDefault()/pageSizeOrDefault()/clampedPageSize(max)`），7 个 DTO 改继承；新接口一律用 Query 对象约定，存量不强制迁移。

⚠️ 风险等级：**中**

---

### [F-07] 条件堆积：AI 协议分流散落在 OpenAiCompatibleProvider 5 个方法中

📍 位置：`integration/ai/OpenAiCompatibleProvider.java` 的 `headersFor:240-246`、`endpointPath:248-254`、`buildPayload:257-281`、`parseNonStreaming:284-332`、`parseStreamDelta:338-380`

🔍 问题描述：新增一种协议（如 Gemini 原生）需要同步改 5 个 switch。同类内还有双 HTTP 栈并存（chat 走原生 `HttpClient`、embed 走 `RestClient`）与双客户端缓存（`clientCache` 换版本 clear、`httpClientCache` 从不清——这是个小泄漏）。

🛠️ 重构建议：不必急于大拆——当前只有 3 种协议、5 个 switch 高内聚于一个类，属「可接受的临时集中」。但建议：
1. 合并双缓存为一个（修泄漏）；
2. 若确认要接第 4 种协议，届时再抽 `ProtocolCodec` 接口（encode/decode/streamDecode 三方法），把 5 个 switch 收进三个实现类。现在抽属于过度设计。

⚠️ 风险等级：**中（先修缓存泄漏，协议抽象挂账待第 4 协议出现）**

---

### [F-08] 接口分裂：AI 模型类型枚举三种口径 + 同域双路径前缀

📍 位置：
- `AiModelUpdateRequest.java:9`（正则只许 4 类）、`AiModelImportRequest.java:24`（正则 6 类）、`AiConfigServiceImpl.java:39-42`（常量 4 个）vs `AiProviderAdminServiceImpl.java:53-58`（常量 6 个）
- `/api/admin/ai/**`（两个 controller）与 `/api/admin/ai-config/**`（第三个 controller）同一管理域双前缀

🛠️ 重构建议：新建 `enums/AiModelType`（6 值 + `runtimeSupported()` 标记前 4 类可进运行时快照），两处正则改为枚举校验、两处常量删除；`ai-config` 前缀的 controller 路径并入 `/api/admin/ai/config`（保留旧路径一个版本期）。

⚠️ 风险等级：**中**

---

## 低风险（技术债务记录，择机处理）

### [F-09] 工具函数复制群（servicerequest/workorder/community/aichat）

📍 `parseIds` ×5（`WorkOrderServiceImpl.java:451`、`ServiceRequestServiceImpl.java:220`、`DirectMessageServiceImpl.java:139`、`CommunityServiceImpl.java:391`、`AiMessageResponse.java:29`）、`writeIds` ×3、`parseFormData` ×2 逐字相同（含相同日志文案）、`enrichImageUrls` ×2、单号 "TMP+回写" 模式 ×2、手机号正则 ×2、UUID token 惯用法 ×5。

🛠️ 建议：收敛到 `common/util`：`JsonIds.parse/write`、`PhoneNumbers.isValid`、`OrderNoGenerator.next(prefix, id)`；`AiMessageResponse.java:21` 自建 static ObjectMapper 改注入或统一工具。逐个 10 分钟级替换，可在任一改动经过这些文件时顺手做。

⚠️ **低**

### [F-10] 冗余注解与实体继承策略分裂

📍 `WorkOrderSummaryResponse.java:13,16` 等 6 处 `@JsonSerialize(ToStringSerializer)` 与 `JacksonConfig.java:17-18` 全局配置重复；`UserRoleEntity.java:21-22` 手写 createdAt/createdBy 绕过 BaseEntity 自动填充；aichat 模块内 `AiProviderEntity/AiModelEntity/AiDefaultModelEntity` 不继承 BaseEntity 而同模块其他实体继承。

🛠️ 建议：删 6 处冗余注解；UserRoleEntity 改继承 BaseEntity；aichat 三实体因无审计需求可不强制继承，但需在模块文档记一句约定。

⚠️ **低**

### [F-11] 转换器风格分裂（同一项目三种落点）

📍 DTO 静态 `from()`（aichat 模式，最规范）vs Service 私有方法（community 模式）vs 混用（ServiceRequest：Summary 有 `from` + Service 又有 `toSummary`；WorkOrder：Summary 有 `from` 而 Detail 无，靠 `WorkOrderServiceImpl.java:423` 手拼 26 参 record）。

🛠️ 建议：定一条项目规范「一律 DTO 静态工厂」，新代码遵守即可；存量里优先补 `WorkOrderDetailResponse.from()`（26 个实参的手拼构造最易在加字段时错位），其余不强制回改。

⚠️ **低**

### [F-12] NO_PROXY 直连规避三处重复

📍 `OpenAiCompatibleProvider.java:459-465`、`AiProviderAdminServiceImpl.java:341-344`、`AiSpeechServiceImpl.java:58-60`，同一「绕过系统代理防境外绕行」逻辑三写。

🛠️ 建议：收敛到 `HttpClientConfig` 提供一个 `directHttpClient()` bean（内置 NO_PROXY + 默认超时），三处引用。

⚠️ **低**

---

## 明确标注为「可接受重复」（不要动）

1. **SessionStore（identity）vs JwSessionStore（jwauth）**：方法级对照确认两者并非复制——键型（token 哈希多会话 vs userId 单会话）、加密（无 vs AES-GCM）、续期（有 renew vs 无）、Redis 索引（双键 Set vs 单键）全不同，方法集合仅 2/7 重叠。合并需先造泛型 `Store<K,V>` 抽象，收益只覆盖 InMemory/Redis 的样板脚手架，属于「为消灭重复创造更复杂的元抽象」的反例。**维持现状，仅当第三套会话存储出现时再抽象**。
2. **CurrentUserResponse vs AdminUserSummaryResponse** 字段重叠：两个视图的合理 DTO 分层。
3. **双状态机模板（RequestTransitions vs WorkOrderTransitions）**：表内容是两张不同的状态表，switch 骨架同构属状态机模式固有形态；真正要修的是 F-01 的调用纪律，不是把两张表合并成一个元抽象。
4. **WorkOrder `transitionRequest` 5参/3参重载**：同一语义的默认参数重载，合理。
5. **devtool 三个 `/api/dev/*` 端点**：开发工具，标注保留，上线前确认有 profile 隔离即可。

---

## 附：git 历史佐证要点

- servicerequest 与 workorder **并非不同时期独立开发**：`git log --follow` 显示两模块全部文件由 3 个共同提交构成（`aa083ca` 2026-08-28 同日新增双状态机 → `4171c44` 08-29 → `6e0d86c` 09-15），重复属同批双生而非时期漂移。
- identity 会话存储演进自 2026-08-21（`09ce17b`→`ce38f87`→`401a5a8`），jwauth 会话存储 08-29 独立新增（`145d7ad`→`a562e41`），无同 commit 复制痕迹——支持「可接受重复」判定。
- `integration/qq/` 目录与配套 `V39__add_qq_identity_fields.sql` 截至诊断日仍为 untracked。

---

## 渐进式改进路线（按依赖排序）

| 顺序 | 动作 | 覆盖问题 | 规模 |
|---|---|---|---|
| 1 | QQ 提交前抽 OAuth 基类 + SHA-256/异常模板收敛 | F-04 | 2 天 |
| 2 | review 走状态机 + transitionRequest 上移为公开服务 + 补 version 条件更新 | F-01 | 2 天 |
| 3 | resolveBinding 下沉 service，删 controller 直连 mapper | F-02 | 半天 |
| 4 | 状态枚举收敛（display + inProgressSet 挂枚举），dashboard/screen 引用同一来源 | F-03 | 1 天 |
| 5 | Admin 时间 DTO 改 LocalDateTime + 枚举三口径统一 | F-05/F-08 | 1 天 |
| 6 | PageQuery 基类 + common/util 工具收敛（随手做） | F-06/F-09/F-10 | 1 天 |
| 7 | （挂账）第 4 种 AI 协议出现时再抽 ProtocolCodec | F-07 | 届时 2 天 |

另有两处独立小问题不在上述分类内但建议顺手处理：

- `WechatPhoneNumberClient.java:31,47` 硬编码 connect 超时与 `https://api.weixin.qq.com`，同包 `WechatApiIdentityProvider` 用的是 `properties.getApiBaseUrl()`，配置口径应统一；
- `OpenAiCompatibleProvider.httpClientCache`（`:460`）换版本不清空，与 `clientCache`（`:474`）行为不一致，属轻量内存泄漏。
