/*
 * Campus OS 管理控制面壳层（原生 ES Module，无构建链）。
 *
 * 分层：
 *   vendor（Vue / Element Plus，index.html 同步加载）
 *   → core/api.js（统一客户端 + X-Campus-Id + 401）
 *   → core/context.js（校区上下文）
 *   → core/router.js（路由元数据 / 权限过滤 / hash 监听）
 *   → core/registry.js（window.AdminPages 注册中心）
 *   → pages/*.js（传统脚本按路由加载并注册组件）
 *   → app.js（本文件：登录守卫、侧栏、顶栏、校区切换、命令面板、健康状态、主题）
 */
import { AdminApi } from './core/api.js';
import { AdminContext } from './core/context.js';
import {
    ROUTE_GROUPS,
    ROUTE_TITLES,
    ROUTE_SUBTITLES,
    PAGE_COMPONENTS,
    GROUP_ORDER,
    routeName,
    allowedRoutes,
    permissionsOf,
    pageComponentName
} from './core/router.js';
import { loadPage } from './core/page-loader.js';

const Vue = window.Vue;
const El = window.ElementPlus;
const Icons = window.ElementPlusIconsVue;

/* 兼容 window.AdminApp.state.user：登录页等传统脚本仍通过它读写当前用户。 */
window.AdminApp = window.AdminApp || {};
window.AdminApp.state = window.AdminApp.state || Vue.reactive({ user: null });
window.AdminApp.context = AdminContext;

const ROUTE_ICONS = {
    dashboard: Icons.DataLine,
    analytics: Icons.TrendCharts,
    services: Icons.Grid,
    requests: Icons.Tickets,
    leave: Icons.Calendar,
    workorders: Icons.Promotion,
    sla: Icons.Timer,
    eval: Icons.Star,
    'community-overview': Icons.TrendCharts,
    'community-posts': Icons.ChatDotRound,
    community: Icons.Warning,
    'community-appeals': Icons.RefreshLeft,
    'community-sections': Icons.Menu,
    'community-topics': Icons.CollectionTag,
    'community-words': Icons.Filter,
    'community-official': Icons.Bell,
    'community-accounts': Icons.UserFilled,
    'community-ads': Icons.VideoCamera,
    'community-advertisers': Icons.Postcard,
    users: Icons.User,
    roles: Icons.Key,
    orgs: Icons.OfficeBuilding,
    dicts: Icons.Notebook,
    achievements: Icons.Trophy,
    ai: Icons.MagicStick,
    knowledge: Icons.Collection,
    aitest: Icons.ChatLineRound,
    homework: Icons.EditPen,
    'app-versions': Icons.Upload,
    notices: Icons.BellFilled,
    audit: Icons.Document,
    'system-config': Icons.Setting,
    'file-storage': Icons.FolderOpened,
    'scheduled-tasks': Icons.AlarmClock,
    push: Icons.Message,
    'ai-security': Icons.Lock,
    'content-calendar': Icons.Calendar,
    creators: Icons.Avatar
};

const PageLoading = {template: '<div class="page-loading" role="status">正在加载页面…</div>'};
const PageLoadError = {
    props: ['error'],
    template: '<div class="api-error-panel" role="alert"><strong>页面资源加载失败</strong><p>{{ error && error.message }}</p><button type="button" @click="retry">重新加载页面</button></div>',
    methods: {retry: function () { window.location.reload(); }}
};
function asyncPage(name) {
    return Vue.defineAsyncComponent({loader: function () { return loadPage(name); }, loadingComponent: PageLoading,
        errorComponent: PageLoadError, delay: 120, timeout: 20000});
}

