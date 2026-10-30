/* 服务 SLA 看板 A02 + 节假日配置 S07：
   - GET /api/admin/sla/overview?startDate&endDate：按服务项/组织聚合承诺时长、超时率、平均办结、满意度、返工/驳回；
   - GET/POST/DELETE /api/admin/sla/holidays：节假日与调休维护（SLA 截止推算使用）；
   - 截止时间以 service_item.sla_hours + 工作日历推算；工单 due_at 由工单模块按承诺时限写入。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    function pad(n) { return n < 10 ? '0' + n : '' + n; }
    function dateText(d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
    function fmtTime(v) { return v ? String(v).replace('T', ' ').slice(0, 16) : '—'; }

    window.AdminPages.SlaPage = {
        name: 'SlaPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>服务 SLA</h1><div class="sub">承诺时长 · 超时率 · 满意度 · 返工/驳回</div></div>',
            '    <div class="r" style="flex-wrap:wrap;gap:8px">',
            '      <input class="os-input" type="date" style="width:144px" v-model="startDate" title="开始日期" />',
            '      <span style="color:var(--tk-muted);font-size:11px">至</span>',
            '      <input class="os-input" type="date" style="width:144px" v-model="endDate" title="结束日期" />',
            '      <button class="btn2 pri" @click="load">查询</button>',
            '      <button class="btn2" :disabled="!d" @click="exportCsv">导出 CSV</button>',
            '    </div></div>',

            '  <div v-if="loading" class="os-panel"><div class="os-empty">正在计算 SLA 指标…</div></div>',
            '  <div v-else-if="error" class="os-panel"><div class="os-empty">{{ error }} <button class="os-btn sm" style="margin-left:8px" @click="load">重试</button></div></div>',

            '  <template v-else-if="d">',
            '    <div class="os-grid" style="grid-template-columns:repeat(5,1fr)">',
            '      <div class="os-kpi"><div class="k-label"><span class="dot"></span>服务申请</div><div class="k-val">{{ d.summary.requests }}<small>件</small></div><div class="k-foot"><span>办结 {{ d.summary.completed }} · 撤销 {{ d.summary.cancelled }}</span></div></div>',
            '      <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-bad)"></span>超时率</div><div class="k-val" :style="{ color: d.summary.overdueRatePercent > 10 ? \'var(--tk-bad)\' : \'var(--tk-text)\' }">{{ d.summary.overdueRatePercent }}<small>%</small></div><div class="k-foot"><span>超时 {{ d.summary.overdue }} / 有承诺 {{ d.summary.withSla }}</span></div></div>',
            '      <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-info)"></span>平均办结</div><div class="k-val">{{ d.summary.avgResolveHours == null ? "—" : d.summary.avgResolveHours }}<small v-if="d.summary.avgResolveHours != null">小时</small></div><div class="k-foot"><span>按已办结申请计算</span></div></div>',
            '      <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>满意度</div><div class="k-val">{{ d.summary.avgSatisfaction == null ? "—" : d.summary.avgSatisfaction }}<small v-if="d.summary.avgSatisfaction != null">分</small></div><div class="k-foot"><span>服务评价平均星级（1-5）</span></div></div>',
            '      <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>返工 / 驳回</div><div class="k-val">{{ d.summary.reworkRatePercent }}<small>%</small></div><div class="k-foot"><span>返工 {{ d.summary.rework }} · 驳回 {{ d.summary.rejected }}</span></div></div>',
            '    </div>',

            '    <div class="os-note"><b>截止时间口径</b> {{ d.dueAtBasis }} 当前日历：工作日 {{ d.holidays.workdayStartHour }}:00-{{ d.holidays.workdayEndHour }}:00，{{ d.holidays.weekendWorkday ? "周末计工作日" : "周末不计工作日" }}；区间内节假日 {{ d.holidays.holidayDays }} 天、调休上班 {{ d.holidays.extraWorkdays }} 天。</div>',

            '    <div class="os-panel">',
            '      <div class="os-panel-h"><h3>按服务项</h3><span class="hint">超时率 = 超时 / 有承诺时限的申请</span></div>',
            '      <div style="overflow:auto;max-height:360px">',
            '        <table style="width:100%;border-collapse:collapse;table-layout:fixed"><thead><tr><th style="width:26%">服务项</th><th style="width:88px">承诺(h)</th><th style="width:76px">申请</th><th style="width:76px">办结</th><th style="width:76px">超时</th><th style="width:90px">超时率</th><th style="width:100px">平均办结(h)</th><th style="width:90px">满意度</th><th style="width:76px">返工</th></tr></thead><tbody>',
            '          <tr v-for="r in d.byService" :key="r.itemName"><td><b style="font-weight:550;color:var(--tk-text)">{{ r.itemName }}</b></td><td class="os-mono">{{ r.slaHours == null ? "—" : r.slaHours }}</td><td class="os-mono">{{ r.requests }}</td><td class="os-mono">{{ r.completed }}</td><td class="os-mono" :style="{ color: r.overdue > 0 ? \'var(--tk-bad)\' : \'var(--tk-text2)\' }">{{ r.overdue }}</td><td class="os-mono">{{ r.overdueRatePercent }}%</td><td class="os-mono">{{ r.avgResolveHours == null ? "—" : r.avgResolveHours }}</td><td class="os-mono">{{ r.avgSatisfaction == null ? "—" : r.avgSatisfaction }}</td><td class="os-mono">{{ r.rework }}</td></tr>',
            '          <tr v-if="!d.byService.length"><td colspan="9" class="os-empty">区间内没有服务申请</td></tr>',
            '        </tbody></table>',
            '      </div>',
            '    </div>',

            '    <div style="display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:16px">',
            '      <div class="os-panel">',
            '        <div class="os-panel-h"><h3>按受理组织</h3><span class="hint">组织维度</span></div>',
            '        <div style="overflow:auto;max-height:300px">',
            '          <table style="width:100%;border-collapse:collapse;table-layout:fixed"><thead><tr><th>组织</th><th style="width:70px">申请</th><th style="width:70px">办结</th><th style="width:70px">超时</th><th style="width:82px">超时率</th><th style="width:96px">平均办结(h)</th></tr></thead><tbody>',
            '            <tr v-for="r in d.byOrganization" :key="r.organizationName"><td style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ r.organizationName }}</td><td class="os-mono">{{ r.requests }}</td><td class="os-mono">{{ r.completed }}</td><td class="os-mono" :style="{ color: r.overdue > 0 ? \'var(--tk-bad)\' : \'var(--tk-text2)\' }">{{ r.overdue }}</td><td class="os-mono">{{ r.overdueRatePercent }}%</td><td class="os-mono">{{ r.avgResolveHours == null ? "—" : r.avgResolveHours }}</td></tr>',
            '            <tr v-if="!d.byOrganization.length"><td colspan="6" class="os-empty">暂无数据</td></tr>',
            '          </tbody></table>',
            '        </div>',
            '      </div>',
            '      <div class="os-panel">',
            '        <div class="os-panel-h"><h3>超时 / 慢单 Top</h3><span class="hint">超时优先，其次办结耗时</span></div>',
            '        <div style="overflow:auto;max-height:300px">',
            '          <table style="width:100%;border-collapse:collapse;table-layout:fixed"><thead><tr><th style="width:26%">申请</th><th style="width:24%">服务项</th><th style="width:18%">组织</th><th style="width:80px">状态</th><th style="width:96px">截止时间</th><th style="width:76px">办结(h)</th></tr></thead><tbody>',
            '            <tr v-for="o in d.slowOrders" :key="o.requestId"><td class="os-mono" style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="o.requestNo">{{ o.requestNo }}</td><td style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ o.itemName }}</td><td style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ o.organizationName }}</td><td><span class="os-tag" :class="o.overdue ? \'bad\' : \'warn\'">{{ o.overdue ? "超时" : "慢单" }}</span></td><td style="font-size:10px;color:var(--tk-text3)">{{ fmtTime(o.deadlineAt) }}</td><td class="os-mono">{{ o.resolveHours == null ? "—" : o.resolveHours }}</td></tr>',
            '            <tr v-if="!d.slowOrders.length"><td colspan="6" class="os-empty">没有超时或慢单</td></tr>',
            '          </tbody></table>',
            '        </div>',
            '      </div>',
            '    </div>',

            '    <div class="os-panel">',
            '      <div class="os-panel-h"><h3>节假日与工作日历</h3><span class="hint">{{ holidayYear }} 年 · 当前校区</span><div class="r"><button v-if="canEdit" class="os-btn sm primary" @click="openHoliday()">新增</button><button class="os-btn sm" @click="loadHolidays">刷新</button></div></div>',
            '      <div class="os-note" style="margin:12px 18px 0"><b>规则</b> 节假日（工作日=否）在 SLA 截止推算中顺延；调休上班（工作日=是）按工作日处理。写操作会写入审计日志。{{ canEdit ? "" : "当前账号只读，维护需 system:config:manage 权限。" }}</div>',
            '      <div style="overflow:auto;max-height:300px">',
            '        <table style="width:100%;border-collapse:collapse"><thead><tr><th style="width:130px">日期</th><th>名称</th><th style="width:110px">是否工作日</th><th style="width:120px">校区</th><th>备注</th><th v-if="canEdit" style="width:120px;text-align:right">操作</th></tr></thead><tbody>',
            '          <tr v-for="h in holidays" :key="h.id"><td class="os-mono">{{ h.holidayDate }}</td><td>{{ h.holidayName }}</td><td><span class="os-tag" :class="h.workday ? \'ok\' : \'warn\'">{{ h.workday ? "调休上班" : "节假日" }}</span></td><td class="os-mono">{{ h.campusId === 0 ? "平台级" : h.campusId }}</td><td style="font-size:11px;color:var(--tk-text3)">{{ h.remark || "—" }}</td><td v-if="canEdit" style="text-align:right;white-space:nowrap"><span style="font-size:11px;color:var(--tk-info);cursor:pointer" @click="openHoliday(h)">编辑</span><span style="font-size:11px;color:var(--tk-bad);cursor:pointer;margin-left:9px" @click="removeHoliday(h)">删除</span></td></tr>',
            '          <tr v-if="!holidays.length"><td :colspan="canEdit ? 6 : 5" class="os-empty">尚未配置节假日；未配置时仅按周一至周五计算工作日</td></tr>',
            '        </tbody></table>',
            '      </div>',
            '    </div>',
            '  </template>',

            '  <div v-if="holidayOpen" class="cmd-mask" style="padding-top:12vh" @click.self="holidayOpen = false">',
            '    <div class="cmd-panel" style="width:480px">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">{{ holidayForm.id ? "编辑节假日" : "新增节假日" }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="holidayOpen = false">✕</span></div>',
            '      <div style="padding:18px 20px;display:flex;flex-direction:column;gap:13px">',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>日期 <em>*</em></label><input class="os-input" type="date" v-model="holidayForm.holidayDate" /></div>',
            '          <div class="os-field"><label>类型</label><select class="os-select" v-model="holidayForm.isWorkday"><option :value="false">节假日（顺延）</option><option :value="true">调休上班</option></select></div>',
            '        </div>',
            '        <div class="os-field"><label>名称 <em>*</em></label><input class="os-input" v-model.trim="holidayForm.holidayName" maxlength="64" placeholder="如：国庆节 / 调休上班" /></div>',
            '        <div class="os-field"><label>备注</label><textarea class="os-textarea" v-model.trim="holidayForm.remark" maxlength="255" placeholder="选填"></textarea></div>',
            '        <div style="display:flex;justify-content:flex-end;gap:9px"><button class="os-btn" @click="holidayOpen = false">取消</button><button class="os-btn primary" :disabled="holidaySaving" @click="saveHoliday">{{ holidaySaving ? "保存中…" : "保存" }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var now = new Date();
            var end = dateText(now);
            var startObj = new Date(now.getTime() - 29 * 86400000);
            var d = Vue.ref(null);
            var startDate = Vue.ref(dateText(startObj));
            var endDate = Vue.ref(end);
            var loading = Vue.ref(false);
            var error = Vue.ref('');
            var holidays = Vue.ref([]);
            var holidayYear = Vue.ref(now.getFullYear());
            var holidayOpen = Vue.ref(false);
            var holidaySaving = Vue.ref(false);
            var holidayForm = Vue.reactive({ id: null, holidayDate: '', holidayName: '', isWorkday: false, remark: '' });

            function permissions() {
                var user = (window.AdminApp && window.AdminApp.state && window.AdminApp.state.user) || {};
                var perms = (user.permissions || []).slice();
                var roles = user.roles || [];
                if (roles.indexOf('PLATFORM_ADMIN') >= 0 && perms.indexOf('platform:all') < 0) perms.push('platform:all');
                return perms;
            }
            var canEdit = Vue.computed(function () {
                var perms = permissions();
                return perms.indexOf('platform:all') >= 0 || perms.indexOf('system:config:manage') >= 0;
            });

            function load() {
                loading.value = true;
                error.value = '';
                api.get('/api/admin/sla/overview?startDate=' + startDate.value + '&endDate=' + endDate.value)
                    .then(function (data) { d.value = data; })
                    .catch(function (err) {
                        d.value = null;
                        error.value = (err && err.message) || 'SLA 数据加载失败';
                        El.ElMessage.error(error.value);
                    })
                    .finally(function () { loading.value = false; });
            }
            function loadHolidays() {
                api.get('/api/admin/sla/holidays?year=' + holidayYear.value)
                    .then(function (data) { holidays.value = data || []; })
                    .catch(function (err) { El.ElMessage.error((err && err.message) || '节假日加载失败'); });
            }
            function openHoliday(row) {
                if (!canEdit.value) return;
                if (row) {
                    holidayForm.id = row.id;
                    holidayForm.holidayDate = row.holidayDate;
                    holidayForm.holidayName = row.holidayName;
                    holidayForm.isWorkday = !!row.workday;
                    holidayForm.remark = row.remark || '';
                } else {
                    holidayForm.id = null;
                    holidayForm.holidayDate = '';
                    holidayForm.holidayName = '';
                    holidayForm.isWorkday = false;
                    holidayForm.remark = '';
                }
                holidayOpen.value = true;
            }
            function saveHoliday() {
                if (!holidayForm.holidayDate) { El.ElMessage.warning('请选择日期'); return; }
                if (!holidayForm.holidayName) { El.ElMessage.warning('请填写名称'); return; }
                var payload = {
                    holidayDate: holidayForm.holidayDate,
                    holidayName: holidayForm.holidayName,
                    isWorkday: !!holidayForm.isWorkday,
                    remark: holidayForm.remark || null
                };
                holidaySaving.value = true;
                var req = holidayForm.id
                    ? api.post('/api/admin/sla/holidays/' + holidayForm.id, payload)
                    : api.post('/api/admin/sla/holidays', payload);
                req.then(function () {
                    El.ElMessage.success(holidayForm.id ? '节假日已更新' : '节假日已新增');
                    holidayOpen.value = false;
                    loadHolidays();
                    load();
                }).catch(function (err) { El.ElMessage.error((err && err.message) || '保存失败'); })
                    .finally(function () { holidaySaving.value = false; });
            }
            function removeHoliday(row) {
                if (!canEdit.value) return;
                El.ElMessageBox.confirm('删除后 SLA 截止推算不再按「' + row.holidayName + '」顺延，确定删除？',
                    '删除节假日', { type: 'warning' })
                    .then(function () { return api.delete('/api/admin/sla/holidays/' + row.id); })
                    .then(function () {
                        El.ElMessage.success('已删除');
                        loadHolidays();
                        load();
                    })
                    .catch(function (err) {
                        if (err !== 'cancel' && err !== 'close') El.ElMessage.error((err && err.message) || '删除失败');
                    });
            }
            function exportCsv() {
                if (!d.value) return;
                var lines = [];
                lines.push('"SLA 看板","' + d.value.rangeStart + ' ~ ' + d.value.rangeEnd + '"');
                lines.push('"口径","' + String(d.value.dueAtBasis).replace(/"/g, '""') + '"');
                lines.push('');
                lines.push('"汇总","数值"');
                var s = d.value.summary;
                [['服务申请', s.requests], ['已办结', s.completed], ['有承诺时限', s.withSla], ['超时', s.overdue],
                    ['超时率%', s.overdueRatePercent], ['平均办结(h)', s.avgResolveHours == null ? '' : s.avgResolveHours],
                    ['满意度', s.avgSatisfaction == null ? '' : s.avgSatisfaction], ['返工', s.rework],
                    ['返工率%', s.reworkRatePercent], ['驳回', s.rejected], ['撤销', s.cancelled]]
                    .forEach(function (row) { lines.push('"' + row[0] + '","' + row[1] + '"'); });
                lines.push('');
                lines.push('"服务项","承诺h","申请","办结","超时","超时率%","平均办结h","满意度","返工"');
                d.value.byService.forEach(function (r) {
                    lines.push(['"' + r.itemName + '"', r.slaHours == null ? '' : r.slaHours, r.requests, r.completed,
                        r.overdue, r.overdueRatePercent, r.avgResolveHours == null ? '' : r.avgResolveHours,
                        r.avgSatisfaction == null ? '' : r.avgSatisfaction, r.rework].join(','));
                });
                lines.push('');
                lines.push('"组织","申请","办结","超时","超时率%","平均办结h"');
                d.value.byOrganization.forEach(function (r) {
                    lines.push(['"' + r.organizationName + '"', r.requests, r.completed, r.overdue,
                        r.overdueRatePercent, r.avgResolveHours == null ? '' : r.avgResolveHours].join(','));
                });
                var blob = new Blob(['\ufeff' + lines.join('\n')], { type: 'text/csv;charset=utf-8' });
                var a = document.createElement('a');
                a.href = URL.createObjectURL(blob);
                a.download = 'sla-' + d.value.rangeStart + '-' + d.value.rangeEnd + '.csv';
                a.click();
                URL.revokeObjectURL(a.href);
            }

            load();
            loadHolidays();
            return {
                d: d, startDate: startDate, endDate: endDate, loading: loading, error: error,
                holidays: holidays, holidayYear: holidayYear, canEdit: canEdit, holidayOpen: holidayOpen,
                holidaySaving: holidaySaving, holidayForm: holidayForm,
                load: load, loadHolidays: loadHolidays, openHoliday: openHoliday, saveHoliday: saveHoliday,
                removeHoliday: removeHoliday, exportCsv: exportCsv, fmtTime: fmtTime
            };
        }
    };
})();
