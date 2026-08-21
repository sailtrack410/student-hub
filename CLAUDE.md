# Student Hub 项目设计规范

本文档是 Student Hub 的工程约定，供开发、评审、测试和部署使用。它既是当前项目的设计规范，也是后续引入代码助手时必须遵守的工作上下文。

## 1. 项目定位

Student Hub 是面向高校学生的自助服务与校园事务智能助手，主要提供：

- 校园办事指南、政策和通知查询
- 课表、成绩、缴费、申请进度等个人信息查询
- 报修、请假、证明申请等事务导航和受控办理
- AI 对话、意图识别、表单信息收集和结果解释
- 面向辅导员、学院和系统管理员的管理后台

### 当前阶段的架构决策

- 学生入口优先支持微信小程序，H5 作为补充；管理端使用桌面 Web。
- 后端采用 Spring Boot 模块化单体，不拆分微服务。
- AI 只调用外部模型 API，直接由 Java 后端调用，不单独部署 Python 容器。
- 模型供应商通过适配层隔离，业务代码不得绑定某一家模型厂商的 SDK。
- 真正的事务办理由规则、权限和校园系统接口执行，模型不能直接修改业务数据。
- 只有在出现复杂文档解析、RAG、异步 Agent 或本地模型需求时，才评估独立 AI 服务。

## 2. 技术栈

| 层次 | 约定 |
|---|---|
| 语言 | Java 17 |
| 后端框架 | Spring Boot 3.x |
| 构建 | Maven Wrapper 优先 |
| ORM | MyBatis-Plus 3.x；复杂 SQL 使用显式 Mapper XML/注解 |
| 数据库 | MySQL 8.0+，统一使用 `utf8mb4` |
| 缓存 | Redis，用于会话、限流、短期缓存和幂等键 |
| API 文档 | OpenAPI 3 + Knife4j/SpringDoc |
| 认证 | 校园统一身份认证适配器；短期会话使用 HttpOnly Cookie 或 Bearer Token |
| AI | 外部模型 API；通过 `AiProvider`/`AiChatService` 统一封装 |
| HTTP 客户端 | Spring `RestClient` 或项目统一 HTTP 客户端，禁止业务代码各自创建客户端 |
| 日志 | SLF4J + Logback；生产环境支持结构化日志 |
| 部署 | Docker；MVP 使用单个应用容器，外部依赖使用独立基础设施服务 |
| 测试 | JUnit 5、Spring Boot Test、MockMvc；关键流程增加集成测试 |

### 依赖原则

- 优先使用 Spring Boot 和 JDK 自带能力，新增依赖必须说明用途、版本和替代方案。
- 不重复引入多个同类库。尤其不得同时引入多个 JWT、JSON 或 HTTP 客户端实现，除非有迁移说明。
- 依赖版本由 `pom.xml` 统一管理；禁止在业务代码或脚本中隐式下载未锁定版本。
- 外部服务 SDK 只能出现在 `integration` 或 `infrastructure` 包中，Controller 和领域 Service 不直接依赖厂商类型。

## 3. 后端目录和分层

推荐的基础目录如下：

```text
src/main/java/com/example/studenthub/
├── StudentHubApplication.java
├── common/                 # Result、分页、常量、枚举、工具
├── config/                 # Spring、Redis、OpenAPI、HTTP 客户端配置
├── controller/
│   ├── student/            # 学生端接口
│   ├── admin/              # 管理端接口
│   └── common/             # 登录、AI、文件等跨端接口
├── controller/**/dto/      # 请求/响应 DTO
├── controller/**/query/    # 查询条件
├── entity/                 # 持久化实体，禁止跨层暴露
├── mapper/                 # MyBatis Mapper
├── service/                # 业务接口
├── service/impl/           # 业务实现
├── convert/                # MapStruct 转换器
├── integration/            # 校园系统、微信、模型、短信等外部适配器
├── exception/              # 业务异常和全局异常处理
├── interceptor/            # 认证、审计、幂等、限流等拦截器
└── utils/                  # 无业务含义的通用工具
```

业务代码按 `controller -> service -> mapper` 依赖。`controller` 不直接访问 Mapper，`entity` 不出现在 Controller 的入参或出参中。

新增业务模块至少应明确：接口、DTO/Query、Entity、Mapper、Service、ServiceImpl、ConvertMapper、错误码和测试。简单只读接口可以省略不需要的文件，但不得为了凑目录创建空类。

## 4. Controller 和 API 规范

### 路径

- 根路径统一为 `/api`。
- 学生端：`/api/student/{resource}`。
- 管理端：`/api/admin/{resource}`。
- 公共能力：`/api/common/{resource}`。
- URL 全部使用小写；多词使用 `/` 或 `-`，禁止驼峰。

示例：`GET /api/student/course/schedule`、`POST /api/student/affair/apply`。

### HTTP 方法

为兼容现有参考项目，当前仅使用 `GET`、`POST`、`DELETE`：

| 方法 | 用途 |
|---|---|
| `GET` | 无副作用的详情、列表、统计、状态查询 |
| `POST` | 创建、更新、分页查询、导出、确认、重试等操作 |
| `DELETE` | 删除或撤销操作 |

