/* 独立请假审批（H01）：真实接口 /api/admin/leave-requests。
   数据来自 service_request 中 item_code = AFFAIR-LEAVE 的申请；支持筛选、详情、通过/驳回/销假。
   销假语义：已通过（PROCESSING）→ 办结（COMPLETED），并记录 leave_cancel_at；不伪造新的申请状态。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var LEAVE_STATUS_TEXT = {
        PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已销假'
    };
    var LEAVE_STATUS_TONE = {
        PENDING: 'a', APPROVED: 'g', REJECTED: 'r', CANCELLED: 'n2'
    };
    var REQUEST_STATUS_TEXT = {
        SUBMITTED: '待审批', PROCESSING: '已通过', COMPLETED: '已办结',
        REJECTED: '已驳回', CANCELLED: '已撤销'
    };
    var TABS = [
        { value: '', label: '全部' },
        { value: 'PENDING', label: '待审批' },
        { value: 'APPROVED', label: '已通过' },
        { value: 'REJECTED', label: '已驳回' },
        { value: 'CANCELLED', label: '已销假' }
    ];
    var LEAVE_TYPES = ['病假', '事假', '其他'];
    var ACTION_TEXT = { APPROVE: '通过', REJECT: '驳回', CANCEL: '销假' };

    function fmt(v) {
        if (!v) return '—';
        return String(v).replace('T', ' ').slice(0, 16);
    }
    function timeRange(row) {
        if (!row.leaveStartAt && !row.leaveEndAt) return '—';
        var start = row.leaveStartAt ? fmt(row.leaveStartAt).slice(0, 10) : '?';
        var end = row.leaveEndAt ? fmt(row.leaveEndAt).slice(0, 10) : '?';
        return start + ' 至 ' + end;
    }

    window.AdminPages.LeaveApprovalPage = {
        name: 'LeaveApprovalPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:14px;flex:1;min-height:0">',
            '  <div class="page-h">',
            '    <div><h1>请假审批</h1><div class="sub">独立请假审批闭环 · 待审/通过/驳回/销假 · 数据来自服务申请中的请假事项</div></div>',
            '    <div class="r"><button class="btn2" @click="loadAll">刷新</button></div>',
            '  </div>',

            '  <div class="os-grid" style="grid-template-columns:repeat(4,1fr)">',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>待审批</div><div class="k-val">{{ stats.pending }}</div><div class="k-foot"><span>需辅导员/管理员处理</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>已通过</div><div class="k-val">{{ stats.approved }}</div><div class="k-foot"><span>请假生效中</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-bad)"></span>已驳回</div><div class="k-val">{{ stats.rejected }}</div><div class="k-foot"><span>含驳回原因</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-muted)"></span>已销假</div><div class="k-val">{{ stats.cancelled }}</div><div class="k-foot"><span>已办结并记录销假时间</span></div></div>',
            '  </div>',

            '  <div style="display:flex;align-items:center;gap:9px;flex-wrap:wrap">',
            '    <div class="tabs" style="margin:0">',
            '      <span v-for="t in tabs" :key="t.value" class="tab" :class="{ on: status === t.value }" @click="switchStatus(t.value)">{{ t.label }}</span>',
            '    </div>',
            '    <select class="os-select" style="width:120px" v-model="leaveType" @change="reload"><option value="">全部类型</option><option v-for="t in leaveTypes" :key="t" :value="t">{{ t }}</option></select>',
            '    <input class="os-input" type="date" style="width:140px" v-model="startDate" title="请假开始日期" />',
            '    <span style="font-size:11px;color:var(--tk-muted)">至</span>',
            '    <input class="os-input" type="date" style="width:140px" v-model="endDate" title="请假结束日期" />',
            '    <div class="srch" style="height:34px;width:220px;background:var(--tk-input)"><el-icon :size="13"><Search /></el-icon><input v-model.trim="keyword" placeholder="单号 / 标题 / 申请人" @keyup.enter="reload" /></div>',
            '    <button class="btn2 pri" @click="reload">查询</button>',
            '    <button class="btn2" @click="resetFilters">重置</button>',
            '  </div>',

            '  <div class="lcard" style="flex:1;min-height:0;display:flex;flex-direction:column">',
            '    <div class="cap">请假申请<span class="cnt" v-if="Number(total) > 0">共 {{ total }} 条</span><span class="cnt" v-if="!canManage" style="margin-left:8px">只读账号</span></div>',
            '    <div v-if="loading" style="padding:36px 0;text-align:center;color:var(--tk-muted);font-size:12px">加载中…</div>',
            '    <div v-else-if="error" style="padding:30px 0;text-align:center;color:var(--tk-muted);font-size:12px">{{ error }} <button class="os-btn sm" style="margin-left:8px" @click="loadAll">重试</button></div>',
            '    <div v-else class="tblw" style="flex:1;min-height:0;overflow:auto">',
            '      <table class="pk">',
            '        <colgroup><col style="width:142px"><col style="width:150px"><col style="width:78px"><col style="width:178px"><col style="width:62px"><col style="width:92px"><col style="width:136px"><col style="width:118px"></colgroup>',
            '        <thead><tr><th>申请单号</th><th>申请人 / 组织</th><th>类型</th><th>请假时间</th><th>天数</th><th>状态</th><th>提交 / 办结</th><th class="r">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-if="!rows.length"><td colspan="8" style="text-align:center;color:var(--tk-muted);padding:36px 0">当前筛选条件下没有请假申请</td></tr>',
            '          <tr v-for="row in rows" :key="row.requestId" style="cursor:pointer" @click="openDetail(row)">',
            '            <td style="color:var(--tk-muted)">{{ row.requestNo }}</td>',
            '            <td><div style="font-size:12px;color:var(--tk-text)">{{ row.requesterName || "—" }}</div><div style="font-size:10px;color:var(--tk-muted);margin-top:2px">{{ row.requesterOrganizationName || "未绑定组织" }}</div></td>',
            '            <td><span class="bdg n2">{{ row.leaveType || "—" }}</span></td>',
            '            <td style="font-size:11px;color:var(--tk-text3)">{{ timeRange(row) }}</td>',
            '            <td class="os-mono">{{ row.leaveDays == null ? "—" : row.leaveDays }}</td>',
            '            <td><span class="bdg" :class="leaveTone(row.leaveStatus)">{{ leaveStatusText(row.leaveStatus) }}</span></td>',
            '            <td style="font-size:10.5px;color:var(--tk-text3)">{{ fmt(row.submittedAt) }}<div v-if="row.completedAt" style="font-size:10px;color:var(--tk-ok);margin-top:2px">办结 {{ fmt(row.completedAt) }}</div></td>',
            '            <td class="r" @click.stop>',
            '              <span class="op" @click="openDetail(row)">详情</span>',
            '              <template v-if="canManage && row.leaveStatus === \'PENDING\'"> · <span class="op" @click="openAction(\'APPROVE\', row)">通过</span> · <span class="op" style="color:var(--tk-bad)" @click="openAction(\'REJECT\', row)">驳回</span></template>',
            '              <template v-if="canManage && row.leaveStatus === \'APPROVED\'"> · <span class="op" style="color:var(--tk-warn)" @click="openAction(\'CANCEL\', row)">销假</span></template>',
            '            </td>',
            '          </tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',
            '  </div>',

            '  <div class="pfoot">',
            '    <div class="pgs">',
            '      <b class="edge" @click="goPage(pageNum - 1)" v-if="pageNum > 1">‹</b>',
            '      <b v-for="p in pageNumbers" :key="p" :class="{ on: p === pageNum }" @click="goPage(p)">{{ p }}</b>',
            '      <b class="edge" @click="goPage(pageNum + 1)" v-if="pageNum < totalPages">›</b>',
            '    </div>',
            '    <div class="tot">',
            '      <span>每页',
            '        <select v-model.number="pageSize" @change="reload" style="border:0;background:none;color:inherit;font:inherit;outline:none">',
            '          <option :value="10">10</option><option :value="20">20</option><option :value="50">50</option>',
            '        </select> 条 · 共 {{ total }} 条',
            '      </span>',
            '    </div>',
            '  </div>',

            '  <div v-if="detail" class="cmd-mask" style="padding-top:6vh" @click.self="detail = null">',
            '    <div class="cmd-panel" style="width:760px;max-height:86vh;display:flex;flex-direction:column">',
            '      <div class="cmd-input" style="flex:none">',
            '        <b style="font-size:14px;color:var(--tk-text)">请假详情 {{ detail.requestNo }}</b>',
            '        <span class="bdg" :class="leaveTone(detail.leaveStatus)" style="margin-left:9px">{{ leaveStatusText(detail.leaveStatus) }}</span>',
            '        <span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="detail = null">✕</span>',
            '      </div>',
            '      <div style="overflow:auto;padding:16px 20px;display:flex;flex-direction:column;gap:14px">',
            '        <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:10px">',
            '          <div class="os-field"><label>申请人</label><div style="font-size:12px;color:var(--tk-text)">{{ detail.requester ? detail.requester.displayName : "—" }}<span style="color:var(--tk-muted);font-size:10px;margin-left:6px">{{ detail.requester ? detail.requester.phoneMasked : "" }}</span></div></div>',
            '          <div class="os-field"><label>组织</label><div style="font-size:12px;color:var(--tk-text)">{{ detail.requester && detail.requester.organizationName || detail.handlerOrganizationName || "—" }}</div></div>',
            '          <div class="os-field"><label>请假类型</label><div style="font-size:12px;color:var(--tk-text)">{{ detail.leaveType || "—" }}</div></div>',
            '          <div class="os-field"><label>请假时间</label><div style="font-size:12px;color:var(--tk-text)">{{ timeRange(detail) }}</div></div>',
            '          <div class="os-field"><label>请假天数</label><div style="font-size:12px;color:var(--tk-text)">{{ detail.leaveDays == null ? "—" : detail.leaveDays + " 天" }}</div></div>',
            '          <div class="os-field"><label>申请单状态</label><div style="font-size:12px;color:var(--tk-text)">{{ requestStatusText(detail.status) }}</div></div>',
            '        </div>',
            '        <div class="os-field"><label>请假事由</label><div class="os-note" style="margin:0">{{ detail.leaveReason || "—" }}</div></div>',
            '        <div v-if="detail.materials && detail.materials.length" class="os-field"><label>请假材料</label><div style="display:flex;gap:8px;flex-wrap:wrap"><a v-for="m in materialUrls(detail.materials)" :key="m" :href="m" target="_blank" rel="noopener"><img :src="m" style="width:74px;height:74px;object-fit:cover;border-radius:6px;border:1px solid var(--tk-stroke)" /></a></div></div>',
            '        <div v-if="detail.rejectReason" class="os-field"><label>驳回原因</label><div class="os-note bad" style="margin:0">{{ detail.rejectReason }}</div></div>',
            '        <div v-if="detail.leaveCancelAt" class="os-field"><label>销假时间</label><div style="font-size:12px;color:var(--tk-text)">{{ fmt(detail.leaveCancelAt) }}</div></div>',
            '        <div class="os-field"><label>处理时间线</label>',
            '          <div style="display:flex;flex-direction:column;gap:7px;margin-top:4px">',
            '            <div v-for="(log, i) in detail.timeline" :key="i" style="display:flex;gap:9px;font-size:11px;color:var(--tk-text3)"><span class="os-mono" style="width:118px;flex:none">{{ fmt(log.createdAt) }}</span><span style="width:78px;flex:none">{{ actionText(log.action) }}</span><span style="flex:1">{{ log.operatorName || "系统" }}<span v-if="log.comment" style="color:var(--tk-text2)">：{{ log.comment }}</span></span></div>',
            '            <div v-if="!detail.timeline || !detail.timeline.length" style="font-size:11px;color:var(--tk-muted)">暂无处理记录</div>',
            '          </div>',
            '        </div>',
            '        <div v-if="detail.availableActions && detail.availableActions.length" style="display:flex;justify-content:flex-end;gap:9px;border-top:1px solid var(--tk-row);padding-top:12px">',
            '          <button v-if="hasAction(\'REJECT\')" class="os-btn" style="color:var(--tk-bad)" @click="openAction(\'REJECT\', detail)">驳回</button>',
            '          <button v-if="hasAction(\'APPROVE\')" class="os-btn primary" @click="openAction(\'APPROVE\', detail)">通过</button>',
            '          <button v-if="hasAction(\'CANCEL\')" class="os-btn primary" @click="openAction(\'CANCEL\', detail)">销假</button>',
            '        </div>',
            '      </div>',
            '    </div>',
            '  </div>',

            '  <div v-if="actionModal.visible" class="cmd-mask" style="padding-top:18vh" @click.self="actionModal.visible = false">',
            '    <div class="cmd-panel" style="width:460px">',
            '      <div class="cmd-input"><b style="font-size:13px;color:var(--tk-text)">{{ actionTitle }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="actionModal.visible = false">✕</span></div>',
            '      <div style="padding:16px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div style="font-size:11px;color:var(--tk-text3)">{{ actionModal.row.requestNo }} · {{ actionModal.row.requesterName || "—" }} · {{ timeRange(actionModal.row) }}</div>',
            '        <div v-if="actionModal.kind === \'CANCEL\'" class="os-note warn" style="margin:0">销假将把该请假标记为已销假并办结申请（status=COMPLETED），记录销假时间；此操作不可撤销。</div>',
            '        <div class="os-field"><label>{{ actionModal.kind === \'REJECT\' ? "驳回原因（必填）" : "审批意见（选填）" }}</label><textarea class="os-textarea" v-model.trim="actionModal.comment" maxlength="512" :placeholder="actionModal.kind === \'REJECT\' ? \'请说明驳回原因，将通知申请人\' : \'可填写审批说明\'"></textarea></div>',
            '        <div style="display:flex;justify-content:flex-end;gap:9px"><button class="os-btn" @click="actionModal.visible = false">取消</button><button class="os-btn primary" :disabled="submitting" @click="submitAction">{{ submitting ? "提交中…" : "确认" + actionLabel }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var stats = Vue.ref({ pending: 0, approved: 0, rejected: 0, cancelled: 0, total: 0 });
            var rows = Vue.ref([]);
            var total = Vue.ref(0);
            var pageNum = Vue.ref(1);
            var pageSize = Vue.ref(20);
            var totalPages = Vue.ref(1);
            var status = Vue.ref('');
            var leaveType = Vue.ref('');
            var startDate = Vue.ref('');
            var endDate = Vue.ref('');
            var keyword = Vue.ref('');
            var loading = Vue.ref(false);
            var error = Vue.ref('');
            var detail = Vue.ref(null);
            var submitting = Vue.ref(false);
            var actionModal = Vue.reactive({ visible: false, kind: 'APPROVE', row: {}, comment: '' });

            function permissions() {
                var user = (window.AdminApp && window.AdminApp.state && window.AdminApp.state.user) || {};
                var perms = (user.permissions || []).slice();
                var roles = user.roles || [];
                if (roles.indexOf('PLATFORM_ADMIN') >= 0 && perms.indexOf('platform:all') < 0) perms.push('platform:all');
                return perms;
            }
            var canManage = Vue.computed(function () {
                var perms = permissions();
                return perms.indexOf('platform:all') >= 0 || perms.indexOf('catalog:manage') >= 0
                    || perms.indexOf('work-order:dispatch') >= 0;
            });
            var pageNumbers = Vue.computed(function () {
                var pages = [];
                var start = Math.max(1, pageNum.value - 2);
                var end = Math.min(totalPages.value, start + 4);
                start = Math.max(1, end - 4);
                for (var p = start; p <= end; p++) pages.push(p);
                return pages;
            });
            var actionTitle = Vue.computed(function () {
                return { APPROVE: '通过请假', REJECT: '驳回请假', CANCEL: '确认销假' }[actionModal.kind] || '处理请假';
            });
            var actionLabel = Vue.computed(function () {
                return ACTION_TEXT[actionModal.kind] || actionModal.kind;
            });

            function buildQuery() {
                var query = 'pageNum=' + pageNum.value + '&pageSize=' + pageSize.value;
                if (status.value) query += '&status=' + encodeURIComponent(status.value);
                if (leaveType.value) query += '&leaveType=' + encodeURIComponent(leaveType.value);
                if (startDate.value) query += '&startDate=' + encodeURIComponent(startDate.value);
                if (endDate.value) query += '&endDate=' + encodeURIComponent(endDate.value);
                if (keyword.value) query += '&keyword=' + encodeURIComponent(keyword.value);
                return query;
            }
            function loadList() {
                loading.value = true;
                error.value = '';
                return api.get('/api/admin/leave-requests?' + buildQuery())
                    .then(function (data) {
                        rows.value = (data && data.records) || [];
                        total.value = (data && data.total) || 0;
                        totalPages.value = Math.max(1, Math.ceil(total.value / pageSize.value));
                        if (pageNum.value > totalPages.value) {
                            pageNum.value = totalPages.value;
                        }
                    })
                    .catch(function (err) {
                        rows.value = [];
                        total.value = 0;
                        error.value = (err && err.message) || '请假列表加载失败';
                        El.ElMessage.error(error.value);
                    })
                    .finally(function () { loading.value = false; });
            }
            function loadStats() {
                return api.get('/api/admin/leave-requests/stats')
                    .then(function (data) { stats.value = data || stats.value; })
                    .catch(function () { /* 统计失败不阻断列表 */ });
            }
            function loadAll() {
                loadStats();
                return loadList();
            }
            function reload() { pageNum.value = 1; loadList(); }
            function switchStatus(value) {
                status.value = value;
                reload();
            }
            function goPage(page) {
                if (page < 1 || page > totalPages.value || page === pageNum.value) return;
                pageNum.value = page;
                loadList();
            }
            function resetFilters() {
                status.value = '';
                leaveType.value = '';
                startDate.value = '';
                endDate.value = '';
                keyword.value = '';
                pageNum.value = 1;
                loadList();
            }
            function leaveStatusText(v) { return LEAVE_STATUS_TEXT[v] || v || '—'; }
            function leaveTone(v) { return LEAVE_STATUS_TONE[v] || 'n2'; }
            function requestStatusText(v) { return REQUEST_STATUS_TEXT[v] || v || '—'; }
            function actionText(action) {
                return { SUBMIT: '提交', DISPATCH: '通过', START: '开始处理', COMPLETE: '办结/销假', CONFIRM: '确认', REJECT: '驳回', CANCEL: '撤销', EVALUATE: '评价', URGE: '催单', ARRIVAL_PHOTO: '到场', COMPLETION_PHOTO: '完工' }[action] || action || '—';
            }
            function materialUrls(materials) {
                var urls = [];
                (materials || []).forEach(function (m) { (m.urls || []).forEach(function (u) { urls.push(u); }); });
                return urls;
            }
            function hasAction(action) {
                return !!(detail.value && (detail.value.availableActions || []).indexOf(action) >= 0);
            }

            function openDetail(row) {
                api.get('/api/admin/leave-requests/' + row.requestId)
                    .then(function (data) { detail.value = data; })
                    .catch(function (err) { El.ElMessage.error((err && err.message) || '详情加载失败'); });
            }
            function openAction(kind, row) {
                actionModal.kind = kind;
                actionModal.row = Object.assign({}, row, {
                    requesterName: row.requesterName || (row.requester && row.requester.displayName) || null
                });
                actionModal.comment = '';
                actionModal.visible = true;
            }
            function submitAction() {
                if (!canManage.value) { El.ElMessage.warning('当前账号没有请假审批权限'); return; }
                var kind = actionModal.kind;
                if (kind === 'REJECT' && !actionModal.comment) {
                    El.ElMessage.warning('驳回需填写原因');
                    return;
                }
                var url = '/api/admin/leave-requests/' + actionModal.row.requestId
                    + (kind === 'APPROVE' ? '/approve' : kind === 'REJECT' ? '/reject' : '/cancel');
                var body = kind === 'REJECT' ? { reason: actionModal.comment } : { comment: actionModal.comment || null };
                submitting.value = true;
                api.post(url, body)
                    .then(function () {
                        El.ElMessage.success('已' + actionText(kind));
                        actionModal.visible = false;
                        detail.value = null;
                        loadAll();
                    })
                    .catch(function (err) { El.ElMessage.error((err && err.message) || '操作失败'); })
                    .finally(function () { submitting.value = false; });
            }

            loadAll();
            return {
                stats: stats, rows: rows, total: total, pageNum: pageNum, pageSize: pageSize,
                totalPages: totalPages, pageNumbers: pageNumbers, loading: loading, error: error,
                status: status, leaveType: leaveType, startDate: startDate, endDate: endDate, keyword: keyword,
                tabs: TABS, leaveTypes: LEAVE_TYPES, canManage: canManage,
                detail: detail, actionModal: actionModal, submitting: submitting, actionTitle: actionTitle, actionLabel: actionLabel,
                loadAll: loadAll, reload: reload, switchStatus: switchStatus, goPage: goPage,
                resetFilters: resetFilters, openDetail: openDetail, openAction: openAction,
                submitAction: submitAction, hasAction: hasAction, materialUrls: materialUrls,
                leaveStatusText: leaveStatusText, leaveTone: leaveTone, requestStatusText: requestStatusText,
                actionText: actionText, timeRange: timeRange, fmt: fmt
            };
        }
    };
})();
