/* 应用外壳：Hash 路由、按权限渲染菜单、登录守卫与 401 统一跳转。 */
(function () {
    'use strict';

    var Vue = window.Vue;
    var El = window.ElementPlus;
    var api = window.AdminApi;

    // 画布 18 页全量白名单：platform:all 为超管万能权限；未实现后端的页面用静态稿页占位
    // bigscreen 为客户侧数据大屏（/screen/），不属管理台菜单，路由保留供直链
    var ROUTE_PERMISSION = {
        dashboard: ['platform:all', 'system:user:manage'],
        services: ['platform:all', 'catalog:manage'],
        requests: ['platform:all', 'service-request:view:self'],
        workorders: ['platform:all', 'work-order:dispatch'],
        eval: ['platform:all'],
        leaves: ['platform:all'],
        crisis: ['platform:all'],
        community: ['platform:all', 'community:moderate'],
        ai: ['platform:all', 'ai:manage'],
        knowledge: ['platform:all', 'ai:manage'],
        aitest: ['platform:all', 'ai:manage'],
        users: ['platform:all', 'system:user:manage'],
        roles: ['platform:all', 'system:role:manage'],
        orgs: ['platform:all', 'system:organization:manage'],
        dicts: ['platform:all'],
        notices: ['platform:all'],
        audit: ['platform:all']
    };

    var ROUTE_TITLES = {
        dashboard: '工作台',
        services: '服务目录',
        requests: '服务申请',
        workorders: '工单调度',
        eval: '服务评价',
        leaves: '请假审批',
        crisis: '危机预警',
        community: '校园社区',
        ai: 'AI 基座',
        knowledge: '知识库',
        aitest: 'AI 对话测试',
        users: '用户管理',
        roles: '角色权限',
        orgs: '组织架构',
        dicts: '字典管理',
        notices: '通知公告',
        audit: '审计日志'
    };

    // 侧栏分组（画布导航结构）；bigscreen 已从管理台菜单移除（客户侧大屏）
    var ROUTE_GROUPS = {
        dashboard: '概览',
        services: '服务运营',
        requests: '服务运营',
        workorders: '服务运营',
        eval: '服务运营',
        leaves: '学生工作',
        crisis: '学生工作',
        community: '内容与智能',
        ai: '内容与智能',
        knowledge: '内容与智能',
        aitest: '内容与智能',
        users: '用户与组织',
        roles: '用户与组织',
        orgs: '用户与组织',
        dicts: '用户与组织',
        notices: '系统管理',
        audit: '系统管理'
    };

    // 有真实后端的页面（其余走设计稿静态稿）
    var REAL_PAGES = ['dashboard', 'services', 'requests', 'workorders', 'eval', 'community', 'users', 'ai', 'aitest', 'knowledge'];

    window.AdminApp = {
        routePermission: ROUTE_PERMISSION,
        state: Vue.reactive({ user: null })
    };

    function permissionsOf() {
        var perms = (window.AdminApp.state.user && window.AdminApp.state.user.permissions) || [];
        // 超管角色注入万能权限，白名单全开
        var roles = (window.AdminApp.state.user && window.AdminApp.state.user.roles) || [];
        if (roles.indexOf('PLATFORM_ADMIN') >= 0 && perms.indexOf('platform:all') < 0) {
            perms = perms.concat(['platform:all']);
        }
        return perms;
    }

    function allowedRoutes() {
        var perms = permissionsOf();
        return Object.keys(ROUTE_PERMISSION).filter(function (route) {
            return ROUTE_PERMISSION[route].some(function (code) {
                return perms.indexOf(code) >= 0;
            });
        });
    }

    function routeName() {
        var hash = location.hash || '';
        var name = hash.replace(/^#\/?/, '').split('?')[0];
        return name || '';
    }

    var RootApp = {
        name: 'AdminRoot',
        template: [
            '<div v-if="route === \'login\'" style="height:100%">',
            '  <component :is="\'LoginPage\'" />',
            '</div>',
            '<div v-else-if="user" class="layout">',
            '  <aside>',
            '    <div class="brand">',
            '      <div class="mark"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l7 2.8v5.6c0 4.4-2.9 7.4-7 8.9-4.1-1.5-7-4.5-7-8.9V5.8z"/><path d="M9.2 12l2 2 3.6-3.8"/></svg></div>',
            '      <div><b>Student Hub</b><em>智慧校园 · 管理台</em></div>',
            '    </div>',
            '    <nav>',
            '      <div v-for="grp in groupedMenu" :key="grp.name">',
            '        <div class="grp">{{ grp.name }}</div>',
            '        <div v-for="item in grp.items" :key="item.route" class="item"',
            '             :class="{ on: route === item.route }" @click="navigate(item.route)">',
            '          <span>{{ item.label }}</span>',
            '        </div>',
            '      </div>',
            '    </nav>',
            '    <div class="side-foot">',
            '      <div class="me-card">',
            '        <div class="av">{{ (user.displayName || "?").slice(0, 1) }}</div>',
            '        <div><div class="nm">{{ user.displayName }}</div><div class="rl">{{ user.username }}</div></div>',
            '        <span class="out" @click="logout">退出</span>',
            '      </div>',
            '    </div>',
            '  </aside>',
            '  <main>',
            '    <div class="topbar">',
            '      <div class="search"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="M20 20l-4.3-4.3"/></svg><span>搜索工单、用户、内容…</span></div>',
            '      <div class="r">',
            '        <button class="ticon" :title="theme === \'dark\' ? \'切换到浅色\' : \'切换到深色\'" @click="toggleTheme">',
            '          <svg v-if="theme === \'dark\'" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="12" r="4.2"/><path d="M12 2.5v2.2M12 19.3v2.2M21.5 12h-2.2M4.7 12H2.5M18.7 5.3l-1.6 1.6M6.9 17.1l-1.6 1.6M18.7 18.7l-1.6-1.6M6.9 6.9L5.3 5.3"/></svg>',
            '          <svg v-else width="17" height="17" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round"><path d="M20.5 14.5A8.5 8.5 0 019.5 3.5a8.5 8.5 0 1011 11z"/></svg>',
            '        </button>',
            '        <button class="ticon" title="通知"><svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round"><path d="M18 9a6 6 0 10-12 0c0 6.5-2.5 8.5-2.5 8.5h17S18 15.5 18 9z"/><path d="M13.7 20.5a2 2 0 01-3.4 0"/></svg><span class="dot"></span></button>',
            '        <div class="me">',
            '          <div class="av">{{ (user.displayName || "?").slice(0, 1) }}</div>',
            '          <div><span class="nm">{{ user.displayName }}</span><span class="rl">超级管理员</span></div>',
            '        </div>',
            '      </div>',
            '    </div>',
            '    <div class="content">',
            '      <component :is="pageComponent" />',
            '    </div>',
            '  </main>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var route = Vue.ref(routeName());
            var ready = Vue.ref(false);

            var ICONS = {
                workorders: ElementPlusIconsVue.Promotion,
                users: ElementPlusIconsVue.User,
                ai: ElementPlusIconsVue.MagicStick
                ,knowledge: ElementPlusIconsVue.Collection
            };

            var theme = Vue.ref(localStorage.getItem('admin_theme') || 'dark');
            document.documentElement.dataset.theme = theme.value;

            function toggleTheme() {
                theme.value = theme.value === 'dark' ? 'light' : 'dark';
                document.documentElement.dataset.theme = theme.value;
                localStorage.setItem('admin_theme', theme.value);
            }

            var menuItems = Vue.computed(function () {
                return allowedRoutes().map(function (item) {
                    return { route: item, label: ROUTE_TITLES[item], icon: ICONS[item], group: ROUTE_GROUPS[item] || '功能' };
                });
            });

            var groupedMenu = Vue.computed(function () {
                var groups = [];
                var byName = {};
                menuItems.value.forEach(function (item) {
                    if (!byName[item.group]) {
                        byName[item.group] = { name: item.group, items: [] };
                        groups.push(byName[item.group]);
                    }
                    byName[item.group].items.push(item);
                });
                return groups;
            });

            var pageComponent = Vue.computed(function () {
                var mapping = {
                    dashboard: 'DashboardPage',
                    services: 'ServiceCatalogPage',
                    requests: 'RequestsPage',
                    workorders: 'WorkOrdersPage',
                    eval: 'EvalPage',
                    leaves: 'DesignPage',
                    crisis: 'DesignPage',
                    community: 'CommunityAdminPage',
                    ai: 'AiConfigPage',
                    knowledge: 'KnowledgeBasePage',
                    aitest: 'AiTestPage',
                    users: 'UsersPage',
                    roles: 'DesignPage',
                    orgs: 'DesignPage',
                    dicts: 'DesignPage',
                    notices: 'DesignPage',
                    audit: 'DesignPage'
                };
                return mapping[route.value] || 'LoginPage';
            });

            var title = Vue.computed(function () {
                return ROUTE_TITLES[route.value] || '管理台';
            });

            function goLogin() {
                window.AdminApp.state.user = null;
                if (route.value !== 'login') {
                    location.hash = '#/login';
                }
            }

            function enter() {
                var name = routeName();
                if (!api.getToken()) {
                    if (name !== 'login') location.hash = '#/login';
                    return;
                }
                if (name === 'login') {
                    if (window.AdminApp.state.user) {
                        location.hash = '#/' + (allowedRoutes()[0] || '');
                    }
                    return;
                }
                if (!window.AdminApp.state.user) {
                    // 令牌存在但会话尚未恢复，等 restoreUser 完成后再做权限判断
                    return;
                }
                var permitted = allowedRoutes().indexOf(name) >= 0;
                if (!permitted) {
                    var fallback = allowedRoutes()[0];
                    El.ElMessage.warning('没有访问该页面的权限');
                    location.hash = fallback ? '#/' + fallback : '#/login';
                    if (!fallback) {
                        api.setToken('');
                    }
                }
            }

            function restoreUser() {
                if (!api.getToken()) {
                    ready.value = true;
                    return;
                }
                api.get('/api/common/auth/me', { skipUnauthorized: false })
                    .then(function (user) {
                        window.AdminApp.state.user = user;
                        ready.value = true;
                        Vue.nextTick(enter);
                    })
                    .catch(function () {
                        // 401 已由 API 客户端统一清理并跳转
                        ready.value = true;
                    });
            }

            function navigate(index) {
                location.hash = '#/' + index;
            }

            function logout() {
                var token = api.getToken();
                api.setToken('');
                window.AdminApp.state.user = null;
                location.hash = '#/login';
                if (token) {
                    api.post('/api/common/auth/logout', undefined, { skipUnauthorized: true })
                        .catch(function () {});
                }
            }

            function onHashChange() {
                var next = routeName();
                route.value = next || 'login';
                enter();
                if (route.value !== 'login' && !window.AdminApp.state.user) {
                    restoreUser();
                }
            }

            api.onUnauthorized(function () {
                El.ElMessage.error('登录已过期，请重新登录');
                goLogin();
            });

            window.addEventListener('hashchange', onHashChange);

            Vue.onMounted(function () {
                if (!route.value) {
                    // 初始无 hash：有令牌先拉会话再定向，否则去登录页
                    route.value = 'login';
                    if (api.getToken()) {
                        restoreUser();
                    }
                    location.hash = '#/login';
                    return;
                }
                route.value = route.value;
                if (route.value !== 'login' && !window.AdminApp.state.user) {
                    restoreUser();
                } else {
                    ready.value = true;
                }
                enter();
            });

            return {
                route: route,
                user: Vue.computed(function () { return window.AdminApp.state.user; }),
                menuItems: menuItems,
                groupedMenu: groupedMenu,
                pageComponent: pageComponent,
                title: title,
                navigate: navigate,
                logout: logout,
                theme: theme,
                toggleTheme: toggleTheme,
                PlatformIcon: ElementPlusIconsVue.Platform
            };
        }
    };

    var app = Vue.createApp(RootApp);
    app.use(El, { locale: window.ElementPlusLocaleZhCn });
    // 全局 Markdown 渲染组件（md-view.js，marked + DOMPurify），任意页面模板可直接用 <md-view :source="..." />
    app.component('md-view', window.AdminUI.mdView);
    app.component('LoginPage', window.AdminPages.LoginPage);
    app.component('WorkOrdersPage', window.AdminPages.WorkOrdersPage);
    app.component('UsersPage', window.AdminPages.UsersPage);
    app.component('AiConfigPage', window.AdminPages.AiConfigPage);
    app.component('AiProvidersPage', window.AdminPages.AiProvidersPage);
    app.component('AiModelsPage', window.AdminPages.AiModelsPage);
    app.component('AiDefaultsPage', window.AdminPages.AiDefaultsPage);
    app.component('KnowledgeBasePage', window.AdminPages.KnowledgeBasePage);
    app.component('ServiceCatalogPage', window.AdminPages.ServiceCatalogPage);
    app.component('DesignPage', window.AdminPages.DesignPage);
    app.component('BigscreenPage', window.AdminPages.BigscreenPage);
    app.component('DashboardPage', window.AdminPages.DashboardPage);
    app.component('RequestsPage', window.AdminPages.RequestsPage);
    app.component('EvalPage', window.AdminPages.EvalPage);
    app.component('CommunityAdminPage', window.AdminPages.CommunityAdminPage);
    app.component('AiTestPage', window.AdminPages.AiTestPage);
    for (var name in window.ElementPlusIconsVue) {
        if (Object.prototype.hasOwnProperty.call(window.ElementPlusIconsVue, name)) {
            app.component(name, window.ElementPlusIconsVue[name]);
        }
    }
    app.mount('#app');
})();
