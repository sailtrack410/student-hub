# Student Hub —— Nginx 反向代理部署说明

本目录提供 Student Hub 的 Nginx 反向代理模板与部署步骤。目标是把 80/443 的 HTTPS 流量代理到
本机 Spring Boot（默认 `127.0.0.1:9000`），并针对 **SSE 流式对话、WebSocket 社区推送、300MB 安装包上传、
管理台/大屏 SPA 缓存策略** 做专门配置。

```
浏览器 / 微信小程序 / Flutter App
        │  HTTP :80（启用 HTTPS 后 301 → :443）
        ▼
    Nginx :443（TLS 终止、gzip、缓存策略、SSE/WS 特殊处理）
        │  HTTP/1.1 → 127.0.0.1:9000
        ▼
    Spring Boot（server.port=${SERVER_PORT:9000}）
```

## 0. 快速开始（最短路径）

```bash
sudo cp deploy/nginx/student-hub.conf /etc/nginx/conf.d/student-hub.conf
sudo sed -i 's/student-hub\.example\.com/<你的域名>/g' /etc/nginx/conf.d/student-hub.conf
sudo nginx -t && sudo systemctl reload nginx          # -t 不通过就不要 reload
curl -sS https://<你的域名>/api/common/system/health   # 返回 Result JSON 即代理成功
```

HTTPS、上传大小、SSE/WebSocket 验证、排错见下文对应章节。

## 1. 目录与文件

| 文件 | 说明 |
|---|---|
| `deploy/nginx/student-hub.conf` | 反向代理模板。包含 `upstream` / `map` / `server`，属于 **http 上下文** 配置 |
| `deploy/nginx/README.md` | 本文档 |
| `docker-compose.yml` | 末尾有「可选 nginx 容器」注释示例（默认不启用，不影响现有启动） |

## 2. 与后端实现的对应关系（改配置前先看）

| 路径 | 后端行为 | Nginx 特殊处理 |
|---|---|---|
| `/admin`、`/admin/`、`/screen`、`/screen/` | `AdminWebConfig` 把四个入口 forward 到 `index.html`；静态文件放在 `static/admin/`、`static/screen/` | 入口 HTML `no-store`，静态资源 `expires 30d` |
| `/api/**` | 统一 `Result<T>` JSON；`/api/common/assets/**` 自带 7 天缓存、`/api/common/files/view/**` 自带 30 分钟私有缓存 | 普通 API `no-store`；素材/签名文件两个前缀单独放行，保留后端缓存头 |
| `/api/app/ai/conversations/{id}/chat-stream` | SSE（`SseEmitter(180_000L)`，位于 `/api/app/ai/` 前缀下） | `proxy_buffering off; gzip off; read_timeout 600s` |
| `/api/admin/ai/chat-stream` | 管理端 SSE 测试（`SseEmitter(180_000L)`） | 同上，精确路径单独配置 |
| `/ws/community` | WebSocket 社区实时推送，`CommunityWebSocketConfig` 注册；握手需 `?token=<accessToken>` | `Upgrade`/`Connection` 头，`read_timeout 3600s` |
| `/api/admin/app-versions/{id}/package` | 安装包上传，**原始字节流**（非 multipart），后端上限 **300MB**，校验 `.apk/.ipa/.hap` 与 ZIP 魔数 | `client_max_body_size 320m` |
| `/api/app/files`、`/api/admin/knowledge-bases/{id}/documents` 等 | 走 multipart，受 Spring 限制：单文件 5MB / 请求 20MB（`application.yaml`） | Nginx 不拦，但后端仍会 413/400；需放大时改后端 `FILE_MAX_SIZE_MB` |

> 说明：仓库内 URL 都是相对路径（如 `/api/common/files/view/...`、`/api/public/app-version/{id}/package`），
> 因此反向代理不需要改写响应体里的绝对地址。

## 3. 前置条件

1. 后端已启动并监听 9000：
   ```bash
   curl -sf http://127.0.0.1:9000/api/common/system/health   # 返回 Result JSON 即正常
   ```
2. 已安装 nginx（`nginx -v`）。**`http2 on;` 需 nginx ≥ 1.25.1**；更旧版本请改用
   `listen 443 ssl http2;`（模板里已注明）。
3. 域名 A/AAAA 记录已指向本机；安全组/防火墙放行 80、443（如启用 HTTPS 且做 301，80 也要放行）。
4. 证书：Let's Encrypt（推荐 certbot）或已有证书文件；**不要把私钥提交到仓库**。

## 4. 部署步骤

### 4.1 方式 A：复制到 `conf.d`（推荐，单机部署）

