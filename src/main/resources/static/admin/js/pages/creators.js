/* 创作者运营（C10）：认证、等级、禁言/封禁、申诉。
   真实性约束：
   - 账号状态（NORMAL/MUTED/BANNED）由后端按生效处罚派生，页面不提供直接改写入口；
   - 禁言到期后由后端定时任务置为 EXPIRED 并自动恢复状态；
   - 申诉通过会同时撤销处罚，处理结果与操作人写回处罚记录。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var CERT = {
        NONE: { label: '未认证', tone: 'muted' },
        PENDING: { label: '待审核', tone: 'warn' },
        APPROVED: { label: '已认证', tone: 'ok' },
        REJECTED: { label: '已驳回', tone: 'bad' },
        DISABLED: { label: '已停用', tone: 'muted' }
    };
    var ACCOUNT = {
        NORMAL: { label: '正常', tone: 'ok' },
        MUTED: { label: '禁言中', tone: 'warn' },
        BANNED: { label: '已封禁', tone: 'bad' }
    };
    var PENALTY = {
        WARN: { label: '警告', tone: 'info' },
        MUTE: { label: '禁言', tone: 'warn' },
        BAN: { label: '封禁', tone: 'bad' }
    };
    var PENALTY_STATUS = {
        ACTIVE: { label: '生效中', tone: 'bad' },
        EXPIRED: { label: '已到期', tone: 'muted' },
        REVOKED: { label: '已撤销', tone: 'muted' }
    };
    var APPEAL = {
        NONE: { label: '未申诉', tone: 'muted' },
        PENDING: { label: '申诉中', tone: 'warn' },
        APPROVED: { label: '申诉通过', tone: 'ok' },
        REJECTED: { label: '申诉驳回', tone: 'bad' }
    };

    function blankPenalty() {
        return { penaltyType: 'MUTE', reason: '', evidenceUrl: '', startAt: '', endAt: '' };
    }

    window.AdminPages.CreatorsPage = {
        name: 'CreatorsPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>创作者运营</h1><div class="sub">认证 / 等级 / 禁言封禁 / 申诉</div></div>',
            '    <div class="r"><button class="btn2" @click="load">刷新</button><button class="btn2 pri" @click="openCreate">新建创作者档案</button></div></div>',

            '  <div class="os-grid" style="grid-template-columns:repeat(5,1fr)">',
            '    <div class="os-kpi"><div class="k-label"><span class="dot"></span>创作者</div><div class="k-val">{{ total }}<small>位</small></div><div class="k-foot"><span>当前筛选结果</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>已认证</div><div class="k-val">{{ countCert(\'APPROVED\') }}<small>位</small></div><div class="k-foot"><span>本页统计</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>待审核</div><div class="k-val">{{ countCert(\'PENDING\') }}<small>位</small></div><div class="k-foot"><span>需处理</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>禁言中</div><div class="k-val">{{ countAccount(\'MUTED\') }}<small>位</small></div><div class="k-foot"><span>到期自动恢复</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-bad)"></span>已封禁</div><div class="k-val">{{ countAccount(\'BANNED\') }}<small>位</small></div><div class="k-foot"><span>需人工解除</span></div></div>',
            '  </div>',

            '  <div class="os-panel" style="flex:1;min-height:0;display:flex;flex-direction:column">',
            '    <div class="os-panel-h" style="flex-wrap:wrap;row-gap:8px">',
            '      <select class="os-select" style="width:126px" v-model="certFilter" @change="reloadPage"><option value="">全部认证</option><option v-for="(v,k) in CERT" :key="k" :value="k">{{ v.label }}</option></select>',
            '      <select class="os-select" style="width:120px" v-model="accountFilter" @change="reloadPage"><option value="">全部状态</option><option v-for="(v,k) in ACCOUNT" :key="k" :value="k">{{ v.label }}</option></select>',
            '      <select class="os-select" style="width:104px" v-model="levelFilter" @change="reloadPage"><option value="">全部等级</option><option v-for="n in 5" :key="n" :value="n">Lv.{{ n }}</option></select>',
            '      <div class="srch" style="width:220px;background:var(--tk-input)"><el-icon :size="13"><Search /></el-icon><input v-model.trim="keyword" placeholder="搜索昵称 / 用户 ID" @keyup.enter="reloadPage" /></div>',
            '      <span style="flex:1"></span>',
            '      <span style="font-size:11px;color:var(--tk-text3)">共 {{ total }} 条</span>',
            '    </div>',
            '    <div style="overflow:auto;flex:1">',
            '      <table class="pk" style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr><th style="width:20%">创作者</th><th style="width:13%">认证</th><th style="width:8%">等级</th><th style="width:9%">积分</th><th style="width:10%">账号状态</th><th style="width:15%">禁言至 / 处罚</th><th style="width:13%">最近活跃</th><th style="width:12%;text-align:right">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-for="c in rows" :key="c.id">',
            '            <td><div style="display:flex;align-items:center;gap:8px"><span class="os-mono" style="width:22px;height:22px;border-radius:50%;background:var(--tk-subtle);display:inline-flex;align-items:center;justify-content:center;font-size:11px">{{ (c.displayName || \'?\').slice(0,1) }}</span>',
            '              <div><b style="font-weight:550;color:var(--tk-text)">{{ c.displayName || c.userId }}</b><div style="font-size:11px;color:var(--tk-text3)">@{{ c.username || \'-\' }} · {{ c.phoneMasked || \'未绑手机\' }}</div></div></div></td>',
            '            <td><span class="os-tag" :class="(CERT[c.certificationStatus]||{}).tone || \'muted\'">{{ (CERT[c.certificationStatus]||{}).label || c.certificationStatus }}</span></td>',
            '            <td><span class="os-tag">Lv.{{ c.level }}</span></td>',
            '            <td class="os-mono" style="color:var(--tk-text3)">{{ c.score }}</td>',
            '            <td><span class="os-tag" :class="(ACCOUNT[c.status]||{}).tone || \'muted\'">{{ (ACCOUNT[c.status]||{}).label || c.status }}</span></td>',
            '            <td style="font-size:11px;color:var(--tk-text3)">',
            '              <span v-if="c.status === \'MUTED\' && c.mutedUntil">至 {{ fmt(c.mutedUntil) }}</span>',
            '              <span v-else-if="c.status === \'BANNED\'" :title="c.bannedReason || \'\'">已限制发布</span>',
            '              <span v-else>—</span>',
            '              <div v-if="c.activePenaltyCount" style="margin-top:3px;color:var(--tk-warn)">生效处罚 {{ c.activePenaltyCount }} 条</div>',
            '            </td>',
            '            <td style="color:var(--tk-text3)">{{ c.lastActiveAt ? fmt(c.lastActiveAt) : "—" }}</td>',
            '            <td style="text-align:right;white-space:nowrap">',
            '              <span style="font-size:11px;color:var(--tk-info);cursor:pointer;margin-left:8px" @click="openDetail(c)">详情</span>',
            '              <span style="font-size:11px;color:var(--tk-ok);cursor:pointer;margin-left:8px" @click="openCertify(c)">认证</span>',
            '              <span style="font-size:11px;color:var(--tk-bad);cursor:pointer;margin-left:8px" @click="openPenalty(c)">处罚</span>',
            '            </td>',
            '          </tr>',
            '          <tr v-if="!loading && !rows.length"><td colspan="8" class="os-empty">暂无创作者档案。创作者首次发布内容或点击「新建创作者档案」后出现。</td></tr>',
            '          <tr v-if="loading"><td colspan="8" class="os-empty">加载中…</td></tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',
            '    <div style="display:flex;align-items:center;gap:8px;justify-content:flex-end;padding:10px 14px">',
            '      <span style="font-size:11px;color:var(--tk-text3)">第 {{ page }} 页</span>',
            '      <button class="os-btn" :disabled="page <= 1" @click="page--; load()">上一页</button>',
            '      <button class="os-btn" :disabled="page * 20 >= total" @click="page++; load()">下一页</button>',
            '    </div>',
            '  </div>',

            /* ---------------- 详情 ---------------- */
            '  <div v-if="detailOpen" class="cmd-mask" style="padding-top:5vh" @click.self="detailOpen = false">',
            '    <div class="cmd-panel" style="width:820px;max-height:88vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">创作者详情 · {{ detail.displayName }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="detailOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:16px 20px;display:flex;flex-direction:column;gap:14px">',
            '        <div class="os-grid" style="grid-template-columns:repeat(4,1fr)">',
            '          <div class="os-kpi"><div class="k-label">等级</div><div class="k-val">Lv.{{ detail.level }}</div></div>',
            '          <div class="os-kpi"><div class="k-label">积分</div><div class="k-val">{{ detail.score }}</div></div>',
            '          <div class="os-kpi"><div class="k-label">认证</div><div class="k-val" style="font-size:16px">{{ (CERT[detail.certificationStatus]||{}).label }}</div></div>',
            '          <div class="os-kpi"><div class="k-label">状态</div><div class="k-val" style="font-size:16px">{{ (ACCOUNT[detail.status]||{}).label }}</div></div>',
            '        </div>',
            '        <div style="display:flex;gap:8px"><button class="os-btn" @click="openEditLevel">调整等级 / 昵称</button><button class="os-btn" @click="openCertify(detail)">认证审核</button><button class="os-btn" @click="openPenalty(detail)">新增处罚</button></div>',
            '        <div>',
            '          <div class="os-section-head" style="font-size:12px;color:var(--tk-text2);margin-bottom:8px">处罚与申诉记录</div>',
            '          <table class="pk" style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '            <thead><tr><th style="width:10%">类型</th><th style="width:26%">原因</th><th style="width:16%">时间</th><th style="width:11%">处罚状态</th><th style="width:12%">申诉</th><th style="width:25%;text-align:right">操作</th></tr></thead>',
            '            <tbody>',
            '              <tr v-for="p in detail.penalties || []" :key="p.id">',
            '                <td><span class="os-tag" :class="(PENALTY[p.penaltyType]||{}).tone">{{ (PENALTY[p.penaltyType]||{}).label || p.penaltyType }}</span></td>',
            '                <td style="color:var(--tk-text2)"><div style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ p.reason }}</div>',
            '                  <div v-if="p.evidenceUrl" style="font-size:11px"><a :href="p.evidenceUrl" target="_blank" style="color:var(--tk-info)">证据链接</a></div></td>',
            '                <td style="font-size:11px;color:var(--tk-text3)">{{ fmt(p.startAt) }}<br/>{{ p.endAt ? fmt(p.endAt) : "永久" }}</td>',
            '                <td><span class="os-tag" :class="(PENALTY_STATUS[p.status]||{}).tone || \'muted\'">{{ (PENALTY_STATUS[p.status]||{}).label || p.status }}</span></td>',
            '                <td><span class="os-tag" :class="(APPEAL[p.appealStatus]||{}).tone || \'muted\'">{{ (APPEAL[p.appealStatus]||{}).label || p.appealStatus }}</span><div v-if="p.appealResult" style="font-size:10px;color:var(--tk-text3);margin-top:2px">{{ p.appealResult }}</div></td>',
            '                <td style="text-align:right;white-space:nowrap">',
            '                  <span v-if="p.status === \'ACTIVE\'" style="font-size:11px;color:var(--tk-warn);cursor:pointer;margin-left:6px" @click="revoke(p)">撤销</span>',
            '                  <span style="font-size:11px;color:var(--tk-info);cursor:pointer;margin-left:6px" @click="openAppeal(p)">处理申诉</span>',
            '                </td>',
            '              </tr>',
            '              <tr v-if="!(detail.penalties || []).length"><td colspan="6" class="os-empty">暂无处罚记录</td></tr>',
            '            </tbody>',
            '          </table>',
            '        </div>',
            '      </div>',
            '    </div>',
            '  </div>',

            /* ---------------- 新建档案 ---------------- */
            '  <div v-if="createOpen" class="cmd-mask" style="padding-top:10vh" @click.self="createOpen = false">',
            '    <div class="cmd-panel" style="width:560px;max-height:80vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">新建创作者档案</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="createOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-field"><label>选择用户 <em>*</em></label>',
