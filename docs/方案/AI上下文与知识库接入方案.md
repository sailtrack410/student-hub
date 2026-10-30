# AI 对话上下文与知识库接入改造方案（2026-10-19）

> 目标：让 App AI 真正用上知识库；同时把"一轮对话能带多少上下文、超了怎么办"变成可配置、可观测、可降级的能力，而不是靠 10 条历史 + 12000 字符的拍脑袋限制。

---

## 1. 现状与缺口（代码证据）

| 问题 | 现状 | 位置 |
|---|---|---|
| 知识库不生效 | App 对话只查 `visibility=PUBLIC AND enabled=true` 的库；当前两个库都是 INTERNAL → 每次返回 null | `KnowledgeBaseService.assistantContext():110` |
| 参数硬编码 | topK=6、阈值 0.30 写死，不读知识库自己的 `topK/scoreThreshold` | 同上 |
| 无引用/无依据约束 | App 系统提示词没有"仅依据资料、必须引用、无依据拒答"规则；响应无 sources | `CampusAssistantPrompts:10`、`AiMessageResponse` |
| 上下文只按条数 | `historyWindow=10` 按条数取；单条 `maxInputCharacters=12000` 只做单条校验，**没有总 token 预算** | `AiConversationServiceImpl.buildPrompt():442`、`AiChatServiceImpl.validateInputSize` |
| 无摘要 | 历史超长直接多带/截断模型侧，没有会话摘要 | `ai_conversation` 无 summary 字段 |
| 无溢出兜底 | 供应商报 context_length 时没有重试/降级 | `AiConversationServiceImpl` catch 仅通用降级 |
| token 记录不细 | `ai_message.token_usage` 只有总量，无 prompt/completion 拆分 | `AiMessageEntity:28` |
| 向量通道异常 | 实测 `vectorRank` 全为 null，口语化问题 0 命中；当前实际只有 pg_trgm 词法生效 | `RetrievalService` + 运行实例 |

---

## 2. 总体思路

两条线分开做，互不阻塞：

```
知识库接入线：可见性/供AI使用解耦 → 结构化检索结果(sources) → 提示词引用约束
上下文管理线：模型窗口 → 预算分配 → 裁剪/摘要 → 溢出重试 → 指标观测
```

配置分四层，越具体优先级越高：

1. **模型层**（`ai_model`）：上下文窗口、最大输出 —— 跟模型走；
2. **全局策略层**（`ai_config`）：安全余量、预算比例、摘要开关、估算系数；
3. **知识库层**（`ai_knowledge_base`）：是否供 AI 使用、RAG token 预算、TopK/阈值（已有）；
4. **会话层**（`ai_conversation`）：摘要、已摘要到哪条消息、上次 token 占用（运行时写入，不手工配）。

---

## 3. 数据模型变更（MySQL V54）

### 3.1 `ai_config`（单行，全局策略）

```sql
ALTER TABLE ai_config ADD COLUMN max_context_tokens INT NOT NULL DEFAULT 60000;
ALTER TABLE ai_config ADD COLUMN context_safety_margin_tokens INT NOT NULL DEFAULT 512;
ALTER TABLE ai_config ADD COLUMN max_output_tokens INT NOT NULL DEFAULT 2048;
ALTER TABLE ai_config ADD COLUMN history_token_ratio DECIMAL(4,3) NOT NULL DEFAULT 0.400;
ALTER TABLE ai_config ADD COLUMN rag_token_ratio DECIMAL(4,3) NOT NULL DEFAULT 0.250;
ALTER TABLE ai_config ADD COLUMN attachment_token_ratio DECIMAL(4,3) NOT NULL DEFAULT 0.150;
ALTER TABLE ai_config ADD COLUMN summary_enabled TINYINT NOT NULL DEFAULT 1;
ALTER TABLE ai_config ADD COLUMN summary_trigger_ratio DECIMAL(4,3) NOT NULL DEFAULT 0.800;
ALTER TABLE ai_config ADD COLUMN summary_keep_recent_turns INT NOT NULL DEFAULT 4;
ALTER TABLE ai_config ADD COLUMN token_estimate_chars_per_token DECIMAL(4,2) NOT NULL DEFAULT 1.50;
```

> `max_output_tokens` 放全局默认；若模型表也填了则以模型为准。

