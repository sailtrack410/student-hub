/* 数据分析 A05：服务申请/工单/社区/用户/AI 五类指标、按天趋势与分类占比。
   数据全部来自 GET /api/admin/analytics/overview?days=7|30，不填充模拟数据。
   图表使用内联 SVG 折线 + CSS 条形；提供表格兜底与 CSV 导出。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var REQUEST_STATUS = {
        SUBMITTED: '待受理', PROCESSING: '处理中', COMPLETED: '已办结',
        REJECTED: '已驳回', CANCELLED: '已撤销'
    };
    var ORDER_STATUS = {
        PENDING_DISPATCH: '待派单', ASSIGNED: '已派单', PROCESSING: '处理中',
        ONSITE_PHOTO_RECORDED: '现场照片已记录', PENDING_CONFIRMATION: '待确认',
        COMPLETED: '已完成', REJECTED: '已驳回', CANCELLED: '已撤销'
    };
    var SERIES = [
        { key: 'requests', label: '服务申请', color: '#2563EB' },
        { key: 'workOrders', label: '工单', color: '#0EA5E9' },
        { key: 'posts', label: '社区帖子', color: '#16A34A' }
    ];
    var BAR_SERIES = [
        { key: 'newUsers', label: '新增用户', color: '#9333EA' },
        { key: 'aiSessions', label: 'AI 会话', color: '#D97706' }
    ];

    window.AdminPages.AnalyticsPage = {
        name: 'AnalyticsPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>数据分析</h1><div class="sub">服务 · 工单 · 社区 · 用户 · AI 五类指标</div></div>',
            '    <div class="r" style="flex-wrap:wrap;gap:8px">',
            '      <div style="display:flex;gap:4px"><button class="tab" :class="{ on: days === 7 }" @click="setDays(7)">近 7 天</button><button class="tab" :class="{ on: days === 30 }" @click="setDays(30)">近 30 天</button></div>',
            '      <button class="btn2" :disabled="!d || !d.trend.length" @click="exportCsv">导出 CSV</button>',
            '      <button class="btn2" @click="load">刷新</button>',
            '    </div></div>',

            '  <div v-if="loading" class="os-panel"><div class="os-empty">正在加载分析数据…</div></div>',
            '  <div v-else-if="error" class="os-panel"><div class="os-empty">{{ error }} <button class="os-btn sm" style="margin-left:8px" @click="load">重试</button></div></div>',

            '  <template v-else-if="d">',
            '    <div class="os-grid" style="grid-template-columns:repeat(5,1fr)">',
            '      <div class="os-kpi"><div class="k-label"><span class="dot"></span>服务申请</div><div class="k-val">{{ d.kpis.requestsCreated }}<small>件</small></div><div class="k-foot"><span>已办结 {{ d.kpis.requestsCompleted }} 件</span></div></div>',
            '      <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-info)"></span>工单</div><div class="k-val">{{ d.kpis.workOrdersCreated }}<small>单</small></div><div class="k-foot"><span>已办结 {{ d.kpis.workOrdersCompleted }} 单</span></div></div>',
            '      <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>社区内容</div><div class="k-val">{{ d.kpis.posts }}<small>帖</small></div><div class="k-foot"><span>评论 {{ d.kpis.comments }} 条</span></div></div>',
            '      <div class="os-kpi"><div class="k-label"><span class="dot" style="background:#9333EA"></span>用户</div><div class="k-val">{{ d.kpis.newUsers }}<small>新用户</small></div><div class="k-foot"><span>活跃 {{ d.kpis.activeUsers }} 人</span></div></div>',
            '      <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>AI</div><div class="k-val">{{ d.kpis.aiSessions }}<small>会话</small></div><div class="k-foot"><span>消息 {{ d.kpis.aiMessages }} 条</span></div></div>',
            '    </div>',

            '    <div class="os-panel">',
            '      <div class="os-panel-h"><h3>AI 成本</h3><span class="hint">{{ days }} 天 · 仅聚合 ai_message 历史 token，不调用模型 API</span><span style="flex:1"></span>',
            '        <span v-if="cost && cost.kpis" class="os-tag" :class="cost.kpis.pricingConfigured ? \'ok\' : \'warn\'">{{ cost.kpis.pricingConfigured ? "单价已配置" : cost.kpis.unpricedModels + " 个模型未配置单价" }}</span>',
            '        <span v-if="!canViewCost" class="os-tag warn">无权限</span>',
            '        <button v-if="canViewCost" class="os-btn sm" style="margin-left:8px" @click="openPricing()">单价配置</button></div>',
            '      <div v-if="costLoading" class="os-empty" style="padding:18px 0">正在聚合 AI 用量…</div>',
            '      <div v-else-if="costError" class="os-empty" style="padding:18px 0">{{ costError }} <button class="os-btn sm" style="margin-left:8px" @click="loadCost">重试</button></div>',
            '      <template v-else-if="cost && cost.kpis">',
            '        <div :style="{ display:\'grid\', gridTemplateColumns:\'repeat(4,1fr)\', borderBottom:\'1px solid var(--tk-row)\' }">',
            '          <div style="padding:10px 16px;border-right:1px solid var(--tk-row)"><div style="font-size:10px;color:var(--tk-muted)">AI 请求</div><b style="font-size:20px;font-weight:650;color:var(--tk-text);font-variant-numeric:tabular-nums">{{ fmtNum(cost.kpis.requests) }}</b><span style="font-size:10px;color:var(--tk-text3);margin-left:4px">条</span></div>',
            '          <div style="padding:10px 16px;border-right:1px solid var(--tk-row)"><div style="font-size:10px;color:var(--tk-muted)">Token 总量</div><b style="font-size:20px;font-weight:650;color:var(--tk-text);font-variant-numeric:tabular-nums">{{ fmtNum(cost.kpis.totalTokens) }}</b><div style="font-size:9.5px;color:var(--tk-text3)">prompt {{ fmtNum(cost.kpis.promptTokens) }} · completion {{ fmtNum(cost.kpis.completionTokens) }}</div></div>',
            '          <div style="padding:10px 16px;border-right:1px solid var(--tk-row)"><div style="font-size:10px;color:var(--tk-muted)">估算成本</div><b style="font-size:20px;font-weight:650" :style="{ color: cost.kpis.estimatedCost == null ? \'var(--tk-muted)\' : \'var(--tk-text)\', fontVariantNumeric:\'tabular-nums\' }">{{ fmtCost(cost.kpis.estimatedCost) }}</b><div style="font-size:9.5px;color:var(--tk-text3)">按每 1K tokens 单价估算</div></div>',
            '          <div style="padding:10px 16px"><div style="font-size:10px;color:var(--tk-muted)">单价状态</div><b style="font-size:14px;font-weight:600" :style="{ color: cost.kpis.pricingConfigured ? \'var(--tk-ok)\' : \'var(--tk-warn)\' }">{{ cost.kpis.pricingConfigured ? "全部已配置" : cost.kpis.unpricedModels + " 个模型缺失" }}</b><div style="font-size:9.5px;color:var(--tk-text3)">缺失时成本显示 —</div></div>',
            '        </div>',
            '        <div v-if="!cost.kpis.pricingConfigured" class="os-note warn" style="margin:10px 16px 0;padding:8px 11px"><b>成本未计算</b> 有 {{ cost.kpis.unpricedModels }} 个使用中的模型缺少输入或输出单价，为避免伪造金额，总成本与当日成本返回空。请在下方表格或右上「单价配置」中保存单价后刷新。</div>',
            '        <div style="display:grid;grid-template-columns:minmax(0,1.5fr) minmax(280px,.7fr);gap:0">',
            '          <div style="overflow:auto;max-height:260px;border-right:1px solid var(--tk-row)">',
            '            <table style="width:100%;border-collapse:collapse;table-layout:fixed"><thead><tr><th style="width:22%">模型</th><th style="width:13%">供应商</th><th style="width:8%">请求</th><th style="width:12%">prompt</th><th style="width:12%">completion</th><th style="width:13%">输入/1K</th><th style="width:13%">输出/1K</th><th>估算成本</th><th style="width:58px"></th></tr></thead><tbody>',
            '              <tr v-for="m in cost.byModel" :key="m.modelKey"><td><div class="os-mono" style="font-size:10.5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="m.modelKey">{{ m.modelKey }}</div><div v-if="!m.modelId" style="font-size:9px;color:var(--tk-warn)">未在 AI 模型配置中登记</div></td><td style="font-size:10.5px;color:var(--tk-text3)">{{ m.providerName || "—" }}</td><td class="os-mono">{{ fmtNum(m.requests) }}</td><td class="os-mono">{{ fmtNum(m.promptTokens) }}</td><td class="os-mono">{{ fmtNum(m.completionTokens) }}</td><td class="os-mono">{{ m.inputPricePer1k == null ? "—" : m.inputPricePer1k }}</td><td class="os-mono">{{ m.outputPricePer1k == null ? "—" : m.outputPricePer1k }}</td><td class="os-mono" :style="{ color: m.estimatedCost == null ? \'var(--tk-muted)\' : \'var(--tk-text)\' }">{{ fmtCost(m.estimatedCost) }}</td><td><button v-if="m.modelId" class="os-btn sm" @click="openPricing(m.modelId)">单价</button></td></tr>',
            '              <tr v-if="!cost.byModel.length"><td colspan="9" class="os-empty">该时间范围没有 AI 调用记录</td></tr>',
            '            </tbody></table>',
            '          </div>',
            '          <div style="padding:12px 14px">',
            '            <div style="font-size:10.5px;color:var(--tk-text3);margin-bottom:7px">每日 Token 趋势<span style="float:right;color:var(--tk-muted)">{{ cost.rangeStart }} ~ {{ cost.rangeEnd }}</span></div>',
            '            <div style="display:flex;align-items:flex-end;gap:2px;height:58px">',
            '              <div v-for="p in cost.trend" :key="p.date" :title="p.date + \' · \' + fmtNum(p.totalTokens) + \' tokens · 成本 \' + fmtCost(p.estimatedCost)" :style="{ flex:\'1\', minWidth:\'3px\', height: costBarHeight(p) + \'%\', background:\'var(--tk-brand)\', borderRadius:\'2px 2px 0 0\', opacity:0.8 }"></div>',
            '            </div>',
            '            <div style="display:flex;justify-content:space-between;font-size:9.5px;color:var(--tk-muted);margin-top:3px"><span>{{ cost.rangeStart.slice(5) }}</span><span>{{ cost.rangeEnd.slice(5) }}</span></div>',
            '            <div class="os-note" style="padding:8px 10px;margin-top:9px;font-size:10px;line-height:1.6">{{ cost.pricingNote }}</div>',
            '          </div>',
            '        </div>',
            '      </template>',
            '    </div>',

            '    <div style="display:grid;grid-template-columns:minmax(0,1.7fr) minmax(300px,.8fr);gap:16px">',
            '      <div class="os-panel">',
            '        <div class="os-panel-h"><h3>按天趋势</h3><span class="hint">{{ d.rangeStart }} ~ {{ d.rangeEnd }}</span>',
            '          <div class="r"><span v-for="s in series" :key="s.key" style="display:flex;align-items:center;gap:4px;font-size:10px;color:var(--tk-text3)"><i :style="{width:\'7px\',height:\'7px\',borderRadius:\'2px\',background:s.color,display:\'inline-block\'}"></i>{{ s.label }}</span></div></div>',
            '        <div class="os-panel-b" style="padding:10px 14px 12px">',
            '          <svg :viewBox="\'0 0 760 220\'" style="width:100%;height:190px;overflow:visible" preserveAspectRatio="none">',
            '            <g stroke="var(--tk-row)" stroke-width="1"><line v-for="n in 4" :key="n" x1="0" :y1="40 + (n-1)*52" x2="760" :y2="40 + (n-1)*52"/></g>',
            '            <path v-for="s in series" :key="s.key" :d="linePath(s.key)" fill="none" :stroke="s.color" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />',
            '            <g v-for="(p, i) in d.trend" :key="p.date">',
            '              <circle v-for="s in series" :key="s.key" :cx="pointX(i)" :cy="pointY(p[s.key])" r="2.6" fill="var(--tk-panel)" :stroke="s.color" stroke-width="1.8"><title>{{ p.date }} · {{ s.label }} {{ p[s.key] }}</title></circle>',
            '              <text v-if="i % labelStep === 0 || i === d.trend.length - 1" :x="pointX(i)" y="214" text-anchor="middle" font-size="10" fill="var(--tk-muted)">{{ shortDate(p.date) }}</text>',
            '            </g>',
            '          </svg>',
            '        </div>',
            '      </div>',
            '      <div class="os-panel">',
            '        <div class="os-panel-h"><h3>用户与 AI</h3><span class="hint">按天</span></div>',
            '        <div class="os-panel-b" style="display:flex;flex-direction:column;gap:12px">',
            '          <div v-for="s in barSeries" :key="s.key">',
            '            <div style="display:flex;justify-content:space-between;font-size:11px;color:var(--tk-text3);margin-bottom:6px"><span>{{ s.label }}</span><b style="color:var(--tk-text)">{{ totalOf(s.key) }}</b></div>',
            '            <div style="display:flex;align-items:flex-end;gap:2px;height:52px">',
            '              <div v-for="(p, i) in d.trend" :key="p.date + s.key" :title="p.date + \' \' + p[s.key]" :style="{ flex:\'1\', minWidth:\'2px\', height: barHeight(p[s.key], s.key) + \'%\', background:s.color, borderRadius:\'2px 2px 0 0\', opacity:0.85 }"></div>',
            '            </div>',
            '          </div>',
            '          <div class="os-note" style="padding:9px 11px"><b>口径</b> 新增用户按 sys_user.created_at；AI 会话按 ai_conversation；活跃用户 = 期间内产生申请/发帖/评论/AI 会话的去重用户。</div>',
            '        </div>',
            '      </div>',
            '    </div>',

            '    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:12px">',
            '      <div class="os-panel"><div class="os-panel-h"><h3>服务申请状态</h3></div><div class="os-panel-b" style="display:flex;flex-direction:column;gap:8px">',
            '        <div v-for="s in d.requestStatus" :key="s.name"><div style="display:flex;justify-content:space-between;font-size:11px;color:var(--tk-text3)"><span>{{ statusName(s.name, REQUEST_STATUS) }}</span><span>{{ s.value }} · {{ s.percent }}%</span></div><div style="height:6px;background:var(--tk-subtle);border-radius:3px;margin-top:4px;overflow:hidden"><div :style="{ width: s.percent + \'%\', height:\'6px\', background:\'var(--tk-brand)\' }"></div></div></div>',
            '        <div v-if="!d.requestStatus.length" class="os-empty">暂无数据</div>',
            '      </div></div>',
            '      <div class="os-panel"><div class="os-panel-h"><h3>工单状态</h3></div><div class="os-panel-b" style="display:flex;flex-direction:column;gap:8px">',
            '        <div v-for="s in d.workOrderStatus" :key="s.name"><div style="display:flex;justify-content:space-between;font-size:11px;color:var(--tk-text3)"><span>{{ statusName(s.name, ORDER_STATUS) }}</span><span>{{ s.value }} · {{ s.percent }}%</span></div><div style="height:6px;background:var(--tk-subtle);border-radius:3px;margin-top:4px;overflow:hidden"><div :style="{ width: s.percent + \'%\', height:\'6px\', background:\'var(--tk-info)\' }"></div></div></div>',
            '        <div v-if="!d.workOrderStatus.length" class="os-empty">暂无数据</div>',
            '      </div></div>',
            '      <div class="os-panel"><div class="os-panel-h"><h3>服务项 Top</h3></div><div class="os-panel-b" style="display:flex;flex-direction:column;gap:8px">',
            '        <div v-for="s in d.topServices" :key="s.name"><div style="display:flex;justify-content:space-between;font-size:11px;color:var(--tk-text3)"><span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ s.name }}</span><span>{{ s.value }}</span></div><div style="height:6px;background:var(--tk-subtle);border-radius:3px;margin-top:4px;overflow:hidden"><div :style="{ width: s.percent + \'%\', height:\'6px\', background:\'var(--tk-ok)\' }"></div></div></div>',
            '        <div v-if="!d.topServices.length" class="os-empty">暂无数据</div>',
            '      </div></div>',
            '    </div>',

            '    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:12px">',
            '      <div class="os-panel"><div class="os-panel-h"><h3>社区分区 Top</h3></div><div class="os-panel-b" style="display:flex;flex-direction:column;gap:8px">',
            '        <div v-for="s in d.topSections" :key="s.name" style="display:flex;justify-content:space-between;font-size:11px;color:var(--tk-text3)"><span>{{ s.name }}</span><b style="color:var(--tk-text)">{{ s.value }} 帖</b></div>',
            '        <div v-if="!d.topSections.length" class="os-empty">暂无数据</div></div></div>',
            '      <div class="os-panel"><div class="os-panel-h"><h3>新增用户身份</h3></div><div class="os-panel-b" style="display:flex;flex-direction:column;gap:8px">',
            '        <div v-for="s in d.userIdentities" :key="s.name" style="display:flex;justify-content:space-between;font-size:11px;color:var(--tk-text3)"><span>{{ s.name }}</span><b style="color:var(--tk-text)">{{ s.value }} 人</b></div>',
            '        <div v-if="!d.userIdentities.length" class="os-empty">暂无数据</div></div></div>',
            '      <div class="os-panel"><div class="os-panel-h"><h3>社区举报状态</h3></div><div class="os-panel-b" style="display:flex;flex-direction:column;gap:8px">',
            '        <div v-for="s in d.reportStatus" :key="s.name" style="display:flex;justify-content:space-between;font-size:11px;color:var(--tk-text3)"><span>{{ statusName(s.name, {PENDING:\'待处理\',ACCEPTED:\'已受理\',REJECTED:\'已驳回\'}) }}</span><b style="color:var(--tk-text)">{{ s.value }}</b></div>',
            '        <div v-if="!d.reportStatus.length" class="os-empty">暂无数据</div></div></div>',
            '    </div>',

            '    <div class="os-panel">',
            '      <div class="os-panel-h"><h3>趋势数据表</h3><span class="hint">表格兜底，便于核对与复制</span><div class="r"><button class="os-btn sm" @click="showTable = !showTable">{{ showTable ? "收起" : "展开" }}</button></div></div>',
            '      <div v-if="showTable" style="overflow:auto;max-height:300px">',
            '        <table style="width:100%;border-collapse:collapse"><thead><tr><th>日期</th><th>服务申请</th><th>工单</th><th>社区帖子</th><th>新增用户</th><th>AI 会话</th></tr></thead><tbody>',
            '          <tr v-for="p in d.trend" :key="p.date"><td class="os-mono">{{ p.date }}</td><td class="os-mono">{{ p.requests }}</td><td class="os-mono">{{ p.workOrders }}</td><td class="os-mono">{{ p.posts }}</td><td class="os-mono">{{ p.newUsers }}</td><td class="os-mono">{{ p.aiSessions }}</td></tr>',
            '        </tbody></table>',
            '      </div>',
            '    </div>',
            '  </template>',

            '  <div v-if="pricingOpen" class="cmd-mask" style="padding-top:16vh" @click.self="pricingOpen = false">',
            '    <div class="cmd-panel" style="width:500px">',
            '      <div class="cmd-input"><b style="font-size:13px;color:var(--tk-text)">模型单价配置</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="pricingOpen = false">✕</span></div>',
            '      <div style="padding:16px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-field"><label>模型</label><select class="os-select" v-model.number="pricingForm.modelId" @change="onPricingModelChange"><option :value="null" disabled>请选择模型</option><option v-for="m in (cost ? cost.models : [])" :key="m.modelId" :value="m.modelId">{{ m.providerName || "未知供应商" }} / {{ m.providerModelId }}{{ m.enabled ? "" : "（已停用）" }}</option></select></div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>输入单价 / 1K tokens</label><input class="os-input" type="number" min="0" step="0.0001" v-model="pricingForm.inputPrice" placeholder="如 0.001" /></div>',
            '          <div class="os-field"><label>输出单价 / 1K tokens</label><input class="os-input" type="number" min="0" step="0.0001" v-model="pricingForm.outputPrice" placeholder="如 0.002" /></div>',
            '        </div>',
            '        <div class="os-note warn" style="margin:0"><b>口径</b> 两项都填写后才计算成本；任一项留空即视为未配置，成本返回 null（不伪造金额）。留空两项保存表示清除已配置单价。币种以供应商定价为准，平台不做汇率换算。</div>',
            '        <div style="display:flex;justify-content:flex-end;gap:9px"><button class="os-btn" @click="pricingOpen = false">取消</button><button class="os-btn primary" :disabled="pricingSaving || !pricingForm.modelId" @click="savePricing">{{ pricingSaving ? "保存中…" : "保存单价" }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var d = Vue.ref(null);
            var days = Vue.ref(7);
            var loading = Vue.ref(false);
            var error = Vue.ref('');
            var showTable = Vue.ref(false);
            var canViewCost = Vue.computed(function () {
                var user = (window.AdminApp && window.AdminApp.state && window.AdminApp.state.user) || {};
                var perms = (user.permissions || []).slice();
                var roles = user.roles || [];
                if (roles.indexOf('PLATFORM_ADMIN') >= 0 && perms.indexOf('platform:all') < 0) perms.push('platform:all');
                return perms.indexOf('platform:all') >= 0 || perms.indexOf('ai:manage') >= 0;
            });
            var cost = Vue.ref(null);
            var costLoading = Vue.ref(false);
            var costError = Vue.ref('');
            var pricingOpen = Vue.ref(false);
            var pricingSaving = Vue.ref(false);
            var pricingForm = Vue.reactive({ modelId: null, inputPrice: '', outputPrice: '' });

            function load() {
                loading.value = true;
                error.value = '';
                api.get('/api/admin/analytics/overview?days=' + days.value)
                    .then(function (data) {
                        d.value = data;
                    })
                    .catch(function (err) {
                        d.value = null;
                        error.value = (err && err.message) || '分析数据加载失败';
                        El.ElMessage.error(error.value);
                    })
                    .finally(function () { loading.value = false; });
                loadCost();
            }
            function loadCost() {
                if (!canViewCost.value) {
                    cost.value = null;
                    costError.value = '当前账号无 AI 成本查看权限（需要 platform:all 或 ai:manage）';
                    return;
                }
                costLoading.value = true;
                costError.value = '';
                api.get('/api/admin/ai/usage/cost?days=' + days.value)
                    .then(function (data) { cost.value = data; })
                    .catch(function (err) {
                        cost.value = null;
                        costError.value = (err && err.message) || 'AI 成本数据加载失败';
                    })
                    .finally(function () { costLoading.value = false; });
            }
            function setDays(value) {
                if (days.value === value) return;
                days.value = value;
                load();
            }

            var maxValue = Vue.computed(function () {
                if (!d.value || !d.value.trend.length) return 1;
                var max = 1;
                d.value.trend.forEach(function (p) {
                    SERIES.forEach(function (s) { max = Math.max(max, +p[s.key] || 0); });
                });
                return max;
            });
            var labelStep = Vue.computed(function () {
                var len = d.value && d.value.trend ? d.value.trend.length : 7;
                return Math.max(1, Math.ceil(len / 8));
            });
            function pointX(i) {
                var len = d.value ? d.value.trend.length : 1;
                return 24 + i * (712 / Math.max(len - 1, 1));
            }
            function pointY(value) {
                return 178 - (+value || 0) / maxValue.value * 128;
            }
            function linePath(key) {
                if (!d.value) return '';
                return d.value.trend.map(function (p, i) {
                    return (i === 0 ? 'M' : 'L') + pointX(i).toFixed(1) + ' ' + pointY(p[key]).toFixed(1);
                }).join(' ');
            }
            function totalOf(key) {
                if (!d.value) return 0;
                return d.value.trend.reduce(function (sum, p) { return sum + (+p[key] || 0); }, 0);
            }
            var maxBar = Vue.computed(function () {
                if (!d.value) return 1;
                var max = 1;
                d.value.trend.forEach(function (p) {
                    BAR_SERIES.forEach(function (s) { max = Math.max(max, +p[s.key] || 0); });
                });
                return max;
            });
            function barHeight(value, key) {
                return Math.max(2, Math.round((+value || 0) / maxBar.value * 100));
            }
            function shortDate(date) { return date ? String(date).slice(5) : ''; }
            function statusName(name, map) { return (map && map[name]) || name; }
            function fmtNum(value) {
                if (value == null) return '0';
                var number = Number(value);
                return isFinite(number) ? number.toLocaleString('zh-CN') : String(value);
            }
            function fmtCost(value) {
                if (value == null) return '—';
                var number = Number(value);
                if (!isFinite(number)) return '—';
                if (number === 0) return '0';
                return number.toFixed(6).replace(/0+$/, '').replace(/\.$/, '');
            }
            var maxCostTokens = Vue.computed(function () {
                var max = 0;
                (cost.value && cost.value.trend || []).forEach(function (p) {
                    max = Math.max(max, +p.totalTokens || 0);
                });
                return max;
            });
            function costBarHeight(point) {
                if (!maxCostTokens.value) return 2;
                return Math.max(2, Math.round((+point.totalTokens || 0) / maxCostTokens.value * 100));
            }
            function openPricing(modelId) {
                var models = (cost.value && cost.value.models) || [];
                if (!models.length) {
                    El.ElMessage.warning('暂无 AI 模型配置，请先在 AI 供应商/模型页添加模型');
                    return;
                }
                pricingForm.modelId = modelId || models[0].modelId;
                syncPricingForm();
                pricingOpen.value = true;
            }
            function syncPricingForm() {
                var models = (cost.value && cost.value.models) || [];
                var model = null;
                models.forEach(function (item) { if (item.modelId === pricingForm.modelId) model = item; });
                pricingForm.inputPrice = model && model.inputPricePer1k != null ? String(model.inputPricePer1k) : '';
                pricingForm.outputPrice = model && model.outputPricePer1k != null ? String(model.outputPricePer1k) : '';
            }
            function onPricingModelChange() { syncPricingForm(); }
            function toPrice(value) {
                if (value === '' || value === null || value === undefined) return null;
                var number = Number(value);
                return isFinite(number) ? number : NaN;
            }
            function savePricing() {
                if (!pricingForm.modelId) { El.ElMessage.warning('请选择模型'); return; }
                var input = toPrice(pricingForm.inputPrice);
                var output = toPrice(pricingForm.outputPrice);
                if (isNaN(input) || isNaN(output) || (input != null && input < 0) || (output != null && output < 0)) {
                    El.ElMessage.warning('单价必须为非负数字');
                    return;
                }
                if ((input == null) !== (output == null)) {
                    El.ElMessage.warning('输入/输出单价只填一项时仍视为未配置，两项都填写才会计算成本');
                }
                pricingSaving.value = true;
                api.post('/api/admin/ai/models/' + pricingForm.modelId + '/pricing',
                        { inputPricePer1k: input, outputPricePer1k: output })
                    .then(function () {
                        El.ElMessage.success('模型单价已保存');
                        pricingOpen.value = false;
                        loadCost();
                    })
                    .catch(function (err) { El.ElMessage.error((err && err.message) || '单价保存失败'); })
                    .finally(function () { pricingSaving.value = false; });
            }

            function exportCsv() {
                if (!d.value) return;
                var lines = [];
                lines.push('"数据分析导出","' + d.value.rangeStart + ' ~ ' + d.value.rangeEnd + '","' + days.value + ' 天"');
                lines.push('');
                lines.push('"汇总指标","数值"');
                var kpis = d.value.kpis;
                [['服务申请', kpis.requestsCreated], ['已办结申请', kpis.requestsCompleted],
                    ['工单', kpis.workOrdersCreated], ['已办结工单', kpis.workOrdersCompleted],
                    ['社区帖子', kpis.posts], ['社区评论', kpis.comments], ['新增用户', kpis.newUsers],
                    ['活跃用户', kpis.activeUsers], ['AI 会话', kpis.aiSessions], ['AI 消息', kpis.aiMessages]]
                    .forEach(function (row) { lines.push('"' + row[0] + '","' + row[1] + '"'); });
                lines.push('');
                lines.push('"日期","服务申请","工单","社区帖子","新增用户","AI 会话"');
                d.value.trend.forEach(function (p) {
                    lines.push([p.date, p.requests, p.workOrders, p.posts, p.newUsers, p.aiSessions]
                        .map(function (v) { return '"' + v + '"'; }).join(','));
                });
                lines.push('');
                lines.push('"分类","名称","数量","占比%"');
                [['服务申请状态', d.value.requestStatus], ['工单状态', d.value.workOrderStatus],
                    ['服务项', d.value.topServices], ['社区分区', d.value.topSections],
                    ['用户身份', d.value.userIdentities], ['举报状态', d.value.reportStatus]]
                    .forEach(function (group) {
                        group[1].forEach(function (s) {
                            lines.push(['"' + group[0] + '"', '"' + s.name + '"', s.value, s.percent].join(','));
                        });
                    });
                var blob = new Blob(['\ufeff' + lines.join('\n')], { type: 'text/csv;charset=utf-8' });
                var a = document.createElement('a');
                a.href = URL.createObjectURL(blob);
                a.download = 'analytics-' + days.value + 'd-' + d.value.rangeEnd + '.csv';
                a.click();
                URL.revokeObjectURL(a.href);
            }

            load();
            return {
                d: d, days: days, loading: loading, error: error, showTable: showTable,
                load: load, setDays: setDays, series: SERIES, barSeries: BAR_SERIES,
                pointX: pointX, pointY: pointY, linePath: linePath, totalOf: totalOf,
                barHeight: barHeight, shortDate: shortDate, labelStep: labelStep,
                statusName: statusName, exportCsv: exportCsv,
                cost: cost, costLoading: costLoading, costError: costError, loadCost: loadCost, canViewCost: canViewCost,
                fmtNum: fmtNum, fmtCost: fmtCost, costBarHeight: costBarHeight,
                pricingOpen: pricingOpen, pricingSaving: pricingSaving, pricingForm: pricingForm,
                openPricing: openPricing, onPricingModelChange: onPricingModelChange, savePricing: savePricing,
                REQUEST_STATUS: REQUEST_STATUS, ORDER_STATUS: ORDER_STATUS
            };
        }
    };
})();
