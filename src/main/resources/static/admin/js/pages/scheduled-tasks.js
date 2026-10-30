/* 定时任务 SYS06：列出 Spring 实际注册的 @Scheduled 任务，支持手动执行一次并查看最近结果。
   自动触发由调度器负责；本页“最近执行”只记录本页发起的手动执行（不侵入其他 bundle 埋点）。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    function tone(status) {
        if (status === 'SUCCESS') return 'ok';
        if (status === 'FAILED') return 'bad';
        if (status === 'RUNNING') return 'warn';
        return 'muted';
    }

    window.AdminPages.ScheduledTasksPage = {
        name: 'ScheduledTasksPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>定时任务</h1><div class="sub">@Scheduled 任务清单与手动执行</div></div>',
            '    <div class="r"><button class="btn2" :disabled="loading" @click="load">刷新</button></div></div>',

            '  <div class="os-grid" style="grid-template-columns:repeat(4,1fr)">',
            '    <div class="os-kpi"><div class="k-label"><span class="dot"></span>已注册任务</div><div class="k-val">{{ tasks.length }}<small>个</small></div><div class="k-foot"><span>自动启用 {{ enabledCount }} · 停用 {{ disabledCount }}</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>执行中</div><div class="k-val">{{ runningCount }}</div><div class="k-foot"><span>手动触发并发执行数为 1</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>最近成功</div><div class="k-val">{{ successCount }}</div><div class="k-foot"><span>仅统计本页手动执行</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-bad)"></span>最近失败</div><div class="k-val">{{ failedCount }}</div><div class="k-foot"><span>失败原因见列表</span></div></div>',
            '  </div>',

            '  <div class="os-note"><b>执行说明</b> 自动触发由 Spring 调度器按触发规则执行；「自动」开关只控制自动触发，停用后任务体直接返回，手动执行仍可用于排障。本页「手动执行」会异步跑一次并记录结果，同一任务执行中会拒绝重复触发。</div>',
            '  <div v-if="!canRun" class="os-note warn"><b>只读</b> 当前账号缺少 system:config:manage / platform:all，仅可查看任务清单。</div>',

            '  <div v-if="loading" class="os-panel"><div class="os-empty">正在读取定时任务…</div></div>',
            '  <div v-else-if="error" class="os-panel"><div class="os-empty">{{ error }} <button class="os-btn sm" style="margin-left:8px" @click="load">重试</button></div></div>',

            '  <div v-else class="os-panel" style="flex:1;min-height:0;display:flex;flex-direction:column">',
            '    <div class="os-panel-h"><h3>任务清单</h3><span class="hint">按任务 key 排序</span></div>',
            '    <div style="overflow:auto;flex:1">',
            '      <table style="width:100%;border-collapse:collapse;table-layout:fixed"><thead><tr><th style="width:30%">任务</th><th style="width:22%">触发规则</th><th style="width:100px">最近状态</th><th style="width:128px">最近开始</th><th style="width:86px">耗时</th><th>失败信息</th><th style="width:100px;text-align:right">操作</th></tr></thead><tbody>',
            '        <tr v-for="t in tasks" :key="t.key">',
            '          <td><div style="min-width:0"><b style="font-weight:550;color:var(--tk-text)">{{ t.description }}</b><div class="os-mono" style="font-size:9.5px;color:var(--tk-muted);margin-top:3px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="t.key">{{ t.key }}</div></div></td>',
            '          <td style="font-size:11px;color:var(--tk-text3);overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="t.trigger">{{ t.trigger }}</td>',
            '          <td><el-switch v-if="canRun" :model-value="!!t.enabled" size="small" :loading="t.toggling" @change="toggle(t, $event)" /><span v-else class="os-tag" :class="t.enabled ? \'ok\' : \'muted\'">{{ t.enabled ? "启用" : "停用" }}</span></td>',
            '          <td><span v-if="t.lastStatus" class="os-tag" :class="tone(t.lastStatus)">{{ statusText(t.lastStatus) }}</span><span v-else style="font-size:11px;color:var(--tk-muted)">未手动执行</span></td>',
            '          <td style="font-size:11px;color:var(--tk-text3)">{{ t.lastStartedAt || "—" }}</td>',
            '          <td class="os-mono" style="color:var(--tk-text3)">{{ t.lastDurationMs == null ? "—" : (t.lastDurationMs + " ms") }}</td>',
            '          <td style="font-size:10.5px;color:var(--tk-bad);overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="t.lastError || \'\'">{{ t.lastError || "—" }}</td>',
            '          <td style="text-align:right"><button v-if="canRun" class="os-btn sm" :disabled="t.running" @click="run(t)">{{ t.running ? "执行中…" : "执行一次" }}</button><span v-else style="font-size:11px;color:var(--tk-muted)">只读</span></td>',
            '        </tr>',
            '        <tr v-if="!tasks.length"><td colspan="8" class="os-empty">未发现已注册的 @Scheduled 任务</td></tr>',
            '      </tbody></table>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var tasks = Vue.ref([]);
            var loading = Vue.ref(false);
            var error = Vue.ref('');
            var pollTimer = null;

            function permissions() {
                var user = (window.AdminApp && window.AdminApp.state && window.AdminApp.state.user) || {};
                var perms = (user.permissions || []).slice();
                var roles = user.roles || [];
                if (roles.indexOf('PLATFORM_ADMIN') >= 0 && perms.indexOf('platform:all') < 0) perms.push('platform:all');
                return perms;
            }
            var canRun = Vue.computed(function () {
                var perms = permissions();
                return perms.indexOf('platform:all') >= 0 || perms.indexOf('system:config:manage') >= 0;
            });
            var runningCount = Vue.computed(function () {
                return tasks.value.filter(function (t) { return t.running; }).length;
            });
            var successCount = Vue.computed(function () {
                return tasks.value.filter(function (t) { return t.lastStatus === 'SUCCESS'; }).length;
            });
            var failedCount = Vue.computed(function () {
                return tasks.value.filter(function (t) { return t.lastStatus === 'FAILED'; }).length;
            });
            var enabledCount = Vue.computed(function () {
                return tasks.value.filter(function (t) { return t.enabled !== false; }).length;
            });
            var disabledCount = Vue.computed(function () {
                return tasks.value.filter(function (t) { return t.enabled === false; }).length;
            });
            function statusText(status) {
                return { RUNNING: '执行中', SUCCESS: '成功', FAILED: '失败' }[status] || status;
            }

            function load(showError) {
                loading.value = true;
                return api.get('/api/admin/system/scheduled-tasks')
                    .then(function (data) {
                        tasks.value = data || [];
                        error.value = '';
                        schedulePoll();
                    })
                    .catch(function (err) {
                        error.value = (err && err.message) || '定时任务加载失败';
                        if (showError !== false) El.ElMessage.error(error.value);
                    })
                    .finally(function () { loading.value = false; });
            }
            function schedulePoll() {
                if (pollTimer) { clearTimeout(pollTimer); pollTimer = null; }
                if (tasks.value.some(function (t) { return t.running; })) {
                    pollTimer = setTimeout(function () { load(false); }, 1500);
                }
            }
            function toggle(task, enabled) {
                if (!canRun.value) { El.ElMessage.warning('当前账号没有启停权限'); return; }
                var action = enabled ? '启用' : '停用';
                var message = enabled
                    ? '启用「' + task.description + '」的自动触发？下一轮调度开始生效。'
                    : '停用「' + task.description + '」的自动触发？手动执行仍可用于排障。';
                El.ElMessageBox.confirm(message, action + '定时任务', { type: enabled ? 'info' : 'warning' })
                    .then(function () {
                        task.toggling = true;
                        return api.post('/api/admin/system/scheduled-tasks/toggle?key=' + encodeURIComponent(task.key) + '&enabled=' + (enabled ? 'true' : 'false'));
                    })
                    .then(function () {
                        task.enabled = enabled;
                        El.ElMessage.success(action + '成功');
                    })
                    .catch(function (err) {
                        if (err !== 'cancel' && err !== 'close') El.ElMessage.error((err && err.message) || (action + '失败'));
                        load(false);
                    })
                    .finally(function () { task.toggling = false; });
            }

            function run(task) {
                if (!canRun.value) { El.ElMessage.warning('当前账号没有手动执行权限'); return; }
                El.ElMessageBox.confirm('手动执行「' + task.description + '」一次？任务将异步执行，结果稍后刷新可见。',
                    '执行定时任务', { type: 'info' })
                    .then(function () { return api.post('/api/admin/system/scheduled-tasks/run?key=' + encodeURIComponent(task.key)); })
                    .then(function () {
                        El.ElMessage.success('已触发执行，正在刷新结果');
                        load(false);
                    })
                    .catch(function (err) {
                        if (err !== 'cancel' && err !== 'close') El.ElMessage.error((err && err.message) || '触发失败');
                    });
            }

            load();
            return {
                tasks: tasks, loading: loading, error: error, canRun: canRun,
                runningCount: runningCount, successCount: successCount, failedCount: failedCount,
                enabledCount: enabledCount, disabledCount: disabledCount,
                load: load, run: run, toggle: toggle, tone: tone, statusText: statusText
            };
        }
    };
})();