### 3.2 `ai_model`（模型能力）

```sql
ALTER TABLE ai_model ADD COLUMN context_window_tokens INT NULL;
ALTER TABLE ai_model ADD COLUMN max_output_tokens INT NULL;
```

### 3.3 `ai_knowledge_base`（AI 接入开关与预算）

```sql
ALTER TABLE ai_knowledge_base ADD COLUMN assistant_enabled TINYINT NOT NULL DEFAULT 0;
ALTER TABLE ai_knowledge_base ADD COLUMN rag_token_budget INT NOT NULL DEFAULT 3000;
ALTER TABLE ai_knowledge_base ADD COLUMN assistant_priority INT NOT NULL DEFAULT 0;
```

- `assistant_enabled` 与 `visibility` **解耦**：内部资料可以给登录用户的 AI 用，但不会被免登录公开接口查询；
- 公开接口仍然只认 `visibility=PUBLIC`。

### 3.4 `ai_conversation`（会话记忆）

```sql
ALTER TABLE ai_conversation ADD COLUMN summary TEXT NULL;
ALTER TABLE ai_conversation ADD COLUMN summary_until_message_id BIGINT NULL;
ALTER TABLE ai_conversation ADD COLUMN summary_updated_at DATETIME NULL;
ALTER TABLE ai_conversation ADD COLUMN last_prompt_tokens INT NULL;
ALTER TABLE ai_conversation ADD COLUMN last_context_tokens INT NULL;
ALTER TABLE ai_conversation ADD COLUMN compact_count INT NOT NULL DEFAULT 0;
```

### 3.5 `ai_message`（计量）

```sql
ALTER TABLE ai_message ADD COLUMN prompt_tokens INT NULL;
ALTER TABLE ai_message ADD COLUMN completion_tokens INT NULL;
```

保留现有 `token_usage`（总量）兼容；新字段用于预算校准。

---

## 4. 参数放在哪（管理端位置）

| 参数 | 存储 | 管理端位置 |
|---|---|---|
| 上下文窗口 / 最大输出 | `ai_model.context_window_tokens` / `max_output_tokens` | AI 基座 → 模型管理（CHAT 模型行） |
| 全局最大上下文、安全余量 | `ai_config` | AI 基座 → 全局设置 → 新增「上下文策略」 |
| 历史/知识库/附件预算比例 | `ai_config` | 同上 |
| 摘要开关/触发比例/保留轮数 | `ai_config` | 同上 |
| token 估算系数 | `ai_config` | 同上（高级项） |
| 供 AI 检索 | `ai_knowledge_base.assistant_enabled` | 知识库 → 编辑弹窗 |
| RAG token 预算 | `ai_knowledge_base.rag_token_budget` | 知识库 → 编辑弹窗 |
| 召回 TopK/阈值 | `ai_knowledge_base.top_k/score_threshold`（已有） | 知识库 → 编辑弹窗 |
| 会话摘要 | `ai_conversation.summary*` | 不手工配；管理端对话详情只读展示 |
| prompt/completion tokens | `ai_message.prompt_tokens/completion_tokens` | 对话详情/统计只读 |

> 2026-10-20 增量：RAG 独立 PostgreSQL、启用开关、文档解析与召回参数已移入「系统管理 → 系统配置 → 运行配置 → RAG 知识库」，
> 保存后热更新连接池。嵌入模型仍在「AI 基座 → 默认模型 → 向量模型」选择；一旦知识库产生向量分块，
> 默认 EMBEDDING 模型即锁定，禁止更换/清除/停用/删除，需先删除全部知识库文档。详见
> [`管理端外部集成配置说明.md`](管理端外部集成配置说明.md)。

默认值建议（可按实际模型调整）：

- 模型窗口：DeepSeek-V3 系 64000；其他模型在模型管理里填实际值；
- 最大输出：2048；
- 安全余量：512；
- 历史 40% / RAG 25% / 附件 15%（剩余 20% 给系统提示词与当前消息）；
- 摘要触发 80%，保留最近 4 轮；
- 估算系数 1.5 字符/token（中文为主），上线后校准。

---

## 5. 上下文预算算法

### 5.1 单次请求预算

