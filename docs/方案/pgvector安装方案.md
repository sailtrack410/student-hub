# pgvector 安装方案（RAG 库 5435）

## 背景

RAG 库容器 `1Panel-postgresql-iIW9` 用的是官方 `postgres:18.6-alpine` 镜像，**该镜像不含 pgvector**，因此 `CREATE EXTENSION vector` 报 `extension "vector" is not available`。这是"还没装"，不是"装不上"。

**已探测确认的前置条件（只读检查，未改动任何东西）**

| 检查项 | 结果 |
|---|---|
| PG 版本 | PostgreSQL 18.6 |
| 发行版 | Alpine 3.24.1，x86_64 |
| `pg_config` | ✅ 可用，`VERSION = PostgreSQL 18.6` |
| 服务端头文件 | ✅ `/usr/local/include/postgresql/server`，**903 个 .h 齐全**（含编译 pgvector 必需的 `postgres.h`、`utils/array.h`、`fmgr.h` 等） |
| 编译工具链 | ❌ 无 gcc/make/git（需装） |
| apk 源连通 | ✅ 可访问 dl-cdn.alpinelinux.org |
| GitHub 连通 | ✅ 可访问 |
| 数据卷 | `bind /opt/1panel/apps/postgresql/postgresql/data -> /var/lib/postgresql` |
| 重启策略 | `always` |
| PGDATA | `/var/lib/postgresql/18/docker` |

**关键点**：PG18 是源码编译到 `/usr/local` 的，**不走 apk**（apk 源里只有 `postgresql16-dev`）。所以不能靠 `apk add postgresql18-dev` 拿头文件——但头文件**镜像里已经带了**，只需装 gcc/make 即可编译。

**为什么不用 `pgvector/pgvector:pg18-alpine` 换镜像**：该标签不存在（`docker manifest inspect` 返回 no such manifest）。即使存在，换镜像需重建 1Panel 纳管的容器，风险更高。

## 方案：容器内编译安装（推荐）

优点：不动数据卷、不重建容器、1Panel 纳管状态不受影响。缺点：容器重建后需重装（可用下方"持久化"方案规避）。

```bash
# 1) 记录当前状态，便于回退
docker inspect 1Panel-postgresql-iIW9 --format '{{.Config.Image}}' > /root/pgvector-backup-image.txt

# 2) 安装编译工具链（仅容器内，不动数据卷）
docker exec 1Panel-postgresql-iIW9 apk add --no-cache build-base git

# 3) 拉取源码（选与 PG18 兼容的版本）
docker exec 1Panel-postgresql-iIW9 sh -c 'cd /tmp && git clone --branch v0.8.0 --depth 1 https://github.com/pgvector/pgvector.git'

# 4) 编译并安装（pg_config 已在 PATH，扩展会装到 /usr/local）
docker exec 1Panel-postgresql-iIW9 sh -c 'cd /tmp/pgvector && make && make install'

# 5) 验证扩展文件已就位
docker exec 1Panel-postgresql-iIW9 sh -c 'ls /usr/local/lib/postgresql/vector.so /usr/local/share/postgresql/extension/vector.control'

# 6) 创建扩展（用现有 RAG 账号）
psql -h ***REDACTED-HOST*** -p 5435 -U user_GJpKeA -d postgres -c 'CREATE EXTENSION vector;'

# 7) 确认
psql -h ***REDACTED-HOST*** -p 5435 -U user_GJpKeA -d postgres -c "select extname, extversion from pg_extension where extname='vector';"
```

**注意**：`make install` 写入的是容器可写层，**容器重建即丢失**。清理建议放在最后（`apk del build-base git` 与删 `/tmp/pgvector`）以减小镜像层，但清理掉的也仅是可写层。

## 若需容器重建后仍保留（持久化方案）

把编译产物放到数据卷（bind mount），再用符号链接接入：

```bash
# 产物复制到数据卷内的独立目录
docker exec 1Panel-postgresql-iIW9 sh -c '
  mkdir -p /var/lib/postgresql/pgvector/{lib,extension}
  cp /tmp/pgvector/vector.so /var/lib/postgresql/pgvector/lib/
  cp /tmp/pgvector/vector.control /tmp/pgvector/vector--*.sql /var/lib/postgresql/pgvector/extension/
  ln -sf /var/lib/postgresql/pgvector/lib/vector.so /usr/local/lib/postgresql/vector.so
  ln -sf /var/lib/postgresql/pgvector/extension/vector.control /usr/local/share/postgresql/extension/vector.control
  ln -sf /var/lib/postgresql/pgvector/extension/vector--0.8.0.sql /usr/local/share/postgresql/extension/vector--0.8.0.sql
'
```

重建容器后重新执行符号链接即可。**但更干净的长期做法是改用带 pgvector 的自定义镜像**，这需要提交 Dockerfile 与 1Panel 应用配置变更，属于部署层改动。

## 装上之后：应用侧需要配合的改动

当前代码**已经为 pgvector 预留了切换点**，装上后不需要改表结构（`embedding_dim` 字段已记录）：

1. **新增向量索引**（当前无 ANN 索引，走精确扫描）：
   ```sql
   CREATE INDEX idx_ai_kc_embedding_hnsw ON ai_knowledge_chunk
     USING hnsw (embedding_vec vector_cosine_ops);
   ```
   注意：现有 `embedding` 列是 `double precision[]`，HNSW 需要 `vector(dim)` 类型列。**需要加一列 `embedding_vec` 并回填**（或迁移类型），这是应用侧要做的改动。

2. **召回 SQL 换成 ANN 查询**：`ORDER BY embedding_vec <=> ?::vector LIMIT n`，替代现在的 `unnest` 精确余弦。这一步是 `RetrievalService.recallVector()` 的内部替换，接口不变。

3. **写入侧同时写两列**（或择一切换）。建议先双写、回填、再切换查询，保证可回滚。

## 建议的实施顺序

1. **现在**：容器内编译安装（步骤 1-7），先让 `CREATE EXTENSION vector` 成功
2. **然后**：我改应用侧——加 `embedding_vec` 列 + 双写 + ANN 查询 + 索引创建，用评测集验证 ANN 与精确扫描结果一致
3. **最后**：数据量上来后，ANN 索引才真正体现价值（当前 0 行，精确扫描完全够用）

## 风险与回退

| 风险 | 缓解 |
|---|---|
| 编译失败（版本不兼容） | pgvector 需选与 PG18 兼容的版本；失败不影响现有服务（只写了容器可写层） |
| 容器重建丢失扩展 | 用持久化方案，或改用自定义镜像 |
| 影响 1Panel 纳管 | 不重建、不改容器配置，仅在容器内装包，纳管状态不受影响 |
| 数据卷被误动 | 安装过程**不触碰** `/var/lib/postgresql` 数据目录 |
| ANN 结果与精确扫描不一致 | 先用评测集对比，HNSW 有近似误差，必要时调 `ef_search` |

## 附：只读探测命令（可复核）

```bash
# 确认镜像不含 pgvector
docker exec 1Panel-postgresql-iIW9 sh -c 'ls /usr/local/share/postgresql/extension/ | grep -i vector' || echo 无
# 确认头文件齐全（编译前提）
docker exec 1Panel-postgresql-iIW9 sh -c 'find /usr/local/include/postgresql/server -name "*.h" | wc -l'
# 确认 pg_config
docker exec 1Panel-postgresql-iIW9 pg_config | grep -E 'VERSION|PKGLIBDIR|SHAREDIR'