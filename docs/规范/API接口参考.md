# student-hub HTTP 接口参考

本文档由源码逐文件盘点生成，覆盖全部 50 个 Controller。生成日期：2026-10-17（2026-10-19 增量：徽章方案B 字典热更新；App 版本发布；2026-10-21 增量：SYS03 安全运行时、广告事件防刷、广告主远程搜索）。

## 全局约定

- **统一响应**：所有 JSON 接口返回 `Result<T>`：`{ success: boolean, code: string, message: string, data: T, timestamp: string }`，成功固定 `code="OK"`；下文各接口只描述 `data` 部分。
- **分页**：分页接口返回 `PageResult<T>`：`{ pageNum, pageSize, total, records: T[] }`，pageNum 从 1 开始。
- **认证**：除「白名单（无需登录）」接口外，均需请求头 `Authorization: Bearer <accessToken>`（不透明令牌，登录接口下发）。401/403 与业务错误码见文末速查表。
- **App 会话**：Flutter App 请求携带 `x-client-type: app`；新登录会话使用 `app-session-ttl`（默认 30 天）及滑动续期，未声明客户端类型的请求使用默认短期会话。QQ/密码/注册等认证入口的 401 表示该次认证失败，不得按平台会话过期清除已有 Token；平台鉴权失败才返回 `AUTH_401` 并退出登录。
- **ID 序列化**：数字型 ID（Long）返回为字符串（JacksonConfig 全局 Long→String，防 JS 精度丢失），请求中数字 ID 仍可传数字或字符串。
- **角色/权限**：App 端部分接口有 `@PreAuthorize` 权限点要求，已在鉴权列标注；无标注的管理端接口默认登录即可，标注了权限点的需相应权限。
- **通用分页参数**：`pageNum`（默认 1）、`pageSize`（各接口上限不同，默认 10）。

### 白名单（无需登录）路径

`/api/common/system/health`、`/api/common/auth/login`、`/api/common/auth/captcha`（仅密码登录启用时装配）、`/api/common/auth/wechat-login`、`/api/common/auth/qq-login`、`/api/common/auth/dev-wechat-login`、`/api/common/auth/email-code`、`/api/common/auth/register`、`/api/common/auth/password/reset`、`/api/common/files/view/**`、`/api/common/assets/**`、`/api/public/screen/**`、`/api/public/knowledge-bases/**`、`/api/public/app-version/**`、`/ws/**`，以及 Swagger/静态页（`/doc.html`、`/v3/api-docs/**`、`/swagger-ui/**` 等）。

---

## 管理端 /api/admin

### 用户管理 `/api/admin/users`（需权限 system:user:manage）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/users` | 分页查询用户，支持关键词/状态/身份/授权状态/官方号/角色过滤 | 登录+system:user:manage | query: pageNum, pageSize, keyword, status: ENABLED/DISABLED, identityType: STUDENT/TEACHER/LOGISTICS/ADMIN, authorizationStatus: BASIC/PENDING/AUTHORIZED/REJECTED, official: true/false, roleId |
| POST `/api/admin/users/{userId}/status` | 启用/停用账号，停用后在线会话立即失效，不能操作自己 | 同上 | body: status: ENABLED/DISABLED |
| POST `/api/admin/users/{userId}/authorization` | 授权审核，APPROVE 置已授权 / REJECT 置已拒绝 | 同上 | body: action: APPROVE/REJECT, reason |
| POST `/api/admin/users/{userId}/official` | 设置/取消官方账号；官方账号在社区展示「官方 · 头衔」替代 Lv 等级，可作为官方通知发布主体 | 同上 | body: official: true/false, officialTitle（设为官方时必填） |
| PUT `/api/admin/users/{userId}/roles` | 整体覆盖调整用户角色，授予服务角色联动后勤身份 | 同上 | body: roleIds: Long[] |
| GET `/api/admin/users/workers` | 查询可派单的服务人员（绑定服务角色且启用） | 同上 | query: keyword |

- 返回：分页接口 → `PageResult<AdminUserSummaryResponse>`；角色接口 → `AdminUserSummaryResponse`；workers → `WorkerCandidateResponse[]`。
- `AdminUserSummaryResponse`：userId, username, displayName, phone, identityType, authorizationStatus, status: ENABLED/DISABLED, lastLoginAt, createdAt, roles: RoleItem[]{roleId, roleCode, roleName}
- `WorkerCandidateResponse`：userId, username, displayName, phone

### 角色 `/api/admin/roles`（需权限 system:user:manage）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/roles` | 查询启用中的角色列表，供角色分配下拉 | 登录+system:user:manage | 无 |

- 返回：`AdminRoleResponse[]`：roleId, roleCode, roleName, roleType: ADMIN/REQUESTER/WORKER/DISPATCHER, status: ENABLED/DISABLED

### 服务目录 `/api/admin/catalog`（需权限 catalog:manage）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/catalog/categories` | 查询全部分类（含停用）及其服务项 | 登录+catalog:manage | 无 |
| GET `/api/admin/catalog/items` | 查询服务项列表，可按分类过滤，含停用 | 同上 | query: categoryId |
| POST `/api/admin/catalog/categories` | 创建启用状态的服务分类 | 同上 | body: categoryCode, categoryName, icon, description, sortOrder |
| POST `/api/admin/catalog/items` | 创建服务项 | 同上 | body: ServiceItemUpsertRequest（见下） |
| POST `/api/admin/catalog/items/update` | 按 itemId 更新服务项 | 同上 | body: ServiceItemUpsertRequest（itemId 必填） |
| POST `/api/admin/catalog/items/{itemId}/status` | 上架/下架服务项 | 同上 | body: status: ENABLED/DISABLED |
| DELETE `/api/admin/catalog/items/{itemId}` | 逻辑删除服务项 | 同上 | 无 |

- GET categories → `CatalogCategoryResponse[]`（与 App 端同名结构，见 App 端「服务目录」）。
- GET items → `ServiceItemAdminResponse[]`：itemId, categoryId, categoryName, itemCode, itemName, summary, description, icon, status: ENABLED/DISABLED, slaHours, sortOrder, handlerOrganizationId, formSchema(JSON), updatedAt
- `ServiceItemUpsertRequest`：itemId(更新必填), categoryId, itemCode, itemName, summary, description, icon, formSchema(JSON), handlerOrganizationId, slaHours, sortOrder

### 服务申请审批 `/api/admin/service-requests`（需权限 platform:all / catalog:manage / work-order:dispatch 其一）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/service-requests` | 全平台分页查询服务申请，可按状态过滤 | 登录+上述权限之一 | query: pageNum, pageSize, status: SUBMITTED/PROCESSING/COMPLETED/REJECTED/CANCELLED |
| POST `/api/admin/service-requests/{requestId}/review` | 审批：APPROVE 通过（转处理中）/ REJECT 驳回 | 同上 | body: action: APPROVE/REJECT, comment(驳回原因) |

- 返回：`PageResult<AdminServiceRequestSummaryResponse>`：requestId, requestNo, itemName, title, requesterId, status, submittedAt, completedAt, rejectReason

### 工单调度 `/api/admin/work-orders`（需权限 work-order:dispatch）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/work-orders` | 按调度员组织数据范围分页查询工单队列，可按状态过滤 | 登录+work-order:dispatch | query: pageNum, pageSize, status: PENDING_DISPATCH/ASSIGNED/PROCESSING/COMPLETED/REJECTED/CANCELLED |
| GET `/api/admin/work-orders/{orderId}` | 查询工单详情（含关联申请表单内容），需在调度范围内 | 同上 | 无 |
| POST `/api/admin/work-orders/{orderId}/assign` | 派发工单给服务人员；首次派单把申请推进到处理中 | 同上 | body: assigneeId, comment |
| POST `/api/admin/work-orders/{orderId}/reject` | 驳回无效工单，申请同步置为未受理并通知申请人 | 同上 | body: reason |

- 返回：`PageResult<WorkOrderSummaryResponse>` / `WorkOrderDetailResponse`（结构见 App 端「我的工单」）。

### 服务评价 `/api/admin/evaluations`（需权限 platform:all / catalog:manage 其一）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/evaluations` | 全平台分页查询评价，可按评分与回复状态过滤 | 登录+上述权限之一 | query: pageNum, pageSize, rating(1-5), replied: true/false |
| POST `/api/admin/evaluations/{evaluationId}/reply` | 回复评价并站内通知评价人，一条仅可回复一次 | 同上 | body: reply |

- 返回：`PageResult<AdminEvaluationSummaryResponse>`：evaluationId, requestId, workOrderId, rating, tags, comment, reply, repliedAt, evaluatorId, assigneeName, createdAt

### 公告管理 `/api/admin/notices`（需权限 platform:all / announcement:manage 其一）

