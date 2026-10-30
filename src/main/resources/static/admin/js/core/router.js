/*
 * 管理端路由核心（原生 ES Module）。
 *
 * 从 app.js 抽离：路由元数据、权限过滤、页面组件映射、hash 监听与 navigate。
 * 页面脚本仍通过 window.AdminPages 注册组件，由 core/registry.js 统一收集。
 *
 * 兼容：window.AdminApp.routePermission 指向同一份权限表，login.js 等旧脚本可继续使用。
 */

/** 路由 → 允许访问的权限码（platform:all 为超管万能权限）。 */
export const ROUTE_PERMISSION = {
    dashboard: ['platform:all', 'system:user:manage'],
    analytics: ['platform:all', 'analytics:view'],
    services: ['platform:all', 'catalog:manage'],
    requests: ['platform:all', 'service-request:view:self'],
    leave: ['platform:all', 'catalog:manage', 'work-order:dispatch'],
    workorders: ['platform:all', 'work-order:dispatch'],
    sla: ['platform:all', 'sla:view'],
    eval: ['platform:all'],
    'community-overview': ['platform:all', 'community:moderate'],
    'community-posts': ['platform:all', 'community:moderate'],
    community: ['platform:all', 'community:moderate'],
    'community-appeals': ['platform:all', 'community:moderate'],
    'community-sections': ['platform:all', 'community:moderate'],
    'community-topics': ['platform:all', 'community:moderate'],
    'community-words': ['platform:all', 'community:moderate'],
    'community-official': ['platform:all', 'community:moderate'],
    'community-accounts': ['platform:all', 'community:official:manage', 'community:moderate'],
    'community-ads': ['platform:all', 'community:ad:manage', 'community:moderate'],
    'community-advertisers': ['platform:all', 'community:advertiser:manage'],
    achievements: ['platform:all', 'system:user:manage'],
    ai: ['platform:all', 'ai:manage'],
    knowledge: ['platform:all', 'ai:manage'],
    aitest: ['platform:all', 'ai:manage'],
    users: ['platform:all', 'system:user:manage'],
    roles: ['platform:all', 'system:role:manage'],
    orgs: ['platform:all', 'system:organization:manage'],
    dicts: ['platform:all', 'system:dict:manage'],
    'app-versions': ['platform:all', 'system:user:manage'],
    homework: ['platform:all', 'catalog:manage'],
    notices: ['platform:all', 'announcement:manage'],
    audit: ['platform:all', 'system:audit:view'],
    'system-config': ['platform:all', 'system:config:view'],
    'file-storage': ['platform:all', 'file:manage'],
    'scheduled-tasks': ['platform:all', 'system:config:view'],
    push: ['platform:all', 'push:manage'],
    'ai-security': ['platform:all', 'ai:security:manage', 'ai:manage'],
    'content-calendar': ['platform:all', 'content:operate'],
    creators: ['platform:all', 'creator:manage', 'community:moderate']
};

export const ROUTE_TITLES = {
    dashboard: '总览',
    analytics: '数据分析',
    services: '服务目录',
    requests: '服务申请',
    leave: '请假审批',
    workorders: '工单调度',
    sla: '服务 SLA',
    eval: '服务评价',
    'community-overview': '社区概览',
    'community-posts': '帖子管理',
    community: '举报处置',
    'community-appeals': '申诉复核',
    'community-sections': '分区管理',
    'community-topics': '话题管理',
    'community-words': '屏蔽词',
    'community-official': '官方通知',
    'community-accounts': '官方与运营账号',
    'community-ads': '信息流广告',
    'community-advertisers': '广告主管理',
    ai: 'AI 基座',
    knowledge: '知识库',
    aitest: 'AI 对话测试',
    users: '用户管理',
    roles: '角色权限',
    orgs: '组织架构',
    dicts: '字典管理',
    'app-versions': '版本发布',
    homework: '作业管理',
    achievements: '成就管理',
    notices: '通知公告',
    audit: '审计日志',
    'system-config': '系统配置',
    'file-storage': '文件存储',
    'scheduled-tasks': '定时任务',
    push: '推送运营',
    'ai-security': 'AI 安全策略',
    'content-calendar': '内容日历',
    creators: '创作者运营'
};

