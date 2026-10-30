/* 内容运营（A03/C09）：推荐位 / 活动 / 专题排期、上下线、预览。
   真实性约束：
   - 运行态由后端按状态 + 时间窗派生（ONLINE/NOT_STARTED/EXPIRED/OFFLINE/DRAFT），页面不自行推断；
   - 上线与预览共用同一套校验（图片/跳转/时间窗），预览通过才允许上线；
   - 预览中的冲突项来自同位置真实在线内容 / 同运营位真实已上线排期，非示例数据。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var SLOT_TYPES = { BANNER: 'Banner', POPUP: '弹窗', RECOMMEND: '推荐位', TOPIC: '专题', ACTIVITY: '活动' };
    var POSITIONS = {
        HOME_TOP: '首页顶部', HOME_POPUP: '首页弹窗', SERVICE_HALL: '服务大厅',
        COMMUNITY_FEED: '社区信息流', PROFILE: '个人中心'
    };
    var TARGET_TYPES = { POST: '帖子', ACTIVITY: '活动', TOPIC: '专题', URL: '外部链接', NONE: '不跳转' };
    var SLOT_STATUS = {
        DRAFT: { label: '草稿', tone: 'muted' }, ONLINE: { label: '在线', tone: 'ok' }, OFFLINE: { label: '已下线', tone: 'warn' }
    };
    var RUNTIME_STATE = {
        ONLINE: { label: '投放中', tone: 'ok' }, NOT_STARTED: { label: '未开始', tone: 'warn' },
        EXPIRED: { label: '已过期', tone: 'bad' }, OFFLINE: { label: '已下线', tone: 'muted' },
        DRAFT: { label: '草稿', tone: 'muted' }, ONGOING: { label: '进行中', tone: 'ok' },
        PLANNED: { label: '已排期', tone: 'info' }
    };
    var ENTRY_TYPES = { ACTIVITY: '活动', TOPIC: '专题', RECOMMEND: '推荐', PROMOTION: '促销' };
    var CALENDAR_STATUS = {
        PLANNED: { label: '已排期', tone: 'info' }, PUBLISHED: { label: '已上线', tone: 'ok' }, OFFLINE: { label: '已下线', tone: 'muted' }
    };

    function pad(n) { return n < 10 ? '0' + n : '' + n; }
    function localInput(d) {
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    }
    function blankSlot() {
        var now = new Date();
        var end = new Date(now.getTime() + 14 * 86400000);
        return {
            id: null, slotCode: '', slotName: '', slotType: 'BANNER', position: 'HOME_TOP',
            title: '', subtitle: '', imageUrl: '', targetType: 'NONE', targetValue: '',
            weight: 0, sortOrder: 0, startAt: localInput(now), endAt: localInput(end), remark: ''
        };
    }
    function blankEntry() {
        var now = new Date();
        var end = new Date(now.getTime() + 7 * 86400000);
        return {
            id: null, slotId: null, entryType: 'ACTIVITY', title: '', summary: '', coverUrl: '',
            startAt: localInput(now), endAt: localInput(end), owner: '', remark: ''
        };
    }

    window.AdminPages.ContentCalendarPage = {
        name: 'ContentCalendarPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>内容运营</h1><div class="sub">推荐位 / 活动 / 专题排期与上下线</div></div>',
            '    <div class="r"><button class="btn2" @click="reloadAll">刷新</button>',
            '      <button class="btn2" @click="openCreateSlot">新建运营位</button>',
            '      <button class="btn2 pri" @click="openCreateEntry">新建排期</button></div></div>',

            '  <div class="os-grid" style="grid-template-columns:repeat(5,1fr)">',
            '    <div class="os-kpi"><div class="k-label"><span class="dot"></span>运营位</div><div class="k-val">{{ slots.length }}<small>个</small></div><div class="k-foot"><span>含草稿与已下线</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>在线投放</div><div class="k-val">{{ countSlotState(\'ONLINE\') }}<small>个</small></div><div class="k-foot"><span>当前时间窗内</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>待生效/已过期</div><div class="k-val">{{ countSlotState(\'NOT_STARTED\') + countSlotState(\'EXPIRED\') }}<small>个</small></div><div class="k-foot"><span>需要检查时间窗</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-info)"></span>排期条目</div><div class="k-val">{{ entries.length }}<small>条</small></div><div class="k-foot"><span>按开始时间排序</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-bad)"></span>上线中排期</div><div class="k-val">{{ countEntryState(\'ONGOING\') + countEntryState(\'NOT_STARTED\') }}<small>条</small></div><div class="k-foot"><span>已发布未下线</span></div></div>',
            '  </div>',

            '  <div class="os-panel" style="flex:1;min-height:0;display:flex;flex-direction:column">',
            '    <div class="os-panel-h" style="flex-wrap:wrap;row-gap:8px">',
            '      <div class="tabs" style="display:flex;gap:4px">',
            '        <button class="tab" :class="{ on: activeTab === \'slots\' }" @click="activeTab = \'slots\'">运营位</button>',
            '        <button class="tab" :class="{ on: activeTab === \'calendar\' }" @click="activeTab = \'calendar\'">内容日历</button>',
            '      </div>',
            '      <span style="flex:1"></span>',
            '      <template v-if="activeTab === \'slots\'">',
            '        <select class="os-select" style="width:118px" v-model="slotType" @change="loadSlots"><option value="">全部类型</option><option v-for="(v,k) in SLOT_TYPES" :key="k" :value="k">{{ v }}</option></select>',
            '        <select class="os-select" style="width:112px" v-model="slotStatus" @change="loadSlots"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="ONLINE">在线</option><option value="OFFLINE">已下线</option></select>',
            '        <div class="srch" style="width:200px;background:var(--tk-input)"><el-icon :size="13"><Search /></el-icon><input v-model.trim="slotKeyword" placeholder="搜索标题 / 编码" @keyup.enter="loadSlots" /></div>',
            '      </template>',
            '      <template v-else>',
            '        <input class="os-input" style="width:180px" type="datetime-local" v-model="rangeFrom" />',
            '        <span style="font-size:12px;color:var(--tk-text3)">至</span>',
            '        <input class="os-input" style="width:180px" type="datetime-local" v-model="rangeTo" />',
            '        <select class="os-select" style="width:112px" v-model="entryType" @change="loadEntries"><option value="">全部类型</option><option v-for="(v,k) in ENTRY_TYPES" :key="k" :value="k">{{ v }}</option></select>',
            '        <button class="btn2" @click="loadEntries">查询</button>',
            '      </template>',
            '    </div>',

            /* ---------------- 运营位 ---------------- */
            '    <div v-if="activeTab === \'slots\'" style="overflow:auto;flex:1">',
            '      <table class="pk" style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr><th style="width:14%">编码 / 类型</th><th style="width:22%">标题</th><th style="width:12%">位置</th><th style="width:10%">状态</th><th style="width:10%">运行态</th><th style="width:17%">时间窗</th><th style="width:15%;text-align:right">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-for="s in slots" :key="s.id">',
            '            <td><div class="os-mono" style="color:var(--tk-text)">{{ s.slotCode }}</div><div style="font-size:11px;color:var(--tk-text3);margin-top:2px">{{ SLOT_TYPES[s.slotType] || s.slotType }}</div></td>',
            '            <td><b style="font-weight:550">{{ s.title }}</b><div style="font-size:11px;color:var(--tk-text3);margin-top:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ s.subtitle || "—" }}</div></td>',
            '            <td style="color:var(--tk-text3)">{{ POSITIONS[s.position] || s.position }}</td>',
            '            <td><span class="os-tag" :class="(SLOT_STATUS[s.status]||{}).tone || \'muted\'">{{ (SLOT_STATUS[s.status]||{}).label || s.status }}</span></td>',
            '            <td><span class="os-tag" :class="(RUNTIME_STATE[s.runtimeState]||{}).tone || \'muted\'">{{ (RUNTIME_STATE[s.runtimeState]||{}).label || s.runtimeState }}</span></td>',
            '            <td style="font-size:11px;color:var(--tk-text3)">{{ s.startAt ? fmt(s.startAt) : "立即" }}<br/>{{ s.endAt ? fmt(s.endAt) : "长期" }}</td>',
            '            <td style="text-align:right;white-space:nowrap">',
            '              <span style="font-size:11px;color:var(--tk-info);cursor:pointer;margin-left:8px" @click="openSlotEdit(s)">编辑</span>',
            '              <span style="font-size:11px;color:var(--tk-text2);cursor:pointer;margin-left:8px" @click="previewSlot(s)">预览</span>',
            '              <span v-if="s.status !== \'ONLINE\'" style="font-size:11px;color:var(--tk-ok);cursor:pointer;margin-left:8px" @click="onlineSlot(s)">上线</span>',
            '              <span v-else style="font-size:11px;color:var(--tk-warn);cursor:pointer;margin-left:8px" @click="offlineSlot(s)">下线</span>',
            '              <span style="font-size:11px;color:var(--tk-bad);cursor:pointer;margin-left:8px" @click="removeSlot(s)">删除</span>',
            '            </td>',
            '          </tr>',
            '          <tr v-if="!slotLoading && !slots.length"><td colspan="7" class="os-empty">暂无运营位，点击「新建运营位」开始</td></tr>',
            '          <tr v-if="slotLoading"><td colspan="7" class="os-empty">加载中…</td></tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',

            /* ---------------- 内容日历 ---------------- */
            '    <div v-if="activeTab === \'calendar\'" style="overflow:auto;flex:1">',
            '      <table class="pk" style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr><th style="width:10%">类型</th><th style="width:24%">标题</th><th style="width:16%">绑定运营位</th><th style="width:18%">时间窗</th><th style="width:10%">状态</th><th style="width:10%">运行态</th><th style="width:12%;text-align:right">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-for="e in entries" :key="e.id">',
            '            <td><span class="os-tag">{{ ENTRY_TYPES[e.entryType] || e.entryType }}</span></td>',
            '            <td><b style="font-weight:550">{{ e.title }}</b><div style="font-size:11px;color:var(--tk-text3);margin-top:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ e.summary || "—" }}</div></td>',
            '            <td style="color:var(--tk-text3)">{{ e.slotName || "未绑定" }}</td>',
            '            <td style="font-size:11px;color:var(--tk-text3)">{{ fmt(e.startAt) }}<br/>{{ fmt(e.endAt) }}</td>',
            '            <td><span class="os-tag" :class="(CALENDAR_STATUS[e.status]||{}).tone || \'muted\'">{{ (CALENDAR_STATUS[e.status]||{}).label || e.status }}</span></td>',
            '            <td><span class="os-tag" :class="(RUNTIME_STATE[e.runtimeState]||{}).tone || \'muted\'">{{ (RUNTIME_STATE[e.runtimeState]||{}).label || e.runtimeState }}</span></td>',
            '            <td style="text-align:right;white-space:nowrap">',
            '              <span v-if="e.status === \'PLANNED\'" style="font-size:11px;color:var(--tk-info);cursor:pointer;margin-left:6px" @click="openEntryEdit(e)">编辑</span>',
            '              <span style="font-size:11px;color:var(--tk-text2);cursor:pointer;margin-left:6px" @click="previewEntry(e)">预览</span>',
            '              <span v-if="e.status !== \'PUBLISHED\'" style="font-size:11px;color:var(--tk-ok);cursor:pointer;margin-left:6px" @click="publishEntry(e)">上线</span>',
            '              <span v-else style="font-size:11px;color:var(--tk-warn);cursor:pointer;margin-left:6px" @click="offlineEntry(e)">下线</span>',
            '              <span style="font-size:11px;color:var(--tk-bad);cursor:pointer;margin-left:6px" @click="removeEntry(e)">删除</span>',
            '            </td>',
            '          </tr>',
            '          <tr v-if="!entryLoading && !entries.length"><td colspan="7" class="os-empty">所选时间范围内暂无排期</td></tr>',
            '          <tr v-if="entryLoading"><td colspan="7" class="os-empty">加载中…</td></tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',
            '  </div>',

            /* ---------------- 运营位编辑器 ---------------- */
            '  <div v-if="slotOpen" class="cmd-mask" style="padding-top:5vh" @click.self="slotOpen = false">',
            '    <div class="cmd-panel" style="width:700px;max-height:88vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">{{ slotForm.id ? "编辑运营位" : "新建运营位" }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="slotOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>编码 <em>*</em></label><input class="os-input os-mono" v-model.trim="slotForm.slotCode" :disabled="!!slotForm.id" maxlength="64" placeholder="HOME_BANNER_AUTUMN" /></div>',
            '          <div class="os-field"><label>名称 <em>*</em></label><input class="os-input" v-model.trim="slotForm.slotName" maxlength="100" /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>类型 <em>*</em></label><select class="os-select" v-model="slotForm.slotType"><option v-for="(v,k) in SLOT_TYPES" :key="k" :value="k">{{ v }}</option></select></div>',
            '          <div class="os-field"><label>展示位置 <em>*</em></label><select class="os-select" v-model="slotForm.position"><option v-for="(v,k) in POSITIONS" :key="k" :value="k">{{ v }}</option></select></div>',
            '        </div>',
            '        <div class="os-field"><label>主标题 <em>*</em></label><input class="os-input" v-model.trim="slotForm.title" maxlength="200" /></div>',
            '        <div class="os-field"><label>副标题</label><input class="os-input" v-model.trim="slotForm.subtitle" maxlength="500" /></div>',
            '        <div class="os-field"><label>图片 URL（Banner/弹窗必填）</label><input class="os-input" v-model.trim="slotForm.imageUrl" maxlength="500" placeholder="https://..." /></div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>跳转类型</label><select class="os-select" v-model="slotForm.targetType"><option v-for="(v,k) in TARGET_TYPES" :key="k" :value="k">{{ v }}</option></select></div>',
            '          <div class="os-field"><label>跳转目标</label><input class="os-input" v-model.trim="slotForm.targetValue" :disabled="slotForm.targetType === \'NONE\'" maxlength="200" placeholder="URL 或业务 ID" /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>权重（越大越靠前）</label><input class="os-input" type="number" v-model.number="slotForm.weight" /></div>',
            '          <div class="os-field"><label>排序值</label><input class="os-input" type="number" v-model.number="slotForm.sortOrder" /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>开始时间（留空=立即）</label><input class="os-input" type="datetime-local" v-model="slotForm.startAt" /></div>',
            '          <div class="os-field"><label>结束时间（留空=长期）</label><input class="os-input" type="datetime-local" v-model="slotForm.endAt" /></div>',
            '        </div>',
            '        <div class="os-field"><label>备注</label><input class="os-input" v-model.trim="slotForm.remark" maxlength="500" /></div>',
            '        <div class="os-note"><b>上线规则</b> 上线前会校验图片、跳转与时间窗；Banner/弹窗缺图会被拒绝。保存为草稿不影响线上内容。</div>',
            '        <div style="display:flex;gap:8px;justify-content:flex-end"><button class="os-btn" @click="slotOpen = false">取消</button><button class="os-btn primary" :disabled="slotSaving" @click="saveSlot">{{ slotSaving ? "提交中…" : "保存" }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            /* ---------------- 排期编辑器 ---------------- */
            '  <div v-if="entryOpen" class="cmd-mask" style="padding-top:6vh" @click.self="entryOpen = false">',
            '    <div class="cmd-panel" style="width:640px;max-height:86vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">{{ entryForm.id ? "编辑排期" : "新建排期" }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="entryOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>类型 <em>*</em></label><select class="os-select" v-model="entryForm.entryType"><option v-for="(v,k) in ENTRY_TYPES" :key="k" :value="k">{{ v }}</option></select></div>',
            '          <div class="os-field"><label>绑定运营位</label><select class="os-select" v-model="entryForm.slotId"><option :value="null">不绑定</option><option v-for="s in slots" :key="s.id" :value="s.id">{{ s.slotCode }} · {{ s.title }}</option></select></div>',
            '        </div>',
            '        <div class="os-field"><label>标题 <em>*</em></label><input class="os-input" v-model.trim="entryForm.title" maxlength="200" /></div>',
            '        <div class="os-field"><label>摘要</label><textarea class="os-textarea" v-model.trim="entryForm.summary" maxlength="1000" style="height:80px"></textarea></div>',
            '        <div class="os-field"><label>封面 URL</label><input class="os-input" v-model.trim="entryForm.coverUrl" maxlength="500" /></div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>开始时间 <em>*</em></label><input class="os-input" type="datetime-local" v-model="entryForm.startAt" /></div>',
            '          <div class="os-field"><label>结束时间 <em>*</em></label><input class="os-input" type="datetime-local" v-model="entryForm.endAt" /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>负责人</label><input class="os-input" v-model.trim="entryForm.owner" maxlength="64" /></div>',
            '          <div class="os-field"><label>备注</label><input class="os-input" v-model.trim="entryForm.remark" maxlength="500" /></div>',
            '        </div>',
            '        <div class="os-note"><b>排期规则</b> 新建为「已排期」，需手动上线；同一运营位重叠的已上线排期会在预览中提示冲突。</div>',
            '        <div style="display:flex;gap:8px;justify-content:flex-end"><button class="os-btn" @click="entryOpen = false">取消</button><button class="os-btn primary" :disabled="entrySaving" @click="saveEntry">{{ entrySaving ? "提交中…" : "保存" }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            /* ---------------- 预览 ---------------- */
            '  <div v-if="previewOpen" class="cmd-mask" style="padding-top:8vh" @click.self="previewOpen = false">',
            '    <div class="cmd-panel" style="width:600px;max-height:82vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">投放预览</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="previewOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:16px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-note" :style="preview.valid ? \'\' : \'border-color:var(--tk-bad)\'"><b>{{ preview.valid ? "校验通过" : "校验未通过" }}</b> {{ preview.valid ? "可安全上线" : "请按下方告警修正后再上线" }}</div>',
            '        <div v-if="preview.warnings && preview.warnings.length">',
            '          <div class="os-section-head" style="font-size:12px;color:var(--tk-warn);margin-bottom:6px">告警</div>',
            '          <div v-for="(w,i) in preview.warnings" :key="i" style="font-size:12px;color:var(--tk-text2);margin-bottom:4px">· {{ w }}</div>',
            '        </div>',
            '        <div v-if="preview.conflicts && preview.conflicts.length">',
            '          <div class="os-section-head" style="font-size:12px;color:var(--tk-bad);margin-bottom:6px">冲突内容</div>',
            '          <div v-for="(c,i) in preview.conflicts" :key="i" style="font-size:12px;color:var(--tk-text2);margin-bottom:4px">· {{ c }}</div>',
            '        </div>',
            '        <div style="border:1px solid var(--tk-border);border-radius:8px;overflow:hidden">',
            '          <div v-if="preview.payload && (preview.payload.imageUrl || preview.payload.coverUrl)" style="height:132px;background:var(--tk-subtle) center/cover no-repeat" :style="{ backgroundImage: \'url(\' + (preview.payload.imageUrl || preview.payload.coverUrl) + \')\' }"></div>',
            '          <div style="padding:12px 14px">',
            '            <div style="font-size:14px;font-weight:600;color:var(--tk-text)">{{ (preview.payload && (preview.payload.title || preview.payload.slotName)) || "未命名内容" }}</div>',
            '            <div style="font-size:12px;color:var(--tk-text3);margin-top:4px">{{ (preview.payload && (preview.payload.subtitle || preview.payload.summary)) || "" }}</div>',
            '            <div style="font-size:11px;color:var(--tk-text3);margin-top:8px">',
            '              <span v-if="preview.payload">{{ preview.payload.position ? (POSITIONS[preview.payload.position] || preview.payload.position) : (ENTRY_TYPES[preview.payload.entryType] || preview.payload.entryType) }}</span>',
            '              <span v-if="preview.payload && preview.payload.target"> · {{ preview.payload.target }}</span>',
            '              <span v-if="preview.payload"> · {{ RUNTIME_STATE[preview.payload.runtimeState] ? RUNTIME_STATE[preview.payload.runtimeState].label : preview.payload.runtimeState }}</span>',
            '            </div>',
            '          </div>',
            '        </div>',
            '        <div style="display:flex;justify-content:flex-end"><button class="os-btn" @click="previewOpen = false">关闭</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var activeTab = Vue.ref('slots');
            var slots = Vue.ref([]);
            var entries = Vue.ref([]);
            var slotType = Vue.ref('');
            var slotStatus = Vue.ref('');
            var slotKeyword = Vue.ref('');
            var entryType = Vue.ref('');
            var slotLoading = Vue.ref(false);
            var entryLoading = Vue.ref(false);
            var slotOpen = Vue.ref(false);
            var entryOpen = Vue.ref(false);
            var previewOpen = Vue.ref(false);
            var slotSaving = Vue.ref(false);
            var entrySaving = Vue.ref(false);
            var slotForm = Vue.reactive(blankSlot());
            var entryForm = Vue.reactive(blankEntry());
            var preview = Vue.reactive({ valid: true, warnings: [], conflicts: [], payload: null });
            var now = new Date();
            var twoWeeks = new Date(now.getTime() + 14 * 86400000);
            var rangeFrom = Vue.ref(localInput(now));
            var rangeTo = Vue.ref(localInput(twoWeeks));

            function fmt(v) { return v ? String(v).replace('T', ' ').slice(0, 16) : '—'; }
            function countSlotState(state) { return slots.value.filter(function (s) { return s.runtimeState === state; }).length; }
            function countEntryState(state) { return entries.value.filter(function (e) { return e.runtimeState === state; }).length; }

            function loadSlots() {
                slotLoading.value = true;
                var qs = [];
                if (slotType.value) qs.push('slotType=' + encodeURIComponent(slotType.value));
                if (slotStatus.value) qs.push('status=' + encodeURIComponent(slotStatus.value));
                if (slotKeyword.value) qs.push('keyword=' + encodeURIComponent(slotKeyword.value));
                api.get('/api/admin/content/slots' + (qs.length ? '?' + qs.join('&') : ''))
                    .then(function (data) { slots.value = data || []; })
                    .catch(function (error) { El.ElMessage.error(error.message || '运营位加载失败'); })
                    .finally(function () { slotLoading.value = false; });
            }
            function loadEntries() {
                entryLoading.value = true;
                var qs = [];
                if (rangeFrom.value) qs.push('from=' + encodeURIComponent(rangeFrom.value + ':00'));
                if (rangeTo.value) qs.push('to=' + encodeURIComponent(rangeTo.value + ':00'));
                if (entryType.value) qs.push('entryType=' + encodeURIComponent(entryType.value));
                api.get('/api/admin/content/calendar' + (qs.length ? '?' + qs.join('&') : ''))
                    .then(function (data) { entries.value = data || []; })
                    .catch(function (error) { El.ElMessage.error(error.message || '排期加载失败'); })
                    .finally(function () { entryLoading.value = false; });
            }
            function reloadAll() { loadSlots(); loadEntries(); }

            function openCreateSlot() {
                Object.assign(slotForm, blankSlot());
                slotForm.id = null;
                slotOpen.value = true;
            }
            function openSlotEdit(s) {
                Object.assign(slotForm, {
                    id: s.id, slotCode: s.slotCode, slotName: s.slotName, slotType: s.slotType,
                    position: s.position, title: s.title, subtitle: s.subtitle || '', imageUrl: s.imageUrl || '',
                    targetType: s.targetType, targetValue: s.targetValue || '', weight: s.weight || 0,
                    sortOrder: s.sortOrder || 0,
                    startAt: s.startAt ? String(s.startAt).slice(0, 16) : '',
                    endAt: s.endAt ? String(s.endAt).slice(0, 16) : '',
                    remark: s.remark || ''
                });
                slotOpen.value = true;
            }
            function saveSlot() {
                if (!slotForm.slotCode || !slotForm.slotName || !slotForm.title) {
                    El.ElMessage.warning('编码、名称、主标题为必填'); return;
                }
                var payload = {
                    slotCode: slotForm.slotCode, slotName: slotForm.slotName, slotType: slotForm.slotType,
                    position: slotForm.position, title: slotForm.title, subtitle: slotForm.subtitle,
                    imageUrl: slotForm.imageUrl, targetType: slotForm.targetType,
                    targetValue: slotForm.targetType === 'NONE' ? null : slotForm.targetValue,
                    weight: slotForm.weight, sortOrder: slotForm.sortOrder,
                    startAt: slotForm.startAt ? slotForm.startAt + ':00' : null,
                    endAt: slotForm.endAt ? slotForm.endAt + ':00' : null,
                    remark: slotForm.remark
                };
                slotSaving.value = true;
                var request = slotForm.id ? api.put('/api/admin/content/slots/' + slotForm.id, payload)
                    : api.post('/api/admin/content/slots', payload);
                request.then(function () {
                    El.ElMessage.success(slotForm.id ? '运营位已更新' : '运营位已创建（草稿）');
                    slotOpen.value = false;
                    loadSlots();
                }).catch(function (error) { El.ElMessage.error(error.message || '保存失败'); })
                    .finally(function () { slotSaving.value = false; });
            }
            function onlineSlot(s) {
                api.post('/api/admin/content/slots/' + s.id + '/online')
                    .then(function () { El.ElMessage.success('已上线'); loadSlots(); })
                    .catch(function (error) { El.ElMessage.error(error.message || '上线失败'); });
            }
            function offlineSlot(s) {
                El.ElMessageBox.confirm('下线后该运营位立即从投放端移除，确定继续？', '下线运营位', { type: 'warning' })
                    .then(function () { return api.post('/api/admin/content/slots/' + s.id + '/offline'); })
                    .then(function () { El.ElMessage.success('已下线'); loadSlots(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '下线失败'); });
            }
            function removeSlot(s) {
                El.ElMessageBox.confirm('确定删除运营位「' + s.title + '」？在线或被排期引用时会被拒绝。', '删除运营位', { type: 'warning' })
                    .then(function () { return api.delete('/api/admin/content/slots/' + s.id); })
                    .then(function () { El.ElMessage.success('已删除'); loadSlots(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '删除失败'); });
            }
            function previewSlot(s) {
                api.get('/api/admin/content/slots/' + s.id + '/preview')
                    .then(function (data) { showPreview(data); })
                    .catch(function (error) { El.ElMessage.error(error.message || '预览失败'); });
            }
            function showPreview(data) {
                preview.valid = !!(data && data.valid);
                preview.warnings = (data && data.warnings) || [];
                preview.conflicts = (data && data.conflicts) || [];
                preview.payload = (data && data.payload) || null;
                previewOpen.value = true;
            }

            function openCreateEntry() {
                Object.assign(entryForm, blankEntry());
                entryForm.id = null;
                entryOpen.value = true;
            }
            function openEntryEdit(e) {
                Object.assign(entryForm, {
                    id: e.id, slotId: e.slotId || null, entryType: e.entryType, title: e.title,
                    summary: e.summary || '', coverUrl: e.coverUrl || '',
                    startAt: String(e.startAt).slice(0, 16), endAt: String(e.endAt).slice(0, 16),
                    owner: e.owner || '', remark: e.remark || ''
                });
                entryOpen.value = true;
            }
            function saveEntry() {
                if (!entryForm.title || !entryForm.startAt || !entryForm.endAt) {
                    El.ElMessage.warning('标题与起止时间为必填'); return;
                }
                var payload = {
                    slotId: entryForm.slotId, entryType: entryForm.entryType, title: entryForm.title,
                    summary: entryForm.summary, coverUrl: entryForm.coverUrl,
                    startAt: entryForm.startAt + ':00', endAt: entryForm.endAt + ':00',
                    owner: entryForm.owner, remark: entryForm.remark
                };
                entrySaving.value = true;
                var request = entryForm.id ? api.put('/api/admin/content/calendar/' + entryForm.id, payload)
                    : api.post('/api/admin/content/calendar', payload);
                request.then(function () {
                    El.ElMessage.success(entryForm.id ? '排期已更新' : '排期已创建（未上线）');
                    entryOpen.value = false;
                    loadEntries();
                }).catch(function (error) { El.ElMessage.error(error.message || '保存失败'); })
                    .finally(function () { entrySaving.value = false; });
            }
            function publishEntry(e) {
                api.post('/api/admin/content/calendar/' + e.id + '/publish')
                    .then(function () { El.ElMessage.success('排期已上线'); loadEntries(); })
                    .catch(function (error) { El.ElMessage.error(error.message || '上线失败'); });
            }
            function offlineEntry(e) {
                El.ElMessageBox.confirm('下线后该排期不再展示，确定继续？', '下线排期', { type: 'warning' })
                    .then(function () { return api.post('/api/admin/content/calendar/' + e.id + '/offline'); })
                    .then(function () { El.ElMessage.success('已下线'); loadEntries(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '下线失败'); });
            }
            function removeEntry(e) {
                El.ElMessageBox.confirm('确定删除排期「' + e.title + '」？已上线排期需先下线。', '删除排期', { type: 'warning' })
                    .then(function () { return api.delete('/api/admin/content/calendar/' + e.id); })
                    .then(function () { El.ElMessage.success('已删除'); loadEntries(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '删除失败'); });
            }
            function previewEntry(e) {
                api.get('/api/admin/content/calendar/' + e.id + '/preview')
                    .then(function (data) { showPreview(data); })
                    .catch(function (error) { El.ElMessage.error(error.message || '预览失败'); });
            }

            loadSlots();
            loadEntries();

            return {
                activeTab: activeTab, slots: slots, entries: entries,
                slotType: slotType, slotStatus: slotStatus, slotKeyword: slotKeyword,
                entryType: entryType, rangeFrom: rangeFrom, rangeTo: rangeTo,
                slotLoading: slotLoading, entryLoading: entryLoading,
                slotOpen: slotOpen, entryOpen: entryOpen, previewOpen: previewOpen,
                slotSaving: slotSaving, entrySaving: entrySaving,
                slotForm: slotForm, entryForm: entryForm, preview: preview,
                loadSlots: loadSlots, loadEntries: loadEntries, reloadAll: reloadAll,
                countSlotState: countSlotState, countEntryState: countEntryState, fmt: fmt,
                openCreateSlot: openCreateSlot, openSlotEdit: openSlotEdit, saveSlot: saveSlot,
                onlineSlot: onlineSlot, offlineSlot: offlineSlot, removeSlot: removeSlot, previewSlot: previewSlot,
                openCreateEntry: openCreateEntry, openEntryEdit: openEntryEdit, saveEntry: saveEntry,
                publishEntry: publishEntry, offlineEntry: offlineEntry, removeEntry: removeEntry, previewEntry: previewEntry,
                SLOT_TYPES: SLOT_TYPES, POSITIONS: POSITIONS, TARGET_TYPES: TARGET_TYPES,
                SLOT_STATUS: SLOT_STATUS, RUNTIME_STATE: RUNTIME_STATE, ENTRY_TYPES: ENTRY_TYPES,
                CALENDAR_STATUS: CALENDAR_STATUS
            };
        }
    };
})();
