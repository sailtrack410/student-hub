# Student Hub API

学生自助服务与校园事务智能助手的后端基础工程。

## 当前基础能力

- Spring Boot 3 + Java 17 + Maven
- 统一 `Result<T>` 响应和全局异常处理
- Spring Security、多角色权限、组织数据范围和不透明服务端会话
- Flyway 数据库迁移与开发环境种子数据
- MySQL、Redis 配置及本地 Docker Compose 依赖
- Knife4j 接口调试：`/doc.html`；Swagger UI：`/api`
- 健康检查：`GET /api/common/system/health`
- 外部模型 API 的 `AiProvider` 抽象和 OpenAI-compatible 适配器
- AI 默认关闭；配置 `AI_ENABLED=true` 后才允许调用外部模型

## 本地启动

环境要求：JDK 17、Maven 3.9+、MySQL 8、Redis 7。

```bash
cp .env.example .env
docker compose up -d mysql redis
set -a; source .env; set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

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

当前提供 Provider 和 Service 基础抽象；Prompt 版本管理、AI 工具调用及正式校园身份适配将在明确外部系统契约后接入。

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
| 开发/测试 | `mock` | 不请求微信接口，非空且不以 invalid 开头的 code 可用于 Mock 登录 |
| 生产 | `real` | 真实调用微信 `jscode2session` 接口 |

生产环境还需配置：

```bash
WECHAT_MODE=real
WECHAT_APP_ID=你的小程序AppID
WECHAT_APP_SECRET=你的小程序AppSecret
```

可选超时配置（单位：秒）：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `WECHAT_CONNECT_TIMEOUT_SECONDS` | `5` | 连接微信接口的建立超时 |
| `WECHAT_READ_TIMEOUT_SECONDS` | `10` | 等待微信接口响应的读取超时 |

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
