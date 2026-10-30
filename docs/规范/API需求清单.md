# student-hub App 移动端 API 需求清单

> **文档用途**：面向后端研发团队，详细定义支撑移动端画布新页面（Ardot 画布 `cocraft://localhost/file/736716979446631`）所需的**新增接口**、**已有接口变更**以及**全局规范声明**。  
> **制定日期**：2026-10-19（更新对齐审计清单 Items 31–44）  
> **联调核对基准**：本地后端 `student-hub-api`（端口 `9000`，状态 `UP`）与 Flutter 移动端 (`app/`)。  
> **当前状态总览**：  
> - **核心既有接口**：35 个控制器已全部就位并联调通过（登录认证、服务大厅目录、申请提交与完工评价、工单流转、社区发帖/点赞/评论/举报/私信、课表查询、勋章墙与佩戴）。  
> - **画布新增需求接口**：共 25 项新增定义与扩展规范（包含 Items 31–44 全部需求点，其中 Item 44 为私聊实时状态与回执），按优先级分批次落地。

---

## 接口实现与接入状态速览表 (对齐 Items 31–44)

| 需求项 | 模块 | 接口 / 规范定义 | 当前后端状态 | App 接入状态 | 归属分类 |
|---|---|---|---|---|---|
| **Item 31** | **教务服务** | `GET /api/app/grades` (学期成绩/GPA/同步态) | ✅ 已实现 | ✅ 已接入（示例兜底） | 核心新增 |
| **Item 32** | **服务大厅** | `GET /api/app/services/{id}` (增加 status/disabledReason/restoreAt) | ⏳ 待扩展 | 🟡 客户端走通用详情，补充下架态 | 接口扩展 |
| **Item 33** | **工单流转** | `GET /api/app/work-orders/{id}` (增加 appointmentTime/negotiateStatus) | ⏳ 待扩展 | 🟡 客户端已有改期展示位 | 接口扩展 |
| **Item 34** | **社区治理** | `moderationStatus` / `visibleScope` / `POST .../moderation/appeal` | ⏳ 待扩展 | 🟡 客户端展示审核中/申诉通道 | 接口扩展与新增 |
| **Item 35** | **通知中心** | `NotificationResponse` (增加 `relatedCard` 结构化实体) | ⏳ 待扩展 | 🟡 客户端已支持场景卡片渲染 | 接口扩展 |
| **Item 36** | **消息中心** | 4 大类未读汇总 | ✅ 已实现（`/api/app/messages/category-summary`） | ✅ 已接入 | 核心新增 |
| **Item 37** | **设备安全** | `GET /api/app/devices` & `POST /api/app/devices/revoke-others` | ✅ 已实现 | ✅ 已接入 | 核心新增 |
| **Item 38** | **AI 助手** | `POST /api/app/ai/.../chat-stream` (SSE流式/建议/卡片) | ✅ 后端已实现 | ✅ App 已接流式 | 体验增强 |
| **Item 38** | **AI 助手** | `GET /api/app/ai/recommendations` (2×2场景推荐指令) | ✅ 后端已实现 | ✅ App 已接接口 | 体验增强 |
| **Item 39** | **他人主页** | `GET /api/app/achievements/users/{userId}/badges` & 关注 | ⏳ 待开发 | 🟡 客户端走作者主页兜底 | 核心新增 |
| **Item 40** | **全局规范** | 动态表单 400 校验结构 `errors: [{field, message}]` | ⏳ 待规范 | ✅ 客户端表单校验已就绪 | 规范对齐 |
| **Item 41** | **账号安全** | 注册/找回密码/校园邮箱绑定策略声明 (`@stu.mycc.edu.cn`) | ✅ 已实现（真实 SMTP；开发态返回 devCode） | ✅ 已接入注册/找回/邮箱页 | 规范与能力 |
| **Item 42** | **端能力** | 客户端日历导出 (ICS) 与社区海报长图渲染 (本地 Canvas) | ✅ 纯端能力 | ✅ 客户端原生实现，不占后端接口 | 端能力声明 |
| **Item 43** | **用户偏好** | `GET/PUT /api/app/users/me/preferences` (偏好与推送开关) | ✅ 已实现（持久化） | ✅ 已接入设置页 | 核心新增 |
| **Item 44** | **私聊实时** | WebSocket `/ws/community` 增加 `DELIVERED` / `READ` / `TYPING` / `PRESENCE` 事件 | ✅ 已实现 | ✅ 已接入 | 核心新增 |
| - | **私聊实时** | `POST /api/app/community/messages/delivered` (批量送达回执) | ✅ 已实现 | ✅ 已接入 | 核心新增 |
| - | **私聊实时** | `POST /api/app/community/messages/read` 扩展 `messageIds` 并回推已读事件 | ✅ 已实现 | ✅ 已接入 | 接口扩展 |
| - | **私聊实时** | `GET /api/app/community/presence?userIds=` (批量在线状态) | ✅ 已实现 | ✅ 已接入（聊天页） | 核心新增 |
| - | **私聊实时** | `GET /api/app/community/messages/contacts` 增加 `online/lastSeenAt/unreadCount` | ✅ 已实现 | 🟡 客户端可接列表在线态 | 接口扩展 |
| - | **账号安全** | `GET/POST /api/app/profile/credentials`（自定义账号 ID + 密码登录） | ✅ 已实现 | ✅ 已接入（账号中心） | 已完成 |
| - | **勋章成就** | `GET /api/app/achievements/badges` (全量19枚勋章墙) | ✅ 已实现 | ✅ 已接入 (AchievementApi) | 已完成 |
| - | **勋章成就** | `POST /api/app/achievements/wear` (佩戴/卸下勋章) | ✅ 已实现 | ✅ 已接入 (AchievementApi) | 已完成 |
| - | **勋章成就** | `GET /api/app/achievements/profile` (成长档案等级进度) | ✅ 已实现 | ✅ 已接入 (AchievementApi) | 已完成 |
| - | **首页聚合** | `GET /api/app/home/dashboard` (首页仪表盘聚合BFF) | ✅ 已实现 | ✅ 已接入 | 已完成 |
| - | **消息聚合** | `GET /api/app/messages/inbox` (三路聚合收件箱流) | ✅ 已实现 | ✅ 已接入 | 已完成 |
| - | **工单催单** | `POST /api/app/service-requests/{id}/urge` (超时催单) | ✅ 后端已实现 | ✅ App 已接入（有真实 requestId 时） | 已完成 |
| - | **校园资产** | `GET /api/app/electricity?sn=` (iyunmu 电表查询) | ✅ 已实现 | ✅ 已接入（扫码/粘贴 SN） | 本轮新增 |
| - | **文件上传** | `POST /api/app/files/document` (PDF/DOCX/TXT/MD) | ✅ 已实现 | ✅ AI 附件已接 | 本轮新增 |
| - | **AI 语音** | `POST /api/app/files/audio` + `POST /api/app/ai/speech/transcribe` | ✅ 已实现 | ✅ 已接入 | 本轮新增 |

