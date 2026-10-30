/* 工单调度页：SLA 统计/超时筛选/服务人员负载、队列分页、详情查看、派发/改派/批量派发/驳回。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    var STATUS_OPTIONS = [
        { value: '', label: '全部' },
        { value: 'PENDING_DISPATCH', label: '待派发' },
        { value: 'ASSIGNED', label: '已派发' },
        { value: 'PROCESSING', label: '处理中' },
        { value: 'PENDING_CONFIRMATION', label: '待确认' },
        { value: 'COMPLETED', label: '已完成' },
        { value: 'REJECTED', label: '已驳回' }
    ];

    var STATUS_TAG = {
        PENDING_DISPATCH: 'warning',
        ASSIGNED: 'primary',
        PROCESSING: 'success',
        ONSITE_PHOTO_RECORDED: 'success',
        PENDING_CONFIRMATION: 'warning',
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
            '<div class="pgw" style="display:flex;flex-direction:column;gap:14px;flex:1;min-height:0">',
            '  <div class="page-h">',
            '    <div>',
            '      <h1>工单调度</h1>',
            '      <div class="sub">SLA 时限跟踪 · 派单/改派/批量派发 · 处理人负载</div>',
            '    </div>',
            '    <div class="r">',
            '      <button class="btn2" @click="toggleBatchMode">{{ batchMode ? "退出批量" : "批量派单" }}</button>',
            '      <button class="btn2" @click="refreshAll()">刷新</button>',
            '    </div>',
            '  </div>',

            '  <div class="statgrid">',
            '    <div class="stat"><div class="lb">待派发</div><div class="v"><b>{{ stats.pendingDispatch || 0 }}</b><span>单</span></div></div>',
            '    <div class="stat"><div class="lb">进行中</div><div class="v"><b>{{ stats.inProgressTotal || 0 }}</b><span>单</span></div></div>',
            '    <div class="stat"><div class="lb">已超时</div><div class="v"><b style="color:var(--tk-bad)">{{ stats.overdue || 0 }}</b><span>单</span></div></div>',
            '    <div class="stat"><div class="lb">即将超时</div><div class="v"><b style="color:var(--tk-warn)">{{ stats.dueSoon || 0 }}</b><span>4 小时内</span></div></div>',
            '    <div class="stat"><div class="lb">平均处理时长</div><div class="v"><b>{{ stats.avgProcessHours == null ? "-" : stats.avgProcessHours }}</b><span>小时</span></div></div>',
            '    <div class="stat"><div class="lb">近 30 天完成</div><div class="v"><b>{{ stats.completedLast30Days || 0 }}</b><span>单</span></div></div>',
            '  </div>',

            '  <div class="lcard" style="flex:none;gap:8px">',
            '    <div class="cap" style="font-size:13px">处理人负载<span class="cnt">{{ workload.length }} 人 · 进行中/超时</span>',
            '      <span class="r" style="font-size:11px;color:var(--tk-muted)">超时率 {{ stats.overdueRate == null ? "-" : stats.overdueRate + "%" }} · 平均办结 {{ stats.avgCompletionHours == null ? "-" : stats.avgCompletionHours }} 小时</span>',
            '    </div>',
            '    <div v-if="!workload.length" style="font-size:12px;color:var(--tk-muted);padding:6px 0">暂无在岗服务人员</div>',
            '    <div style="display:flex;gap:8px;overflow-x:auto;padding-bottom:2px">',
            '      <div v-for="w in workload" :key="w.userId"',
            '           style="flex:none;min-width:150px;border:1px solid var(--tk-stroke);border-radius:8px;padding:8px 10px;display:flex;flex-direction:column;gap:4px">',
            '        <div style="display:flex;align-items:center;gap:6px">',
            '          <b style="font-size:12px;color:var(--tk-text)">{{ w.displayName || w.username }}</b>',
            '          <span style="font-size:10px;color:var(--tk-muted)">{{ w.organizationName || "未归属组织" }}</span>',
            '        </div>',
            '        <div style="display:flex;gap:6px;font-size:11px;color:var(--tk-text3)">',
            '          <span>处理中 <b style="color:var(--tk-text)">{{ w.activeCount }}</b></span>',
            '          <span v-if="Number(w.overdueCount) > 0" style="color:var(--tk-bad)">超时 {{ w.overdueCount }}</span>',
            '          <span v-if="Number(w.dueSoonCount) > 0" style="color:var(--tk-warn)">临期 {{ w.dueSoonCount }}</span>',
            '        </div>',
            '      </div>',
            '    </div>',
            '  </div>',

            '  <div class="tabs">',
            '    <span v-for="item in statusOptions" :key="item.value" class="tab"',
            '          :class="{ on: status === item.value }" @click="switchStatus(item.value)">{{ item.label }}</span>',
            '  </div>',

            '  <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">',
            '    <span style="font-size:11px;color:var(--tk-muted)">SLA 筛选</span>',
            '    <button class="btn2" :class="{ pri: slaFilter === \'\' }" @click="switchSlaFilter(\'\')">全部</button>',
            '    <button class="btn2" :class="{ pri: slaFilter === \'overdue\' }" @click="switchSlaFilter(\'overdue\')">已超时</button>',
            '    <button class="btn2" :class="{ pri: slaFilter === \'dueSoon\' }" @click="switchSlaFilter(\'dueSoon\')">即将超时</button>',
            '    <span v-if="batchMode" style="margin-left:auto;display:flex;align-items:center;gap:8px;font-size:12px;color:var(--tk-text3)">',
            '      <span>已选 <b style="color:var(--tk-text)">{{ selected.length }}</b> 条</span>',
            '      <button class="btn2 pri" :disabled="!selected.length" @click="openBatchAssign"><span>批量派发</span></button>',
            '    </span>',
            '  </div>',

            '  <div class="lcard" style="flex:1;min-height:0">',
            '    <div class="cap">工单列表<span class="cnt" v-if="Number(total) > 0">共 {{ total }} 条</span>',
            '      <span class="r" v-if="batchMode" style="font-size:11px"><span class="op" @click="toggleSelectAll">{{ allSelected ? "取消全选" : "全选本页" }}</span></span>',
            '    </div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto">',
            '      <table class="pk">',
            '        <colgroup>',
            '          <col v-if="batchMode" style="width:34px">',
            '          <col style="width:190px"><col><col style="width:100px"><col style="width:80px"><col style="width:150px"><col style="width:120px"><col style="width:170px"><col style="width:220px">',
            '        </colgroup>',
            '        <thead><tr>',
            '          <th v-if="batchMode"></th><th>工单号</th><th>标题</th><th>状态</th><th>优先级</th><th>SLA</th><th>处理人</th><th>创建时间</th><th class="r">操作</th>',
            '        </tr></thead>',
            '        <tbody>',
            '          <tr v-if="!rows.length && !loading"><td :colspan="batchMode ? 9 : 8" style="text-align:center;color:var(--tk-muted);padding:32px 0">暂无工单</td></tr>',
            '          <tr v-if="loading"><td :colspan="batchMode ? 9 : 8" style="text-align:center;color:var(--tk-muted);padding:32px 0">加载中…</td></tr>',
            '          <tr v-for="row in rows" :key="row.orderId">',
            '            <td v-if="batchMode"><input type="checkbox" :checked="selected.indexOf(row.orderId) >= 0" @change="toggleSelect(row)" /></td>',
            '            <td style="color:var(--tk-muted)">{{ row.orderNo }}</td>',
            '            <td style="color:var(--tk-text)">{{ row.title }}</td>',
            '            <td><span class="bdg" :class="statusTone(row.status)">{{ statusLabel(row.status) }}</span></td>',
            '            <td><span class="bdg" :class="row.priority === \'URGENT\' ? \'r\' : \'n2\'">{{ row.priority === "URGENT" ? "紧急" : "普通" }}</span></td>',
            '            <td>',
            '              <div style="display:flex;flex-direction:column;gap:2px">',
            '                <span style="font-size:11px;color:var(--tk-text3)">截止 {{ fmt(row.dueAt) }}</span>',
            '                <span class="bdg" :class="slaTone(row)">{{ slaText(row) }}</span>',
            '              </div>',
            '            </td>',
            '            <td>{{ row.assigneeName || "-" }}</td>',
            '            <td style="color:var(--tk-text3)">{{ fmt(row.createdAt) }}</td>',
            '            <td class="r">',
            '              <span class="op" @click="openDetail(row)">详情</span>',
            '              <template v-if="row.status === \'PENDING_DISPATCH\'"> · <span class="op" @click="openAssign(row)">派发</span></template>',
            '              <template v-if="canReassign(row.status)"> · <span class="op" @click="openReassign(row)">改派</span></template>',
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

            // 弹层: 工单详情（SLA + 照片 + 时间线）
            '  <div class="pmask" :class="{ show: detailVisible }" @click.self="detailVisible = false">',
            '    <div class="pcard w640" v-if="detail">',
            '      <div class="ph"><b>工单详情</b><span class="no">{{ detail.orderNo }}</span><span class="sp"></span><span class="x" @click="detailVisible = false">✕</span></div>',
            '      <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">',
            '        <span class="badge" :class="detailTone(detail.status)">{{ statusLabel(detail.status) }}</span>',
            '        <span class="bdg" :class="detail.priority === \'URGENT\' ? \'r\' : \'n2\'">{{ detail.priority === "URGENT" ? "紧急" : "普通" }}</span>',
            '        <span class="bdg" :class="slaTone(detail)">{{ slaText(detail) }}</span>',
            '        <span style="font-size:12px;color:var(--tk-text3)">创建于 {{ fmt(detail.createdAt) }}</span>',
            '      </div>',
            '      <div class="kv">',
            '        <div class="row">标题　　　{{ detail.title }}</div>',
            '        <div class="row">申请编号　{{ detail.requestNo || "-" }}</div>',
            '        <div class="row">申请人　　{{ detail.requesterName || "-" }} {{ detail.requesterPhone ? "· " + detail.requesterPhone : "" }}</div>',
            '        <div class="row">报修地址　{{ detail.repairAddress || "-" }}</div>',
            '        <div class="row">处理人　　{{ detail.assigneeName || "-" }} {{ detail.assigneePhone ? "· " + detail.assigneePhone : "" }}</div>',
            '        <div class="row">SLA　　　 承诺 {{ detail.slaHours || "-" }} 小时 · 截止 {{ fmt(detail.dueAt) }}</div>',
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
            '      <template v-if="detail.arrivalPhotoUrl || (detail.completionPhotoUrls || []).length">',
            '        <div class="plbl">工单照片</div>',
            '        <div style="display:flex;gap:8px;flex-wrap:wrap">',
            '          <div v-if="detail.arrivalPhotoUrl" style="display:flex;flex-direction:column;gap:4px">',
            '            <img :src="detail.arrivalPhotoUrl" alt="现场照片" style="width:120px;height:90px;object-fit:cover;border-radius:6px;border:1px solid var(--tk-stroke)" />',
            '            <span style="font-size:11px;color:var(--tk-text3)">现场照片</span>',
            '          </div>',
            '          <div v-for="(url, idx) in (detail.completionPhotoUrls || [])" :key="url" style="display:flex;flex-direction:column;gap:4px">',
            '            <img :src="url" alt="完工照片" style="width:120px;height:90px;object-fit:cover;border-radius:6px;border:1px solid var(--tk-stroke)" />',
            '            <span style="font-size:11px;color:var(--tk-text3)">完工照片 {{ idx + 1 }}</span>',
            '          </div>',
            '        </div>',
            '      </template>',
            '      <template v-if="(detail.timeline || []).length">',
            '        <div class="plbl">处理时间线</div>',
            '        <div style="display:flex;flex-direction:column;gap:6px;max-height:200px;overflow:auto">',
            '          <div v-for="(log, idx) in detail.timeline" :key="idx" style="display:flex;gap:8px;font-size:12px;color:var(--tk-text3)">',
            '            <span style="flex:none;color:var(--tk-muted)">{{ fmt(log.createdAt) }}</span>',
            '            <b style="flex:none;color:var(--tk-text)">{{ log.action }}</b>',
            '            <span style="flex:none">{{ log.operatorName }}</span>',
            '            <span style="color:var(--tk-text2)">{{ log.comment || "" }}</span>',
            '          </div>',
            '        </div>',
            '      </template>',
            '      <div class="pbtn"><span class="sp"></span>',
            '        <button class="c ghost" @click="detailVisible = false">关闭</button>',
            '        <button v-if="detail.status === \'PENDING_DISPATCH\'" class="c blue" @click="detailVisible = false; openAssign(detail)">派发</button>',
            '      </div>',
            '    </div>',
            '  </div>',

            // 弹层: 派发 / 批量派发
            '  <div class="pmask" :class="{ show: assignVisible }" @click.self="assignVisible = false">',
            '    <div class="pcard">',
            '      <div class="ph"><b>{{ assignMode === "batch" ? "批量派发" : "派发工单" }}</b><span class="no" v-if="assignMode === \'batch\'">已选 {{ selected.length }} 条</span><span class="sp"></span><span class="x" @click="assignVisible = false">✕</span></div>',
            '      <div class="fld"><label>服务人员 <em>*</em></label>',
            '        <div class="pin"><select v-model="assigneeId" :disabled="workersLoading" style="width:100%">',
            '          <option :value="null" disabled>{{ workersLoading ? "加载中…" : "选择服务人员（按负载从低到高排序）" }}</option>',
            '          <option v-for="worker in workers" :key="worker.userId" :value="worker.userId">',
            '            {{ worker.displayName + (worker.activeCount != null ? "（进行中 " + worker.activeCount + "）" : "") }}',
            '          </option>',
            '        </select></div>',
            '      </div>',
            '      <div class="fld"><label>派单备注</label><textarea class="pta" v-model="assignComment" maxlength="512" placeholder="可空"></textarea></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="assignVisible = false">取消</button>',
            '        <button class="c blue" :disabled="assignSubmitting" @click="submitAssign">{{ assignSubmitting ? "提交中…" : "确认派发" }}</button></div>',
            '    </div>',
            '  </div>',

            // 弹层: 改派
            '  <div class="pmask" :class="{ show: reassignVisible }" @click.self="reassignVisible = false">',
            '    <div class="pcard">',
            '      <div class="ph"><b>改派工单</b><span class="no">{{ reassignOrderNo }}</span><span class="sp"></span><span class="x" @click="reassignVisible = false">✕</span></div>',
            '      <div class="fld"><label>新处理人 <em>*</em></label>',
            '        <div class="pin"><select v-model="reassignAssigneeId" :disabled="workersLoading" style="width:100%">',
            '          <option :value="null" disabled>{{ workersLoading ? "加载中…" : "选择新处理人" }}</option>',
            '          <option v-for="worker in workers" :key="worker.userId" :value="worker.userId">',
            '            {{ worker.displayName + (worker.activeCount != null ? "（进行中 " + worker.activeCount + "）" : "") }}',
            '          </option>',
            '        </select></div>',
            '      </div>',
            '      <div class="fld"><label>改派说明</label><textarea class="pta" v-model="reassignComment" maxlength="512" placeholder="将记录到申请时间线"></textarea></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="reassignVisible = false">取消</button>',
            '        <button class="c blue" :disabled="reassignSubmitting" @click="submitReassign">{{ reassignSubmitting ? "提交中…" : "确认改派" }}</button></div>',
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
            var slaFilter = Vue.ref('');
            var rows = Vue.ref([]);
            var total = Vue.ref(0);
            var pageNum = Vue.ref(1);
            var pageSize = Vue.ref(10);
            var loading = Vue.ref(false);
            var stats = Vue.ref({});
            var workload = Vue.ref([]);

            var batchMode = Vue.ref(false);
            var selected = Vue.ref([]);

            var detail = Vue.ref(null);
            var detailVisible = Vue.ref(false);

            var assignVisible = Vue.ref(false);
            var assignMode = Vue.ref('single');
            var assignOrderId = Vue.ref(null);
            var assigneeId = Vue.ref(null);
            var assignComment = Vue.ref('');
            var workers = Vue.ref([]);
            var workersLoading = Vue.ref(false);
            var assignSubmitting = Vue.ref(false);

            var reassignVisible = Vue.ref(false);
            var reassignOrderId = Vue.ref(null);
            var reassignOrderNo = Vue.ref('');
            var reassignAssigneeId = Vue.ref(null);
            var reassignComment = Vue.ref('');
            var reassignSubmitting = Vue.ref(false);

            var rejectVisible = Vue.ref(false);
            var rejectOrderId = Vue.ref(null);
            var rejectReason = Vue.ref('');
            var rejectSubmitting = Vue.ref(false);

            function load() {
                loading.value = true;
                var url = '/api/admin/work-orders?pageNum=' + pageNum.value + '&pageSize=' + pageSize.value;
                if (status.value) url += '&status=' + status.value;
                if (slaFilter.value === 'overdue') url += '&overdue=true';
                if (slaFilter.value === 'dueSoon') url += '&dueSoon=true';
                api.get(url)
                    .then(function (page) {
                        rows.value = page.records || [];
                        total.value = page.total || 0;
                        var visibleIds = rows.value.map(function (item) { return item.orderId; });
                        selected.value = selected.value.filter(function (id) { return visibleIds.indexOf(id) >= 0; });
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { loading.value = false; });
            }

            function loadStats() {
                api.get('/api/admin/work-orders/stats')
                    .then(function (data) { stats.value = data || {}; })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }

            function loadWorkload() {
                api.get('/api/admin/work-orders/workload')
                    .then(function (list) { workload.value = list || []; })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }

            function refreshAll() {
                load();
                loadStats();
                loadWorkload();
            }

            function reload() {
                pageNum.value = 1;
                load();
            }

            function switchStatus(value) {
                status.value = value;
                reload();
            }

            function switchSlaFilter(value) {
                slaFilter.value = value;
                reload();
            }

            function toggleBatchMode() {
                batchMode.value = !batchMode.value;
                selected.value = [];
            }

            function toggleSelect(row) {
                var ids = selected.value.slice();
                var index = ids.indexOf(row.orderId);
                if (index >= 0) {
                    ids.splice(index, 1);
                } else {
                    ids.push(row.orderId);
                }
                selected.value = ids;
            }

            var allSelected = Vue.computed(function () {
                return rows.value.length > 0 && rows.value.every(function (row) {
                    return selected.value.indexOf(row.orderId) >= 0;
                });
            });

            function toggleSelectAll() {
                if (allSelected.value) {
                    selected.value = [];
                } else {
                    selected.value = rows.value.map(function (row) { return row.orderId; });
                }
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
                api.get('/api/admin/work-orders/workload')
                    .then(function (list) {
                        workers.value = list || [];
                        if (!workers.value.length) {
                            El.ElMessage.warning('暂无在岗服务人员，请先在用户管理中授予服务人员角色');
                        }
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { workersLoading.value = false; });
            }

            function openAssign(row) {
                assignMode.value = 'single';
                assignOrderId.value = row.orderId;
                assigneeId.value = null;
                assignComment.value = '';
                assignVisible.value = true;
                loadWorkers();
            }

            function openBatchAssign() {
                if (!selected.value.length) {
                    El.ElMessage.warning('请先勾选待派发工单');
                    return;
                }
                assignMode.value = 'batch';
                assignOrderId.value = null;
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
                var promise;
                if (assignMode.value === 'batch') {
                    promise = api.post('/api/admin/work-orders/batch-assign',
                        { orderIds: selected.value.slice(), assigneeId: assigneeId.value, comment: assignComment.value || null });
                } else {
                    promise = api.post('/api/admin/work-orders/' + assignOrderId.value + '/assign',
                        { assigneeId: assigneeId.value, comment: assignComment.value || null });
                }
                promise.then(function (result) {
                    if (assignMode.value === 'batch' && result) {
                        var message = '批量派发完成：成功 ' + result.success + ' / ' + result.requested;
                        if (result.failures && result.failures.length) {
                            El.ElMessage.warning(message + '，失败 ' + result.failures.length + ' 条');
                        } else {
                            El.ElMessage.success(message);
                        }
                        selected.value = [];
                    } else {
                        El.ElMessage.success('派发成功');
                    }
                    assignVisible.value = false;
                    refreshAll();
                })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { assignSubmitting.value = false; });
            }

            function openReassign(row) {
                reassignOrderId.value = row.orderId;
                reassignOrderNo.value = row.orderNo || '';
                reassignAssigneeId.value = null;
                reassignComment.value = '';
                reassignVisible.value = true;
                loadWorkers();
            }

            function submitReassign() {
                if (!reassignAssigneeId.value) {
                    El.ElMessage.warning('请选择新处理人');
                    return;
                }
                reassignSubmitting.value = true;
                api.post('/api/admin/work-orders/' + reassignOrderId.value + '/reassign',
                    { assigneeId: reassignAssigneeId.value, comment: reassignComment.value || null })
                    .then(function () {
                        El.ElMessage.success('改派成功');
                        reassignVisible.value = false;
                        refreshAll();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { reassignSubmitting.value = false; });
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
                        refreshAll();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { rejectSubmitting.value = false; });
            }

            function canReject(statusValue) {
                return statusValue === 'PENDING_DISPATCH' || statusValue === 'ASSIGNED';
            }

            function canReassign(statusValue) {
                return statusValue === 'ASSIGNED' || statusValue === 'PROCESSING' || statusValue === 'ONSITE_PHOTO_RECORDED';
            }

            function slaTone(row) {
                if (row.overdue) return 'r';
                if (row.dueSoon) return 'a';
                if (row.remainingHours != null) return 'g';
                return 'n2';
            }

            function slaText(row) {
                if (row.overdue) {
                    return '超时 ' + (row.overdueHours == null ? '-' : row.overdueHours) + 'h';
                }
                if (row.remainingHours != null) {
                    return '剩余 ' + row.remainingHours + 'h';
                }
                return row.completedAt ? '已完成' : '-';
            }

            function statusTone(value) {
                if (value === 'PENDING_CONFIRMATION') return 'a';
                if (value === 'ONSITE_PHOTO_RECORDED') return 'i';
                if (value === 'PROCESSING') return 'i';
                if (value === 'COMPLETED') return 'g';
                if (value === 'REJECTED' || value === 'CANCELLED') return 'r';
                return 'n2';
            }

            function detailTone(value) {
                return statusTone(value);
            }

            function formEntries(formData) {
                if (!formData) return [];
                var entries = [];
                Object.keys(formData).forEach(function (key) {
                    var value = formData[key];
                    if (value == null || value === '') return;
                    if (Array.isArray(value)) {
                        entries.push([key, value.join('、')]);
                    } else if (typeof value === 'object') {
                        entries.push([key, JSON.stringify(value)]);
                    } else {
                        entries.push([key, String(value)]);
                    }
                });
                return entries;
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
            function goPage(p) {
                if (p >= 1 && p <= totalPages.value && p !== pageNum.value) {
                    pageNum.value = p;
                    load();
                }
            }
            function prevPage() { goPage(pageNum.value - 1); }
            function nextPage() { goPage(pageNum.value + 1); }

            refreshAll();

            return {
                statusOptions: STATUS_OPTIONS, status: status, slaFilter: slaFilter, rows: rows, total: total,
                pageNum: pageNum, pageSize: pageSize, loading: loading, totalPages: totalPages,
                pageNumbers: pageNumbers, goPage: goPage, prevPage: prevPage, nextPage: nextPage,
                stats: stats, workload: workload, batchMode: batchMode, selected: selected, allSelected: allSelected,
                load: load, reload: reload, refreshAll: refreshAll,
                switchStatus: switchStatus, switchSlaFilter: switchSlaFilter, toggleBatchMode: toggleBatchMode,
                toggleSelect: toggleSelect, toggleSelectAll: toggleSelectAll,
                detail: detail, detailVisible: detailVisible,
                assignVisible: assignVisible, assignMode: assignMode, assigneeId: assigneeId, assignComment: assignComment,
                workers: workers, workersLoading: workersLoading, assignSubmitting: assignSubmitting,
                reassignVisible: reassignVisible, reassignOrderNo: reassignOrderNo,
                reassignAssigneeId: reassignAssigneeId, reassignComment: reassignComment,
                reassignSubmitting: reassignSubmitting,
                rejectVisible: rejectVisible, rejectReason: rejectReason, rejectSubmitting: rejectSubmitting,
                openDetail: openDetail, openAssign: openAssign, openBatchAssign: openBatchAssign,
                submitAssign: submitAssign, openReassign: openReassign, submitReassign: submitReassign,
                openReject: openReject, submitReject: submitReject,
                canReject: canReject, canReassign: canReassign, slaTone: slaTone, slaText: slaText,
                statusLabel: statusLabel, statusTag: function (value) { return STATUS_TAG[value] || 'info'; },
                statusTone: statusTone, detailTone: detailTone, formEntries: formEntries,
                fmt: api.fmtDateTime
            };
        }
    };
})();