公告渠道拆分：SCREEN 终端大屏轮播 / APP 校园公告共用 screen_notice 表，以 channel 区分；
发布状态机 DRAFT/SCHEDULED → PUBLISHED → OFFLINE（可恢复），SCHEDULED 到点由 30 秒定时器自动上线。

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/notices` | 查询公告列表，可按渠道/发布状态/类型过滤 | 登录+上述权限之一 | query: channel: SCREEN/APP, publishStatus: DRAFT/SCHEDULED/PUBLISHED/OFFLINE, noticeType: NOTICE/NEWS/ACTIVITY |
| POST `/api/admin/notices` | 创建公告，action 决定初始状态 | 同上 | body: noticeType, title(≤128), summary(≤512), publishDate(yyyy-MM-dd，默认今天), channel, action: PUBLISH(默认)/SCHEDULE/DRAFT/OFFLINE, publishAt(ISO，SCHEDULE 必填且须未来), sortOrder, author(≤64), enabled |
| PUT `/api/admin/notices/{id}` | 更新公告内容（action 语义同创建） | 同上 | body 同创建 |
| POST `/api/admin/notices/{id}/publish` | 上线：置 PUBLISHED（SCHEDULED 可提前上线） | 同上 | 无 |
| POST `/api/admin/notices/{id}/offline` | 下线：置 OFFLINE，可再 publish 恢复 | 同上 | 无 |
| DELETE `/api/admin/notices/{id}` | 删除公告 | 同上 | 无 |

- `AdminNoticeResponse`：id, noticeType, typeText(通知/要闻/活动), title, summary, publishDate, channel, publishStatus, publishAt, sortOrder, author, enabled

### 字典管理 `/api/admin/dicts`（需权限 platform:all / system:dict:manage 其一）

通用字典：typeCode 为业务读取键，itemCode 在同一字典内唯一；编码创建后不可修改；内置字典（builtin=true）不可删除，可停用。
种子字典：
- 学校结构：`CAMPUS`（2）/ `COLLEGE`（5）/ `MAJOR`（42）/ `CLASS`（687，extra 含学院/专业/校区/层次/人数）；
- 宿舍报修：`REPAIR_FAULT_TYPE`（12，extra.emergency 标记停水/停电/漏水）、`DORM_BUILDING`（21 栋）；
- 全量系统枚举（V88）：身份与组织、服务/工单/请假、社区治理/广告、公告/内容位、消息推送、App 版本、AI、审计/配置等 74 类 387 项；
- 成就与成长只读字典：`ACHIEVEMENT_CATEGORY` / `ACHIEVEMENT_BADGE` / `ACHIEVEMENT_RARITY` / `ACHIEVEMENT_METRIC` / `ACHIEVEMENT_GRANT_SOURCE` / `USER_LEVEL`，由 `SystemDictProvider` 从 `achievement_*` 实时读取，`source=ACHIEVEMENT`、`readonly=true`，不接受管理端增删改。
- 业务目录只读字典：`SERVICE_CATEGORY` / `SERVICE_ITEM` / `COMMUNITY_SECTION` / `COMMUNITY_TOPIC` / `IDENTITY_REJECT_REASON` / `USER_ROLE`，由 `SystemDictProvider` 从业务表实时读取，`source=BUSINESS`、`readonly=true`，编辑入口在「服务目录」「社区分区/话题」「身份审核」「角色权限」。

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/dicts/types` | 字典类型列表（含 itemCount），按编码升序 | 登录+上述权限之一 | query: keyword(编码/名称模糊), status: ENABLED/DISABLED(可选) |
| POST `/api/admin/dicts/types` | 创建字典类型 | 同上 | body: typeCode(^[A-Z][A-Z0-9_]{1,63}$), typeName(≤64), remark(≤255), status(缺省 ENABLED) |
| PUT `/api/admin/dicts/types/{id}` | 更新名称/备注/状态；typeCode 传不同值返回 400 | 同上 | body 同上 |
| DELETE `/api/admin/dicts/types/{id}` | 删除类型并级联删除其字典项；内置字典返回 400 | 同上 | 无 |
| GET `/api/admin/dicts/types/{typeCode}/items` | 字典项列表，按 sortOrder/itemCode 升序 | 同上 | query: includeDisabled(默认 false) |
| POST `/api/admin/dicts/items` | 创建字典项 | 同上 | body: typeCode, itemCode, itemLabel(≤128), itemValue(缺省=itemLabel), sortOrder, status, extra(合法 JSON 或空), remark |
| PUT `/api/admin/dicts/items/{id}` | 更新字典项；typeCode/itemCode 不可修改 | 同上 | body 同上（typeCode/itemCode 传不同值返回 400） |
| DELETE `/api/admin/dicts/items/{id}` | 删除字典项（逻辑删除） | 同上 | 无 |

- `DictTypeResponse`：id, typeCode, typeName, remark, status, builtin, itemCount, createdAt, updatedAt, source（MANUAL/ACHIEVEMENT）, readonly
- `DictItemResponse`：id, typeCode, itemCode, itemLabel, itemValue, sortOrder, status, extra, remark
- 只读字典（readonly=true）调用新增/编辑/删除字典项返回 400；实际维护入口在「成就管理」等业务页面。
- 当前管理端目录规模：**92 类 / 1245 项**（80 类持久化字典 1156 项 + 6 类成就/成长只读字典 52 项 + 6 类业务目录只读字典 37 项）。

### 社区治理 `/api/admin/community`（需权限 platform:all / community:moderate 其一）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/community/reports` | 分页查询举报，可按状态与对象类型过滤 | 登录+上述权限之一 | query: pageNum, pageSize, status: PENDING/ACCEPTED/REJECTED, targetType: POST/COMMENT/MESSAGE/USER |
| GET `/api/admin/community/reports/summary` | 治理概览：待处理/已成立/已驳回/今日举报数与生效处罚数 | 同上 | 无 |
| POST `/api/admin/community/reports/{reportId}/handle` | 处理举报：ACCEPT 可同时处置内容并处罚作者；REJECT 仅记录 | 同上 | body: action: ACCEPT/REJECT, contentAction: NONE/HIDE/DELETE, sanctionType: MUTE/BAN, sanctionHours(0=长期), note |
| POST `/api/admin/community/content/{targetType}/{contentId}/hide` | 下架帖子或评论（内容保留、仅治理端可见），通知作者 | 同上 | body: reason（可空请求体） |
| POST `/api/admin/community/content/{targetType}/{contentId}/restore` | 恢复已下架内容并通知作者 | 同上 | body: reason（可空请求体） |
| POST `/api/admin/community/users/{userId}/sanctions` | 处罚用户：MUTE 限制发言 / BAN 禁止访问 | 同上 | body: sanctionType: MUTE/BAN, hours(0=长期), reason |
| POST `/api/admin/community/sanctions/{sanctionId}/revoke` | 撤销处罚（申诉解除）并通知本人 | 同上 | 无 |

- 举报分页 → `AdminCommunityReportResponse`：id, targetType, targetId, targetTitle, targetAuthorId, targetAuthorName, reporterId, reporterName, reasonCode, reasonDetail, snapshot(JSON 内容快照), status, handleNote, handledBy, handledAt, createdAt
- summary → `{ pendingCount, acceptedCount, rejectedCount, todayCount, activeSanctionCount }`

### 社区运营 `/api/admin/community`（需权限 platform:all / community:moderate 其一）

社区作为独立管理项，含概览、帖子管理、申诉复核、分区/话题、屏蔽词。发布免人工审核；命中屏蔽词自动隐藏（HIDDEN）并通知作者，作者可申诉。

| 方法 路径 | 说明 | 请求 |
|---|---|---|
| GET `/api/admin/community/overview` | 社区数据概览：帖子/评论/活跃用户/举报/隐藏/申诉/屏蔽词 + 7 天发帖趋势 + 分区分布 | 无 |
| GET `/api/admin/community/posts` | 帖子管理列表（带封面图、状态、举报数） | query: pageNum, pageSize, keyword, sectionId, status: ENABLED/HIDDEN/DELETED, postType: DYNAMIC/ARTICLE |
| GET `/api/admin/community/posts/{postId}` | 帖子详情（完整图文块、隐藏原因、举报数） | 无 |
| GET `/api/admin/community/sections` | 分区列表（含停用与帖子数） | 无 |
| POST `/api/admin/community/sections` | 创建分区 | body: sectionCode, sectionName, icon, description, bannerTitle, bannerSubtitle, bannerImage, sortOrder, status |
| PUT `/api/admin/community/sections/{id}` | 更新分区（编码不可改） | body 同上 |
| DELETE `/api/admin/community/sections/{id}` | 删除分区（分区下有帖子时拒绝） | 无 |
| GET `/api/admin/community/topics` | 话题列表 | 无 |
| POST `/api/admin/community/topics` | 创建话题 | body: name, heat, status |
| PUT `/api/admin/community/topics/{id}` | 更新话题 | body 同上 |
| DELETE `/api/admin/community/topics/{id}` | 删除话题 | 无 |
| GET `/api/admin/community/banned-words` | 屏蔽词列表 | query: keyword(可选) |
| POST `/api/admin/community/banned-words` | 新增屏蔽词（命中自动隐藏并通知作者） | body: word, remark, status |
| PUT `/api/admin/community/banned-words/{id}` | 更新屏蔽词 | body 同上 |
| DELETE `/api/admin/community/banned-words/{id}` | 删除屏蔽词 | 无 |
| GET `/api/admin/community/banned-words/export` | 导出屏蔽词 Excel（列：屏蔽词/备注/状态） | 无 |
| POST `/api/admin/community/banned-words/import` | 导入屏蔽词 Excel（首行表头，重复词跳过） | multipart: file(.xlsx/.xls) |
| GET `/api/admin/community/appeals` | 申诉列表（帖子/评论/处罚） | query: pageNum, pageSize, status: PENDING/APPROVED/REJECTED, targetType: POST/COMMENT/SANCTION |
| POST `/api/admin/community/appeals/{id}/handle` | 处理申诉：APPROVE 恢复内容/撤销处罚；REJECT 驳回；结果通知用户 | body: action: APPROVE/REJECT, note(≤500) |

- 概览 → `AdminCommunityStatsResponse`：totalPosts, todayPosts, totalComments, todayComments, activeUsers7d, pendingReports, hiddenPosts, hiddenComments, pendingAppeals, bannedWordCount, trend[{date,count}], sections[{sectionId,sectionName,count}]
- 帖子列表 → `AdminCommunityPostResponse`：postId, sectionId, sectionName, postType, title, summary, coverUrl, authorId, authorName, status, hiddenReason, reportCount, commentCount, likeCount, createdAt
- 帖子详情 → `AdminCommunityPostDetailResponse`：在列表字段基础上含 content、contentBlocks（图片块带签名 url/thumbnailUrl）、imageUrls、thumbnailUrls、hiddenAt、topics
- 屏蔽词导入结果 → `AdminBannedWordImportResult`：total, imported, duplicated, invalid；状态列支持「启用/停用」或 ENABLED/DISABLED