'          <div style="display:flex;gap:8px"><input class="os-input" style="flex:1" v-model.trim="userSearch" placeholder="输入昵称 / 用户名 / 手机号搜索" @keyup.enter="searchUsers" /><button class="os-btn" @click="searchUsers">{{ userSearching ? "搜索中…" : "搜索" }}</button></div>',
'          <select class="os-select" style="margin-top:8px" v-model="createForm.userId"><option value="">请选择用户（仅当前校区启用用户）</option><option v-for="u in userOptions" :key="u.userId" :value="String(u.userId)">{{ u.displayName }} · @{{ u.username }} · #{{ u.userId }}</option></select>',
'          <div style="font-size:11px;color:var(--tk-text3);margin-top:6px">列表来自 /api/admin/users（当前校区、启用状态、最多 20 条）；已有档案的用户创建会被拒绝并提示。</div>',
'        </div>',
            '        <div class="os-field"><label>展示昵称（留空取用户昵称）</label><input class="os-input" v-model.trim="createForm.displayName" maxlength="100" /></div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>初始等级</label><select class="os-select" v-model.number="createForm.level"><option v-for="n in 5" :key="n" :value="n">Lv.{{ n }}</option></select></div>',
            '          <div class="os-field"><label>初始积分</label><input class="os-input" type="number" min="0" v-model.number="createForm.score" /></div>',
            '        </div>',
            '        <div class="os-field"><label>备注</label><input class="os-input" v-model.trim="createForm.remark" maxlength="500" /></div>',
            '        <div style="display:flex;gap:8px;justify-content:flex-end"><button class="os-btn" @click="createOpen = false">取消</button><button class="os-btn primary" :disabled="createSaving" @click="submitCreate">{{ createSaving ? "提交中…" : "创建" }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            /* ---------------- 认证 ---------------- */
            '  <div v-if="certifyOpen" class="cmd-mask" style="padding-top:12vh" @click.self="certifyOpen = false">',
            '    <div class="cmd-panel" style="width:520px">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">认证审核 · {{ certifyTarget.displayName }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="certifyOpen = false">✕</span></div>',
            '      <div style="padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-field"><label>审核决定</label><select class="os-select" v-model="certifyForm.decision"><option value="APPROVED">通过认证</option><option value="REJECTED">驳回</option><option value="PENDING">转待审核</option><option value="DISABLED">停用认证</option></select></div>',
            '        <div class="os-field"><label>备注 / 驳回原因</label><textarea class="os-textarea" v-model.trim="certifyForm.note" maxlength="255" style="height:80px"></textarea></div>',
            '        <div style="display:flex;gap:8px;justify-content:flex-end"><button class="os-btn" @click="certifyOpen = false">取消</button><button class="os-btn primary" :disabled="certifySaving" @click="submitCertify">{{ certifySaving ? "提交中…" : "提交" }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            /* ---------------- 处罚 ---------------- */
            '  <div v-if="penaltyOpen" class="cmd-mask" style="padding-top:10vh" @click.self="penaltyOpen = false">',
            '    <div class="cmd-panel" style="width:560px">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">新增处罚 · {{ penaltyTarget.displayName }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="penaltyOpen = false">✕</span></div>',
            '      <div style="padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-field"><label>处罚类型</label><select class="os-select" v-model="penaltyForm.penaltyType"><option value="WARN">警告（不改变账号状态）</option><option value="MUTE">禁言（到期自动恢复）</option><option value="BAN">封禁（留空结束时间为永久）</option></select></div>',
            '        <div class="os-field"><label>原因 <em>*</em></label><textarea class="os-textarea" v-model.trim="penaltyForm.reason" maxlength="500" style="height:80px" placeholder="请写明违规事实与依据"></textarea></div>',
            '        <div class="os-field"><label>证据链接</label><input class="os-input" v-model.trim="penaltyForm.evidenceUrl" maxlength="500" placeholder="https://..." /></div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>开始时间（留空=现在）</label><input class="os-input" type="datetime-local" v-model="penaltyForm.startAt" /></div>',
            '          <div class="os-field"><label>结束时间（禁言必填；封禁可空）</label><input class="os-input" type="datetime-local" v-model="penaltyForm.endAt" /></div>',
            '        </div>',
            '        <div class="os-note"><b>状态派生</b> 禁言/封禁创建后，档案状态自动变为禁言中/已封禁；禁言到期由定时任务自动恢复，封禁需人工撤销。</div>',
            '        <div style="display:flex;gap:8px;justify-content:flex-end"><button class="os-btn" @click="penaltyOpen = false">取消</button><button class="os-btn primary" :disabled="penaltySaving" @click="submitPenalty">{{ penaltySaving ? "提交中…" : "提交处罚" }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            /* ---------------- 申诉处理 ---------------- */
            '  <div v-if="appealOpen" class="cmd-mask" style="padding-top:14vh" @click.self="appealOpen = false">',
            '    <div class="cmd-panel" style="width:520px">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">处理申诉</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="appealOpen = false">✕</span></div>',
            '      <div style="padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-note">处罚：{{ (PENALTY[appealTarget.penaltyType]||{}).label }} · {{ appealTarget.reason }}</div>',
            '        <div class="os-field"><label>处理结论</label><select class="os-select" v-model="appealForm.approve"><option :value="true">支持申诉（撤销处罚）</option><option :value="false">驳回申诉</option></select></div>',
            '        <div class="os-field"><label>结论说明</label><textarea class="os-textarea" v-model.trim="appealForm.result" maxlength="500" style="height:80px"></textarea></div>',
            '        <div style="display:flex;gap:8px;justify-content:flex-end"><button class="os-btn" @click="appealOpen = false">取消</button><button class="os-btn primary" :disabled="appealSaving" @click="submitAppeal">{{ appealSaving ? "提交中…" : "提交" }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            /* ---------------- 等级/昵称 ---------------- */
            '  <div v-if="levelOpen" class="cmd-mask" style="padding-top:14vh" @click.self="levelOpen = false">',
            '    <div class="cmd-panel" style="width:520px">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">调整等级 / 昵称</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="levelOpen = false">✕</span></div>',
            '      <div style="padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-field"><label>展示昵称</label><input class="os-input" v-model.trim="levelForm.displayName" maxlength="100" /></div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>等级</label><select class="os-select" v-model.number="levelForm.level"><option v-for="n in 5" :key="n" :value="n">Lv.{{ n }}</option></select></div>',
            '          <div class="os-field"><label>积分</label><input class="os-input" type="number" min="0" v-model.number="levelForm.score" /></div>',
            '        </div>',
            '        <div class="os-field"><label>备注</label><input class="os-input" v-model.trim="levelForm.remark" maxlength="500" /></div>',
            '        <div style="display:flex;gap:8px;justify-content:flex-end"><button class="os-btn" @click="levelOpen = false">取消</button><button class="os-btn primary" :disabled="levelSaving" @click="submitLevel">{{ levelSaving ? "提交中…" : "保存" }}</button></div>',
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
            var page = Vue.ref(1);
            var loading = Vue.ref(false);
            var keyword = Vue.ref('');
            var certFilter = Vue.ref('');
            var accountFilter = Vue.ref('');
            var levelFilter = Vue.ref('');
            var detailOpen = Vue.ref(false);
            var detail = Vue.reactive({ id: null, displayName: '', level: 1, score: 0, certificationStatus: 'NONE', status: 'NORMAL', penalties: [] });
            var createOpen = Vue.ref(false);
            var createSaving = Vue.ref(false);
            var createForm = Vue.reactive({ userId: '', displayName: '', level: 1, score: 0, remark: '' });
            var userSearch = Vue.ref('');
            var userOptions = Vue.ref([]);
            var userSearching = Vue.ref(false);
            var certifyOpen = Vue.ref(false);
            var certifySaving = Vue.ref(false);
            var certifyTarget = Vue.reactive({ id: null, displayName: '' });
            var certifyForm = Vue.reactive({ decision: 'APPROVED', note: '' });
            var penaltyOpen = Vue.ref(false);
            var penaltySaving = Vue.ref(false);
            var penaltyTarget = Vue.reactive({ id: null, displayName: '' });
            var penaltyForm = Vue.reactive(blankPenalty());
            var appealOpen = Vue.ref(false);
            var appealSaving = Vue.ref(false);
            var appealTarget = Vue.reactive({ id: null, penaltyType: 'MUTE', reason: '' });
            var appealForm = Vue.reactive({ approve: true, result: '' });
            var levelOpen = Vue.ref(false);
            var levelSaving = Vue.ref(false);
            var levelForm = Vue.reactive({ displayName: '', level: 1, score: 0, remark: '' });

            function fmt(v) { return v ? String(v).replace('T', ' ').slice(0, 16) : '—'; }
            function countCert(state) { return rows.value.filter(function (r) { return r.certificationStatus === state; }).length; }
            function countAccount(state) { return rows.value.filter(function (r) { return r.status === state; }).length; }

            function reloadPage() { page.value = 1; load(); }
            function load() {
                loading.value = true;
                var qs = ['pageNum=' + page.value, 'pageSize=20'];
                if (keyword.value) qs.push('keyword=' + encodeURIComponent(keyword.value));
                if (certFilter.value) qs.push('certificationStatus=' + encodeURIComponent(certFilter.value));
                if (accountFilter.value) qs.push('status=' + encodeURIComponent(accountFilter.value));
                if (levelFilter.value) qs.push('level=' + encodeURIComponent(levelFilter.value));
                api.get('/api/admin/creators?' + qs.join('&'))
                    .then(function (data) {
                        rows.value = (data && data.records) || [];
                        total.value = (data && data.total) || 0;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '创作者加载失败'); })
                    .finally(function () { loading.value = false; });
            }
            function openDetail(row) {
                api.get('/api/admin/creators/' + row.id)
                    .then(function (data) { Object.assign(detail, data); detailOpen.value = true; })
                    .catch(function (error) { El.ElMessage.error(error.message || '详情加载失败'); });
            }
            function openCreate() {
                Object.assign(createForm, { userId: '', displayName: '', level: 1, score: 0, remark: '' });
                userSearch.value = '';
                createOpen.value = true;
                searchUsers();
            }
            function searchUsers() {
                userSearching.value = true;
                var qs = ['pageNum=1', 'pageSize=20', 'status=ENABLED'];
                if (userSearch.value) qs.push('keyword=' + encodeURIComponent(userSearch.value));
                api.get('/api/admin/users?' + qs.join('&'))
                    .then(function (data) {
                        userOptions.value = (data && data.records) || [];
                        if (!userOptions.value.length) El.ElMessage.info('没有匹配的启用用户');
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '用户搜索失败'); })
                    .finally(function () { userSearching.value = false; });
            }
            function submitCreate() {
                if (!createForm.userId) { El.ElMessage.warning('请填写用户 ID'); return; }
                createSaving.value = true;
                api.post('/api/admin/creators', {
                    userId: Number(createForm.userId), displayName: createForm.displayName,
                    level: createForm.level, score: createForm.score, remark: createForm.remark
                }).then(function () {
                    El.ElMessage.success('创作者档案已创建');
                    createOpen.value = false;
                    load();
                }).catch(function (error) { El.ElMessage.error(error.message || '创建失败'); })
                    .finally(function () { createSaving.value = false; });
            }
            function openCertify(row) {
                certifyTarget.id = row.id;
                certifyTarget.displayName = row.displayName || row.userId;
                certifyForm.decision = row.certificationStatus === 'PENDING' ? 'APPROVED' : 'APPROVED';
                certifyForm.note = '';
                certifyOpen.value = true;
            }
            function submitCertify() {
                certifySaving.value = true;
                api.post('/api/admin/creators/' + certifyTarget.id + '/certify', {
                    decision: certifyForm.decision, note: certifyForm.note
                }).then(function () {
                    El.ElMessage.success('认证状态已更新');
                    certifyOpen.value = false;
                    load();
                    if (detailOpen.value) openDetail({ id: certifyTarget.id });
                }).catch(function (error) { El.ElMessage.error(error.message || '提交失败'); })
                    .finally(function () { certifySaving.value = false; });
            }
            function openPenalty(row) {
                penaltyTarget.id = row.id;
                penaltyTarget.displayName = row.displayName || row.userId;
                Object.assign(penaltyForm, blankPenalty());
                penaltyOpen.value = true;
            }
            function submitPenalty() {
                if (!penaltyForm.reason) { El.ElMessage.warning('请填写处罚原因'); return; }
                if (penaltyForm.penaltyType === 'MUTE' && !penaltyForm.endAt) {
                    El.ElMessage.warning('禁言必须填写结束时间（或留空由后端默认 24 小时）');
                }
                penaltySaving.value = true;
                api.post('/api/admin/creators/' + penaltyTarget.id + '/penalties', {
                    penaltyType: penaltyForm.penaltyType,
                    reason: penaltyForm.reason,
                    evidenceUrl: penaltyForm.evidenceUrl,
                    startAt: penaltyForm.startAt ? penaltyForm.startAt + ':00' : null,
                    endAt: penaltyForm.endAt ? penaltyForm.endAt + ':00' : null
                }).then(function () {
                    El.ElMessage.success('处罚已创建，账号状态已重算');
                    penaltyOpen.value = false;
                    load();
                    if (detailOpen.value) openDetail({ id: penaltyTarget.id });
                }).catch(function (error) { El.ElMessage.error(error.message || '提交失败'); })
                    .finally(function () { penaltySaving.value = false; });
            }
            function revoke(penalty) {
                El.ElMessageBox.confirm('撤销后该处罚立即失效，账号状态按剩余处罚重算，确定继续？', '撤销处罚', { type: 'warning' })
                    .then(function () { return api.post('/api/admin/creators/penalties/' + penalty.id + '/revoke'); })
                    .then(function () {
                        El.ElMessage.success('处罚已撤销');
                        if (detail.id) openDetail({ id: detail.id });
                        load();
                    })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '撤销失败'); });
            }
            function openAppeal(penalty) {
                appealTarget.id = penalty.id;
                appealTarget.penaltyType = penalty.penaltyType;
                appealTarget.reason = penalty.reason;
                appealForm.approve = true;
                appealForm.result = '';
                appealOpen.value = true;
            }
            function submitAppeal() {
                appealSaving.value = true;
                api.post('/api/admin/creators/penalties/' + appealTarget.id + '/appeal', {
                    approve: appealForm.approve, result: appealForm.result
                }).then(function () {
                    El.ElMessage.success(appealForm.approve ? '申诉已通过，处罚已撤销' : '申诉已驳回');
                    appealOpen.value = false;
                    if (detail.id) openDetail({ id: detail.id });
                    load();
                }).catch(function (error) { El.ElMessage.error(error.message || '提交失败'); })
                    .finally(function () { appealSaving.value = false; });
            }
            function openEditLevel() {
                levelForm.displayName = detail.displayName || '';
                levelForm.level = detail.level || 1;
                levelForm.score = detail.score || 0;
                levelForm.remark = detail.remark || '';
                levelOpen.value = true;
            }
            function submitLevel() {
                levelSaving.value = true;
                api.put('/api/admin/creators/' + detail.id, {
                    displayName: levelForm.displayName, level: levelForm.level,
                    score: levelForm.score, remark: levelForm.remark
                }).then(function () {
                    El.ElMessage.success('创作者资料已更新');
                    levelOpen.value = false;
                    openDetail({ id: detail.id });
                    load();
                }).catch(function (error) { El.ElMessage.error(error.message || '保存失败'); })
                    .finally(function () { levelSaving.value = false; });
            }

            load();

            return {
                rows: rows, total: total, page: page, loading: loading,
                keyword: keyword, certFilter: certFilter, accountFilter: accountFilter, levelFilter: levelFilter,
                detailOpen: detailOpen, detail: detail,
                createOpen: createOpen, createSaving: createSaving, createForm: createForm,
                userSearch: userSearch, userOptions: userOptions, userSearching: userSearching,
                searchUsers: searchUsers,
                certifyOpen: certifyOpen, certifySaving: certifySaving, certifyTarget: certifyTarget, certifyForm: certifyForm,
                penaltyOpen: penaltyOpen, penaltySaving: penaltySaving, penaltyTarget: penaltyTarget, penaltyForm: penaltyForm,
                appealOpen: appealOpen, appealSaving: appealSaving, appealTarget: appealTarget, appealForm: appealForm,
                levelOpen: levelOpen, levelSaving: levelSaving, levelForm: levelForm,
                load: load, reloadPage: reloadPage, fmt: fmt, countCert: countCert, countAccount: countAccount,
                openDetail: openDetail, openCreate: openCreate, submitCreate: submitCreate,
                openCertify: openCertify, submitCertify: submitCertify,
                openPenalty: openPenalty, submitPenalty: submitPenalty, revoke: revoke,
                openAppeal: openAppeal, submitAppeal: submitAppeal,
                openEditLevel: openEditLevel, submitLevel: submitLevel,
                CERT: CERT, ACCOUNT: ACCOUNT, PENALTY: PENALTY, PENALTY_STATUS: PENALTY_STATUS, APPEAL: APPEAL
            };
        }
    };
})();
