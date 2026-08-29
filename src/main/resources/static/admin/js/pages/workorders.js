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
            '<div class="page-card">',
            '  <div class="toolbar">',
            '    <el-radio-group v-model="status" @change="reload">',
            '      <el-radio-button v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</el-radio-button>',
            '    </el-radio-group>',
            '    <div class="grow"></div>',
            '    <el-button :icon="RefreshIcon" @click="reload">刷新</el-button>',
            '  </div>',
            '  <el-table :data="rows" v-loading="loading" stripe>',
            '    <el-table-column prop="orderNo" label="工单号" width="200" show-overflow-tooltip />',
            '    <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />',
            '    <el-table-column label="状态" width="100">',
            '      <template #default="scope">',
            '        <el-tag :type="statusTag(scope.row.status)" effect="light">{{ statusLabel(scope.row.status) }}</el-tag>',
            '      </template>',
            '    </el-table-column>',
            '    <el-table-column prop="assigneeName" label="处理人" width="120">',
            '      <template #default="scope">{{ scope.row.assigneeName || "-" }}</template>',
            '    </el-table-column>',
            '    <el-table-column label="创建时间" width="150">',
            '      <template #default="scope">{{ fmt(scope.row.createdAt) }}</template>',
            '    </el-table-column>',
            '    <el-table-column label="操作" width="210" fixed="right">',
            '      <template #default="scope">',
            '        <el-button link type="primary" @click="openDetail(scope.row)">详情</el-button>',
            '        <el-button v-if="scope.row.status === \'PENDING_DISPATCH\'" link type="success" @click="openAssign(scope.row)">派发</el-button>',
            '        <el-button v-if="canReject(scope.row.status)" link type="danger" @click="openReject(scope.row)">驳回</el-button>',
            '      </template>',
            '    </el-table-column>',
            '  </el-table>',
            '  <div class="pager">',
            '    <el-pagination layout="total, prev, pager, next, sizes" :total="total"',
            '                   v-model:current-page="pageNum" v-model:page-size="pageSize"',
            '                   :page-sizes="[10, 20, 50]" @current-change="load" @size-change="reload" />',
            '  </div>',

            '  <el-dialog v-model="detailVisible" title="工单详情" width="640px">',
            '    <el-descriptions v-if="detail" :column="2" border>',
            '      <el-descriptions-item label="工单号">{{ detail.orderNo }}</el-descriptions-item>',
            '      <el-descriptions-item label="申请编号">{{ detail.requestNo || "-" }}</el-descriptions-item>',
            '      <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>',
            '      <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>',
            '      <el-descriptions-item label="处理人">{{ detail.assigneeName || "-" }}</el-descriptions-item>',
            '      <el-descriptions-item label="申请人">{{ detail.requesterName || "-" }}</el-descriptions-item>',
            '      <el-descriptions-item label="创建时间">{{ fmt(detail.createdAt) }}</el-descriptions-item>',
            '      <el-descriptions-item label="派单时间">{{ fmt(detail.assignedAt) }}</el-descriptions-item>',
            '      <el-descriptions-item label="开始时间">{{ fmt(detail.startedAt) }}</el-descriptions-item>',
            '      <el-descriptions-item label="完成时间">{{ fmt(detail.completedAt) }}</el-descriptions-item>',
            '      <el-descriptions-item v-if="detail.completeRemark" label="完成说明" :span="2">{{ detail.completeRemark }}</el-descriptions-item>',
            '    </el-descriptions>',
            '    <template v-if="detail && formEntries(detail.formData).length">',
            '      <h4 style="margin:16px 0 8px">申请表单内容</h4>',
            '      <el-descriptions :column="1" border>',
            '        <el-descriptions-item v-for="entry in formEntries(detail.formData)" :key="entry[0]" :label="entry[0]">',
            '          {{ entry[1] }}',
            '        </el-descriptions-item>',
            '      </el-descriptions>',
            '    </template>',
            '  </el-dialog>',

            '  <el-dialog v-model="assignVisible" title="派发工单" width="520px">',
            '    <el-form label-width="90px">',
            '      <el-form-item label="服务人员" required>',
            '        <el-select v-model="assigneeId" filterable placeholder="搜索昵称/用户名/手机号" style="width:100%"',
            '                   :loading="workersLoading">',
            '          <el-option v-for="worker in workers" :key="worker.userId" :value="worker.userId"',
            '                     :label="worker.displayName + (worker.phone ? \'（\' + worker.phone + \'）\' : \'\')" />',
            '        </el-select>',
            '      </el-form-item>',
            '      <el-form-item label="派单备注">',
            '        <el-input v-model="assignComment" type="textarea" :rows="2" maxlength="512" show-word-limit placeholder="可空" />',
            '      </el-form-item>',
            '    </el-form>',
            '    <template #footer>',
            '      <el-button @click="assignVisible = false">取消</el-button>',
            '      <el-button type="primary" :loading="assignSubmitting" @click="submitAssign">确认派发</el-button>',
            '    </template>',
            '  </el-dialog>',

            '  <el-dialog v-model="rejectVisible" title="驳回工单" width="480px">',
            '    <el-input v-model="rejectReason" type="textarea" :rows="3" maxlength="512" show-word-limit',
            '              placeholder="请填写驳回原因（必填）" />',
            '    <template #footer>',
            '      <el-button @click="rejectVisible = false">取消</el-button>',
            '      <el-button type="danger" :loading="rejectSubmitting" @click="submitReject">确认驳回</el-button>',
            '    </template>',
            '  </el-dialog>',
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
                detail: detail, detailVisible: detailVisible,
                assignVisible: assignVisible, assigneeId: assigneeId, assignComment: assignComment,
                workers: workers, workersLoading: workersLoading, assignSubmitting: assignSubmitting,
                rejectVisible: rejectVisible, rejectReason: rejectReason, rejectSubmitting: rejectSubmitting,
                load: load, reload: reload,
                openDetail: openDetail, openAssign: openAssign, submitAssign: submitAssign,
                openReject: openReject, submitReject: submitReject,
                canReject: canReject,
                statusLabel: statusLabel, statusTag: function (value) { return STATUS_TAG[value] || 'info'; },
                formEntries: formEntries,
                fmt: api.fmtDateTime,
                RefreshIcon: ElementPlusIconsVue.Refresh
            };
        }
    };
})();