### 官方通知 `/api/admin/official-feed`（需权限 platform:all / community:moderate 其一）

抓取学校官网（仅 `*.mycc.edu.cn`）栏目列表与正文，以官方账号发布到「校园通知」分区（sectionCode `OFFICIAL_NOTICE`）。正文含图片发布为 ARTICLE（图文混排块），纯文本发布为 DYNAMIC；按原文 URL 与内容哈希去重。默认 30 分钟定时同步，也可手动触发。

| 方法 路径 | 说明 | 请求 |
|---|---|---|
| GET `/api/admin/official-feed/sources` | 抓取来源列表（含发布分区、官方主体、最近同步状态） | 无 |
| GET `/api/admin/official-feed/official-users` | 可选的官方发布主体 | 无 |
| POST `/api/admin/official-feed/sources` | 新增来源（地址必须为学校官网域名） | body: sourceName, listUrl, sectionId, authorId, maxPages(1-5), maxArticles(1-10), maxAgeDays, enabled |
| PUT `/api/admin/official-feed/sources/{sourceId}` | 更新来源 | body 同上 |
| POST `/api/admin/official-feed/sources/{sourceId}/sync` | 立即同步单个来源 | 无 |
| POST `/api/admin/official-feed/sync-all` | 立即同步全部启用来源 | 无 |
| GET `/api/admin/official-feed/articles` | 抓取记录分页（PUBLISHED/SKIPPED/FAILED） | query: pageNum, pageSize, sourceId, status |

- 同步结果 → `OfficialFeedSyncResult`：sourceId, sourceName, discovered, published, skipped, failed, status, items[{title, sourceUrl, postId, postType, status, message}]
- 单次最多处理 `maxArticles` 篇新文章；超过 `maxAgeDays` 的旧通知只记录为 SKIPPED，不发布；正文为空时回退提取内嵌 PDF/附件链接。
- 帖子及评论响应新增官方标识：`authorOfficial`（boolean）、`authorOfficialTitle`（string）；作者主页与用户名片同步返回 `official/officialTitle`。客户端应优先展示「官方 · 头衔」，官方账号不再展示 Lv 等级。

### 成就管理 `/api/admin/achievements`（需权限 platform:all / system:user:manage 其一）

勋章/成就目录与解锁条件配置；条件 = 指标 + 阈值，用户在 `AchievementService` 懒评估中达标即自动解锁并通知，无需人工授予。

| 方法 路径 | 说明 | 请求 |
|---|---|---|
| GET `/api/admin/achievements/badges` | 勋章列表（响应新增 categoryName、iconUrl） | query: category, enabled(可选) |
| POST `/api/admin/achievements/badges` | 创建勋章/成就（badgeCode 唯一）；新码如需展示请随后上传图标 | body: badgeCode, badgeName, category: COMMUNITY/SERVICE/WORKER/JW/GROWTH, rarity: COMMON/RARE/EPIC/LEGENDARY, description, metricCode, metricThreshold(≥1), rewardExp, sortOrder, enabled |
| PUT `/api/admin/achievements/badges/{id}` | 更新（badgeCode 不可改） | body 同上 |
| POST `/api/admin/achievements/badges/{id}/status` | 启用/停用（停用不参与评估，并清空该勋章的佩戴态） | query: enabled |
| DELETE `/api/admin/achievements/badges/{id}` | 逻辑删除（并清空该勋章的佩戴态） | 无 |
| DELETE `/api/admin/achievements/badges/{id}/icon` | 清除勋章图标：iconUrl 置空，客户端回退内置本地素材/占位图 | 无 |
| POST `/api/admin/achievements/badges/{id}/icon` | 上传/替换勋章图标：PNG/JPEG/WebP ≤512KB（服务端嗅探魔数），写入对象存储 `static/assets/badges/{code}.{ext}` 并更新 iconUrl | multipart: file |

- `AdminAchievementBadgeResponse`：id, badgeCode, badgeName, category, categoryName, rarity, description, metricCode, metricThreshold, rewardExp, sortOrder, enabled, iconUrl, createdAt
- 内置 19 枚勋章 iconUrl 为 null（客户端走本地素材）；新增码上传图标后由字典接口下发带版本参数的 URL。

- 解锁指标：POST_COUNT, COMMENT_COUNT, POST_LIKES, POST_LIKES_SINGLE, REQUEST_COUNT, REQUEST_COMPLETED, EVALUATION_COUNT, EVALUATION_FIVE_STAR, WORKER_ORDER_COMPLETED, JW_AUTH, SCHEDULE_IMPORTED, PROFILE_COMPLETE, LEVEL

### 版本发布 `/api/admin/app-versions`（需权限 platform:all / system:user:manage 其一）

App 安装包版本管理：创建草稿 → 上传安装包（原始流，不走 multipart）或填写外链 → 发布。同一平台+渠道同时只保留一个已发布版本，发布新版本时旧版本自动下线。

| 方法 路径 | 说明 | 请求 |
|---|---|---|
| GET `/api/admin/app-versions` | 版本列表（versionCode 倒序） | query: platform(可选 ANDROID/IOS/HARMONY), status(可选 DRAFT/PUBLISHED/OFFLINE) |
| POST `/api/admin/app-versions` | 创建草稿（同平台+渠道版本号唯一） | body: platform, channel?, versionCode, versionName, minSupportedVersionCode?, forceUpdate?, updateLog?, downloadUrl? |
| PUT `/api/admin/app-versions/{id}` | 更新草稿（仅 DRAFT） | body 同上 |
| POST `/api/admin/app-versions/{id}/package` | 上传安装包：**原始流**，`Content-Type: application/octet-stream`，body 即文件 | query: fileName(.apk/.ipa/.hap)；≤300MB；校验 ZIP 魔数并流式计算 SHA-256；仅 DRAFT |
| POST `/api/admin/app-versions/{id}/publish` | 发布（须有安装包或 downloadUrl） | 无 |
| POST `/api/admin/app-versions/{id}/offline` | 下线（仅 PUBLISHED） | 无 |
| DELETE `/api/admin/app-versions/{id}` | 删除（仅 DRAFT/OFFLINE，逻辑删除） | 无 |
| GET `/api/admin/app-versions/{id}/download` | 管理端下载已上传安装包（校验用，流式） | 无 |

- `AppVersionResponse`：id, platform, channel, versionCode, versionName, minSupportedVersionCode, forceUpdate, updateLog, packageFileName, packageSize, packageSha256, hasPackage, downloadUrl, status, publishedAt, downloadCount, createdAt
- 安装包存对象存储 `static/packages/{platform}/{versionName}-{versionCode}.{ext}`；上传/下载均流式，不整包读入内存。

### 工作台 `/api/admin/dashboard`（需权限 platform:all / system:user:manage / work-order:dispatch 其一）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/dashboard` | 聚合数据：KPI、近 7 天工单趋势、分类占比、最新工单 | 登录+上述权限之一 | 无 |

- 返回：`DashboardResponse`：pendingWorkOrders, pendingDeltaPercent, todayNewUsers, todayNewUsersDeltaPercent, todayAiSessions, todayAiSessionsDeltaPercent, pendingRequests, pendingRequestsDelta, weekNewWorkOrders, weekResolvedWorkOrders, resolveRatePercent, dailyAvgNew, trend: [{day, created, resolved}], categories: [{name, percent}], latestOrders: [{orderId, orderNo, title, status, createdAt}]

### AI 全局设置 `/api/admin/ai/config`（需权限 ai:manage）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/ai/config` | 查询 AI 全局设置 | 登录+ai:manage | 无 |
| PUT `/api/admin/ai/config` | 保存设置，进程内即时生效 | 同上 | body: enabled, connectTimeoutSeconds(1-120), readTimeoutSeconds(1-600), maxInputCharacters, hourlyMessageLimit, historyWindow(0-100), maxMessageCharacters，以及上下文策略字段（见下） |

- 返回：`AiConfigResponse`：enabled, connectTimeoutSeconds, readTimeoutSeconds, maxInputCharacters, hourlyMessageLimit, historyWindow, maxMessageCharacters, maxContextTokens, contextSafetyMarginTokens, maxOutputTokens, historyTokenRatio, ragTokenRatio, attachmentTokenRatio, summaryEnabled, summaryTriggerRatio, summaryKeepRecentTurns, tokenEstimateCharsPerToken, activeChatModel(默认聊天模型，未配置为 null)
- 上下文策略（V58）：单次请求按「模型窗口 - 最大输出 - 安全余量」计算可用预算，再按 history/rag/attachment 比例分配；历史超预算时异步压缩为会话摘要；供应商报 context length 自动压缩重试一次。

### AI 供应商与模型 `/api/admin/ai`（需权限 ai:manage）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/ai/providers` | 供应商列表 | 登录+ai:manage | 无 |
| POST `/api/admin/ai/providers` | 新增供应商 | 同上 | body: providerName, baseUrl, apiKey, providerCode, icon, protocol: OPENAI_CHAT/ANTHROPIC_MESSAGES/OPENAI_RESPONSES, streaming |
| PUT `/api/admin/ai/providers/{providerId}` | 编辑供应商；apiKey 留空保持原值 | 同上 | body: 同创建，字段均可空部分更新 |
| DELETE `/api/admin/ai/providers/{providerId}` | 删除供应商，级联删模型与默认绑定 | 同上 | 无 |
| POST `/api/admin/ai/providers/{providerId}/check` | 连通性检查（探测 /models） | 同上 | 无 |
| GET `/api/admin/ai/providers/{providerId}/remote-models` | 拉取供应商可用模型 ID 列表 | 同上 | 无 |
| POST `/api/admin/ai/providers/{providerId}/models/import` | 批量导入模型，已存在自动跳过 | 同上 | body: models: [{providerModelId, modelType: CHAT/VISION/ASR/TTS/EMBEDDING/RERANK, capabilities: stream/vision/audio/tools/structured/reasoning[]}] |
| GET `/api/admin/ai/models` | 跨供应商模型列表，支持关键词与类型过滤 | 同上 | query: keyword, modelType |
| PUT `/api/admin/ai/models/{modelId}` | 更新模型启用状态/类型/上下文能力 | 同上 | body: enabled, modelType: CHAT/VISION/ASR/EMBEDDING/RERANK, contextWindowTokens(可空), maxOutputTokens(可空) |
| DELETE `/api/admin/ai/models/{modelId}` | 删除模型，若为类型默认一并清除 | 同上 | 无 |
| GET `/api/admin/ai/defaults` | 查询 CHAT/VISION/ASR 类型默认模型 | 同上 | 无 |
| PUT `/api/admin/ai/defaults` | 设置类型默认模型，modelId 为空表示清除 | 同上 | body: modelId, typeCode: CHAT/VISION/ASR/EMBEDDING |

