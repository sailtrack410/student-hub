/* 应用外壳：Hash 路由、按权限渲染菜单、登录守卫与 401 统一跳转。 */
(function () {
    'use strict';

    var Vue = window.Vue;
    var El = window.ElementPlus;
    var api = window.AdminApi;

    var ROUTE_PERMISSION = {
        workorders: ['work-order:dispatch'],
        users: ['system:user:manage'],
        ai: ['ai:manage']
    };

    var ROUTE_TITLES = {
        workorders: '工单调度',
        users: '用户管理',
        ai: 'AI 配置'
    };

    window.AdminApp = {
        routePermission: ROUTE_PERMISSION,
        state: Vue.reactive({ user: null })
    };

    function permissionsOf() {
        return (window.AdminApp.state.user && window.AdminApp.state.user.permissions) || [];
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
            '      <el-icon :size="20"><component :is="PlatformIcon" /></el-icon>',
            '      <span>Student Hub</span>',
            '    </div>',
            '    <el-menu :default-active="route" background-color="#1f2d3d" text-color="#bfcbd9" active-text-color="#ffffff" @select="navigate">',
            '      <el-menu-item v-for="item in menuItems" :key="item.route" :index="item.route">',
            '        <el-icon><component :is="item.icon" /></el-icon>',
            '        <span>{{ item.label }}</span>',
            '      </el-menu-item>',
            '    </el-menu>',
            '  </aside>',
            '  <main>',
            '    <div class="topbar">',
            '      <span class="title">{{ title }}</span>',
            '      <span style="display:flex; align-items:center; gap:12px">',
            '        <span class="muted">{{ user.displayName }}（{{ user.username }}）</span>',
            '        <el-button link type="danger" @click="logout">退出登录</el-button>',
            '      </span>',
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
            };

            var menuItems = Vue.computed(function () {
                return allowedRoutes().map(function (item) {
                    return { route: item, label: ROUTE_TITLES[item], icon: ICONS[item] };
                });
            });

            var pageComponent = Vue.computed(function () {
                var mapping = { workorders: 'WorkOrdersPage', users: 'UsersPage', ai: 'AiConfigPage' };
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
                pageComponent: pageComponent,
                title: title,
                navigate: navigate,
                logout: logout,
                PlatformIcon: ElementPlusIconsVue.Platform
            };
        }
    };

    var app = Vue.createApp(RootApp);
    app.use(El, { locale: window.ElementPlusLocaleZhCn });
    app.component('LoginPage', window.AdminPages.LoginPage);
    app.component('WorkOrdersPage', window.AdminPages.WorkOrdersPage);
    app.component('UsersPage', window.AdminPages.UsersPage);
    app.component('AiConfigPage', window.AdminPages.AiConfigPage);
    app.component('AiProvidersPage', window.AdminPages.AiProvidersPage);
    app.component('AiModelsPage', window.AdminPages.AiModelsPage);
    app.component('AiDefaultsPage', window.AdminPages.AiDefaultsPage);
    for (var name in window.ElementPlusIconsVue) {
        if (Object.prototype.hasOwnProperty.call(window.ElementPlusIconsVue, name)) {
            app.component(name, window.ElementPlusIconsVue[name]);
        }
    }
    app.mount('#app');
})();
