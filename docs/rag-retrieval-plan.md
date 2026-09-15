# RAG 检索准确率优化方案

面向"加入重排序模型 + 复杂知识库检索、追求最高准确率"的目标。本文先诊断当前实现的实际瓶颈（基于代码证据），再给出分阶段路线与落地映射。

## 〇、实测确认的环境约束（2026-09-15，已据此调整方案）

在真实 RAG 库（`***REDACTED-HOST***:5435`，PostgreSQL 18.6 Alpine 容器）实测：

| 扩展 | 可用性 | 影响 |
|---|---|---|
| `pgvector` | ❌ **不可用**（`pg_available_extensions` 无此项，扩展二进制未随镜像提供） | **无法使用 ANN/HNSW 向量索引**，向量只能精确扫描 |
| `pg_bigm` | ❌ 不可用 | 中文词法改用 `pg_trgm` 三元组 |
| `pg_trgm` | ✅ 可用（已创建并建 GIN 索引，`EXPLAIN` 确认走 `Bitmap Index Scan`） | 中文子串匹配可走索引 |

**并且现有召回的病根已被实测复现**：`ai_knowledge_chunk.search_vector` 是 `to_tsvector('simple', content)` 生成列，41 道评测题**命中 0 行**，候选池实际退化为无排序的任意 N 条。

**已验证的替代方案实测数据**（同一份评测集）：

| 方案 | R@1 | R@5 |
|---|---|---|
| 词法 `word_similarity` 阈值 0.05 | 29/41 | 39/41 |
| 词法阈值 0.1（索引预过滤下界） | 26/41 | 36/41 |
| 词法 + 标题加权 RRF 融合（k=60） | 28/41 | 38/41 |
| 改造前（simple 全文检索） | 0 候选 | 0 候选 |

两个实现要点：`%>` 走 trgm 索引需把 `word_similarity_threshold` 降到 0.1（默认 0.6 对"中文短问句 vs 长正文"过严，仅放行 1 题），该设置是**会话级**而非 GUC；`unnest` 在两数组长度不等时**静默截断不报错**，维度必须用 `WHERE array_length(...)` 显式拦截。

**§一～§六 以下内容中，凡涉及 pgvector / pg_bigm / ANN 的部分均需按上表调整。**

## 一、现状诊断

当前链路：`KnowledgeBaseService.retrieve()` → 关键词粗召回 → 在候选内算向量余弦 + 二元组词法相似 → 线性加权排序。

以下问题按对准确率的影响排序。

### P0-1 向量召回被关键词召回"卡脖子"（最致命）

`KnowledgeBaseService.java:97-104` 的候选池来自全文检索：

```sql
SELECT ... FROM ai_knowledge_chunk
WHERE knowledge_base_id IN (...) AND search_vector @@ plainto_tsquery('simple', ?)
ORDER BY fulltext_score DESC LIMIT <candidateLimit>
```

向量余弦只在**这 N 行候选内**计算（`retrieve()` 第 107 行）。也就是说：关键词没召回的正确答案，向量再准也进不了排序。

### P0-2 中文关键词召回在 `simple` 配置下基本失效 → 候选池退化为随机采样

`search_vector` 由 `RagSchemaInitializer` 定义为 `to_tsvector('simple', content)`。PostgreSQL 默认分词器不切分中文，连续汉字会成为一个整体 token：

- `to_tsvector('simple','图书馆周末开放时间')` → `'图书馆周末开放时间':1`
- `plainto_tsquery('simple','图书馆几点关门')` → 单个 token

两者只有在"整串完全一致"时才匹配，正文段落中几乎不可能命中。于是 `retrieve()` 第 99-104 行的 `rows.isEmpty()` 分支被频繁触发，回退 SQL **没有 ORDER BY**：

```sql
SELECT ... WHERE knowledge_base_id IN (...) LIMIT 40   -- 无序
```

结论：**每次检索实际是在"任意 40 个分块"里排序**，真正有效的词法得分是 Java 里对这批候选算的 `ngramSimilarity`（二元组覆盖率）。召回上限被锁死在"这 40 个里恰好包含多少正确分块"。