- `AiProviderResponse`：providerId, providerCode, providerName, icon, baseUrl, protocol, streaming, apiKeyConfigured, enabled, lastCheckAt, lastCheckOk, lastCheckResult, modelCount
- `AiModelResponse`：modelId, providerId, providerName, providerModelId, modelType, enabled, capabilities, contextWindowTokens, maxOutputTokens
- `AiDefaultModelResponse`：typeCode, typeName, model(AiModelResponse)
- 连通性检查 → `AiProviderCheckResponse`：ok, result, checkedAt；导入 → 新增数量 Integer

### RAG 知识库 `/api/admin/knowledge-bases`（需权限 ai:manage）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/knowledge-bases` | 知识库列表 | 登录+ai:manage | 无 |
| POST `/api/admin/knowledge-bases` | 创建知识库 | 同上 | body: name, description, visibility: INTERNAL/PUBLIC, enabled, chunkSize(300-3000), chunkOverlap(0-600), topK(1-20), scoreThreshold(0-1), systemPrompt, assistantEnabled(默认 true), ragTokenBudget(200-32000), assistantPriority |
| PUT `/api/admin/knowledge-bases/{id}` | 更新知识库 | 同上 | body: 同创建 |
| DELETE `/api/admin/knowledge-bases/{id}` | 删除知识库 | 同上 | 无 |
| GET `/api/admin/knowledge-bases/{id}/documents` | 文档列表 | 同上 | 无 |
| POST `/api/admin/knowledge-bases/{id}/documents` | 上传并索引文档（multipart，form 字段 file） | 同上 | multipart: file |
| POST `/api/admin/knowledge-bases/{id}/documents/{docId}/reindex` | 重建文档索引 | 同上 | 无 |
| DELETE `/api/admin/knowledge-bases/{id}/documents/{docId}` | 删除文档 | 同上 | 无 |
| POST `/api/admin/knowledge-bases/{id}/test` | 混合检索测试 | 同上 | body: question, generateAnswer(默认 true) |

- `KnowledgeBaseResponse`：id, name, description, visibility, enabled, chunkSize, chunkOverlap, topK, scoreThreshold, systemPrompt, documentCount, chunkCount, assistantEnabled, ragTokenBudget, assistantPriority, createdAt, updatedAt
- `assistantEnabled` 与 `visibility` 解耦：内部库可给登录用户的 App AI 检索；公开接口仍只允许 `visibility=PUBLIC`。
- `KnowledgeDocumentResponse`：id, knowledgeBaseId, fileName, contentType, fileSize, status, chunkCount, errorMessage, indexedAt, createdAt
- `RagTestResponse`：answer, hits: [{chunkId, documentId, fileName, ordinal, page, titlePath, content, score, vectorScore, keywordScore, vectorRank, lexicalRank}], retrievalMs, generationMs, embeddingModel

### AI 流式测试（SSE）

- **POST `/api/admin/ai/chat-stream`（text/event-stream）** — SSE 流式对话测试，需权限 platform:all / ai:manage。请求体：`{ messages: [{role, content}], providerId(可选，缺省走默认语言模型), model(可选), knowledgeBaseId(可选，联动检索注入上下文) }`。
- SSE 事件：`delta`（增量文本）、`sources`（知识库召回片段 JSON）、`done`（结束，含 model/promptTokens/completionTokens）、`error`（失败原因）。连接超时 180 秒。

---

## App 端 /api/app

### 个人中心 `/api/app/profile`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| POST `/api/app/profile` | 更新昵称与头像（头像先经 /api/app/files 上传），至少一项；设置即视为用户自定义，后续 QQ 绑定不再覆盖 | 登录 | body: displayName(1-32 字), avatarFileId(0 表示清空头像恢复默认) |
| POST `/api/app/profile/qq` | 绑定 QQ：服务端反查 openId 并校验归属；仅在昵称/头像从未自定义时用 QQ 资料覆盖默认值 | 登录 | body: accessToken, openId(可选，仅用于比对) |
| POST `/api/app/profile/phone` | 手动填写手机号 | 登录 | body: phone |
| GET `/api/app/profile/credentials` | 查询登录账号与密码状态（含 qqBound） | 登录 | 无 |
| POST `/api/app/profile/credentials` | 设置登录账号（用户名）与密码 | 登录 | body: username(4-20 位字母/数字/下划线), password(6-128，受运行时策略约束), currentPassword(已设置密码时必填) |
| POST `/api/app/profile/email` | 绑定校园邮箱 | 登录 | body: email(@stu.mycc.edu.cn/@mycc.edu.cn), code(BIND 验证码) |
| POST `/api/app/profile/phone/wechat` | 用 getPhoneNumber 的 code 换取微信绑定手机号 | 登录 | body: code |

- 返回：`CurrentUserResponse`（结构见通用认证「会话」）。
- `AccountCredentialsResponse`：username, passwordSet, email, qqBound。
- QQ 绑定规则：用户从未主动设置昵称时用 QQ 昵称覆盖默认昵称；从未主动设置头像时用 QQ 头像覆盖默认头像；已自定义的字段保持不变。一个 QQ 只能绑定一个平台账号，当前账号已绑定其他 QQ 时暂不支持更换。

### 服务目录 `/api/app/services`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/services` | 当前校区已上线服务分类及服务项，按排序值升序 | 登录 | 无 |
| GET `/api/app/services/{itemId}` | 服务项详情与动态申请表单定义 | 登录 | 无 |

- `CatalogCategoryResponse`：categoryId, categoryCode, categoryName, icon, description, status, items: `ServiceItemSummaryResponse[]`
- `ServiceItemSummaryResponse`：itemId, itemCode, categoryId, itemName, summary, icon, slaHours
- 详情 → `ServiceItemDetailResponse`：itemId, itemCode, itemName, summary, description, icon, formSchema(JSON 字段定义), slaHours

### 服务申请 `/api/app/service-requests`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| POST `/api/app/service-requests` | 提交服务申请；建议携带 Idempotency-Key 请求头防重复 | 登录+service-request:create | header: Idempotency-Key(可选)；body: itemId, title(默认取服务项名), formData(JSON，按 formSchema) |
| GET `/api/app/service-requests` | 分页查询我的申请，按提交时间倒序 | 登录+service-request:view:self | query: pageNum, pageSize, status: SUBMITTED/PROCESSING/COMPLETED/REJECTED/CANCELLED |
| GET `/api/app/service-requests/{requestId}` | 申请详情（本人可查），含表单、工单、评价与时间线 | 登录+service-request:view:self | 无 |
| POST `/api/app/service-requests/{requestId}/cancel` | 撤销申请（办结前），已派单工单同步撤销 | 登录+service-request:view:self | body: reason |
| POST `/api/app/service-requests/{requestId}/urge` | 催单：站内提醒当前接单师傅尽快处理；单条申请每 2 小时限频 1 次，冷却期内返回 urged=false | 登录+service-request:view:self | 无 |

- 催单返回 `UrgeResponse`：requestId, urged, nextUrgeAvailableSeconds(urged=true 时为 7200，冷却中为剩余秒数), message

- `ServiceRequestSummaryResponse`：requestId, requestNo, title, itemName, status, submittedAt, completedAt, workOrderId, workOrderNo, workOrderStatus, assigneeName（列表页据此显示“待验收”与师傅信息）
- `ServiceRequestDetailResponse`：在概要基础上加 itemIcon, slaHours, formData, rejectReason, cancelReason, workOrder(WorkOrderSummaryResponse), evaluation(EvaluationResponse), timeline: [{action, operatorName, comment, createdAt}]

### 服务评价 `/api/app/service-requests/{requestId}/evaluation`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| POST `/api/app/service-requests/{requestId}/evaluation` | 已办结申请的评价，一次机会，评分 1-5 星 | 登录+evaluation:create | body: rating(1-5), tags(逗号分隔), comment |

- 返回：`EvaluationResponse`：evaluationId, requestId, rating, tags, comment, assigneeName, createdAt

### 我的工单 `/api/app/work-orders`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/work-orders/assigned` | 分页查询指派给当前服务人员的工单 | 登录+work-order:view:assigned | query: pageNum, pageSize, status: PENDING_DISPATCH/ASSIGNED/PROCESSING/COMPLETED/REJECTED/CANCELLED |
| GET `/api/app/work-orders/{orderId}` | 工单详情（仅当前处理人），含申请表单内容 | 登录+work-order:view:assigned | 无 |
| POST `/api/app/work-orders/{orderId}/start` | 开始处理工单 | 登录+work-order:process | 无 |
| POST `/api/app/work-orders/{orderId}/complete` | 填写办理结果并办结，申请同步办结并通知评价 | 登录+work-order:process | body: remark |
| POST `/api/app/work-orders/{orderId}/arrival-photo` | 上传现场照片（拍照打卡） | 登录+work-order:process | body: fileId |
| POST `/api/app/work-orders/{orderId}/completion-photo` | 上传完工照片并提交报修人确认 | 登录+work-order:process | body: fileId, remark |
| POST `/api/app/work-orders/{orderId}/confirm` | 报修人确认工单完成 | 登录+service-request:view:self | 无 |
| POST `/api/app/work-orders/{orderId}/reject-confirmation` | 报修人退回完工确认 | 登录+service-request:view:self | body: reason |

