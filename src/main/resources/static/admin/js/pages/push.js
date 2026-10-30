/* 消息推送（N02/N03/N04）：模板、任务、触达统计。
   真实性约束（与后端一致）：
   - 渠道能力以 GET /api/admin/push/channels 为准；未配置通道返回 NOT_CONFIGURED，不计成功/失败；
   - 站内通知（LOG/WS 渠道）逐用户真实落库，App 消息中心可读；
   - 点击由 App 回调写入，未接入时统计为 0，页面不伪造点击；
   - 失败/未配置明细可重试，计数由后端按明细重算，前端不做本地估算。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var CHANNEL_LABEL = { LOG: '日志', WS: '站内 WebSocket', FCM: 'FCM(Android/Web)', APNS: 'APNs(iOS)', WEBPUSH: 'Web Push' };
    var TASK_STATUS = {
        DRAFT: { label: '草稿', tone: 'muted' },
        SCHEDULED: { label: '定时待发', tone: 'warn' },
        SENDING: { label: '发送中', tone: 'info' },
        SENT: { label: '已发送', tone: 'ok' },
        PARTIAL: { label: '部分成功', tone: 'warn' },
        FAILED: { label: '失败', tone: 'bad' },
        CANCELLED: { label: '已取消', tone: 'muted' }
    };
    var DELIVERY_STATUS = {
        SENT: { label: '已发送', tone: '' },
        DELIVERED: { label: '已到达', tone: 'ok' },
        CLICKED: { label: '已点击', tone: 'ok' },
        FAILED: { label: '失败', tone: 'bad' },
        NOT_CONFIGURED: { label: '通道未配置', tone: 'warn' },
        SKIPPED: { label: '频控跳过', tone: 'muted' },
        PENDING: { label: '待发送', tone: 'muted' }
    };
    var AUDIENCE_LABEL = { ALL: '全部用户', CAMPUS: '本校区', ROLE: '按角色', ORGANIZATION: '按组织', USER: '指定用户' };
    var SCENE_OPTIONS = ['SYSTEM', 'ACTIVITY', 'WORK_ORDER', 'ANNOUNCEMENT', 'AI_TODO'];

    function pad(n) { return n < 10 ? '0' + n : '' + n; }
    function localInput(d) {
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    }
    function blankTemplate() {
        return {
            id: null, templateCode: '', templateName: '', scene: 'SYSTEM',
            titleTemplate: '', contentTemplate: '', defaultChannels: ['LOG'], status: 'ENABLED', remark: ''
        };
    }
    function blankTask() {
        return {
            id: null, taskName: '', templateId: null, title: '', content: '',
            variables: [], audienceType: 'ALL', audienceValue: '',
            channels: ['LOG', 'WS'], scheduleType: 'NOW', scheduledAt: '',
            frequencyLimit: 3, frequencyWindowHours: 24, action: 'SEND', remark: ''
        };
    }

    window.AdminPages.PushPage = {
        name: 'PushPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>消息推送</h1><div class="sub">推送模板 / 推送任务 / 触达统计</div></div>',
            '    <div class="r"><button class="btn2" @click="reloadAll">刷新</button><button class="btn2 pri" @click="openTaskCreate">新建推送任务</button></div></div>',

            '  <div class="os-grid" style="grid-template-columns:repeat(6,1fr)">',
            '    <div class="os-kpi"><div class="k-label"><span class="dot"></span>任务总数</div><div class="k-val">{{ tasks.length }}<small>个</small></div><div class="k-foot"><span>当前校区</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>成功投递</div><div class="k-val">{{ sum(\'successCount\') }}<small>条</small></div><div class="k-foot"><span>已交给通道</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-bad)"></span>失败</div><div class="k-val">{{ sum(\'failedCount\') }}<small>条</small></div><div class="k-foot"><span>可重试</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-info)"></span>确认到达</div><div class="k-val">{{ sum(\'deliveredCount\') }}<small>条</small></div><div class="k-foot"><span>在线写入成功</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>通道未配置</div><div class="k-val">{{ sum(\'notConfiguredCount\') }}<small>条</small></div><div class="k-foot"><span>不计成功/失败</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-text3)"></span>点击</div><div class="k-val">{{ sum(\'clickedCount\') }}<small>次</small></div><div class="k-foot"><span>App 回调采集</span></div></div>',
            '  </div>',

            '  <div class="os-panel" style="flex:1;min-height:0;display:flex;flex-direction:column">',
            '    <div class="os-panel-h" style="flex-wrap:wrap;row-gap:8px">',
            '      <div class="tabs" style="display:flex;gap:4px">',
            '        <button v-for="t in tabs" :key="t.value" class="tab" :class="{ on: activeTab === t.value }" @click="switchTab(t.value)">{{ t.label }}</button>',
            '      </div>',
            '      <span style="flex:1"></span>',
            '      <template v-if="activeTab === \'tasks\'">',
            '        <select class="os-select" style="width:132px" v-model="taskStatus" @change="loadTasks"><option value="">全部状态</option><option v-for="(v,k) in TASK_STATUS" :key="k" :value="k">{{ v.label }}</option></select>',
            '        <div class="srch" style="width:210px;background:var(--tk-input)"><el-icon :size="13"><Search /></el-icon><input v-model.trim="taskKeyword" placeholder="搜索任务名 / 标题" @keyup.enter="loadTasks" /></div>',
            '      </template>',
            '      <template v-else-if="activeTab === \'templates\'">',
            '        <select class="os-select" style="width:132px" v-model="tplScene" @change="loadTemplates"><option value="">全部场景</option><option v-for="s in scenes" :key="s" :value="s">{{ s }}</option></select>',
            '        <select class="os-select" style="width:120px" v-model="tplStatus" @change="loadTemplates"><option value="">全部状态</option><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select>',
            '        <button class="btn2 pri" @click="openTemplateCreate">新建模板</button>',
            '      </template>',
            '      <template v-else>',
            '        <input class="os-input" style="width:182px" type="datetime-local" v-model="statsFrom" />',
            '        <span style="font-size:12px;color:var(--tk-text3)">至</span>',
            '        <input class="os-input" style="width:182px" type="datetime-local" v-model="statsTo" />',
            '        <button class="btn2" @click="loadStats">查询</button>',
            '      </template>',
            '    </div>',

            /* ---------------- 任务 ---------------- */
            '    <div v-if="activeTab === \'tasks\'" style="overflow:auto;flex:1">',
            '      <table class="pk" style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr><th style="width:24%">任务</th><th style="width:11%">人群</th><th style="width:12%">渠道</th><th style="width:11%">时机</th><th style="width:9%">状态</th><th style="width:19%">投递计数</th><th style="width:14%;text-align:right">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-for="t in tasks" :key="t.id">',
            '            <td><b style="font-weight:550;color:var(--tk-text)">{{ t.taskName }}</b><div style="font-size:11px;color:var(--tk-text3);margin-top:3px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ t.title }}</div></td>',
            '            <td style="color:var(--tk-text3)">{{ AUDIENCE_LABEL[t.audienceType] || t.audienceType }}<span v-if="t.audienceValue" class="os-mono" style="margin-left:4px">#{{ t.audienceValue }}</span></td>',
            '            <td><span v-for="c in t.channels" :key="c" class="os-tag" style="margin-right:4px">{{ CHANNEL_LABEL[c] || c }}</span></td>',
            '            <td style="color:var(--tk-text3)">{{ t.scheduleType === \'SCHEDULED\' ? fmt(t.scheduledAt) : \'立即\' }}</td>',
            '            <td><span class="os-tag" :class="(TASK_STATUS[t.status]||{}).tone || \'muted\'">{{ (TASK_STATUS[t.status]||{}).label || t.status }}</span></td>',
            '            <td style="font-size:11px;color:var(--tk-text3)">总 {{ t.totalCount }} · 成功 {{ t.successCount }} · 失败 {{ t.failedCount }}<br/>到达 {{ t.deliveredCount }} · 未配置 {{ t.notConfiguredCount }} · 跳过 {{ t.skippedCount }}</td>',
            '            <td style="text-align:right;white-space:nowrap">',
            '              <span v-if="canSend(t)" style="font-size:11px;color:var(--tk-info);cursor:pointer;margin-left:8px" @click="sendTask(t)">发送</span>',
            '              <span v-if="t.status === \'DRAFT\' || t.status === \'SCHEDULED\'" style="font-size:11px;color:var(--tk-text3);cursor:pointer;margin-left:8px" @click="cancelTask(t)">取消</span>',
            '              <span v-if="t.failedCount > 0 || t.notConfiguredCount > 0" style="font-size:11px;color:var(--tk-warn);cursor:pointer;margin-left:8px" @click="retryTask(t)">重试</span>',
            '              <span style="font-size:11px;color:var(--tk-info);cursor:pointer;margin-left:8px" @click="openDeliveries(t)">明细</span>',
            '              <span style="font-size:11px;color:var(--tk-bad);cursor:pointer;margin-left:8px" @click="removeTask(t)">删除</span>',
            '            </td>',
            '          </tr>',
            '          <tr v-if="!taskLoading && !tasks.length"><td colspan="7" class="os-empty">暂无推送任务，点击右上角「新建推送任务」开始</td></tr>',
            '          <tr v-if="taskLoading"><td colspan="7" class="os-empty">加载中…</td></tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',

            /* ---------------- 模板 ---------------- */
            '    <div v-if="activeTab === \'templates\'" style="overflow:auto;flex:1">',
            '      <table class="pk" style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr><th style="width:15%">编码</th><th style="width:18%">名称</th><th style="width:11%">场景</th><th style="width:14%">默认渠道</th><th style="width:16%">标题模板</th><th style="width:9%">状态</th><th style="width:17%;text-align:right">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-for="tpl in templates" :key="tpl.id">',
            '            <td class="os-mono" style="color:var(--tk-text)">{{ tpl.templateCode }}</td>',
            '            <td><b style="font-weight:550">{{ tpl.templateName }}</b><div v-if="tpl.variables && tpl.variables.length" style="font-size:11px;color:var(--tk-text3);margin-top:3px">变量：{{ tpl.variables.join(\', \') }}</div></td>',
            '            <td><span class="os-tag">{{ tpl.scene }}</span></td>',
            '            <td><span v-for="c in tpl.defaultChannels" :key="c" class="os-tag" style="margin-right:4px">{{ CHANNEL_LABEL[c] || c }}</span></td>',
            '            <td style="color:var(--tk-text3);overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ tpl.titleTemplate }}</td>',
            '            <td><span class="os-tag" :class="tpl.status === \'ENABLED\' ? \'ok\' : \'muted\'">{{ tpl.status === \'ENABLED\' ? \'启用\' : \'停用\' }}</span></td>',
            '            <td style="text-align:right;white-space:nowrap">',
            '              <span style="font-size:11px;color:var(--tk-info);cursor:pointer;margin-left:8px" @click="openTemplateEdit(tpl)">编辑</span>',
            '              <span style="font-size:11px;color:var(--tk-ok);cursor:pointer;margin-left:8px" @click="openTest(tpl)">测试发送</span>',
            '              <span style="font-size:11px;color:var(--tk-bad);cursor:pointer;margin-left:8px" @click="removeTemplate(tpl)">删除</span>',
            '            </td>',
            '          </tr>',
            '          <tr v-if="!tplLoading && !templates.length"><td colspan="7" class="os-empty">暂无模板，先创建模板再建任务</td></tr>',
            '          <tr v-if="tplLoading"><td colspan="7" class="os-empty">加载中…</td></tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',

            /* ---------------- 统计 ---------------- */
            '    <div v-if="activeTab === \'stats\'" style="overflow:auto;flex:1;padding:4px 2px">',
            '      <div v-if="statsLoading" class="os-empty">统计加载中…</div>',
            '      <template v-else>',
            '        <div class="os-note" style="margin-bottom:12px"><b>口径</b> 发送=已交给通道；成功=通道返回成功；到达=在线写入成功；点击=App 回调；通道未配置不计成功/失败。到达率 {{ pct(stats.deliverRate) }} · 点击率 {{ pct(stats.clickRate) }}。</div>',
            '        <table class="pk" style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '          <thead><tr><th style="width:20%">渠道</th><th>投递总数</th><th>成功</th><th>失败</th><th>到达</th><th>点击</th><th>未配置</th><th>频控跳过</th></tr></thead>',
            '          <tbody>',
            '            <tr v-for="c in stats.channels" :key="c.channel">',
            '              <td><b style="font-weight:550">{{ CHANNEL_LABEL[c.channel] || c.channel }}</b></td>',
            '              <td>{{ c.total }}</td><td>{{ c.success }}</td><td>{{ c.failed }}</td><td>{{ c.delivered }}</td><td>{{ c.clicked }}</td><td style="color:var(--tk-warn)">{{ c.notConfigured }}</td><td style="color:var(--tk-text3)">{{ c.skipped }}</td>',
            '            </tr>',
            '            <tr v-if="!stats.channels.length"><td colspan="8" class="os-empty">所选时间范围内暂无投递记录</td></tr>',
            '          </tbody>',
            '        </table>',
            '        <div style="margin-top:18px"><div class="os-section-head" style="font-size:12px;color:var(--tk-text2);margin-bottom:8px">通道能力（后端实时探测）</div>',
            '          <table class="pk" style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '            <thead><tr><th style="width:20%">通道</th><th style="width:12%">状态</th><th>说明</th></tr></thead>',
            '            <tbody><tr v-for="cap in stats.capabilities" :key="cap.channel">',
            '              <td><b style="font-weight:550">{{ CHANNEL_LABEL[cap.channel] || cap.channel }}</b></td>',
            '              <td><span class="os-tag" :class="cap.configured ? \'ok\' : \'warn\'">{{ cap.configured ? \'可投递\' : \'未配置\' }}</span></td>',
            '              <td style="color:var(--tk-text3)">{{ cap.reason || \'—\' }}</td>',
            '            </tr></tbody>',
            '          </table>',
            '        </div>',
            '      </template>',
            '    </div>',
            '  </div>',

            /* ---------------- 任务编辑器 ---------------- */
            '  <div v-if="taskOpen" class="cmd-mask" style="padding-top:4vh" @click.self="taskOpen = false">',
            '    <div class="cmd-panel" style="width:760px;max-height:90vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">{{ taskForm.id ? "编辑推送任务" : "新建推送任务" }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="taskOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>任务名称 <em>*</em></label><input class="os-input" v-model.trim="taskForm.taskName" maxlength="100" placeholder="例如：秋季学期选课提醒" /></div>',
            '          <div class="os-field"><label>关联模板</label><select class="os-select" v-model="taskForm.templateId" @change="applyTemplate"><option :value="null">不关联</option><option v-for="tpl in templates" :key="tpl.id" :value="tpl.id">{{ tpl.templateCode }} · {{ tpl.templateName }}</option></select></div>',
            '        </div>',
            '        <div class="os-field"><label>推送标题 <em>*</em></label><input class="os-input" v-model.trim="taskForm.title" maxlength="200" placeholder="支持 {变量}，如：{name} 同学，选课即将开始" /></div>',
            '        <div class="os-field"><label>推送正文 <em>*</em></label><textarea class="os-textarea" v-model.trim="taskForm.content" maxlength="2000" placeholder="支持 {变量}；正文会同时写入站内通知与已配置的 OS 通道"></textarea></div>',
            '        <div class="os-field"><label>模板变量（键值对，渲染后发送）</label>',
            '          <div style="display:flex;flex-direction:column;gap:6px">',
            '            <div v-for="(v,i) in taskForm.variables" :key="i" style="display:flex;gap:8px">',
            '              <input class="os-input" style="width:180px" v-model.trim="v.key" placeholder="变量名" />',
            '              <input class="os-input" style="flex:1" v-model="v.value" placeholder="变量值" />',
            '              <button class="os-btn" @click="taskForm.variables.splice(i,1)">移除</button>',
            '            </div>',
            '            <div><button class="os-btn" @click="taskForm.variables.push({key:\'\',value:\'\'})">+ 添加变量</button></div>',
            '          </div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>目标人群 <em>*</em></label><select class="os-select" v-model="taskForm.audienceType"><option v-for="(label,key) in AUDIENCE_LABEL" :key="key" :value="key">{{ label }}</option></select></div>',
            '          <div class="os-field" v-if="taskForm.audienceType !== \'ALL\' && taskForm.audienceType !== \'CAMPUS\'"><label>{{ taskForm.audienceType === \'USER\' ? \'用户 ID（逗号分隔）\' : (taskForm.audienceType === \'ROLE\' ? \'角色\' : \'组织\') }}</label>',
            '            <select v-if="taskForm.audienceType === \'ROLE\'" class="os-select" v-model="taskForm.audienceValue"><option value="">请选择角色</option><option v-for="r in roles" :key="r.roleId" :value="String(r.roleId)">{{ r.roleName }}</option></select>',
            '            <select v-else-if="taskForm.audienceType === \'ORGANIZATION\'" class="os-select" v-model="taskForm.audienceValue"><option value="">请选择组织</option><option v-for="o in organizations" :key="o.organizationId" :value="String(o.organizationId)">{{ o.organizationName }}</option></select>',
            '            <input v-else class="os-input" v-model.trim="taskForm.audienceValue" placeholder="例如：1001,1002" />',
            '          </div>',
            '        </div>',
            '        <div class="os-field"><label>推送渠道 <em>*</em></label>',
            '          <div style="display:flex;gap:14px;flex-wrap:wrap;padding-top:4px">',
            '            <label v-for="c in channelOptions" :key="c.value" style="display:flex;align-items:center;gap:6px;font-size:12px;color:var(--tk-text2)">',
            '              <input type="checkbox" :value="c.value" v-model="taskForm.channels" /> {{ c.label }}',
            '              <span v-if="!c.configured" class="os-tag" style="background:var(--tk-warn-soft);color:var(--tk-warn)">未配置</span>',
            '            </label>',
            '          </div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>发送时机 <em>*</em></label><select class="os-select" v-model="taskForm.scheduleType"><option value="NOW">立即发送</option><option value="SCHEDULED">定时发送</option></select></div>',
            '          <div class="os-field"><label>定时时间</label><input class="os-input" type="datetime-local" v-model="taskForm.scheduledAt" :disabled="taskForm.scheduleType !== \'SCHEDULED\'" /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>频控：窗口内最多接收次数（0=不限）</label><input class="os-input" type="number" min="0" max="100" v-model.number="taskForm.frequencyLimit" /></div>',
            '          <div class="os-field"><label>频控窗口（小时，0=不限）</label><input class="os-input" type="number" min="0" max="720" v-model.number="taskForm.frequencyWindowHours" /></div>',
            '        </div>',
            '        <div class="os-note"><b>发送说明</b> 立即发送会逐用户生成投递明细；站内渠道同时写入用户通知箱。未配置的 OS 通道记为「通道未配置」，不计成功也不计失败。变量缺失会被后端拒绝并给出缺失清单。</div>',
            '        <div style="display:flex;gap:8px;justify-content:flex-end;padding-top:2px">',
            '          <button class="os-btn" @click="taskOpen = false">取消</button>',
            '          <button class="os-btn" :disabled="taskSubmitting" @click="submitTask(\'DRAFT\')">存草稿</button>',
            '          <button class="os-btn" :disabled="taskSubmitting" @click="submitTask(\'SCHEDULE\')">定时发送</button>',
            '          <button class="os-btn primary" :disabled="taskSubmitting" @click="submitTask(\'SEND\')">{{ taskSubmitting ? "提交中…" : "立即发送" }}</button>',
            '        </div>',
            '      </div>',
            '    </div>',
            '  </div>',

            /* ---------------- 模板编辑器 ---------------- */
            '  <div v-if="tplOpen" class="cmd-mask" style="padding-top:6vh" @click.self="tplOpen = false">',
            '    <div class="cmd-panel" style="width:640px;max-height:84vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">{{ tplForm.id ? "编辑推送模板" : "新建推送模板" }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="tplOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>模板编码 <em>*</em></label><input class="os-input os-mono" v-model.trim="tplForm.templateCode" :disabled="!!tplForm.id" maxlength="64" placeholder="如 SELECTION_REMINDER" /></div>',
            '          <div class="os-field"><label>模板名称 <em>*</em></label><input class="os-input" v-model.trim="tplForm.templateName" maxlength="100" /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>场景 <em>*</em></label><select class="os-select" v-model="tplForm.scene"><option v-for="s in scenes" :key="s" :value="s">{{ s }}</option></select></div>',
            '          <div class="os-field"><label>状态</label><select class="os-select" v-model="tplForm.status"><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></div>',
            '        </div>',
            '        <div class="os-field"><label>标题模板 <em>*</em></label><input class="os-input" v-model.trim="tplForm.titleTemplate" maxlength="200" placeholder="如：{name} 同学，你的报修已完工" /></div>',
            '        <div class="os-field"><label>正文模板 <em>*</em></label><textarea class="os-textarea" v-model.trim="tplForm.contentTemplate" maxlength="2000" placeholder="如：工单 {orderNo} 已由 {worker} 完成，请及时验收"></textarea></div>',
            '        <div class="os-field"><label>默认渠道</label><div style="display:flex;gap:14px;flex-wrap:wrap;padding-top:4px"><label v-for="c in channelOptions" :key="c.value" style="display:flex;align-items:center;gap:6px;font-size:12px;color:var(--tk-text2)"><input type="checkbox" :value="c.value" v-model="tplForm.defaultChannels" /> {{ c.label }}</label></div></div>',
            '        <div class="os-field"><label>备注</label><input class="os-input" v-model.trim="tplForm.remark" maxlength="500" /></div>',
            '        <div class="os-note"><b>变量规则</b> 使用 <span class="os-mono">{变量名}</span>；测试发送或建任务时必须提供全部变量，缺失会被后端拒绝。</div>',
            '        <div style="display:flex;gap:8px;justify-content:flex-end"><button class="os-btn" @click="tplOpen = false">取消</button><button class="os-btn primary" :disabled="tplSubmitting" @click="submitTemplate">{{ tplSubmitting ? "提交中…" : "保存" }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            /* ---------------- 测试发送 ---------------- */
            '  <div v-if="testOpen" class="cmd-mask" style="padding-top:6vh" @click.self="testOpen = false">',
            '    <div class="cmd-panel" style="width:660px;max-height:84vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">测试发送 · {{ testTemplate.templateName }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="testOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-note">测试发送会向<b>当前登录管理员</b>写入一条站内通知，并尝试已配置通道；不会触达真实用户。</div>',
            '        <div v-if="testVars.length">',
            '          <div v-for="(v,i) in testVars" :key="i" class="os-field" style="margin-bottom:8px"><label>{{ v.key }} <em>*</em></label><input class="os-input" v-model="v.value" :placeholder="\'请输入 \' + v.key" /></div>',
            '        </div>',
            '        <div v-else class="os-note">该模板没有变量，可直接发送。</div>',
            '        <div style="display:flex;gap:8px;justify-content:flex-end"><button class="os-btn" @click="testOpen = false">关闭</button><button class="os-btn primary" :disabled="testSubmitting" @click="submitTest">{{ testSubmitting ? "发送中…" : "发送测试" }}</button></div>',
            '        <div v-if="testResult">',
            '          <div class="os-section-head" style="font-size:12px;color:var(--tk-text2);margin:6px 0">发送结果</div>',
            '          <div class="os-note" style="margin-bottom:8px">站内通知：{{ testResult.notificationCreated ? "已写入当前管理员通知箱" : "未写入" }}<br/>标题：{{ testResult.title }}</div>',
            '          <table class="pk" style="width:100%;border-collapse:collapse"><thead><tr><th style="width:22%">渠道</th><th style="width:20%">结果</th><th>说明</th></tr></thead>',
            '            <tbody><tr v-for="r in testResult.results" :key="r.channel"><td>{{ CHANNEL_LABEL[r.channel] || r.channel }}</td><td><span class="os-tag" :class="DELIVERY_STATUS[r.status] ? DELIVERY_STATUS[r.status].tone : \'muted\'">{{ DELIVERY_STATUS[r.status] ? DELIVERY_STATUS[r.status].label : r.status }}</span></td><td style="color:var(--tk-text3)">{{ r.message || "—" }}</td></tr></tbody></table>',
            '        </div>',
            '      </div>',
            '    </div>',
            '  </div>',

            /* ---------------- 投递明细 ---------------- */
            '  <div v-if="deliveryOpen" class="cmd-mask" style="padding-top:6vh" @click.self="deliveryOpen = false">',
            '    <div class="cmd-panel" style="width:820px;max-height:84vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">投递明细 · {{ deliveryTask.taskName }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="deliveryOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:14px 20px;display:flex;flex-direction:column;gap:10px">',
            '        <table class="pk" style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '          <thead><tr><th style="width:12%">用户 ID</th><th style="width:16%">渠道</th><th style="width:14%">状态</th><th style="width:9%">尝试</th><th>说明 / 失败原因</th></tr></thead>',
            '          <tbody>',
            '            <tr v-for="d in deliveries" :key="d.id">',
            '              <td class="os-mono">{{ d.userId }}</td>',
            '              <td>{{ CHANNEL_LABEL[d.channel] || d.channel }}</td>',
            '              <td><span class="os-tag" :class="DELIVERY_STATUS[d.status] ? DELIVERY_STATUS[d.status].tone : \'muted\'">{{ DELIVERY_STATUS[d.status] ? DELIVERY_STATUS[d.status].label : d.status }}</span></td>',
            '              <td>{{ d.attempts }}</td>',
            '              <td style="color:var(--tk-text3)">{{ d.errorMessage || d.providerMessage || "—" }}</td>',
            '            </tr>',
            '            <tr v-if="!deliveries.length"><td colspan="5" class="os-empty">暂无投递明细</td></tr>',
            '          </tbody>',
            '        </table>',
            '        <div style="display:flex;align-items:center;gap:8px;justify-content:flex-end">',
            '          <span style="font-size:11px;color:var(--tk-text3)">共 {{ deliveryTotal }} 条 · 第 {{ deliveryPage }} 页</span>',
            '          <button class="os-btn" :disabled="deliveryPage <= 1" @click="deliveryPage--; loadDeliveries()">上一页</button>',
            '          <button class="os-btn" :disabled="deliveryPage * 20 >= deliveryTotal" @click="deliveryPage++; loadDeliveries()">下一页</button>',
            '        </div>',
            '      </div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var tabs = [{ value: 'tasks', label: '推送任务' }, { value: 'templates', label: '推送模板' }, { value: 'stats', label: '触达统计' }];
            var activeTab = Vue.ref('tasks');
            var tasks = Vue.ref([]);
            var templates = Vue.ref([]);
            var roles = Vue.ref([]);
            var organizations = Vue.ref([]);
            var capabilities = Vue.ref([]);
            var taskStatus = Vue.ref('');
            var taskKeyword = Vue.ref('');
            var tplScene = Vue.ref('');
            var tplStatus = Vue.ref('');
            var taskLoading = Vue.ref(false);
            var tplLoading = Vue.ref(false);
            var statsLoading = Vue.ref(false);
            var taskOpen = Vue.ref(false);
            var tplOpen = Vue.ref(false);
            var testOpen = Vue.ref(false);
            var deliveryOpen = Vue.ref(false);
            var taskSubmitting = Vue.ref(false);
            var tplSubmitting = Vue.ref(false);
            var testSubmitting = Vue.ref(false);
            var taskForm = Vue.reactive(blankTask());
            var tplForm = Vue.reactive(blankTemplate());
            var testTemplate = Vue.reactive({ id: null, templateName: '', templateCode: '' });
            var testVars = Vue.ref([]);
            var testResult = Vue.ref(null);
            var deliveryTask = Vue.reactive({ id: null, taskName: '' });
            var deliveries = Vue.ref([]);
            var deliveryTotal = Vue.ref(0);
            var deliveryPage = Vue.ref(1);
            var stats = Vue.reactive({ channels: [], capabilities: [], deliverRate: null, clickRate: null });
            var now = new Date();
            var weekAgo = new Date(now.getTime() - 7 * 86400000);
            var statsFrom = Vue.ref(localInput(weekAgo));
            var statsTo = Vue.ref(localInput(now));

            var scenes = SCENE_OPTIONS;
            var channelOptions = Vue.computed(function () {
                return ['LOG', 'WS', 'FCM', 'APNS', 'WEBPUSH'].map(function (channel) {
                    var cap = capabilities.value.find(function (item) { return item.channel === channel; });
                    return {
                        value: channel,
                        label: CHANNEL_LABEL[channel] || channel,
                        configured: !cap ? channel === 'LOG' || channel === 'WS' : !!cap.configured
                    };
                });
            });

            function fmt(v) { return v ? String(v).replace('T', ' ').slice(0, 16) : '—'; }
            function pct(v) { return v === null || v === undefined ? '—' : (Math.round(v * 1000) / 10) + '%'; }
            function sum(field) {
                return tasks.value.reduce(function (total, task) { return total + (task[field] || 0); }, 0);
            }
            function canSend(task) {
                return ['DRAFT', 'SCHEDULED', 'FAILED'].indexOf(task.status) >= 0;
            }

            function loadChannels() {
                return api.get('/api/admin/push/channels').then(function (data) {
                    capabilities.value = data || [];
                }).catch(function () { capabilities.value = []; });
            }
            function loadTasks() {
                taskLoading.value = true;
                var qs = [];
                if (taskStatus.value) qs.push('status=' + encodeURIComponent(taskStatus.value));
                if (taskKeyword.value) qs.push('keyword=' + encodeURIComponent(taskKeyword.value));
                qs.push('pageNum=1', 'pageSize=50');
                api.get('/api/admin/push/tasks?' + qs.join('&'))
                    .then(function (data) { tasks.value = (data && data.records) || []; })
                    .catch(function (error) { El.ElMessage.error(error.message || '任务加载失败'); })
                    .finally(function () { taskLoading.value = false; });
            }
            function loadTemplates() {
                tplLoading.value = true;
                var qs = [];
                if (tplScene.value) qs.push('scene=' + encodeURIComponent(tplScene.value));
                if (tplStatus.value) qs.push('status=' + encodeURIComponent(tplStatus.value));
                api.get('/api/admin/push/templates' + (qs.length ? '?' + qs.join('&') : ''))
                    .then(function (data) { templates.value = data || []; })
                    .catch(function (error) { El.ElMessage.error(error.message || '模板加载失败'); })
                    .finally(function () { tplLoading.value = false; });
            }
            function loadRoles() {
                api.get('/api/admin/roles').then(function (data) {
                    roles.value = (data || []).filter(function (role) { return role.status !== 'DISABLED'; });
                }).catch(function () { roles.value = []; });
            }
            function loadOrganizations() {
                api.get('/api/admin/organizations').then(function (data) {
                    organizations.value = (data || []).filter(function (org) { return org.status !== 'DISABLED'; });
                }).catch(function () { organizations.value = []; });
            }
            function loadStats() {
                statsLoading.value = true;
                var qs = [];
                if (statsFrom.value) qs.push('from=' + encodeURIComponent(statsFrom.value + ':00'));
                if (statsTo.value) qs.push('to=' + encodeURIComponent(statsTo.value + ':00'));
                api.get('/api/admin/push/stats' + (qs.length ? '?' + qs.join('&') : ''))
                    .then(function (data) {
                        stats.channels = (data && data.channels) || [];
                        stats.capabilities = (data && data.capabilities) || [];
                        stats.deliverRate = data ? data.deliverRate : null;
                        stats.clickRate = data ? data.clickRate : null;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '统计加载失败'); })
                    .finally(function () { statsLoading.value = false; });
            }
            function reloadAll() {
                loadChannels();
                loadTasks();
                loadTemplates();
                if (activeTab.value === 'stats') loadStats();
            }
            function switchTab(value) {
                activeTab.value = value;
                if (value === 'tasks') loadTasks();
                if (value === 'templates') loadTemplates();
                if (value === 'stats') loadStats();
            }

            // ---------------- 模板
            function openTemplateCreate() {
                Object.assign(tplForm, blankTemplate());
                tplForm.id = null;
                tplOpen.value = true;
            }
            function openTemplateEdit(tpl) {
                Object.assign(tplForm, {
                    id: tpl.id, templateCode: tpl.templateCode, templateName: tpl.templateName,
                    scene: tpl.scene, titleTemplate: tpl.titleTemplate, contentTemplate: tpl.contentTemplate,
                    defaultChannels: (tpl.defaultChannels || []).slice(), status: tpl.status, remark: tpl.remark || ''
                });
                tplOpen.value = true;
            }
            function submitTemplate() {
                if (!tplForm.templateCode) { El.ElMessage.warning('请填写模板编码'); return; }
                if (!tplForm.templateName) { El.ElMessage.warning('请填写模板名称'); return; }
                if (!tplForm.titleTemplate || !tplForm.contentTemplate) { El.ElMessage.warning('标题与正文模板不能为空'); return; }
                if (!tplForm.defaultChannels.length) { El.ElMessage.warning('至少选择一个默认渠道'); return; }
                var payload = {
                    templateCode: tplForm.templateCode, templateName: tplForm.templateName, scene: tplForm.scene,
                    titleTemplate: tplForm.titleTemplate, contentTemplate: tplForm.contentTemplate,
                    defaultChannels: tplForm.defaultChannels, status: tplForm.status, remark: tplForm.remark
                };
                tplSubmitting.value = true;
                var request = tplForm.id ? api.put('/api/admin/push/templates/' + tplForm.id, payload)
                    : api.post('/api/admin/push/templates', payload);
                request.then(function () {
                    El.ElMessage.success(tplForm.id ? '模板已更新' : '模板已创建');
                    tplOpen.value = false;
                    loadTemplates();
                }).catch(function (error) { El.ElMessage.error(error.message || '保存失败'); })
                    .finally(function () { tplSubmitting.value = false; });
            }
            function removeTemplate(tpl) {
                El.ElMessageBox.confirm('模板删除后不影响历史任务快照，确定删除「' + tpl.templateName + '」？', '删除模板', { type: 'warning' })
                    .then(function () { return api.delete('/api/admin/push/templates/' + tpl.id); })
                    .then(function () { El.ElMessage.success('模板已删除'); loadTemplates(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '删除失败'); });
            }
            function openTest(tpl) {
                if (tpl.status !== 'ENABLED') { El.ElMessage.warning('停用的模板无法测试发送'); return; }
                testTemplate.id = tpl.id;
                testTemplate.templateName = tpl.templateName;
                testTemplate.templateCode = tpl.templateCode;
                testVars.value = (tpl.variables || []).map(function (name) { return { key: name, value: '' }; });
                testResult.value = null;
                testOpen.value = true;
            }
            function submitTest() {
                var variables = {};
                for (var i = 0; i < testVars.value.length; i++) {
                    var item = testVars.value[i];
                    if (!item.value) { El.ElMessage.warning('请填写变量 ' + item.key); return; }
                    variables[item.key] = item.value;
                }
                testSubmitting.value = true;
                api.post('/api/admin/push/templates/' + testTemplate.id + '/test', { variables: variables })
                    .then(function (data) {
                        testResult.value = data;
                        El.ElMessage.success('测试发送完成，请查看各渠道结果');
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '测试发送失败'); })
                    .finally(function () { testSubmitting.value = false; });
            }

            // ---------------- 任务
            function openTaskCreate() {
                Object.assign(taskForm, blankTask());
                taskForm.id = null;
                taskOpen.value = true;
            }
            function applyTemplate() {
                var tpl = templates.value.find(function (item) { return item.id === taskForm.templateId; });
                if (!tpl) return;
                taskForm.title = tpl.titleTemplate;
                taskForm.content = tpl.contentTemplate;
                taskForm.channels = (tpl.defaultChannels && tpl.defaultChannels.length) ? tpl.defaultChannels.slice() : ['LOG'];
                taskForm.variables = (tpl.variables || []).map(function (name) { return { key: name, value: '' }; });
            }
            function submitTask(action) {
                if (!taskForm.taskName) { El.ElMessage.warning('请填写任务名称'); return; }
                if (!taskForm.title || !taskForm.content) { El.ElMessage.warning('推送标题与正文不能为空'); return; }
                if (!taskForm.channels.length) { El.ElMessage.warning('至少选择一个渠道'); return; }
                if ((taskForm.audienceType === 'ROLE' || taskForm.audienceType === 'ORGANIZATION' || taskForm.audienceType === 'USER')
                    && !taskForm.audienceValue) { El.ElMessage.warning('请填写人群参数'); return; }
                if (action === 'SCHEDULE' && !taskForm.scheduledAt) { El.ElMessage.warning('请选择定时发送时间'); return; }
                var variables = {};
                taskForm.variables.forEach(function (item) { if (item.key) variables[item.key] = item.value; });
                var payload = {
                    taskName: taskForm.taskName, templateId: taskForm.templateId, title: taskForm.title,
                    content: taskForm.content, variables: variables, audienceType: taskForm.audienceType,
                    audienceValue: taskForm.audienceValue, channels: taskForm.channels,
                    scheduleType: taskForm.scheduleType,
                    scheduledAt: taskForm.scheduleType === 'SCHEDULED' && taskForm.scheduledAt ? taskForm.scheduledAt + ':00' : null,
                    frequencyLimit: taskForm.frequencyLimit, frequencyWindowHours: taskForm.frequencyWindowHours,
                    action: action, remark: taskForm.remark
                };
                var confirmText = action === 'SEND'
                    ? '将立即向选中人群发送推送并写入站内通知，确定继续？'
                    : (action === 'SCHEDULE' ? '将创建定时任务，到点自动发送，确定继续？' : '将保存为草稿，确定继续？');
                El.ElMessageBox.confirm(confirmText, '推送任务', { type: 'warning' })
                    .then(function () {
                        taskSubmitting.value = true;
                        var request = taskForm.id ? api.put('/api/admin/push/tasks/' + taskForm.id, payload)
                            : api.post('/api/admin/push/tasks', payload);
                        return request.then(function (task) {
                            taskOpen.value = false;
                            loadTasks();
                            if (action === 'SEND' && task) {
                                if (task.status === 'FAILED') {
                                    El.ElMessage.error('发送完成但有失败：' + (task.lastError || '请查看明细'));
                                } else if (task.status === 'PARTIAL') {
                                    El.ElMessage.warning('部分成功：成功 ' + task.successCount + ' 条，失败 ' + task.failedCount + ' 条');
                                } else {
                                    El.ElMessage.success('发送完成：总计 ' + task.totalCount + ' 条，成功 ' + task.successCount + ' 条');
                                }
                            } else {
                                El.ElMessage.success(action === 'SCHEDULE' ? '定时任务已创建' : '草稿已保存');
                            }
                        });
                    })
                    .catch(function (error) {
                        if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '提交失败');
                    })
                    .finally(function () { taskSubmitting.value = false; });
            }
            function sendTask(task) {
                El.ElMessageBox.confirm('立即发送「' + task.taskName + '」？将按人群逐用户投递并写入站内通知。', '发送推送', { type: 'warning' })
                    .then(function () { return api.post('/api/admin/push/tasks/' + task.id + '/send'); })
                    .then(function (data) {
                        El.ElMessage.success('已发送：成功 ' + (data.successCount || 0) + ' 条 / 总 ' + (data.totalCount || 0) + ' 条');
                        loadTasks();
                    })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '发送失败'); });
            }
            function cancelTask(task) {
                El.ElMessageBox.confirm('取消后该任务不会发送，确定取消？', '取消任务', { type: 'warning' })
                    .then(function () { return api.post('/api/admin/push/tasks/' + task.id + '/cancel'); })
                    .then(function () { El.ElMessage.success('任务已取消'); loadTasks(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '取消失败'); });
            }
            function retryTask(task) {
                El.ElMessageBox.confirm('将重试失败与未配置的投递明细，可能产生新的投递记录，确定继续？', '重试推送', { type: 'warning' })
                    .then(function () { return api.post('/api/admin/push/tasks/' + task.id + '/retry'); })
                    .then(function (data) {
                        El.ElMessage.success('重试完成：成功 ' + (data.successCount || 0) + ' 条，未配置 ' + (data.notConfiguredCount || 0) + ' 条');
                        loadTasks();
                    })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '重试失败'); });
            }
            function removeTask(task) {
                El.ElMessageBox.confirm('删除任务会同时删除投递明细，确定删除「' + task.taskName + '」？', '删除任务', { type: 'warning' })
                    .then(function () { return api.delete('/api/admin/push/tasks/' + task.id); })
                    .then(function () { El.ElMessage.success('任务已删除'); loadTasks(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '删除失败'); });
            }
            function openDeliveries(task) {
                deliveryTask.id = task.id;
                deliveryTask.taskName = task.taskName;
                deliveryPage.value = 1;
                deliveryOpen.value = true;
                loadDeliveries();
            }
            function loadDeliveries() {
                if (!deliveryTask.id) return;
                api.get('/api/admin/push/tasks/' + deliveryTask.id + '/deliveries?pageNum=' + deliveryPage.value + '&pageSize=20')
                    .then(function (data) {
                        deliveries.value = (data && data.records) || [];
                        deliveryTotal.value = (data && data.total) || 0;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '明细加载失败'); });
            }

            loadChannels();
            loadTasks();
            loadTemplates();
            loadRoles();
            loadOrganizations();

            return {
                tabs: tabs, activeTab: activeTab, tasks: tasks, templates: templates,
                roles: roles, organizations: organizations, scenes: scenes, channelOptions: channelOptions,
                taskStatus: taskStatus, taskKeyword: taskKeyword, tplScene: tplScene, tplStatus: tplStatus,
                taskLoading: taskLoading, tplLoading: tplLoading, statsLoading: statsLoading,
                taskOpen: taskOpen, tplOpen: tplOpen, testOpen: testOpen, deliveryOpen: deliveryOpen,
                taskSubmitting: taskSubmitting, tplSubmitting: tplSubmitting, testSubmitting: testSubmitting,
                taskForm: taskForm, tplForm: tplForm, testTemplate: testTemplate, testVars: testVars,
                testResult: testResult, deliveryTask: deliveryTask, deliveries: deliveries,
                deliveryTotal: deliveryTotal, deliveryPage: deliveryPage,
                stats: stats, statsFrom: statsFrom, statsTo: statsTo,
                loadTasks: loadTasks, loadTemplates: loadTemplates, loadStats: loadStats,
                loadDeliveries: loadDeliveries, reloadAll: reloadAll, switchTab: switchTab,
                sum: sum, canSend: canSend, fmt: fmt, pct: pct,
                openTaskCreate: openTaskCreate, applyTemplate: applyTemplate, submitTask: submitTask,
                sendTask: sendTask, cancelTask: cancelTask, retryTask: retryTask, removeTask: removeTask,
                openTemplateCreate: openTemplateCreate, openTemplateEdit: openTemplateEdit,
                submitTemplate: submitTemplate, removeTemplate: removeTemplate,
                openTest: openTest, submitTest: submitTest, openDeliveries: openDeliveries,
                TASK_STATUS: TASK_STATUS, DELIVERY_STATUS: DELIVERY_STATUS,
                AUDIENCE_LABEL: AUDIENCE_LABEL, CHANNEL_LABEL: CHANNEL_LABEL
            };
        }
    };
})();