```
window      = min(model.context_window_tokens, ai_config.max_context_tokens)
usable      = window - max_output_tokens - context_safety_margin_tokens
system      = estimate(SYSTEM_PROMPT)
ragBudget   = min(usable * rag_token_ratio, kb.rag_token_budget)
attachBudget= usable * attachment_token_ratio
historyBudget = usable - system - ragBudget - attachBudget
```

- 估算：`tokens = ceil(字符数 / chars_per_token)`，中文为主取 1.5；混合内容可再乘 1.1 安全系数；
- 每次调用后用供应商返回的 `promptTokens` 校准：若估算偏差 > 20%，记录并调整系数（后续可做）。
- 没有模型窗口时回退 `max_context_tokens`，绝不允许无上限。

### 5.2 各块打包规则

- **RAG**：按分数排序，按 `ragBudget` 装；相邻分块去重叠（同文档相邻 ordinal 合并）；每条带「来源：文件名/页码/章节」头；装不下时丢低分，至少保留 1 条；无命中则不注入并给"未找到"提示。
- **附件/文档**：按 `attachBudget` 截断（保留头尾 + 省略号），优先保留当前消息附件。
- **历史**：
  1. 从最近往前取，按**整轮**（user+assistant）打包，不拆轮；
  2. 不超过 `historyBudget`；
  3. 永远保留最近 1 轮（即使超预算也保，必要时先砍 RAG/附件）；
  4. 更早的历史：如果 `summary_enabled` 且存在未摘要旧轮，生成/更新摘要，用一条 system 消息「此前对话摘要：…」替代；
  5. 摘要内容保留事实（数字、日期、单号、已确认决定），不保留寒暄。

---

## 6. 超过上下文怎么办（按顺序）

1. **预算内裁剪**：先按比例分配，超了就按第 5.2 节砍（历史→RAG→附件），不直接报错。
2. **生成会话摘要**：历史 tokens 超过 `historyBudget * summary_trigger_ratio` 时，把"最近 N 轮之外"的旧消息压缩成 ≤500 token 摘要，写回 `ai_conversation.summary` 与 `summary_until_message_id`；下一轮只带摘要 + 最近 N 轮。
3. **单条消息过长**：当前用户消息超过 `maxMessageCharacters` → 直接 400 友好提示（现有行为）；附件文本按预算截断并提示"内容过长已截断"。
4. **供应商报 context_length**：捕获错误码 → 重试一次（历史/RAG 各减半）→ 仍失败则降级：去掉历史只带系统+当前消息+RAG 前 3 条；再失败返回统一降级文案，记录 errorCode。
5. **异常兜底**：估算失败/摘要失败 → 截断最老消息并继续；绝不因为摘要失败阻塞回答。
6. **压缩后的写回**：调用成功后写入 `last_prompt_tokens/last_context_tokens`；若 `promptTokens > window * 0.9`，标记 `compact_count+1`，下轮强制先摘要。

---

## 7. 代码改造清单

### 7.1 后端

| 文件 | 改动 |
|---|---|
| `V54__ai_context_and_kb_assistant.sql`（新增） | 上述字段 + 默认值 |
| `AiConfigEntity` / `AiConfigUpdateRequest` / `AiConfigResponse` / `AiConfigServiceImpl` / `AiRuntimeSnapshot` | 新增上下文策略字段并进快照 |
| `AiModelEntity` / 管理端模型 DTO/服务 | 上下文窗口、最大输出 |
| `KnowledgeBaseEntity` / 请求/响应 DTO / 管理端页面 | `assistantEnabled`、`ragTokenBudget`、`assistantPriority` |
| `AiConversationEntity` / `AiMessageEntity` | summary、token 字段 |
| 新增 `ConversationContextBuilder`（核心） | 预算计算、历史打包、RAG 打包、摘要替换、降级重试 |
| 新增 `TokenEstimator` | 字符→token 估算 + 校准 |
| 新增 `ConversationSummaryService` | 摘要生成、写回、失败降级 |
| `AiConversationServiceImpl` | `buildPrompt` 改为调用 ContextBuilder；记录 prompt/completion tokens；捕获 context_length 重试 |
| `KnowledgeBaseService.assistantContext` | 改为返回 `RagContext(text, sources)`；查 `enabled + assistant_enabled`；读 KB 预算；多库合并 |
| `CampusAssistantPrompts` | 加"仅依据资料/引用来源/无依据拒答/资料与个人数据优先级"规则 |
| `AiMessageResponse` + App 流式 done | 增加 `sources` 字段 |
| `AdminAiStreamController` | 复用同一 RagContext 与上下文预算（当前它自己拼 KB） |

