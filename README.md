# Student Hub API

学生自助服务与校园事务智能助手的后端基础工程。

## 当前基础能力

- Spring Boot 3 + Java 17 + Maven
- 统一 `Result<T>` 响应和全局异常处理
- MySQL、Redis 配置及本地 Docker Compose 依赖
- OpenAPI 文档：`/api/swagger-ui.html`
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

当前只提供 Provider 和 Service 基础抽象，学生身份认证、会话持久化、Prompt 版本管理和工具调用将在明确校园系统契约后接入。

## 目录约定

详细设计规则见 [CLAUDE.md](CLAUDE.md)。后续学生、事务、知识库和管理模块按 `controller -> service -> mapper` 分层，并通过 `integration` 适配外部系统。
