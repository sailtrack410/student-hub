/* 控制面总览：工单、用户、AI、社区与系统健康聚合。
   工单 KPI / 趋势 / 分类 / 最新工单来自 /api/admin/dashboard，
   社区指标来自 /api/admin/community/stats，健康来自 /api/admin/system/health。
   不填充假数据：接口失败时对应区块明确显示不可用。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var STATUS_TEXT = {
        PENDING_DISPATCH: '待派发', ASSIGNED: '已派发', PROCESSING: '处理中',
        COMPLETED: '已完成', REJECTED: '已驳回', CANCELLED: '已取消'
    };
    var STATUS_TONE = {
        PENDING_DISPATCH: 'bad', ASSIGNED: 'info', PROCESSING: 'info',
        COMPLETED: 'ok', REJECTED: 'bad', CANCELLED: 'muted'
    };
    var CAT_COLORS = ['#2563EB', '#60A5FA', '#0EA5E9', '#94A3B8'];

    window.AdminPages.DashboardPage = {
        name: 'DashboardPage',
        template: [
            '<div class="pgw dash" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>总览</h1><div class="sub">{{ todayLabel }} · {{ rangeLabel }}</div></div>',
            '    <div class="r" style="flex-wrap:wrap;gap:8px">',
            '      <div style="display:flex;gap:4px"><button class="tab" :class="{ on: days === 7 }" @click="setDays(7)">近 7 天</button><button class="tab" :class="{ on: days === 30 }" @click="setDays(30)">近 30 天</button></div>',
            '      <input class="os-input" type="date" style="width:140px" v-model="customStart" title="开始日期" />',
            '      <span style="color:var(--tk-muted);font-size:11px">至</span>',
            '      <input class="os-input" type="date" style="width:140px" v-model="customEnd" title="结束日期" />',
            '      <button class="btn2" @click="applyRange">应用</button>',
            '      <span class="os-tag info">数据实时聚合</span><button class="btn2" @click="load()">刷新</button></div></div>',

            '  <div class="os-grid" style="grid-template-columns:repeat(4,1fr)">',
            '    <div class="os-kpi"><div class="k-label"><span class="dot"></span>待处理工单</div><div class="k-val">{{ d ? d.pendingWorkOrders : "—" }}<small v-if="d">单</small></div>',
            '      <div class="k-foot" v-if="d"><b :class="d.pendingDeltaPercent <= 0 ? \'os-up\' : \'os-down\'">{{ d.pendingDeltaPercent <= 0 ? "↓" : "↑" }} {{ Math.abs(d.pendingDeltaPercent) }}%</b><span>较上一周期</span></div>',
            '      <svg class="k-spark" width="72" height="22" viewBox="0 0 72 22"><polyline :points="spark(d ? d.trend.map(function(p){return p.created;}) : [])" fill="none" stroke="var(--tk-brand)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-info)"></span>今日新增用户</div><div class="k-val">{{ d ? d.todayNewUsers : "—" }}<small v-if="d">人</small></div>',
            '      <div class="k-foot" v-if="d"><b :class="d.todayNewUsersDeltaPercent >= 0 ? \'os-up\' : \'os-down\'">{{ d.todayNewUsersDeltaPercent >= 0 ? "↑" : "↓" }} {{ Math.abs(d.todayNewUsersDeltaPercent) }}%</b><span>较昨日</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>今日 AI 会话</div><div class="k-val">{{ d ? d.todayAiSessions : "—" }}<small v-if="d">次</small></div>',
            '      <div class="k-foot" v-if="d"><b :class="d.todayAiSessionsDeltaPercent >= 0 ? \'os-up\' : \'os-down\'">{{ d.todayAiSessionsDeltaPercent >= 0 ? "↑" : "↓" }} {{ Math.abs(d.todayAiSessionsDeltaPercent) }}%</b><span>较昨日</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-bad)"></span>待审批服务</div><div class="k-val">{{ d ? d.pendingRequests : "—" }}<small v-if="d">件</small></div>',
            '      <div class="k-foot" v-if="d"><b :class="d.pendingRequestsDelta <= 0 ? \'os-up\' : \'os-down\'">{{ d.pendingRequestsDelta > 0 ? "+" : "" }}{{ d.pendingRequestsDelta }}</b><span>较昨日</span></div></div>',
            '  </div>',

            '  <div style="display:grid;grid-template-columns:minmax(0,1.05fr) minmax(0,.95fr);gap:16px">',
            '    <div class="os-panel">',
            '      <div class="os-panel-h"><h3>今日待办</h3><span class="hint">按当前账号权限过滤</span><span style="flex:1"></span><span class="os-tag" :class="todoTotal > 0 ? \'warn\' : \'ok\'">{{ todoTotal > 0 ? todoTotal + " 项待处理" : "已清空" }}</span></div>',
            '      <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:0">',
            '        <div v-for="t in visibleTodos" :key="t.key" style="padding:10px 13px;border-bottom:1px solid var(--tk-row);border-right:1px solid var(--tk-row);cursor:pointer" title="前往处理" @click="go(t.route)">',
            '          <div style="display:flex;align-items:center;gap:6px"><span class="os-dot" :class="t.count > 0 ? (t.tone === \'bad\' ? \'bad\' : (t.tone === \'warn\' ? \'warn\' : \'ok\')) : \'muted\'"></span><span style="font-size:11px;color:var(--tk-text3)">{{ t.label }}</span></div>',
            '          <b style="display:block;font-size:21px;font-weight:650;margin-top:3px;font-variant-numeric:tabular-nums" :style="{ color: t.count > 0 && t.tone === \'bad\' ? \'var(--tk-bad)\' : \'var(--tk-text)\' }">{{ t.count }}</b>',
            '        </div>',
            '        <div v-if="!visibleTodos.length" class="os-empty" style="grid-column:span 3">当前账号没有可查看的待办</div>',
            '      </div>',
            '    </div>',
            '    <div class="os-panel">',
            '      <div class="os-panel-h"><h3>快捷入口</h3><span class="hint">按权限显示</span></div>',
            '      <div style="display:grid;grid-template-columns:repeat(5,1fr);gap:0">',
            '        <div v-for="q in visibleQuickEntries" :key="q.key" class="cmd-item" style="min-height:58px;justify-content:center;text-align:center;border-right:1px solid var(--tk-row);border-bottom:1px solid var(--tk-row)" @click="go(q.route)"><span style="font-size:12px;color:var(--tk-text2)">{{ q.label }}</span></div>',
            '        <div v-if="!visibleQuickEntries.length" class="os-empty" style="grid-column:span 5">暂无可用入口</div>',
            '      </div>',
            '    </div>',
            '  </div>',

            '  <div style="display:grid;grid-template-columns:minmax(0,1.7fr) minmax(300px,.8fr);gap:16px">',
            '    <div class="os-panel">',
            '      <div class="os-panel-h"><h3>工单处理趋势</h3><span class="hint">{{ rangeLabel }} 新增 / 已解决</span>',
            '        <div class="r"><span style="display:flex;align-items:center;gap:4px;font-size:10px;color:var(--tk-text3)"><i style="width:7px;height:7px;border-radius:2px;background:var(--tk-brand);display:inline-block"></i>新增</span>',
            '        <span style="display:flex;align-items:center;gap:4px;font-size:10px;color:var(--tk-text3)"><i style="width:7px;height:7px;border-radius:2px;background:var(--tk-ok);display:inline-block"></i>已解决</span></div></div>',
            '      <div class="os-panel-b" style="padding:10px 14px 12px">',
            '        <div style="display:flex;gap:24px;margin-bottom:8px" v-if="d">',
            '          <span><b style="font-size:18px;color:var(--tk-text);font-weight:650">{{ d.weekNewWorkOrders }}</b><span style="font-size:10px;color:var(--tk-text3);margin-left:5px">范围内新增</span></span>',
            '          <span><b style="font-size:18px;color:var(--tk-text);font-weight:650">{{ d.weekResolvedWorkOrders }}</b><span style="font-size:10px;color:var(--tk-text3);margin-left:5px">已解决</span></span>',
            '          <span><b style="font-size:18px;color:var(--tk-brand);font-weight:650">{{ d.resolveRatePercent }}%</b><span style="font-size:10px;color:var(--tk-text3);margin-left:5px">解决率</span></span>',
            '          <span><b style="font-size:18px;color:var(--tk-text);font-weight:650">{{ d.dailyAvgNew }}</b><span style="font-size:10px;color:var(--tk-text3);margin-left:5px">日均</span></span>',
            '        </div>',
            '        <svg v-if="d" :viewBox="\'0 0 760 220\'" style="width:100%;height:170px;overflow:visible" preserveAspectRatio="none">',
                        '          <g stroke="var(--tk-row)" stroke-width="1"><line v-for="n in 4" :key="n" x1="0" :y1="40 + (n-1)*52" x2="760" :y2="40 + (n-1)*52"/></g>',
            '          <path :d="areaPath" fill="rgba(37,99,235,.07)" />',
            '          <path :d="linePathCreated" fill="none" stroke="var(--tk-brand)" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />',
            '          <path :d="linePathResolved" fill="none" stroke="var(--tk-ok)" stroke-width="1.8" stroke-dasharray="5 5" stroke-linecap="round" stroke-linejoin="round" />',
            '          <g v-for="(p, i) in d.trend" :key="p.day">',
            '            <circle :cx="pointX(i)" :cy="pointY(p.created)" r="3.2" fill="var(--tk-panel)" stroke="var(--tk-brand)" stroke-width="2"><title>{{ p.day }} 新增 {{ p.created }}</title></circle>',
            '            <circle :cx="pointX(i)" :cy="pointY(p.resolved)" r="2.6" fill="var(--tk-panel)" stroke="var(--tk-ok)" stroke-width="1.8"><title>{{ p.day }} 已解决 {{ p.resolved }}</title></circle>',
            '            <text :x="pointX(i)" y="214" text-anchor="middle" font-size="10" fill="var(--tk-muted)">{{ p.day }}</text>',
            '          </g>',
            '        </svg>',
            '        <div v-if="!d" class="os-empty">工单数据暂不可用</div>',
            '      </div>',
            '    </div>',

            '    <div class="os-panel">',
            '      <div class="os-panel-h"><h3>工单分类占比</h3><span class="hint">范围内</span></div>',
            '      <div class="os-panel-b" style="display:flex;align-items:center;gap:20px">',
            '        <div style="width:124px;height:124px;flex:none;position:relative">',
            '          <svg viewBox="0 0 44 44" width="124" height="124" style="transform:rotate(-90deg)">',
            '            <circle cx="22" cy="22" r="17" fill="none" stroke="var(--tk-subtle)" stroke-width="6"/>',
            '            <circle v-for="(c, i) in (d ? d.categories : [])" :key="c.name" cx="22" cy="22" r="17" fill="none" :stroke="CAT_COLORS[i % CAT_COLORS.length]" stroke-width="6" :stroke-dasharray="donutDash(c)" :stroke-dashoffset="donutOffset(i)"/>',
            '          </svg>',
            '          <div style="position:absolute;inset:26px;border-radius:999px;background:var(--tk-panel);display:grid;place-items:center"><span style="text-align:center"><b style="font-size:22px;font-weight:650;color:var(--tk-text);font-variant-numeric:tabular-nums">{{ d ? d.weekNewWorkOrders : "—" }}</b><div style="font-size:10px;color:var(--tk-text3)">范围内工单</div></span></div>',
            '        </div>',
            '        <div style="display:flex;flex-direction:column;gap:10px;flex:1;min-width:0">',
            '          <div v-for="(c, i) in (d ? d.categories : [])" :key="c.name" style="display:flex;align-items:center;gap:8px;font-size:12px;color:var(--tk-text2)">',
            '            <i :style="{ width: \'7px\', height: \'7px\', borderRadius: \'2px\', background: CAT_COLORS[i % CAT_COLORS.length], flex: \'none\' }"></i><span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ c.name }}</span><b style="margin-left:auto;font-weight:550;color:var(--tk-text)">{{ c.percent }}%</b>',
            '          </div>',
            '          <div v-if="!d || !d.categories.length" style="font-size:11px;color:var(--tk-muted)">范围内暂无工单</div>',
            '        </div>',
            '      </div>',
            '      <div style="border-top:1px solid var(--tk-row);padding:10px 14px;display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px">',
            '        <div><div style="font-size:10px;color:var(--tk-muted);letter-spacing:.05em">解决率</div><b style="font-size:14px;color:var(--tk-text)">{{ d ? d.resolveRatePercent : "—" }}%</b></div>',
            '        <div><div style="font-size:10px;color:var(--tk-muted);letter-spacing:.05em">日均新增</div><b style="font-size:14px;color:var(--tk-text)">{{ d ? d.dailyAvgNew : "—" }}</b></div>',
            '        <div><div style="font-size:10px;color:var(--tk-muted);letter-spacing:.05em">范围内已解决</div><b style="font-size:14px;color:var(--tk-text)">{{ d ? d.weekResolvedWorkOrders : "—" }}</b></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            '  <div style="display:grid;grid-template-columns:minmax(0,1.15fr) minmax(0,.85fr);gap:16px">',
            '    <div class="os-panel" style="display:flex;flex-direction:column;min-height:0">',
            '      <div class="os-panel-h"><h3>最新工单</h3><span class="hint">按创建时间倒序</span><div class="r"><span style="font-size:11px;color:var(--tk-brand);cursor:pointer" @click="go(\'workorders\')">查看全部 →</span></div></div>',
            '      <div style="overflow:auto">',
            '        <table style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '          <thead><tr><th>工单</th><th style="width:92px">状态</th><th style="width:128px">工单号</th><th style="width:118px">创建时间</th></tr></thead>',
            '          <tbody>',
            '            <tr v-for="o in (d ? d.latestOrders : [])" :key="o.orderNo">',
            '              <td><b style="font-weight:550;color:var(--tk-text)">{{ o.title }}</b></td>',
            '              <td><span class="os-tag" :class="statusTone(o.status)">{{ statusText(o.status) }}</span></td>',
            '              <td class="os-mono" style="color:var(--tk-text3)">{{ o.orderNo }}</td>',
            '              <td style="color:var(--tk-text3)">{{ fmtTime(o.createdAt) }}</td>',
            '            </tr>',
            '            <tr v-if="!d || !d.latestOrders.length"><td colspan="4" class="os-empty">暂无工单</td></tr>',
            '          </tbody>',
            '        </table>',
            '      </div>',
            '    </div>',

            '    <div class="os-panel">',
            '      <div class="os-panel-h"><h3>社区运行</h3><span class="hint">近 7 天</span><div class="r"><span style="font-size:11px;color:var(--tk-brand);cursor:pointer" @click="go(\'community-overview\')">社区概览 →</span></div></div>',
            '      <div v-if="c" style="display:grid;grid-template-columns:1fr 1fr;gap:0">',
            '        <div style="padding:10px 14px;border-bottom:1px solid var(--tk-row);border-right:1px solid var(--tk-row)"><div style="font-size:10px;color:var(--tk-muted)">今日发帖</div><b style="font-size:22px;font-weight:650;color:var(--tk-text)">{{ c.todayPosts }}</b><span style="font-size:10px;color:var(--tk-text3);margin-left:5px">/ 总 {{ c.totalPosts }}</span></div>',
            '        <div style="padding:10px 14px;border-bottom:1px solid var(--tk-row)"><div style="font-size:10px;color:var(--tk-muted)">今日评论</div><b style="font-size:22px;font-weight:650;color:var(--tk-text)">{{ c.todayComments }}</b><span style="font-size:10px;color:var(--tk-text3);margin-left:5px">/ 总 {{ c.totalComments }}</span></div>',
            '        <div style="padding:10px 14px;border-right:1px solid var(--tk-row)"><div style="font-size:10px;color:var(--tk-muted)">7 天活跃用户</div><b style="font-size:22px;font-weight:650;color:var(--tk-text)">{{ c.activeUsers7d }}</b></div>',
            '        <div style="padding:10px 14px"><div style="font-size:10px;color:var(--tk-muted)">待处理举报</div><b style="font-size:22px;font-weight:650" :style="{ color: c.pendingReports > 0 ? \'var(--tk-bad)\' : \'var(--tk-text)\' }">{{ c.pendingReports }}</b><span v-if="c.pendingAppeals" style="font-size:10px;color:var(--tk-warn);margin-left:5px">申诉 {{ c.pendingAppeals }}</span></div>',
            '      </div>',
            '      <div v-if="c" style="padding:10px 14px">',
            '        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:7px"><span style="font-size:11px;color:var(--tk-text3)">分区帖子分布</span><span style="font-size:10px;color:var(--tk-muted)">{{ c.sections.length }} 个分区</span></div>',
            '        <div v-for="s in c.sections.slice(0,4)" :key="s.sectionId" style="display:flex;align-items:center;gap:8px;margin:5px 0">',
            '          <span style="font-size:11px;color:var(--tk-text3);width:74px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ s.sectionName }}</span>',
            '          <div style="flex:1;height:5px;background:var(--tk-subtle);border-radius:3px;overflow:hidden"><div :style="{ width: sectionWidth(s.count) + \'%\', height: \'5px\', background: \'var(--tk-brand)\', borderRadius: \'3px\' }"></div></div>',
            '          <b style="font-size:11px;font-weight:550;color:var(--tk-text2);width:30px;text-align:right">{{ s.count }}</b>',
            '        </div>',
            '        <div v-if="!c.sections.length" style="font-size:11px;color:var(--tk-muted)">暂无分区数据</div>',
            '      </div>',
            '      <div v-if="!c" class="os-empty">社区数据暂不可用</div>',
            '    </div>',
            '  </div>',

            '  <div class="os-panel">',
            '    <div class="os-panel-h"><h3>运行状态</h3><span class="hint">实时健康监测</span><div class="r"><span class="os-tag" :class="healthTone">{{ healthText }}</span></div></div>',
            '    <div :style="{gridTemplateColumns: \'repeat(\' + Math.max(1, Math.min(4, healthComponents.length)) + \',1fr)\'}" style="display:grid;gap:0">',
            '      <div v-for="h in healthComponents.slice(0,4)" :key="h.name" style="display:flex;align-items:center;gap:8px;padding:9px 14px;border-right:1px solid var(--tk-row)">',
            '        <span class="os-dot" :class="h.status === \'UP\' ? \'ok\' : (h.status === \'NOT_CONFIGURED\' ? \'muted\' : \'bad\')"></span><span style="font-size:12px;color:var(--tk-text2);flex:1">{{ h.name }}</span><span style="font-size:10px;color:var(--tk-muted)">{{ h.latencyMs != null ? h.latencyMs + "ms" : "—" }}</span>',
            '      </div>',
            '      <div v-if="!healthComponents.length" style="padding:18px;font-size:11px;color:var(--tk-muted);grid-column:span 4">健康接口不可用或当前账号无系统配置权限；请在「系统配置」查看。</div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var d = Vue.ref(null);
            var c = Vue.ref(null);
            var health = Vue.ref(null);
            var days = Vue.ref(7);
            var customStart = Vue.ref('');
            var customEnd = Vue.ref('');
            var todayLabel = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' });

            function pad(n) { return n < 10 ? '0' + n : '' + n; }
            function dateText(date) {
                return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
            }
            function setDays(value) {
                customStart.value = '';
                customEnd.value = '';
                if (days.value === value && d.value) return;
                days.value = value;
                load();
            }
            function applyRange() {
                if (!customStart.value || !customEnd.value) {
                    El.ElMessage.warning('请选择开始和结束日期');
                    return;
                }
                if (customStart.value > customEnd.value) {
                    El.ElMessage.warning('开始日期不能晚于结束日期');
                    return;
                }
                load(customStart.value, customEnd.value);
            }
            function permissions() {
                var user = (window.AdminApp && window.AdminApp.state && window.AdminApp.state.user) || {};
                var perms = (user.permissions || []).slice();
                var roles = user.roles || [];
                if (roles.indexOf('PLATFORM_ADMIN') >= 0 && perms.indexOf('platform:all') < 0) perms.push('platform:all');
                return perms;
            }
            function hasPermission(code) {
                var perms = permissions();
                return perms.indexOf('platform:all') >= 0 || perms.indexOf(code) >= 0;
            }
            var rangeLabel = Vue.computed(function () {
                if (d.value && d.value.rangeStart && d.value.rangeEnd) {
                    return d.value.rangeStart + ' ~ ' + d.value.rangeEnd;
                }
                return '近 ' + days.value + ' 天';
            });
            var visibleTodos = Vue.computed(function () {
                var list = (d.value && d.value.todos) || [];
                return list.filter(function (t) { return hasPermission(t.permission); });
            });
            var todoTotal = Vue.computed(function () {
                return visibleTodos.value.reduce(function (sum, t) { return sum + (+t.count || 0); }, 0);
            });
            var visibleQuickEntries = Vue.computed(function () {
                var list = (d.value && d.value.quickEntries) || [];
                return list.filter(function (q) { return hasPermission(q.permission); });
            });

            function load(startText, endText) {
                var end = endText ? new Date(endText + 'T00:00:00') : new Date();
                var start = startText ? new Date(startText + 'T00:00:00')
                    : new Date(end.getTime() - (days.value - 1) * 86400000);
                var diff = Math.round((end.getTime() - start.getTime()) / 86400000) + 1;
                if (diff >= 1 && diff <= 31) {
                    days.value = diff;
                }
                var query = '?startDate=' + dateText(start) + '&endDate=' + dateText(end);
                api.get('/api/admin/dashboard' + query).then(function (data) { d.value = data; })
                    .catch(function (error) { El.ElMessage.error(error.message || '总览加载失败'); });
                api.get('/api/admin/community/overview').then(function (data) { c.value = data; })
                    .catch(function () { c.value = null; });
                api.get('/api/admin/system/health').then(function (data) { health.value = data; })
                    .catch(function () { health.value = null; });
            }

            function spark(values) {
                if (!values || !values.length) return '';
                var max = Math.max.apply(null, values.concat([1]));
                var step = 72 / Math.max(values.length - 1, 1);
                return values.map(function (v, i) {
                    return (i * step).toFixed(1) + ',' + (20 - v / max * 16).toFixed(1);
                }).join(' ');
            }

            var points = Vue.computed(function () {
                if (!d.value || !d.value.trend.length) return [];
                var values = [];
                d.value.trend.forEach(function (p) { values.push(p.created, p.resolved); });
                var max = Math.max.apply(null, values.concat([1]));
                var len = d.value.trend.length;
                return d.value.trend.map(function (p, i) {
                    return {
                        x: 22 + i * (716 / Math.max(len - 1, 1)),
                        created: 178 - p.created / max * 128,
                        resolved: 178 - p.resolved / max * 128
                    };
                });
            });
            function splitPath(key) {
                return points.value.map(function (p, i) {
                    return (i === 0 ? 'M' : 'L') + p.x.toFixed(1) + ' ' + p[key].toFixed(1);
                }).join(' ');
            }
            var linePathCreated = Vue.computed(function () { return splitPath('created'); });
            var linePathResolved = Vue.computed(function () { return splitPath('resolved'); });
            var areaPath = Vue.computed(function () {
                if (!points.value.length) return '';
                return splitPath('created') + ' L' + points.value[points.value.length - 1].x.toFixed(1) + ' 190 L' + points.value[0].x.toFixed(1) + ' 190 Z';
            });
            function pointX(i) { return points.value[i] ? points.value[i].x : 0; }
            function pointY(v) {
                if (!d.value || !d.value.trend.length) return 0;
                var values = [];
                d.value.trend.forEach(function (p) { values.push(p.created, p.resolved); });
                var max = Math.max.apply(null, values.concat([1]));
                return 178 - v / max * 128;
            }

            var DONUT_C = 106.81;
            function donutDash(category) {
                var len = Math.max(0, Math.min(100, +category.percent || 0)) / 100 * DONUT_C;
                return len.toFixed(2) + ' ' + (DONUT_C - len).toFixed(2);
            }
            function donutOffset(index) {
                if (!d.value || !d.value.categories) return 0;
                var before = 0;
                for (var i = 0; i < index; i++) before += +d.value.categories[i].percent || 0;
                return (-before / 100 * DONUT_C).toFixed(2);
            }

            var healthComponents = Vue.computed(function () {
                return (health.value && health.value.components) || [];
            });
            var healthTone = Vue.computed(function () {
                var s = health.value && health.value.status;
                return s === 'UP' ? 'ok' : (s === 'DOWN' ? 'bad' : 'warn');
            });
            var healthText = Vue.computed(function () {
                var s = health.value && health.value.status;
                return s === 'UP' ? '运行正常' : (s === 'DOWN' ? '服务异常' : (s === 'DEGRADED' ? '部分降级' : '待检测'));
            });

            function statusText(s) { return STATUS_TEXT[s] || s || '—'; }
            function statusTone(s) {
                var t = STATUS_TONE[s] || 'muted';
                return t === 'ok' ? 'ok' : (t === 'bad' ? 'bad' : (t === 'info' ? 'info' : 'muted'));
            }
            function fmtTime(v) { return v ? String(v).replace('T', ' ').slice(5, 16) : '—'; }
            function go(route) { location.hash = '#/' + route; }
            function sectionWidth(count) {
                if (!c.value || !c.value.sections.length) return 0;
                var max = Math.max.apply(null, c.value.sections.map(function (s) { return s.count; }).concat([1]));
                return Math.max(Math.round(count / max * 100), 2);
            }

            load();
            return {
                d: d, c: c, days: days, todayLabel: todayLabel, rangeLabel: rangeLabel,
                visibleTodos: visibleTodos, todoTotal: todoTotal, visibleQuickEntries: visibleQuickEntries,
                customStart: customStart, customEnd: customEnd, applyRange: applyRange,
                setDays: setDays, load: load, spark: spark,
                linePathCreated: linePathCreated, linePathResolved: linePathResolved, areaPath: areaPath,
                pointX: pointX, pointY: pointY, donutDash: donutDash, donutOffset: donutOffset, healthComponents: healthComponents,
                healthTone: healthTone, healthText: healthText, statusText: statusText, statusTone: statusTone,
                fmtTime: fmtTime, go: go, sectionWidth: sectionWidth, CAT_COLORS: CAT_COLORS
            };
        }
    };
})();