### 7.2 管理端

- AI 基座 → 全局设置：新增「上下文策略」区块（窗口上限、安全余量、三类预算比例、摘要开关/触发/保留轮数、估算系数）；
- AI 基座 → 模型管理：CHAT 模型增加「上下文窗口」「最大输出」；
- 知识库 → 编辑：增加「供 AI 助手检索」「RAG token 预算」；
- 知识库 → 检索测试：显示 token 预算占用、被裁掉的分块、来源；
- AI 测试/对话详情：显示每轮 promptTokens、是否触发摘要/裁剪（只读）。

---

## 8. 分阶段落地

| 阶段 | 内容 | 验收 |
|---|---|---|
| P0（先做） | KB 接入开关 + sources + 提示词约束 + 读 KB 参数；修复向量通道（或确认只走词法并标注） | App 问知识库内容能答且带来源；无命中明确说未找到 |
| P1 | V54 字段 + 全局/模型配置 UI + ContextBuilder 预算裁剪（先不做摘要） | 任意长度对话不再把窗口撑爆；超限自动裁剪并记录 token |
| P2 | 会话摘要 + context_length 重试 + 指标观测 | 长对话（50+ 轮）仍稳定；promptTokens 不超窗口 90% |
| P3 | 分块结构/父子分块、RERANK、评测台 | 评测集 R@1/MRR 有基线并可对比 |

---

## 9. 待确认

1. 聊天模型实际上下文窗口（按当前 `deepseek-ai/DeepSeek-V3` 填 64k 还是 128k）？
2. 摘要用哪个模型（建议复用 CHAT 默认模型，低 max_tokens，成本可控）？
3. 历史策略：保留最近几轮原文（默认 4 轮）是否合适？
4. 内部知识库是否允许 App AI 检索（建议允许，登录用户可见；公开接口仍只 PUBLIC）？

---

## 10. 实现状态（2026-10-19）

已落地：

- **V58 迁移**：ai_config 上下文策略 10 字段、ai_model 上下文窗口/最大输出、ai_knowledge_base `assistant_enabled/rag_token_budget/assistant_priority`（默认全允许）、ai_conversation 摘要字段、ai_message prompt/completion/sources。
- **知识库接入**：`assistantContext(question, tokenBudget)` 查 `enabled + assistant_enabled`（内部库也可给登录 AI 用，公开接口仍只 PUBLIC），读库自身 topK/阈值/预算，多库按 priority 合并去重、按 token 预算装包；App 答案带 `sources` 引用。
- **上下文管理**：`AiContextAssembler` 按模型窗口/全局上限/输出/安全余量计算预算，按比例分配历史/RAG/附件；历史从新到旧按整条装；超限时异步生成会话摘要写回 `ai_conversation.summary`；供应商 context_length 错误自动压缩重试一次。
- **计量**：每轮记录 prompt/completion tokens、会话 last_prompt_tokens/last_context_tokens，超 90% 窗口记 compact。
- **管理端**：AI 基座 → 全局设置「上下文策略」、模型管理「上下文窗口/最大输出」、知识库「供 AI 助手检索/token 预算/优先级」。
- **向量通道修复**：`arrayLiteral` 原输出 `{...}` 未加单引号，PostgreSQL 报语法错误被静默降级为纯词法；修复为 `'{...}'::double precision[]`，语义召回恢复（口语化问题已实测命中）。
- **Nginx 反代**：`deploy/nginx/student-hub.conf` + README（SSE 不缓冲、300MB 上传、WebSocket、转发头、静态缓存、HTTPS 注释段）。
- **验证**：后端测试全绿；App AI 实测「寝室灯不亮找谁修」命中 4 条分块、回答引用 `kb-test-doc.md`；管理端配置页与知识库 AI 开关截图验证。

未做/后续：会话摘要目前异步单线程、无独立管理端查看页；RERANK 仍未接入；分块结构与 pgvector HNSW 仍是下一阶段。
