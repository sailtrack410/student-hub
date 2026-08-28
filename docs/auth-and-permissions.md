# 认证与权限设计

## 身份模型

一个 `sys_user` 可以加入多个组织并拥有多个角色。角色分配可以绑定组织和数据范围，因此同一用户可以同时作为服务申请人、教师和部门管理员。

基础关系：

```text
sys_user
├── sys_user_organization ── sys_organization ── sys_campus
└── sys_user_role ── sys_role ── sys_role_permission ── sys_permission
```

## 平台会话

平台使用随机 256 bit 不透明 Bearer Token。Token 不包含用户信息，服务端会话保存用户、角色、权限和角色作用范围。

- 本地开发和测试：内存会话。
- Docker 和生产：Redis 会话。
- Redis Key 只包含 Token 的 SHA-256 指纹。
- 退出登录立即删除服务端会话。
- 默认有效期为 8 小时，可通过 `SESSION_TTL` 调整。
- 修改用户角色或停用账号时，用户管理模块必须撤销该用户已有会话。

## 权限判断

权限分三层：

1. 功能权限：通过 Spring Security Authority 和 `@PreAuthorize` 判断。
2. 数据范围：根据当前角色的 `organizationId + dataScope` 构造查询条件。
3. 流程权限：由业务状态机判断当前状态是否允许该动作。

`data_scope` 当前约定：

| 值 | 含义 |
|---|---|
| `SELF` | 仅本人创建的数据 |
| `ASSIGNED` | 仅分配给本人的任务 |
| `ORGANIZATION` | 当前作用组织 |
| `ORGANIZATION_AND_CHILDREN` | 当前作用组织及下级 |
| `CAMPUS` | 当前校区 |
| `ALL` | 全部数据 |

业务查询不得只因为用户拥有某角色就返回全表数据。

## 身份标识与权限隔离

`sys_user.identity_type` 是身份标识（STUDENT 学生 / TEACHER 教师 / ADMIN 管理员 / LOGISTICS 后勤职工），用于按身份提供差异化功能与信息视图。

- 学生、教师可在小程序"个人中心"自行申报身份（`POST /api/app/profile/identity`）；ADMIN、LOGISTICS 由平台分配，接口拒绝自行设置。
- 身份标识不等于功能权限：接口与数据访问仍由角色（`sys_user_role`）、权限码和数据范围控制；身份只做前端功能隔离与业务分组。
- 身份或昵称修改会原地刷新服务端会话（`SessionStore.replace`，保留剩余有效期），`/me` 立即生效，无需重新登录。

## 教务系统会话

教务系统账号密码仅用于当次登录：

1. 小程序通过 HTTPS 提交教务账号密码。
2. 后端登录教务系统并获取 Cookie、CSRF Token 等短期 Session。
3. 账号密码不写数据库、Redis或日志。
4. 教务 Session 加密后存 Redis，并设置不超过上游有效期的 TTL。
5. Session 失效后要求用户重新认证，不尝试使用已丢弃的密码自动续期。

教务 Session 不返回客户端，也不能用作 Student Hub 的平台登录凭证。
