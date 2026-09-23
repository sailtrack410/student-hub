/* 服务评价（管理端）：真实接口 /api/admin/evaluations。
   评分页签 + 分页 + 回复弹层（一条评价仅可回复一次），画布规范。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    var TABS = [
        { value: '', label: '全部' },
        { value: 'good', label: '好评 · 4-5星' },
        { value: 'mid', label: '中评 · 3星' },
        { value: 'bad', label: '差评 · 1-2星' },
        { value: 'pending', label: '待回复', replied: false },
        { value: 'replied', label: '已回复', replied: true }
    ];
    var RATING_MAP = { good: [4, 5], mid: [3], bad: [1, 2] };

    window.AdminPages.EvalPage = {
        name: 'EvalPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="page-h">',
            '    <div><h1>服务评价</h1><div class="sub">服务完成后的师生评价统一管理与回复</div></div>',
            '    <div class="r"><button class="btn2" @click="load">刷新</button></div>',
            '  </div>',

            '  <div class="tabs">',
            '    <span v-for="t in tabs" :key="t.value" class="tab" :class="{ on: tabValue === t.value }" @click="switchTab(t)">{{ t.label }}</span>',
            '  </div>',

            '  <div class="lcard" style="flex:1;min-height:0">',
            '    <div class="cap">评价列表<span class="cnt" v-if="total">共 {{ total }} 条</span></div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto">',
            '      <table class="pk">',
            '        <colgroup><col style="width:120px"><col style="width:110px"><col style="width:110px"><col style="width:130px"><col><col style="width:120px"><col style="width:110px"><col></colgroup>',
            '        <thead><tr><th>评价ID</th><th>评分</th><th>关联申请</th><th>评价时间</th><th>评价内容</th><th>处理人</th><th>回复状态</th><th class="r">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-if="!rows.length && !loading"><td colspan="8" style="text-align:center;color:var(--tk-muted);padding:32px 0">暂无评价</td></tr>',
            '          <tr v-if="loading"><td colspan="8" style="text-align:center;color:var(--tk-muted);padding:32px 0">加载中…</td></tr>',
            '          <tr v-for="row in rows" :key="row.evaluationId">',
            '            <td style="color:var(--tk-muted)">EV-{{ row.evaluationId }}</td>',
            '            <td><span :style="{ color: ratingColor(row.rating), letterSpacing: \'1px\' }">{{ stars(row.rating) }}</span></td>',
            '            <td style="color:var(--tk-text3)">REQ-{{ row.requestId }}</td>',
            '            <td style="color:var(--tk-text3)">{{ fmt(row.createdAt) }}</td>',
            '            <td style="color:var(--tk-text)">{{ row.comment || "-" }}</td>',
            '            <td>{{ row.assigneeName || "-" }}</td>',
            '            <td><span class="bdg" :class="row.reply ? \'g\' : \'a\'">{{ row.reply ? "已回复" : "待回复" }}</span></td>',
            '            <td class="r">',
            '              <span class="op" @click="openDetail(row)">详情</span>',
            '              <template v-if="!row.reply"> · <span class="op" @click="openReply(row)">回复</span></template>',
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

            // 弹层: 回复
            '  <div class="pmask" :class="{ show: replyVisible }" @click.self="replyVisible = false">',
            '    <div class="pcard" v-if="target">',
            '      <div class="ph"><b>回复评价</b><span class="no">EV-{{ target.evaluationId }}</span><span class="sp"></span><span class="x" @click="replyVisible = false">✕</span></div>',
            '      <div class="psum">',
            '        <div class="l1">「{{ target.comment || "未填写文字评价" }}」 · {{ stars(target.rating) }}</div>',
            '        <div class="l2">关联申请 REQ-{{ target.requestId }} · 处理人 {{ target.assigneeName || "-" }} · {{ fmt(target.createdAt) }}</div>',
            '      </div>',
            '      <div class="fld"><label>回复内容 <em>*</em></label><textarea class="pta" v-model="replyText" maxlength="512" placeholder="回复将展示在评价下方，并站内通知评价人"></textarea></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="replyVisible = false">取消</button>',
            '        <button class="c blue" :disabled="submitting" @click="submit">{{ submitting ? "提交中…" : "提交回复" }}</button></div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var tabValue = Vue.ref('');
            var rows = Vue.ref([]);
            var total = Vue.ref(0);
            var pageNum = Vue.ref(1);
            var pageSize = Vue.ref(10);
            var loading = Vue.ref(false);

            var replyVisible = Vue.ref(false);
            var target = Vue.ref(null);
            var replyText = Vue.ref('');
            var submitting = Vue.ref(false);

            function queryOf() {
                var t = TABS.filter(function (x) { return x.value === tabValue.value; })[0] || {};
                var params = ['pageNum=' + pageNum.value, 'pageSize=' + pageSize.value];
                if (t.replied !== undefined) params.push('replied=' + t.replied);
                if (RATING_MAP[t.value]) params.push('rating=' + RATING_MAP[t.value][0]);
                return params.join('&');
            }
            // 多评分档位（好评4-5、差评1-2）一次查询没法带两个 rating，退化为前端过滤
            function applyRatingFilter(list) {
                var t = TABS.filter(function (x) { return x.value === tabValue.value; })[0] || {};
                var map = RATING_MAP[t.value];
                if (!map) return list;
                return list.filter(function (r) { return map.indexOf(r.rating) >= 0; });
            }

            function load() {
                loading.value = true;
                api.get('/api/admin/evaluations?' + queryOf())
                    .then(function (page) {
                        rows.value = applyRatingFilter(page.records || []);
                        total.value = page.total || 0;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { loading.value = false; });
            }
            function reload() { pageNum.value = 1; load(); }
            function switchTab(t) { tabValue.value = t.value; reload(); }

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

            function openReply(row) {
                target.value = row;
                replyText.value = '';
                replyVisible.value = true;
            }
            function openDetail(row) {
                El.ElMessageBox.alert(
                    (row.comment || '未填写文字评价') + (row.reply ? '<br><br><b>商家回复：</b>' + row.reply : ''),
                    '评价详情 EV-' + row.evaluationId,
                    { dangerouslyUseHTMLString: false, confirmButtonText: '关闭' });
            }
            function submit() {
                if (!replyText.value.trim()) {
                    El.ElMessage.warning('请填写回复内容');
                    return;
                }
                submitting.value = true;
                api.post('/api/admin/evaluations/' + target.value.evaluationId + '/reply',
                        { reply: replyText.value.trim() })
                    .then(function () {
                        El.ElMessage.success('已回复并通知评价人');
                        replyVisible.value = false;
                        load();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { submitting.value = false; });
            }

            function stars(n) {
                var s = '';
                for (var i = 1; i <= 5; i++) s += i <= n ? '★' : '☆';
                return s;
            }
            function ratingColor(n) {
                return n >= 4 ? 'var(--tk-warn)' : n === 3 ? 'var(--tk-warn)' : 'var(--tk-bad)';
            }
            function fmt(v) {
                if (!v) return '-';
                return String(v).replace('T', ' ').slice(0, 16);
            }

            load();

            return {
                tabs: TABS, tabValue: tabValue, rows: rows, total: total, pageNum: pageNum, pageSize: pageSize,
                loading: loading, totalPages: totalPages, pageNumbers: pageNumbers, goPage: goPage, switchTab: switchTab,
                replyVisible: replyVisible, target: target, replyText: replyText, submitting: submitting,
                openReply: openReply, openDetail: openDetail, submit: submit,
                stars: stars, ratingColor: ratingColor, fmt: fmt
            };
        }
    };
})();