export const ROUTE_SUBTITLES = {
    dashboard: '校园运行态势与工单闭环',
    analytics: '服务 / 工单 / 社区 / 用户 / AI 指标与趋势',
    services: '服务目录、办理入口与上下架',
    requests: '申请进度、材料与办理状态',
    leave: '待审 / 已通过 / 已驳回 / 已销假 · 独立请假审批与销假',
    workorders: '工单派发、处理与闭环',
    sla: '承诺时长、超时率与满意度',
    eval: '服务评价、满意度与申诉线索',
    'community-overview': '社区规模、活跃趋势与分区结构',
    'community-posts': '全量帖子、内容详情与下架恢复',
    community: '举报队列与内容巡检',
    'community-appeals': '帖子 / 评论 / 处罚申诉复核',
    'community-sections': '分区增删改、排序与启停',
    'community-topics': '话题配置与热度',
    'community-words': '命中自动隐藏并通知作者',
    'community-official': '学校官网抓取、来源校验与发布',
    'community-accounts': '抓取号、人工运营号与广告服务号的身份治理',
    'community-ads': '官方直投、投放排期与可测数据链路',
    'community-advertisers': '广告主档案、联系人与投放归属',
    ai: '模型网关、供应商接入与默认路由',
    knowledge: '知识库、文档分块与检索测试',
    aitest: 'SSE 流式对话与引用联调',
    users: '多身份用户、组织与账号安全',
    roles: '权限矩阵与数据范围',
    orgs: '校区按上方卡片筛选；树内为学院 / 专业 / 班级',
    dicts: '字典项、枚举与系统参数',
    achievements: '成就徽章、素材与发放记录',
    'app-versions': '客户端版本、灰度与发布记录',
    homework: '作业模板、提交情况与统计',
    notices: 'App 与终端公告发布',
    audit: '关键操作留痕与配置变更追溯',
    'system-config': '运行配置、功能开关与外部集成',
    'file-storage': '存储配置、文件清单与逻辑删除',
    'scheduled-tasks': '调度任务、最近执行与手动触发',
    push: '模板、任务、频控与触达统计',
    'ai-security': 'Prompt 版本、敏感词、工具白名单与配额',
    'content-calendar': '推荐位、专题、活动排期与上下线',
    creators: '认证、等级、处罚与申诉'
};

export const ROUTE_GROUPS = {
    dashboard: '概览',
    analytics: '概览',
    services: '服务运营',
    requests: '服务运营',
    leave: '服务运营',
    workorders: '服务运营',
    sla: '服务运营',
    eval: '服务运营',
    'community-overview': '社区',
    'community-posts': '社区',
    community: '社区',
    'community-appeals': '社区',
    'community-sections': '社区',
    'community-topics': '社区',
    'community-words': '社区',
    'community-official': '社区',
    'community-accounts': '社区',
    'community-ads': '社区',
    'community-advertisers': '社区',
    ai: '内容与智能',
    knowledge: '内容与智能',
    aitest: '内容与智能',
    users: '用户与组织',
    roles: '用户与组织',
    orgs: '用户与组织',
    dicts: '用户与组织',
    achievements: '用户与组织',
    homework: '业务运营',
    'app-versions': '系统管理',
    notices: '内容与智能',
    audit: '系统管理',
    'system-config': '系统管理',
    'file-storage': '系统管理',
    'scheduled-tasks': '系统管理',
    push: '内容与智能',
    'ai-security': '内容与智能',
    'content-calendar': '内容与智能',
    creators: '内容与智能'
};

/** 路由 → 全局注册的页面组件名（组件由 window.AdminPages 提供）。 */
export const PAGE_COMPONENTS = {
    dashboard: 'DashboardPage',
    analytics: 'AnalyticsPage',
    services: 'ServiceCatalogPage',
    requests: 'RequestsPage',
    leave: 'LeaveApprovalPage',
    workorders: 'WorkOrdersPage',
    sla: 'SlaPage',
    eval: 'EvalPage',
    community: 'CommunityAdminPage',
    'community-overview': 'CommunityOverviewPage',
    'community-posts': 'CommunityPostsPage',
    'community-appeals': 'CommunityAppealsPage',
    'community-sections': 'CommunitySectionsPage',
    'community-topics': 'CommunityTopicsPage',
    'community-words': 'CommunityWordsPage',
    'community-official': 'OfficialFeedPage',
    'community-accounts': 'CommunityAccountsPage',
    'community-ads': 'CommunityAdsPage',
    'community-advertisers': 'CommunityAdvertisersPage',
    achievements: 'AchievementsPage',
    ai: 'AiConfigPage',
    knowledge: 'KnowledgeBasePage',
    aitest: 'AiTestPage',
    users: 'UsersPage',
    roles: 'RolesPage',
    orgs: 'OrganizationsPage',
    dicts: 'DictsPage',
    'app-versions': 'AppVersionsPage',
    homework: 'HomeworkAdminPage',
    notices: 'NoticesPage',
    audit: 'AuditLogsPage',
    'system-config': 'SystemConfigPage',
    'file-storage': 'FileStoragePage',
    'scheduled-tasks': 'ScheduledTasksPage',
    push: 'PushPage',
    'ai-security': 'AiSecurityPage',
    'content-calendar': 'ContentCalendarPage',
    creators: 'CreatorsPage'
};

