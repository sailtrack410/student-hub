# student-hub HTTP 接口参考

本文档由源码逐文件盘点生成，覆盖全部 35 个 Controller。生成日期：2026-10-17。

## 全局约定

- **统一响应**：所有 JSON 接口返回 `Result<T>`：`{ success: boolean, code: string, message: string, data: T, timestamp: string }`，成功固定 `code="OK"`；下文各接口只描述 `data` 部分。
- **分页**：分页接口返回 `PageResult<T>`：`{ pageNum, pageSize, total, records: T[] }`，pageNum 从 1 开始。
- **认证**：除「白名单（无需登录）」接口外，均需请求头 `Authorization: Bearer <accessToken>`（不透明令牌，登录接口下发）。401/403 与业务错误码见文末速查表。
- **ID 序列化**：数字型 ID（Long）返回为字符串（JacksonConfig 全局 Long→String，防 JS 精度丢失），请求中数字 ID 仍可传数字或字符串。
- **角色/权限**：App 端部分接口有 `@PreAuthorize` 权限点要求，已在鉴权列标注；无标注的管理端接口默认登录即可，标注了权限点的需相应权限。
- **通用分页参数**：`pageNum`（默认 1）、`pageSize`（各接口上限不同，默认 10）。

### 白名单（无需登录）路径

`/api/common/system/health`、`/api/common/auth/login`、`/api/common/auth/wechat-login`、`/api/common/auth/qq-login`、`/api/common/auth/dev-wechat-login`、`/api/common/files/view/**`、`/api/common/assets/**`、`/api/public/screen/**`、`/api/public/knowledge-bases/**`、`/ws/**`，以及 Swagger/静态页（`/doc.html`、`/v3/api-docs/**`、`/swagger-ui/**` 等）。

---

## 管理端 /api/admin

### 用户管理 `/api/admin/users`（需权限 system:user:manage）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/users` | 分页查询用户，支持关键词/状态/身份/授权状态/角色过滤 | 登录+system:user:manage | query: pageNum, pageSize, keyword, status: ENABLED/DISABLED, identityType: STUDENT/TEACHER/LOGISTICS/ADMIN, authorizationStatus: BASIC/PENDING/AUTHORIZED/REJECTED, roleId |
| POST `/api/admin/users/{userId}/status` | 启用/停用账号，停用后在线会话立即失效，不能操作自己 | 同上 | body: status: ENABLED/DISABLED |
| POST `/api/admin/users/{userId}/authorization` | 授权审核，APPROVE 置已授权 / REJECT 置已拒绝 | 同上 | body: action: APPROVE/REJECT, reason |
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

### 工作台 `/api/admin/dashboard`（需权限 platform:all / system:user:manage / work-order:dispatch 其一）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/dashboard` | 聚合数据：KPI、近 7 天工单趋势、分类占比、最新工单 | 登录+上述权限之一 | 无 |

- 返回：`DashboardResponse`：pendingWorkOrders, pendingDeltaPercent, todayNewUsers, todayNewUsersDeltaPercent, todayAiSessions, todayAiSessionsDeltaPercent, pendingRequests, pendingRequestsDelta, weekNewWorkOrders, weekResolvedWorkOrders, resolveRatePercent, dailyAvgNew, trend: [{day, created, resolved}], categories: [{name, percent}], latestOrders: [{orderId, orderNo, title, status, createdAt}]

### AI 全局设置 `/api/admin/ai/config`（需权限 ai:manage）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/ai/config` | 查询 AI 全局设置 | 登录+ai:manage | 无 |
| PUT `/api/admin/ai/config` | 保存设置，进程内即时生效 | 同上 | body: enabled, connectTimeoutSeconds(1-120), readTimeoutSeconds(1-600), maxInputCharacters, hourlyMessageLimit, historyWindow(0-100), maxMessageCharacters |