```bash
# 1) 复制配置（conf.d/*.conf 会被 nginx.conf 自动 include 到 http{} 内）
sudo cp deploy/nginx/student-hub.conf /etc/nginx/conf.d/student-hub.conf

# 2) 改域名（把示例域名换成真实域名；无域名可填公网 IP 或 _）
sudo sed -i 's/student-hub\.example\.com/<你的域名>/g' /etc/nginx/conf.d/student-hub.conf

# 3) 若后端端口不是 9000，同步改 upstream（与 .env 的 SERVER_PORT 保持一致）
#    sudo sed -i 's/127\.0\.0\.1:9000/127.0.0.1:<SERVER_PORT>/' /etc/nginx/conf.d/student-hub.conf

# 4) 语法检查通过后再重载（见第 8 节）
sudo nginx -t
sudo systemctl reload nginx
```

### 4.2 方式 B：include 到 `nginx.conf`

在 `/etc/nginx/nginx.conf` 的 **`http { ... }`** 块内追加：

```nginx
include /绝对路径/student-hub/deploy/nginx/student-hub.conf;
```

> ⚠️ 不要 include 到 `server { }` 内：本文件含 `upstream`/`map`/`server`，这些指令只允许出现在 http 上下文，
> 否则 `nginx -t` 报 `"upstream" directive is not allowed here`。

### 4.3 必改项 Checklist

| 位置 | 现值 | 说明 |
|---|---|---|
| `server_name` | `student-hub.example.com` | 改成真实域名/公网 IP |
| `upstream → server` | `127.0.0.1:9000` | 与后端 `SERVER_PORT` 一致 |
| `ssl_certificate` / `ssl_certificate_key` | 注释中的占位路径 | 启用 HTTPS 时填写真实证书；仓库不存放证书 |
| 80 → 443 | 注释的 `return 301` | 启用 HTTPS 后开启，并关闭 80 server 的业务监听 |
| `server_name`（协议跳转段） | `student-hub.example.com` | 与主 server 保持一致 |

### 4.4 端口与环境变量 `SERVER_PORT`

- 后端端口来自 `application.yaml` 的 `server.port: ${SERVER_PORT:9000}`，本地 `.env` 里是 `SERVER_PORT=9000`。
- **Nginx 不会读取环境变量**：`upstream` 必须写具体端口。若改了 `SERVER_PORT`，需同步编辑
  `upstream` 行并 `reload`。也可以用 `envsubst` 渲染（模板中的端口是字面量，渲染前需自行把端口改成
  `${SERVER_PORT}` 占位，再 `envsubst '${SERVER_PORT}'`）。
- `docker-compose.yml` 里 `api` 服务的端口映射是 `${SERVER_PORT:-9000}:9000`，容器内始终 9000；
  如果把 Nginx 也放进 compose，upstream 应改成服务名 `api:9000`（见第 12 节）。

## 5. 300MB 安装包上传

- 上传接口：`POST /api/admin/app-versions/{id}/package?fileName=xxx.apk`
  - `Content-Type: application/octet-stream`，body 即文件字节；**不是 multipart**，因此不受后端 5MB multipart 限制。
  - 后端上限 300MB（`AppVersionService.MAX_PACKAGE_BYTES`），所以模板把 `client_max_body_size` 设为 `320m`。
- Nginx 默认 `client_max_body_size 1m`；不改会直接返回 **413**。
- 默认 `proxy_request_buffering on`：Nginx 会先把 300MB 落到临时文件再转给后端。请确认临时目录磁盘空间
  （默认 `/var/lib/nginx/tmp` 或 `/tmp`，见 nginx.conf 的 `client_body_temp_path`）至少空闲 ~400MB。
- 用「大文件上传」做验证：
  ```bash
  TOKEN='<管理端 accessToken>'
  curl -sS -X POST "https://<域名>/api/admin/app-versions/1/package?fileName=app-release.apk" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/octet-stream' \
    --data-binary @app-release.apk
  ```
  期望：`{"success":true,...}` 且返回 `packageSize`/`packageSha256`；若 413 → 检查 `client_max_body_size`；
  若 401 → token/权限（该接口需 `platform:all` 或 `system:user:manage`）。

> 注意：`/api/app/files`、知识库文档上传等 **multipart** 接口仍受后端 5MB/20MB 限制。若业务需要放大，
> 需调整后端 `FILE_MAX_SIZE_MB` 与 `spring.servlet.multipart.max-request-size`（本工程约定不改后端，只在此说明）。

## 6. SSE 流式接口验证

关键配置（模板已含）：`proxy_buffering off; proxy_cache off; gzip off; proxy_read_timeout 600s;`。
后端 SSE 最长 180s，600s 有足够余量；若中间还套了 CDN/WAF，也要关闭其缓冲，否则前端会「等很久一次性出全文」。