- `WorkOrderSummaryResponse`：orderId, orderNo, requestId, title, status, priority, assigneeName, assigneePhone(师傅电话), createdAt, assignedAt, completedAt, completeRemark, photoWorkflowRequired, confirmationRejectReason, arrivalPhotoUrl, completionPhotoUrl, completionPhotoUrls, slaHours, dueAt, remainingHours, overdue, dueSoon, overdueHours
- `WorkOrderDetailResponse`：在概要基础上加 requestNo, requesterName, requesterPhone, assigneePhone(师傅电话，一键拨打), repairAddress, formData, formSchema, completionSubmittedAt, confirmedAt, startedAt

### 校园社区 `/api/app/community`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/community/sections` | 查询社区分区 | 登录 | 无 |
| GET `/api/app/community/topics/hot` | 查询热门话题 | 登录 | 无 |
| GET `/api/app/community/posts` | 分页查询帖子 | 登录 | query: sectionId, keyword, pageNum(默认1), pageSize(默认10), sort: LATEST(默认)/HOT |
| GET `/api/app/community/posts/{postId}` | 帖子详情与评论 | 登录 | 无 |
| POST `/api/app/community/posts` | 发布帖子（动态/文章） | 登录 | body: sectionId, postType: DYNAMIC(默认)/ARTICLE, title, content, imageFileIds(≤9), topics(≤8), contentBlocks: [{type: TEXT/IMAGE, text, fileId}] |
| POST `/api/app/community/posts/{postId}/comments` | 发表评论（可回复、带图） | 登录 | body: content(≤500), parentCommentId, imageFileIds |
| POST `/api/app/community/posts/{postId}/like` | 点赞/取消点赞帖子 | 登录 | 无 |
| POST `/api/app/community/comments/{commentId}/like` | 点赞/取消点赞评论 | 登录 | 无 |
| POST `/api/app/community/comments/{commentId}/pin` | 置顶/取消置顶作者评论 | 登录 | 无 |
| DELETE `/api/app/community/comments/{commentId}` | 删除自己的评论 | 登录 | 无 |
| DELETE `/api/app/community/posts/{postId}` | 删除作者自己发布的帖子：帖子与全部评论同步逻辑删除，发帖/评论经验按经验可回收原则自动扣减 | 登录 | 无 |
| POST `/api/app/community/reports` | 举报帖子/评论/私信/用户；服务端保存内容快照 | 登录 | body: targetType: POST/COMMENT/MESSAGE/USER, targetId, reasonCode: AD/ABUSE/PORN/POLITICS/PRIVACY/OTHER, reasonDetail |
| POST `/api/app/community/appeals` | 本人申诉：帖子/评论被隐藏或账号被处罚后可提交，进入人工复核 | 登录 | body: targetType: POST/COMMENT/SANCTION, targetId, reasonCode: MISJUDGED/FIXED/ACCOUNT/OTHER(可空), reason(≤500，可空), evidenceFileIds(≤3) |
| GET `/api/app/community/authors/{authorId}` | 查看作者主页（资料+帖子） | 登录 | 无 |
| GET `/api/app/community/messages` | 查询与指定用户的私信 | 登录 | query: withUserId |
| POST `/api/app/community/messages` | 发送私信 | 登录 | body: receiverId, content(≤2000), imageFileIds, referenceType(POST，可空), referenceId(可空) |
| POST `/api/app/community/messages/read` | 标记私信已读 | 登录 | query: withUserId, body: messageIds(可空，缺省整会话) |
| POST `/api/app/community/messages/delivered` | 批量标记送达 | 登录 | body: messageIds |
| GET `/api/app/community/presence` | 批量查询在线状态 | 登录 | query: userIds(逗号分隔) |
| GET `/api/app/community/messages/contacts` | 私信会话列表 | 登录 | 无 |
| GET `/api/app/community/unread-count` | 社区未读数（通知+私信） | 登录 | 无 |

- `CommunitySectionResponse`：sectionId, sectionCode, sectionName, icon, description, bannerTitle, bannerSubtitle, bannerImage
- `CommunityTopicResponse`：name, heat
- `CommunityPostResponse`：postId, sectionId, sectionName, sectionIcon, authorId, authorName, avatarUrl, authorLevel(列表场景 null，个人中心 profile 下发), authorBadgeCode(佩戴勋章编码), postType: DYNAMIC/ARTICLE, title, content, imageUrls, thumbnailUrls, contentBlocks(文章图文块：IMAGE 块带 url/thumbnailUrl 签名地址), topics, commentCount, likeCount, liked, createdAt
- 发帖双类型：
  - **DYNAMIC 动态**（缺省）：`content` 必填 ≤2000 字；`title` 可空（缺省取正文前 40 字）；`imageFileIds` ≤9 张图集。
  - **ARTICLE 文章**：`title` 必填（≤80）；`contentBlocks` 必填，按块顺序渲染，`type=TEXT` 时 `text` 必填（单块 ≤5000、全篇合计 ≤10000），`type=IMAGE` 时 `fileId` 必填（≤9 张、须本人上传）；`content` 仅用于列表/搜索摘要（服务端自动取纯文本，≤2000）。
  - 图片块存储于 `content_blocks`，`image_file_ids` 同步保存去重后的图片列表，兼容旧客户端图集渲染。
- `CommunityCommentResponse`：commentId, parentCommentId, authorId, authorName, avatarUrl, content, imageUrls, thumbnailUrls, likeCount, liked, pinned, author, createdAt
- 帖子详情 → `{ post: CommunityPostResponse, comments: CommunityCommentResponse[] }`
- `CommunityAuthorProfileResponse`：userId, displayName, avatarUrl, level, levelName, totalExp(等级/经验实时重算，与成长档案同口径), wornBadgeCode(佩戴勋章编码，未佩戴 null；作者 showMedals=false 且非本人时为 null), isFollowing(是否已关注该作者，user_follow 实时查询), college/major/className(作者 showAcademicInfo=false 且非本人时置空), gender, posts
- `DirectMessageResponse`：messageId, senderId, receiverId, senderAvatarUrl, content, imageUrls, thumbnailUrls, referenceCard, createdAt
- `DirectMessageReferenceCard`：type(POST), id, title, thumbnail(首图缩略图签名路径，无图 null)；发送时传 referenceType+referenceId 生成快照落库，源帖删除后仍回显
- `DirectConversationResponse`：peerId, peerName, avatarUrl, lastContent, lastAt

### 消息通知 `/api/app/notifications`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/notifications` | 分页查询我的通知，按时间倒序 | 登录 | query: pageNum, pageSize, unreadOnly, communityOnly |
| GET `/api/app/notifications/unread-count` | 未读通知数 | 登录 | query: communityOnly(默认 false) |
| POST `/api/app/notifications/{notificationId}/read` | 标记单条已读 | 登录 | 无 |
| POST `/api/app/notifications/read-all` | 全部标记已读 | 登录 | query: communityOnly(默认 false) |

- 返回：`PageResult<NotificationResponse>` / `NotificationResponse`：notificationId, notificationType, title, content, relatedType, relatedId, read, readAt, createdAt

### 校园公告 `/api/app/announcements`

管理员在 `/api/admin/notices` 以 channel=APP 发布的内容在此消费；与终端大屏免登录轮播隔离，登录态只读。

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/announcements` | 分页查询校园公告：仅 APP 渠道已发布，按发布日期倒序 | 登录 | query: pageNum(默认 1), pageSize(默认 10，上限 50) |

- `AppAnnouncementResponse`：id, title, type(NOTICE/NEWS/ACTIVITY), typeText(通知/要闻/活动), summary, publishDate(yyyy-MM-dd), author(发布方署名)

### 统一收件箱 `/api/app/messages/inbox`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/messages/inbox` | 三路会话聚合的统一收件箱流：流程通知折叠 1 条（置顶，未读数=站内通知未读总数）+ 私信按对端展开 + AI 会话按条展开，按最后消息时间倒序 | 登录 | query: pageNum(默认 1), pageSize(默认 20) |
| GET `/api/app/messages/category-summary` | 消息中心四大分类未读数聚合：流程通知=非社区站内通知；互动=社区通知+私信；公告与 AI 待办暂无已读态恒为 0 | 登录 | 无 |

- `InboxItemResponse`：sessionId（`PROCESS_NOTIFICATION` / `DIRECT_MESSAGE:<peerId>` / `AI_ASSISTANT:<conversationId>`）, sessionType(PROCESS_NOTIFICATION/DIRECT_MESSAGE/AI_ASSISTANT), peerId(仅私信), conversationId(仅 AI), title, avatarUrl(仅私信), lastContent, lastTime(原始时间戳，相对时间前端渲染), unreadCount, pinned
- `CategorySummaryResponse`：processNoticeUnread, interactionUnread, announcementUnread, aiTodoCount, totalUnread
- AI 会话取最近 20 个活跃会话；lastTime 为创建/最后活跃时间，当前 AI 无未读概念（固定 0）

### 社交关系 `/api/app/users`, `/api/app/profile`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/users/{userId}/card` | 用户轻量级身份名片：经验条、关注态、勋章 4 格预览（受对方 showMedals 控制） | 登录 | 无 |
| POST `/api/app/users/{userId}/follow` | 关注用户；不可关注自己，重复关注幂等返回当前态 | 登录 | 无 |
| DELETE `/api/app/users/{userId}/follow` | 取消关注；未关注时幂等 | 登录 | 无 |
| GET `/api/app/profile/stats` | 四维社交统计：累计获赞（帖子+评论，仅 ENABLED 内容）、互关好友、关注数、粉丝数 | 登录 | 无 |
| GET `/api/app/profile/privacy` | 查询隐私设置（缺省全公开） | 登录 | 无 |
| PUT `/api/app/profile/privacy` | 修改隐私设置 | 登录 | body: showAcademicInfo, showMedals |