### P1-1 没有重排序，且融合权重是拍出来的

`retrieve()` 第 107 行：`score = 0.70*vector + 0.30*lexical`，固定权重、线性融合。没有 cross-encoder 精排，top-k 顺序只能算近似。

### P1-2 相关性归一化与阈值语义不稳定

- `vector` 是余弦（-1~1，代码只对 `fulltext_score` 做了非负截断，余弦未截断）
- `lexical` 是二元组覆盖率（0~1）
- `scoreThreshold` 默认 0.30 却直接和这个混合分比较

调整权重或换模型都会让阈值含义漂移，且不同 query 之间分数不可比。

### P1-3 向量维度与模型无契约 → 换模型静默失效

`cosine()`（第 114 行）在维度不等时返回 `0`，退化为纯词法。而分块表没有记录 `embedding_model` / `embedding_dim`，**切换向量模型后不重建索引不会有任何报错，准确率直接掉崖**。

### P1-4 没有向量索引，查询期在 Java 里解析文本向量

`embedding` 是 `double precision[]`，查询时把候选行的向量取回 Java 逐字符 parse（第 113 行 `parseArray`）。小规模可接受，规模上去后是明确瓶颈。

### P2-1 分块策略对结构不敏感

`chunk()`（第 88 行）按固定字符窗口切，只在 `cut > start + size/2` 时才优先 `\n` / `。` 边界：

- 不识别标题层级、表格、列表、代码块
- 不按 token 计量（`chunkSize` 是字符数）
- 句子完整性无保证
- overlap 产生的重复文本会各占一条排序位（`content_hash` 有列但无唯一约束，未做分块级去重）

### P2-2 段落粒度太粗，引用不精确

`KnowledgeDocumentParser`：PDF 按页、DOCX 按标题、MD 按标题产出 `Section`。PDF 的 `titlePath` 固定为 `"第N页"`，无法做结构感知加权，也无法给出精确引用。

### P2-3 索引在请求事务内同步执行

`upload()` 是 `@Transactional` 并直接调用 `index()`，内部是 N 次 embedding HTTP 调用。30MB 文档会长时间持有事务并阻塞管理端请求；无队列、无进度、无重试。

### P2-4 查询侧零处理

用户原句直接送 embedding。没有归一化、没有指代消解（"那周末呢" 这类追问）、没有多查询扩展/HyDE。

### P2-5 注入上下文无预算管理

`assistantContext()` 硬编码 topK=6 / threshold=0.30，把命中分块平铺拼接，不控 token 预算、不去重重叠分块。分块过长会稀释生成模型的上下文。

### P0-0 没有评测集 → "准确率最高"当前无法证伪

这是最根本的问题：没有标注集，上述任何改动的收益都无法量化，优化必然靠猜。

## 二、目标架构

```
用户问题
  │
  ├─ 查询理解：归一化 / 指代消解 / 多查询扩展 / HyDE      [阶段 4]
  │
  ├─ 混合召回（并行、互不设限）                          [阶段 1]
  │    ├─ 向量召回：pgvector HNSW (ANN)  ┐
  │    └─ 词法召回：pg_bigm 二元组索引    ├─ RRF 融合
  │
  ├─ 精排：cross-encoder Rerank（topN → topK）           [阶段 3]
  │
  ├─ 上下文组装：token 预算 / 重叠去重 / 引用保留
  │
  ─ 生成 + 引用校验（无依据时明确拒答）
```

## 三、分阶段路线

### 阶段 0：建评测集（必须最先做）

没有度量就不要动检索参数。

- 新增 `ai_rag_eval_set`（query、期望命中的 chunk_id 或 doc+page、难度标签）
- 新增 `ai_rag_eval_run`（run_id、检索参数快照、每 query 的命中排名、聚合指标）
- 指标：recall@k、precision@k、MRR、nDCG@10；后续可加 LLM-judge 的答案忠实度
- 管理端「知识库 → 评测」页：选知识库、填参数、跑评测、出对比记分卡
- 交付标准：记录**基线数字**，后续每个阶段都必须跑赢基线才能合入

