/* 工单调度页：队列分页/状态过滤、详情查看、派发（选择服务人员）、驳回。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    var STATUS_OPTIONS = [
        { value: 'PENDING_DISPATCH', label: '待派发' },
        { value: 'ASSIGNED', label: '已派发' },
        { value: 'PROCESSING', label: '处理中' },
        { value: 'COMPLETED', label: '已完成' },
        { value: 'REJECTED', label: '已驳回' },
        { value: 'CANCELLED', label: '已取消' }
    ];

    var STATUS_TAG = {
        PENDING_DISPATCH: 'warning',
        ASSIGNED: 'primary',
        PROCESSING: 'success',
        COMPLETED: 'info',
        REJECTED: 'danger',
        CANCELLED: 'info'
    };

    function statusLabel(value) {
        for (var i = 0; i < STATUS_OPTIONS.length; i++) {
            if (STATUS_OPTIONS[i].value === value) return STATUS_OPTIONS[i].label;
        }
        return value || '-';
    }

    window.AdminPages.WorkOrdersPage = {
        name: 'WorkOrdersPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="page-h">',
            '    <div>',
            '      <h1>工单调度</h1>',
            '      <div class="sub">工单统一调度与派单 · 全流程闭环跟踪</div>',
            '    </div>',
            '    <div class="r"><button class="btn2" @click="reload">刷新</button></div>',
            '  </div>',

            '  <div class="tabs">',
            '    <span v-for="item in statusOptions" :key="item.value" class="tab"',
            '          :class="{ on: status === item.value }" @click="switchStatus(item.value)">{{ item.label }}</span>',
            '  </div>',

            '  <div class="lcard" style="flex:1">',
            '    <div class="cap">工单列表<span class="cnt" v-if="total">共 {{ total }} 条</span></div>',
            '    <div class="tblw">',
            '      <table class="pk">',
            '        <colgroup><col style="width:200px"><col><col style="width:110px"><col style="width:130px"><col style="width:170px"><col style="width:210px"></colgroup>',
            '        <thead><tr><th>工单号</th><th>标题</th><th>状态</th><th>处理人</th><th>创建时间</th><th class="r">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-if="!rows.length && !loading"><td colspan="6" style="text-align:center;color:var(--tk-muted);padding:32px 0">暂无工单</td></tr>',
            '          <tr v-if="loading"><td colspan="6" style="text-align:center;color:var(--tk-muted);padding:32px 0">加载中…</td></tr>',
            '          <tr v-for="row in rows" :key="row.orderId">',
            '            <td style="color:var(--tk-muted)">{{ row.orderNo }}</td>',
            '            <td style="color:var(--tk-text)">{{ row.title }}</td>',
            '            <td><span class="bdg" :class="statusTone(row.status)">{{ statusLabel(row.status) }}</span></td>',
            '            <td>{{ row.assigneeName || "-" }}</td>',
            '            <td style="color:var(--tk-text3)">{{ fmt(row.createdAt) }}</td>',
            '            <td class="r">',
            '              <span class="op" @click="openDetail(row)">详情</span>',
            '              <template v-if="row.status === \'PENDING_DISPATCH\'"> · <span class="op" @click="openAssign(row)">派发</span></template>',
            '              <template v-if="canReject(row.status)"> · <span class="op r" @click="openReject(row)">驳回</span></template>',
            '            </td>',
            '          </tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',
            '  </div>',

            '  <div class="pfoot">',
            '    <div class="pgs">',
            '      <b class="edge" @click="prevPage" v-if="pageNum > 1">‹</b>',
            '      <b v-for="p in pageNumbers" :key="p" :class="{ on: p === pageNum }" @click="goPage(p)">{{ p }}</b>',
            '      <b class="edge" @click="nextPage" v-if="pageNum < totalPages">›</b>',
            '    </div>',
            '    <div class="tot">',
            '      <span>每页',
            '        <select v-model.number="pageSize" @change="reload" style="border:0;background:none;color:inherit;font:inherit;outline:none">',
            '          <option :value="10">10</option><option :value="20">20</option><option :value="50">50</option>',
            '        </select> 条</span>',
            '      <span>共 {{ total }} 条 · 第 {{ pageNum }} / {{ totalPages }} 页</span>',
            '    </div>',
            '  </div>',

            // 弹层: 工单详情
            '  <div class="pmask" :class="{ show: detailVisible }" @click.self="detailVisible = false">',
            '    <div class="pcard w640" v-if="detail">',
            '      <div class="ph"><b>工单详情</b><span class="no">{{ detail.orderNo }}</span><span class="sp"></span><span class="x" @click="detailVisible = false">✕</span></div>',
            '      <div style="display:flex;align-items:center;gap:10px">',
            '        <span class="badge" :class="detailTone(detail.status)">{{ statusLabel(detail.status) }}</span>',
            '        <span style="font-size:12px;color:#7A7A7A">创建于 {{ fmt(detail.createdAt) }}</span>',
            '      </div>',
            '      <div class="kv">',
            '        <div class="row">标题　　　{{ detail.title }}</div>',
            '        <div class="row">申请编号　{{ detail.requestNo || "-" }}</div>',
            '        <div class="row">申请人　　{{ detail.requesterName || "-" }}</div>',
            '        <div class="row">处理人　　{{ detail.assigneeName || "-" }}</div>',
            '        <div class="row">派单时间　{{ fmt(detail.assignedAt) }}</div>',
            '        <div class="row">开始时间　{{ fmt(detail.startedAt) }}</div>',
            '        <div class="row">完成时间　{{ fmt(detail.completedAt) }}</div>',
            '        <div class="row" v-if="detail.completeRemark">完成说明　{{ detail.completeRemark }}</div>',
            '      </div>',
            '      <template v-if="formEntries(detail.formData).length">',
            '        <div class="plbl">申请表单内容</div>',
            '        <div class="kv">',
            '          <div class="row" v-for="entry in formEntries(detail.formData)" :key="entry[0]">{{ entry[0] }}　{{ entry[1] }}</div>',
            '        </div>',
            '      </template>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="detailVisible = false">关闭</button></div>',
            '    </div>',
            '  </div>',

            // 弹层: 派发
            '  <div class="pmask" :class="{ show: assignVisible }" @click.self="assignVisible = false">',
            '    <div class="pcard">',
            '      <div class="ph"><b>派发工单</b><span class="sp"></span><span class="x" @click="assignVisible = false">✕</span></div>',
            '      <div class="fld"><label>服务人员 <em>*</em></label>',
            '        <div class="pin"><select v-model="assigneeId" :disabled="workersLoading" style="width:100%">',
            '          <option :value="null" disabled>{{ workersLoading ? "加载中…" : "搜索并选择服务人员" }}</option>',
            '          <option v-for="worker in workers" :key="worker.userId" :value="worker.userId">',
            '            {{ worker.displayName + (worker.phone ? "（" + worker.phone + "）" : "") }}',
            '          </option>',
            '        </select></div>',
            '      </div>',
            '      <div class="fld"><label>派单备注</label><textarea class="pta" v-model="assignComment" maxlength="512" placeholder="可空"></textarea></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="assignVisible = false">取消</button>',
            '        <button class="c blue" :disabled="assignSubmitting" @click="submitAssign">{{ assignSubmitting ? "提交中…" : "确认派发" }}</button></div>',
            '    </div>',
            '  </div>',

            // 弹层: 驳回
            '  <div class="pmask" :class="{ show: rejectVisible }" @click.self="rejectVisible = false">',
            '    <div class="pcard w440">',
            '      <div class="ph"><b>驳回工单</b><span class="sp"></span><span class="x" @click="rejectVisible = false">✕</span></div>',
            '      <div class="fld"><label>驳回原因 <em>*</em></label><textarea class="pta" v-model="rejectReason" maxlength="512" placeholder="请填写驳回原因（必填）"></textarea></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="rejectVisible = false">取消</button>',
            '        <button class="c red" :disabled="rejectSubmitting" @click="submitReject">{{ rejectSubmitting ? "提交中…" : "确认驳回" }}</button></div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var status = Vue.ref('PENDING_DISPATCH');
            var rows = Vue.ref([]);
            var total = Vue.ref(0);
            var pageNum = Vue.ref(1);
            var pageSize = Vue.ref(10);
            var loading = Vue.ref(false);

            var detail = Vue.ref(null);
            var detailVisible = Vue.ref(false);

            var assignVisible = Vue.ref(false);
            var assignOrderId = Vue.ref(null);
            var assigneeId = Vue.ref(null);
            var assignComment = Vue.ref('');
            var workers = Vue.ref([]);
            var workersLoading = Vue.ref(false);
            var assignSubmitting = Vue.ref(false);

            var rejectVisible = Vue.ref(false);
            var rejectOrderId = Vue.ref(null);
            var rejectReason = Vue.ref('');
            var rejectSubmitting = Vue.ref(false);

            function load() {
                loading.value = true;
                api.get('/api/admin/work-orders?pageNum=' + pageNum.value +
                        '&pageSize=' + pageSize.value +
                        (status.value ? '&status=' + status.value : ''))
                    .then(function (page) {
                        rows.value = page.records || [];
                        total.value = page.total || 0;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { loading.value = false; });
            }

            function reload() {
                pageNum.value = 1;
                load();
            }

            function openDetail(row) {
                api.get('/api/admin/work-orders/' + row.orderId)
                    .then(function (data) {
                        detail.value = data;
                        detailVisible.value = true;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }

            function loadWorkers() {
                workersLoading.value = true;
                api.get('/api/admin/users/workers')
                    .then(function (list) {
                        workers.value = list || [];
                        if (!workers.value.length) {
                            El.ElMessage.warning('暂无在岗服务人员，请先在用户管理中为用户授予服务人员角色');
                        }
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { workersLoading.value = false; });
            }

            function openAssign(row) {
                assignOrderId.value = row.orderId;
                assigneeId.value = null;
                assignComment.value = '';
                assignVisible.value = true;
                loadWorkers();
            }

            function submitAssign() {
                if (!assigneeId.value) {
                    El.ElMessage.warning('请选择服务人员');
                    return;
                }
                assignSubmitting.value = true;
                api.post('/api/admin/work-orders/' + assignOrderId.value + '/assign',
                        { assigneeId: assigneeId.value, comment: assignComment.value || null })
                    .then(function () {
                        El.ElMessage.success('派发成功');
                        assignVisible.value = false;
                        load();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { assignSubmitting.value = false; });
            }

            function openReject(row) {
                rejectOrderId.value = row.orderId;
                rejectReason.value = '';
                rejectVisible.value = true;
            }

            function submitReject() {
                if (!rejectReason.value.trim()) {
                    El.ElMessage.warning('请填写驳回原因');
                    return;
                }
                rejectSubmitting.value = true;
                api.post('/api/admin/work-orders/' + rejectOrderId.value + '/reject',
                        { reason: rejectReason.value.trim() })
                    .then(function () {
                        El.ElMessage.success('已驳回');
                        rejectVisible.value = false;
                        load();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { rejectSubmitting.value = false; });
            }

            function canReject(statusValue) {
                return statusValue === 'PENDING_DISPATCH' || statusValue === 'ASSIGNED' ||
                    statusValue === 'PROCESSING';
            }

            function switchStatus(value) {
                status.value = value;
                reload();
            }

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
            function prevPage() { goPage(pageNum.value - 1); }
            function nextPage() { goPage(pageNum.value + 1); }

            var TONE_MAP = {
                PENDING_DISPATCH: 'a', ASSIGNED: 'i', PROCESSING: 'i',
                COMPLETED: 'g', REJECTED: 'r', CANCELLED: 'n2'
            };
            function statusTone(value) { return TONE_MAP[value] || 'n2'; }
            function detailTone(value) {
                return { PENDING_DISPATCH: 'a', ASSIGNED: 'i', PROCESSING: 'i', COMPLETED: 'g', REJECTED: 'r', CANCELLED: 'n2' }[value] || 'n2';
            }

            function formEntries(formData) {
                if (!formData) return [];
                return Object.entries(formData).filter(function (entry) {
                    return entry[1] !== null && entry[1] !== undefined && entry[1] !== '';
                });
            }

            load();

            return {
                statusOptions: STATUS_OPTIONS,
                status: status, rows: rows, total: total, pageNum: pageNum, pageSize: pageSize,
                loading: loading,
                totalPages: totalPages, pageNumbers: pageNumbers, goPage: goPage, prevPage: prevPage, nextPage: nextPage,
                switchStatus: switchStatus,
                detail: detail, detailVisible: detailVisible,
                assignVisible: assignVisible, assigneeId: assigneeId, assignComment: assignComment,
                workers: workers, workersLoading: workersLoading, assignSubmitting: assignSubmitting,
                rejectVisible: rejectVisible, rejectReason: rejectReason, rejectSubmitting: rejectSubmitting,
                load: load, reload: reload,
                openDetail: openDetail, openAssign: openAssign, submitAssign: submitAssign,
                openReject: openReject, submitReject: submitReject,
                canReject: canReject,
                statusLabel: statusLabel, statusTag: function (value) { return STATUS_TAG[value] || 'info'; },
                statusTone: statusTone, detailTone: detailTone,
                formEntries: formEntries,
                fmt: api.fmtDateTime,
                RefreshIcon: ElementPlusIconsVue.Refresh
            };
        }
    };
})();