- 关注/取关返回 `FollowResponse`：targetUserId, following, isMutualFollowing
- 名片 `UserCardResponse`：userId, uid, displayName, avatarUrl, gender, identityType, level, levelName, currentExp, nextLevelExp, isFollowing, isMutualFollowing, unlockedBadgeCount, totalBadgeCount, previewBadges: [{badgeCode, name, iconUrl}]
- 统计 `SocialStatsResponse`：likesCount, mutualFollowCount, followingCount, followersCount
- 隐私 `PrivacySettingsResponse`：showAcademicInfo（false 时他人主页学业字段置空）, showMedals（false 时不展示勋章）

### 我的设备 `/api/app/devices`

登录成功按 UA+客户端类型生成稳定设备指纹登记（同设备再登录换绑会话）；踢线=删除对应会话。

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/devices` | 在线设备列表，isCurrent 标识当前请求设备 | 登录 | 无 |
| DELETE `/api/app/devices/{deviceId}` | 踢除设备：该设备会话立即失效；当前设备不允许在此下线（走 /logout） | 登录 | 无 |
| POST `/api/app/devices/logout-others` | 一键退出其他所有设备 | 登录 | 无 |

- `DeviceResponse`：deviceId, deviceName, osType, osVersion, ipAddress, isCurrent, lastActiveAt
- 退出其他设备返回 `{ revokedCount: n }`

### 首页聚合 `/api/app/home`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/home/dashboard` | 首页综合仪表盘（BFF）：问候语、学业信息条（校区/星期/周次）、下一节课（ONGOING/UPCOMING）、最新 APP 公告、最紧迫进行中申请、未读总数；单项失败各自兜底 | 登录 | 无 |

- `HomeDashboardResponse`：greeting, academicInfo{campusName, weekDay, academicWeek, formattedDate}, nextSchedule{courseName, teacher, location, startSection, endSection, timeRange, status}(可 null), announcement{id, title, tag, url}(可 null), inProgressRequest{requestId, orderNo, itemName, currentStatus, submittedAt}(可 null), unreadMessageCount

### AI 助手 `/api/app/ai`（以下均需权限 ai:chat）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| POST `/api/app/ai/conversations` | 创建 AI 会话 | 登录+ai:chat | body: title（可空，默认「新对话」） |
| GET `/api/app/ai/conversations` | 分页查询我的会话，按最近活跃倒序 | 登录+ai:chat | query: pageNum, pageSize |
| GET `/api/app/ai/conversations/{conversationId}/messages` | 会话消息（仅属主），按时间正序 | 登录+ai:chat | 无 |
| POST `/api/app/ai/conversations/{conversationId}/messages` | 发送消息并返回助手回复；AI 不可用时降级回复 | 登录+ai:chat | body: content(≤2000，带附件时可空), attachments: [{type: IMAGE/DOCUMENT, fileId}](IMAGE≤4、DOCUMENT≤2)；旧 images: Long[] 仍兼容，与 attachments 同传时以 attachments 为准 |
| POST `/api/app/ai/speech/transcribe` | 语音转写（mp3/aac/m4a/wav） | 登录+ai:chat | body: fileId |
| POST `/api/app/ai/conversations/{conversationId}/chat-stream` | App 端 SSE 流式对话：delta=增量文本，done=结束（含完整消息与 actions 卡片），error=失败原因 | 登录+ai:chat | body 同普通发送：content, attachments |
| GET `/api/app/ai/recommendations` | 空态 2×2 场景推荐（查课表/查进度/办事指引/校园生活） | 登录+ai:chat | 无 |

- `AiConversationResponse`：conversationId, title, createdAt
- `AiMessageResponse`：messageId, role: USER/ASSISTANT, content, images: Long[], degraded(降级标记), actions: [{type: SERVICE_APPLY/REQUEST_DETAIL/REQUEST_LIST/SCHEDULE, label, itemCode, itemId, url}], suggestions(服务端暂固定 null，前端预留), sources: [{fileName, page, titlePath, score, snippet}](知识库引用，可空), createdAt
- App 对话会自动检索 `enabled + assistantEnabled` 的知识库（不只 PUBLIC），按库的 topK/阈值/token 预算合并；回答带 `sources` 引用，无命中时提示未找到、不编造。
- DOCUMENT 附件服务端读取文本（pdf/docx/txt/md，单份截断 8000 字）注入提示词；解析失败降级忽略该文档不阻断对话

### 成就勋章 `/api/app/achievements`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/achievements/catalog` | 勋章字典（方案B热更新）：客户端传本地 catalogVersion；版本一致时 changed=false 且不下发列表；变化时下发启用分类与勋章字典，客户端保存本地 | 登录 | query: version(可空) |
| GET `/api/app/achievements/state` | 徽章用户态（高频轻量）：先懒评估，返回 catalogVersion、wornBadgeCode、已激活勋章、全部启用勋章进度 | 登录 | 无 |
| GET `/api/app/achievements/profile` | 成长档案：打开即先懒评估，返回等级/累计经验/进度/已获勋章数与最近 3 枚（仅统计启用勋章） | 登录 | 无 |
| GET `/api/app/achievements/badges` | 勋章墙（兼容旧版，deprecated）：全部启用勋章 + 是否解锁 + 进度；响应含 catalogVersion（badgeVersion 已废弃） | 登录 | 无 |
| POST `/api/app/achievements/wear` | 佩戴/卸下勋章：action=WEAR/UNWEAR；缺省时 badgeCode 非空=佩戴、为空=卸下（兼容旧客户端）；佩戴须已解锁且勋章启用 | 登录 | body: badgeCode(可空), action(可空) |

- `AchievementCatalogResponse`：catalogVersion(16 位 hex，内容哈希派生), changed, categories: [{categoryCode, categoryName, sortOrder}], badges: [{badgeCode, badgeName, categoryCode, rarity, description, metricThreshold, rewardExp, sortOrder, iconUrl}]
- `AchievementStateResponse`：catalogVersion, wornBadgeCode, activated: [{badgeCode, unlockedAt}], progress: [{badgeCode, currentProgress}]
- 字典版本由「分类 + 勋章全量内容（含启用态/图标）」哈希派生，管理端任何增删改/启停都会变化；客户端按版本缓存到本地，离线沿用旧缓存。
- 图标：内置 19 枚由客户端本地 `assets/images/badges/$badgeCode.png` 匹配（接口 iconUrl=null）；新增码由管理端上传图标，catalog 下发 `/api/common/assets/badges/{code}.{ext}?v={catalogVersion}`，客户端下载/缓存，缺失时显示占位图。
- `AchievementProfileResponse`：level, levelName, totalExp, nextLevel, nextLevelName, nextLevelExp, expToNext, progressPercent, badgeCount, totalBadgeCount, wornBadgeCode, recentBadges: [{badgeCode, badgeName, obtainedAt}]
- `AchievementBadgeItem`（旧接口）：badgeCode, badgeName, category(COMMUNITY/SERVICE/WORKER/JW/GROWTH), rarity(COMMON/RARE/EPIC/LEGENDARY), description, metricThreshold, rewardExp, unlocked, unlockedAt, currentProgress

### 课表 `/api/app/schedule`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| POST `/api/app/schedule/import` | 从教务系统拉取个人课表并保存 | 登录 | query: semesterCode(可选) |
| GET `/api/app/schedule/view` | 查询已导入课表；不传学期返回最近同步学期 | 登录 | query: semesterCode(可选) |
| GET `/api/app/schedule/view-week` | 按周展开课表：按 weeks/weekParity 过滤当前周，返回学校原始节次时间与总周数；个人课表缺失时回落班级课表缓存；超出校历周数明确报错，不静默截回旧周 | 登录 | query: semesterCode(可选), week(可选，缺省当前周) |
| GET `/api/app/schedule/next-course` | 当前/下一节课程（首页 BFF） | 登录 | 无 |
| GET `/api/app/schedule/adjustments` | 调课通知列表（补课/停课/调课），按发布时间倒序；缺省返回最新有公告学期 | 登录 | query: semesterCode(可选) |

- `ScheduleImportResponse`：semesterCode, semesterName, className, personalCount, classCacheCount
- `ScheduleViewResponse`：semesterCode, semesterName, className, courses: [{courseName, teacher, location, dayOfWeek, startSection, endSection, weeks, weekParity, timeRange}], availableSemesters: [{semesterCode, semesterName}], firstMonday, totalWeeks
- `ScheduleWeekViewResponse`：semesterCode, semesterName, week, weekStart, currentWeek, totalWeeks, days: [{dayOfWeek: 1-7, date, courses: [{courseName, teacher, location, startSection, endSection, timeRange}]}]（days 固定 7 项周一~周日顺序；原始数据支持第12节）
- `ScheduleNextCourseResponse`：hasCourse, current(可 null), next(可 null, 可为后续上课日), week, date；CourseView：courseName, teacher, location, dayOfWeek, startSection, endSection, timeRange
- `ScheduleAdjustmentItem`：id, semesterCode, noticeTime, actionType(补课/停课/调课), courseCode, courseName, classNo, targetClasses: String[], summary（raw_text 不出接口）

### 学业成绩 `/api/app/grades`