```bash
# 管理端 SSE（需 platform:all 或 ai:manage）
curl -N -sS -X POST "https://<域名>/api/admin/ai/chat-stream" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"你好"}]}'

# App 端 SSE（需 ai:chat；conversationId 先用 POST /api/app/ai/conversations 创建）
curl -N -sS -X POST "https://<域名>/api/app/ai/conversations/1/chat-stream" \
  -H "Authorization: Bearer $APP_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"content":"你好"}'
```

判定标准：
- 正常：输出逐行出现 `event:delta`，字/词级增量刷新（`curl -N` 禁用 curl 自身缓冲）。
- 一次性输出：Nginx `gzip off`/`proxy_buffering off` 未生效，或前面还有 CDN 缓冲。
- 连接 60s 左右被断：`proxy_read_timeout` 太小（应 ≥ 600s）。
- 返回 `401 {"code":"AUTH_401"}`：请求已到达后端，只是缺 token 或权限（`/api/admin/ai/chat-stream` 需
  `platform:all`/`ai:manage`，App 端需 `ai:chat`）——说明代理链路正常，先补 token 再测流式效果。

## 7. WebSocket 验证

```bash
# 方式 1：wscat（Node，npx 即可）
npx wscat -c "wss://<域名>/ws/community?token=<accessToken>"

# 方式 2：不装工具，直接看握手状态码（期望 HTTP/1.1 101）
curl -i -N \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Version: 13" \
  -H "Sec-WebSocket-Key: $(openssl rand -base64 16)" \
  "https://<域名>/ws/community?token=<accessToken>"
```

| 现象 | 原因/处理 |
|---|---|
| 101 Switching Protocols | 正常，Nginx Upgrade 配置生效 |
| 400 `Can "Upgrade" only to "WebSocket".` | 请求没带 `Upgrade: websocket` 头（用普通 GET 探测会这样，属正常）；若已带 `Upgrade` 仍 400，检查 Nginx 是否透传了 `Upgrade`/`Connection` |
| 400 / 426 Upgrade Required | `proxy_set_header Upgrade/Connection` 缺失或被上游覆盖 |
| 连接后立刻被关闭、reason=invalid session | 缺 `?token=`，或 token 已失效（后端从 query 取 token 校验） |
| 一段时间无消息即断 | `proxy_read_timeout` 太短（模板 3600s）；或中间设备有 idle timeout |

> 直连后端自检（不经过 Nginx）：`curl -s http://127.0.0.1:9000/ws/community` 返回
> `400 Can "Upgrade" only to "WebSocket".` 说明路由存在；经 Nginx 用带 `Upgrade` 头的请求应得到 101。

## 8. 配置校验与生效

```bash
# 宿主机安装
sudo nginx -t && sudo systemctl reload nginx    # reload 不断开现有连接；不要用 restart

# 容器化（若按第 12 节启用 nginx 容器）
docker compose exec nginx nginx -t
docker compose exec nginx nginx -s reload
```

- `nginx -t` 必须输出 `syntax is ok` + `test is successful` 才可 reload；失败时保留旧配置继续服务，不要 reload。
- 日志：`/var/log/nginx/access.log`、`/var/log/nginx/error.log`（或 `docker compose logs nginx`）。
- 修改配置前先备份：`sudo cp /etc/nginx/conf.d/student-hub.conf{,.bak.$(date +%F)}`。

## 9. 反向代理后的 Spring 注意事项（forwarded headers）

模板已转发：`Host`、`X-Real-IP`、`X-Forwarded-For`、`X-Forwarded-Proto`、`X-Forwarded-Host`、`X-Forwarded-Port`。

1. **Spring Boot 默认不信任这些头**（`server.forward-headers-strategy=none`），因此
   `request.getRemoteAddr()` 会一直是 `127.0.0.1`。需要真实 IP/协议时，在不改代码的前提下设置环境变量：
   ```bash
   SERVER_FORWARD_HEADERS_STRATEGY=framework      # 等价 server.forward-headers-strategy=framework
   ```
   （`docker-compose.yml` 的 `api.environment` 或 `.env` 都可以加，然后重启后端。本任务约束不改后端，
   故这里只作为部署建议。）
2. 后端已有两处 **手工读取** `X-Forwarded-For / X-Real-IP`，不依赖上面的开关：
   - `IdentityAuthServiceImpl.registerDeviceQuietly(...)`：登录设备登记的真实 IP；
   - `PublicScreenController.clientKey(...)`：大屏公开接口的 IP 限流。
   两处都**取 X-Forwarded-For 的第一段**。
3. 因此如果 Nginx 是最外层入口，推荐把模板里的
   `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;` 改成 `$remote_addr`（覆盖客户端伪造值）。
   如果前面还有 CDN/SLB，则保留 `$proxy_add_x_forwarded_for`，并配置：
   ```nginx
   set_real_ip_from <CDN/SLB 回源网段>;
   real_ip_header X-Forwarded-For;
   ```
   只信任自己的边缘节点，否则限流/审计 IP 可被伪造。