如未来要开放标准 REST API，必须先统一版本和客户端约定，不得在同一模块混用两套风格。

### 入参、出参和响应

- 入参使用请求 DTO 或 Query；禁止接收 Entity。
- 出参使用专用 Response DTO/VO；禁止返回 Entity。
- 写操作使用 `@Valid`，字段约束写在 DTO 上；Service 仍需做权限和业务校验。
- 所有接口返回统一的 `Result<T>`，成功和失败结构保持稳定。
- 列表默认返回 `List<T>`；明确需要分页时才返回分页对象，并统一字段名 `pageNum/pageSize/total/records`。
- 分页 Entity 转 DTO 必须使用统一转换工具或 MapStruct，禁止 Controller 手写循环拼装。
- `@Operation` 必须指定全局唯一的 `operationId`，使用 `{动词}{实体}` 命名，例如 `getStudentSchedule`、`createRepairApplication`。

### 事务和幂等

- 涉及多个表、状态流转或外部系统写入的 Service 方法必须明确事务边界。
- 申请、报修、缴费回调等可能重复提交的接口必须使用幂等键或业务唯一键。
- 外部调用成功但本地落库失败时，必须有重试、补偿或人工处理状态，不能只依赖一次同步请求。

## 5. 数据库规范

### 命名

- 表名和字段名使用小写下划线。
- 系统表使用 `sys_` 前缀；学生业务表使用 `stu_`；校园事务表使用 `affair_`；知识和 AI 相关表使用 `ai_` 或 `kb_`。
- 主键字段统一使用 `id` 或项目决定的统一 ID 方案；同一项目不得混用自增、UUID 和雪花 ID。
- 时间字段使用 `created_at`、`updated_at`；状态字段使用明确的枚举值，不用魔法数字。
- 所有表必须有主键、创建时间和更新时间；需要审计的表增加创建人、更新人和逻辑删除字段。

### 数据访问

- Entity 只描述持久化结构；展示字段、聚合字段放在 DTO/VO。
- 查询必须带有明确条件，禁止无条件查询大表。
- 列表接口必须限制最大 `pageSize`，导出接口必须异步化或设置上限。
- 事务状态机要显式定义允许的状态迁移，禁止从 Controller 直接修改状态字段。
- 学号、身份证号、手机号、成绩、缴费记录等敏感字段按最小必要原则读取和展示；日志中禁止输出完整值。
- 所有数据库变更必须有可审查的 DDL 或迁移脚本，不允许只在本地手工改库。

## 6. 认证、授权和隐私

- 学生身份优先接入学校统一身份认证；微信登录只作为入口适配，不替代学校身份确认。
- 权限至少区分学生、辅导员/教师、学院管理员、系统管理员。
- 资源访问必须校验“当前用户是否拥有该资源”，不能只校验登录状态。
- 管理端和学生端使用不同的权限范围、菜单和接口拦截规则。
- Token、Cookie、Session 的有效期、刷新、注销和并发登录策略必须写入配置和文档。
- 密码、JWT 密钥、模型 API Key、数据库密码、云服务凭证不得进入 Git；使用环境变量、密钥服务或部署平台 Secret。
- 任何日志、异常、AI 提示词和埋点都要避免泄露完整身份证号、手机号、成绩、家庭信息和认证凭证。
- 文件上传必须校验大小、扩展名、MIME、内容和访问权限；下载使用短时签名 URL。
- 生产环境关闭调试接口、SQL 详细日志和默认管理员账号。

## 7. AI 调用规范

### 服务边界

```text
Controller -> AiChatService -> AiProvider -> 外部模型 API
                     |
             会话/审计/限流/工具授权
```

- 前端只能提交用户消息、会话 ID 和业务上下文引用，不能提交任意 `systemPrompt`、模型 API Key 或工具权限。
- `AiProvider` 屏蔽 DashScope、DeepSeek、智谱等供应商差异；供应商切换不应影响业务 Service。
- 模型调用必须设置连接超时、读取超时、最大输入长度、最大输出长度和重试次数。
- 重试只允许针对明确的网络临时错误；禁止对业务拒绝、权限错误或内容安全错误盲目重试。
- 流式输出使用 SSE 时，必须处理客户端断开、超时和最终落库状态。

### 提示词和工具

- 系统提示词由后端按场景维护并版本化，代码或配置中不得让用户覆盖核心安全约束。
- AI 回答校园政策时必须尽量附带来源、文档版本和更新时间；没有依据时明确说明无法确认。
- 工具调用采用白名单注册，每个工具定义参数 Schema、权限、审计事件和超时。
- AI 只能提出工具调用；真正执行由后端校验身份、权限、参数、业务状态后完成。
- 创建申请、撤销、退选、缴费等有副作用的动作必须二次确认，并记录操作者、原始参数、执行结果和关联业务单号。
- 不能把模型输出直接拼接成 SQL、脚本、HTML 或可执行命令。

### 会话和审计