基于教务托管会话实时读取「学业成绩」页（`xscj.stuckcj.jsp` + `stuckcj_data.jsp`），按学期分组返回；
服务端 5 分钟内缓存重复请求。教务会话失效返回 `JW_401_02`（同时把绑定状态翻为 EXPIRED，需重新认证）。

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/grades` | 学业成绩报表 | 登录 | query: range=ALL(默认)/YEAR/TERM, scoreType=EFFECTIVE(默认)/RAW, xn(学年起始年，YEAR/TERM 用), xq(学期下拉值，TERM 用) |

- `GradeReportResponse`：range, scoreType, terms: [{termName, courseCount, credits, courses: [...]}], summary
- `Course`：sequence, courseCode, courseName, credit, hours, category, courseType(修读性质), examType(考核方式), score(字符串，兼容等级制), earnedCredits, gradePoint, creditGradePoint, remark(补考/重修等), passed(获得学分>0)
- `Summary`：courseCount, credits, earnedCredits, earnedGradePoints, earnedCreditGradePoints, averageGradePoint(GPA), averageScore(平均成绩), weightedAverageScore(加权平均成绩)
- 有效成绩（EFFECTIVE）过滤补考原始记录；原始成绩（RAW）保留。实测本账号有效 18 门 / 原始 19 门。

### 字典 `/api/app/dicts`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/dicts/{typeCode}` | 按字典编码读取启用项（类型停用/不存在返回空数组），按 sortOrder 升序 | 登录 | 路径参数 typeCode |

- 返回 `DictItemResponse[]`，结构同管理端；`extra` 为扩展 JSON 原文（如报修类型的 `{"emergency":true}`）。

### 教务认证 `/api/app/jw`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/jw/captcha` | 获取算术题验证码（base64 图片） | 登录 | 无 |
| POST `/api/app/jw/auth` | 教务认证；成功后按教务身份自动切换平台身份 | 登录 | body: ssoAccount, password, captchaUid, captchaCode |
| GET `/api/app/jw/status` | 认证状态：UNBOUND/AUTHENTICATED/EXPIRED | 登录 | 无 |
| POST `/api/app/jw/unbind` | 解绑并销毁托管教务会话 | 登录 | 无 |
| GET `/api/app/jw/profile` | 实时拉取教务个人信息 | 登录 | 无 |

- `JwCaptchaResponse`：uid, imageBase64
- `JwAuthResult`：status(固定 AUTHENTICATED), ssoAccount, jwName, jwStudentNo, identityKind: STUDENT/TEACHER, identityChanged
- `JwStatusResponse`：status: UNBOUND/AUTHENTICATED/EXPIRED, ssoAccount, jwName, identityKind, grade, college, major, className, lastAuthAt, sessionExpiresAt
- `JwProfileResponse`：ssoAccount, jwName, jwStudentNo, identityKind, grade, college, major, className

### 文件上传 `/api/app/files`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| POST `/api/app/files` | 上传图片（jpg/png/webp），返回文件 ID 与签名短时访问路径 | 登录 | multipart: file |
| POST `/api/app/files/audio` | 上传音频（mp3/aac/m4a/wav/ogg），供 AI 转写 | 登录 | multipart: file |
| GET `/api/app/files/view-url` | 按文件 ID 换取签名短时访问路径 | 登录 | query: fileId |

- 返回：`StoredFile`：{ fileId, viewPath, fileName }；view-url → 签名路径字符串。

---

## 通用认证 /api/common

### 登录 `/api/common/auth`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/common/auth/captcha` | 获取登录图形验证码（仅 `password-login-enabled=true` 时装配）；`enabled=false` 时客户端不展示验证码 | 无需登录 | 无 |
| POST `/api/common/auth/login` | 开发环境密码登录（生产关闭，password-login-enabled=true 才装配）；开启验证码后必须携带 | 无需登录 | body: username, password, captchaId(可选), captchaCode(可选) |
| POST `/api/common/auth/wechat-login` | 微信登录，code 为 wx.login() 凭证；首登自动建号 | 无需登录 | body: code |
| POST `/api/common/auth/qq-login` | QQ 登录，服务端用 accessToken 反查 openId，并用 APP ID 校验 token 归属后建立身份 | 无需登录 | body: accessToken, openId(可选，仅用于比对) |
| POST `/api/common/auth/dev-wechat-login` | 开发环境固定账号登录，首登自动建号 | 无需登录 | 无 |
| POST `/api/common/auth/email-code` | 发送邮箱验证码，真实 SMTP 发信；dev/test 未配置 SMTP 时仅在响应返回 devCode，生产未配置返回 `AUTH_503_03` | 无需登录 | body: email, purpose: REGISTER/RESET/BIND |
| POST `/api/common/auth/register` | 邮箱验证码注册；昵称默认 `用户` + 6 位数字，头像为空（客户端显示默认头像） | 无需登录 | body: accountId(4-20 位字母/数字/下划线), email, password(6-128，受运行时策略约束), code |
| POST `/api/common/auth/password/reset` | 通过 RESET 邮箱验证码重置密码 | 无需登录 | body: accountId, email, newPassword(6-128，受运行时策略约束), code |
| GET `/api/common/auth/me` | 查询当前用户、固定身份、角色与权限 | 需登录 | 无 |
| POST `/api/common/auth/logout` | 使当前 Bearer Token 立即失效 | 需登录 | 无 |

- `LoginResponse`：accessToken(不透明令牌), expiresAt, isNewUser, user(CurrentUserResponse)
- `CurrentUserResponse`：userId, campusId, defaultOrganizationId, username, displayName, identityType: STUDENT/TEACHER/LOGISTICS/ADMIN, authorizationStatus: BASIC/PENDING/AUTHORIZED/REJECTED, roles: String[], roleScopes: [{roleCode, organizationId, dataScope}], permissions: String[], avatarPath, phone, growth: GrowthSummary（`GET /auth/me` 下发；登录响应中为 null）
  - `avatarPath`：本地上传头像返回带签名的短时 URL；QQ 登录/绑定返回 QQ 头像地址；为空时客户端显示默认渐变头像。QQ 移动 SDK 不提供数字 QQ 号，`username` 为服务端生成的 `qq_<openid 片段>` 平台账号。
  - 昵称：QQ 首次登录默认使用 QQ 昵称，无昵称时回退 `QQ用户` + 4 位 openid 片段；邮箱注册默认 `用户` + 6 位数字。用户主动修改昵称/头像后，QQ 登录不再覆盖。
- QQ 登录补充：
  - real 模式需配置 `QQ_CLIENT_ID`（QQ 互联 APP ID）；缺配置返回 `AUTH_503_02`，不会回退 mock。
  - 服务端先调 `graph.qq.com/oauth2.0/me` 取 openid/unionid；优先比对响应中的 `client_id` 与 APP ID，缺失时再用 `APP ID + openid + accessToken` 调 `get_user_info` 兜底；任何一步失败返回 `AUTH_401_03`。
  - `get_user_info` 同时读取 `nickname` 和 `figureurl_qq_2`（回退 `figureurl_qq_1` 等）作为默认昵称/头像；`client_id` 已校验时资料读取失败只告警，不阻断登录。
  - `QQ_CLIENT_SECRET` 不参与移动端 accessToken 反查，也不得下发客户端；仅为未来网站授权码流程预留。
  - 当前只支持移动 SDK 的 accessToken 模式，不支持网站/H5 的 authorization_code 回调。
- 邮箱验证码补充：SMTP 配置见 [校园邮箱验证码配置说明](../方案/校园邮箱验证码配置说明.md)。生产环境 `MAIL_ENABLED=false` 或缺少 SMTP 时接口明确返回 503，不会伪报“已发送”；验证码不入日志、10 分钟有效。
- **SYS03 安全运行时（2026-10-21）**：
  - `GET /api/common/auth/captcha` 返回 `{enabled, captchaId, image, expiresInSeconds}`；`image` 为 SVG data URI，答案不入响应；一次性、120 秒有效；校验失败返回 `AUTH_401_04`。
  - 密码策略从 `sys_config` 运行时读取：`security.password_min_length`（6–64 钳制，默认 8）、`security.password_require_complex`（默认 true，要求字母+数字），DTO 上限统一 128。
  - 平台端会话 TTL 读取 `security.session_ttl_hours`（1–168，默认 8）；App 端仍用 `studenthub.security.app-session-ttl`（默认 30 天）。
  - `security.max_devices_per_user`（0–20，0 不限，默认 5）在登录成功后踢除最旧设备会话。
  - `security.cors_allowed_origins`（逗号分隔）非空时覆盖环境变量 `ALLOWED_ORIGINS`，支持 `*`，每次请求动态生效。
- `GrowthSummary`：uid(教务学号/工号，未认证 null), level(1-9), levelName, currentExp, nextLevelExp(满级 null), expToNext(满级 null), wornBadgeCode(未佩戴 null)；等级经验实时重算

### 系统 `/api/common/system`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/common/system/health` | 应用健康状态 | 无需登录 | 无 |

- 返回：`{ serviceName: "student-hub-api", status: "UP" }`

### 文件访问 `/api/common/files`（签名 URL 匿名访问）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/common/files/view/{fileId}` | 短时 HMAC 签名校验通过后返回文件内容，链接本身即凭证 | 无需登录（签名校验） | query: expires, sign, thumb(默认 false，图片可缩略至最长边 480) |

- 返回：文件二进制流（非 Result 包装），Cache-Control: private, max-age=1800。

### 静态素材 `/api/common/assets`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/common/assets/{assetPath}` | 读取 MinIO 中 static/assets/ 下的小程序静态素材 | 无需登录 | 无 |

- 返回：文件二进制流（非 Result 包装），浏览器缓存 7 天。

---

## 大屏公开 /api/public

### 数据大屏 `/api/public/screen`（全部无需登录，按 IP 限流：普通查询 60/窗口、AI 问答与语音 20/窗口）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/public/screen/overview` | 运营总览（脱敏统计，不含个人身份数据） | 无需登录 | 无 |
| GET `/api/public/screen/catalog` | 公开服务目录（分类+事项，不含表单模板） | 无需登录 | 无 |
| GET `/api/public/screen/track/{no}` | 凭 WO-/SR- 单号查询公开办理时间线 | 无需登录 | 无 |
| GET `/api/public/screen/notices` | 信息要闻（通知/要闻/活动，供终端轮播） | 无需登录 | 无 |
| POST `/api/public/screen/chat` | 公开 AI 问答，基于公开办事指南 | 无需登录 | body: question(≤500), history: [{role, content}](≤6) |
| POST `/api/public/screen/chat/audio` | 公开语音问答：音频转写为文字，前端带文字提问 | 无需登录 | multipart: file |

