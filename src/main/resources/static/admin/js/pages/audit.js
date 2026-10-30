/* 审计日志：配置、权限、用户、广告审核等关键操作留痕，支持筛选、JSON 差异查看与当前页导出。
   动作/资源标签统一走 ACTION_LABEL / RESOURCE_LABEL；未收录的编码按 snake -> 空格兜底，
   保证新增接口（如广告审核）无需改前端也能读懂。筛选支持下拉选择（可输入自定义编码）。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var ACTION_LABEL = {
        // 登录事件（SYS10）
        LOGIN_SUCCESS: '登录成功',
        LOGIN_FAILED: '登录失败',
        // 系统配置（SystemConfigServiceImpl 手写记录）
        CONFIG_UPDATE: '配置变更',
        ADMIN_UPDATE_SYSTEM_CONFIG: '更新系统配置',
        // 用户 / 角色 / 组织 / 校区
        ADMIN_UPDATE_USER_STATUS: '更新用户状态',
        ADMIN_UPDATE_USER_ROLES: '更新用户角色',
        ADMIN_UPDATE_USER_ROLE_BINDINGS: '更新用户角色绑定',
        ADMIN_REVIEW_USER_AUTHORIZATION: '审核用户认证',
        ADMIN_UPDATE_USER_OFFICIAL: '更新官方身份',
        ADMIN_UPDATE_ROLE: '更新角色',
        ADMIN_UPDATE_ROLE_PERMISSIONS: '更新角色权限',
        ADMIN_CREATE_CAMPUS: '新建校区',
        ADMIN_UPDATE_CAMPUS: '更新校区',
        ADMIN_CREATE_ORGANIZATION: '新建组织',
        ADMIN_UPDATE_ORGANIZATION: '更新组织',
        // 社区内容与治理
        ADMIN_COMMUNITY_CREATE_SECTION: '新建社区分区',
        ADMIN_COMMUNITY_UPDATE_SECTION: '更新社区分区',
        ADMIN_COMMUNITY_DELETE_SECTION: '删除社区分区',
        ADMIN_COMMUNITY_CREATE_TOPIC: '新建话题',
        ADMIN_COMMUNITY_UPDATE_TOPIC: '更新话题',
        ADMIN_COMMUNITY_DELETE_TOPIC: '删除话题',
        ADMIN_COMMUNITY_CREATE_BANNED_WORD: '新增屏蔽词',
        ADMIN_COMMUNITY_UPDATE_BANNED_WORD: '更新屏蔽词',
        ADMIN_COMMUNITY_DELETE_BANNED_WORD: '删除屏蔽词',
        ADMIN_COMMUNITY_IMPORT_BANNED_WORDS: '导入屏蔽词',
        ADMIN_COMMUNITY_EXPORT_BANNED_WORDS: '导出屏蔽词',
        ADMIN_COMMUNITY_HANDLE_APPEAL: '处理社区申诉',
        HANDLE_ADMIN_COMMUNITY_REPORT: '处理举报',
        HIDE_ADMIN_COMMUNITY_CONTENT: '下架社区内容',
        RESTORE_ADMIN_COMMUNITY_CONTENT: '恢复社区内容',
        SANCTION_ADMIN_COMMUNITY_USER: '处罚社区用户',
        REVOKE_ADMIN_COMMUNITY_SANCTION: '撤销社区处罚',
        // 官方账号 / 官方抓取
        ADMIN_CREATE_OFFICIAL_ACCOUNT: '新建官方账号',
        ADMIN_UPDATE_OFFICIAL_ACCOUNT: '更新官方账号',
        ADMIN_UPDATE_OFFICIAL_ACCOUNT_STATUS: '启停官方账号',
        CREATE_OFFICIAL_FEED_SOURCE: '新建抓取源',
        UPDATE_OFFICIAL_FEED_SOURCE: '更新抓取源',
        SYNC_OFFICIAL_FEED_SOURCE: '同步抓取源',
        SYNC_ALL_OFFICIAL_FEEDS: '全量同步抓取源',
        // 官方直投广告（当前后端 operationId）
        ADMIN_CREATE_DIRECT_AD: '新建直投广告',
        ADMIN_UPDATE_DIRECT_AD: '更新直投广告',
        ADMIN_UPDATE_DIRECT_AD_STATUS: '广告上下架',
        ADMIN_SUBMIT_DIRECT_AD_REVIEW: '提交广告审核',
        ADMIN_REVIEW_DIRECT_AD: '审核直投广告',
        ADMIN_DELETE_DIRECT_AD: '删除直投广告',
        // 兼容契约/历史命名（ADMIN_*_COMMUNITY_*）
        ADMIN_CREATE_COMMUNITY_DIRECT_AD: '新建直投广告',
        ADMIN_UPDATE_COMMUNITY_DIRECT_AD: '更新直投广告',
        ADMIN_UPDATE_COMMUNITY_DIRECT_AD_STATUS: '广告上下架',
        ADMIN_SUBMIT_COMMUNITY_DIRECT_AD: '提交广告审核',
        ADMIN_REVIEW_COMMUNITY_DIRECT_AD: '审核直投广告',
        ADMIN_DELETE_COMMUNITY_DIRECT_AD: '删除直投广告',
        // 广告主
        ADMIN_CREATE_COMMUNITY_ADVERTISER: '新建广告主',
        ADMIN_UPDATE_COMMUNITY_ADVERTISER: '更新广告主',
        ADMIN_UPDATE_COMMUNITY_ADVERTISER_STATUS: '启停广告主',
        ADMIN_DELETE_COMMUNITY_ADVERTISER: '删除广告主',
        // 通知 / 版本 / 作业 / 服务
        ADMIN_CREATE_NOTICE: '新建通知',
        ADMIN_UPDATE_NOTICE: '更新通知',
        ADMIN_PUBLISH_NOTICE: '发布通知',
        ADMIN_OFFLINE_NOTICE: '下线通知',
        ADMIN_CREATE_APP_VERSION: '新建版本',
        ADMIN_UPDATE_APP_VERSION: '更新版本',
        ADMIN_PUBLISH_APP_VERSION: '发布版本',
        ADMIN_OFFLINE_APP_VERSION: '下线版本',
        CREATE_HOMEWORK: '新建作业',
        REVIEW_ADMIN_SERVICE_REQUEST: '审核服务申请',
        REPLY_ADMIN_EVALUATION: '回复服务评价'
    };
    var RESOURCE_LABEL = {
        SYSTEM_CONFIG: '系统配置',
        USERS: '用户',
        SYS_USER: '用户',
        ROLES: '角色',
        SYS_ROLE: '角色',
        ORGANIZATIONS: '组织',
        SYS_ORG: '组织',
        CAMPUSES: '校区',
        DICTS: '字典',
        COMMUNITY_DIRECT_ADS: '直投广告',
        COMMUNITY_ADVERTISERS: '广告主',
        COMMUNITY_SECTIONS: '社区分区',
        COMMUNITY_TOPICS: '社区话题',
        COMMUNITY_BANNED_WORDS: '社区屏蔽词',
        COMMUNITY_REPORTS: '社区举报',
        COMMUNITY_OFFICIAL_ACCOUNTS: '官方账号',
        COMMUNITY_APPEALS: '社区申诉',
        OFFICIAL_FEED: '官方抓取',
        NOTICES: '通知公告',
        APP_VERSIONS: '版本发布',
        HOMEWORK: '作业',
        ACHIEVEMENTS: '成就',
        EVALUATIONS: '服务评价',
        SERVICE_REQUESTS: '服务申请',
        WORK_ORDERS: '工单',
        SYSTEM_FEATURE_FLAGS: '功能开关',
        SYSTEM_INTEGRATIONS: '外部集成',
        AUTH: '认证'
    };

    function humanize(code) {
        if (!code) return '';
        return String(code).replace(/_/g, ' ').replace(/\s+/g, ' ').trim();
    }
    function actionLabel(code) {
        if (!code) return '—';
        return ACTION_LABEL[code] || humanize(code);
    }
    function resourceLabel(code) {
        if (!code) return '—';
        return RESOURCE_LABEL[code] || humanize(code);
    }
    function actionTone(code) {
        if (!code) return 'info';
        if (code === 'CONFIG_UPDATE') return 'warn';
        if (code === 'LOGIN_FAILED') return 'bad';
        if (code === 'LOGIN_SUCCESS') return 'ok';
        if (code.indexOf('DELETE') >= 0) return 'bad';
        if (code.indexOf('REVIEW') >= 0 || code.indexOf('SUBMIT') >= 0 || code.indexOf('PUBLISH') >= 0) return 'ok';
        if (code.indexOf('CREATE') >= 0) return 'brand';
        if (code.indexOf('STATUS') >= 0 || code.indexOf('OFFLINE') >= 0) return 'warn';
        return 'info';
    }

    window.AdminPages.AuditLogsPage = {
        name: 'AuditLogsPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>审计日志</h1><div class="sub">关键操作留痕与配置变更追溯</div></div>',
            '    <div class="r"><button class="btn2" :disabled="!rows.length" @click="exportCurrent">导出当前页</button><button class="btn2" style="margin-left:8px" @click="exportAll">导出全量 CSV</button><button class="btn2" style="margin-left:8px" @click="load">刷新</button></div></div>',

            '  <div class="os-grid" style="grid-template-columns:repeat(5,1fr)">',
            '    <div class="os-kpi"><div class="k-label"><span class="dot"></span>筛选结果</div><div class="k-val">{{ total }}<small>条</small></div><div class="k-foot"><span>审计记录总数</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-info)"></span>本页操作人</div><div class="k-val">{{ operatorCount }}</div><div class="k-foot"><span>去重后的操作账号</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-brand)"></span>关键写操作</div><div class="k-val">{{ criticalWrites }}</div><div class="k-foot"><span>本页非配置变更/登录事件</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-bad)"></span>失败写操作</div><div class="k-val" :class="{ bad: failedWrites > 0 }">{{ failedWrites }}</div><div class="k-foot"><span>本页 resultStatus=FAILED</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>最近操作</div><div class="k-val" style="font-size:18px">{{ latestTime }}</div><div class="k-foot"><span>{{ latestOperator }}</span></div></div>',
            '  </div>',

            '  <div class="os-panel">',
            '    <div class="os-panel-h" style="flex-wrap:wrap;row-gap:10px">',
            '      <h3>操作记录</h3><span class="hint">按时间倒序；详情包含变更前后 JSON</span>',
            '      <div class="r" style="flex-wrap:wrap">',
            '        <el-select v-model="filters.resourceType" filterable allow-create clearable default-first-option placeholder="全部资源" style="width:190px" title="资源类型：可选择或输入编码">',
            '          <el-option v-for="r in resourceOptions" :key="r" :label="resourceLabel(r)" :value="r"><span>{{ resourceLabel(r) }}</span><span style="float:right;color:var(--tk-muted);font-size:10px;margin-left:12px">{{ r }}</span></el-option>',
            '        </el-select>',
            '        <el-select v-model="filters.action" filterable allow-create clearable default-first-option placeholder="全部动作" style="width:210px" title="动作：可选择或输入编码">',
            '          <el-option v-for="a in actionOptions" :key="a" :label="actionLabel(a)" :value="a"><span>{{ actionLabel(a) }}</span><span style="float:right;color:var(--tk-muted);font-size:10px;margin-left:12px">{{ a }}</span></el-option>',
            '        </el-select>',
            '        <el-select v-model="filters.resultStatus" clearable placeholder="全部结果" style="width:140px">',
            '          <el-option label="成功" value="SUCCESS"></el-option><el-option label="失败" value="FAILED"></el-option>',
            '        </el-select>',
            '        <input class="os-input" type="datetime-local" style="width:196px" v-model="filters.startAt" title="开始时间" />',
            '        <input class="os-input" type="datetime-local" style="width:196px" v-model="filters.endAt" title="结束时间" />',
            '        <button class="os-btn primary" @click="search">查询</button>',
            '        <button class="os-btn ghost" @click="reset">重置</button>',
            '      </div>',
            '    </div>',
            '    <div class="os-note" style="margin:14px 18px 0"><b>口径</b> 「关键写操作」= 本页除配置变更、登录事件外的写操作数量（用户/角色/校区/社区/广告审核等）；「配置变更」只统计配置中心的 CONFIG_UPDATE。筛选下拉已内置常见动作与资源编码，也可直接输入编码过滤。</div>',
            '    <div style="overflow:auto;min-height:300px">',
            '      <table style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr>',
            '          <th style="width:72px">ID</th><th style="width:160px">操作时间</th><th style="width:150px">操作人</th><th style="width:150px">动作</th><th style="width:150px">资源</th><th>资源 ID</th><th style="width:96px">结果</th><th style="width:130px">IP</th><th style="width:62px"></th>',
            '        </tr></thead>',
            '        <tbody>',
            '          <tr v-for="r in rows" :key="r.id" style="cursor:pointer" @click="openDetail(r)">',
            '            <td class="os-mono" style="color:var(--tk-muted)">#{{ r.id }}</td>',
            '            <td style="color:var(--tk-text3)">{{ fmt(r.createdAt) }}</td>',
            '            <td><div style="display:flex;align-items:center;gap:8px"><span style="width:24px;height:24px;border-radius:7px;background:var(--tk-subtle);display:grid;place-items:center;font-size:10px;color:var(--tk-text2)">{{ (r.operatorName || "?").slice(0,1) }}</span><b style="font-weight:550;color:var(--tk-text)">{{ r.operatorName || "—" }}</b></div></td>',
            '            <td><span class="os-tag" :class="actionTone(r.action)" :title="r.action">{{ actionLabel(r.action) }}</span><div class="os-mono" style="font-size:10px;color:var(--tk-muted);margin-top:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ r.action }}</div></td>',
            '            <td><div style="font-size:12px;color:var(--tk-text2)">{{ resourceLabel(r.resourceType) }}</div><div class="os-mono" style="font-size:10px;color:var(--tk-muted);margin-top:2px">{{ r.resourceType }}</div></td>',
            '            <td class="os-mono" style="color:var(--tk-text3)">{{ r.resourceId || "—" }}</td>',
            '            <td><span class="os-tag" :class="r.resultStatus === \'FAILED\' ? \'bad\' : \'ok\'" :title="r.errorMessage || \'\'">{{ r.resultStatus === "FAILED" ? "失败" : "成功" }}</span>',
            '              <div v-if="r.errorMessage" style="font-size:10px;color:var(--tk-bad);margin-top:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ r.errorMessage }}</div></td>',
            '            <td class="os-mono" style="color:var(--tk-muted)">{{ r.ip || "—" }}</td>',
            '            <td style="text-align:right"><span style="color:var(--tk-brand);font-size:11px">详情</span></td>',
            '          </tr>',
            '          <tr v-if="!rows.length"><td colspan="9" class="os-empty">没有匹配的审计记录</td></tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',
            '    <div style="display:flex;align-items:center;gap:10px;padding:11px 18px;border-top:1px solid var(--tk-stroke)">',
            '      <span style="font-size:11px;color:var(--tk-text3)">共 {{ total }} 条 · 第 {{ pageNum }} / {{ totalPages }} 页</span>',
            '      <span style="flex:1"></span>',
            '      <button class="os-btn sm" :disabled="pageNum <= 1" @click="go(pageNum - 1)">上一页</button>',
            '      <button class="os-btn sm" :disabled="pageNum >= totalPages" @click="go(pageNum + 1)">下一页</button>',
            '    </div>',
            '  </div>',

            '  <div v-if="detail" class="cmd-mask" style="padding-top:8vh" @click.self="detail = null">',
            '    <div class="cmd-panel" style="width:720px;max-height:78vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">审计详情 #{{ detail.id }}</b><span style="flex:1"></span><span class="os-tag" :class="actionTone(detail.action)" :title="detail.action">{{ actionLabel(detail.action) }}</span><span style="cursor:pointer;color:var(--tk-muted);margin-left:10px" @click="detail = null">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:16px">',
            '        <div style="display:grid;grid-template-columns:repeat(2,1fr);gap:10px 24px">',
            '          <div class="os-field"><label>操作人</label><div style="font-size:12px;color:var(--tk-text2)">{{ detail.operatorName || "—" }} <span class="os-mono" style="color:var(--tk-muted)">#{{ detail.operatorId }}</span></div></div>',
            '          <div class="os-field"><label>操作时间</label><div style="font-size:12px;color:var(--tk-text2)">{{ fmt(detail.createdAt) }}</div></div>',
            '          <div class="os-field"><label>动作</label><div style="font-size:12px;color:var(--tk-text2)">{{ actionLabel(detail.action) }} <span class="os-mono" style="color:var(--tk-muted)">{{ detail.action }}</span></div></div>',
            '          <div class="os-field"><label>资源</label><div style="font-size:12px;color:var(--tk-text2)">{{ resourceLabel(detail.resourceType) }} <span class="os-mono" style="color:var(--tk-muted)">{{ detail.resourceType }} · {{ detail.resourceId || "—" }}</span></div></div>',
            '          <div class="os-field"><label>IP / traceId</label><div style="font-size:12px;color:var(--tk-text2)">{{ detail.ip || "—" }} · <span class="os-mono">{{ detail.traceId || "—" }}</span></div></div>',
            '          <div class="os-field"><label>结果</label><div style="font-size:12px;color:var(--tk-text2)"><span class="os-tag" :class="detail.resultStatus === \'FAILED\' ? \'bad\' : \'ok\'">{{ detail.resultStatus === "FAILED" ? "失败" : "成功" }}</span></div></div>',
            '          <div class="os-field" v-if="detail.errorMessage"><label>失败原因</label><div style="font-size:12px;color:var(--tk-bad)">{{ detail.errorMessage }}</div></div>',
            '        </div>',
            '        <div class="os-split">',
            '          <div><div class="os-field" style="margin-bottom:6px"><label>变更前</label></div><pre class="audit-json">{{ pretty(detail.beforeJson) }}</pre></div>',
            '          <div><div class="os-field" style="margin-bottom:6px"><label>变更后</label></div><pre class="audit-json">{{ pretty(detail.afterJson) }}</pre></div>',
            '        </div>',
            '        <div style="font-size:11px;color:var(--tk-muted);line-height:1.6">User-Agent：{{ detail.userAgent || "—" }}</div>',
            '      </div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var rows = Vue.ref([]);
            var total = Vue.ref(0);
            var pageNum = Vue.ref(1);
            var pageSize = 20;
            var totalPages = Vue.computed(function () { return Math.max(1, Math.ceil(total.value / pageSize)); });
            var detail = Vue.ref(null);
            var filters = Vue.reactive({ action: '', resourceType: '', resultStatus: '', startAt: '', endAt: '' });

            var actionOptions = Vue.computed(function () {
                var seen = {};
                Object.keys(ACTION_LABEL).forEach(function (k) { seen[k] = 1; });
                rows.value.forEach(function (r) { if (r.action) seen[r.action] = 1; });
                if (filters.action) seen[filters.action] = 1;
                return Object.keys(seen).sort();
            });
            var resourceOptions = Vue.computed(function () {
                var seen = {};
                Object.keys(RESOURCE_LABEL).forEach(function (k) { seen[k] = 1; });
                rows.value.forEach(function (r) { if (r.resourceType) seen[r.resourceType] = 1; });
                if (filters.resourceType) seen[filters.resourceType] = 1;
                return Object.keys(seen).sort();
            });

            function fmt(v) {
                if (!v) return '—';
                return String(v).replace('T', ' ').slice(0, 19);
            }
            function pretty(v) {
                if (!v) return '（无）';
                try { return JSON.stringify(JSON.parse(v), null, 2); } catch (e) { return v; }
            }
            function load() {
                var params = new URLSearchParams();
                params.set('pageNum', String(pageNum.value));
                params.set('pageSize', String(pageSize));
                if (filters.action) params.set('action', filters.action);
                if (filters.resourceType) params.set('resourceType', filters.resourceType);
                if (filters.resultStatus) params.set('resultStatus', filters.resultStatus);
                if (filters.startAt) params.set('startAt', filters.startAt + ':00');
                if (filters.endAt) params.set('endAt', filters.endAt + ':00');
                api.get('/api/admin/audit-logs?' + params.toString())
                    .then(function (data) {
                        rows.value = (data && data.records) || [];
                        total.value = (data && data.total) || 0;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '审计日志加载失败'); });
            }
            function search() { pageNum.value = 1; load(); }
            function reset() {
                filters.action = ''; filters.resourceType = ''; filters.resultStatus = '';
                filters.startAt = ''; filters.endAt = '';
                search();
            }
            function buildExportParams() {
                var params = new URLSearchParams();
                if (filters.action) params.set('action', filters.action);
                if (filters.resourceType) params.set('resourceType', filters.resourceType);
                if (filters.resultStatus) params.set('resultStatus', filters.resultStatus);
                if (filters.startAt) params.set('startAt', filters.startAt + ':00');
                if (filters.endAt) params.set('endAt', filters.endAt + ':00');
                return params.toString();
            }
            function exportAll() {
                var qs = buildExportParams();
                fetch('/api/admin/audit-logs/export' + (qs ? '?' + qs : ''), {
                    headers: { Authorization: 'Bearer ' + api.getToken() }
                }).then(function (res) {
                    if (!res.ok) throw new Error('导出失败（HTTP ' + res.status + '）');
                    return res.blob();
                }).then(function (blob) {
                    var url = URL.createObjectURL(blob);
                    var a = document.createElement('a');
                    a.href = url;
                    a.download = 'audit-logs-' + new Date().toISOString().slice(0, 10) + '.csv';
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                    URL.revokeObjectURL(url);
                    El.ElMessage.success('已导出全量审计日志（最多 20000 行）');
                }).catch(function (error) { El.ElMessage.error(error.message || '导出失败'); });
            }
            function go(p) {
                if (p < 1 || p > totalPages.value) return;
                pageNum.value = p; load();
            }
            function openDetail(r) { detail.value = r; }
            function exportCurrent() {
                var header = ['ID', '操作时间', '操作人', '动作', '动作编码', '资源', '资源编码', '资源ID', 'IP', 'traceId'];
                var lines = [header.join(',')];
                rows.value.forEach(function (r) {
                    lines.push([r.id, fmt(r.createdAt), r.operatorName || '', actionLabel(r.action), r.action,
                        resourceLabel(r.resourceType), r.resourceType, r.resourceId || '', r.ip || '', r.traceId || ''].map(function (v) {
                        return '"' + String(v).replace(/"/g, '""') + '"';
                    }).join(','));
                });
                var blob = new Blob(['\ufeff' + lines.join('\n')], { type: 'text/csv;charset=utf-8' });
                var a = document.createElement('a');
                a.href = URL.createObjectURL(blob);
                a.download = 'audit-logs-page-' + pageNum.value + '.csv';
                a.click();
                URL.revokeObjectURL(a.href);
            }

            var operatorCount = Vue.computed(function () {
                var seen = {};
                rows.value.forEach(function (r) { if (r.operatorName) seen[r.operatorName] = 1; });
                return Object.keys(seen).length;
            });
            var failedWrites = Vue.computed(function () {
                return rows.value.filter(function (r) { return r.resultStatus === 'FAILED'; }).length;
            });
            var criticalWrites = Vue.computed(function () {
                return rows.value.filter(function (r) {
                    var a = String(r.action || '');
                    return a !== 'CONFIG_UPDATE' && a.indexOf('LOGIN_') !== 0;
                }).length;
            });
            var latestTime = Vue.computed(function () { return rows.value.length ? fmt(rows.value[0].createdAt).slice(5, 16) : '—'; });
            var latestOperator = Vue.computed(function () { return rows.value.length ? (rows.value[0].operatorName || '—') : '—'; });

            Vue.onMounted(load);
            return {
                rows: rows, total: total, pageNum: pageNum, totalPages: totalPages, filters: filters,
                actionOptions: actionOptions, resourceOptions: resourceOptions,
                load: load, search: search, reset: reset, go: go, fmt: fmt, pretty: pretty,
                detail: detail, openDetail: openDetail, exportCurrent: exportCurrent, exportAll: exportAll,
                operatorCount: operatorCount, failedWrites: failedWrites, criticalWrites: criticalWrites,
                latestTime: latestTime, latestOperator: latestOperator,
                actionLabel: actionLabel, resourceLabel: resourceLabel, actionTone: actionTone
            };
        }
    };
})();