4. WebSocket 的 `Upgrade`/`Connection` 不能写死，模板用 `map $http_upgrade $connection_upgrade` 动态取值。

## 10. HTTPS 启用

### 方式 A：certbot（推荐）

```bash
sudo apt install certbot python3-certbot-nginx   # 或对应发行版包管理
sudo certbot --nginx -d <你的域名>               # 自动改写 listen 443 ssl + 证书路径
sudo certbot renew --dry-run                     # 验证自动续期
```

启用后：
- 按模板末尾注释启用「HTTP → HTTPS 301」段，把 80 端口改为纯跳转；
- HSTS 建议全站确认 HTTPS 可用后再开（模板中已注释 `Strict-Transport-Security`）。

### 方式 B：手工证书

把模板中 `listen 80;` 改为 `listen 443 ssl;`（`[::]` 同理），填写 `ssl_certificate` /
`ssl_certificate_key`，并按注释启用 `http2 on;`、TLS 版本等。

### 关于 HTTP/2

- nginx ≥ 1.25.1：模板注释段里的 `http2 on;`（与 `listen 443 ssl;` 同 server 即可）；旧版本改用
  `listen 443 ssl http2;`。
- 开启后静态资源与 API 走 h2；SSE 在 h2 下同样生效；WebSocket 仍按 HTTP/1.1 Upgrade 完成协议升级
  （浏览器/客户端自动处理，Nginx 配置无需改动）。
- 校验：`curl -sI --http2 https://<域名>/admin/` 首行应为 `HTTP/2 200`（本地 curl 需带 HTTP/2 支持）。

## 11. 常见问题

| 现象 | 排查 |
|---|---|
| 上传安装包 413 | `client_max_body_size` 未生效（是否改到了 server/location 外的文件、是否 reload）；或中间 CDN 有自己的上传上限 |
| 502 Bad Gateway | 后端未启动/端口不一致：`curl -sf http://127.0.0.1:9000/api/common/system/health`；`error.log` 里 `connect() failed` |
| 504 Gateway Timeout | 长任务被 `proxy_read_timeout` 截断：普通接口 120s、SSE 600s、WebSocket 3600s；特别慢的同步接口可单独调大 |
| SSE 一次性输出 | 检查 `/api/app/ai/`、`/api/admin/ai/chat-stream` 两段是否 `proxy_buffering off; gzip off;`，以及 CDN 缓冲 |
| 管理台发版后仍是旧页面 | 入口 HTML 必须 `no-store`；静态资源用 `expires 30d`，发版时改文件名或加 `?v=` |
| 后端看到的所有 IP 都是 127.0.0.1 | 未启用 `SERVER_FORWARD_HEADERS_STRATEGY=framework`（见第 9 节） |
| `/admin/` 404 | 后端 `AdminWebConfig` 只转发 `/admin`、`/admin/`；检查 Nginx 的 `location ~ ^/(?:admin|screen)...` 正则是否被其他 location 抢走（`nginx -T` 看最终配置顺序） |
| 配置改动不生效 | 忘记 `systemctl reload nginx`；或改了 `conf.d` 之外的其他文件 |
| `nginx -t` 报 `socket() [::]:80 failed (97: Address family not supported)` | 宿主机/nginx 未启用 IPv6：删除模板中的 `listen [::]:80;`（以及 HTTPS 段的 `[::]:443`） |

## 12. 可选：docker-compose 方式

`docker-compose.yml` 末尾附带了「nginx 容器」注释示例，默认不启用。启用时注意：

1. `upstream` 要改成 compose 服务名：`server api:9000;`（容器内不能用 `127.0.0.1`）；
2. 挂载配置与证书目录（证书不要写进镜像/仓库）；
3. 若 `api` 已直接把 `${SERVER_PORT}` 映射到宿主机，可与 Nginx 并存，但建议生产只暴露 80/443，
   通过 Compose 内部网络访问 `api:9000`；
4. 容器内校验：`docker compose exec nginx nginx -t`。

## 13. 回滚

```bash
# 还原备份并重载；或直接删除 conf.d 下的配置
sudo cp /etc/nginx/conf.d/student-hub.conf.bak.<日期> /etc/nginx/conf.d/student-hub.conf
sudo nginx -t && sudo systemctl reload nginx
```

> 本次交付说明：开发机未安装 nginx，模板已做静态结构校验（括号平衡、11 个 location、必需指令与
> SSE/WS/缓存/安全头逐项齐全），**未执行 `nginx -t`**；部署机上请务必先 `nginx -t` 再 `reload`。
