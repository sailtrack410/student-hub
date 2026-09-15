# Student Hub API

学生自助服务与校园事务智能助手的后端基础工程。

## 当前基础能力

- Spring Boot 3 + Java 17 + Maven
- 统一 `Result<T>` 响应、全局异常处理和 `PageResult` 分页结构
- Spring Security、多角色权限、组织数据范围和不透明服务端会话
- Flyway 数据库迁移与开发环境种子数据
- MySQL、Redis 配置及本地 Docker Compose 依赖
- Knife4j 接口调试：`/doc.html`；Swagger UI：`/api`
- 健康检查：`GET /api/common/system/health`
- 服务目录、服务申请、工单、评价、通知、AI 会话等业务模块与申请-派单-办结-评价闭环
- 报修类工单拍照确认闭环：现场拍照 → 完工拍照 → 报修人确认（或退回重新处理）
- RAG 知识库（`modules/rag`）：PDF/DOCX/TXT/MD 解析分块、向量化与检索测试，检索结果注入 AI 助手上下文；默认关闭，需 `RAG_ENABLED=true`
- 校园社区：失物招领、二手交易、学习咨询、校友杂谈分区，支持发帖、图片和评论
- 公开数据大屏与现场服务终端：`/screen/`（免登录终端）、`GET /api/public/screen/overview`
- 雪花 ID 全局序列化为字符串，避免小程序端精度丢失
- 外部模型 API 的 `AiProvider` 抽象和 OpenAI-compatible 适配器（聊天 / 图片识别 / 语音识别 / 向量模型）
- AI 默认关闭；配置 `AI_ENABLED=true` 后才允许调用外部模型
- 微信小程序学生端正式版（`student-hub-weapp/`），UI 素材由 gpt-image-2 管线生成

## 本地启动

环境要求：JDK 17、Maven 3.9+、MySQL 8、Redis 7。

```bash
cp .env.example .env
docker compose up -d mysql redis
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

根目录未跟踪的 `.env` 由 Spring Boot 启动时自动加载（无需先 source），数据库连接与微信凭证都在其中配置，不要把真实值提交到 Git。

也可以直接启动全部容器：

```bash
docker compose up --build
```

## 测试和打包

```bash
./mvnw test
./mvnw clean package -DskipTests
```

测试使用 H2，不需要本地 MySQL 或 Redis。

## AI 配置

不要把真实 API Key 提交到 Git。开发时在未跟踪的 `.env` 或运行环境中设置：

```bash
AI_ENABLED=true
AI_BASE_URL=https://api.deepseek.com
AI_API_KEY=your-key
AI_MODEL=deepseek-chat
```

当前提供 Provider 和 Service 基础抽象；模型类型分 CHAT / VISION / ASR / EMBEDDING 四类，各自在管理端「AI 配置」中指定默认模型。

## RAG 知识库配置

知识库默认关闭。开启后需额外提供 PostgreSQL 连接（向量表 `ai_knowledge_chunk` 在应用启动时就绪）：

```bash
RAG_ENABLED=true
RAG_DB_URL=jdbc:postgresql://localhost:5435/postgres
RAG_DB_USERNAME=your-user
RAG_DB_PASSWORD=your-password
```

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `RAG_MAX_DOCUMENT_SIZE_MB` | `30` | 单篇文档大小上限（MB） |
| `RAG_MAX_PARSED_CHARACTERS` | `2000000` | 单篇文档解析字符上限 |
| `RAG_EMBEDDING_BATCH_SIZE` | `16` | 向量化批大小 |
| `RAG_LEXICAL_CANDIDATE_LIMIT` | `40` | 词法通道召回候选数 |
| `RAG_VECTOR_CANDIDATE_LIMIT` | `40` | 向量通道召回候选数 |
| `RAG_LEXICAL_MIN_SIMILARITY` | `0.05` | 词法预过滤下界（过低放大候选，过高漏召回） |
| `RAG_RRF_K` | `60` | RRF 融合常数，越小越强调头部排名 |

检索采用**混合召回 + RRF 融合**：向量通道（精确余弦）与词法通道（`pg_trgm` 三元组，支持中文）各自独立取候选，再按排名融合排序，因此任一路都不会被另一路截断。未安装 pgvector 时向量走 SQL 侧精确扫描；`pg_trgm` 不可用时会退化为词法全表扫描并告警。换向量模型后，维度不一致的历史分块会被自动跳过并提示重建索引。检索测试与管理入口：管理端「知识库」页。

## 开发认证

`dev/test` 环境提供四个开发账号，统一密码为 `DevOnly123!`：

| 用户名 | 角色 | 数据范围 |
|---|---|---|
| `admin` | 平台管理员 | 全部 |
| `requester` | 服务申请人 | 本人 |
| `worker` | 服务人员 | 被指派工单 |
| `dispatcher` | 调度员 | 后勤部门及下级 |

```bash
curl -X POST http://localhost:9000/api/common/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"DevOnly123!"}'
```

生产环境强制关闭密码开发登录。微信、校园统一身份认证和管理端正式登录后续接入同一套服务端会话。

## 微信登录

小程序用户通过微信登录，没有单独的注册接口；首次登录自动创建平台账号。

### 接口

```
POST /api/common/auth/wechat-login
```

前端只需提交微信 `wx.login()` 获取的 `code`，后端不返回 `session_key`：

```json
{
  "code": "微信登录code"
}
```

响应符合项目统一 `Result<T>` 结构：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {
    "accessToken": "随机不透明Token",
    "expiresAt": "2026-08-22T12:00:00Z",
    "isNewUser": true,
    "user": {
      "userId": 1,
      "username": "wx_openid_prefix",
      "displayName": "微信用户",
      "identityType": "STUDENT",
      "authorizationStatus": "BASIC",
      "roles": ["REQUESTER"]
    }
  },
  "timestamp": "2026-08-22T11:00:00Z"
}
```