标注量建议 30~50 题起（覆盖：不同措辞、跨文档、表格类、时间类、无答案类）。

### 阶段 1：修复召回下限（收益最大、风险最低）

1. **候选池解耦**：向量召回与词法召回各自独立取 topN，再用 RRF 融合。从根本上消除"词法卡向量"。
2. **中文词法可用**：装 `pg_bigm`（bigram，无需词典，改动最小）替代 `simple`；或退一步用 `pg_trgm` 的 `similarity()` 让它可走索引，而不是只在候选集内算。
3. **pgvector 接入**：`embedding_vec vector(dim)` + HNSW（`vector_cosine_ops`）。若无法装扩展，则保留精确扫描但**必须去掉候选截断**（按选定的 dim 全量扫或分页扫），并评估规模上限。
4. **维度/模型契约**：分块表记录 `embedding_model`、`embedding_dim`、`chunk_version`；查询时维度不匹配 → 明确报错并提示"需重建索引"，禁止静默退化。
5. **分块级去重**：`UNIQUE (knowledge_base_id, content_hash)`。

### 阶段 2：分块与结构（从源头提准确率）

1. **结构感知分块**：解析产出结构树（保留标题层级），按标题/段落/表格/列表切分，保护句子完整性与表格行完整性；按 token 而非字符计量。
2. **父子分块（small-to-big）**：存 `parent_chunk_id` / `section_id`，用小分块检索、用父分块喂上下文，兼顾命中率与上下文完整度。
3. **元数据增强**：`section_path`、`page_start/page_end`、`doc_title`、`doc_type`、`effective_date`、`tags` —— 支撑元数据过滤、时效加权与精确引用。
4. **分块策略可插拔**：`ChunkingStrategy` 接口，保留现 `FixedWindowChunker` 作为默认/对照组。

### 阶段 3：重排序（用户下一步要做的）

1. **新增 `RERANK` 模型类型**，完全复用 ASR/EMBEDDING 的既有扩展路径：
   - `AiRuntimeSnapshot` 加 `rerank` 字段
   - `AiConfigServiceImpl` 加 `TYPE_RERANK`，`resolveBinding` 接入
   - `AiProviderAdminServiceImpl` 默认模型列表与类型名
   - `AiDefaultModelSetRequest` / `AiModelImportRequest` / `AiModelUpdateRequest` 的 `@Pattern` 放开
   - 管理端 `aiconfig.js` 的 `TYPE_LABEL` / `TYPE_OPTIONS`
2. **Provider 能力**：`AiProvider.rerank(...)`，默认 `UnsupportedOperationException`，`OpenAiCompatibleProvider` 实现 `/rerank`（兼容 SiliconFlow / BGE-reranker、Jina、Cohere 形态，需处理各家响应字段差异）。
3. **两阶段检索**：混合召回 topN（≈50）→ 精排 → topK（5~8）。一阶段用 RRF，最终序用 cross-encoder 分。
4. **可观测**：`RagHit` 增加 `rerankScore`、`fusedRank`、`vectorRank`、`lexicalRank`，管理端可直接看两阶段排名差异。
5. **降级**：未配置或调用失败 → 退回 RRF 顺序，绝不让请求失败。

### 阶段 4：查询理解与编排

- 查询归一化 + 校园词表扩展（"报修/宿舍/在读证明"等别称）
- 多查询扩展（LLM 生成 2~3 个改写）+ 可选 HyDE
- 对话感知：追问（"那周末呢"）先用会话历史补全再检索
- 多知识库路由、元数据过滤、政策类文档时效加权
- 自适应 topK；阈值改为分位数而非绝对值（跨 query 可比）
- 上下文打包：token 预算、重叠去重、按分排序、引用保留
- 生成侧：强制引用、无依据时明确拒答（准确性不只取决于召回）

### 阶段 5：工程健壮性

