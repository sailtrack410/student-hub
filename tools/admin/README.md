# 管理端资源构建

管理端沿用 Vue 3 与原有页面脚本，入口通过 `defineAsyncComponent` 按路由加载页面。
`src/page-loader.ts` 是增量 TypeScript 入口；现有页面可以继续逐页迁移。

```sh
npm --prefix tools/admin ci
npm --prefix tools/admin run build
npm --prefix tools/admin run check
```

在仓库根目录执行。`build` 编译严格类型检查的加载器，再运行
`tools/build_admin_assets.py` 扫描 `window.AdminPages` 注册、生成页面清单和内容哈希，
同步更新 `admin/index.html` 的资源 URL 与 import map。
JS/CSS 或页面注册发生变化后均需重新构建；`check` 检测生成物是否过期，不改文件。

加载器对同一文件去重，AI 配置页声明对 AI 测试页的依赖。未授权路由先完成权限判断，
再加载页面资源。共享错误提示只重载页面数据，不自动重放失败的写入请求。

本地浏览器回归（仓库根目录启动静态服务后）使用 `tools/web_release_smoke.py`。
该脚本显式模拟 API，验证下载页状态、无脚本访问、键盘操作及管理端资源加载与错误恢复；
后端发布事务另由 Maven 集成测试验证。
