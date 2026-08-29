/* 登录页：密码登录（dev/test 开启 password-login-enabled 后可用）。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    window.AdminPages.LoginPage = {
        name: 'LoginPage',
        template: [
            '<div class="login-wrap">',
            '  <el-form class="login-card" @submit.prevent="submit">',
            '    <h1>Student Hub 管理台</h1>',
            '    <p class="subtitle">请使用管理员账号登录</p>',
            '    <el-form-item>',
            '      <el-input v-model.trim="username" placeholder="用户名" size="large" :prefix-icon="UserIcon" autocomplete="username" />',
            '    </el-form-item>',
            '    <el-form-item>',
            '      <el-input v-model="password" type="password" placeholder="密码" size="large" :prefix-icon="LockIcon" show-password autocomplete="current-password" @keyup.enter="submit" />',
            '    </el-form-item>',
            '    <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="submit">登 录</el-button>',
            '    <p class="form-hint" style="margin-top:14px">生产环境请使用企业统一账号体系；开发环境可在 application 配置中开启密码登录。</p>',
            '  </el-form>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var username = Vue.ref('');
            var password = Vue.ref('');
            var loading = Vue.ref(false);

            function submit() {
                if (!username.value || !password.value) {
                    El.ElMessage.warning('请输入用户名和密码');
                    return;
                }
                loading.value = true;
                api.post('/api/common/auth/login', { username: username.value, password: password.value },
                        { skipUnauthorized: true })
                    .then(function (data) {
                        var perms = (data.user && data.user.permissions) || [];
                        var allowed = ['workorders', 'users', 'ai'].filter(function (route) {
                            return window.AdminApp.routePermission[route].some(function (p) {
                                return perms.indexOf(p) >= 0;
                            });
                        });
                        if (!allowed.length) {
                            api.post('/api/common/auth/logout', undefined, { skipUnauthorized: true }).catch(function () {});
                            api.setToken('');
                            El.ElMessage.error('该账号没有管理端权限');
                            return;
                        }
                        api.setToken(data.accessToken);
                        window.AdminApp.state.user = data.user;
                        location.hash = '#/' + allowed[0];
                    })
                    .catch(function (error) {
                        El.ElMessage.error(error.message || '登录失败');
                    })
                    .finally(function () {
                        loading.value = false;
                    });
            }

            return {
                username: username,
                password: password,
                loading: loading,
                submit: submit,
                UserIcon: ElementPlusIconsVue.User,
                LockIcon: ElementPlusIconsVue.Lock
            };
        }
    };
})();
