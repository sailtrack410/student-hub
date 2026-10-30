/* 登录页：按共享画布设计稿还原（左右分栏：左品牌区 + 右登录卡）。
   密码登录逻辑与原版一致（dev/test 开启 password-login-enabled 后可用）。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    window.AdminPages.LoginPage = {
        name: 'LoginPage',
        template: [
            '<div class="login-wrap">',
            '  <div class="login-brand">',
            '    <div class="login-logo">',
            '      <div class="mark"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l7 2.8v5.6c0 4.4-2.9 7.4-7 8.9-4.1-1.5-7-4.5-7-8.9V5.8z"/><path d="M9.2 12l2 2 3.6-3.8"/></svg></div>',
            '      <div><b>Student Hub</b><em>智慧校园 · 管理台</em></div>',
            '    </div>',
            '    <div class="brand-mid">',
            '      <h1>智慧校园，<br>一站式管理。</h1>',
            '      <p class="brand-sub">服务大厅 · AI 助手 · 校园服务 · 数据洞察，一站触达。</p>',
            '      <div class="brand-feats">',
            '        <div class="feat"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke-width="1.7" stroke-linecap="round"><rect x="5" y="4" width="14" height="17" rx="2.2"/><path d="M5 9h14M9.5 13h5M9.5 17h5"/></svg><span>服务工单全流程闭环</span></div>',
            '        <div class="feat"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke-width="1.7" stroke-linecap="round"><path d="M12 3l1.8 4.6L18 9.4l-4.2 1.8L12 15.8l-1.8-4.6L6 9.4l4.2-1.8z"/></svg><span>AI 基座与统一知识库</span></div>',
            '        <div class="feat"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke-width="1.7" stroke-linecap="round"><path d="M12 3l7 2.8v5.6c0 4.4-2.9 7.4-7 8.9-4.1-1.5-7-4.5-7-8.9V5.8z"/></svg><span>统一权限与操作审计</span></div>',
            '      </div>',
            '    </div>',
            '    <div class="brand-foot">Student Hub 智慧校园平台 · 2026</div>',
            '  </div>',
            '  <div class="login-panel">',
            '    <div class="login-panel-inner">',
            '    <div class="login-panel-head"><span class="login-kicker"><i></i>Campus OS · 管理控制面</span><span class="login-env">DEV ENV</span></div>',
            '    <form class="login-card" @submit.prevent="submit">',
            '      <h2>欢迎登录</h2>',
            '      <div class="sub">使用 Student Hub 管理台账号继续</div>',
            '      <div class="login-field">',
            '        <label>用户名</label>',
            '        <input v-model.trim="username" type="text" placeholder="用户名" autocomplete="username" autofocus />',
            '      </div>',
            '      <div class="login-field">',
            '        <label>密码</label>',
            '        <input v-model="password" type="password" placeholder="请输入密码" autocomplete="current-password" @keyup.enter="submit" />',
            '      </div>',
            '      <div class="login-field" v-if="captcha.enabled">',
            '        <label>图形验证码</label>',
            '        <div class="login-captcha">',
            '          <input v-model.trim="captchaCode" type="text" maxlength="8" placeholder="输入右侧字符" autocomplete="off" @keyup.enter="submit" />',
            '          <img :src="captcha.image" alt="图形验证码" title="点击刷新验证码" @click="loadCaptcha" />',
            '        </div>',
            '        <div class="login-captcha-tip" @click="loadCaptcha">看不清？换一张</div>',
            '      </div>',
            '      <button type="button" class="login-btn" :disabled="loading" @click="submit">{{ loading ? "登录中…" : "登 录" }}</button>',
            '      <div class="login-tip">生产环境请使用企业统一账号体系；开发环境可在 application 配置中开启密码登录。</div>',
            '    </form>',
            '    <div class="login-panel-foot"><span>仅限授权管理员访问</span><span>操作留痕 · 会话可追溯</span></div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var username = Vue.ref('');
            var password = Vue.ref('');
            var loading = Vue.ref(false);
            var captcha = Vue.reactive({ enabled: false, id: '', image: '' });
            var captchaCode = Vue.ref('');

            function loadCaptcha() {
                api.get('/api/common/auth/captcha', { skipUnauthorized: true })
                    .then(function (data) {
                        captcha.enabled = !!(data && data.enabled);
                        captcha.id = (data && data.captchaId) || '';
                        captcha.image = (data && data.image) || '';
                        captchaCode.value = '';
                    })
                    .catch(function () {
                        /* 验证码接口不可用时保持关闭，不阻断密码登录；服务端启用后仍会拒绝无验证码登录。 */
                        captcha.enabled = false;
                        captcha.id = '';
                        captcha.image = '';
                    });
            }

            function submit() {
                if (!username.value || !password.value) {
                    El.ElMessage.warning('请输入用户名和密码');
                    return;
                }
                if (captcha.enabled && !captchaCode.value) {
                    El.ElMessage.warning('请输入图形验证码');
                    return;
                }
                loading.value = true;
                var payload = { username: username.value, password: password.value };
                if (captcha.enabled) {
                    payload.captchaId = captcha.id;
                    payload.captchaCode = captchaCode.value;
                }
                api.post('/api/common/auth/login', payload,
                        { skipUnauthorized: true })
                    .then(function (data) {
                        var perms = (data.user && data.user.permissions) || [];
                        var allowed = Object.keys(window.AdminApp.routePermission).filter(function (route) {
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
                        /* 先加载校区上下文（/api/admin/context），再进入首个路由：
                           这样目标页面的首个 /api/admin/** 请求就带 X-Campus-Id。
                           上下文失败不阻断登录，保留用户信息并提示，避免白屏。 */
                        var contextReady = (window.AdminContext && typeof window.AdminContext.load === 'function')
                            ? window.AdminContext.load().catch(function (error) {
                                El.ElMessage.warning('校区上下文加载失败：'
                                    + ((error && error.message) || '未知错误') + '，已使用账号默认校区');
                            })
                            : Promise.resolve();
                        contextReady.then(function () {
                            location.hash = '#/' + allowed[0];
                        });
                    })
                    .catch(function (error) {
                        El.ElMessage.error(error.message || '登录失败');
                        if (captcha.enabled) {
                            loadCaptcha();
                        }
                    })
                    .finally(function () {
                        loading.value = false;
                    });
            }

            Vue.onMounted(loadCaptcha);

            return {
                username: username,
                password: password,
                loading: loading,
                captcha: captcha,
                captchaCode: captchaCode,
                loadCaptcha: loadCaptcha,
                submit: submit
            };
        }
    };
})();
