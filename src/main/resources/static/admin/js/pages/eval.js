/* 服务评价（管理端）：真实接口 /api/admin/evaluations。
   评分页签（含差评跟进）+ 标签展示 + 分页 + 回复弹层 + 导出 CSV，画布规范。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    var TABS = [
        { value: '', label: '全部' },
        { value: 'good', label: '好评 · 4-5星', ratings: [4, 5] },
        { value: 'mid', label: '中评 · 3星', ratings: [3] },
        { value: 'bad', label: '差评 · 1-2星', ratings: [1, 2] },
        { value: 'badPending', label: '差评待跟进', ratings: [1, 2], replied: false },
        { value: 'pending', label: '待回复', replied: false },
        { value: 'replied', label: '已回复', replied: true }
    ];

    function currentTab(value) {
        for (var i = 0; i < TABS.length; i++) {
            if (TABS[i].value === value) return TABS[i];
        }
        return {};
    }

    window.AdminPages.EvalPage = {
        name: 'EvalPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="page-h">',
            '    <div><h1>服务评价</h1><div class="sub">评价统一管理 · 差评跟进 · 导出归档</div></div>',
            '    <div class="r"><button class="btn2" @click="exportCsv">导出 CSV</button><button class="btn2" @click="load">刷新</button></div>',
            '  </div>',

            '  <div class="tabs">',
            '    <span v-for="t in tabs" :key="t.value" class="tab" :class="{ on: tabValue === t.value }" @click="switchTab(t)">{{ t.label }}</span>',
            '  </div>',

            '  <div class="lcard" style="flex:1;min-height:0">',
            '    <div class="cap">评价列表<span class="cnt" v-if="Number(total) > 0">共 {{ total }} 条</span>',
            '      <span class="r" style="font-size:11px;color:var(--tk-text3)" v-if="tabValue === \'badPending\'">差评（1-2 星）且尚未回复，需优先跟进</span>',
            '    </div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto">',
            '      <table class="pk">',
            '        <colgroup><col style="width:110px"><col style="width:100px"><col style="width:130px"><col style="width:120px"><col style="width:120px"><col><col style="width:100px"><col style="width:100px"><col style="width:130px"></colgroup>',
            '        <thead><tr><th>评价ID</th><th>评分</th><th>标签</th><th>关联申请</th><th>评价时间</th><th>评价内容</th><th>处理人</th><th>回复状态</th><th class="r">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-if="!rows.length && !loading"><td colspan="9" style="text-align:center;color:var(--tk-muted);padding:32px 0">暂无评价</td></tr>',
            '          <tr v-if="loading"><td colspan="9" style="text-align:center;color:var(--tk-muted);padding:32px 0">加载中…</td></tr>',
            '          <tr v-for="row in rows" :key="row.evaluationId">',
            '            <td style="color:var(--tk-muted)">EV-{{ row.evaluationId }}</td>',
            '            <td><span :style="{ color: ratingColor(row.rating), letterSpacing: \'1px\' }">{{ stars(row.rating) }}</span></td>',
            '            <td><span v-if="row.tags" class="bdg n2">{{ row.tags }}</span><span v-else style="color:var(--tk-muted)">-</span></td>',
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
            '        <div class="l2" v-if="target.tags">标签：{{ target.tags }}</div>',
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

            var FETCH_PAGE_SIZE = 50;
            var MAX_FETCH_PAGES = 20;

            function fetchAllPages(rating, replied) {
                var results = [];
                var page = 1;
                function step() {
                    var params = 'pageSize=' + FETCH_PAGE_SIZE + '&pageNum=' + page;
                    if (rating) params += '&rating=' + rating;
                    if (replied !== undefined) params += '&replied=' + replied;
                    return api.get('/api/admin/evaluations?' + params).then(function (res) {
                        results = results.concat(res.records || []);
                        if (page < MAX_FETCH_PAGES && page * FETCH_PAGE_SIZE < (res.total || 0)) {
                            page++;
                            return step();
                        }
                        return results;
                    });
                }
                return step();
            }

            function loadForTab(tab, pageNo, size) {
                var ratings = tab.ratings || [];
                var replied = tab.replied;
                if (ratings.length <= 1) {
                    var rating = ratings.length ? ratings[0] : null;
                    var params = 'pageNum=' + pageNo + '&pageSize=' + size;
                    if (rating) params += '&rating=' + rating;
                    if (replied !== undefined) params += '&replied=' + replied;
                    return api.get('/api/admin/evaluations?' + params).then(function (res) {
                        return { rows: res.records || [], total: res.total || 0, clientPaged: false };
                    });
                }
                // 多个评分档位（好评、差评）：后端仅支持单 rating，逐档全量拉取后按时间排序前端分页
                return Promise.all(ratings.map(function (rating) {
                    return fetchAllPages(rating, replied);
                })).then(function (groups) {
                    var merged = [];
                    groups.forEach(function (group) { merged = merged.concat(group); });
                    merged.sort(function (a, b) {
                        return String(b.createdAt || '').localeCompare(String(a.createdAt || ''));
                    });
                    var start = (pageNo - 1) * size;
                    return {
                        rows: merged.slice(start, start + size),
                        total: merged.length,
                        clientPaged: true
                    };
                });
            }

            function load() {
                loading.value = true;
                loadForTab(currentTab(tabValue.value), pageNum.value, pageSize.value)
                    .then(function (result) {
                        rows.value = result.rows;
                        total.value = result.total;
                        if (result.clientPaged) {
                            var totalPages = Math.max(1, Math.ceil(result.total / pageSize.value));
                            if (pageNum.value > totalPages) {
                                pageNum.value = totalPages;
                                load();
                            }
                        }
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
                var text = (row.comment || '未填写文字评价')
                    + (row.tags ? '\n标签：' + row.tags : '')
                    + (row.reply ? '\n\n商家回复：' + row.reply : '');
                El.ElMessageBox.alert(text, '评价详情 EV-' + row.evaluationId,
                    { confirmButtonText: '关闭' });
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

            /** 导出当前页签全部评价为 CSV（含标签/回复），用于差评跟进归档。 */
            function exportCsv() {
                var tab = currentTab(tabValue.value);
                var ratings = tab.ratings || [];
                var replied = tab.replied;
                var promise = ratings.length <= 1
                    ? fetchAllPages(ratings.length ? ratings[0] : null, replied)
                    : Promise.all(ratings.map(function (rating) { return fetchAllPages(rating, replied); }))
                        .then(function (groups) {
                            var merged = [];
                            groups.forEach(function (group) { merged = merged.concat(group); });
                            return merged;
                        });
                promise.then(function (list) {
                    list.sort(function (a, b) {
                        return String(b.createdAt || '').localeCompare(String(a.createdAt || ''));
                    });
                    var lines = [['评价ID', '评分', '标签', '关联申请', '处理人', '评价内容', '回复内容', '评价时间']];
                    list.forEach(function (row) {
                        lines.push([
                            row.evaluationId, row.rating, row.tags || '', row.requestId,
                            row.assigneeName || '', row.comment || '', row.reply || '',
                            row.createdAt || ''
                        ]);
                    });
                    var csv = lines.map(function (line) {
                        return line.map(function (cell) {
                            return '"' + String(cell == null ? '' : cell).replace(/"/g, '""') + '"';
                        }).join(',');
                    }).join('\r\n');
                    var blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' });
                    var downloadUrl = URL.createObjectURL(blob);
                    var link = document.createElement('a');
                    link.href = downloadUrl;
                    link.download = '服务评价-' + new Date().toISOString().slice(0, 10) + '.csv';
                    document.body.appendChild(link);
                    link.click();
                    document.body.removeChild(link);
                    URL.revokeObjectURL(downloadUrl);
                    El.ElMessage.success('已导出 ' + list.length + ' 条评价');
                }).catch(function (error) { El.ElMessage.error(error.message); });
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
                load: load, reload: reload,
                replyVisible: replyVisible, target: target, replyText: replyText, submitting: submitting,
                openReply: openReply, openDetail: openDetail, submit: submit, exportCsv: exportCsv,
                stars: stars, ratingColor: ratingColor, fmt: fmt
            };
        }
    };
})();