- **索引异步化**：文档状态机 + 任务表/`@Async`，embedding 移出上传事务，支持进度、重试退避、按内容哈希幂等
- **缓存**：query 向量缓存、检索结果缓存（key = 问题哈希 + KB + 配置版本），KB 变更时失效
- **观测**：各阶段耗时（现有 `retrievalMs`/`generationMs` 基础上细化）、线上"答得不对"反馈回流扩充评测集
- **限流与成本**：embedding/rerank 调用限流

## 四、落地映射（具体文件与表）

### 表结构变更（注意两个库的分工）

| 表 | 所属库 | 变更方式 |
|---|---|---|
| `ai_knowledge_base`、`ai_knowledge_document` | 主库（MySQL） | 新增 Flyway 迁移 **V36**（V35 已占用） |
| `ai_knowledge_chunk` | RAG 库（PostgreSQL） | 改 `RagSchemaInitializer`（应用启动时建表，不走 Flyway） |

- V36：`ai_knowledge_base` 增加 `retrieval_mode`、`candidate_limit`、`rerank_enabled`、`rerank_top_n`、`rrf_k`；新增评测两张表。
- `RagSchemaInitializer`：`ai_knowledge_chunk` 增加 `embedding_vec`、`embedding_model`、`embedding_dim`、`section_path`、`parent_chunk_id`、`page_start`、`page_end`、`chunk_version`；`search_vector` 生成表达式改为 `pg_bigm`；加 HNSW 索引与分块唯一索引。

### 代码重构（当前 `KnowledgeBaseService` 需要拆分）

现有 `KnowledgeBaseService` 把 `index / retrieve / test / assistantContext` 全塞在一个类里，且大量单行写法（第 43-116 行），既不利于测试也不符合项目分层约定。建议拆为：

| 新类 | 职责 |
|---|---|
| `KnowledgeIndexService` | 解析 → 分块 → 向量化 → 落库，异步化入口 |
| `RetrievalService` | 混合召回 + RRF + 精排 + 上下文组装 |
| `RagEvaluationService` | 评测集执行与指标计算 |
| `RerankService` | 封装 rerank 调用与降级 |
| `ChunkingStrategy`（接口）+ 实现 | 固定窗口 / 结构感知 |

## 五、需要确认的前提（影响设计分支）

1. **RAG 库能否装扩展**：`CREATE EXTENSION vector` 与 `pg_bigm` 是否可用？可用 → HNSW ANN + pg_bigm；不可用 → 精确扫描 + 全量候选（需评估规模上限）。这是决定阶段 1 形态的关键。
2. **向量模型与维度**：选哪个模型、多少维？pgvector 的 `vector(N)` 必须定维，且换模型即需重建索引。建议先在管理端确定并写入 `embedding_dim`。
3. **重排序供应商**：SiliconFlow 的 BGE-reranker-v2-m3，还是 Jina / Cohere 形态？决定 `/rerank` 的请求与响应映射写法。
4. **规模预期**：预计文档数与分块数、并发量。决定精确扫描是否够用、是否需要 ANN 与缓存。
5. **是否现在开工**：建议从阶段 0（评测集）+ 阶段 1（候选池解耦）开始，这两步不依赖上面任何决策，且能立刻拿到可量化的提升。

## 六、优先级与预期收益

| 阶段 | 内容 | 准确率影响 | 依赖 |
|---|---|---|---|
| 0 | 评测集与指标 | 使后续一切可度量 | 无 |
| 1 | 候选池解耦 + 中文词法 + pgvector | **最大**（解除召回上限） | 前提 1、2、4 |
| 3 | 重排序 | 大（序质量） | 前提 3 |
| 2 | 分块与结构 | 中大（源头质量） | 无 |
| 4 | 查询理解 | 中（复杂问法） | 无 |
| 5 | 异步化与缓存 | 不直接提升，但决定可用性 | 无 |

注：阶段 1 与阶段 3 是提准确率的两条主线；阶段 0 是前提，否则无法证明"最高"。