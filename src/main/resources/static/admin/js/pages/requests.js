/* 服务申请（管理端审批）：真实接口 /api/admin/service-requests。
   状态页签 + 分页 + 审批/驳回弹层，画布规范。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    var STATUS_TEXT = {
        SUBMITTED: '待审批', PROCESSING: '处理中', COMPLETED: '已完成',
        REJECTED: '已驳回', CANCELLED: '已取消'
    };
    var STATUS_TONE = {
        SUBMITTED: 'a', PROCESSING: 'i', COMPLETED: 'g', REJECTED: 'r', CANCELLED: 'n2'
    };
    var TABS = [
        { value: '', label: '全部' },
        { value: 'SUBMITTED', label: '待审批' },
        { value: 'PROCESSING', label: '处理中' },
        { value: 'COMPLETED', label: '已完成' },
        { value: 'REJECTED', label: '已驳回' }
    ];

    window.AdminPages.RequestsPage = {
        name: 'RequestsPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="page-h">',
            '    <div><h1>服务申请</h1><div class="sub">全校服务申请统一受理与审批</div></div>',
            '    <div class="r"><button class="btn2" @click="load">刷新</button></div>',
            '  </div>',

            '  <div class="tabs">',
            '    <span v-for="t in tabs" :key="t.value" class="tab" :class="{ on: status === t.value }" @click="switchStatus(t.value)">{{ t.label }}</span>',
            '  </div>',

            '  <div class="lcard" style="flex:1;min-height:0">',
            '    <div class="cap">申请记录<span class="cnt" v-if="total">共 {{ total }} 条</span></div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto">',
            '      <table class="pk">',
            '        <colgroup><col style="width:150px"><col style="width:220px"><col style="width:200px"><col style="width:110px"><col style="width:160px"><col style="width:110px"><col></colgroup>',
            '        <thead><tr><th>申请单号</th><th>服务 · 事项</th><th>标题</th><th>状态</th><th>提交时间</th><th>完成时间</th><th class="r">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-if="!rows.length && !loading"><td colspan="7" style="text-align:center;color:var(--tk-muted);padding:32px 0">暂无申请</td></tr>',
            '          <tr v-if="loading"><td colspan="7" style="text-align:center;color:var(--tk-muted);padding:32px 0">加载中…</td></tr>',
            '          <tr v-for="row in rows" :key="row.requestId">',
            '            <td style="color:var(--tk-muted)">{{ row.requestNo }}</td>',
            '            <td style="color:var(--tk-text)">{{ row.itemName }}</td>',
            '            <td>{{ row.title }}</td>',
            '            <td><span class="bdg" :class="statusTone(row.status)">{{ statusText(row.status) }}</span></td>',
            '            <td style="color:var(--tk-text3)">{{ fmt(row.submittedAt) }}</td>',
            '            <td style="color:var(--tk-text3)">{{ fmt(row.completedAt) }}</td>',
            '            <td class="r">',
            '              <span class="op" @click="openApprove(row)">审批</span>',
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
            '        </select> 条</span>',
            '      <span>共 {{ total }} 条 · 第 {{ pageNum }} / {{ totalPages }} 页</span>',
            '    </div>',
            '  </div>',

            // 弹层: 审批
            '  <div class="pmask" :class="{ show: approveVisible }" @click.self="approveVisible = false">',
            '    <div class="pcard" v-if="target">',
            '      <div class="ph"><b>审批申请</b><span class="no">{{ target.requestNo }}</span><span class="sp"></span><span class="x" @click="approveVisible = false">✕</span></div>',
            '      <div class="psum">',
            '        <div class="l1">服务　　　{{ target.itemName }}</div>',
            '        <div class="l1">标题　　　{{ target.title }}</div>',
            '        <div class="l1">提交时间　{{ fmt(target.submittedAt) }}</div>',
            '      </div>',
            '      <div class="plbl">审批动作</div>',
            '      <div class="radio" :class="{ on: action === \'APPROVE\' }" @click="action = \'APPROVE\'"><span class="dot"></span>通过（转处理中，进入派单流程）</div>',
            '      <div class="radio" :class="{ on: action === \'REJECT\' }" @click="action = \'REJECT\'"><span class="dot"></span>驳回（需填写驳回原因）</div>',
            '      <div class="fld"><label>审批意见</label><textarea class="pta" v-model="comment" maxlength="512" placeholder="将记录到申请时间线"></textarea></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="approveVisible = false">取消</button>',
            '        <button class="c" :class="action === \'APPROVE\' ? \'green\' : \'red\'" :disabled="submitting" @click="submit">{{ submitting ? "提交中…" : (action === \'APPROVE\' ? "确认通过" : "确认驳回") }}</button></div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var status = Vue.ref('');
            var rows = Vue.ref([]);
            var total = Vue.ref(0);
            var pageNum = Vue.ref(1);
            var pageSize = Vue.ref(10);
            var loading = Vue.ref(false);

            var approveVisible = Vue.ref(false);
            var target = Vue.ref(null);
            var action = Vue.ref('APPROVE');
            var comment = Vue.ref('');
            var submitting = Vue.ref(false);

            function load() {
                loading.value = true;
                api.get('/api/admin/service-requests?pageNum=' + pageNum.value +
                        '&pageSize=' + pageSize.value +
                        (status.value ? '&status=' + status.value : ''))
                    .then(function (page) {
                        rows.value = page.records || [];
                        total.value = page.total || 0;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { loading.value = false; });
            }
            function reload() { pageNum.value = 1; load(); }
            function switchStatus(v) { status.value = v; reload(); }

            var totalPages = Vue.computed(function () {
                return Math.max(1, Math.ceil(total.value / pageSize.value));
            });
            var pageNumbers = Vue.computed(function () {
                var pages = [];
                var start = Math.max(1, pageNum.value - 2);
                var end = Math.min(totalPages.value, start + 4);
                for (var p = start; p <= end; p++) pages.push(p);
                return pages;
            });
            function goPage(p) { if (p >= 1 && p <= totalPages.value && p !== pageNum.value) { pageNum.value = p; load(); } }

            function openApprove(row) {
                target.value = row;
                action.value = 'APPROVE';
                comment.value = '';
                approveVisible.value = true;
            }
            function submit() {
                if (action.value === 'REJECT' && !comment.value.trim()) {
                    El.ElMessage.warning('驳回需填写原因');
                    return;
                }
                submitting.value = true;
                api.post('/api/admin/service-requests/' + target.value.requestId + '/review',
                        { action: action.value, comment: comment.value.trim() || null })
                    .then(function () {
                        El.ElMessage.success(action.value === 'APPROVE' ? '已通过' : '已驳回');
                        approveVisible.value = false;
                        load();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { submitting.value = false; });
            }

            function statusText(v) { return STATUS_TEXT[v] || v || '-'; }
            function statusTone(v) { return STATUS_TONE[v] || 'n2'; }
            function fmt(v) {
                if (!v) return '-';
                return String(v).replace('T', ' ').slice(0, 16);
            }

            load();

            return {
                tabs: TABS, status: status, rows: rows, total: total, pageNum: pageNum, pageSize: pageSize,
                loading: loading, totalPages: totalPages, pageNumbers: pageNumbers, goPage: goPage,
                switchStatus: switchStatus, reload: reload,
                approveVisible: approveVisible, target: target, action: action, comment: comment, submitting: submitting,
                openApprove: openApprove, submit: submit,
                statusText: statusText, statusTone: statusTone, fmt: fmt
            };
        }
    };
})();