- 会话记录至少包含 `conversation_id`、用户 ID、模型、提示词版本、请求状态、耗时、token 用量和错误码。
- 原始消息是否持久化要按学校隐私政策配置；默认只保存业务所需最小内容，并设置保留期限。
- 对 Prompt Injection、越权请求、敏感信息询问和模型幻觉建立测试用例。
- 生产环境提供模型调用限流、单用户配额、降级回复和人工转接能力。

## 8. 外部校园系统集成

- 每个教务、财务、宿管、报修、统一身份系统都通过独立 Adapter/Facade 接入。
- 业务层依赖项目内部接口，不直接依赖对方系统的 DTO、状态码或 SDK 类型。
- 外部系统字段映射、错误码、超时、重试、签名和幂等规则写入适配器文档。
- 读取接口要区分“无数据”“系统暂不可用”和“无权限”，不得统一返回空值。
- 写入接口要保存请求流水号、外部单号和最终同步状态，支持补偿查询。
- 外部系统不可用时，AI 应明确告知“暂时无法查询/办理”，不得根据旧缓存伪装实时结果。

## 9. 异常、日志和监控

- 业务错误使用统一 `ApplicationException`/错误码；由全局异常处理器转换为 `Result.failed(...)`。
- 不在 Service 中返回失败响应对象或吞掉异常；异常必须向上抛出。
- 抛出业务异常前记录必要上下文，但不得记录密码、Token、完整身份证号或完整 AI 敏感内容。
- 禁止 `System.out.println`、`e.printStackTrace()` 和空 catch。
- 关键日志包含 `traceId`、用户类型、用户 ID（脱敏）、业务单号和外部请求流水号。
- 监控至少覆盖接口耗时、错误率、外部系统失败率、AI 调用耗时/失败率、Redis/MySQL 连接池和队列积压。
- 健康检查只能暴露必要信息；不要在接口中返回 API Key、数据库连接串或堆栈详情。

## 10. 测试规范

- 每个业务 Service 至少覆盖正常路径、参数边界、权限失败、重复提交和外部依赖失败。
- 状态机、权限判断、幂等逻辑和 AI 工具授权必须有单元测试。
- Controller 使用 MockMvc 验证请求校验、认证失败、响应结构和错误码。
- 外部模型、教务和财务系统使用 Mock/Contract Test，不在单元测试中真实消耗线上 API 配额。
- 数据库查询和关键事务增加集成测试；上线前执行一条完整的学生办理流程回归测试。
- 测试不得使用生产数据、真实凭证或真实学生个人信息。

## 11. 代码评审和交付

每次修改代码后都要完成以下检查：

1. 编译和相关测试通过。
2. 检查认证授权、敏感数据、参数校验、幂等和事务边界。
3. 检查外部调用的超时、重试、降级和错误映射。
4. 检查日志是否泄露隐私，是否存在 `System.out`、空 catch 或吞异常。
5. 检查 OpenAPI、数据库 DDL、README 和本规范是否需要同步。

Code Review 发现的问题按 `P0/P1/P2/P3` 记录，必须包含文件、行号、影响、修复建议和状态。P0/P1 问题未处理时不得合并或部署。

提交信息使用：

```text
feat: add repair application query
fix: prevent duplicate affair submission
refactor: isolate ai provider adapter
docs: update campus integration contract
test: add student authorization cases
```

一次提交只解决一个清晰主题，禁止把格式化、无关重构和业务修改混在一起。

## 12. 文档和配置同步

发生以下变化时必须同步文档：

| 变化 | 同步内容 |
|---|---|
| 新增/删除模块 | 目录、接口清单、权限矩阵 |
| 新增外部系统 | Adapter 契约、配置项、错误码、重试策略 |
| 新增 AI 工具 | 参数 Schema、权限、确认流程、审计事件 |
| 数据库变更 | DDL/迁移脚本、Entity、接口说明 |
| 配置变更 | 环境变量清单、开发/测试/生产说明 |
| 安全策略变更 | 认证、授权、隐私和日志规则 |

建议后续补充以下文档：

```text
README.md                         # 项目介绍、启动和部署
docs/architecture.md              # 架构和模块边界
docs/api-conventions.md           # API 和错误码
docs/auth-and-permissions.md      # 认证、角色、权限矩阵
docs/campus-integrations/         # 各校园系统适配器契约
docs/ai-policy.md                 # 模型、提示词、工具和数据保留策略
db/migrations/                    # 数据库迁移脚本
```

## 13. 当前不做的事情

- 不为简单的外部 API 问答单独部署 Python 服务。
- 不在第一阶段引入微服务、服务网格或 Kubernetes。
- 不允许小程序直接调用模型供应商 API。
- 不允许把 AI 当作绕过权限的万能入口。
- 不在没有学校接口和明确数据授权的情况下承诺实时查询或自动办理。

## 14. 开发命令约定

项目工程建立后，应提供并保持以下命令可用：

```bash
./mvnw clean compile
./mvnw test
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw clean package -DskipTests
docker build -t student-hub-api:dev .
```

所有命令都应在 README 中注明 JDK、数据库、Redis、必要环境变量和外部 API Mock/沙箱配置。开发环境不得依赖生产凭证。