首次登录自动创建用户：`identityType` 设为 `STUDENT`，`authorizationStatus` 设为 `BASIC`，并绑定 `REQUESTER`（服务申请人）基础权限角色。

### 环境配置

| 环境 | WECHAT_MODE | 说明 |
|---|---|---|
| 开发 | `real` | 使用小程序真实 AppID 和 AppSecret 调用微信 `jscode2session` |
| 测试 | `mock` | 仅自动化测试使用，不请求微信接口 |
| 生产 | `real` | 使用生产配置调用微信 `jscode2session` 接口 |

开发和生产环境必须配置同一个小程序对应的 AppID 与 AppSecret：

```bash
WECHAT_MODE=real
WECHAT_APP_ID=你的小程序AppID
WECHAT_APP_SECRET=你的小程序AppSecret
```

缺少 AppID 或 AppSecret 时，登录接口返回 `AUTH_503_01`，不会回退到 Mock 登录。

可选超时配置（单位：秒）：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `WECHAT_CONNECT_TIMEOUT_SECONDS` | `5` | 连接微信接口的建立超时 |
| `WECHAT_READ_TIMEOUT_SECONDS` | `10` | 等待微信接口响应的读取超时 |

## 接口清单

统一前缀与角色约束见 Knife4j 文档，主要接口：

| 分组 | 接口 |
|---|---|
| 认证 | `POST /api/common/auth/login`（仅 dev/test）、`POST /api/common/auth/wechat-login`、`GET /api/common/auth/me`、`POST /api/common/auth/logout` |
| 服务目录 | `GET /api/app/services`、`GET /api/app/services/{itemId}`；管理端 `POST /api/admin/catalog/*` |
| 服务申请 | `POST /api/app/service-requests`（支持 `Idempotency-Key` 幂等）、`GET /api/app/service-requests`、`GET /api/app/service-requests/{id}`、`POST /api/app/service-requests/{id}/cancel` |
| 工单（服务人员） | `GET /api/app/work-orders/assigned`、`GET /api/app/work-orders/{id}`、`POST /api/app/work-orders/{id}/start`、`POST /api/app/work-orders/{id}/arrival-photo`、`POST /api/app/work-orders/{id}/completion-photo`、`POST /api/app/work-orders/{id}/complete` |
| 工单确认（报修人） | `POST /api/app/work-orders/{id}/confirm`、`POST /api/app/work-orders/{id}/reject-confirmation` |
| 工单（调度端） | `GET /api/admin/work-orders`、`GET /api/admin/work-orders/{id}`、`POST /api/admin/work-orders/{id}/assign`、`POST /api/admin/work-orders/{id}/reject` |
| 知识库（管理端） | `GET/POST /api/admin/knowledge-bases`、`PUT/DELETE /api/admin/knowledge-bases/{id}`、`GET/POST /api/admin/knowledge-bases/{id}/documents`、`POST /api/admin/knowledge-bases/{id}/documents/{docId}/reindex`、`POST /api/admin/knowledge-bases/{id}/test` |
| 知识库（公开） | `POST /api/public/knowledge-bases/{id}/query`（仅 PUBLIC 且启用的知识库） |
| 评价 | `POST /api/app/service-requests/{id}/evaluation` |
| 通知 | `GET /api/app/notifications`、`GET /api/app/notifications/unread-count`、`POST /api/app/notifications/{id}/read`、`POST /api/app/notifications/read-all` |
| AI 助手 | `POST /api/app/ai/conversations`、`GET /api/app/ai/conversations`、`GET /api/app/ai/conversations/{id}/messages`、`POST /api/app/ai/conversations/{id}/messages` |
| 校园社区 | `GET /api/app/community/sections`、`GET /api/app/community/posts`、`GET /api/app/community/posts/{id}`、`POST /api/app/community/posts`、`POST /api/app/community/posts/{id}/comments` |

