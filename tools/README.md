# 工具脚本

## admin_smoke.py

管理端 Campus OS 冒烟脚本（Playwright / Chromium）。覆盖真实登录页、30 条路由、校区上下文与切换、广告/广告主/审计关键元素；逐页截图并输出 `artifacts/admin-smoke/summary.json`。

依赖：

```bash
python3 -m pip install playwright
python3 -m playwright install chromium
```

运行（需后端已在 9000 端口启动）：

```bash
python3 tools/admin_smoke.py
ADMIN_BASE_URL=http://127.0.0.1:9000 ADMIN_USERNAME=admin ADMIN_PASSWORD='DevOnly123!' python3 tools/admin_smoke.py
```

退出码 0 表示全部通过；失败时打印失败路由与 console error。截图与 summary 默认写入 `artifacts/admin-smoke/`，可用 `ADMIN_SMOKE_OUT` 覆盖。

## seed_app_demo.py

按真实接口链路生成 App 演示数据：提交申请 → 调度派单 → 维修员开始 → 现场/完工照片 → 报修人确认 → 评价，生成“已提交 / 处理中 / 待验收 / 已办结并评价”四条记录。

依赖：Python 3、`requests`。

运行（需 `dev` 后端已在 9000 端口启动）：

```bash
python3 tools/seed_app_demo.py
python3 tools/seed_app_demo.py --base-url http://127.0.0.1:9000 --run-id demo-20261022
```

使用 dev 账号 `requester / dispatcher / worker`（统一密码 `DevOnly123!`）。脚本会真实上传 1×1 PNG 作为图片，不伪造外部 URL；重复用不同 `--run-id` 运行会新增记录。