export const GROUP_ORDER = ['概览', '服务运营', '社区', '内容与智能', '用户与组织', '业务运营', '系统管理'];

export function routeNames() {
    return Object.keys(ROUTE_PERMISSION);
}

/** 规范化 hash 中的路由名（兼容 #/route、#route、带 query）。
 *  校区管理已并入组织架构；旧书签 #/campuses 统一回落到 orgs。 */
export function routeName(hash) {
    const raw = hash === undefined ? (window.location.hash || '') : hash;
    const name = String(raw).replace(/^#\/?/, '').split('?')[0];
    return name === 'campuses' ? 'orgs' : (name || '');
}

export function routeTitle(name) {
    return ROUTE_TITLES[name] || '控制面';
}

export function routeGroup(name) {
    return ROUTE_GROUPS[name] || '';
}

export function routeSubtitle(name) {
    return ROUTE_SUBTITLES[name] || '';
}

/** 权限码：角色含 PLATFORM_ADMIN 时补 platform:all。 */
export function permissionsOf(user) {
    let permissions = (user && user.permissions) || [];
    const roles = (user && user.roles) || [];
    if (roles.indexOf('PLATFORM_ADMIN') >= 0 && permissions.indexOf('platform:all') < 0) {
        permissions = permissions.concat(['platform:all']);
    }
    return permissions;
}

export function allowedRoutes(user) {
    const permissions = permissionsOf(user);
    return routeNames().filter(function (name) {
        return ROUTE_PERMISSION[name].some(function (code) {
            return permissions.indexOf(code) >= 0;
        });
    });
}

export function canAccess(user, name) {
    if (!name || !ROUTE_PERMISSION[name]) {
        return false;
    }
    return allowedRoutes(user).indexOf(name) >= 0;
}

export function pageComponentName(name) {
    return PAGE_COMPONENTS[name] || 'LoginPage';
}

/**
 * 扩展路由（供后续页面模块追加，例如社区广告主）。
 * 默认路由表已包含全部 39 条产品路由，扩展 API 只用于避免再次改壳层。
 */
export function registerRoute(name, meta) {
    if (!name) return;
    meta = meta || {};
    ROUTE_PERMISSION[name] = meta.permission || ['platform:all'];
    ROUTE_TITLES[name] = meta.title || name;
    ROUTE_SUBTITLES[name] = meta.subtitle || '';
    ROUTE_GROUPS[name] = meta.group || '功能';
    if (meta.component) {
        PAGE_COMPONENTS[name] = meta.component;
    }
}

export function navigate(name) {
    window.location.hash = '#/' + name;
}

/** 监听 hash 变化；返回取消订阅函数。 */
export function onRouteChange(handler) {
    function listener() {
        handler(routeName());
    }
    window.addEventListener('hashchange', listener);
    return function off() {
        window.removeEventListener('hashchange', listener);
    };
}

export const AdminRouter = {
    ROUTE_PERMISSION: ROUTE_PERMISSION,
    ROUTE_TITLES: ROUTE_TITLES,
    ROUTE_SUBTITLES: ROUTE_SUBTITLES,
    ROUTE_GROUPS: ROUTE_GROUPS,
    PAGE_COMPONENTS: PAGE_COMPONENTS,
    GROUP_ORDER: GROUP_ORDER,
    routes: routeNames,
    routeName: routeName,
    routeTitle: routeTitle,
    routeGroup: routeGroup,
    routeSubtitle: routeSubtitle,
    permissionsOf: permissionsOf,
    allowedRoutes: allowedRoutes,
    canAccess: canAccess,
    pageComponentName: pageComponentName,
    registerRoute: registerRoute,
    navigate: navigate,
    onRouteChange: onRouteChange
};

/* 兼容入口：旧登录页/页面脚本读取 window.AdminApp.routePermission。 */
window.AdminApp = window.AdminApp || {};
window.AdminApp.routePermission = ROUTE_PERMISSION;
window.AdminRouter = AdminRouter;

export default AdminRouter;
