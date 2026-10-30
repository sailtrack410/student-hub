/* 社区身份治理：抓取号 / 人工运营号 / 广告服务号。
   官方抓取号必须 OFFICIAL_AUTO，人工运营号 OFFICIAL_HUMAN，广告服务号 AD_SERVICE。
   接口：/api/admin/community/official-accounts。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var TYPE = {
        OFFICIAL_AUTO: { label: '官方抓取号', hint: '官网抓取自动发布', tone: 'info', icon: 'Cpu' },
        OFFICIAL_HUMAN: { label: '人工运营号', hint: '真实运营人员发布', tone: 'ok', icon: 'UserFilled' },
        AD_SERVICE: { label: '广告服务号', hint: '广告/商户服务主体', tone: 'warn', icon: 'Wallet' }
    };
    var VERIFY = {
        NONE: { label: '未认证', tone: 'muted' },
        PENDING: { label: '待审核', tone: 'warn' },
        APPROVED: { label: '已认证', tone: 'ok' },
        REJECTED: { label: '已驳回', tone: 'bad' },
        DISABLED: { label: '认证停用', tone: 'muted' }
    };
    var STATUS = { ENABLED: '正常', DISABLED: '已停用' };

    function blank() {
        return {
            userId: null,
            accountType: 'OFFICIAL_HUMAN',
            displayName: '',
            operatorName: '',
            officialTitle: '',
            description: '',
            verificationStatus: 'PENDING',
            status: 'ENABLED',
            username: ''
        };
    }

    window.AdminPages.CommunityAccountsPage = {
        name: 'CommunityAccountsPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>官方与运营账号</h1><div class="sub">社区发布主体的身份治理</div></div>',
            '    <div class="r"><button class="btn2" @click="load">刷新</button><button class="btn2" @click="openPublish">官方代发</button><button class="btn2 pri" @click="openCreate">新建账号</button></div></div>',

            '  <div class="os-grid" style="grid-template-columns:repeat(4,1fr)">',
            '    <div class="os-kpi"><div class="k-label"><span class="dot"></span>运营账号</div><div class="k-val">{{ total }}<small>个</small></div><div class="k-foot"><span>官方号 + 广告服务号</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-info)"></span>官方抓取号</div><div class="k-val">{{ typeCount(\'OFFICIAL_AUTO\') }}<small>个</small></div><div class="k-foot"><span>官网内容自动发布</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>人工运营号</div><div class="k-val">{{ typeCount(\'OFFICIAL_HUMAN\') }}<small>个</small></div><div class="k-foot"><span>需绑定运营人</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>待认证</div><div class="k-val">{{ pendingVerify }}<small>个</small></div><div class="k-foot"><span>核验资质后通过</span></div></div>',
            '  </div>',

            '  <div class="os-note"><b>身份边界</b> 抓取号只用于官网同步，不参与人工登录；人工运营号必须有真实运营人；广告服务号对外展示为商户主体，不走「官方」标识。已绑定官网抓取来源的账号不能改为非抓取类型。</div>',

            '  <div class="os-panel" style="flex:1;min-height:0;display:flex;flex-direction:column">',
            '    <div class="os-panel-h" style="flex-wrap:wrap;row-gap:8px">',
            '      <div style="display:flex;gap:4px"><button v-for="t in typeTabs" :key="t.value" class="tab" :class="{ on: accountType === t.value }" style="background:transparent;border-color:transparent;color:var(--tk-text3)" :style="accountType === t.value ? \'background:var(--tk-text);color:var(--tk-panel)\' : \'\'" @click="accountType = t.value; pageNum = 1; load()">{{ t.label }}<span style="margin-left:6px;font-size:10px;opacity:.7">{{ t.count }}</span></button></div>',
            '      <span style="flex:1"></span>',
            '      <select class="os-select" style="width:116px" v-model="status" @change="pageNum = 1; load()"><option value="">全部状态</option><option value="ENABLED">正常</option><option value="DISABLED">已停用</option></select>',
            '      <select class="os-select" style="width:126px" v-model="verificationStatus" @change="pageNum = 1; load()"><option value="">全部认证</option><option value="PENDING">待审核</option><option value="APPROVED">已认证</option><option value="REJECTED">已驳回</option><option value="NONE">未认证</option></select>',
            '      <div class="srch" style="width:220px;background:var(--tk-input)"><el-icon :size="13"><Search /></el-icon><input v-model.trim="keyword" placeholder="搜索账号 / 展示名 / 运营人" @keyup.enter="pageNum = 1; load()" /></div>',
            '    </div>',
            '    <div style="overflow:auto;flex:1">',
            '      <table style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr><th style="width:27%">账号</th><th style="width:128px">类型</th><th style="width:150px">运营人 / 说明</th><th style="width:108px">认证</th><th style="width:84px">状态</th><th style="width:132px">更新时间</th><th style="width:132px;text-align:right">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-for="a in rows" :key="a.userId">',
            '            <td><div style="display:flex;align-items:center;gap:10px;min-width:0">',
            '              <span style="width:30px;height:30px;border-radius:8px;background:var(--tk-subtle);display:grid;place-items:center;font-size:11px;color:var(--tk-brand);flex:none">{{ (a.displayName || "?").slice(0,1) }}</span>',
            '              <div style="min-width:0"><div style="display:flex;align-items:center;gap:6px"><b style="font-weight:550;color:var(--tk-text);overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ a.displayName }}</b><span v-if="a.official" class="os-tag brand">官方</span></div>',
            '                <div style="font-size:10px;color:var(--tk-muted);margin-top:3px"><span class="os-mono">@{{ a.username }}</span><span v-if="a.officialTitle" style="margin-left:7px">{{ a.officialTitle }}</span></div></div></div></td>',
            '            <td><span class="os-tag" :class="TYPE[a.accountType] ? TYPE[a.accountType].tone : \'\'">{{ TYPE[a.accountType] ? TYPE[a.accountType].label : a.accountType }}</span></td>',
            '            <td><div style="font-size:12px;color:var(--tk-text2)">{{ a.operatorName || "—" }}</div><div style="font-size:10px;color:var(--tk-muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ a.description || TYPE[a.accountType] && TYPE[a.accountType].hint || "—" }}</div></td>',
            '            <td><span class="os-tag" :class="VERIFY[a.verificationStatus] ? VERIFY[a.verificationStatus].tone : \'muted\'">{{ VERIFY[a.verificationStatus] ? VERIFY[a.verificationStatus].label : a.verificationStatus }}</span></td>',
            '            <td><span class="os-dot" :class="a.status === \'ENABLED\' ? \'ok\' : \'muted\'" style="margin-right:5px"></span><span style="font-size:11px;color:var(--tk-text3)">{{ STATUS[a.status] || a.status }}</span></td>',
            '            <td style="font-size:11px;color:var(--tk-text3)">{{ fmt(a.updatedAt) }}</td>',
            '            <td style="text-align:right;white-space:nowrap"><span style="font-size:11px;color:var(--tk-info);cursor:pointer" @click="openEdit(a)">编辑</span><span style="font-size:11px;margin-left:9px;cursor:pointer" :style="{ color: a.status === \'ENABLED\' ? \'var(--tk-warn)\' : \'var(--tk-ok)\' }" @click="toggle(a)">{{ a.status === "ENABLED" ? "停用" : "启用" }}</span></td>',
            '          </tr>',
            '          <tr v-if="!rows.length"><td colspan="7" class="os-empty">没有匹配的运营账号</td></tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',
            '    <div style="display:flex;align-items:center;gap:10px;padding:11px 18px;border-top:1px solid var(--tk-stroke)">',
            '      <span style="font-size:11px;color:var(--tk-text3)">共 {{ total }} 个账号 · 第 {{ pageNum }} / {{ totalPages }} 页</span><span style="flex:1"></span>',
            '      <button class="os-btn sm" :disabled="pageNum <= 1" @click="pageNum--; load()">上一页</button><button class="os-btn sm" :disabled="pageNum >= totalPages" @click="pageNum++; load()">下一页</button>',
            '    </div>',
            '  </div>',

            // C12 官方代发：以官方账号身份发布社区帖子
            '  <div v-if="publishOpen" class="cmd-mask" style="padding-top:5vh" @click.self="publishOpen = false">',
            '    <div class="cmd-panel" style="width:640px;max-height:86vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">官方代发社区帖子</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="publishOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-note"><b>代发规则</b> 仅 OFFICIAL_AUTO / OFFICIAL_HUMAN 可代发；内容命中屏蔽词会被拒绝（不静默隐藏官方内容）；代发帖由审计 AOP 记录操作人。</div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>官方账号 <em>*</em></label><select class="os-select" v-model.number="publishForm.officialUserId" style="width:100%"><option :value="null">请选择官方账号</option><option v-for="a in publishAccounts" :key="a.userId" :value="a.userId" :disabled="a.status !== \'ENABLED\'">{{ a.displayName }}（{{ a.accountType }} · {{ a.status === \'ENABLED\' ? \'启用\' : \'停用\' }}）</option></select></div>',
            '          <div class="os-field"><label>发布分区 <em>*</em></label><select class="os-select" v-model.number="publishForm.sectionId" style="width:100%"><option :value="null">请选择分区</option><option v-for="sec in publishSections" :key="sec.sectionId" :value="sec.sectionId" :disabled="sec.status !== \'ENABLED\'">{{ sec.sectionName }}{{ sec.status !== \'ENABLED\' ? \'（已停用）\' : \'\' }}</option></select></div>',
            '        </div>',
            '        <div class="os-field"><label>标题 <em>*</em></label><input class="os-input" v-model.trim="publishForm.title" maxlength="80" placeholder="例如：关于 2026 年秋季学期期中教学检查的通知" /></div>',
            '        <div class="os-field"><label>正文 <em>*</em></label><textarea class="os-textarea" v-model="publishForm.content" maxlength="2000" rows="7" placeholder="官方内容正文，发布后进入对应分区信息流"></textarea><div style="font-size:10px;color:var(--tk-text3);margin-top:3px">{{ (publishForm.content || \'\').length }} / 2000</div></div>',
            '        <div class="os-field"><label>话题标签（逗号分隔，最多 8 个）</label><input class="os-input" v-model.trim="publishForm.topics" maxlength="200" placeholder="例如：教务通知,期中检查" /></div>',
            '        <div style="display:flex;justify-content:flex-end;gap:8px"><button class="os-btn" @click="publishOpen = false">取消</button><button class="os-btn primary" :disabled="publishing" @click="submitPublish">{{ publishing ? "发布中…" : "以官方身份发布" }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            '  <div v-if="editorOpen" class="cmd-mask" style="padding-top:6vh" @click.self="editorOpen = false">',
            '    <div class="cmd-panel" style="width:590px;max-height:84vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">{{ form.userId ? "编辑运营账号" : "新建运营账号" }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="editorOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-field"><label>账号类型 <em>*</em></label><div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px">',
            '          <button v-for="(m, k) in TYPE" :key="k" class="os-btn" :class="{ primary: form.accountType === k }" style="height:58px;flex-direction:column;align-items:flex-start;gap:3px;padding:0 11px" @click="form.accountType = k"><b style="font-size:12px">{{ m.label }}</b><span style="font-size:10px;opacity:.72">{{ m.hint }}</span></button>',
            '        </div></div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>展示名称 <em>*</em></label><input class="os-input" v-model.trim="form.displayName" maxlength="64" placeholder="例如：教务处" /></div>',
            '          <div class="os-field"><label>官方头衔 / 职务</label><input class="os-input" v-model.trim="form.officialTitle" maxlength="64" placeholder="例如：学校官方、教务处" /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>运营人 / 责任主体 <em v-if="form.accountType === \'OFFICIAL_HUMAN\'">*</em></label><input class="os-input" v-model.trim="form.operatorName" maxlength="64" placeholder="真实运营人姓名或部门" /></div>',
            '          <div class="os-field"><label>登录名</label><input class="os-input" v-model.trim="form.username" :disabled="!!form.userId" maxlength="32" placeholder="留空自动生成" /></div>',
            '        </div>',
            '        <div class="os-field"><label>账号说明</label><textarea class="os-textarea" v-model.trim="form.description" maxlength="255" placeholder="对外展示的主体说明、运营范围或广告服务内容"></textarea></div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>认证状态</label><select class="os-select" v-model="form.verificationStatus"><option value="NONE">未认证</option><option value="PENDING">待审核</option><option value="APPROVED">已认证</option><option value="REJECTED">已驳回</option><option value="DISABLED">认证停用</option></select></div>',
            '          <div class="os-field"><label>账号状态</label><select class="os-select" v-model="form.status"><option value="ENABLED">正常</option><option value="DISABLED">停用</option></select></div>',
            '        </div>',
            '        <div class="os-note"><b>规则</b> 人工运营号必须填写运营人；广告服务号不会获得「官方」标识；停用后该账号不能再发布内容。</div>',
            '        <div style="display:flex;justify-content:flex-end;gap:8px"><button class="os-btn" @click="editorOpen = false">取消</button><button class="os-btn primary" :disabled="submitting" @click="submit">{{ submitting ? "提交中…" : "保存账号" }}</button></div>',
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
            var accountType = Vue.ref('');
            var status = Vue.ref('');
            var verificationStatus = Vue.ref('');
            var keyword = Vue.ref('');
            var typeCounts = Vue.reactive({ OFFICIAL_AUTO: 0, OFFICIAL_HUMAN: 0, AD_SERVICE: 0 });
            var editorOpen = Vue.ref(false);
            var submitting = Vue.ref(false);
            var form = Vue.reactive(blank());
            var publishOpen = Vue.ref(false);
            var publishing = Vue.ref(false);
            var publishAccounts = Vue.ref([]);
            var publishSections = Vue.ref([]);
            var publishForm = Vue.reactive({
                officialUserId: null, sectionId: null, title: '', content: '', topics: ''
            });

            function loadPublishOptions() {
                api.get('/api/admin/community/official-accounts?pageNum=1&pageSize=100')
                    .then(function (d) { publishAccounts.value = (d && d.records) || []; })
                    .catch(function (e) { El.ElMessage.error(e.message); });
                api.get('/api/admin/community/sections')
                    .then(function (d) { publishSections.value = d || []; })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }
            function openPublish() {
                publishForm.officialUserId = null;
                publishForm.sectionId = null;
                publishForm.title = '';
                publishForm.content = '';
                publishForm.topics = '';
                publishOpen.value = true;
                loadPublishOptions();
            }
            function submitPublish() {
                if (!publishForm.officialUserId) { El.ElMessage.warning('请选择官方账号'); return; }
                if (!publishForm.sectionId) { El.ElMessage.warning('请选择发布分区'); return; }
                if (!publishForm.title.trim()) { El.ElMessage.warning('请填写标题'); return; }
                if (!publishForm.content.trim()) { El.ElMessage.warning('请填写正文'); return; }
                var topics = publishForm.topics
                    ? publishForm.topics.split(/[，,]/).map(function (t) { return t.trim(); })
                        .filter(function (t) { return !!t; }).slice(0, 8)
                    : [];
                publishing.value = true;
                api.post('/api/admin/community/posts/official-publish', {
                    officialUserId: publishForm.officialUserId,
                    sectionId: publishForm.sectionId,
                    title: publishForm.title.trim(),
                    content: publishForm.content.trim(),
                    topics: topics.length ? topics : null
                }).then(function (result) {
                    El.ElMessage.success('官方代发成功（帖子 #' + result.postId + '，' + result.officialName + '）');
                    publishOpen.value = false;
                }).catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { publishing.value = false; });
            }

            var typeTabs = Vue.computed(function () {
                return [
                    { value: '', label: '全部' },
                    { value: 'OFFICIAL_AUTO', label: '抓取号', count: typeCounts.OFFICIAL_AUTO },
                    { value: 'OFFICIAL_HUMAN', label: '人工号', count: typeCounts.OFFICIAL_HUMAN },
                    { value: 'AD_SERVICE', label: '广告服务', count: typeCounts.AD_SERVICE }
                ];
            });
            var pendingVerify = Vue.computed(function () {
                return rows.value.filter(function (a) { return a.verificationStatus === 'PENDING'; }).length;
            });
            function typeCount(t) { return typeCounts[t] || 0; }
            function fmt(v) { return v ? String(v).replace('T', ' ').slice(5, 16) : '—'; }

            function load() {
                var p = new URLSearchParams();
                p.set('pageNum', String(pageNum.value)); p.set('pageSize', String(pageSize));
                if (accountType.value) p.set('accountType', accountType.value);
                if (status.value) p.set('status', status.value);
                if (verificationStatus.value) p.set('verificationStatus', verificationStatus.value);
                if (keyword.value) p.set('keyword', keyword.value);
                api.get('/api/admin/community/official-accounts?' + p.toString())
                    .then(function (data) {
                        rows.value = (data && data.records) || [];
                        total.value = (data && data.total) || 0;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '运营账号加载失败'); });
                // 当前页类型分布；分页场景下明确是“当前页”口径。
                Promise.all(['OFFICIAL_AUTO', 'OFFICIAL_HUMAN', 'AD_SERVICE'].map(function (t) {
                    return api.get('/api/admin/community/official-accounts?pageNum=1&pageSize=1&accountType=' + t)
                        .then(function (d) { typeCounts[t] = (d && d.total) || 0; }).catch(function () {});
                }));
            }
            function openCreate() {
                Object.assign(form, blank());
                editorOpen.value = true;
            }
            function openEdit(a) {
                Object.assign(form, blank());
                form.userId = a.userId;
                form.accountType = a.accountType;
                form.displayName = a.displayName;
                form.operatorName = a.operatorName || '';
                form.officialTitle = a.officialTitle || '';
                form.description = a.description || '';
                form.verificationStatus = a.verificationStatus || 'PENDING';
                form.status = a.status || 'ENABLED';
                form.username = a.username || '';
                editorOpen.value = true;
            }
            function submit() {
                if (!form.displayName) { El.ElMessage.warning('请填写展示名称'); return; }
                if (form.accountType === 'OFFICIAL_HUMAN' && !form.operatorName) { El.ElMessage.warning('人工运营号必须填写运营人'); return; }
                var payload = {
                    accountType: form.accountType,
                    displayName: form.displayName,
                    operatorName: form.operatorName,
                    description: form.description,
                    officialTitle: form.officialTitle,
                    verificationStatus: form.verificationStatus,
                    status: form.status,
                    username: form.username || null
                };
                submitting.value = true;
                var req = form.userId
                    ? api.post('/api/admin/community/official-accounts/' + form.userId, payload)
                    : api.post('/api/admin/community/official-accounts', payload);
                req.then(function () {
                    El.ElMessage.success(form.userId ? '账号已更新' : '账号已创建');
                    editorOpen.value = false;
                    load();
                }).catch(function (error) { El.ElMessage.error(error.message || '保存失败'); })
                    .finally(function () { submitting.value = false; });
            }
            function toggle(a) {
                var next = a.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
                api.post('/api/admin/community/official-accounts/' + a.userId + '/status', { status: next })
                    .then(function () { El.ElMessage.success('账号已' + (next === 'ENABLED' ? '启用' : '停用')); load(); })
                    .catch(function (error) { El.ElMessage.error(error.message || '更新失败'); });
            }

            load();
            return {
                rows: rows, total: total, pageNum: pageNum, totalPages: totalPages, accountType: accountType,
                status: status, verificationStatus: verificationStatus, keyword: keyword, typeTabs: typeTabs,
                typeCount: typeCount, pendingVerify: pendingVerify, load: load, fmt: fmt, editorOpen: editorOpen,
                form: form, submitting: submitting, openCreate: openCreate, openEdit: openEdit, submit: submit,
                toggle: toggle, TYPE: TYPE, VERIFY: VERIFY, STATUS: STATUS,
                publishOpen: publishOpen, publishing: publishing, publishAccounts: publishAccounts,
                publishSections: publishSections, publishForm: publishForm,
                openPublish: openPublish, submitPublish: submitPublish
            };
        }
    };
})();
