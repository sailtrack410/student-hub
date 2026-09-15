# Windows 启动说明

1. 安装 JDK 17 或更高版本，并确认命令行可以执行 `java -version`。
2. 解压后保持 `.env`、`start-windows.cmd` 和 `target` 文件夹在项目根目录。
3. 双击 `start-windows.cmd`，或在命令行执行它。
4. 后台启动后访问 `http://localhost:9000/actuator/health`，返回 `{"status":"UP"}` 即正常。

本包已经包含可执行 JAR，不需要先安装 Maven。项目源码、数据库迁移和微信小程序源码分别位于 `src`、`student-hub-weapp` 等目录。

`.env` 含有运行所需配置，请勿公开上传。当前配置使用远程 MySQL、Redis、MinIO 和 PostgreSQL RAG 数据库；运行机器需要能够访问这些地址。

管理端：`http://localhost:9000/admin`
接口文档：`http://localhost:9000/doc.html`
