# AI 跨领域查询边界

本轮保留模块单体，以 AI 意图数据装配路径作为第一批边界。
`AiAssistantDataServiceImpl` 通过领域拥有的只读接口查询信息，不直接注入其他领域的 Mapper，
也不接收其持久化实体。

| 数据所属领域 | 查询契约 | 对外数据与约束 |
| --- | --- | --- |
| 教务认证 | `JwBindingQuery` | 只查询最近同步的学期编码；不读取账号凭据 |
| 课表 | `ScheduleQuery` | 按用户返回课程和学期快照；使用不可变记录与列表 |
| 服务申请 | `ServiceRequestQuery` | 按申请人和状态筛选最近记录；查询上限 20 条；不输出申请表单内容 |
| 服务目录 | `ServiceCatalogQuery` | 按校区和编码读取启用的服务项，仅返回 ID 与名称 |

查询实现留在数据所属模块内部，持有本模块 Mapper，并使用只读事务。
AI 保留日期、教学周、单双周匹配和动作卡片装配逻辑；申请的状态筛选、用户隔离和数量上限
由申请领域维护。既有服务调用与数据库迁移保持原有部署方式。

`AiModuleBoundaryTest` 扫描已编译 AI 类，禁止引用其他领域 Mapper，并检查意图装配依赖和
查询投影。`AiQueryBoundaryIntegrationTest` 验证用户隔离、课程日期与单双周、申请筛选及上限、
服务项校区及停用规则和教务绑定逻辑删除。

```sh
mvn -q -Dtest=AiQueryBoundaryIntegrationTest,AiModuleBoundaryTest test
```

后续跨模块数据查询沿用 owner query/application 接口；可逐个领域扩展架构规则，
避免把一次边界收敛扩大为全项目重构。