- 返回：`AiConfigResponse`：enabled, connectTimeoutSeconds, readTimeoutSeconds, maxInputCharacters, hourlyMessageLimit, historyWindow, maxMessageCharacters, activeChatModel(默认聊天模型，未配置为 null)

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
| PUT `/api/admin/ai/models/{modelId}` | 更新模型启用状态或类型 | 同上 | body: enabled, modelType: CHAT/VISION/ASR/EMBEDDING |
| DELETE `/api/admin/ai/models/{modelId}` | 删除模型，若为类型默认一并清除 | 同上 | 无 |
| GET `/api/admin/ai/defaults` | 查询 CHAT/VISION/ASR 类型默认模型 | 同上 | 无 |
| PUT `/api/admin/ai/defaults` | 设置类型默认模型，modelId 为空表示清除 | 同上 | body: modelId, typeCode: CHAT/VISION/ASR/EMBEDDING |

- `AiProviderResponse`：providerId, providerCode, providerName, icon, baseUrl, protocol, streaming, apiKeyConfigured, enabled, lastCheckAt, lastCheckOk, lastCheckResult, modelCount
- `AiModelResponse`：modelId, providerId, providerName, providerModelId, modelType, enabled, capabilities
- `AiDefaultModelResponse`：typeCode, typeName, model(AiModelResponse)
- 连通性检查 → `AiProviderCheckResponse`：ok, result, checkedAt；导入 → 新增数量 Integer

### RAG 知识库 `/api/admin/knowledge-bases`（需权限 ai:manage）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/admin/knowledge-bases` | 知识库列表 | 登录+ai:manage | 无 |
| POST `/api/admin/knowledge-bases` | 创建知识库 | 同上 | body: name, description, visibility: INTERNAL/PUBLIC, enabled, chunkSize(300-3000), chunkOverlap(0-600), topK(1-20), scoreThreshold(0-1), systemPrompt |
| PUT `/api/admin/knowledge-bases/{id}` | 更新知识库 | 同上 | body: 同创建 |
| DELETE `/api/admin/knowledge-bases/{id}` | 删除知识库 | 同上 | 无 |
| GET `/api/admin/knowledge-bases/{id}/documents` | 文档列表 | 同上 | 无 |
| POST `/api/admin/knowledge-bases/{id}/documents` | 上传并索引文档（multipart，form 字段 file） | 同上 | multipart: file |
| POST `/api/admin/knowledge-bases/{id}/documents/{docId}/reindex` | 重建文档索引 | 同上 | 无 |
| DELETE `/api/admin/knowledge-bases/{id}/documents/{docId}` | 删除文档 | 同上 | 无 |
| POST `/api/admin/knowledge-bases/{id}/test` | 混合检索测试 | 同上 | body: question, generateAnswer(默认 true) |

- `KnowledgeBaseResponse`：id, name, description, visibility, enabled, chunkSize, chunkOverlap, topK, scoreThreshold, systemPrompt, documentCount, chunkCount, createdAt, updatedAt
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
| POST `/api/app/profile` | 更新昵称与头像（头像先经 /api/app/files 上传），至少一项 | 登录 | body: displayName(1-32 字), avatarFileId |
| POST `/api/app/profile/phone` | 手动填写手机号 | 登录 | body: phone |
| POST `/api/app/profile/phone/wechat` | 用 getPhoneNumber 的 code 换取微信绑定手机号 | 登录 | body: code |

- 返回：`CurrentUserResponse`（结构见通用认证「会话」）。

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

- `ServiceRequestSummaryResponse`：requestId, requestNo, title, itemName, status, submittedAt, completedAt
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

- `WorkOrderSummaryResponse`：orderId, orderNo, requestId, title, status, priority, assigneeName, createdAt, assignedAt, completedAt, completeRemark, photoWorkflowRequired, confirmationRejectReason, arrivalPhotoUrl, completionPhotoUrl, completionPhotoUrls
- `WorkOrderDetailResponse`：在概要基础上加 requestNo, requesterName, requesterPhone, repairAddress, formData, formSchema, completionSubmittedAt, confirmedAt, startedAt

