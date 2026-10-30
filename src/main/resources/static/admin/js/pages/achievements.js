/* 成就管理（管理端）：勋章/成就目录与解锁条件，满足条件自动发放（无需人工授予）。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    var METRICS = [
        { value: 'POST_COUNT', label: '发帖数' },
        { value: 'COMMENT_COUNT', label: '评论数' },
        { value: 'POST_LIKES', label: '帖子累计获赞' },
        { value: 'POST_LIKES_SINGLE', label: '单帖最高获赞' },
        { value: 'REQUEST_COUNT', label: '服务申请数' },
        { value: 'REQUEST_COMPLETED', label: '办结申请数' },
        { value: 'EVALUATION_COUNT', label: '评价数' },
        { value: 'EVALUATION_FIVE_STAR', label: '五星评价数' },
        { value: 'WORKER_ORDER_COMPLETED', label: '完成工单数' },
        { value: 'JW_AUTH', label: '教务认证' },
        { value: 'SCHEDULE_IMPORTED', label: '课表导入' },
        { value: 'PROFILE_COMPLETE', label: '资料完善' },
        { value: 'LEVEL', label: '等级' }
    ];
    var CATEGORIES = ['COMMUNITY', 'SERVICE', 'WORKER', 'JW', 'GROWTH'];
    var RARITIES = ['COMMON', 'RARE', 'EPIC', 'LEGENDARY'];

    window.AdminPages.AchievementsPage = {
        name: 'AchievementsPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="page-h"><div><h1>成就管理</h1><div class="sub">发布勋章与成就，设置解锁条件；用户满足条件后系统自动发放</div><div class="sub" style="margin-top:4px;color:var(--tk-muted)">新增勋章请上传素材（随勋章字典热更新到客户端缓存）；内置 19 枚随 App 包，通常无需上传，仅在确有替换需求时使用（需新版客户端生效）</div></div>',
            '    <div class="r"><button class="btn2" @click="reload">刷新</button><button class="btn2" style="margin-left:8px" @click="openGrant">手动发放</button><button class="c blue" style="margin-left:8px" @click="openCreate">新增勋章</button></div></div>',
            '  <div class="tabs"><span class="tab" :class="{ on: tab === \'badges\' }" @click="switchTab(\'badges\')">勋章目录</span><span class="tab" :class="{ on: tab === \'grants\' }" @click="switchTab(\'grants\')">发放记录</span></div>',
            '  <div v-if="tab === \'badges\'" class="lcard" style="display:flex;flex-direction:column;min-height:0;flex:1">',
            '    <div class="cap">勋章列表<span class="cnt" v-if="rows.length">共 {{ rows.length }} 枚</span></div>',
            '    <div style="display:flex;gap:8px;padding:0 16px 12px">',
            '      <div class="ipt2" style="width:160px"><select v-model="filterCategory" @change="load" style="width:100%"><option value="">全部类别</option><option v-for="c in categories" :key="c" :value="c">{{ c }}</option></select></div>',
            '      <div class="ipt2" style="width:140px"><select v-model="filterEnabled" @change="load" style="width:100%"><option value="">全部状态</option><option value="true">启用</option><option value="false">停用</option></select></div>',
            '    </div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto"><table class="pk">',
            '      <thead><tr><th style="width:150px">编码</th><th style="width:130px">名称</th><th style="width:110px">素材</th><th style="width:90px">类别</th><th style="width:90px">稀有度</th><th>解锁条件</th><th style="width:70px">奖励</th><th style="width:60px">排序</th><th style="width:70px">状态</th><th style="width:130px" class="r">操作</th></tr></thead>',
            '      <tbody>',
            '        <tr v-if="!rows.length && !loading"><td colspan="10" style="text-align:center;color:var(--tk-muted);padding:24px 0">暂无勋章</td></tr>',
            '        <tr v-for="b in rows" :key="b.id">',
            '          <td style="color:var(--tk-text3);font-family:ui-monospace,Menlo,monospace;font-size:12px">{{ b.badgeCode }}</td>',
            '          <td>{{ b.badgeName }}</td>',
            '          <td style="text-align:center">',
            '            <img v-if="b.iconUrl" :src="iconSrc(b)" alt="素材" style="width:32px;height:32px;object-fit:contain;border-radius:6px;background:var(--tk-subtle)" />',
            '            <div v-else style="color:var(--tk-muted);font-size:12px">未上传</div>',
            '            <div style="margin-top:2px"><span class="op" @click="pickIcon(b)">{{ iconUploadingId === b.id ? "上传中…" : (b.iconUrl ? "替换" : "上传") }}</span><template v-if="b.iconUrl"> · <span class="op" style="color:var(--tk-bad)" @click="clearIcon(b)">清除</span></template></div>',
            '          </td>',
            '          <td>{{ b.category }}</td><td>{{ b.rarity }}</td>',
            '          <td><div>{{ b.description }}</div><div style="color:var(--tk-muted);font-size:12px">{{ metricLabel(b.metricCode) }} ≥ {{ b.metricThreshold }}</div></td>',
            '          <td>{{ b.rewardExp }} 经验</td><td>{{ b.sortOrder }}</td>',
            '          <td><span class="bdg" :class="b.enabled ? \'g\' : \'a\'">{{ b.enabled ? "启用" : "停用" }}</span></td>',
            '          <td class="r"><span class="op" @click="openEdit(b)">编辑</span> · <span class="op" @click="toggle(b)">{{ b.enabled ? "停用" : "启用" }}</span> · <span class="op" style="color:var(--tk-bad)" @click="remove(b)">删除</span></td>',
            '        </tr>',
            '      </tbody></table></div>',
            '  </div>',
            // 发放记录（SYS09）
            '  <div v-if="tab === \'grants\'" class="lcard" style="display:flex;flex-direction:column;min-height:0;flex:1">',
            '    <div class="cap">发放记录<span class="cnt" v-if="grantTotal">共 {{ grantTotal }} 条</span>',
            '      <span style="margin-left:auto;display:flex;gap:8px;align-items:flex-end">',
            '        <div class="ipt2" style="width:170px"><input v-model="grantUserId" placeholder="按用户 ID 筛选" @keyup.enter="loadGrants(1)" /></div>',
            '        <div class="ipt2" style="width:200px"><select v-model="grantBadgeCode" style="width:100%" @change="loadGrants(1)"><option value="">全部勋章</option><option v-for="b in rows" :key="b.badgeCode" :value="b.badgeCode">{{ b.badgeName }}</option></select></div>',
            '        <button class="c" @click="loadGrants(1)">查询</button>',
            '      </span></div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto"><table class="pk">',
            '      <thead><tr><th style="width:80px">记录 ID</th><th style="width:200px">用户</th><th>勋章</th><th style="width:100px">进度</th><th style="width:160px">解锁时间</th><th style="width:100px">来源</th><th style="width:90px" class="r">操作</th></tr></thead>',
            '      <tbody>',
            '        <tr v-if="!grants.length && !grantLoading"><td colspan="7" style="text-align:center;color:var(--tk-muted);padding:48px 0">暂无发放记录</td></tr>',
            '        <tr v-for="g in grants" :key="g.id"><td style="color:var(--tk-muted)">#{{ g.id }}</td>',
            '          <td>{{ g.userName }}<span style="color:var(--tk-muted);font-size:12px"> #{{ g.userId }}</span></td>',
            '          <td>{{ g.badgeName }}<span style="color:var(--tk-muted);font-size:12px"> · {{ g.badgeCode }}</span></td>',
            '          <td>{{ g.currentProgress }}</td><td style="color:var(--tk-text3)">{{ fmt(g.unlockedAt) }}</td>',
            '          <td><span class="bdg" :class="g.grantSource === \'MANUAL\' ? \'a\' : \'g\'">{{ g.grantSource === "MANUAL" ? "手动发放" : "自动达成" }}</span></td>',
            '          <td class="r"><span class="op" style="color:var(--tk-bad)" @click="revokeGrant(g)">撤销</span></td></tr>',
            '      </tbody></table></div>',
            '    <div class="pfoot"><div class="pgs"><b class="edge" v-if="grantPage > 1" @click="loadGrants(grantPage - 1)">‹</b><b class="on">{{ grantPage }}</b><b class="edge" v-if="grants.length >= grantPageSize" @click="loadGrants(grantPage + 1)">›</b></div>',
            '      <div class="tot">共 {{ grantTotal }} 条</div></div>',
            '  </div>',

            // 手动发放弹层
            '  <div class="pmask" :class="{ show: grantVisible }" @click.self="grantVisible = false">',
            '    <div class="pcard">',
            '      <div class="ph"><b>手动发放勋章</b><span class="no">SYS09</span><span class="sp"></span><span class="x" @click="grantVisible = false">✕</span></div>',
            '      <div class="psum"><div class="l2">用于活动奖励、补偿等运营场景；已拥有的用户会被拒绝，撤销后若仍满足自动条件会重新发放。</div></div>',
            '      <div class="fld"><label>用户 ID <em>*</em></label><div class="pin"><input v-model.number="grantForm.userId" placeholder="例如 1002" /></div></div>',
            '      <div class="fld"><label>勋章 <em>*</em></label><div class="pin"><select v-model="grantForm.badgeCode" style="width:100%"><option value="">请选择</option><option v-for="b in rows.filter(function (x) { return x.enabled; })" :key="b.badgeCode" :value="b.badgeCode">{{ b.badgeName }}（{{ b.badgeCode }}）</option></select></div></div>',
            '      <div class="fld"><label>备注</label><div class="pin"><input v-model="grantForm.note" maxlength="255" placeholder="写入审计日志，便于追溯" /></div></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="grantVisible = false">取消</button><button class="c blue" :disabled="grantSubmitting" @click="submitGrant">{{ grantSubmitting ? "提交中…" : "确认发放" }}</button></div>',
            '    </div>',
            '  </div>',

            '  <div class="pmask" :class="{ show: visible }" @click.self="visible = false">',
            '    <div class="pcard" v-if="form">',
            '      <div class="ph"><b>{{ form.id ? "编辑勋章" : "新增勋章" }}</b><span class="sp"></span><span class="x" @click="visible = false">✕</span></div>',
            '      <div class="fld"><label>勋章编码 <em>*</em></label><div class="pin"><input v-model="form.badgeCode" :disabled="!!form.id" maxlength="64" placeholder="如 COMMUNITY_POST_10" /></div></div>',
            '      <div class="fld"><label>勋章名称 <em>*</em></label><div class="pin"><input v-model="form.badgeName" maxlength="64" /></div></div>',
            '      <div style="display:flex;gap:12px"><div class="fld" style="flex:1"><label>类别</label><div class="pin"><select v-model="form.category" style="width:100%"><option v-for="c in categories" :key="c" :value="c">{{ c }}</option></select></div></div>',
            '        <div class="fld" style="flex:1"><label>稀有度</label><div class="pin"><select v-model="form.rarity" style="width:100%"><option v-for="r in rarities" :key="r" :value="r">{{ r }}</option></select></div></div></div>',
            '      <div class="fld"><label>解锁说明 <em>*</em></label><div class="pin"><input v-model="form.description" maxlength="255" placeholder="如：累计发布 10 篇帖子" /></div></div>',
            '      <div style="display:flex;gap:12px"><div class="fld" style="flex:1"><label>解锁指标</label><div class="pin"><select v-model="form.metricCode" style="width:100%"><option v-for="m in metrics" :key="m.value" :value="m.value">{{ m.label }}</option></select></div></div>',
            '        <div class="fld" style="flex:1"><label>阈值</label><div class="pin"><input type="number" v-model.number="form.metricThreshold" min="1" /></div></div></div>',
            '      <div style="display:flex;gap:12px"><div class="fld" style="flex:1"><label>奖励经验</label><div class="pin"><input type="number" v-model.number="form.rewardExp" min="0" /></div></div>',
            '        <div class="fld" style="flex:1"><label>排序</label><div class="pin"><input type="number" v-model.number="form.sortOrder" /></div></div>',
            '        <div class="fld" style="flex:1"><label>状态</label><div class="pin"><select v-model="form.enabled" style="width:100%"><option :value="true">启用</option><option :value="false">停用</option></select></div></div></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="visible = false">取消</button><button class="c blue" :disabled="submitting" @click="submit">{{ submitting ? "提交中…" : "保存" }}</button></div>',
            '    </div>',
            '  </div>',
            '  <input id="achievement-icon-file" type="file" accept=".png,.jpg,.jpeg,.webp" style="display:none" @change="onIconFileChange" />',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var rows = Vue.ref([]);
            var loading = Vue.ref(false);
            var filterCategory = Vue.ref('');
            var filterEnabled = Vue.ref('');
            var visible = Vue.ref(false);
            var form = Vue.ref(null);
            var submitting = Vue.ref(false);
            var iconUploadingId = Vue.ref(null);
            var iconTarget = null;
            var iconBust = Date.now();
            var tab = Vue.ref('badges');
            var grants = Vue.ref([]);
            var grantTotal = Vue.ref(0);
            var grantPage = Vue.ref(1);
            var grantPageSize = 20;
            var grantLoading = Vue.ref(false);
            var grantUserId = Vue.ref('');
            var grantBadgeCode = Vue.ref('');
            var grantVisible = Vue.ref(false);
            var grantSubmitting = Vue.ref(false);
            var grantForm = Vue.reactive({ userId: null, badgeCode: '', note: '' });

            function switchTab(value) {
                tab.value = value;
                if (value === 'grants') loadGrants(1);
            }
            function reload() {
                load();
                if (tab.value === 'grants') loadGrants(grantPage.value);
            }
            function loadGrants(page) {
                grantPage.value = page || 1;
                grantLoading.value = true;
                var params = ['pageNum=' + grantPage.value, 'pageSize=' + grantPageSize];
                if (grantUserId.value) params.push('userId=' + grantUserId.value);
                if (grantBadgeCode.value) params.push('badgeCode=' + encodeURIComponent(grantBadgeCode.value));
                api.get('/api/admin/achievements/grants?' + params.join('&'))
                    .then(function (data) {
                        grants.value = (data && data.records) || [];
                        grantTotal.value = (data && data.total) || 0;
                    })
                    .catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { grantLoading.value = false; });
            }
            function openGrant() {
                grantForm.userId = null;
                grantForm.badgeCode = '';
                grantForm.note = '';
                grantVisible.value = true;
            }
            function submitGrant() {
                if (!grantForm.userId || !grantForm.badgeCode) {
                    El.ElMessage.warning('请填写用户 ID 并选择勋章');
                    return;
                }
                grantSubmitting.value = true;
                api.post('/api/admin/achievements/grants', {
                    userId: grantForm.userId,
                    badgeCode: grantForm.badgeCode,
                    note: grantForm.note || null
                }).then(function () {
                    El.ElMessage.success('勋章已发放');
                    grantVisible.value = false;
                    tab.value = 'grants';
                    loadGrants(1);
                }).catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { grantSubmitting.value = false; });
            }
            function revokeGrant(g) {
                El.ElMessageBox.confirm('确认撤销「' + g.badgeName + '」的发放记录？用户将失去该勋章。',
                    '撤销发放', { type: 'warning', confirmButtonText: '撤销', cancelButtonText: '取消' })
                    .then(function () { return api.delete('/api/admin/achievements/grants/' + g.id); })
                    .then(function () {
                        El.ElMessage.success('已撤销');
                        loadGrants(grantPage.value);
                    })
                    .catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }
            function fmt(v) {
                if (!v) return '-';
                return String(v).replace('T', ' ').slice(0, 16);
            }

            function load() {
                loading.value = true;
                iconBust = Date.now();
                var params = [];
                if (filterCategory.value) params.push('category=' + filterCategory.value);
                if (filterEnabled.value) params.push('enabled=' + filterEnabled.value);
                api.get('/api/admin/achievements/badges' + (params.length ? '?' + params.join('&') : ''))
                    .then(function (data) { rows.value = data || []; })
                    .catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { loading.value = false; });
            }
            function iconSrc(b) {
                if (!b.iconUrl) return '';
                var sep = b.iconUrl.indexOf('?') >= 0 ? '&' : '?';
                return b.iconUrl + sep + 't=' + iconBust;
            }
            function pickIcon(b) {
                iconTarget = b;
                var input = document.getElementById('achievement-icon-file');
                if (input) { input.value = ''; input.click(); }
            }
            function onIconFileChange(event) {
                var file = event.target && event.target.files && event.target.files[0];
                if (!file || !iconTarget) return;
                var name = (file.name || '').toLowerCase();
                if (!/\.(png|jpe?g|webp)$/.test(name)) {
                    El.ElMessage.warning('素材仅支持 PNG/JPEG/WebP');
                    return;
                }
                if (file.size > 512 * 1024) {
                    El.ElMessage.warning('素材不能超过 512KB');
                    return;
                }
                var target = iconTarget;
                iconUploadingId.value = target.id;
                api.upload('/api/admin/achievements/badges/' + target.id + '/icon', file)
                    .then(function () { El.ElMessage.success('素材已上传，勋章字典版本已更新'); load(); })
                    .catch(function (e) { El.ElMessage.error(e.message || '素材上传失败'); })
                    .finally(function () { iconUploadingId.value = null; iconTarget = null; });
            }
            function clearIcon(b) {
                El.ElMessageBox.confirm('确认清除「' + b.badgeName + '」的素材？清除后客户端回退内置素材或占位图。', '清除素材', {
                    type: 'warning', confirmButtonText: '清除', cancelButtonText: '取消'
                }).then(function () { return api.delete('/api/admin/achievements/badges/' + b.id + '/icon'); })
                    .then(function () { El.ElMessage.success('已清除'); load(); })
                    .catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }
            function openCreate() {
                form.value = { id: null, badgeCode: '', badgeName: '', category: 'COMMUNITY', rarity: 'COMMON',
                    description: '', metricCode: 'POST_COUNT', metricThreshold: 1, rewardExp: 0, sortOrder: 0, enabled: true };
                visible.value = true;
            }
            function openEdit(b) {
                form.value = { id: b.id, badgeCode: b.badgeCode, badgeName: b.badgeName, category: b.category,
                    rarity: b.rarity, description: b.description, metricCode: b.metricCode,
                    metricThreshold: b.metricThreshold, rewardExp: b.rewardExp, sortOrder: b.sortOrder, enabled: b.enabled };
                visible.value = true;
            }
            function submit() {
                submitting.value = true;
                var f = form.value;
                var payload = { badgeCode: f.badgeCode, badgeName: f.badgeName, category: f.category,
                    rarity: f.rarity, description: f.description, metricCode: f.metricCode,
                    metricThreshold: f.metricThreshold, rewardExp: f.rewardExp, sortOrder: f.sortOrder, enabled: f.enabled };
                var promise = f.id ? api.put('/api/admin/achievements/badges/' + f.id, payload)
                    : api.post('/api/admin/achievements/badges', payload);
                promise.then(function () { El.ElMessage.success('已保存'); visible.value = false; load(); })
                    .catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { submitting.value = false; });
            }
            function toggle(b) {
                api.post('/api/admin/achievements/badges/' + b.id + '/status?enabled=' + (!b.enabled))
                    .then(function () { El.ElMessage.success('已更新'); load(); })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }
            function remove(b) {
                El.ElMessageBox.confirm('确认删除勋章「' + b.badgeName + '」？已获得的记录会保留，但不再展示与评估。', '删除勋章', {
                    type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
                }).then(function () { return api.delete('/api/admin/achievements/badges/' + b.id); })
                    .then(function () { El.ElMessage.success('已删除'); load(); })
                    .catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }
            function metricLabel(code) {
                var found = METRICS.filter(function (m) { return m.value === code; })[0];
                return found ? found.label : code;
            }

            load();

            return {
                rows: rows, loading: loading, filterCategory: filterCategory, filterEnabled: filterEnabled,
                visible: visible, form: form, submitting: submitting,
                categories: CATEGORIES, rarities: RARITIES, metrics: METRICS,
                load: load, openCreate: openCreate, openEdit: openEdit, submit: submit,
                tab: tab, switchTab: switchTab, reload: reload,
                grants: grants, grantTotal: grantTotal, grantPage: grantPage, grantPageSize: grantPageSize,
                grantLoading: grantLoading, grantUserId: grantUserId, grantBadgeCode: grantBadgeCode,
                grantVisible: grantVisible, grantSubmitting: grantSubmitting, grantForm: grantForm,
                loadGrants: loadGrants, openGrant: openGrant, submitGrant: submitGrant, revokeGrant: revokeGrant,
                fmt: fmt,
                toggle: toggle, remove: remove, metricLabel: metricLabel,
                iconUploadingId: iconUploadingId, iconSrc: iconSrc, pickIcon: pickIcon,
                onIconFileChange: onIconFileChange, clearIcon: clearIcon
            };
        }
    };
})();