## 微信小程序

学生端正式版位于 `student-hub-weapp/`，用微信开发者工具导入该目录即可运行。

- 四个 Tab：首页、服务大厅、AI 助手、我的；另有登录、服务详情、申请表单、申请列表/详情、评价、消息、服务人员工单列表与工单详情等页面
- 统一请求层 `utils/request.js`：自动携带 Token、解包 `Result<T>`、401 自动跳转登录，并过滤 `undefined` 查询参数
- 资源地址统一走 `utils/resource.js`（`resourceUrl`/`thumbnailUrl`），兼容后端绝对对象地址与本地 API 相对路径
- 动态申请表单由服务项 `formSchema` 驱动（`components/form-renderer`），schema 重建时保留已填值
- 后勤身份（`identityType=LOGISTICS` 或持有 `WORKER` 角色）隐藏学生专属入口：课表、我的申请、请假/场地/在读证明等服务项，视图规则收敛在 `utils/directory.js`
- 真机调试时把 `utils/config.js` 的 `baseUrl` 改为电脑局域网 IP，并在开发者工具勾选“不校验合法域名”
- 使用 `worker` 开发账号登录可在“我的 → 我的工单”体验服务人员接单、现场拍照、完工拍照与提交确认流程

## 文件上传与存储

申请材料、现场照片等文件通过 `POST /api/app/files` 上传（登录后调用），仅支持 jpg/png/webp、默认 ≤5MB；服务端校验扩展名、MIME 与文件头魔数，按短时 HMAC 签名 URL 读取（`GET /api/common/files/view/{id}`，默认 30 分钟有效）。

存储后端可切换：

- `FILE_STORAGE=local`（默认）：写入 `FILE_STORAGE_DIR` 本地目录；
- `FILE_STORAGE=minio`：写入 MinIO 桶（`MINIO_ENDPOINT`/`MINIO_BUCKET`/`MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY`，凭据放未跟踪的 `.env`）。

所有图片上传先经过统一图片压缩模块：小程序端调用 `utils/image.js` 压缩，服务端
`ImageCompressionService` 再按最大边 1600px 和 JPEG 质量 0.82 做安全压缩；透明 PNG 保留透明通道，WebP 保留原格式。后续新增图片上传接口应复用 `FileStorageService.store`，不要自行实现压缩逻辑。

请假申请的"请假材料"、宿舍/教室报修的"现场照片"为选填图片字段，由服务项表单 schema 驱动。

## 教务系统认证

个人中心支持绑定青果（Kingosoft）教务系统（经 SSO 统一身份认证）：

- `GET /api/app/jw/captcha` 算术题验证码；`POST /api/app/jw/auth` 认证（RSA 加密密码，账号/密码/验证码错误统一提示）
- 认证成功后按教务身份自动切换平台身份（学生/教师），同一教务账号仅可绑定一个平台用户
- 教务会话 Cookie 经 AES-GCM 加密托管（`JW_ENCRYPT_KEY`），与平台会话分开失效，默认 48 小时上限（`JW_SESSION_TTL_HOURS`）
- 三态展示：未认证 / 已认证 / 已失效（失效后点击重新认证）；`GET /api/app/jw/profile` 实时拉取教务个人信息

## UI 素材管线

Logo、吉祥物、TabBar 图标、分类图标、空状态插画等由 `gpt-image-2` 生成：

```bash
node tools/generate-assets.mjs            # 生成缺失素材（清单见 tools/assets/manifest.mjs）
node tools/assets/reprocess.mjs --force   # 按展示尺寸重新压缩已有素材
```

生图接口凭证从 `/Users/sailtrack/Desktop/.env` 或环境变量 `IMAGE_API_URL`/`IMAGE_API_KEY` 读取。素材统一扁平插画风（主色 #2563EB、强调色 #14B8A6），压缩后总体积控制在小程序主包限制内。

## 接口调试

开发环境启用 Knife4j，启动后访问：

- Knife4j 调试页：[http://localhost:9000/doc.html](http://localhost:9000/doc.html)
- Swagger UI 调试页：[http://localhost:9000/api](http://localhost:9000/api)
- OpenAPI JSON：[http://localhost:9000/v3/api-docs](http://localhost:9000/v3/api-docs)

先调用开发登录接口获取 `accessToken`，再在文档页的全局授权中填入 Token，即可调试受保护接口。生产环境默认关闭接口文档。

## 数据库迁移

- 正式表结构：`src/main/resources/db/migration`
- 开发种子数据：`src/main/resources/db/devdata`
- `dev/test` 会加载两者，`prod` 只加载正式迁移。
- 已应用的迁移文件禁止修改；结构变化必须新增版本文件。

## 目录约定

详细设计规则见 [CLAUDE.md](CLAUDE.md)。后续学生、事务、知识库和管理模块按 `controller -> service -> mapper` 分层，并通过 `integration` 适配外部系统。