### 校园社区 `/api/app/community`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/community/sections` | 查询社区分区 | 登录 | 无 |
| GET `/api/app/community/topics/hot` | 查询热门话题 | 登录 | 无 |
| GET `/api/app/community/posts` | 分页查询帖子 | 登录 | query: sectionId, keyword, pageNum(默认1), pageSize(默认10), sort: LATEST(默认)/HOT |
| GET `/api/app/community/posts/{postId}` | 帖子详情与评论 | 登录 | 无 |
| POST `/api/app/community/posts` | 发布帖子 | 登录 | body: sectionId, title(≤80), content(≤2000), imageFileIds(≤9), topics(≤8) |
| POST `/api/app/community/posts/{postId}/comments` | 发表评论（可回复、带图） | 登录 | body: content(≤500), parentCommentId, imageFileIds |
| POST `/api/app/community/posts/{postId}/like` | 点赞/取消点赞帖子 | 登录 | 无 |
| POST `/api/app/community/comments/{commentId}/like` | 点赞/取消点赞评论 | 登录 | 无 |
| POST `/api/app/community/comments/{commentId}/pin` | 置顶/取消置顶作者评论 | 登录 | 无 |
| DELETE `/api/app/community/comments/{commentId}` | 删除自己的评论 | 登录 | 无 |
| POST `/api/app/community/reports` | 举报帖子/评论/私信/用户；服务端保存内容快照 | 登录 | body: targetType: POST/COMMENT/MESSAGE/USER, targetId, reasonCode: AD/ABUSE/PORN/POLITICS/PRIVACY/OTHER, reasonDetail |
| GET `/api/app/community/authors/{authorId}` | 查看作者主页（资料+帖子） | 登录 | 无 |
| GET `/api/app/community/messages` | 查询与指定用户的私信 | 登录 | query: withUserId |
| POST `/api/app/community/messages` | 发送私信 | 登录 | body: receiverId, content(≤2000), imageFileIds |
| POST `/api/app/community/messages/read` | 标记私信会话已读 | 登录 | query: withUserId |
| GET `/api/app/community/messages/contacts` | 私信会话列表 | 登录 | 无 |
| GET `/api/app/community/unread-count` | 社区未读数（通知+私信） | 登录 | 无 |

- `CommunitySectionResponse`：sectionId, sectionCode, sectionName, icon, description, bannerTitle, bannerSubtitle, bannerImage
- `CommunityTopicResponse`：name, heat
- `CommunityPostResponse`：postId, sectionId, sectionName, sectionIcon, authorId, authorName, avatarUrl, authorLevel(列表场景 null，个人中心 profile 下发), authorBadgeCode(佩戴勋章编码), title, content, imageUrls, thumbnailUrls, topics, commentCount, likeCount, liked, createdAt
- `CommunityCommentResponse`：commentId, parentCommentId, authorId, authorName, avatarUrl, content, imageUrls, thumbnailUrls, likeCount, liked, pinned, author, createdAt
- 帖子详情 → `{ post: CommunityPostResponse, comments: CommunityCommentResponse[] }`
- `CommunityAuthorProfileResponse`：userId, displayName, avatarUrl, college, major, className, gender, posts
- `DirectMessageResponse`：messageId, senderId, receiverId, senderAvatarUrl, content, imageUrls, thumbnailUrls, createdAt
- `DirectConversationResponse`：peerId, peerName, avatarUrl, lastContent, lastAt

### 消息通知 `/api/app/notifications`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/notifications` | 分页查询我的通知，按时间倒序 | 登录 | query: pageNum, pageSize, unreadOnly, communityOnly |
| GET `/api/app/notifications/unread-count` | 未读通知数 | 登录 | query: communityOnly(默认 false) |
| POST `/api/app/notifications/{notificationId}/read` | 标记单条已读 | 登录 | 无 |
| POST `/api/app/notifications/read-all` | 全部标记已读 | 登录 | query: communityOnly(默认 false) |

- 返回：`PageResult<NotificationResponse>` / `NotificationResponse`：notificationId, notificationType, title, content, relatedType, relatedId, read, readAt, createdAt