---

## 目录索引

- [一、新增接口清单 (New Endpoints)](#一新增接口清单-new-endpoints)
  - [1. 荣誉勋章与成就模块 (`/api/app/achievements`)](#1-荣誉勋章与成就模块-apiappachievements)
  - [2. 用户成长、偏好与社交关系模块 (`/api/app/users`, `/api/app/profile`)](#2-用户成长偏好与社交关系模块-apiappusers-apiappprofile)
  - [3. 设备与安全管理模块 (`/api/app/devices`)](#3-设备与安全管理模块-apiappdevices)
  - [4. 教务成绩查询模块 (`/api/app/jw/grades`) [Item 31]](#4-教务成绩查询模块-apiappjwgrades-item-31)
  - [5. 首页与校园广播聚合模块 (`/api/app/home`, `/api/app/announcements`)](#5-首页与校园广播聚合模块-apiapphome-apiappannouncements)
  - [6. 消息中心与流程通知模块 (`/api/app/messages`, `/api/app/service-requests`)](#6-消息中心与流程通知模块-apiappmessages-apiappservice-requests)
  - [7. AI 助手增强模块 (`/api/app/ai`) [Item 38]](#7-ai-助手增强模块-apiappai-item-38)
  - [8. 违规申诉、服务催单与社区删帖模块](#8-违规申诉服务催单与社区删帖模块)
  - [9. 私聊实时状态、送达与已读回执（Item 44）](#9-私聊实时状态送达与已读回执-item-44)
- [二、已有接口调整与变更清单 (Modified Endpoints)](#二已有接口调整与变更清单-modified-endpoints)
  - [1. 服务事项详情扩展停用与维护状态 `ServiceItemDetailResponse` [Item 32]](#1-服务事项详情扩展停用与维护状态-serviceitemdetailresponse-item-32)
  - [2. 工单流转与协商改期状态 `WorkOrderSummaryResponse` [Item 33]](#2-工单流转与协商改期状态-workordersummaryresponse-item-33)
  - [3. 社区帖子与评论实体扩展审核与可见性 [Item 34]](#3-社区帖子与评论实体扩展审核与可见性-item-34)
  - [4. 统一通知实体增加结构化卡片 `relatedCard` [Item 35]](#4-统一通知实体增加结构化卡片-relatedcard-item-35)
  - [5. 用户会话基础信息 `GET /api/common/auth/me`](#5-用户会话基础信息-get-apicommonauthme)
  - [6. 作者主页支持隐私脱敏与佩戴勋章 `GET /api/app/community/authors/{id}`](#6-作者主页支持隐私脱敏与佩戴勋章-get-apiappcommunityauthorsid)
  - [7. 私信支持关联场景卡片 `POST /api/app/community/messages`](#7-私信支持关联场景卡片-post-apiappcommunitymessages)
- [三、全局技术规范与端能力声明 (Specifications & Client Capabilities)](#三全局技术规范与端能力声明-specifications--client-capabilities)
  - [1. 动态表单 400 校验错误响应格式规范 [Item 40]](#1-动态表单-400-校验错误响应格式规范-item-40)
  - [2. 账号注册、密码找回与校园邮箱策略规范 [Item 41]](#2-账号注册密码找回与校园邮箱策略规范-item-41)
  - [3. 客户端原生端能力声明（本地日历导出与长图海报）[Item 42]](#3-客户端原生端能力声明本地日历导出与长图海报-item-42)
- [四、开发优先级与版本规划建议](#四开发优先级与版本规划建议)

---

# 一、新增接口清单 (New Endpoints)

## 1. 荣誉勋章与成就模块 (`/api/app/achievements`)

对应画布页面：`APP-荣誉勋章墙` (`5:1934`)、`APP-我的主页` (`5:1654`)、`APP-他人主页-身份信息气泡弹层` (`5:2297`)。

### 1.1 查询全量勋章库与个人解锁进度
- **方法与路径**：`GET /api/app/achievements/badges`
- **鉴权**：需登录（`Authorization: Bearer <token>`）
- **请求参数**：无
- **响应出参（`data` 部分）**：
```json
{
  "unlockedCount": 8,
  "totalCount": 19,
  "wornBadgeCode": "COMMUNITY_FIRST_POST",
  "categories": [
    {
      "categoryCode": "COMMUNITY",
      "categoryName": "社区互动",
      "badges": [
        {
          "badgeCode": "COMMUNITY_FIRST_POST",
          "name": "破冰首帖",
          "rarity": "COMMON",
          "metalColor": "SILVER",
          "enamelColor": "青绿",
          "iconUrl": "/api/common/assets/badges/COMMUNITY_FIRST_POST.png",
          "conditionDesc": "首次在校园社区发布动态",
          "unlocked": true,
          "isWorn": true,
          "unlockedAt": "2026-10-15T14:32:00",
          "currentProgress": 1,
          "targetProgress": 1
        },
        {
          "badgeCode": "COMMUNITY_POST_10",
          "name": "社区常客",
          "rarity": "RARE",
          "metalColor": "GOLD",
          "enamelColor": "青绿",
          "iconUrl": "/api/common/assets/badges/COMMUNITY_POST_10.png",
          "conditionDesc": "累计发布 10 篇动态",
          "unlocked": false,
          "isWorn": false,
          "unlockedAt": null,
          "currentProgress": 4,
          "targetProgress": 10
        }
      ]
    }
  ]
}
```

### 1.1 补充（2026-10-19 方案B 定稿）
- 字典与用户态拆分：`GET /api/app/achievements/catalog?version=` 下发带版本号的展示字典（分类名/排序/新增码 `iconUrl`），客户端保存本地；`GET /api/app/achievements/state` 高频下发 `catalogVersion / wornBadgeCode / activated / progress`。
- 图标策略：内置 19 枚**不**由服务端下发 URL，客户端按 `badgeCode` 匹配本地 `app/assets/images/badges/$code.png`；新增码由管理端上传图标后下发远程 `iconUrl`。因此本文档 1.1 响应中的 `iconUrl/metalColor/enamelColor` 属本地字典字段，不再作为服务端必出字段。
- `POST /api/app/achievements/wear` 支持显式 `action: WEAR|UNWEAR`（缺省时兼容旧的 `badgeCode=null` 卸下语义），佩戴校验勋章必须启用且已解锁。

### 1.2 查询他人已公开的勋章列表 [Item 39]
- **方法与路径**：`GET /api/app/achievements/users/{userId}/badges`
- **鉴权**：需登录
- **请求参数**：Path 参数 `userId`
- **响应出参（`data` 部分）**：
```json
{
  "userId": 100293,
  "displayName": "苏晴晴",
  "isMedalsPublic": true,
  "wornBadge": {
    "badgeCode": "LEARNING_PIONEER",
    "name": "博学先锋",
    "iconUrl": "/api/common/assets/badges/LEARNING_PIONEER.png"
  },
  "unlockedCount": 6,
  "publicBadges": [
    {
      "badgeCode": "LEARNING_PIONEER",
      "name": "博学先锋",
      "iconUrl": "/api/common/assets/badges/LEARNING_PIONEER.png",
      "unlockedAt": "2026-09-20"
    },
    {
      "badgeCode": "COMMUNITY_FIRST_POST",
      "name": "破冰首帖",
      "iconUrl": "/api/common/assets/badges/COMMUNITY_FIRST_POST.png",
      "unlockedAt": "2026-09-15"
    }
  ]
}
```
- **后端说明**：若被查用户在隐私设置中将 `showMedals` 设为 `false`，则返回 `isMedalsPublic: false`，`publicBadges: []`。

### 1.3 佩戴 / 卸下勋章
- **方法与路径**：`POST /api/app/achievements/wear`
- **鉴权**：需登录
- **请求入参**：
```json
{
  "badgeCode": "COMMUNITY_FIRST_POST",
  "action": "WEAR"
}
```
- **后端说明**：`action` 枚举为 `WEAR`（佩戴）或 `UNWEAR`（卸下）。用户同一时刻最多佩戴 1 枚主勋章。

---

## 2. 用户成长、偏好与社交关系模块 (`/api/app/users`, `/api/app/profile`)

对应画布页面：`APP-他人主页` (`5:1778`)、`APP-他人主页-身份信息气泡弹层` (`5:2297`)、`APP-账号与安全` (`5:1843`)。

### 2.1 用户轻量级身份名片（气泡弹层直出专用）
- **方法与路径**：`GET /api/app/users/{userId}/card`
- **鉴权**：需登录
- **请求参数**：Path 参数 `userId`
- **响应出参（`data` 部分）**：
```json
{
  "userId": "100293",
  "displayName": "苏晴晴",
  "avatarUrl": "https://file.mycc.edu.cn/avatar/2026/su.jpg",
  "gender": "FEMALE",
  "college": "外国语学院",
  "major": "英语语言文学",
  "enrollmentYear": 2024,
  "level": 3,
  "levelTitle": "校园达人",
  "wornBadge": {
    "badgeCode": "CAMPUS_STAR",
    "name": "校园达人",
    "iconUrl": "/api/common/assets/badges/CAMPUS_STAR.png"
  },
  "isFollowing": true,
  "isMutualFollowing": false,
  "recentBadgePreviews": [
    { "code": "CAMPUS_STAR", "iconUrl": "/api/common/assets/badges/CAMPUS_STAR.png" },
    { "code": "LEARNING_PIONEER", "iconUrl": "/api/common/assets/badges/LEARNING_PIONEER.png" },
    { "code": "COMMUNITY_FIRST_POST", "iconUrl": "/api/common/assets/badges/COMMUNITY_FIRST_POST.png" }
  ]
}
```

### 2.2 个人四维社交数据统计
- **方法与路径**：`GET /api/app/profile/stats`
- **鉴权**：需登录
- **响应出参（`data` 部分）**：
```json
{
  "likesCount": 128,
  "mutualFollowCount": 12,
  "followingCount": 46,
  "followersCount": 89
}
```

### 2.3 关注 / 取消关注用户 [Item 39]
- **方法与路径**：
  - 关注：`POST /api/app/users/{userId}/follow`
  - 取关：`DELETE /api/app/users/{userId}/follow`
- **鉴权**：需登录
- **请求参数**：Path 参数 `userId`
- **响应出参（`data` 部分）**：
```json
{
  "targetUserId": 100293,
  "following": true,
  "isMutualFollowing": true
}
```

### 2.4 用户个性化偏好与推送开关 [Item 43]
- **方法与路径**：
  - 查询：`GET /api/app/users/me/preferences`
  - 更新：`PUT /api/app/users/me/preferences`
- **鉴权**：需登录
- **对应画布页面**：`APP-账号与安全` (`5:1843`)
- **出参与请求 Body（`data` 部分）**：
```json
{
  "pushNewChatReminder": true,
  "pushInteractionReminder": true,
  "pushWorkOrderStatusServiceAccount": true,
  "autoSyncSchedule": true,
  "vibrateEnabled": true,
  "showAcademicInfo": false,
  "showMedals": true
}
```
- **后端说明**：
  - `pushNewChatReminder`: 接收新私聊消息系统推送
  - `pushInteractionReminder`: 点赞、评论与被关注等互动提醒
  - `pushWorkOrderStatusServiceAccount`: 工单流转微信服务号/订阅消息同步推送
  - `autoSyncSchedule`: 教务课表每周自动静默同步
  - `showAcademicInfo`: 向他人公开年级与专业（隐私控制）
  - `showMedals`: 在个人名片与主页向他人公开勋章

---

## 3. 设备与安全管理模块 (`/api/app/devices`)

对应画布页面：`APP-我的设备` (`5:2041`)。

### 3.1 查询我的在线设备列表
- **方法与路径**：`GET /api/app/devices`
- **鉴权**：需登录
- **响应出参（`data` 部分）**：
```json
[
  {
    "deviceId": "dev_01HK98Z44",
    "deviceName": "iPhone 15 Pro",
    "osType": "iOS",
    "osVersion": "17.4",
    "networkEnv": "绵城校园 Wi-Fi (安州校区)",
    "ipAddress": "10.64.120.35",
    "location": "四川省绵阳市",
    "isCurrent": true,
    "lastActiveAt": "2026-10-18T10:15:00"
  },
  {
    "deviceId": "dev_01HK77B89",
    "deviceName": "MacBook Pro",
    "osType": "macOS",
    "osVersion": "14.3",
    "networkEnv": "绵城校园 Wi-Fi (游仙校区)",
    "ipAddress": "10.68.80.12",
    "location": "四川省绵阳市",
    "isCurrent": false,
    "lastActiveAt": "2026-10-17T22:15:00"
  }
]
```
- **脱敏与提示说明**：画布已将原始裸 IP 优化为友好提示（如“绵城校园 Wi-Fi · 10.64.*.*”）。

### 3.2 下线指定设备
- **方法与路径**：`DELETE /api/app/devices/{deviceId}`
- **鉴权**：需登录
- **响应出参**：`data: true`

### 3.3 一键下线其他所有设备 [Item 37]
- **方法与路径**：`POST /api/app/devices/revoke-others`（兼容别名 `POST /api/app/devices/logout-others`）
- **鉴权**：需登录
- **请求参数**：无
- **响应出参**：`data: { "revokedCount": 2, "message": "已成功下线其他 2 台设备" }`
- **安全说明**：仅注销非当前设备的所有活跃 Token，当前设备保持登录。客户端执行前展示高危二次确认弹窗。

---

## 4. 教务成绩查询模块 (`/api/app/jw/grades`) [Item 31]

对应画布新增页面：`APP-成绩查询`。

### 4.1 查询指定学期成绩与学分绩点
- **方法与路径**：`GET /api/app/jw/grades`
- **鉴权**：需登录（需已绑定教务系统）
- **请求参数（Query）**：
  - `semester`（string，选填）：学期标识，如 `2025-2026-1`。若为空默认查询当前最新学期。
- **响应出参（`data` 部分）**：
```json
{
  "studentNo": "2024010822",
  "studentName": "李航",
  "college": "计算机科学与技术学院",
  "major": "软件工程",
  "currentSemester": "2025-2026学年 秋季学期",
  "availableSemesters": [
    { "code": "2025-2026-1", "name": "2025-2026学年 秋季学期" },
    { "code": "2024-2025-2", "name": "2024-2025学年 春季学期" },
    { "code": "2024-2025-1", "name": "2024-2025学年 秋季学期" }
  ],
  "overview": {
    "gpa": 3.82,
    "gradeRank": "5/128",
    "totalCreditsEarned": 24.5,
    "totalCreditsRequired": 32.0,
    "semesterCredits": 24.5,
    "passedCount": 7,
    "unpassedCount": 0
  },
  "syncStatus": "SYNCED",
  "lastSyncAt": "2026-10-18 09:30:00",
  "courses": [
    {
      "courseCode": "CS301",
      "courseName": "软件工程导论",
      "credits": 3.5,
      "score": 92,
      "scoreLevel": "优秀",
      "gradePoint": 4.0,
      "courseType": "必修",
      "examType": "正常考试",
      "teacherName": "陈建国教授"
    },
    {
      "courseCode": "CS302",
      "courseName": "计算机网络原理",
      "credits": 4.0,
      "score": 88,
      "scoreLevel": "良好",
      "gradePoint": 3.7,
      "courseType": "必修",
      "examType": "正常考试",
      "teacherName": "张卫国副教授"
    },
    {
      "courseCode": "CS305",
      "courseName": "数据库系统课程设计",
      "credits": 2.0,
      "score": 95,
      "scoreLevel": "优秀",
      "gradePoint": 4.0,
      "courseType": "实验",
      "examType": "答辩考核",
      "teacherName": "李晓梅副教授"
    },
    {
      "courseCode": "GE204",
      "courseName": "中国传统文化概论",
      "credits": 2.0,
      "score": 85,
      "scoreLevel": "良好",
      "gradePoint": 3.5,
      "courseType": "通选",
      "examType": "论文考查",
      "teacherName": "王文远教授"
    }
  ]
}
```
- **异常响应**：
  - 未绑定教务时返回 `403 JW_NOT_AUTHENTICATED`，前端引导跳转至 `APP-教务系统认证` (`7:148`)。
  - 教务系统维护/同步中返回 `syncStatus: "SYNCING"`，前端展示骨架与轮询提示。

---

## 5. 首页与校园广播聚合模块 (`/api/app/home`, `/api/app/announcements`)

对应画布页面：`APP-首页` (`5:1`)。

### 5.1 首页综合仪表盘聚合接口 (BFF)
- **方法与路径**：`GET /api/app/home/dashboard`
- **鉴权**：需登录
- **出参**：聚合下一节课日程、全员滚动广播、当前最紧迫的进行中待办工单、未读通知红点。

### 5.2 校园全员广播 / 公告列表
- **方法与路径**：`GET /api/app/announcements`
- **鉴权**：需登录
- **Query 参数**：`pageNum=1`, `pageSize=10`

---

## 6. 消息中心与流程通知模块 (`/api/app/messages`, `/api/app/service-requests`)

对应画布页面：`APP-消息聚合中心` (`5:2433`)、`APP-流程办理通知详情` (`5:2519`)。

### 6.1 消息中心 4 大分类未读数聚合 [Item 36]
- **方法与路径**：`GET /api/app/messages/unread-summary`（兼容别名 `GET /api/app/messages/category-summary`）
- **鉴权**：需登录
- **出参（`data` 部分）**：
```json
{
  "processNoticeUnread": 1,
  "interactionUnread": 4,
  "announcementUnread": 0,
  "aiTodoCount": 2,
  "directMessageUnread": 3,
  "totalUnread": 10
}
```
- **说明**：对应消息中心顶部四大快捷分类图标上方的气泡角标。

### 6.2 统一会话收件箱流 (Inbox Stream)
- **方法与路径**：`GET /api/app/messages/inbox`
- **鉴权**：需登录
- **Query 参数**：`pageNum=1`, `pageSize=20`

---

## 7. AI 助手增强模块 (`/api/app/ai`) [Item 38]

对应画布页面：`APP-AI助手` (`5:2651`)。

### 7.1 App 端 SSE 流式会话对话接口
- **方法与路径**：`POST /api/app/ai/conversations/{conversationId}/chat-stream`
- **请求头**：`Accept: text/event-stream`
- **请求入参**：
```json
{
  "content": "查一下我今天下午有什么课？顺便提醒我晚上该带什么材料去办在读证明。",
  "attachments": []
}
```
- **响应事件流（SSE Events）**：
```text
event: delta
data: {"text": "您好！今天下午您在 **第三教学楼 302** 有两门课程：\n\n1. **《软件工程导论》**（14:00 - 15:35）\n2. **《计算机网络原理》**（15:55 - 17:30）"}

event: card
data: {"cardType": "SCHEDULE", "title": "今日下午课程", "items": [{"name": "软件工程导论", "time": "14:00 - 15:35", "location": "三教302"}, {"name": "计算机网络原理", "time": "15:55 - 17:30", "location": "三教302"}]}

event: card
data: {"cardType": "SERVICE_GUIDE", "title": "在读证明自助开具", "location": "师生服务大厅 1 号自助打印机", "requiredDocs": ["学生证", "校园卡一卡通"]}

event: suggestions
data: {"suggestions": ["慎思楼402教室导航", "查看本周完整课表", "一键预约在读证明打印"]}

event: done
data: {"messageId": "msg_901283", "totalTokens": 412}
```

### 7.2 AI 空态 2×2 场景推荐指令列表
- **方法与路径**：`GET /api/app/ai/recommendations`
- **出参**：返回 4 组真实校园卡片（查课表作息、报修进度、在读证明指引、校车与生活咨询）。

---

## 8. 违规申诉、服务催单与社区删帖模块

### 8.1 社区内容违规申诉接口 [Item 34]
- **方法与路径**：`POST /api/app/community/moderation/appeal`
- **鉴权**：需登录
- **请求入参**：
```json
{
  "targetType": "POST",
  "targetId": 50192,
  "appealReason": "该贴为正常的考研教材转让信息，包含书本实拍，不存在任何商业违规广告，请管理员复核解封。",
  "contactPhone": "18888888888",
  "attachments": ["https://file.mycc.edu.cn/appeal/evidence_01.jpg"]
}
```
- **响应出参（`data` 部分）**：
```json
{
  "appealId": "AP-2026101801",
  "status": "PENDING",
  "submittedAt": "2026-10-18T10:20:00",
  "message": "申诉已提交，人工管理员将在 24 小时内完成复核并站内信通知结果。"
}
```

### 8.2 服务申请催单
- **方法与路径**：`POST /api/app/service-requests/{requestId}/urge`

### 8.3 作者自删动态
- **方法与路径**：`DELETE /api/app/community/posts/{postId}`


## 9. 私聊实时状态、送达与已读回执（Item 44）

对应页面：`APP-私信对话页`（`5:2560`）、消息中心私信会话。  
目标：在现有“新私信推送”基础上，补齐**对方正在输入**、**在线/离线**、**消息送达**、**消息已读**四类实时能力。  
当前基线：`/ws/community?token=` 已能由服务端推送新私信 `DirectMessageResponse`；客户端 `CommunitySocket` 已接入连接、重连与消息去重。

### 9.0 WebSocket 事件协议（统一信封）

- **连接地址**：`ws://<host>/ws/community?token=<登录 token>`（生产 `wss://`）
- **鉴权**：连接时 `token` 参数，服务端复用不透明会话校验；无效 token 立即关闭连接。
- **服务端 → 客户端**统一信封：
```json
{
  "type": "MESSAGE | DELIVERED | READ | TYPING | PRESENCE | PONG",
  "data": {}
}
```
- **兼容要求**：现有裸 `DirectMessageResponse` 帧可继续发送；客户端按“有 `type` 走信封，无 `type` 视为 `MESSAGE`”兼容，避免一次性破坏已上线版本。
- **客户端 → 服务端**统一信封：
```json
{ "type": "TYPING", "data": { "receiverId": "2111820209061076994", "typing": true } }
{ "type": "PING", "data": {} }
```
- **心跳**：客户端每 25s 发 `PING`，服务端回 `PONG`；60s 无心跳服务端视为离线。

### 9.1 正在输入 `TYPING`

- **方向**：客户端 → 服务端 → 接收方客户端；不落库、不进历史。
- **客户端发送**：
```json
{ "type": "TYPING", "data": { "receiverId": "2111820209061076994", "typing": true } }
```
- **服务端推送**：
```json
{ "type": "TYPING", "data": { "fromUserId": "2110887885012078594", "typing": true } }
```
- **规则**：
  - 仅向当前与发送者在同一会话的在线接收方推送；
  - 客户端节流：输入中最多每 3s 发一次 `typing=true`；停止输入 5s 或发送消息后发 `typing=false`；
  - 服务端不持久化，连接断开即丢弃；
  - 频控：单用户单会话最多 20 条/分钟，超限丢弃。

### 9.2 在线/离线状态 `PRESENCE`

- **服务端推送**（连接建立/断开、多端最后一条连接关闭时触发）：
```json
{
  "type": "PRESENCE",
  "data": {
    "userId": "2111820209061076994",
    "online": true,
    "lastSeenAt": null
  }
}
```
- **规则**：
  - 同一用户多端连接时，只有首条连接建立、最后一条连接断开才广播状态变化；
  - 接收范围：与该用户存在私信会话的用户；避免全站广播；
  - `lastSeenAt` 在离线时下发，在线时为 `null`；
  - 状态存 Redis（在线集合 + 最后在线时间），不以数据库轮询为准。

- **批量查询接口**（进入会话/消息中心时初始化状态）：
  - **方法与路径**：`GET /api/app/community/presence?userIds=2111820209061076994,2111820209061076995`
  - **鉴权**：需登录
  - **响应出参（`data` 部分）**：
```json
[
  { "userId": "2111820209061076994", "online": true, "lastSeenAt": null },
  { "userId": "2111820209061076995", "online": false, "lastSeenAt": "2026-10-18T22:10:00" }
]
```
  - **限制**：单次最多 50 个 userId；与当前用户无会话关系的用户返回 `online=false, lastSeenAt=null`，避免探测陌生人。

### 9.3 消息送达回执 `DELIVERED`

- **方法与路径**：`POST /api/app/community/messages/delivered`
- **鉴权**：需登录（仅接收方可回执）
- **请求入参**：
```json
{ "messageIds": ["2111821000000000001", "2111821000000000002"] }
```
- **响应出参（`data` 部分）**：
```json
{ "updatedCount": 2, "deliveredAt": "2026-10-19T00:20:00" }
```
- **服务端行为**：
  - 只允许更新 `receiver_id = 当前用户` 且 `delivered_at IS NULL` 的消息；
  - 更新后向对应发送方推送：
```json
{
  "type": "DELIVERED",
  "data": {
    "messageIds": ["2111821000000000001", "2111821000000000002"],
    "deliveredAt": "2026-10-19T00:20:00"
  }
}
```
  - 单次最多 100 条；重复回执幂等，不重复推送。

### 9.4 已读回执 `READ`

- **方法与路径**：`POST /api/app/community/messages/read?withUserId={peerId}`（扩展现有接口）
- **鉴权**：需登录
- **请求入参（可选 body）**：
```json
{ "messageIds": ["2111821000000000001", "2111821000000000002"] }
```
- **规则**：
  - 不传 `messageIds`：保持当前行为，把该会话内所有 `receiver_id=当前用户` 且未读消息置为已读；
  - 传 `messageIds`：仅标记指定且属于该会话、接收人为当前用户的消息；
  - 返回 `data`：
```json
{ "readCount": 2, "readAt": "2026-10-19T00:21:00", "messageIds": ["..."] }
```
  - 向发送方推送：
```json
{ "type": "READ", "data": { "readerId": "2110887885012078594", "messageIds": ["..."], "readAt": "2026-10-19T00:21:00" } }
```
  - 进入会话自动已读时，建议只回执当前可见的最后一条，减少事件量。

### 9.5 消息实体与联系人列表扩展

**`DirectMessageResponse` 增加字段**（`GET /messages`、`POST /messages`、WS `MESSAGE` 同步）：
```diff
  {
    "messageId": "2111821000000000001",
    "senderId": "...",
    "receiverId": "...",
    "content": "...",
    "imageUrls": [],
    "createdAt": "2026-10-19T00:18:00",
+   "deliveredAt": "2026-10-19T00:18:02",
+   "readAt": "2026-10-19T00:21:00",
+   "status": "SENT | DELIVERED | READ"
  }
```
- `status` 以接收方回执推进；发送方消息气泡据此显示“已送达/已读”。

**`GET /api/app/community/messages/contacts` 增加字段**：
```diff
  {
    "peerId": "...",
    "peerName": "苏晴晴",
    "avatarUrl": null,
    "lastContent": "...",
    "lastAt": "2026-10-19T00:18:00",
+   "online": true,
+   "lastSeenAt": null,
+   "unreadCount": 2
  }
```
- 私信会话仍统一由**消息中心**展示，**不新增独立私聊列表页**；该接口仅服务消息中心排序、未读数与在线态展示。

### 9.6 数据库与状态字段

```sql
-- V52__community_direct_message_receipts.sql（建议）
ALTER TABLE community_direct_message
    ADD COLUMN delivered_at TIMESTAMP NULL AFTER created_at,
    ADD COLUMN read_at TIMESTAMP NULL AFTER delivered_at;

CREATE INDEX idx_community_dm_receiver_unread
    ON community_direct_message (receiver_id, read_at, created_at);
```

- 在线状态存 Redis：`community:presence:{userId}`（连接数/过期时间），最后在线时间落库或 Redis。
- 不新增独立“私聊列表”表或页面。

### 9.7 验收标准

1. A 在线、B 在线：A 输入时 B 在 1s 内看到“对方正在输入…”，停止输入 5s 后消失；
2. A 发送消息，B 在线：B 收到 `MESSAGE` 后回执 `DELIVERED`，A 气泡从“发送中”变“已送达”；
3. B 打开会话：回执 `READ`，A 气泡变“已读”；
4. B 断网/关闭 App：A 在 60s 内看到 B 离线；B 重新上线后状态恢复，A 收到 `PRESENCE`；
5. 同一用户多端在线：关闭一端不广播离线，最后一条连接关闭才广播；
6. 断线重连后：客户端补拉历史 + 未读，消息不重复、不丢失；
7. 回执接口重复调用幂等，不产生重复 WS 事件。

### 9.8 优先级建议

| 优先级 | 能力 | 说明 |
|---|---|---|
| P0 | `READ` 回执 + `DELIVERED` | 聊天最基础的可靠感，改动集中在私信表与 WS 推送 |
| P1 | `PRESENCE` 在线状态 + contacts 字段 | 依赖 Redis presence，消息中心可先展示在线点 |
| P2 | `TYPING` 正在输入 | 体验增强，可不落库，频控要求高 |

---

# 二、已有接口调整与变更清单 (Modified Endpoints)

## 1. 服务事项详情扩展停用与维护状态 `ServiceItemDetailResponse` [Item 32]
- **接口路径**：`GET /api/app/services/{id}`
- **修改原因**：部分线下窗口在节假日或系统维护期间处于暂停受理态，详情页需展示下架提示与预计恢复时间，并禁用“立即申请”主按钮。
- **出参扩展**：
```diff
  public class ServiceItemDetailResponse {
      private Long id;
      private String title;
      private String categoryName;
+     private String status;           // 状态: AVAILABLE(正常开放), MAINTENANCE(维护检修), DISABLED(已停用下架)
+     private String disabledReason;    // 停用说明: 如 "期末设备检修，本周五前暂停线上办理"
+     private LocalDateTime restoreAt;  // 预计恢复时间: "2026-10-25T08:00:00"
      private String location;
      private String processingTime;
      private String costText;         // 如 "公区保障全免"
      private List<String> requiredMaterials;
  }
```

---

## 2. 工单流转与协商改期状态 `WorkOrderSummaryResponse` [Item 33]
- **接口路径**：`GET /api/app/work-orders/{id}` & `GET /api/app/service-requests/{id}`
- **修改原因**：维修师傅与学生电话沟通后可能发起预约改期，工单详情需展示协商状态及预计到达时间窗口。
- **出参扩展**：
```diff
  public class WorkOrderSummaryResponse {
      private Long workOrderId;
      private String status;           // PENDING_DISPATCH, ASSIGNED, PROCESSING, PENDING_CONFIRMATION, COMPLETED
+     private LocalDateTime appointmentTime; // 约定上门时间: "2026-10-18T14:30:00"
+     private String workerArriveWindow;     // 履约窗口: "预计今天 14:00 - 15:00 之间到达"
+     private String negotiateStatus;        // 协商状态: NOT_REQUIRED(无需协商), PROPOSED(师傅已提改期), ACCEPTED(学生已确认), REJECTED(学生已拒绝)
+     private String negotiateReason;        // 改期原因: "因上一单配件调货，需协商顺延至下午"
      private String assigneeName;
      private String assigneePhone;
  }
```

---

## 3. 社区帖子与评论实体扩展审核与可见性 [Item 34]
- **接口路径**：`GET /api/app/community/posts` & `GET /api/app/community/posts/{id}`
- **修改原因**：应对校园网络内容合规与分级治理，列表与详情需区分审核状态与可见范围。
- **出参扩展**：
```diff
  public class CommunityPostResponse {
      private Long postId;
      private String title;
      private String content;
+     private String moderationStatus; // NORMAL(正常), AUDITING(机审/人工复审中), REJECTED(违规已拦截), DELETED(已删除)
+     private String visibleScope;     // CAMPUS_ALL(全校可见), REALNAME_ONLY(仅实名师生可见)
+     private String rejectReason;     // 审核拦截原因（仅作者本人与管理员可见）
      private Integer likesCount;
      private Integer commentsCount;
  }
```

---

## 4. 统一通知实体增加结构化卡片 `relatedCard` [Item 35]
- **接口路径**：`GET /api/app/messages/inbox` & `GET /api/common/notifications`
- **修改原因**：通知不再只是静态文字，需直接附带可点击直达的高保真结构化实体卡片（报修卡、教务成绩卡、帖子违规提醒卡等）。
- **出参扩展**：
```json
{
  "id": "notif_881920",
  "category": "PROCESS_NOTIFICATION",
  "title": "工单已派工",
  "content": "王建国师傅已接单，预计今天 14:30 前到达现场",
  "createdAt": "2026-10-18T10:10:00",
  "relatedCard": {
    "cardType": "WORK_ORDER",
    "cardId": "WO-2026101701",
    "title": "宿舍电风扇异响报修",
    "status": "PROCESSING",
    "statusDesc": "师傅已接单",
    "actionText": "查看工单进度",
    "targetRoute": "/pages/work-order/detail?id=WO-2026101701"
  }
}
```

---

## 5. 用户会话基础信息 `GET /api/common/auth/me`
- **出参扩展**：增加 `studentNo`, `college`, `major`, `academicYear`, `level`, `exp`, `nextLevelExp`, `wornBadge` 字段。

---

## 6. 作者主页支持隐私脱敏与佩戴勋章 `GET /api/app/community/authors/{id}`
- **出参扩展**：包含 `level`, `wornBadge`, `isFollowing`, 若隐私设置 `showAcademicInfo=false` 则自动脱敏班级专业。

---

## 7. 私信支持关联场景卡片 `POST /api/app/community/messages`
- **入参扩展**：支持传入 `referenceType` (`POST` / `WORK_ORDER`) 与 `referenceId`，对话顶部携带原帖/工单上下文。

---

# 三、全局技术规范与端能力声明 (Specifications & Client Capabilities)

## 1. 动态表单 400 校验错误响应格式规范 [Item 40]
为确保移动端动态表单（报修提交、失物认领、违规申诉等）在字段校验失败时，能够将错误信息精准高亮提示到具体输入控件下方，后端所有接口在抛出 `MethodArgumentNotValidException` 或业务参数校验失败时，统一遵循以下响应体结构：

- **HTTP 状态码**：`400 Bad Request`
- **标准响应 JSON 结构**：
```json
{
  "code": 400,
  "message": "表单内容校验不通过，请修正标红字段后重试",
  "timestamp": "2026-10-18T10:25:30",
  "errors": [
    {
      "field": "contactPhone",
      "rejectedValue": "138000",
      "message": "请输入正确的 11 位手机号码"
    },
    {
      "field": "images",
      "rejectedValue": null,
      "message": "故障现场照片不可为空，请至少上传 1 张现场凭证"
    },
    {
      "field": "description",
      "rejectedValue": "坏了",
      "message": "故障现象描述过短，请详述具体损坏情况（至少 5 个字）"
    }
  ]
}
```
- **客户端联动行为**：App 接收到 `errors` 数组后，自动定位并滚动至首个错误表单项，控件边框变红并展示对应的 `message` 文本，避免单调的全局 Toast 轰炸。

---

## 2. 账号注册、密码找回与校园邮箱策略规范 [Item 41]
画布登录页（`4:3`）已正式补充 `学号密码登录`、`新用户注册`、`找回密码` 与 `邮箱绑定` 入口。相关后端业务规则明确如下：

1. **学号唯一性与实名绑定**：
   - 注册只需登录 ID（4-20 位字母/数字/下划线）、密码（8-20 位含字母和数字）、邮箱验证码，前端不收集学号/姓名。
   - 不再要求填写学号、姓名；登录 ID 全局唯一。
2. **校园邮箱后缀硬性白名单**：
   - 注册与找回密码的安全邮箱必须使用学校官方邮箱域名：`@stu.mycc.edu.cn`（学生）或 `@mycc.edu.cn`（教职工）。
   - 验证码有效期 10 分钟；真实 SMTP 配置见 [`方案/校园邮箱验证码配置说明.md`](../方案/校园邮箱验证码配置说明.md)。
   - 开发/测试未配置 SMTP 时接口返回 `devCode`；生产未配置时明确返回 `AUTH_503_03`，不伪报已发送。发送频率限制尚未实现，上线前需补充。
3. **QQ 互联第三方绑定机制**：
   - QQ 首次授权登录自动创建平台账号，默认使用 QQ 昵称/头像；用户主动改过昵称或头像后不再被 QQ 覆盖。
   - 已登录账号可在账号中心绑定 QQ；同一 QQ 只能绑定一个账号，当前账号已绑定其他 QQ 时明确报错。
   - QQ 移动 SDK 不提供数字 QQ 号，平台账号为 `qq_<openid 片段>`；当前未强制要求学号/邮箱验证后才发放 Token，后续如学校要求再收紧授权状态。

---

## 3. 客户端原生端能力声明（本地日历导出与长图海报）[Item 42]
为减轻后端服务器渲染压力与带宽开销，以下两项功能被明确定义为**客户端本地原生实现（Zero-Backend Overhead）**，后端无需开发额外转码接口：

1. **教务课表系统日历导出 (ICS 生成)**：
   - **实现机制**：App 获取 `GET /api/app/jw/schedule` 纯 JSON 数据后，由 Flutter 客户端本地解析组装符合 RFC 5545 标准的 `.ics` 字符串。
   - **交互形态**：调用系统底层原生通道（`add-to-calendar`），用户点击“导出至系统日历”即可直接调起 iOS 日历或 Android 系统日程写入权限，支持闹钟提前 15 分钟提醒。
2. **社区帖子与勋章成就长图分享**：
   - **实现机制**：客户端基于 Flutter `RepaintBoundary` 本地渲染 375pt 高保真卡片并导出为高清 PNG，动态附加 App 下载二维码水印。
   - **交互形态**：生成完成后直接拉起系统原生分享面板（System Share Sheet），支持保存至系统相册或微信好友/朋友圈分享。

---

# 四、开发优先级与版本规划建议

结合全站 43 项核心审计清单与前后端交付节奏，研发排期规划如下：

| 阶段 | 核心模块与接口 | 包含审计清单项 | 建议排期 |
|---|---|---|---|
| **Phase 1 (P0 急迫)** | **教务成绩与表单基础规范**<br>1. 教务成绩查询接口 (`/jw/grades`)<br>2. 动态表单 400 统一校验错误响应格式 (`errors`)<br>3. 服务事项状态扩展 (`ServiceItemDetailResponse`) | Items 31, 32, 40 | 1~2 人天 |
| **Phase 2 (P1 核心)** | **设备管理、偏好与工单协同**<br>1. 设备列表与一键下线其他设备 (`/devices/revoke-others`)<br>2. 个人个性化偏好与推送设置 (`/preferences`)<br>3. 工单预约与协商改期状态 (`WorkOrderSummaryResponse`)<br>4. 消息中心 4 大类未读数汇总 (`/unread-summary`) | Items 33, 36, 37, 43 | 2 人天 |
| **Phase 3 (P2 体验)** | **AI 助手与社区合规治理**<br>1. App 端 SSE 流式会话与结构化卡片下发 (`/chat-stream`)<br>2. AI 2×2 场景推荐指令库 (`/recommendations`)<br>3. 社区内容违规审核态与申诉通道 (`/moderation/appeal`)<br>4. 他人主页公开勋章与关注关系 | Items 34, 35, 38, 39 | 2~3 人天 |
| **Phase 4 (P3 体系)** | **账号注册体系完善与端能力落地**<br>1. 邮箱验证码发送与学号绑定策略<br>2. 客户端 ICS 日历导出与海报本地绘制验收 | Items 41, 42 | 1 人天 |

### 系统推送（2026-10-19 新增）

| 接口 | 说明 | 状态 |
|---|---|---|
| `POST /api/app/push/tokens` | 登记 FCM/APNs/Web Push Token | ✅ 已实现 |
| `DELETE /api/app/push/tokens?token=` | 解绑 Token | ✅ 已实现 |
| WS `/ws/community` `NOTIFICATION` | 站内通知实时推送 | ✅ 已实现 |

### 应用更新（已有 + 2026-10-19 增强）

| 接口 | 说明 | 状态 |
|---|---|---|
| `GET /api/public/app-version/latest` | 检查最新版本/强更 | ✅ 已有 |
| `GET /api/public/app-version/{id}/package` | 安装包流式下载 | ✅ 已有 |
| 管理端上传安装包 | 服务端计算 SHA-256 | ✅ 已有 |
| App 下载进度/校验/安装 | 应用内流式下载 + SHA-256 + open_filex | ✅ 本轮新增 |

### 定位（2026-10-19 新增）

| 接口 | 说明 | 状态 |
|---|---|---|
| `GET /api/app/locations/nearby` | 高德附近 POI | ✅ 已实现 |
| `GET /api/app/locations/reverse` | 高德逆地理编码 | ✅ 已实现 |
| 发帖定位字段 | `locationName/locationAddress/latitude/longitude/poiId` | ✅ 已实现 |