- `ScreenOverviewResponse`：generatedAt, users: {total, students, todayNew}, requests: {total, today, inProgress, statusDist}, orders: {total, completed, processing, completedToday}, trend: [{date, weekday, submitted, completed}], topServices: [{itemName, count}], community: {posts, comments, likes, messages}, ai: {conversations, answers, todayAnswers}, recentCompleted: [{itemName, completedAt}]
- `ScreenCatalogResponse`：categories: [{id, name, icon, description, items: [{id, itemCode, name, icon, summary, description, slaHours}]}]
- `ScreenOrderProgressResponse`：orderNo, requestNo, itemName, status, statusText, submittedAt, timeline: [{time, actionText, note}]
- `ScreenNoticeResponse`：notices: [{type: NOTICE/NEWS/ACTIVITY, typeText, title, summary, publishDate}]
- 问答 → `PublicChatResponse`：{ answer, model }；语音 → `{ text }`

### App 版本检查 `/api/public/app-version`（无需登录）

| 方法 路径 | 说明 | 请求 |
|---|---|---|
| GET `/api/public/app-version/latest` | App 启动检查更新 | query: platform(必填 ANDROID/IOS/HARMONY), channel(默认 OFFICIAL), versionCode(客户端当前版本号) |
| GET `/api/public/app-version/{id}/package` | 下载已发布安装包（流式，累加 downloadCount；仅 PUBLISHED） | 无 |

- `AppVersionCheckResponse`：hasUpdate, forceUpdate, currentVersionCode, latest: {id, versionCode, versionName, minSupportedVersionCode, forceUpdate, updateLog, downloadUrl, packageSize, packageSha256, publishedAt}；无发布版本时 latest=null
- `hasUpdate = latest.versionCode > versionCode`；`forceUpdate = latest.forceUpdate || versionCode < latest.minSupportedVersionCode`
- `downloadUrl`：有上传包时为 `/api/public/app-version/{id}/package`，否则为配置的外部 URL（iOS App Store / CDN）。

### 公开知识库 `/api/public/knowledge-bases`（无需登录）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| POST `/api/public/knowledge-bases/{id}/query` | 查询 visibility=PUBLIC 且启用的知识库，混合检索并生成回答 | 无需登录 | body: question(≤2000), generateAnswer(默认 true) |

- 返回：`RagTestResponse`（结构见管理端 RAG 知识库）。

---

## 开发调试 /api/dev（仅 dev Profile 装配）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/dev/jw/probe` | 用托管教务会话探测任意教务相对路径，返回状态/跳转/响应片段 | 需登录 | query: path |
| POST `/api/dev/jw/probe-post` | 同上，POST 方式 | 需登录 | query: path, body(可选表单串) |
| GET `/api/dev/schedule/raw` | 拉取教务课表原始页面，确认端点与解析 | 需登录 | 无 |
| POST `/api/dev/schedule-sync/run` | 手动触发一次课表同步 | 无需登录（dev 白名单外但方法开放） | 无 |

- probe 返回：`{ status, location, contentType, size, preview }`（无托管会话时 code=DEV_NO_SESSION）。

---

## 特殊接口说明

### 文件上传（multipart/form-data）

- `POST /api/app/files` — 图片上传，form 字段 `file`。
- `POST /api/app/files/audio` — 音频上传，form 字段 `file`。
- `POST /api/admin/knowledge-bases/{id}/documents` — 知识库文档上传，form 字段 `file`。
- `POST /api/public/screen/chat/audio` — 大屏语音上传，form 字段 `file`。

上传类返回 `Result<StoredFile>`：`{ fileId, viewPath, fileName }`；viewPath 为带签名的短时访问路径。

### SSE 流式

- `POST /api/admin/ai/chat-stream`（Content-Type: text/event-stream）— 管理端流式对话测试。事件流：`delta` → `sources`（启用知识库时）→ `done`/`error`。响应非 Result 包装。

### 文件直读（非 Result 包装）

- `GET /api/common/files/view/{fileId}?expires=&sign=&thumb=` — 签名文件内容，支持缩略图。
- `GET /api/common/assets/{assetPath}` — 静态素材直读。

### WebSocket

- `ws://<host>/ws/community?token=<登录 token>` — 社区实时消息推送（CommunityWebSocketConfig 注册，全源放行），路径在登录白名单内。
- 连接成功后，服务端按 userId 推送私信 JSON：`{messageId, senderId, receiverId, content, imageUrls, createdAt}`；
- 客户端上行消息仍走 `POST /api/app/community/messages`，WS 当前只负责服务端推送；断线重连、去重由前端处理。

---

## 错误码速查表

来源：`common/ErrorCode.java`。HTTP 状态与 code 前缀对应；响应结构 `{ success: false, code, message, data: null }`。

| 枚举名 | code | message | HTTP |
|---|---|---|---|
| INVALID_PARAMETER | COMMON_400 | 请求参数不合法 | 400 |
| UNAUTHORIZED | AUTH_401 | 请先完成身份认证 | 401 |
| CAPTCHA_INVALID | AUTH_401_04 | 验证码错误或已过期，请刷新后重试 | 401 |
| INVALID_CREDENTIALS | AUTH_401_01 | 用户名或密码错误 | 401 |
| WECHAT_LOGIN_FAILED | AUTH_401_02 | 微信登录失败，请稍后重试 | 401 |
| WECHAT_NOT_CONFIGURED | AUTH_503_01 | 微信登录服务尚未配置 | 503 |
| QQ_LOGIN_FAILED | AUTH_401_03 | QQ 登录失败，请稍后重试 | 401 |
| QQ_NOT_CONFIGURED | AUTH_503_02 | QQ 登录服务尚未配置 | 503 |
| EMAIL_NOT_CONFIGURED | AUTH_503_03 | 邮箱服务尚未配置，请联系管理员 | 503 |
| EMAIL_SEND_FAILED | AUTH_502_01 | 验证码发送失败，请稍后重试 | 502 |
| ACCOUNT_DISABLED | AUTH_403_01 | 账号已停用 | 403 |
| FORBIDDEN | AUTH_403 | 没有执行该操作的权限 | 403 |
| NOT_FOUND | COMMON_404 | 资源不存在 | 404 |
| AI_NOT_CONFIGURED | AI_503 | AI 服务尚未配置 | 503 |
| AI_PROVIDER_ERROR | AI_502 | AI 服务暂时不可用 | 502 |
| ROLE_NOT_CONFIGURED | AUTH_500_01 | 默认角色未配置，请联系管理员 | 500 |
| CATALOG_ITEM_NOT_FOUND | CATALOG_404_01 | 服务项不存在或已停用 | 404 |
| CATALOG_CATEGORY_NOT_FOUND | CATALOG_404_02 | 服务分类不存在 | 404 |
| REQUEST_NOT_FOUND | REQUEST_404_01 | 申请不存在 | 404 |
| REQUEST_STATE_INVALID | REQUEST_409_01 | 当前状态不允许该操作 | 409 |
| REQUEST_DUPLICATE | REQUEST_409_02 | 请勿重复提交 | 409 |
| ORDER_NOT_FOUND | ORDER_404_01 | 工单不存在 | 404 |
| ORDER_STATE_INVALID | ORDER_409_01 | 工单当前状态不允许该操作 | 409 |
| ORDER_NOT_ASSIGNED_TO_CURRENT_USER | ORDER_403_01 | 该工单未指派给你 | 403 |
| EVALUATION_DUPLICATE | EVALUATION_409_01 | 该申请已评价过 | 409 |
| NOTIFICATION_NOT_FOUND | NOTIFICATION_404_01 | 通知不存在 | 404 |
| CONVERSATION_NOT_FOUND | AI_404_01 | 会话不存在 | 404 |
| AI_RATE_LIMITED | AI_429_01 | 提问太频繁，请稍后再试 | 429 |
| JW_AUTH_FAILED | JW_401_01 | 账号、密码或验证码错误 | 401 |
| JW_SESSION_EXPIRED | JW_401_02 | 教务认证已失效，请重新认证 | 401 |
| JW_ACCOUNT_ALREADY_BOUND | JW_409_01 | 该账号已被绑定到其他用户 | 409 |
| JW_RATE_LIMITED | JW_429_01 | 操作过于频繁，请稍后再试 | 429 |
| PHONE_EXCHANGE_FAILED | PHONE_400_01 | 手机号获取失败，请手动填写 | 400 |
| PUBLIC_RATE_LIMITED | PUBLIC_429 | 访问过于频繁，请稍后再试 | 429 |
| AD_EVENT_RATE_LIMITED | AD_429_01 | 广告事件上报过于频繁，请稍后再试 | 429 |
| AD_ATTRIBUTION_INVALID | AD_400_01 | 点击归因无效：缺少同广告的有效曝光或已过期 | 400 |
| COMMUNITY_REPORT_DUPLICATE | COMMUNITY_409_01 | 你已举报过该内容，正在处理中 | 409 |
| COMMUNITY_REPORT_HANDLED | COMMUNITY_409_02 | 该举报已处理 | 409 |
| COMMUNITY_REPORT_TARGET_INVALID | COMMUNITY_400_01 | 举报对象不存在或不可举报 | 400 |
| COMMUNITY_REPORT_NOT_FOUND | COMMUNITY_404_01 | 举报记录不存在 | 404 |
| COMMUNITY_MUTED | COMMUNITY_403_01 | 你已被限制发言，暂时无法发布内容 | 403 |
| COMMUNITY_REPORT_RATE_LIMITED | COMMUNITY_429_01 | 举报过于频繁，请稍后再试 | 429 |
| INTERNAL_ERROR | COMMON_500 | 系统繁忙，请稍后重试 | 500 |

| GET `/api/app/electricity` | 宿舍电费查询（iyunmu 云平台缓存值） | 登录 | query: sn（YM 开头设备号） |