### AI 助手 `/api/app/ai`（以下均需权限 ai:chat）

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| POST `/api/app/ai/conversations` | 创建 AI 会话 | 登录+ai:chat | body: title（可空，默认「新对话」） |
| GET `/api/app/ai/conversations` | 分页查询我的会话，按最近活跃倒序 | 登录+ai:chat | query: pageNum, pageSize |
| GET `/api/app/ai/conversations/{conversationId}/messages` | 会话消息（仅属主），按时间正序 | 登录+ai:chat | 无 |
| POST `/api/app/ai/conversations/{conversationId}/messages` | 发送消息并返回助手回复；AI 不可用时降级回复 | 登录+ai:chat | body: content(≤2000，带图片时可空), images: Long[](≤4) |
| POST `/api/app/ai/speech/transcribe` | 语音转写（mp3/aac/m4a/wav） | 登录+ai:chat | body: fileId |

- `AiConversationResponse`：conversationId, title, createdAt
- `AiMessageResponse`：messageId, role: USER/ASSISTANT, content, images: Long[], degraded(降级标记), actions: [{type: SERVICE_APPLY/REQUEST_DETAIL/REQUEST_LIST/SCHEDULE, label, itemCode, itemId, url}], createdAt

### 成就勋章 `/api/app/achievements`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| GET `/api/app/achievements/profile` | 成长档案：打开即先懒评估，返回等级/累计经验/进度/已获勋章数与最近 3 枚 | 登录 | 无 |
| GET `/api/app/achievements/badges` | 勋章墙：全部 19 枚 + 是否解锁 + 进度（未获得也返回）；响应含 badgeVersion 供客户端字典热更新判断 | 登录 | 无 |
| POST `/api/app/achievements/wear` | 佩戴/卸下勋章（badgeCode=null 卸下；佩戴须已解锁） | 登录 | body: badgeCode(可空) |

- `AchievementProfileResponse`：level, levelName, totalExp, nextLevel, nextLevelName, nextLevelExp, expToNext, progressPercent, badgeCount, totalBadgeCount, wornBadgeCode, recentBadges: [{badgeCode, badgeName, obtainedAt}]
- `AchievementBadgeItem`：badgeCode, badgeName, category(COMMUNITY/SERVICE/WORKER/JW/GROWTH), rarity(COMMON/RARE/EPIC/LEGENDARY), description, metricThreshold, rewardExp, unlocked, unlockedAt, currentProgress
- 图标随包本地匹配 `assets/badges/$badgeCode.png`，接口不下发图标 URL

### 课表 `/api/app/schedule`

| 方法 路径 | 说明 | 鉴权 | 请求 |
|---|---|---|---|
| POST `/api/app/schedule/import` | 从教务系统拉取个人课表并保存 | 登录 | query: semesterCode(可选) |
| GET `/api/app/schedule/view` | 查询已导入课表；不传学期返回最近同步学期 | 登录 | query: semesterCode(可选) |
| GET `/api/app/schedule/view-week` | 按周展开课表：服务端解析周次串成日期与时间段（节次映射服务端统一维护）；个人课表缺失时回落班级课表缓存 | 登录 | query: semesterCode(可选), week(可选，缺省当前周) |
| GET `/api/app/schedule/next-course` | 当前/下一节课程（首页 BFF） | 登录 | 无 |
| GET `/api/app/schedule/adjustments` | 调课通知列表（补课/停课/调课），按发布时间倒序；缺省返回最新有公告学期 | 登录 | query: semesterCode(可选) |

