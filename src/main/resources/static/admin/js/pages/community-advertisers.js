/* 社区广告主：广告主主体（名称/联系人/电话/状态）的维护与启停。
   广告创建时通过 advertiserId 关联广告主；广告主停用不影响历史广告数据，只影响新建广告时的可选范围。
   接口：/api/admin/community/advertisers（按管理端校区上下文隔离）。
   权限：community:advertiser:manage 或 platform:all；无权限时只读，不渲染写操作。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var STATUS = {
        ENABLED: { label: '启用中', tone: 'ok' },
        DISABLED: { label: '已停用', tone: 'muted' }
    };

    function blank() {
        return {
            advertiserId: null,
            name: '',
            contactName: '',
            contactPhone: '',
            status: 'ENABLED',
            remark: ''
        };
    }

    function currentPermissions() {
        var user = (window.AdminApp && window.AdminApp.state && window.AdminApp.state.user) || {};
        var perms = (user.permissions || []).slice();
        var roles = user.roles || [];
        if (roles.indexOf('PLATFORM_ADMIN') >= 0 && perms.indexOf('platform:all') < 0) {
            perms.push('platform:all');
        }
        return perms;
    }

    window.AdminPages.CommunityAdvertisersPage = {
        name: 'CommunityAdvertisersPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>社区广告主</h1><div class="sub">广告投放主体、联系人与启停治理</div></div>',
            '    <div class="r"><button class="btn2" @click="load">刷新</button><button v-if="canManage" class="btn2 pri" @click="openCreate">新建广告主</button></div></div>',

            '  <div class="os-grid" style="grid-template-columns:repeat(4,1fr)">',
            '    <div class="os-kpi"><div class="k-label"><span class="dot"></span>广告主总数</div><div class="k-val">{{ allTotal }}<small>个</small></div><div class="k-foot"><span>当前校区全部广告主</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>启用中</div><div class="k-val">{{ enabledTotal }}<small>个</small></div><div class="k-foot"><span>可被新建广告关联</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-muted)"></span>已停用</div><div class="k-val">{{ disabledTotal }}<small>个</small></div><div class="k-foot"><span>历史广告仍保留关联</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-info)"></span>本页联系方式完整</div><div class="k-val">{{ contactReady }}<small>个</small></div><div class="k-foot"><span>本页同时填写联系人与电话</span></div></div>',
            '  </div>',

            '  <div class="os-note"><b>广告主边界</b> 广告主是投放主体（商户/部门），一个广告主可关联多条广告；停用广告主只是禁止继续新建关联广告，历史广告的投放与统计不受影响。删除为逻辑删除，已关联广告保留历史 advertiserId。</div>',
            '  <div v-if="!canManage" class="os-note warn"><b>只读权限</b> 当前账号缺少 community:advertiser:manage / platform:all，仅可查看广告主列表，不能新建、编辑、启停或删除。</div>',

            '  <div class="os-panel" style="flex:1;min-height:0;display:flex;flex-direction:column">',
            '    <div class="os-panel-h" style="flex-wrap:wrap;row-gap:8px">',
            '      <h3>广告主列表</h3><span class="hint">按创建时间倒序 · 与广告同校区</span>',
            '      <div class="r" style="flex-wrap:wrap">',
            '        <select class="os-select" style="width:116px" v-model="statusFilter" @change="pageNum = 1; load()"><option value="">全部状态</option><option value="ENABLED">启用中</option><option value="DISABLED">已停用</option></select>',
            '        <div class="srch" style="width:230px;background:var(--tk-input)"><el-icon :size="13"><Search /></el-icon><input v-model.trim="keyword" placeholder="搜索名称 / 联系人 / 电话" @keyup.enter="pageNum = 1; load()" /></div>',
            '      </div>',
            '    </div>',
            '    <div style="overflow:auto;flex:1">',
            '      <table style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr><th style="width:30%">广告主</th><th style="width:140px">联系人</th><th style="width:150px">联系电话</th><th style="width:92px">状态</th><th>备注</th><th style="width:128px">创建时间</th><th style="width:170px;text-align:right">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-for="a in rows" :key="a.advertiserId">',
            '            <td><div style="display:flex;align-items:center;gap:8px;min-width:0">',
            '              <span style="width:30px;height:30px;border-radius:8px;background:var(--tk-subtle);display:grid;place-items:center;font-size:11px;color:var(--tk-brand);flex:none">{{ (a.name || "?").slice(0,1) }}</span>',
            '              <div style="min-width:0"><b style="font-weight:550;color:var(--tk-text);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;display:block">{{ a.name }}</b>',
            '                <div style="font-size:10px;color:var(--tk-muted);margin-top:3px"><span class="os-mono">#{{ a.advertiserId }}</span><span v-if="a.campusId" style="margin-left:7px">校区 {{ a.campusId }}</span></div></div></div></td>',
            '            <td style="font-size:12px;color:var(--tk-text2)">{{ a.contactName || "—" }}</td>',
            '            <td><span class="os-mono" style="font-size:11px;color:var(--tk-text2)">{{ a.contactPhone || "—" }}</span></td>',
            '            <td><span class="os-tag" :class="STATUS[a.status] ? STATUS[a.status].tone : \'muted\'">{{ STATUS[a.status] ? STATUS[a.status].label : a.status }}</span></td>',
            '            <td style="font-size:11px;color:var(--tk-text3);overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="a.remark || \'\'">{{ a.remark || "—" }}</td>',
            '            <td style="font-size:11px;color:var(--tk-text3)">{{ fmt(a.createdAt) }}</td>',
            '            <td style="text-align:right;white-space:nowrap">',
            '              <template v-if="canManage">',
            '                <span style="font-size:11px;color:var(--tk-info);cursor:pointer" @click="openEdit(a)">编辑</span>',
            '                <span style="font-size:11px;margin-left:9px;cursor:pointer" :style="{ color: a.status === \'ENABLED\' ? \'var(--tk-warn)\' : \'var(--tk-ok)\' }" @click="toggle(a)">{{ a.status === "ENABLED" ? "停用" : "启用" }}</span>',
            '                <span style="font-size:11px;color:var(--tk-bad);cursor:pointer;margin-left:9px" @click="remove(a)">删除</span>',
            '              </template>',
            '              <span v-else style="font-size:11px;color:var(--tk-muted)">只读</span>',
            '            </td>',
            '          </tr>',
            '          <tr v-if="!rows.length"><td colspan="7" class="os-empty">没有匹配的广告主，{{ canManage ? "点击右上角「新建广告主」开始维护" : "请联系管理员创建" }}</td></tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',
            '    <div style="display:flex;align-items:center;gap:10px;padding:11px 18px;border-top:1px solid var(--tk-stroke)">',
            '      <span style="font-size:11px;color:var(--tk-text3)">共 {{ total }} 个广告主 · 第 {{ pageNum }} / {{ totalPages }} 页</span><span style="flex:1"></span>',
            '      <button class="os-btn sm" :disabled="pageNum <= 1" @click="pageNum--; load()">上一页</button>',
            '      <button class="os-btn sm" :disabled="pageNum >= totalPages" @click="pageNum++; load()">下一页</button>',
            '    </div>',
            '  </div>',

            '  <div v-if="editorOpen" class="cmd-mask" style="padding-top:6vh" @click.self="editorOpen = false">',
            '    <div class="cmd-panel" style="width:560px;max-height:86vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">{{ form.advertiserId ? "编辑广告主" : "新建广告主" }}</b><span style="flex:1"></span><span class="os-tag info">社区广告主</span><span style="cursor:pointer;color:var(--tk-muted);margin-left:10px" @click="editorOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>广告主名称 <em>*</em></label><input class="os-input" v-model.trim="form.name" maxlength="100" placeholder="例如：校园便利服务中心" /></div>',
            '          <div class="os-field"><label>状态</label><select class="os-select" v-model="form.status"><option value="ENABLED">启用中</option><option value="DISABLED">已停用</option></select></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>联系人</label><input class="os-input" v-model.trim="form.contactName" maxlength="50" placeholder="对接人姓名" /></div>',
            '          <div class="os-field"><label>联系电话</label><input class="os-input" v-model.trim="form.contactPhone" maxlength="30" placeholder="手机号或固定电话" /></div>',
            '        </div>',
            '        <div class="os-field"><label>备注</label><textarea class="os-textarea" v-model.trim="form.remark" maxlength="500" placeholder="合作范围、结算方式或内部说明（不在客户端展示）"></textarea></div>',
            '        <div class="os-note"><b>提示</b> 广告主停用后不可再被新建广告关联；编辑广告主不会修改已关联广告的标题与投放内容。</div>',
            '        <div style="display:flex;justify-content:flex-end;gap:8px"><button class="os-btn" @click="editorOpen = false">取消</button><button class="os-btn primary" :disabled="submitting" @click="submit">{{ submitting ? "提交中…" : "保存广告主" }}</button></div>',
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
            var pageSize = 12;
            var totalPages = Vue.computed(function () { return Math.max(1, Math.ceil(total.value / pageSize)); });
            var statusFilter = Vue.ref('');
            var keyword = Vue.ref('');
            var allTotal = Vue.ref(0);
            var enabledTotal = Vue.ref(0);
            var disabledTotal = Vue.ref(0);

            var editorOpen = Vue.ref(false);
            var submitting = Vue.ref(false);
            var form = Vue.reactive(blank());

            var canManage = Vue.computed(function () {
                var perms = currentPermissions();
                return perms.indexOf('platform:all') >= 0 || perms.indexOf('community:advertiser:manage') >= 0;
            });
            var contactReady = Vue.computed(function () {
                return rows.value.filter(function (a) { return a.contactName && a.contactPhone; }).length;
            });

            function fmt(v) { return v ? String(v).replace('T', ' ').slice(0, 16) : '—'; }

            function countOf(status) {
                var p = new URLSearchParams();
                p.set('pageNum', '1'); p.set('pageSize', '1');
                if (status) p.set('status', status);
                return api.get('/api/admin/community/advertisers?' + p.toString())
                    .then(function (data) { return (data && data.total) || 0; })
                    .catch(function () { return null; });
            }

            function load() {
                var p = new URLSearchParams();
                p.set('pageNum', String(pageNum.value)); p.set('pageSize', String(pageSize));
                if (statusFilter.value) p.set('status', statusFilter.value);
                if (keyword.value) p.set('keyword', keyword.value);
                api.get('/api/admin/community/advertisers?' + p.toString())
                    .then(function (data) {
                        rows.value = (data && data.records) || [];
                        total.value = (data && data.total) || 0;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '广告主列表加载失败'); });
                Promise.all([countOf(''), countOf('ENABLED'), countOf('DISABLED')]).then(function (counts) {
                    if (counts[0] != null) allTotal.value = counts[0];
                    if (counts[1] != null) enabledTotal.value = counts[1];
                    if (counts[2] != null) disabledTotal.value = counts[2];
                });
            }

            function openCreate() {
                if (!canManage.value) { El.ElMessage.warning('当前账号没有广告主管理权限'); return; }
                Object.assign(form, blank());
                editorOpen.value = true;
            }
            function openEdit(a) {
                if (!canManage.value) return;
                Object.assign(form, blank());
                form.advertiserId = a.advertiserId;
                form.name = a.name || '';
                form.contactName = a.contactName || '';
                form.contactPhone = a.contactPhone || '';
                form.status = a.status || 'ENABLED';
                form.remark = a.remark || '';
                editorOpen.value = true;
            }
            function validPhone(phone) {
                if (!phone) return true;
                return /^[0-9+\-\s()#]{6,30}$/.test(phone);
            }
            function submit() {
                if (!canManage.value) { El.ElMessage.warning('当前账号没有广告主管理权限'); return; }
                if (!form.name) { El.ElMessage.warning('请填写广告主名称'); return; }
                if (!validPhone(form.contactPhone)) { El.ElMessage.warning('联系电话格式不正确（仅支持数字、+、-、空格，6-30 位）'); return; }
                var payload = {
                    name: form.name,
                    contactName: form.contactName || null,
                    contactPhone: form.contactPhone || null,
                    status: form.status,
                    remark: form.remark || null
                };
                submitting.value = true;
                var req = form.advertiserId
                    ? api.post('/api/admin/community/advertisers/' + form.advertiserId, payload)
                    : api.post('/api/admin/community/advertisers', payload);
                req.then(function () {
                    El.ElMessage.success(form.advertiserId ? '广告主已更新' : '广告主已创建');
                    editorOpen.value = false;
                    load();
                }).catch(function (error) { El.ElMessage.error(error.message || '保存失败'); })
                    .finally(function () { submitting.value = false; });
            }
            function toggle(a) {
                if (!canManage.value) return;
                var next = a.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
                var run = function () {
                    api.post('/api/admin/community/advertisers/' + a.advertiserId + '/status', { status: next })
                        .then(function () { El.ElMessage.success(next === 'ENABLED' ? '广告主已启用' : '广告主已停用'); load(); })
                        .catch(function (error) { El.ElMessage.error(error.message || '状态更新失败'); });
                };
                if (next === 'DISABLED') {
                    El.ElMessageBox.confirm('停用后该广告主不能再被新建广告关联，已投放广告不受影响。确定停用「' + a.name + '」？', '停用广告主', { type: 'warning' })
                        .then(run)
                        .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '操作失败'); });
                } else {
                    run();
                }
            }
            function remove(a) {
                if (!canManage.value) return;
                El.ElMessageBox.confirm('删除为逻辑删除；已关联广告保留历史数据，但将无法再按广告主筛选到该主体。确定删除「' + a.name + '」？', '删除广告主', { type: 'warning' })
                    .then(function () { return api.delete('/api/admin/community/advertisers/' + a.advertiserId); })
                    .then(function () { El.ElMessage.success('广告主已删除'); load(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '删除失败'); });
            }

            load();
            return {
                rows: rows, total: total, pageNum: pageNum, totalPages: totalPages, statusFilter: statusFilter,
                keyword: keyword, allTotal: allTotal, enabledTotal: enabledTotal, disabledTotal: disabledTotal,
                contactReady: contactReady, canManage: canManage, editorOpen: editorOpen, submitting: submitting,
                form: form, load: load, fmt: fmt, openCreate: openCreate, openEdit: openEdit, submit: submit,
                toggle: toggle, remove: remove, STATUS: STATUS
            };
        }
    };
})();