const RootApp = {
    name: 'AdminRoot',
    template: [
        '<div v-if="route === \'login\'" style="height:100%">',
        '  <component :is="\'LoginPage\'" />',
        '</div>',
        '<div v-else-if="user" class="layout" :class="{ \'rail-open\': railOpen }">',
        '  <div class="rail-mask" @click="closeRail"></div>',
        '  <aside class="rail">',
        '    <div class="brand">',
        '      <div class="mark"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l7 2.8v5.6c0 4.4-2.9 7.4-7 8.9-4.1-1.5-7-4.5-7-8.9V5.8z"/><path d="M9.2 12l2 2 3.6-3.8"/></svg></div>',
        '      <div><b>Campus OS</b><em>STUDENT HUB · 控制面</em></div>',
        '    </div>',
        '    <nav>',
        '      <div v-for="grp in groupedMenu" :key="grp.name" class="nav-group">',
        '        <button class="grp" type="button" :aria-expanded="isGroupOpen(grp.name)"',
        '                :class="{ active: pageGroup === grp.name && !isGroupOpen(grp.name) }"',
        '                @click="toggleGroup(grp.name)">',
        '          <span class="grp-name">{{ grp.name }}</span>',
        '          <span class="grp-n">{{ grp.items.length }}</span>',
        '          <span class="grp-caret" :class="{ open: isGroupOpen(grp.name) }"></span>',
        '        </button>',
        '        <div class="grp-items" v-show="isGroupOpen(grp.name)">',
        '          <div v-for="item in grp.items" :key="item.route" class="item"',
        '               :class="{ on: route === item.route }" @click="navigate(item.route)">',
        '            <span class="ico"><component :is="item.icon" /></span>',
        '            <span>{{ item.label }}</span>',
        '          </div>',
        '        </div>',
        '      </div>',
        '    </nav>',
        '    <div class="side-foot">',
        '      <div class="me-card">',
        '        <div class="av">{{ (user.displayName || "?").slice(0, 1) }}</div>',
        '        <div><span class="nm">{{ user.displayName }}</span><span class="rl">{{ roleLabel }}</span></div>',
        '        <div class="out" title="退出登录" @click="logout"><el-icon><SwitchButton /></el-icon></div>',
        '      </div>',
        '    </div>',
        '  </aside>',
        '  <main>',
        '    <header class="topbar">',
        '      <button class="rail-toggle" type="button" title="展开菜单" @click="toggleRail">',
        '        <el-icon :size="16"><Menu /></el-icon>',
        '      </button>',
        '      <div class="tb-title">',
        '        <div class="tb-kicker"><span class="dot"></span>{{ pageGroup || "控制台" }} · CONTROL PLANE</div>',
        '        <h1>{{ pageTitle }}<span class="sub">{{ pageSubtitle }}</span></h1>',
        '      </div>',
        '      <div class="top-actions">',
        '        <div class="campus-wrap" @click.stop>',
        '          <button class="campus-pill" type="button" :title="campusHint" @click="toggleCampusMenu">',
        '            <i :class="{ off: !campusLoaded }"></i><span class="campus-name">{{ campusLabel }}</span>',
        '            <el-icon :size="11" class="campus-caret"><ArrowDown /></el-icon>',
        '          </button>',
        '          <div v-if="campusMenuOpen" class="campus-menu">',
        '            <div class="cm-head">数据范围 · 校区</div>',
        '            <button v-for="c in campusOptions" :key="c.campusId" type="button" class="campus-opt"',
        '                    :class="{ on: isCurrentCampus(c), locked: !platformAdmin }" @click="selectCampus(c)">',
        '              <span class="dot"></span>',
        '              <span class="nm">{{ c.campusName }}</span>',
        '              <span class="tag">{{ isCurrentCampus(c) ? "当前" : (c.status === "ENABLED" ? "可切换" : "停用") }}</span>',
        '            </button>',
        '            <div v-if="!campusOptions.length" class="cm-empty">{{ campusError || "校区加载中…" }}</div>',
        '            <div v-if="!platformAdmin" class="cm-note">当前账号仅可查看所属校区</div>',
        '            <div v-if="canManageOrgs" class="campus-foot">',
        '              <button type="button" class="campus-opt manage" @click="goOrgs">组织架构 · 校区与归属</button>',
        '            </div>',
        '          </div>',
        '        </div>',
        '        <button class="cmd-trigger" @click="openCommand">',
        '          <el-icon :size="14"><Search /></el-icon><span class="cmd-text">搜索页面、功能</span><kbd>⌘K</kbd>',
        '        </button>',
        '        <button class="density-toggle" type="button" :aria-pressed="density === \'compact\'" @click="toggleDensity">{{ density === "compact" ? "紧凑" : "舒适" }}</button>',
        '        <button class="ticon" title="切换主题" @click="toggleTheme">',
        '          <el-icon :size="15"><component :is="theme === \'dark\' ? \'Sunny\' : \'Moon\'" /></el-icon>',
        '        </button>',
        '        <button class="ticon" title="系统配置" @click="goSystem">',
        '          <el-icon :size="15"><Setting /></el-icon>',
        '        </button>',
        '        <button class="ticon" title="通知公告" @click="goNotices">',
        '          <el-icon :size="15"><Bell /></el-icon><span class="badge-dot"></span>',
        '        </button>',
        '        <div class="tavatar" :title="user.displayName">{{ (user.displayName || "?").slice(0, 1) }}</div>',
        '      </div>',
        '    </header>',
        '    <div class="content">',
        '      <section v-if="apiError" class="api-error-panel" role="alert" aria-live="assertive">',
        '        <strong>请求未完成</strong><p>{{ apiError.message }}</p>',
        '        <small>{{ apiError.method }} {{ apiError.path }}<span v-if="apiError.code"> · {{ apiError.code }}</span></small>',
        '        <div><button type="button" @click="reloadCurrent">重新加载当前页</button><button type="button" @click="apiError = null">关闭提示</button></div>',
        '        <small>重新加载只会刷新页面数据。提交操作请核对结果后重新发起。</small>',
        '      </section>',
        '      <component v-if="pageComponent" :is="pageComponent" :key="pageKey" />',
        '      <div v-else class="page-loading" role="status">正在确认页面访问权限…</div>',
        '    </div>',
        '    <footer class="statusbar">',
        '      <span class="sig on"><i></i>控制面在线</span>',
        '      <span class="sig" :class="healthTone"><i></i>{{ healthLabel }}</span>',
        '      <span class="sig" :class="dbTone"><i></i>MySQL {{ dbLabel }}</span>',
        '      <span class="sig" :class="redisTone"><i></i>Redis {{ redisLabel }}</span>',
        '      <span class="sp"></span>',
        '      <span class="hint">操作留痕已启用</span>',
        '      <span class="ver">v1.0 · {{ todayText }}</span>',
        '    </footer>',
        '  </main>',
        '  <div v-if="commandOpen" class="cmd-mask" @click.self="closeCommand">',
        '    <div class="cmd-panel">',
        '      <div class="cmd-input">',
        '        <el-icon :size="16" style="color:var(--tk-muted)"><Search /></el-icon>',
        '        <input ref="commandInput" v-model="commandQuery" placeholder="输入页面名称或功能关键词…" @keydown.esc="closeCommand" @keydown.enter="runCommand" />',
        '        <kbd style="font-size:10px;color:var(--tk-muted);border:1px solid var(--tk-stroke);border-radius:4px;padding:1px 5px">ESC</kbd>',
        '      </div>',
        '      <div class="cmd-list">',
        '        <div v-for="(item, i) in commandResults" :key="item.route" class="cmd-item" :class="{ on: i === commandIndex }" @mouseenter="commandIndex = i" @click="runCommand(item.route)">',
        '          <span class="ic"><component :is="item.icon" /></span>',
        '          <span>{{ item.label }}</span>',
        '          <span class="grp">{{ item.group }}</span>',
        '        </div>',
        '        <div v-if="!commandResults.length" class="cmd-empty">没有匹配的页面或功能</div>',
        '      </div>',
        '      <div class="cmd-foot"><span><kbd>↵</kbd> 进入</span><span><kbd>↑↓</kbd> 选择</span><span><kbd>ESC</kbd> 关闭</span></div>',
        '    </div>',
        '  </div>',
        '</div>'
    ].join('\n'),
    setup: function () {
        const state = window.AdminApp.state;
        const route = Vue.ref(routeName() || 'login');
        /* 切换校区时不整页 reload：重建当前页面组件以触发数据重载，壳层/菜单/主题保持不动。 */
        const pageKey = Vue.ref(0);
        const ready = Vue.ref(false);
        const theme = Vue.ref(localStorage.getItem('admin_theme') || 'light');
        document.documentElement.dataset.theme = theme.value;
        const density = Vue.ref(localStorage.getItem('admin_density') === 'compact' ? 'compact' : 'comfortable');
        document.documentElement.dataset.density = density.value;
        const apiError = Vue.ref(null);
        function toggleDensity() {
            density.value = density.value === 'compact' ? 'comfortable' : 'compact';
            document.documentElement.dataset.density = density.value;
            localStorage.setItem('admin_density', density.value);
        }
        function onApiError(event) { apiError.value = event.detail; }
        function reloadCurrent() { apiError.value = null; pageKey.value += 1; }

        const railOpen = Vue.ref(false);
        const campusMenuOpen = Vue.ref(false);
        const MENU_GROUPS_KEY = 'admin_menu_open_groups_v1';
        function readOpenGroups() {
            const result = {};
            try {
                const saved = JSON.parse(localStorage.getItem(MENU_GROUPS_KEY) || '[]');
                if (Array.isArray(saved)) saved.forEach(function (name) { result[name] = true; });
            } catch (e) { /* 忽略损坏的本地偏好 */ }
            return result;
        }
        const openGroups = Vue.reactive(readOpenGroups());
        function isGroupOpen(name) { return openGroups[name] === true; }
        function toggleGroup(name) {
            if (openGroups[name]) delete openGroups[name];
            else openGroups[name] = true;
            try {
                localStorage.setItem(MENU_GROUPS_KEY, JSON.stringify(Object.keys(openGroups).filter(function (key) {
                    return openGroups[key] === true;
                })));
            } catch (e) { /* 隐私模式下不影响导航 */ }
        }
        const campusState = Vue.reactive(AdminContext.snapshot());
        const unsubscribeContext = AdminContext.subscribe(function (next) {
            Object.assign(campusState, next);
        });

        const health = Vue.ref([]);
        const healthLoaded = Vue.ref(false);
        const commandOpen = Vue.ref(false);
        const commandQuery = Vue.ref('');
        const commandIndex = Vue.ref(0);
        const commandInput = Vue.ref(null);
        const todayText = Vue.ref(new Date().toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }).replace('/', '.'));

        const user = Vue.computed(function () { return state.user; });
        const routeList = Vue.computed(function () { return allowedRoutes(state.user); });

        const menuItems = Vue.computed(function () {
            return routeList.value.map(function (name) {
                return {
                    route: name,
                    label: ROUTE_TITLES[name] || name,
                    icon: ROUTE_ICONS[name] || Icons.Menu,
                    group: ROUTE_GROUPS[name] || '功能'
                };
            });
        });

        const groupedMenu = Vue.computed(function () {
            const groups = [];
            const byName = {};
            menuItems.value.forEach(function (item) {
                if (!byName[item.group]) {
                    byName[item.group] = { name: item.group, items: [] };
                    groups.push(byName[item.group]);
                }
                byName[item.group].items.push(item);
            });
            return groups.sort(function (a, b) {
                const ai = GROUP_ORDER.indexOf(a.name);
                const bi = GROUP_ORDER.indexOf(b.name);
                return (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi);
            });
        });

        const pageComponent = Vue.computed(function () {
            if (route.value === 'login') return 'LoginPage';
            if (!ready.value || routeList.value.indexOf(route.value) < 0) return null;
            return PAGE_COMPONENTS[route.value] ? pageComponentName(route.value) : null;
        });
        const pageTitle = Vue.computed(function () { return ROUTE_TITLES[route.value] || '控制面'; });
        const pageGroup = Vue.computed(function () { return ROUTE_GROUPS[route.value] || ''; });
        const pageSubtitle = Vue.computed(function () { return ROUTE_SUBTITLES[route.value] || ''; });

        const roleLabel = Vue.computed(function () {
            const roles = (state.user && state.user.roles) || [];
            if (roles.indexOf('PLATFORM_ADMIN') >= 0) return '平台管理员';
            if (roles.indexOf('COMMUNITY_OPERATOR') >= 0) return '社区运营';
            return roles.length ? roles[0] : '管理账号';
        });

        /* ---------- 校区切换 ---------- */
        const campusLoaded = Vue.computed(function () { return campusState.loaded === true; });
        const campusError = Vue.computed(function () { return campusState.error || ''; });
        const platformAdmin = Vue.computed(function () { return campusState.platformAdmin === true; });
        const campusOptions = Vue.computed(function () { return campusState.campuses || []; });
        const campusLabel = Vue.computed(function () {
            if (campusState.campusName) return campusState.campusName;
            const stored = AdminApi.getCampusId();
            const match = (campusState.campuses || []).filter(function (item) {
                return stored !== null && String(item.campusId) === String(stored);
            })[0];
            return match ? match.campusName : '校区上下文';
        });
        const campusHint = Vue.computed(function () {
            if (!campusLoaded.value) return '校区上下文加载中';
            return platformAdmin.value ? '点击切换数据所属校区' : '当前账号仅可查看所属校区';
        });
        const canManageOrgs = Vue.computed(function () { return routeList.value.indexOf('orgs') >= 0; });

        function isCurrentCampus(campus) {
            return String(campus.campusId) === String(campusState.campusId);
        }

        function toggleCampusMenu() {
            campusMenuOpen.value = !campusMenuOpen.value;
            if (campusMenuOpen.value && !campusState.loaded && !campusState.loading) {
                loadContextOnce();
            }
        }

        function closeCampusMenu() {
            campusMenuOpen.value = false;
        }

        let campusFormDirty = false;

        function markCampusFormDirty(event) {
            const target = event && event.target;
            if (!target || !target.closest || !target.closest('.content')) return;
            if (target.disabled || target.readOnly) return;
            campusFormDirty = true;
        }

        function hasOpenEditor() {
            const selectors = ['.content .cmd-mask', '.content .pmask.show', '.el-overlay', '.el-dialog', '.el-drawer'];
            return selectors.some(function (selector) {
                return Array.prototype.some.call(document.querySelectorAll(selector), function (element) {
                    const style = window.getComputedStyle(element);
                    return style.display !== 'none' && style.visibility !== 'hidden'
                        && element.getBoundingClientRect().width > 0;
                });
            });
        }

        function performCampusSwitch(campus) {
            if (!AdminContext.setCampus(campus.campusId)) {
                El.ElMessage.error('校区切换失败，请刷新后重试');
                return;
            }
            closeCampusMenu();
            campusFormDirty = false;
            pageKey.value += 1;
            window.scrollTo({ top: 0, behavior: 'auto' });
            El.ElMessage.success('已切换到 ' + (campus.campusName || '目标校区'));
        }

        function selectCampus(campus) {
            if (!campus) return;
            if (isCurrentCampus(campus)) {
                closeCampusMenu();
                return;
            }
            if (!campusState.platformAdmin) {
                El.ElMessage.info('仅平台管理员可切换校区');
                return;
            }
            if (campus.status && campus.status !== 'ENABLED') {
                El.ElMessage.warning('该校区已停用，暂不可切换');
                return;
            }
            /* 打开中的编辑弹层直接阻断：这是最容易丢表单的场景，不弹确认让用户误点。 */
            if (hasOpenEditor()) {
                El.ElMessage.warning('请先保存或关闭当前编辑表单，再切换校区');
                return;
            }
            if (!campusFormDirty) {
                performCampusSwitch(campus);
                return;
            }
            El.ElMessageBox.confirm('当前页面有未提交的改动，切换校区会重新加载本页数据并丢失这些改动。确定继续？',
                '切换校区', { type: 'warning', confirmButtonText: '继续切换', cancelButtonText: '留在本页' })
                .then(function () { performCampusSwitch(campus); })
                .catch(function () {});
        }

        /* ---------- 抽屉侧栏（≤1024 生效，CSS 控制显隐） ---------- */
        function toggleRail() { railOpen.value = !railOpen.value; }
        function closeRail() { railOpen.value = false; }

        /* ---------- 健康状态 ---------- */
        const healthSummary = Vue.computed(function () {
            const comps = health.value || [];
            if (!comps.length) return '';
            if (comps.some(function (c) { return c.status === 'DOWN'; })) return 'DOWN';
            if (comps.some(function (c) { return c.status !== 'UP'; })) return 'DEGRADED';
            return 'UP';
        });
        const healthTone = Vue.computed(function () {
            if (!healthLoaded.value) return '';
            const summary = healthSummary.value;
            return summary === 'UP' ? 'on' : (summary === 'DOWN' ? 'bad' : '');
        });
        const healthLabel = Vue.computed(function () {
            if (!healthLoaded.value) return '健康检查连接中';
            const summary = healthSummary.value;
            if (!summary) return '健康检查不可用';
            return summary === 'UP' ? '全部服务正常' : (summary === 'DOWN' ? '服务异常' : '部分服务降级');
        });
        const dbComp = Vue.computed(function () {
            return (health.value || []).filter(function (c) { return /mysql|数据库/i.test(c.name); })[0];
        });
        const redisComp = Vue.computed(function () {
            return (health.value || []).filter(function (c) { return /redis|缓存/i.test(c.name); })[0];
        });
        const dbTone = Vue.computed(function () { return dbComp.value ? (dbComp.value.status === 'UP' ? 'on' : 'bad') : ''; });
        const dbLabel = Vue.computed(function () { return dbComp.value ? (dbComp.value.status === 'UP' ? '正常' : '异常') : '待检测'; });
        const redisTone = Vue.computed(function () { return redisComp.value ? (redisComp.value.status === 'UP' ? 'on' : 'bad') : ''; });
        const redisLabel = Vue.computed(function () { return redisComp.value ? (redisComp.value.status === 'UP' ? '正常' : '异常') : '待检测'; });

        function hasHealthPermission() {
            const permissions = permissionsOf(state.user);
            return permissions.indexOf('platform:all') >= 0
                || permissions.indexOf('system:config:view') >= 0
                || permissions.indexOf('system:config:manage') >= 0;
        }

        function loadHealth() {
            if (!hasHealthPermission()) return;
            AdminApi.get('/api/admin/system/health', { skipUnauthorized: true, silent: true })
                .then(function (data) {
                    health.value = (data && data.components) || [];
                    healthLoaded.value = true;
                })
                .catch(function () { healthLoaded.value = false; });
        }

        /* ---------- 校区上下文加载 ---------- */
        let contextPromise = null;

        function loadContextOnce(force) {
            if (contextPromise && !force) return contextPromise;
            contextPromise = AdminContext.load()
                .catch(function (error) {
                    const message = (error && error.message) || '校区上下文加载失败';
                    El.ElMessage.warning(message + '（当前使用账号默认校区）');
                    return AdminContext.snapshot();
                });
            return contextPromise;
        }

        /* ---------- 主题 ---------- */
        function toggleTheme() {
            theme.value = theme.value === 'dark' ? 'light' : 'dark';
            document.documentElement.dataset.theme = theme.value;
            localStorage.setItem('admin_theme', theme.value);
        }

        /* ---------- 命令面板 ---------- */
        const commandResults = Vue.computed(function () {
            const query = commandQuery.value.trim().toLowerCase();
            return menuItems.value.filter(function (item) {
                if (!query) return true;
                return (item.label + ' ' + item.group + ' ' + item.route).toLowerCase().indexOf(query) >= 0;
            });
        });

        function openCommand() {
            commandOpen.value = true;
            commandQuery.value = '';
            commandIndex.value = 0;
            closeCampusMenu();
            Vue.nextTick(function () {
                if (commandInput.value) commandInput.value.focus();
            });
        }
        function closeCommand() { commandOpen.value = false; }
        function runCommand(preset) {
            const target = typeof preset === 'string' ? preset : (commandResults.value[commandIndex.value] || {}).route;
            if (!target) return;
            closeCommand();
            navigate(target);
        }

        function onGlobalKey(e) {
            if ((e.metaKey || e.ctrlKey) && (e.key === 'k' || e.key === 'K')) {
                e.preventDefault();
                if (commandOpen.value) closeCommand(); else openCommand();
                return;
            }
            if (e.key === 'Escape') {
                closeCampusMenu();
                closeRail();
            }
            if (!commandOpen.value) return;
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                commandIndex.value = Math.min(commandIndex.value + 1, commandResults.value.length - 1);
            }
            if (e.key === 'ArrowUp') {
                e.preventDefault();
                commandIndex.value = Math.max(commandIndex.value - 1, 0);
            }
            if (e.key === 'Enter') {
                e.preventDefault();
                runCommand();
            }
        }

        /* ---------- 导航 ---------- */
        function navigate(name) {
            closeRail();
            closeCampusMenu();
            window.location.hash = '#/' + name;
        }

        function goNotices() {
            if (routeList.value.indexOf('notices') >= 0) navigate('notices');
            else El.ElMessage.info('当前账号没有通知公告权限');
        }
        function goSystem() { navigate('system-config'); }
        function goOrgs() {
            if (routeList.value.indexOf('orgs') >= 0) navigate('orgs');
            else El.ElMessage.info('当前账号没有组织管理权限');
        }

        function goLogin() {
            state.user = null;
            if (route.value !== 'login') window.location.hash = '#/login';
        }

        /* ---------- 登录守卫 ---------- */
        function enter() {
            const name = routeName() || 'login';
            route.value = name;
            if (!AdminApi.getToken()) {
                if (name !== 'login') window.location.hash = '#/login';
                return;
            }
            if (name === 'login') {
                if (state.user) {
                    const first = allowedRoutes(state.user)[0];
                    if (first) window.location.hash = '#/' + first;
                }
                return;
            }
            if (!state.user) {
                restoreUser();
                return;
            }
            const routes = allowedRoutes(state.user);
            if (routes.indexOf(name) < 0) {
                El.ElMessage.warning('没有访问该页面的权限');
                if (routes[0]) {
                    window.location.hash = '#/' + routes[0];
                } else {
                    AdminApi.setToken('');
                    window.location.hash = '#/login';
                }
                return;
            }
            loadHealth();
            if (!campusState.loaded && !campusState.loading) {
                loadContextOnce();
            }
        }

        function restoreUser() {
            if (!AdminApi.getToken()) {
                ready.value = true;
                return;
            }
            AdminApi.get('/api/common/auth/me', { skipUnauthorized: false })
                .then(function (currentUser) {
                    state.user = currentUser;
                    /* 先加载校区上下文，再渲染业务页，确保页面首个请求就带 X-Campus-Id。 */
                    return loadContextOnce();
                })
                .then(function () {
                    ready.value = true;
                    Vue.nextTick(enter);
                })
                .catch(function () {
                    ready.value = true;
                });
        }

        function logout() {
            const token = AdminApi.getToken();
            AdminApi.setToken('');
            state.user = null;
            AdminContext.reset();
            window.location.hash = '#/login';
            if (token) {
                AdminApi.post('/api/common/auth/logout', undefined, { skipUnauthorized: true }).catch(function () {});
            }
        }

        function onHashChange() {
            apiError.value = null;
            route.value = routeName() || 'login';
            campusFormDirty = false;
            pageKey.value += 1;
            closeCampusMenu();
            if (route.value === 'login') closeRail();
            if (!state.user && AdminApi.getToken()) restoreUser();
            else enter();
        }

        function onDocumentClick() {
            closeCampusMenu();
        }

        AdminApi.onUnauthorized(function () {
            El.ElMessage.error('登录已过期，请重新登录');
            goLogin();
        });

        window.addEventListener('admin-api-error', onApiError);
        window.addEventListener('hashchange', onHashChange);
        window.addEventListener('keydown', onGlobalKey);
        document.addEventListener('click', onDocumentClick);
        document.addEventListener('input', markCampusFormDirty, true);
        document.addEventListener('change', markCampusFormDirty, true);

        Vue.onMounted(function () {
            const name = routeName();
            const flash = AdminContext.consumeCampusFlash();
            if (flash) {
                El.ElMessage.success('已切换到 ' + flash);
            }
            if (!name) {
                route.value = 'login';
                window.location.hash = '#/login';
                if (AdminApi.getToken()) restoreUser();
                else ready.value = true;
                return;
            }
            if (name === 'login') {
                ready.value = true;
                if (AdminApi.getToken()) restoreUser();
                return;
            }
            route.value = name;
            if (!state.user) restoreUser();
            else {
                ready.value = true;
                enter();
            }
        });

        Vue.onUnmounted(function () {
            window.removeEventListener('admin-api-error', onApiError);
            window.removeEventListener('hashchange', onHashChange);
            window.removeEventListener('keydown', onGlobalKey);
            document.removeEventListener('click', onDocumentClick);
            document.removeEventListener('input', markCampusFormDirty, true);
            document.removeEventListener('change', markCampusFormDirty, true);
            unsubscribeContext();
        });

        return {
            route: route,
            pageKey: pageKey,
            ready: ready,
            user: user,
            menuItems: menuItems,
            groupedMenu: groupedMenu,
            pageComponent: pageComponent,
            pageTitle: pageTitle,
            pageGroup: pageGroup,
            pageSubtitle: pageSubtitle,
            roleLabel: roleLabel,
            navigate: navigate,
            toggleGroup: toggleGroup,
            isGroupOpen: isGroupOpen,
            logout: logout,
            density: density,
            toggleDensity: toggleDensity,
            apiError: apiError,
            reloadCurrent: reloadCurrent,
            theme: theme,
            toggleTheme: toggleTheme,
            railOpen: railOpen,
            toggleRail: toggleRail,
            closeRail: closeRail,
            campusMenuOpen: campusMenuOpen,
            campusOptions: campusOptions,
            campusLabel: campusLabel,
            campusHint: campusHint,
            campusLoaded: campusLoaded,
            campusError: campusError,
            platformAdmin: platformAdmin,
            canManageOrgs: canManageOrgs,
            isCurrentCampus: isCurrentCampus,
            toggleCampusMenu: toggleCampusMenu,
            selectCampus: selectCampus,
            commandOpen: commandOpen,
            commandQuery: commandQuery,
            commandResults: commandResults,
            commandIndex: commandIndex,
            commandInput: commandInput,
            openCommand: openCommand,
            closeCommand: closeCommand,
            runCommand: runCommand,
            goNotices: goNotices,
            goSystem: goSystem,
            goOrgs: goOrgs,
            healthTone: healthTone,
            healthLabel: healthLabel,
            dbTone: dbTone,
            dbLabel: dbLabel,
            redisTone: redisTone,
            redisLabel: redisLabel,
            todayText: todayText
        };
    }
};

const app = Vue.createApp(RootApp);
app.use(El, { locale: window.ElementPlusLocaleZhCn });
if (window.AdminUI && window.AdminUI.mdView) {
    app.component('md-view', window.AdminUI.mdView);
}

/* 路由只注册异步工厂，首次进入页面时才请求其脚本。 */
const registered = {};
Object.keys(PAGE_COMPONENTS).forEach(function (routeKey) {
    const componentName = PAGE_COMPONENTS[routeKey];
    if (registered[componentName]) return;
    registered[componentName] = true;
    app.component(componentName, asyncPage(componentName));
});
app.component('LoginPage', asyncPage('LoginPage'));

for (const name in Icons) {
    if (Object.prototype.hasOwnProperty.call(Icons, name)) {
        app.component(name, Icons[name]);
    }
}
app.mount('#app');
