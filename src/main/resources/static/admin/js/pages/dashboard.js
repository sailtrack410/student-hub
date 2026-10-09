/* 工作台：按共享画布设计稿还原。
   KPI 四项 + 近7天工单趋势（双柱）+ 分类占比（conic 环）+ 最新工单 + 心理预警动态占位 + 系统状态。
   数据来自 /api/admin/dashboard 聚合接口（真实业务表统计）；预警动态与系统状态暂为静态占位。 */
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
    var CAT_COLORS = ['var(--tk-info)', 'var(--tk-ok)', 'var(--tk-warn)', 'var(--tk-muted)'];

    window.AdminPages.DashboardPage = {
        name: 'DashboardPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:20px;flex:1;min-height:0">',
            '  <div class="page-h">',
            '    <div><h1>工作台</h1><div class="sub">智慧校园运营概览 · {{ todayLabel }}</div></div>',
            '    <div class="r"><button class="btn2" @click="load">刷新</button></div>',
            '  </div>',

            '  <div v-if="d" style="display:grid;grid-template-columns:repeat(4,1fr);gap:20px">',
            '    <div class="lcard" style="gap:6px;padding:18px 20px">',
            '      <span style="font-size:14px;color:var(--tk-text3)">待处理工单</span>',
            '      <b style="font-size:30px;font-weight:650;color:var(--tk-text);line-height:1.1">{{ d.pendingWorkOrders }}</b>',
            '      <span style="font-size:13px"><span :style="{ color: d.pendingDeltaPercent <= 0 ? \'var(--tk-ok)\' : \'var(--tk-bad)\', fontWeight: 500 }">{{ d.pendingDeltaPercent <= 0 ? "↓" : "↑" }} {{ Math.abs(d.pendingDeltaPercent) }}%</span> <span style="color:var(--tk-text3)">较上周</span></span>',
            '    </div>',
            '    <div class="lcard" style="gap:6px;padding:18px 20px">',
            '      <span style="font-size:14px;color:var(--tk-text3)">今日新增用户</span>',
            '      <b style="font-size:30px;font-weight:650;color:var(--tk-text);line-height:1.1">{{ d.todayNewUsers }}</b>',
            '      <span style="font-size:13px;color:var(--tk-text3)">注册统计待接入</span>',
            '    </div>',
            '    <div class="lcard" style="gap:6px;padding:18px 20px">',
            '      <span style="font-size:14px;color:var(--tk-text3)">AI 会话量（今日）</span>',
            '      <b style="font-size:30px;font-weight:650;color:var(--tk-text);line-height:1.1">{{ d.todayAiSessions }}</b>',
            '      <span style="font-size:13px"><span :style="{ color: d.todayAiSessionsDeltaPercent <= 0 ? \'var(--tk-ok)\' : \'var(--tk-ok)\', fontWeight: 500 }">{{ d.todayAiSessionsDeltaPercent <= 0 ? "↓" : "↑" }} {{ Math.abs(d.todayAiSessionsDeltaPercent) }}%</span> <span style="color:var(--tk-text3)">较昨日</span></span>',
            '    </div>',
            '    <div class="lcard" style="gap:6px;padding:18px 20px">',
            '      <span style="font-size:14px;color:var(--tk-text3)">待审批申请</span>',
            '      <b style="font-size:30px;font-weight:650;color:var(--tk-text);line-height:1.1">{{ d.pendingRequests }}</b>',
            '      <span style="font-size:13px;color:var(--tk-text3)">服务申请待受理</span>',
            '    </div>',
            '  </div>',

            '  <div v-if="d" style="display:flex;gap:20px;flex:1;min-height:0">',
            '    <div style="flex:1.6;display:flex;flex-direction:column;gap:20px;min-width:0;min-height:0">',
            '      <div class="lcard" style="gap:14px">',
            '        <div class="cap" style="font-size:17px">工单处理趋势 · 近 7 天</div>',
            '        <div style="display:flex;gap:32px">',
            '          <span><b style="font-size:20px;font-weight:650;color:var(--tk-text)">{{ d.weekNewWorkOrders }} 单</b><div style="font-size:12px;color:var(--tk-text3)">本周新增</div></span>',
            '          <span><b style="font-size:20px;font-weight:650;color:var(--tk-text)">{{ d.weekResolvedWorkOrders }} 单</b><div style="font-size:12px;color:var(--tk-text3)">本周已解决</div></span>',
            '          <span><b style="font-size:20px;font-weight:650;color:var(--tk-info)">{{ d.resolveRatePercent }} %</b><div style="font-size:12px;color:var(--tk-text3)">解决率</div></span>',
            '          <span><b style="font-size:20px;font-weight:650;color:var(--tk-text)">{{ d.dailyAvgNew }} 单/日</b><div style="font-size:12px;color:var(--tk-text3)">日均新增</div></span>',
            '        </div>',
            '        <div style="display:flex;align-items:flex-end;gap:16px;min-height:150px;padding:0 4px">',
            '          <div v-for="p in d.trend" :key="p.day" style="flex:1;display:flex;flex-direction:column;align-items:center;gap:6px;min-width:0">',
            '            <div style="display:flex;align-items:flex-end;gap:5px;width:100%;justify-content:center;height:120px;border-bottom:1px solid var(--tk-row)">',
            '              <div style="display:flex;flex-direction:column;align-items:center;gap:2px">',
            '                <span style="font-size:10.5px;color:var(--tk-muted);font-variant-numeric:tabular-nums">{{ p.created }}/{{ p.resolved }}</span>',
            '                <i :style="barStyle(p.created)"></i>',
            '                <i :style="barStyle(p.resolved, true)"></i>',
            '              </div>',
            '            </div>',
            '            <span style="font-size:12px;color:var(--tk-text3)">{{ p.day }}</span>',
            '          </div>',
            '        </div>',
            '        <div style="display:flex;gap:14px;font-size:12px;color:var(--tk-text2)">',
            '          <span><i style="width:8px;height:8px;border-radius:3px;background:var(--tk-info);display:inline-block;margin-right:5px"></i>新增工单</span>',
            '          <span><i style="width:8px;height:8px;border-radius:3px;background:var(--tk-ok);display:inline-block;margin-right:5px"></i>已解决</span>',
            '        </div>',
            '      </div>',

            '      <div class="lcard" style="flex:1;min-height:0;gap:12px">',
            '        <div class="cap" style="font-size:17px">最新工单<span class="r"><span class="op" style="font-size:13px;color:var(--tk-info);cursor:pointer" @click="$router && null">查看全部</span></span></div>',
            '        <div class="tblw" style="flex:1;min-height:0;overflow:auto">',
            '          <table class="pk">',
            '            <colgroup><col><col style="width:100px"><col style="width:96px"><col style="width:150px"></colgroup>',
            '            <thead><tr><th>工单</th><th>状态</th><th>工单号</th><th>创建时间</th></tr></thead>',
            '            <tbody>',
            '              <tr v-if="!d.latestOrders.length"><td colspan="4" style="text-align:center;color:var(--tk-muted);padding:24px 0">暂无工单</td></tr>',
            '              <tr v-for="o in d.latestOrders" :key="o.orderNo">',
            '                <td style="color:var(--tk-text)">{{ o.title }}</td>',
            '                <td><span :style="{ fontSize: \'12px\', fontWeight: 500, color: toneColor(o.status) }">{{ statusText(o.status) }}</span></td>',
            '                <td style="color:var(--tk-muted)">{{ o.orderNo }}</td>',
            '                <td style="color:var(--tk-text3)">{{ fmtTime(o.createdAt) }}</td>',
            '              </tr>',
            '            </tbody>',
            '          </table>',
            '        </div>',
            '      </div>',
            '    </div>',

            '    <div style="width:380px;flex:none;display:flex;flex-direction:column;gap:20px;min-height:0">',
            '      <div class="lcard" style="gap:16px">',
            '        <div class="cap" style="font-size:17px">工单分类占比 · 本周</div>',
            '        <div style="display:flex;align-items:center;gap:20px">',
            '          <div :style="{ width: \'110px\', height: \'110px\', borderRadius: \'999px\', flex: \'none\', position: \'relative\', background: donutGradient }">',
            '            <div style="position:absolute;inset:22px;border-radius:999px;background:var(--tk-panel);display:grid;place-items:center">',
            '              <span style="text-align:center"><b style="font-size:20px;font-weight:650;color:var(--tk-text)">{{ d.weekNewWorkOrders }}</b><div style="font-size:9px;color:var(--tk-text3)">本周工单</div></span>',
            '            </div>',
            '          </div>',
            '          <div style="display:flex;flex-direction:column;gap:9px;flex:1">',
            '            <div v-for="(c, i) in d.categories" :key="c.name" style="display:flex;align-items:center;gap:9px;font-size:13px;color:var(--tk-text2)">',
            '              <i :style="{ width: \'8px\', height: \'8px\', borderRadius: \'2.5px\', background: CAT_COLORS[i], flex: \'none\' }"></i>{{ c.name }}',
            '              <b style="margin-left:auto;font-weight:600;color:var(--tk-text)">{{ c.percent }}%</b>',
            '            </div>',
            '            <div v-if="!d.categories.length" style="font-size:12px;color:var(--tk-muted)">本周暂无工单</div>',
            '          </div>',
            '        </div>',
            '      </div>',

            '      <div class="lcard" style="gap:4px">',
            '        <div class="cap" style="font-size:17px;margin-bottom:6px">心理预警动态<span class="r"><span class="op" style="font-size:13px;color:var(--tk-info);cursor:pointer" @click="notify(\'危机预警模块待后端实现\')">查看全部</span></span></div>',
            '        <div style="display:flex;align-items:center;gap:10px;padding:12px 0;border-top:1px solid var(--tk-row)"><span style="width:8px;height:8px;border-radius:50%;background:var(--tk-muted);flex:none"></span><span style="font-size:13px;color:var(--tk-muted);flex:1">预警模块待后端接入</span></div>',
            '      </div>',

            '      <div class="lcard" style="gap:4px">',
            '        <div class="cap" style="font-size:17px;margin-bottom:6px">系统状态</div>',
            '        <div style="display:flex;align-items:center;padding:12px 0;border-top:1px solid var(--tk-row)"><span style="font-size:13px;color:var(--tk-text2);flex:1">API 网关</span><span style="font-size:12px;font-weight:500;color:var(--tk-ok)">{{ apiOk ? "正常" : "检查中" }}</span></div>',
            '        <div style="display:flex;align-items:center;padding:12px 0;border-top:1px solid var(--tk-row)"><span style="font-size:13px;color:var(--tk-text2);flex:1">MySQL 主库</span><span style="font-size:12px;font-weight:500;color:var(--tk-ok)">{{ apiOk ? "正常" : "-" }}</span></div>',
            '        <div style="display:flex;align-items:center;padding:12px 0;border-top:1px solid var(--tk-row)"><span style="font-size:13px;color:var(--tk-text2);flex:1">Redis 缓存</span><span style="font-size:12px;font-weight:500;color:var(--tk-ok)">{{ apiOk ? "正常" : "-" }}</span></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            '  <div v-if="!d && !loading" class="lcard" style="align-items:center;justify-content:center;min-height:200px;color:var(--tk-muted)">加载失败，点击右上角刷新重试</div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var d = Vue.ref(null);
            var loading = Vue.ref(false);
            var apiOk = Vue.ref(true);
            var todayLabel = Vue.ref(new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }));

            function load() {
                loading.value = true;
                api.get('/api/admin/dashboard')
                    .then(function (data) { d.value = data; apiOk.value = true; })
                    .catch(function (error) { El.ElMessage.error(error.message); apiOk.value = false; })
                    .finally(function () { loading.value = false; });
            }

            function maxCreated() {
                if (!d.value) return 1;
                return Math.max.apply(null, d.value.trend.map(function (p) { return Math.max(p.created, p.resolved); })) || 1;
            }
            function barStyle(v, resolved) {
                var pct = Math.round(v / maxCreated() * 100);
                return {
                    display: 'block', width: '26px',
                    height: Math.max(pct, 2) + '%', minHeight: '3px',
                    background: resolved ? 'var(--tk-ok)' : 'var(--tk-info)',
                    borderRadius: '5px 5px 0 0'
                };
            }
            var donutGradient = Vue.computed(function () {
                if (!d.value || !d.value.categories.length) return 'var(--tk-muted)';
                var acc = 0;
                var stops = [];
                d.value.categories.forEach(function (c, i) {
                    var start = acc, end = acc + c.percent;
                    stops.push(CAT_COLORS[i] + ' ' + start + '% ' + end + '%');
                    acc = end;
                });
                if (acc < 100) stops.push('var(--tk-muted) ' + acc + '% 100%');
                return 'conic-gradient(' + stops.join(', ') + ')';
            });
            function statusText(s) { return STATUS_TEXT[s] || s || '-'; }
            function toneColor(s) {
                var tone = STATUS_TONE[s] || 'muted';
                return { bad: 'var(--tk-bad)', info: 'var(--tk-info)', ok: 'var(--tk-ok)', muted: 'var(--tk-muted)' }[tone];
            }
            function fmtTime(v) {
                if (!v) return '-';
                return String(v).replace('T', ' ').slice(5, 16);
            }
            function notify(msg) { El.ElMessage.info(msg); }

            load();

            return {
                d: d, loading: loading, apiOk: apiOk, todayLabel: todayLabel,
                load: load, barStyle: barStyle, donutGradient: donutGradient,
                statusText: statusText, toneColor: toneColor, fmtTime: fmtTime, notify: notify,
                CAT_COLORS: CAT_COLORS
            };
        }
    };
})();
