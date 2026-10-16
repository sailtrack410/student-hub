/* 校园社区（管理端）：真实接口 /api/admin/community。
   治理看板 + 举报队列（处理弹层含内容处置与用户处罚）+ 内容巡检下架/恢复。
   处理动作全部留痕（处理人/处理说明），并站内通知举报人与被处置人。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    var TABS = [
        { value: 'PENDING', label: '待处理' },
        { value: 'ACCEPTED', label: '已成立' },
        { value: 'REJECTED', label: '已驳回' },
        { value: '', label: '全部' }
    ];
    var REASON_LABELS = {
        AD: '广告营销', ABUSE: '辱骂攻击', PORN: '色情低俗',
        POLITICS: '违法违规', PRIVACY: '泄露隐私', OTHER: '其他'
    };
    var TARGET_LABELS = { POST: '帖子', COMMENT: '评论', MESSAGE: '私信', USER: '用户' };
    var STATUS_LABELS = { PENDING: '待处理', ACCEPTED: '已成立', REJECTED: '已驳回' };

    window.AdminPages.CommunityAdminPage = {
        name: 'CommunityAdminPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="page-h">',
            '    <div><h1>校园社区</h1><div class="sub">内容治理：举报处理、内容下架与用户处罚，全部操作留痕</div></div>',
            '    <div class="r"><button class="btn2" @click="refresh">刷新</button></div>',
            '  </div>',

            // 治理看板
            '  <div style="display:flex;gap:12px">',
            '    <div class="lcard" style="flex:1" v-for="k in kpis" :key="k.label">',
            '      <div class="cap">{{ k.label }}</div>',
            '      <div style="font-size:26px;font-weight:600;padding:8px 0 4px;color:var(--tk-text)">{{ k.value }}</div>',
            '    </div>',
            '  </div>',

            '  <div class="tabs">',
            '    <span v-for="t in tabs" :key="t.value" class="tab" :class="{ on: tabValue === t.value }" @click="switchTab(t)">{{ t.label }}</span>',
            '  </div>',

            '  <div class="lcard" style="flex:1;min-height:0">',
            '    <div class="cap">举报队列<span class="cnt" v-if="total">共 {{ total }} 条</span></div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto">',
            '      <table class="pk">',
            '        <colgroup><col style="width:110px"><col style="width:90px"><col><col style="width:110px"><col style="width:110px"><col style="width:100px"><col style="width:130px"><col style="width:100px"><col></colgroup>',
            '        <thead><tr><th>举报ID</th><th>对象</th><th>被举报内容</th><th>被举报人</th><th>举报人</th><th>原因</th><th>举报时间</th><th>状态</th><th class="r">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-if="!rows.length && !loading"><td colspan="9" style="text-align:center;color:var(--tk-muted);padding:32px 0">暂无举报</td></tr>',
            '          <tr v-if="loading"><td colspan="9" style="text-align:center;color:var(--tk-muted);padding:32px 0">加载中…</td></tr>',
            '          <tr v-for="row in rows" :key="row.id">',
            '            <td style="color:var(--tk-muted)">RP-{{ row.id }}</td>',
            '            <td>{{ targetLabel(row.targetType) }} #{{ row.targetId }}</td>',
            '            <td style="color:var(--tk-text)">{{ row.targetTitle || "-" }}</td>',
            '            <td>{{ row.targetAuthorName || "-" }}</td>',
            '            <td style="color:var(--tk-text3)">{{ row.reporterName || "-" }}</td>',
            '            <td><span class="bdg r">{{ reasonLabel(row.reasonCode) }}</span></td>',
            '            <td style="color:var(--tk-text3)">{{ fmt(row.createdAt) }}</td>',
            '            <td><span class="bdg" :class="statusClass(row.status)">{{ statusLabel(row.status) }}</span></td>',
            '            <td class="r">',
            '              <span class="op" @click="openDetail(row)">详情</span>',
            '              <template v-if="row.status === \'PENDING\'"> · <span class="op" @click="openHandle(row)">处理</span></template>',
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

            // 巡检：无需举报直接下架/恢复
            '  <div class="lcard">',
            '    <div class="cap">内容巡检：直接下架 / 恢复</div>',
            '    <div style="display:flex;gap:12px;align-items:flex-end;padding:8px 0 4px;flex-wrap:wrap">',
            '      <div class="fld" style="width:150px"><label>对象类型</label>',
            '        <select v-model="patrolType" class="pta" style="height:34px">',
            '          <option value="POST">帖子</option><option value="COMMENT">评论</option>',
            '        </select></div>',
            '      <div class="fld" style="width:170px"><label>对象 ID <em>*</em></label>',
            '        <input class="pta" style="height:34px" v-model="patrolId" placeholder="例如 1234567890" /></div>',
            '      <div class="fld" style="flex:1;min-width:200px"><label>处置原因</label>',
            '        <input class="pta" style="height:34px" v-model="patrolReason" maxlength="500" placeholder="会随站内通知告知内容作者" /></div>',
            '      <button class="btn2" :disabled="patrolling" @click="patrol(\'hide\')">下架</button>',
            '      <button class="btn2" :disabled="patrolling" @click="patrol(\'restore\')">恢复</button>',
            '    </div>',
            '  </div>',

            // 弹层：处理举报
            '  <div class="pmask" :class="{ show: handleVisible }" @click.self="handleVisible = false">',
            '    <div class="pcard" v-if="target">',
            '      <div class="ph"><b>处理举报</b><span class="no">RP-{{ target.id }}</span><span class="sp"></span><span class="x" @click="handleVisible = false">✕</span></div>',
            '      <div class="psum">',
            '        <div class="l1">{{ targetLabel(target.targetType) }} #{{ target.targetId }}：{{ target.targetTitle || "-" }}</div>',
            '        <div class="l2">被举报人 {{ target.targetAuthorName || "-" }} · 举报人 {{ target.reporterName || "-" }} · {{ reasonLabel(target.reasonCode) }} · {{ fmt(target.createdAt) }}</div>',
            '        <div class="l2" v-if="target.reasonDetail">补充说明：{{ target.reasonDetail }}</div>',
            '      </div>',
            '      <div class="fld"><label>处理结论 <em>*</em></label>',
            '        <el-radio-group v-model="handleForm.action">',
            '          <el-radio value="ACCEPT">举报成立</el-radio>',
            '          <el-radio value="REJECT">举报不成立</el-radio>',
            '        </el-radio-group></div>',
            '      <template v-if="handleForm.action === \'ACCEPT\'">',
            '        <div class="fld" v-if="isContentTarget"><label>内容处置</label>',
            '          <el-select v-model="handleForm.contentAction" style="width:100%">',
            '            <el-option label="不处置（仅记录结论）" value="NONE"></el-option>',
            '            <el-option label="下架（保留内容，可恢复）" value="HIDE"></el-option>',
            '            <el-option label="删除" value="DELETE"></el-option>',
            '          </el-select></div>',
            '        <div class="fld"><label>处罚作者</label>',
            '          <el-select v-model="handleForm.sanctionType" style="width:100%">',
            '            <el-option label="不处罚" value=""></el-option>',
            '            <el-option label="限制发言（MUTE）" value="MUTE"></el-option>',
            '            <el-option label="禁止访问社区（BAN）" value="BAN"></el-option>',
            '          </el-select></div>',
            '        <div class="fld" v-if="handleForm.sanctionType"><label>处罚时长（小时，留空表示长期有效）</label>',
            '          <el-input v-model.number="handleForm.sanctionHours" placeholder="例如 72" /></div>',
            '      </template>',
            '      <div class="fld"><label>处理说明</label><textarea class="pta" v-model="handleForm.note" maxlength="500" placeholder="会展示给举报人，并作为处置依据留痕"></textarea></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="handleVisible = false">取消</button>',
            '        <button class="c blue" :disabled="submitting" @click="submitHandle">{{ submitting ? "提交中…" : "提交处理" }}</button></div>',
            '    </div>',
            '  </div>',

            // 弹层：详情（含内容快照）
            '  <div class="pmask" :class="{ show: detailVisible }" @click.self="detailVisible = false">',
            '    <div class="pcard" v-if="target">',
            '      <div class="ph"><b>举报详情</b><span class="no">RP-{{ target.id }}</span><span class="sp"></span><span class="x" @click="detailVisible = false">✕</span></div>',
            '      <div class="psum">',
            '        <div class="l1">{{ targetLabel(target.targetType) }} #{{ target.targetId }}：{{ target.targetTitle || "-" }}</div>',
            '        <div class="l2">状态 {{ statusLabel(target.status) }} · 举报人 {{ target.reporterName || "-" }} · 被举报人 {{ target.targetAuthorName || "-" }}</div>',
            '        <div class="l2" v-if="target.handleNote">处理说明：{{ target.handleNote }}</div>',
            '      </div>',
            '      <div class="fld"><label>举报时刻的内容快照（作者删改后仍可追溯）</label>',
            '        <pre style="white-space:pre-wrap;word-break:break-all;background:var(--tk-card2,#00000010);border-radius:8px;padding:12px;font-size:12px;line-height:1.6;max-height:260px;overflow:auto">{{ prettySnapshot }}</pre></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="detailVisible = false">关闭</button></div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var tabValue = Vue.ref('PENDING');
            var rows = Vue.ref([]);
            var total = Vue.ref(0);
            var pageNum = Vue.ref(1);
            var pageSize = Vue.ref(10);
            var loading = Vue.ref(false);
            var summary = Vue.ref({});

            var handleVisible = Vue.ref(false);
            var detailVisible = Vue.ref(false);
            var target = Vue.ref(null);
            var submitting = Vue.ref(false);
            var handleForm = Vue.reactive({ action: 'ACCEPT', contentAction: 'HIDE', sanctionType: '', sanctionHours: null, note: '' });

            var patrolType = Vue.ref('POST');
            var patrolId = Vue.ref('');
            var patrolReason = Vue.ref('');
            var patrolling = Vue.ref(false);

            var kpis = Vue.computed(function () {
                return [
                    { label: '待处理举报', value: summary.value.pendingCount || 0 },
                    { label: '已成立', value: summary.value.acceptedCount || 0 },
                    { label: '已驳回', value: summary.value.rejectedCount || 0 },
                    { label: '今日新增', value: summary.value.todayCount || 0 },
                    { label: '生效中处罚', value: summary.value.activeSanctionCount || 0 }
                ];
            });

            function loadSummary() {
                api.get('/api/admin/community/reports/summary')
                    .then(function (data) { summary.value = data || {}; })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }
            function load() {
                loading.value = true;
                var params = ['pageNum=' + pageNum.value, 'pageSize=' + pageSize.value];
                if (tabValue.value) params.push('status=' + tabValue.value);
                api.get('/api/admin/community/reports?' + params.join('&'))
                    .then(function (page) {
                        rows.value = page.records || [];
                        total.value = page.total || 0;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { loading.value = false; });
            }
            function reload() { pageNum.value = 1; load(); }
            function switchTab(t) { tabValue.value = t.value; reload(); }
            function refresh() { loadSummary(); load(); }

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

            var isContentTarget = Vue.computed(function () {
                return !!target.value && (target.value.targetType === 'POST' || target.value.targetType === 'COMMENT');
            });
            var prettySnapshot = Vue.computed(function () {
                if (!target.value || !target.value.snapshot) return '（无快照）';
                try { return JSON.stringify(JSON.parse(target.value.snapshot), null, 2); }
                catch (e) { return target.value.snapshot; }
            });

            function openHandle(row) {
                target.value = row;
                handleForm.action = 'ACCEPT';
                handleForm.contentAction = 'HIDE';
                handleForm.sanctionType = '';
                handleForm.sanctionHours = null;
                handleForm.note = '';
                handleVisible.value = true;
            }
            function openDetail(row) {
                target.value = row;
                detailVisible.value = true;
            }
            function submitHandle() {
                var body = {
                    action: handleForm.action,
                    contentAction: handleForm.action === 'ACCEPT' ? handleForm.contentAction : null,
                    sanctionType: handleForm.action === 'ACCEPT' && handleForm.sanctionType ? handleForm.sanctionType : null,
                    sanctionHours: handleForm.action === 'ACCEPT' && handleForm.sanctionType
                        ? (handleForm.sanctionHours || 0) : null,
                    note: handleForm.note || null
                };
                if (handleForm.action === 'REJECT' && !body.note) {
                    El.ElMessage.warning('驳回时请填写处理说明，举报人会收到该说明');
                    return;
                }
                submitting.value = true;
                api.post('/api/admin/community/reports/' + target.value.id + '/handle', body)
                    .then(function () {
                        El.ElMessage.success(handleForm.action === 'ACCEPT' ? '已处理并通知相关用户' : '已驳回并反馈举报人');
                        handleVisible.value = false;
                        refresh();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { submitting.value = false; });
            }
            function patrol(action) {
                var id = Number(patrolId.value);
                if (!id || id <= 0) {
                    El.ElMessage.warning('请填写有效的对象 ID');
                    return;
                }
                if (action === 'hide' && !patrolReason.value.trim()) {
                    El.ElMessage.warning('下架需要填写处置原因');
                    return;
                }
                patrolling.value = true;
                api.post('/api/admin/community/content/' + patrolType.value + '/' + id + '/' + action,
                        { reason: patrolReason.value.trim() || null })
                    .then(function () {
                        El.ElMessage.success(action === 'hide' ? '已下架并通知作者' : '已恢复并通知作者');
                        patrolId.value = '';
                        patrolReason.value = '';
                        refresh();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { patrolling.value = false; });
            }

            function reasonLabel(code) { return REASON_LABELS[code] || code || '-'; }
            function targetLabel(type) { return TARGET_LABELS[type] || type || '-'; }
            function statusLabel(status) { return STATUS_LABELS[status] || status || '-'; }
            function statusClass(status) {
                return status === 'PENDING' ? 'a' : status === 'ACCEPTED' ? 'r' : 'g';
            }
            function fmt(v) {
                if (!v) return '-';
                return String(v).replace('T', ' ').slice(0, 16);
            }

            refresh();

            return {
                tabs: TABS, tabValue: tabValue, rows: rows, total: total, pageNum: pageNum, pageSize: pageSize,
                loading: loading, totalPages: totalPages, pageNumbers: pageNumbers, goPage: goPage,
                switchTab: switchTab, refresh: refresh, kpis: kpis,
                handleVisible: handleVisible, detailVisible: detailVisible, target: target,
                handleForm: handleForm, submitting: submitting, isContentTarget: isContentTarget,
                prettySnapshot: prettySnapshot, openHandle: openHandle, openDetail: openDetail, submitHandle: submitHandle,
                patrolType: patrolType, patrolId: patrolId, patrolReason: patrolReason, patrolling: patrolling, patrol: patrol,
                reasonLabel: reasonLabel, targetLabel: targetLabel, statusLabel: statusLabel, statusClass: statusClass, fmt: fmt
            };
        }
    };
})();
