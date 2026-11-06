/* 服务申请（管理端审批）：真实接口 /api/admin/service-requests。
   状态页签 + 分页 + 审批详情弹层（申请人/表单/材料图片/工单 SLA/评价/时间线 + 通过/驳回），画布规范。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    var STATUS_TEXT = {
        SUBMITTED: '待受理', PROCESSING: '处理中', COMPLETED: '已完成',
        REJECTED: '已驳回', CANCELLED: '已取消'
    };
    var STATUS_TONE = {
        SUBMITTED: 'a', PROCESSING: 'i', COMPLETED: 'g', REJECTED: 'r', CANCELLED: 'n2'
    };
    var TABS = [
        { value: '', label: '全部' },
        { value: 'SUBMITTED', label: '待受理' },
        { value: 'PROCESSING', label: '处理中' },
        { value: 'COMPLETED', label: '已完成' },
        { value: 'REJECTED', label: '已驳回' }
    ];

    window.AdminPages.RequestsPage = {
        name: 'RequestsPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="page-h">',
            '    <div><h1>服务申请</h1><div class="sub">按流程类型办理 · 请假进入独立审批，报修进入维修调度，其他事项人工受理</div></div>',
            '    <div class="r"><button class="btn2" :disabled="exporting" @click="exportCsv">{{ exporting ? "导出中…" : "导出 CSV" }}</button><button class="btn2" @click="load">刷新</button></div>',
            '  </div>',

            '  <div class="tabs">',
            '    <span v-for="t in tabs" :key="t.value" class="tab" :class="{ on: status === t.value }" @click="switchStatus(t.value)">{{ t.label }}</span>',
            '  </div>',

            '  <div class="lcard" style="flex:1;min-height:0">',
            '    <div class="cap">申请记录<span class="cnt" v-if="Number(total) > 0">共 {{ total }} 条</span></div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto">',
            '      <table class="pk">',
            '        <colgroup><col style="width:150px"><col style="width:200px"><col><col style="width:110px"><col style="width:160px"><col style="width:110px"><col style="width:120px"></colgroup>',
            '        <thead><tr><th>申请单号</th><th>服务 · 事项</th><th>标题</th><th>状态</th><th>提交时间</th><th>完成时间</th><th class="r">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-if="!rows.length && !loading"><td colspan="7" style="text-align:center;color:var(--tk-muted);padding:32px 0">暂无申请</td></tr>',
            '          <tr v-if="loading"><td colspan="7" style="text-align:center;color:var(--tk-muted);padding:32px 0">加载中…</td></tr>',
            '          <tr v-for="row in rows" :key="row.requestId" style="cursor:pointer" @click="openDetail(row)">',
            '            <td style="color:var(--tk-muted)">{{ row.requestNo }}</td>',
            '            <td style="color:var(--tk-text)">{{ row.itemName }}<div style="font-size:11px;color:var(--tk-text3)">{{ flowLabel(row.flowType) }}</div></td>',
            '            <td>{{ row.title }}</td>',
            '            <td><span class="bdg" :class="statusTone(row.status)">{{ row.statusLabel || statusText(row.status) }}</span></td>',
            '            <td style="color:var(--tk-text3)">{{ fmt(row.submittedAt) }}</td>',
            '            <td style="color:var(--tk-text3)">{{ fmt(row.completedAt) }}</td>',
            '            <td class="r" @click.stop>',
            '              <span class="op" @click="openDetail(row)">详情</span>',
            '              <template v-if="canOperate(row)"> · <span class="op" @click="openDetail(row)">办理</span></template>',
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

            // 弹层: 审批详情
            '  <div class="pmask" :class="{ show: detailVisible }" @click.self="detailVisible = false">',
            '    <div class="pcard w720" v-if="detail" style="max-height:88vh;overflow:auto">',
            '      <div class="ph"><b>审批详情</b><span class="no">{{ detail.requestNo }}</span><span class="sp"></span><span class="x" @click="detailVisible = false">✕</span></div>',

            '      <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">',
            '        <span class="bdg" :class="statusTone(detail.status)">{{ detail.statusLabel || statusText(detail.status) }}</span>',
            '        <b style="font-size:13px;color:var(--tk-text)">{{ detail.title }}</b>',
            '        <span style="font-size:12px;color:var(--tk-text3)">{{ detail.itemName }}<template v-if="detail.categoryName"> · {{ detail.categoryName }}</template></span>',
            '      </div>',

            '      <div class="plbl">申请人</div>',
            '      <div class="kv">',
            '        <div class="row">姓名　　{{ detail.requester ? (detail.requester.displayName || "-") : "-" }}</div>',
            '        <div class="row">手机号　{{ detail.requester && detail.requester.phoneMasked ? detail.requester.phoneMasked : "-" }}</div>',
            '        <div class="row">组织　　{{ detail.requester && detail.requester.organizationName ? detail.requester.organizationName : "-" }}</div>',
            '        <div class="row">提交时间　{{ fmt(detail.submittedAt) }}<template v-if="detail.completedAt"> · 办结 {{ fmt(detail.completedAt) }}</template></div>',
            '        <div class="row" v-if="detail.handlerOrganizationName">受理组织　{{ detail.handlerOrganizationName }}</div>',
            '        <div class="row" v-if="detail.slaHours">承诺时限　{{ detail.slaHours }} 小时</div>',
            '        <div class="row" v-if="detail.rejectReason" style="color:var(--tk-bad)">驳回原因　{{ detail.rejectReason }}</div>',
            '        <div class="row" v-if="detail.cancelReason" style="color:var(--tk-bad)">撤销原因　{{ detail.cancelReason }}</div>',
            '        <div class="row" v-if="detail.approvalComment">办理意见　{{ detail.approvalComment }}</div>',
            '      </div>',

            '      <template v-if="formEntries.length">',
            '        <div class="plbl">申请表单</div>',
            '        <div class="kv">',
            '          <div class="row" v-for="entry in formEntries" :key="entry.key"><span style="color:var(--tk-text3)">{{ entry.label }}　</span>{{ entry.display }}</div>',
            '        </div>',
            '      </template>',

            '      <template v-if="(detail.materials || []).length">',
            '        <div class="plbl">材料图片</div>',
            '        <div style="display:flex;flex-direction:column;gap:8px">',
            '          <div v-for="material in detail.materials" :key="material.fieldKey">',
            '            <div style="font-size:11px;color:var(--tk-text3);margin-bottom:4px">{{ material.label }}</div>',
            '            <div style="display:flex;gap:8px;flex-wrap:wrap">',
            '              <a v-for="url in material.urls" :key="url" :href="url" target="_blank" rel="noopener noreferrer">',
            '                <img :src="url" :alt="material.label" style="width:110px;height:82px;object-fit:cover;border-radius:6px;border:1px solid var(--tk-stroke)" />',
            '              </a>',
            '            </div>',
            '          </div>',
            '        </div>',
            '      </template>',

            '      <template v-if="detail.flowType === \'REPAIR\' && (detail.attachments || []).length">',
            '        <div class="plbl">工单附件</div>',
            '        <div style="display:flex;gap:8px;flex-wrap:wrap">',
            '          <div v-for="(file, idx) in detail.attachments" :key="idx" style="display:flex;flex-direction:column;gap:4px">',
            '            <a :href="file.url" target="_blank" rel="noopener noreferrer">',
            '              <img :src="file.url" :alt="file.label" style="width:110px;height:82px;object-fit:cover;border-radius:6px;border:1px solid var(--tk-stroke)" />',
            '            </a>',
            '            <span style="font-size:11px;color:var(--tk-text3)">{{ file.label }}</span>',
            '          </div>',
            '        </div>',
            '      </template>',

            '      <template v-if="detail.flowType === \'REPAIR\' && detail.workOrder">',
            '        <div class="plbl">关联工单</div>',
            '        <div class="kv">',
            '          <div class="row">工单号　　{{ detail.workOrder.orderNo || "-" }}</div>',
            '          <div class="row">状态　　　{{ detail.workOrder.status || "-" }}<template v-if="detail.workOrder.assigneeName"> · 处理人 {{ detail.workOrder.assigneeName }}</template></div>',
            '          <div class="row">SLA 截止　{{ fmt(detail.workOrder.dueAt) }}',
            '            <span class="bdg" :class="slaTone(detail.workOrder)">{{ slaText(detail.workOrder) }}</span></div>',
            '        </div>',
            '      </template>',

            '      <template v-if="detail.evaluation">',
            '        <div class="plbl">服务评价</div>',
            '        <div class="kv">',
            '          <div class="row">评分　　　{{ detail.evaluation.rating }} 星</div>',
            '          <div class="row" v-if="detail.evaluation.tags">标签　　　{{ detail.evaluation.tags }}</div>',
            '          <div class="row" v-if="detail.evaluation.comment">评语　　　{{ detail.evaluation.comment }}</div>',
            '          <div class="row" v-if="detail.evaluation.reply">商家回复　{{ detail.evaluation.reply }}</div>',
            '        </div>',
            '      </template>',

            '      <template v-if="(detail.timeline || []).length">',
            '        <div class="plbl">处理时间线</div>',
            '        <div style="display:flex;flex-direction:column;gap:6px;max-height:220px;overflow:auto">',
            '          <div v-for="(log, idx) in detail.timeline" :key="idx" style="display:flex;gap:8px;font-size:12px;color:var(--tk-text3)">',
            '            <span style="flex:none;color:var(--tk-muted)">{{ fmt(log.createdAt) }}</span>',
            '            <b style="flex:none;color:var(--tk-text)">{{ log.action }}</b>',
            '            <span style="flex:none">{{ log.operatorName }}</span>',
            '            <span style="color:var(--tk-text2)">{{ log.comment || "" }}</span>',
            '          </div>',
            '        </div>',
            '      </template>',

            '      <template v-if="(detail.availableActions || []).length">',
            '        <div class="plbl">办理操作</div>',
            '        <div class="radio" v-if="detail.availableActions.indexOf(\'APPROVE\') >= 0" :class="{ on: action === \'APPROVE\' }" @click="action = \'APPROVE\'"><span class="dot"></span>受理（转人工处理中）</div>',
            '        <div class="radio" v-if="detail.availableActions.indexOf(\'REJECT\') >= 0" :class="{ on: action === \'REJECT\' }" @click="action = \'REJECT\'"><span class="dot"></span>驳回（需填写驳回原因）</div>',
            '        <div class="radio" v-if="detail.availableActions.indexOf(\'COMPLETE\') >= 0" :class="{ on: action === \'COMPLETE\' }" @click="action = \'COMPLETE\'"><span class="dot"></span>人工办结（记录实际办理结果）</div>',
            '        <div class="fld"><label>审批意见</label><textarea class="pta" v-model="comment" maxlength="512" placeholder="将记录到申请时间线"></textarea></div>',
            '        <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="detailVisible = false">取消</button>',
            '          <button class="c" :class="action === \'REJECT\' ? \'red\' : \'green\'" :disabled="submitting" @click="submit">{{ submitting ? "提交中…" : (action === \'APPROVE\' ? "确认受理" : action === \'COMPLETE\' ? "确认办结" : "确认驳回") }}</button></div>',
            '      </template>',
            '      <div class="pbtn" v-else><span class="sp"></span><button class="c ghost" @click="detailVisible = false">关闭</button></div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var status = Vue.ref('');
            var exporting = Vue.ref(false);
            var rows = Vue.ref([]);
            var total = Vue.ref(0);
            var pageNum = Vue.ref(1);
            var pageSize = Vue.ref(10);
            var loading = Vue.ref(false);

            var detail = Vue.ref(null);
            var detailVisible = Vue.ref(false);
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

            function openDetail(row) {
                if (row.flowType === 'LEAVE_APPROVAL') { openLeave(row); return; }
                detail.value = null;
                detailVisible.value = true;
                api.get('/api/admin/service-requests/' + row.requestId)
                    .then(function (data) {
                        if (data.flowType === 'LEAVE_APPROVAL') { detailVisible.value = false; openLeave(data); return; }
                        detail.value = data;
                        var actions = data.availableActions || [];
                        action.value = actions.indexOf('APPROVE') >= 0 ? 'APPROVE' : actions.indexOf('COMPLETE') >= 0 ? 'COMPLETE' : 'REJECT';
                        comment.value = '';
                    })
                    .catch(function (error) {
                        detailVisible.value = false;
                        El.ElMessage.error(error.message);
                    });
            }

            function submit() {
                if (!detail.value || (detail.value.availableActions || []).indexOf(action.value) < 0) return;
                if (action.value === 'REJECT' && !comment.value.trim()) {
                    El.ElMessage.warning('驳回需填写原因');
                    return;
                }
                submitting.value = true;
                var body = { comment: comment.value.trim() || null };
                if (action.value !== 'COMPLETE') body.action = action.value;
                api.post('/api/admin/service-requests/' + detail.value.requestId + (action.value === 'COMPLETE' ? '/complete' : '/review'), body)
                    .then(function () {
                        El.ElMessage.success(action.value === 'APPROVE' ? '已受理' : action.value === 'COMPLETE' ? '已办结' : '已驳回');
                        detailVisible.value = false;
                        load();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { submitting.value = false; });
            }

            var formEntries = Vue.computed(function () {
                var data = detail.value && detail.value.formData;
                if (!data) return [];
                var schema = detail.value.formSchema;
                var entries = [];
                var rendered = {};
                if (schema && schema.length) {
                    schema.forEach(function (field) {
                        var key = field.key;
                        if (!key || rendered[key]) return;
                        rendered[key] = true;
                        var value = data[key];
                        if (value == null || value === '') return;
                        var type = field.type || 'input';
                        if (type === 'image') {
                            if (Array.isArray(value) && value.length) {
                                entries.push({ key: key, label: field.label || key, display: value.length + ' 张图片（见材料图片）' });
                            }
                            return;
                        }
                        entries.push({ key: key, label: field.label || key, display: displayValue(value) });
                    });
                }
                Object.keys(data).forEach(function (key) {
                    if (rendered[key]) return;
                    var value = data[key];
                    if (value == null || value === '') return;
                    if (Array.isArray(value) && value.length && typeof value[0] === 'string' && value[0].indexOf('/api/common/files/view/') === 0) {
                        entries.push({ key: key, label: key, display: value.length + ' 张图片（见材料图片）' });
                        return;
                    }
                    entries.push({ key: key, label: key, display: displayValue(value) });
                });
                return entries;
            });

            /** 导出当前筛选条件（状态页签）下的真实数据；复用分页接口，单次最多 1000 条。 */
            function exportCsv() {
                if (exporting.value) return;
                var pageSize = 50;
                var maxPages = 20;
                var collected = [];
                var expectedTotal = 0;
                exporting.value = true;
                function fetchPage(page) {
                    var query = 'pageNum=' + page + '&pageSize=' + pageSize;
                    if (status.value) query += '&status=' + encodeURIComponent(status.value);
                    return api.get('/api/admin/service-requests?' + query).then(function (data) {
                        var records = (data && data.records) || [];
                        expectedTotal = (data && data.total) || 0;
                        collected = collected.concat(records);
                        var pages = Math.ceil(expectedTotal / pageSize);
                        if (records.length && page < pages && page < maxPages) {
                            return fetchPage(page + 1);
                        }
                        return collected;
                    });
                }
                fetchPage(1).then(function (records) {
                    var header = ['申请单号', '服务事项', '标题', '状态', '提交时间', '完成时间', '驳回原因'];
                    var lines = [header.join(',')];
                    records.forEach(function (row) {
                        lines.push([row.requestNo || '', row.itemName || '', row.title || '',
                            row.statusLabel || statusText(row.status), fmt(row.submittedAt), fmt(row.completedAt),
                            row.rejectReason || ''].map(function (value) {
                            return '"' + String(value).replace(/"/g, '""') + '"';
                        }).join(','));
                    });
                    var blob = new Blob(['\ufeff' + lines.join('\n')], { type: 'text/csv;charset=utf-8' });
                    var link = document.createElement('a');
                    link.href = URL.createObjectURL(blob);
                    link.download = 'service-requests-' + (status.value || 'all') + '-'
                        + new Date().toISOString().slice(0, 10) + '.csv';
                    link.click();
                    URL.revokeObjectURL(link.href);
                    if (expectedTotal > records.length) {
                        El.ElMessage.warning('已导出 ' + records.length + ' 条（筛选共 ' + expectedTotal
                            + ' 条，单次上限 ' + (pageSize * maxPages) + ' 条），请缩小筛选范围后再导出');
                    } else {
                        El.ElMessage.success('已导出 ' + records.length + ' 条申请');
                    }
                }).catch(function (error) {
                    El.ElMessage.error((error && error.message) || '导出失败');
                }).finally(function () {
                    exporting.value = false;
                });
            }

            function displayValue(value) {
                if (Array.isArray(value)) return value.join('、');
                if (typeof value === 'object') return JSON.stringify(value);
                return String(value);
            }

            function flowLabel(flow) { return { REPAIR: '报修派工', LEAVE_APPROVAL: '请假审批', MANUAL: '人工受理', GUIDE: '仅办事指南' }[flow] || '流程待确认'; }
            function openLeave(row) { window.location.hash = '#/leave?requestId=' + encodeURIComponent(row.requestId); }
            function canOperate(row) { return (row.availableActions || []).length > 0; }
            function statusText(v) { return STATUS_TEXT[v] || v || '-'; }
            function statusTone(v) { return STATUS_TONE[v] || 'n2'; }
            function slaTone(order) {
                if (!order) return 'n2';
                if (order.overdue) return 'r';
                if (order.dueSoon) return 'a';
                if (order.remainingHours != null) return 'g';
                return 'n2';
            }
            function slaText(order) {
                if (!order) return '-';
                if (order.overdue) return '已超时';
                if (order.remainingHours != null) return '剩余 ' + order.remainingHours + 'h';
                return order.completedAt ? '已完成' : '-';
            }
            function fmt(v) {
                if (!v) return '-';
                return String(v).replace('T', ' ').slice(0, 16);
            }

            load();

            return {
                tabs: TABS, status: status, rows: rows, total: total, pageNum: pageNum, pageSize: pageSize,
                loading: loading, totalPages: totalPages, pageNumbers: pageNumbers, goPage: goPage,
                switchStatus: switchStatus, load: load, reload: reload, exporting: exporting, exportCsv: exportCsv,
                detail: detail, detailVisible: detailVisible, action: action, comment: comment, submitting: submitting,
                openDetail: openDetail, submit: submit, formEntries: formEntries, flowLabel: flowLabel, canOperate: canOperate,
                slaTone: slaTone, slaText: slaText,
                statusText: statusText, statusTone: statusTone, fmt: fmt
            };
        }
    };
})();