- `ScheduleImportResponse`：semesterCode, semesterName, className, personalCount, classCacheCount
- `ScheduleViewResponse`：semesterCode, semesterName, className, courses: [{courseName, teacher, location, dayOfWeek, startSection, endSection, weeks, weekParity}], availableSemesters: [{semesterCode, semesterName}], firstMonday, totalWeeks
- `ScheduleWeekViewResponse`：semesterCode, semesterName, week, weekStart, currentWeek, days: [{dayOfWeek: 1-7, date, courses: [{courseName, teacher, location, startSection, endSection, timeRange}]}]（days 固定 7 项周一~周日顺序）
- `ScheduleNextCourseResponse`：hasCourse, current(可 null), next(可 null, 可为后续上课日), week, date；CourseView：courseName, teacher, location, dayOfWeek, startSection, endSection, timeRange
- `ScheduleAdjustmentItem`：id, semesterCode, noticeTime, actionType(补课/停课/调课), courseCode, courseName, classNo, targetClasses: String[], summary（raw_text 不出接口）

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
| POST `/api/common/auth/login` | 开发环境密码登录（生产关闭，password-login-enabled=true 才装配） | 无需登录 | body: username, password |
| POST `/api/common/auth/wechat-login` | 微信登录，code 为 wx.login() 凭证；首登自动建号 | 无需登录 | body: code |
| POST `/api/common/auth/qq-login` | QQ 登录，服务端用 accessToken 反查 openId 建立身份 | 无需登录 | body: accessToken, openId(可选，用于比对) |
| POST `/api/common/auth/dev-wechat-login` | 开发环境固定账号登录，首登自动建号 | 无需登录 | 无 |
| GET `/api/common/auth/me` | 查询当前用户、固定身份、角色与权限 | 需登录 | 无 |
| POST `/api/common/auth/logout` | 使当前 Bearer Token 立即失效 | 需登录 | 无 |

- `LoginResponse`：accessToken(不透明令牌), expiresAt, isNewUser, user(CurrentUserResponse)
- `CurrentUserResponse`：userId, campusId, defaultOrganizationId, username, displayName, identityType: STUDENT/TEACHER/LOGISTICS/ADMIN, authorizationStatus: BASIC/PENDING/AUTHORIZED/REJECTED, roles: String[], roleScopes: [{roleCode, organizationId, dataScope}], permissions: String[], avatarPath, phone

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

- `ws://<host>/ws/community` — 社区实时消息推送（CommunityWebSocketConfig 注册，全源放行），路径在登录白名单内。

---

## 错误码速查表

来源：`common/ErrorCode.java`。HTTP 状态与 code 前缀对应；响应结构 `{ success: false, code, message, data: null }`。

| 枚举名 | code | message | HTTP |
|---|---|---|---|
| INVALID_PARAMETER | COMMON_400 | 请求参数不合法 | 400 |
| UNAUTHORIZED | AUTH_401 | 请先完成身份认证 | 401 |
| INVALID_CREDENTIALS | AUTH_401_01 | 用户名或密码错误 | 401 |
| WECHAT_LOGIN_FAILED | AUTH_401_02 | 微信登录失败，请稍后重试 | 401 |
| WECHAT_NOT_CONFIGURED | AUTH_503_01 | 微信登录服务尚未配置 | 503 |
| QQ_LOGIN_FAILED | AUTH_401_03 | QQ 登录失败，请稍后重试 | 401 |
| QQ_NOT_CONFIGURED | AUTH_503_02 | QQ 登录服务尚未配置 | 503 |
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
| COMMUNITY_REPORT_DUPLICATE | COMMUNITY_409_01 | 你已举报过该内容，正在处理中 | 409 |
| COMMUNITY_REPORT_HANDLED | COMMUNITY_409_02 | 该举报已处理 | 409 |
| COMMUNITY_REPORT_TARGET_INVALID | COMMUNITY_400_01 | 举报对象不存在或不可举报 | 400 |
| COMMUNITY_REPORT_NOT_FOUND | COMMUNITY_404_01 | 举报记录不存在 | 404 |
| COMMUNITY_MUTED | COMMUNITY_403_01 | 你已被限制发言，暂时无法发布内容 | 403 |
| COMMUNITY_REPORT_RATE_LIMITED | COMMUNITY_429_01 | 举报过于频繁，请稍后再试 | 429 |
| INTERNAL_ERROR | COMMON_500 | 系统繁忙，请稍后重试 | 500 |
