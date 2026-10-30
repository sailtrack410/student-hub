/* 校园社区（管理端）：真实接口 /api/admin/community。
   治理看板 + 举报队列（SLA 倒计时/证据聚合/处理模板/批量处置）+ 内容巡检下架/恢复。
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
            '<div class="pgw comm" style="display:flex;flex-direction:column;gap:12px;flex:1;min-height:0">',
            '  <div class="page-h">',
            '    <div><h1>举报处置</h1><div class="sub">举报队列与内容巡检：24h 处理时限、证据集中查看、模板化处置与批量操作</div></div>',
            '    <div class="r"><button class="btn2" @click="openTemplates">处理模板</button><button class="btn2" @click="refresh">刷新</button></div>',
            '  </div>',

            // 治理看板
            '  <div class="statgrid five">',
            '    <div class="stat" v-for="k in kpis" :key="k.label">',
            '      <div class="lb">{{ k.label }}</div><div class="v"><b>{{ k.value }}</b></div>',
            '    </div>',
            '  </div>',

            '  <div class="tabs">',
            '    <span v-for="t in tabs" :key="t.value" class="tab" :class="{ on: tabValue === t.value }" @click="switchTab(t)">{{ t.label }}</span>',
            '  </div>',

            '  <div class="panel" style="flex:1;min-height:0">',
            '    <div class="cap">举报队列<span class="cnt" v-if="total">共 {{ total }} 条</span>',
            '      <span style="margin-left:auto;display:flex;gap:8px;align-items:center">',
            '        <template v-if="selectedIds.length">',
            '          <span style="font-size:12px;color:var(--tk-muted)">已选 {{ selectedIds.length }} 条</span>',
            '          <button class="c" style="color:var(--tk-bad);border-color:var(--tk-bad)" @click="openBatch(\'ACCEPT\')">批量成立</button>',
            '          <button class="c" @click="openBatch(\'REJECT\')">批量驳回</button>',
            '          <button class="c ghost" @click="clearSelection">取消选择</button>',
            '        </template>',
            '      </span></div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto">',
            '      <table class="pk">',
            '        <colgroup><col style="width:36px"><col style="width:110px"><col style="width:90px"><col><col style="width:110px"><col style="width:110px"><col style="width:100px"><col style="width:120px"><col style="width:100px"><col></colgroup>',
            '        <thead><tr><th><input type="checkbox" :checked="allSelected" @change="toggleAll($event)" /></th><th>举报ID</th><th>对象</th><th>被举报内容</th><th>被举报人</th><th>举报人</th><th>原因</th><th>举报时间 / 时限</th><th>状态</th><th class="r">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-if="!rows.length && !loading"><td colspan="10" style="text-align:center;color:var(--tk-muted);padding:64px 0">暂无举报</td></tr>',
            '          <tr v-if="loading"><td colspan="10" style="text-align:center;color:var(--tk-muted);padding:64px 0">加载中…</td></tr>',
            '          <tr v-for="row in rows" :key="row.id">',
            '            <td><input type="checkbox" :value="row.id" :checked="selectedIds.indexOf(row.id) >= 0" @change="toggleOne(row, $event)" /></td>',
            '            <td style="color:var(--tk-muted)">RP-{{ row.id }}</td>',
            '            <td>{{ targetLabel(row.targetType) }} #{{ row.targetId }}<div v-if="row.sameTargetReportCount > 1" style="font-size:10px;color:var(--tk-warn)">同对象累计 {{ row.sameTargetReportCount }} 次</div></td>',
            '            <td style="color:var(--tk-text)">{{ row.targetTitle || "-" }}</td>',
            '            <td>{{ row.targetAuthorName || "-" }}</td>',
            '            <td style="color:var(--tk-text3)">{{ row.reporterName || "-" }}</td>',
            '            <td><span class="bdg r">{{ reasonLabel(row.reasonCode) }}</span></td>',
            '            <td style="color:var(--tk-text3)">{{ fmt(row.createdAt) }}',
            '              <div v-if="row.status === \'PENDING\'" class="bdg" :class="slaTone(row)" style="margin-top:3px">{{ slaText(row) }}</div>',
            '            </td>',
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
            '  <div class="panel">',
            '    <div class="cap">内容巡检：直接下架 / 恢复</div>',
            '    <div style="display:flex;gap:8px;align-items:flex-end;flex-wrap:wrap">',
            '      <div style="width:150px"><div style="font-size:12px;color:var(--tk-muted);margin-bottom:6px">对象类型</div>',
            '        <div class="ipt2" style="width:100%"><select v-model="patrolType" style="width:100%"><option value="POST">帖子</option><option value="COMMENT">评论</option></select></div></div>',
            '      <div style="width:170px"><div style="font-size:12px;color:var(--tk-muted);margin-bottom:6px">对象 ID <em style="color:var(--tk-bad);font-style:normal">*</em></div>',
            '        <div class="ipt2" style="width:100%"><input v-model="patrolId" placeholder="例如 1234567890" /></div></div>',
            '      <div style="flex:1;min-width:200px"><div style="font-size:12px;color:var(--tk-muted);margin-bottom:6px">处置原因</div>',
            '        <div class="ipt2" style="width:100%"><input v-model="patrolReason" maxlength="500" placeholder="会随站内通知告知内容作者" /></div></div>',
            '      <button class="c" style="color:var(--tk-bad);border-color:var(--tk-bad)" :disabled="patrolling" @click="patrol(\'hide\')">下架</button>',
            '      <button class="c blue" :disabled="patrolling" @click="patrol(\'restore\')">恢复</button>',
            '    </div>',
            '  </div>',

            // 弹层：处理举报（单个）
            '  <div class="pmask" :class="{ show: handleVisible }" @click.self="handleVisible = false">',
            '    <div class="pcard" v-if="target">',
            '      <div class="ph"><b>处理举报</b><span class="no">RP-{{ target.id }}</span><span class="sp"></span><span class="x" @click="handleVisible = false">✕</span></div>',
            '      <div class="psum">',
            '        <div class="l1">{{ targetLabel(target.targetType) }} #{{ target.targetId }}：{{ target.targetTitle || "-" }}</div>',
            '        <div class="l2">被举报人 {{ target.targetAuthorName || "-" }} · 举报人 {{ target.reporterName || "-" }} · {{ reasonLabel(target.reasonCode) }} · {{ fmt(target.createdAt) }}</div>',
            '        <div class="l2" v-if="target.reasonDetail">补充说明：{{ target.reasonDetail }}</div>',
            '        <div class="l2" v-if="target.status === \'PENDING\'">处理时限：{{ slaText(target) }}</div>',
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
            '      <div class="fld"><label>处理模板</label>',
            '        <el-select v-model="handleForm.templateId" clearable placeholder="选择模板自动填充处理说明" style="width:100%" @change="applyTemplate">',
            '          <el-option v-for="t in reportTemplates" :key="t.id" :label="t.templateName" :value="t.id"></el-option>',
            '        </el-select></div>',
            '      <div class="fld"><label>处理说明</label><textarea class="pta" v-model="handleForm.note" maxlength="500" placeholder="会展示给举报人，并作为处置依据留痕"></textarea></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="handleVisible = false">取消</button>',
            '        <button class="c blue" :disabled="submitting" @click="submitHandle">{{ submitting ? "提交中…" : "提交处理" }}</button></div>',
            '    </div>',
            '  </div>',

            // 弹层：批量处理
            '  <div class="pmask" :class="{ show: batchVisible }" @click.self="batchVisible = false">',
            '    <div class="pcard">',
            '      <div class="ph"><b>批量处理举报</b><span class="no">{{ batchForm.action === \'ACCEPT\' ? \'成立\' : \'驳回\' }} {{ selectedIds.length }} 条</span><span class="sp"></span><span class="x" @click="batchVisible = false">✕</span></div>',
            '      <div class="psum"><div class="l1">已选举报：{{ selectedIds.join(", ") }}</div>',
            '        <div class="l2">批量操作为同一结论；已处理或已失效的举报会自动跳过并在结果中提示。</div></div>',
            '      <div class="fld" v-if="batchForm.action === \'ACCEPT\'"><label>内容处置</label>',
            '        <el-select v-model="batchForm.contentAction" style="width:100%">',
            '          <el-option label="不处置（仅记录结论）" value="NONE"></el-option>',
            '          <el-option label="下架（保留内容，可恢复）" value="HIDE"></el-option>',
            '          <el-option label="删除" value="DELETE"></el-option>',
            '        </el-select></div>',
            '      <div class="fld"><label>处理模板</label>',
            '        <el-select v-model="batchForm.templateId" clearable placeholder="选择模板自动填充处理说明" style="width:100%" @change="applyBatchTemplate">',
            '          <el-option v-for="t in reportTemplates" :key="t.id" :label="t.templateName" :value="t.id"></el-option>',
            '        </el-select></div>',
            '      <div class="fld"><label>处理说明</label><textarea class="pta" v-model="batchForm.note" maxlength="500" placeholder="会统一展示给举报人"></textarea></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="batchVisible = false">取消</button>',
            '        <button class="c blue" :disabled="submitting" @click="submitBatch">{{ submitting ? "提交中…" : "批量提交" }}</button></div>',
            '    </div>',
            '  </div>',

            // 弹层：详情（含内容快照 + 证据）
            '  <div class="pmask" :class="{ show: detailVisible }" @click.self="detailVisible = false">',
            '    <div class="pcard" v-if="target" style="max-width:680px">',
            '      <div class="ph"><b>举报详情</b><span class="no">RP-{{ target.id }}</span><span class="sp"></span><span class="x" @click="detailVisible = false">✕</span></div>',
            '      <div class="psum">',
            '        <div class="l1">{{ targetLabel(target.targetType) }} #{{ target.targetId }}：{{ target.targetTitle || "-" }}</div>',
            '        <div class="l2">状态 {{ statusLabel(target.status) }} · 举报人 {{ target.reporterName || "-" }} · 被举报人 {{ target.targetAuthorName || "-" }} · 原因 {{ reasonLabel(target.reasonCode) }}</div>',
            '        <div class="l2" v-if="target.sameTargetReportCount > 1">该对象累计被举报 {{ target.sameTargetReportCount }} 次（含已处理）</div>',
            '        <div class="l2" v-if="target.handleNote">处理说明：{{ target.handleNote }}</div>',
            '      </div>',
            '      <div class="fld" v-if="target.evidenceUrls && target.evidenceUrls.length"><label>图片证据（内容图片，集中查看）</label>',
            '        <div style="display:flex;gap:8px;flex-wrap:wrap">',
            '          <img v-for="u in target.evidenceUrls" :key="u" :src="u" style="width:96px;height:96px;object-fit:cover;border-radius:8px;border:1px solid var(--tk-stroke);cursor:zoom-in" @click="zoom = u" />',
            '        </div></div>',
            '      <div class="fld"><label>举报时刻的内容快照（作者删改后仍可追溯）</label>',
            '        <pre style="white-space:pre-wrap;word-break:break-all;background:var(--tk-card2,#00000010);border-radius:8px;padding:12px;font-size:12px;line-height:1.6;max-height:260px;overflow:auto">{{ prettySnapshot }}</pre></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="detailVisible = false">关闭</button></div>',
            '    </div>',
            '  </div>',

            // 弹层：处理模板管理
            '  <div class="pmask" :class="{ show: templateVisible }" @click.self="templateVisible = false">',
            '    <div class="pcard" style="max-width:720px">',
            '      <div class="ph"><b>处理模板</b><span class="no">举报说明 / 申诉回复</span><span class="sp"></span><span class="x" @click="templateVisible = false">✕</span></div>',
            '      <div class="psum"><div class="l2">平台内置模板只读；校区模板可新增/删除，保存后在工作台处理举报时可一键引用。</div></div>',
            '      <div class="tblw" style="max-height:320px;overflow:auto"><table class="pk">',
            '        <thead><tr><th style="width:130px">类型</th><th>模板名称</th><th>正文</th><th style="width:70px">范围</th><th style="width:70px" class="r">操作</th></tr></thead>',
            '        <tbody><tr v-if="!templates.length"><td colspan="5" style="text-align:center;color:var(--tk-muted);padding:32px 0">暂无模板</td></tr>',
            '          <tr v-for="t in templates" :key="t.id"><td>{{ t.templateType === \'APPEAL_REPLY\' ? \'申诉回复\' : \'举报说明\' }}</td>',
            '            <td>{{ t.templateName }}</td><td style="color:var(--tk-muted);font-size:12px">{{ t.content }}</td>',
            '            <td><span class="bdg" :class="t.builtin ? \'a\' : \'g\'">{{ t.builtin ? \'内置\' : \'校区\' }}</span></td>',
            '            <td class="r"><span v-if="!t.builtin" class="op" style="color:var(--tk-bad)" @click="removeTemplate(t)">删除</span><span v-else style="color:var(--tk-muted)">—</span></td></tr>',
            '        </tbody></table></div>',
            '      <div style="display:flex;gap:8px;margin-top:12px;flex-wrap:wrap;align-items:flex-end">',
            '        <div style="width:120px"><div style="font-size:12px;color:var(--tk-muted);margin-bottom:6px">类型</div>',
            '          <div class="ipt2" style="width:100%"><select v-model="templateForm.templateType" style="width:100%"><option value="REPORT_NOTE">举报说明</option><option value="APPEAL_REPLY">申诉回复</option></select></div></div>',
            '        <div style="width:160px"><div style="font-size:12px;color:var(--tk-muted);margin-bottom:6px">名称</div><div class="ipt2" style="width:100%"><input v-model="templateForm.templateName" maxlength="64" /></div></div>',
            '        <div style="flex:1;min-width:200px"><div style="font-size:12px;color:var(--tk-muted);margin-bottom:6px">正文</div><div class="ipt2" style="width:100%"><input v-model="templateForm.content" maxlength="1000" /></div></div>',
            '        <button class="c blue" :disabled="templateSubmitting" @click="createTemplate">新增模板</button>',
            '      </div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="templateVisible = false">关闭</button></div>',
            '    </div>',
            '  </div>',

            // 图片放大
            '  <div v-if="zoom" class="pmask show" @click.self="zoom = \'\'" style="z-index:60">',
            '    <img :src="zoom" style="max-width:86vw;max-height:86vh;border-radius:8px;background:#fff" />',
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
            var handleForm = Vue.reactive({ action: 'ACCEPT', contentAction: 'HIDE', sanctionType: '', sanctionHours: null, note: '', templateId: null });

            var selectedIds = Vue.ref([]);
            var batchVisible = Vue.ref(false);
            var batchForm = Vue.reactive({ action: 'ACCEPT', contentAction: 'NONE', note: '', templateId: null });

            var templates = Vue.ref([]);
            var templateVisible = Vue.ref(false);
            var templateSubmitting = Vue.ref(false);
            var templateForm = Vue.reactive({ templateType: 'REPORT_NOTE', templateName: '', content: '' });
            var zoom = Vue.ref('');

            var patrolType = Vue.ref('POST');
            var patrolId = Vue.ref('');
            var patrolReason = Vue.ref('');
            var patrolling = Vue.ref(false);

            var reportTemplates = Vue.computed(function () {
                return templates.value.filter(function (t) { return t.templateType === 'REPORT_NOTE'; });
            });
            var allSelected = Vue.computed(function () {
                return rows.value.length > 0 && selectedIds.value.length === rows.value.length;
            });

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
                        selectedIds.value = [];
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { loading.value = false; });
            }
            function loadTemplates() {
                api.get('/api/admin/community/moderation-templates')
                    .then(function (data) { templates.value = data || []; })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }
            function reload() { pageNum.value = 1; load(); }
            function switchTab(t) { tabValue.value = t.value; reload(); }
            function refresh() { loadSummary(); load(); loadTemplates(); }

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

            function toggleAll(event) {
                selectedIds.value = event.target.checked ? rows.value.map(function (r) { return r.id; }) : [];
            }
            function toggleOne(row, event) {
                var list = selectedIds.value.slice();
                if (event.target.checked) {
                    if (list.indexOf(row.id) < 0) list.push(row.id);
                } else {
                    list = list.filter(function (id) { return id !== row.id; });
                }
                selectedIds.value = list;
            }
            function clearSelection() { selectedIds.value = []; }

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
                handleForm.templateId = null;
                handleVisible.value = true;
            }
            function openDetail(row) {
                target.value = row;
                detailVisible.value = true;
            }
            function applyTemplate(id) {
                var tpl = templateById(id);
                if (tpl) handleForm.note = tpl.content;
            }
            function applyBatchTemplate(id) {
                var tpl = templateById(id);
                if (tpl) batchForm.note = tpl.content;
            }
            function templateById(id) {
                if (!id) return null;
                return templates.value.filter(function (t) { return t.id === id; })[0] || null;
            }
            function submitHandle() {
                var body = {
                    action: handleForm.action,
                    contentAction: handleForm.action === 'ACCEPT' ? handleForm.contentAction : null,
                    sanctionType: handleForm.action === 'ACCEPT' && handleForm.sanctionType ? handleForm.sanctionType : null,
                    sanctionHours: handleForm.action === 'ACCEPT' && handleForm.sanctionType
                        ? (handleForm.sanctionHours || 0) : null,
                    note: handleForm.note || null,
                    templateId: handleForm.templateId || null
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
            function openBatch(action) {
                if (!selectedIds.value.length) return;
                batchForm.action = action;
                batchForm.contentAction = 'NONE';
                batchForm.note = '';
                batchForm.templateId = null;
                batchVisible.value = true;
            }
            function submitBatch() {
                if (batchForm.action === 'REJECT' && !batchForm.note) {
                    El.ElMessage.warning('批量驳回需要填写处理说明');
                    return;
                }
                submitting.value = true;
                var body = {
                    reportIds: selectedIds.value.slice(),
                    action: batchForm.action,
                    contentAction: batchForm.action === 'ACCEPT' ? batchForm.contentAction : null,
                    note: batchForm.note || null,
                    templateId: batchForm.templateId || null
                };
                api.post('/api/admin/community/reports/batch-handle', body)
                    .then(function (result) {
                        El.ElMessage.success('批量完成：成功 ' + (result.updated || 0)
                            + ' 条，跳过 ' + (result.skipped || 0) + ' 条');
                        batchVisible.value = false;
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

            function openTemplates() {
                templateVisible.value = true;
                loadTemplates();
            }
            function createTemplate() {
                if (!templateForm.templateName.trim() || !templateForm.content.trim()) {
                    El.ElMessage.warning('请填写模板名称与正文');
                    return;
                }
                templateSubmitting.value = true;
                api.post('/api/admin/community/moderation-templates', {
                    templateType: templateForm.templateType,
                    templateName: templateForm.templateName.trim(),
                    content: templateForm.content.trim(),
                    status: 'ENABLED',
                    sortOrder: 100
                }).then(function () {
                    El.ElMessage.success('模板已新增');
                    templateForm.templateName = '';
                    templateForm.content = '';
                    loadTemplates();
                }).catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { templateSubmitting.value = false; });
            }
            function removeTemplate(t) {
                El.ElMessageBox.confirm('确认删除模板「' + t.templateName + '」？', '删除模板', {
                    type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
                }).then(function () {
                    return api.delete('/api/admin/community/moderation-templates/' + t.id);
                }).then(function () {
                    El.ElMessage.success('已删除');
                    loadTemplates();
                }).catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }

            function reasonLabel(code) { return REASON_LABELS[code] || code || '-'; }
            function targetLabel(type) { return TARGET_LABELS[type] || type || '-'; }
            function statusLabel(status) { return STATUS_LABELS[status] || status || '-'; }
            function statusClass(status) {
                return status === 'PENDING' ? 'a' : status === 'ACCEPTED' ? 'r' : 'g';
            }
            function slaRemaining(row) {
                if (row.createdAt === undefined || row.createdAt === null) return null;
                if (typeof row.remainingMinutes === 'number') return row.remainingMinutes;
                return null;
            }
            function slaText(row) {
                var remaining = slaRemaining(row);
                if (remaining === null) return '—';
                if (remaining < 0) return '已超时 ' + formatMinutes(-remaining);
                return '剩余 ' + formatMinutes(remaining);
            }
            function slaTone(row) {
                var remaining = slaRemaining(row);
                if (remaining === null) return 'muted';
                if (remaining < 0) return 'r';
                return remaining <= 240 ? 'a' : 'g';
            }
            function formatMinutes(minutes) {
                var m = Math.max(0, Math.round(minutes));
                if (m >= 60) {
                    return Math.floor(m / 60) + 'h ' + (m % 60) + 'm';
                }
                return m + 'm';
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
                selectedIds: selectedIds, allSelected: allSelected, toggleAll: toggleAll, toggleOne: toggleOne,
                clearSelection: clearSelection, batchVisible: batchVisible, batchForm: batchForm,
                openBatch: openBatch, submitBatch: submitBatch,
                templates: templates, reportTemplates: reportTemplates, templateVisible: templateVisible,
                templateForm: templateForm, templateSubmitting: templateSubmitting,
                openTemplates: openTemplates, createTemplate: createTemplate, removeTemplate: removeTemplate,
                applyTemplate: applyTemplate, applyBatchTemplate: applyBatchTemplate, zoom: zoom,
                reasonLabel: reasonLabel, targetLabel: targetLabel, statusLabel: statusLabel, statusClass: statusClass,
                slaText: slaText, slaTone: slaTone, fmt: fmt
            };
        }
    };
})();
