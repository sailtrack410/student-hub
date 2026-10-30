/* 社区运营（管理端）：概览/帖子/申诉/分区/话题/屏蔽词 六个页签，真实接口 /api/admin/community/**。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    var TABS = [
        { value: 'overview', label: '社区概览' },
        { value: 'posts', label: '帖子管理' },
        { value: 'appeals', label: '申诉复核' },
        { value: 'sections', label: '分区管理' },
        { value: 'topics', label: '话题管理' },
        { value: 'words', label: '屏蔽词' }
    ];
    var STATUS_MAP = { ENABLED: '正常', HIDDEN: '已隐藏', DELETED: '已删除' };
    var APPEAL_STATUS = { PENDING: '待处理', APPROVED: '已通过', REJECTED: '已驳回' };

    function createCommunityPage(pageName, initialTab, pageTitle, pageSub) {
        return {
            name: pageName,
        template: [
            '<div class="pgw comm" :class="\'tab-\' + tab" style="display:flex;flex-direction:column;gap:10px;flex:1;min-height:0">',
            '  <div class="page-h"><div><h1>{{ pageTitle }}</h1><div class="sub">{{ pageSub }}</div></div>',
            '    <div class="r"><button class="btn2" @click="reload">刷新</button></div></div>',

            // 概览：满宽 6 KPI + 趋势/处置 + 分区/健康，四块数据面
            '  <div v-if="tab === \'overview\'" class="cv">',
            '    <div class="cv-toolbar">',
            '      <div class="cv-scope"><span class="live"></span><b>社区运营</b><span class="sep">/</span><span>当前校区</span><span class="sep">/</span><span>近 {{ days }} 天</span></div>',
            '      <div class="cv-meta">',
            '        <span style="display:inline-flex;border:1px solid var(--tk-stroke);border-radius:4px;overflow:hidden">',
            '          <button type="button" style="height:22px;padding:0 9px;border:0;font-size:11px;cursor:pointer" :style="days === 7 ? \'background:var(--tk-brand);color:#fff\' : \'background:var(--tk-panel);color:var(--tk-text2)\'" @click="setDays(7)">近 7 天</button>',
            '          <button type="button" style="height:22px;padding:0 9px;border:0;border-left:1px solid var(--tk-stroke);font-size:11px;cursor:pointer" :style="days === 30 ? \'background:var(--tk-brand);color:#fff\' : \'background:var(--tk-panel);color:var(--tk-text2)\'" @click="setDays(30)">近 30 天</button>',
            '        </span>',
            '        <span>更新 {{ refreshAt || \'—\' }}</span>',
            '        <button class="cv-refresh" :disabled="statsLoading" @click="loadStats">{{ statsLoading ? \'加载中…\' : \'刷新数据\' }}</button>',
            '        <button class="cv-refresh" :disabled="statsLoading" @click="exportStats">导出 CSV</button>',
            '      </div>',
            '    </div>',
            '    <div v-if="statsError && !stats" class="cv-loading" style="flex:none;display:flex;gap:10px;align-items:center;justify-content:center">指标加载失败：{{ statsError }}<button class="cv-refresh" @click="loadStats">重试</button></div>',
            '    <div class="cv-kpis" v-if="stats">',
            '      <div class="cv-kpi"><div class="k-top"><span>帖子总数</span><em>+{{ stats.todayPosts }} 今日</em></div><div class="k-num">{{ stats.totalPosts }}<small>条</small></div><div class="k-sub">评论 {{ stats.totalComments }} · 近 {{ days }} 天活跃 {{ stats.activeUsers7d }}</div></div>',
            '      <div class="cv-kpi"><div class="k-top"><span>评论总数</span><em>+{{ stats.todayComments }} 今日</em></div><div class="k-num">{{ stats.totalComments }}<small>条</small></div><div class="k-sub">帖均 {{ avgComments() }} 条 · 今日互动 {{ (+stats.todayPosts) + (+stats.todayComments) }}</div></div>',
            '      <div class="cv-kpi"><div class="k-top"><span>{{ days }} 日活跃用户</span><em>去重</em></div><div class="k-num blue">{{ stats.activeUsers7d }}<small>人</small></div><div class="k-sub">发帖 / 评论去重口径 · 含今日 · 非留存率</div></div>',
            '      <div class="cv-kpi link" @click="go(\'community\')"><div class="k-top"><span>待处理举报</span><em class="warn" v-if="+stats.pendingReports">需处置</em><em v-else>已清空</em></div><div class="k-num" :class="{ warn: +stats.pendingReports }">{{ stats.pendingReports }}<small>条</small></div><div class="k-sub">用户举报内容 · 点击进入处置</div></div>',
            '      <div class="cv-kpi link" @click="go(\'community-appeals\')"><div class="k-top"><span>待处理申诉</span><em class="warn" v-if="+stats.pendingAppeals">需复核</em><em v-else>已清空</em></div><div class="k-num" :class="{ warn: +stats.pendingAppeals }">{{ stats.pendingAppeals }}<small>条</small></div><div class="k-sub">帖子 / 评论 / 处罚申诉</div></div>',
            '      <div class="cv-kpi"><div class="k-top"><span>隐藏内容</span><em :class="{ warn: hiddenTotal() > 0 }">健康 {{ healthScore() }}%</em></div><div class="k-num" :class="{ bad: hiddenTotal() > 0 }">{{ hiddenTotal() }}<small>条</small></div><div class="k-sub">帖 {{ stats.hiddenPosts }} · 评 {{ stats.hiddenComments }} · 不计入自然流</div></div>',
            '    </div>',
            '    <div class="cv-kpis" v-else><div class="cv-kpi" v-for="n in 6" :key="n"><div class="k-top"><span>加载中</span></div><div class="k-num">—</div><div class="k-sub">正在读取社区指标</div></div></div>',

            '    <div class="cv-main">',
            '      <section class="cv-panel cv-trend">',
            '        <div class="cv-panel-h"><h3>发帖趋势</h3><span class="hint">近 {{ days }} 天</span><div class="r"><span class="big">{{ rangePosts() }}</span><span class="delta">区间累计</span></div></div>',
            '        <div class="cv-chart" v-if="stats">',
            '          <div class="cv-y"><span>{{ maxTrend() }}</span><span>{{ Math.ceil(maxTrend() / 2) }}</span><span>0</span></div>',
            '          <div class="cv-bars" :style="{ gridTemplateColumns: \'repeat(\' + (stats.trend.length || 7) + \',minmax(0,1fr))\', gap: days === 30 ? \'2px\' : \'8px\' }">',
            '            <div class="cv-guide" v-for="n in 3" :key="n" :style="{ top: (n * 25) + \'%\' }"></div>',
            '            <div class="cv-bar-col" v-for="(d, i) in stats.trend" :key="d.date">',
            '              <div class="cv-bar-track"><div class="cv-bar" :style="{ height: barHeight(d.count) + \'%\' }"><span class="v" v-if="days <= 7 || d.count > 0">{{ d.count }}</span></div></div>',
            '              <div class="cv-bar-date">{{ barDate(d.date, i) }}</div>',
            '            </div>',
            '          </div>',
            '        </div>',
            '        <div class="cv-loading" v-else>加载趋势数据…</div>',
            '        <div class="cv-chart-foot" v-if="stats"><span>峰值 <b>{{ weekPeak() }}</b></span><span>日均 <b>{{ weekAvg() }}</b></span><span>今日 <b>{{ stats.todayPosts }}</b></span><span style="margin-left:auto">共 {{ stats.trend.length }} 天 · 按发帖数统计 · {{ stats.rangeStart }} ~ {{ stats.rangeEnd }}</span></div>',
            '      </section>',
            '      <section class="cv-panel cv-todo">',
            '        <div class="cv-panel-h"><h3>处置队列</h3><span class="cv-badge" v-if="pendingTotal()">{{ pendingTotal() }} 待处理</span><span class="cv-badge" v-else style="background:var(--tk-ok-bg);color:var(--tk-ok)">已清空</span><div class="r"><span class="hint">按优先级</span></div></div>',
            '        <div class="cv-todo-list">',
            '          <div class="cv-todo-row" @click="go(\'community\')"><span class="dot warn"></span><div class="tx"><b>举报待处置</b><small>涉事内容需人工判定，超时会影响社区健康</small></div><div class="num warn">{{ stats ? stats.pendingReports : "—" }}</div><span class="arrow">→</span></div>',
            '          <div class="cv-todo-row" @click="go(\'community-appeals\')"><span class="dot"></span><div class="tx"><b>申诉待复核</b><small>帖子 / 评论 / 处罚申诉，逐条给出结论</small></div><div class="num">{{ stats ? stats.pendingAppeals : "—" }}</div><span class="arrow">→</span></div>',
            '          <div class="cv-todo-row" @click="go(\'community-posts\')"><span class="dot"></span><div class="tx"><b>内容巡检</b><small>查看最新帖子与隐藏内容，处理异常内容</small></div><div class="num">{{ stats ? stats.totalPosts : "—" }}</div><span class="arrow">→</span></div>',
            '        </div>',
            '        <div class="cv-todo-actions"><button class="pri" @click="go(\'community\')">处置举报</button><button @click="go(\'community-appeals\')">复核申诉</button><button @click="go(\'community-posts\')">帖子管理</button></div>',
            '      </section>',
            '    </div>',

            '    <div class="cv-secondary" v-if="stats" :style="{ gridTemplateColumns: \'minmax(0,1.6fr) minmax(0,1.1fr) minmax(220px,.9fr)\' }">',
            '      <section class="cv-panel cv-sections">',
            '        <div class="cv-panel-h"><h3>分区结构</h3><span class="hint">帖子量 / 占比（近 {{ days }} 天）</span><div class="r"><span class="hint">{{ stats ? stats.sections.length : 0 }} 个分区</span><span style="font-size:10px;color:var(--tk-brand);cursor:pointer" @click="go(\'community-sections\')">管理分区 →</span></div></div>',
            '        <table class="cv-section-table" v-if="stats">',
            '          <thead><tr><th>分区</th><th class="num">帖子</th><th class="pct">占比</th><th class="mini">分布</th></tr></thead>',
            '          <tbody><tr v-for="s in stats.sections" :key="s.sectionId">',
            '            <td class="nm">{{ s.sectionName }}</td><td class="num">{{ s.count }}</td><td class="pct">{{ sectionShare(s) }}%</td>',
            '            <td class="mini"><div><i :style="{ width: barWidth(s.count, maxSection()) + \'%\' }"></i></div></td>',
            '          </tr><tr v-if="!stats.sections.length"><td colspan="4" style="text-align:center;color:var(--tk-muted);padding:26px">暂无分区数据</td></tr></tbody>',
            '        </table>',
            '      </section>',
            '      <section class="cv-panel cv-topics">',
            '        <div class="cv-panel-h"><h3>话题标签</h3><span class="hint">区间 Top N · 近 {{ days }} 天</span><div class="r"><span class="hint">{{ (stats.topics || []).length }} 个</span><span style="font-size:10px;color:var(--tk-brand);cursor:pointer" @click="go(\'community-topics\')">管理话题 →</span></div></div>',
            '        <div style="flex:1;overflow:auto;min-height:0" v-if="stats.topics && stats.topics.length">',
            '          <table class="cv-section-table">',
            '            <thead><tr><th>话题</th><th class="num">帖子</th><th class="pct">占比</th><th class="mini">分布</th></tr></thead>',
            '            <tbody><tr v-for="t in stats.topics" :key="t.topic"><td class="nm"># {{ t.topic }}</td><td class="num">{{ t.count }}</td><td class="pct">{{ topicShare(t) }}%</td>',
            '              <td class="mini"><div><i :style="{ width: barWidth(t.count, maxTopic()) + \'%\' }"></i></div></td></tr></tbody>',
            '          </table>',
            '        </div>',
            '        <div v-else class="cv-loading" style="min-height:120px">区间内暂无带话题标签的帖子</div>',
            '        <div class="cv-health-note">话题按帖子 topics 标签统计（区间内正常展示帖子）；活跃用户为发帖/评论去重口径，不是留存/回访率。</div>',
            '      </section>',
            '      <section class="cv-panel cv-health">',
            '        <div class="cv-panel-h"><h3>内容健康</h3><span class="hint">隐藏内容占比估算</span></div>',
            '        <div class="cv-health-top"><span class="score">{{ healthScore() }}<small>%</small></span><span class="tag">{{ hiddenTotal() ? \'需关注\' : \'健康\' }}</span></div>',
            '        <div class="cv-health-meter"><i :style="{ width: healthScore() + \'%\' }"></i></div>',
            '        <div class="cv-health-rows">',
            '          <div class="cv-health-row">隐藏帖子 <b>{{ stats ? stats.hiddenPosts : "—" }}<small> 条</small></b></div>',
            '          <div class="cv-health-row">隐藏评论 <b>{{ stats ? stats.hiddenComments : "—" }}<small> 条</small></b></div>',
            '          <div class="cv-health-row">待处置合计 <b>{{ pendingTotal() }}<small> 条</small></b></div>',
            '        </div>',
            '        <div class="cv-health-note">健康度按「1 − 隐藏内容 / 总内容」估算，仅作运营参考，不代表审核结论。</div>',
            '      </section>',
            '    </div>',
            '  </div>',

            // 帖子
            '  <div v-if="tab === \'posts\'" style="display:flex;flex-direction:column;gap:12px;flex:1;min-height:0">',
            '    <div class="panel" style="display:flex;flex-direction:column;min-height:0;flex:1">',
            '    <div class="cap">帖子列表<span class="cnt" v-if="postTotal">共 {{ postTotal }} 条</span>',
            '      <span style="margin-left:auto;display:flex;gap:8px;align-items:center">',
            '        <template v-if="postSelected.length">',
            '          <span style="font-size:12px;color:var(--tk-muted)">已选 {{ postSelected.length }} 条</span>',
            '          <button class="c" style="color:var(--tk-bad);border-color:var(--tk-bad)" @click="batchPost(\'HIDE\')">批量下架</button>',
            '          <button class="c" @click="batchPost(\'RESTORE\')">批量恢复</button>',
            '          <button class="c ghost" @click="postSelected = []">取消选择</button>',
            '        </template>',
            '      </span></div>',
            '    <div style="display:flex;gap:8px;flex-wrap:wrap">',
            '      <div class="ipt2" style="width:220px"><input v-model="postKeyword" placeholder="标题/正文搜索" @keyup.enter="loadPosts" /></div>',
            '      <div class="ipt2" style="width:160px"><select v-model="postSectionId" @change="loadPosts" style="width:100%"><option :value="null">全部分区</option><option v-for="s in sections" :key="s.sectionId" :value="s.sectionId">{{ s.sectionName }}</option></select></div>',
            '      <div class="ipt2" style="width:130px"><select v-model="postStatus" @change="loadPosts" style="width:100%"><option value="">全部状态</option><option value="ENABLED">正常</option><option value="HIDDEN">已隐藏</option><option value="DELETED">已删除</option></select></div>',
            '      <div class="ipt2" style="width:130px"><select v-model="postType" @change="loadPosts" style="width:100%"><option value="">全部类型</option><option value="DYNAMIC">动态</option><option value="ARTICLE">文章</option></select></div>',
            '      <button class="c blue" @click="loadPosts">搜索</button>',
            '    </div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto"><table class="pk">',
            '      <thead><tr><th style="width:34px"><input type="checkbox" :checked="postAllSelected" @change="toggleAllPosts($event)" /></th><th style="width:70px">封面</th><th>标题 / 摘要</th><th style="width:110px">作者</th><th style="width:100px">分区</th><th style="width:70px">类型</th><th style="width:80px">状态</th><th style="width:70px">举报</th><th style="width:140px" class="r">操作</th></tr></thead>',
            '      <tbody>',
            '        <tr v-if="!posts.length && !loadingPosts"><td colspan="9" style="text-align:center;color:var(--tk-muted);padding:64px 0">暂无帖子</td></tr>',
            '        <tr v-for="p in posts" :key="p.postId">',
            '          <td><input type="checkbox" :value="p.postId" :checked="postSelected.indexOf(p.postId) >= 0" @change="togglePost(p, $event)" /></td>',
            '          <td><img v-if="p.coverUrl" :src="p.coverUrl" title="点击预览封面" style="width:56px;height:56px;object-fit:cover;border-radius:6px;cursor:zoom-in" @click="previewImage = p.coverUrl" /><span v-else style="color:var(--tk-muted)">-</span></td>',
            '          <td><div style="font-weight:600"><span v-if="p.pinned" class="bdg r" style="margin-right:6px">置顶</span>{{ p.title }}</div><div style="color:var(--tk-muted);font-size:12px">{{ p.summary }}</div></td>',
            '          <td>{{ p.authorName }}</td><td>{{ p.sectionName || "-" }}</td>',
            '          <td>{{ p.postType === "ARTICLE" ? "文章" : "动态" }}</td>',
            '          <td><span class="bdg" :class="p.status === \'ENABLED\' ? \'g\' : (p.status === \'HIDDEN\' ? \'a\' : \'r\')">{{ statusText(p.status) }}</span></td>',
            '          <td>{{ p.reportCount }}</td>',
            '          <td class="r"><span class="op" @click="openPost(p)">详情</span>',
            '            <template v-if="p.status === \'ENABLED\'"> · <span class="op" style="color:var(--tk-bad)" @click="hidePost(p)">下架</span>',
            '              · <span class="op" :style="p.pinned ? \'color:var(--tk-warn)\' : \'color:var(--tk-info)\'" @click="togglePin(p)">{{ p.pinned ? "取消置顶" : "置顶" }}</span>',
            '              · <span class="op" style="color:var(--tk-text2,#334155)" @click="openReply(p)">官方回复</span></template>',
            '            <template v-if="p.status === \'HIDDEN\'"> · <span class="op" style="color:var(--tk-ok)" @click="restorePost(p)">恢复</span></template>',
            '          </td>',
            '        </tr>',
            '      </tbody></table></div>',
            '    </div>',
            '    <div class="pfoot"><div class="pgs"><b class="edge" v-if="postPage > 1" @click="loadPosts(postPage - 1)">‹</b><b v-for="p in postPageNumbers" :key="p" :class="{ on: p === postPage }" @click="loadPosts(p)">{{ p }}</b><b class="edge" v-if="postPage < postTotalPages" @click="loadPosts(postPage + 1)">›</b></div><div class="tot"><span>每页 <select v-model.number="postPageSize" @change="loadPosts(1)" style="border:0;background:none;color:inherit;font:inherit;outline:none"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</span><span>共 {{ postTotal }} 条</span></div></div>',
            '  </div>',

            // 申诉
            '  <div v-if="tab === \'appeals\'" style="display:flex;flex-direction:column;gap:12px;flex:1;min-height:0">',
            '    <div class="panel" style="display:flex;flex-direction:column;min-height:0;flex:1">',
            '    <div class="cap">申诉列表<span class="cnt" v-if="appealTotal">共 {{ appealTotal }} 条</span>',
            '      <span style="margin-left:auto;display:flex;gap:8px;align-items:center">',
            '        <template v-if="appealSelected.length">',
            '          <span style="font-size:12px;color:var(--tk-muted)">已选 {{ appealSelected.length }} 条</span>',
            '          <button class="c blue" @click="batchAppeal(\'APPROVE\')">批量通过</button>',
            '          <button class="c" @click="batchAppeal(\'REJECT\')">批量驳回</button>',
            '          <button class="c ghost" @click="appealSelected = []">取消选择</button>',
            '        </template>',
            '      </span></div>',
            '    <div class="tabs"><span v-for="t in appealTabs" :key="t.value" class="tab" :class="{ on: appealStatus === t.value }" @click="switchAppealStatus(t.value)">{{ t.label }}</span>',
            '      <div class="ipt2" style="width:150px;margin-left:auto"><select v-model="appealType" @change="loadAppeals(1)" style="width:100%"><option value="">全部对象</option><option value="POST">帖子</option><option value="COMMENT">评论</option><option value="SANCTION">处罚</option></select></div>',
            '    </div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto"><table class="pk">',
            '      <thead><tr><th style="width:34px"><input type="checkbox" :checked="appealAllSelected" @change="toggleAllAppeals($event)" /></th><th style="width:60px">ID</th><th style="width:110px">用户</th><th style="width:70px">对象</th><th>申诉对象</th><th style="width:90px">原因</th><th style="width:80px">状态</th><th style="width:150px">提交时间 / 时限</th><th style="width:120px" class="r">操作</th></tr></thead>',
            '      <tbody>',
            '        <tr v-if="!appeals.length && !loadingAppeals"><td colspan="9" style="text-align:center;color:var(--tk-muted);padding:64px 0">暂无申诉</td></tr>',
            '        <tr v-for="a in appeals" :key="a.appealId">',
            '          <td><input type="checkbox" :value="a.appealId" :checked="appealSelected.indexOf(a.appealId) >= 0" @change="toggleAppeal(a, $event)" /></td>',
            '          <td style="color:var(--tk-muted)">AP-{{ a.appealId }}</td><td>{{ a.userName }}</td>',
            '          <td>{{ a.targetType === "POST" ? "帖子" : (a.targetType === "COMMENT" ? "评论" : "处罚") }}</td>',
            '          <td><div>{{ a.targetTitle }}</div><div style="color:var(--tk-muted);font-size:12px">{{ a.reason }}</div></td>',
            '          <td>{{ a.reasonCode || "-" }}</td>',
            '          <td><span class="bdg" :class="a.status === \'PENDING\' ? \'a\' : (a.status === \'APPROVED\' ? \'g\' : \'r\')">{{ appealText(a.status) }}</span></td>',
            '          <td style="color:var(--tk-text3)">{{ fmt(a.createdAt) }}',
            '            <div v-if="a.status === \'PENDING\'" class="bdg" :class="slaTone(a)" style="margin-top:3px">{{ slaText(a) }}</div></td>',
            '          <td class="r"><template v-if="a.status === \'PENDING\'"><span class="op" @click="openAppeal(a)">处理</span></template><template v-else><span style="color:var(--tk-muted)">{{ a.handleNote || "-" }}</span></template></td>',
            '        </tr>',
            '      </tbody></table></div>',
            '    </div>',
            '    <div class="pfoot"><div class="pgs"><b class="edge" v-if="appealPage > 1" @click="loadAppeals(appealPage - 1)">‹</b><b v-for="p in appealPageNumbers" :key="p" :class="{ on: p === appealPage }" @click="loadAppeals(p)">{{ p }}</b><b class="edge" v-if="appealPage < appealTotalPages" @click="loadAppeals(appealPage + 1)">›</b></div><div class="tot">共 {{ appealTotal }} 条</div></div>',
            '  </div>',

            // 分区
            '  <div v-if="tab === \'sections\'" class="panel" style="display:flex;flex-direction:column;min-height:0;flex:1">',
            '    <div class="cap">分区管理<span class="cnt" v-if="sections.length">共 {{ sections.length }} 个</span>',
            '      <span style="margin-left:auto;display:flex;gap:8px;align-items:center">',
            '        <span style="font-size:12px;color:var(--tk-muted)">拖拽行首手柄或使用 ↑↓ 调整顺序，改完点「保存排序」</span>',
            '        <button class="c" :disabled="!sectionOrderDirty || sectionSaving" @click="saveSectionOrder">{{ sectionSaving ? "保存中…" : "保存排序" }}</button>',
            '        <button class="c blue" @click="openSection()">新增分区</button>',
            '      </span></div>',
            '    <div style="display:flex;gap:8px"><div class="ipt2" style="width:240px"><input v-model="sectionKeyword" placeholder="搜索编码/名称" /></div></div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto"><table class="pk"><thead><tr><th style="width:70px">排序</th><th>编码</th><th>名称</th><th>简介</th><th style="width:80px">推荐权重</th><th style="width:70px">状态</th><th style="width:70px">帖子数</th><th style="width:110px" class="r">操作</th></tr></thead>',
            '      <tbody><tr v-if="!filteredSections.length"><td colspan="8" style="text-align:center;color:var(--tk-muted);padding:64px 0">暂无分区</td></tr>',
            '        <tr v-for="(s, index) in filteredSections" :key="s.sectionId" draggable="true" @dragstart="dragStart(index)" @dragover.prevent @drop="dropAt(index)" style="cursor:move">',
            '          <td style="white-space:nowrap"><span style="color:var(--tk-muted);cursor:grab">⠿</span> {{ index + 1 }}',
            '            <span class="op" style="margin-left:6px" @click.stop="moveSection(index, -1)">↑</span>',
            '            <span class="op" @click.stop="moveSection(index, 1)">↓</span></td>',
            '          <td style="color:var(--tk-text3)">{{ s.sectionCode }}</td><td>{{ s.sectionName }}</td><td style="color:var(--tk-muted);font-size:12px">{{ s.description || "-" }}</td>',
            '          <td>{{ s.recommendWeight }}</td>',
            '          <td><span class="bdg" :class="s.status === \'ENABLED\' ? \'g\' : \'a\'">{{ s.status === "ENABLED" ? "启用" : "停用" }}</span></td><td>{{ s.postCount }}</td>',
            '          <td class="r"><span class="op" @click="openSection(s)">编辑</span> · <span class="op" @click="toggleSection(s)">{{ s.status === "ENABLED" ? "停用" : "启用" }}</span> · <span class="op" style="color:var(--tk-bad)" @click="removeSection(s)">删除</span></td></tr></tbody></table></div>',
            '  </div>',

            // 话题
            '  <div v-if="tab === \'topics\'" class="panel" style="display:flex;flex-direction:column;min-height:0;flex:1">',
            '    <div class="cap">话题管理<span class="cnt" v-if="topics.length">共 {{ topics.length }} 个</span><button class="c blue" style="margin-left:auto" @click="openTopic()">新增话题</button></div>',
            '    <div style="display:flex;gap:8px"><div class="ipt2" style="width:240px"><input v-model="topicKeyword" placeholder="搜索话题" /></div></div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto"><table class="pk"><thead><tr><th>话题</th><th style="width:80px">热度</th><th style="width:90px">关联帖子</th><th style="width:90px">推荐位</th><th style="width:80px">状态</th><th style="width:220px" class="r">操作</th></tr></thead>',
            '      <tbody><tr v-if="!filteredTopics.length"><td colspan="6" style="text-align:center;color:var(--tk-muted);padding:64px 0">暂无话题</td></tr>',
            '        <tr v-for="t in filteredTopics" :key="t.topicId"><td># {{ t.name }}<div v-if="t.mergedIntoId" style="font-size:11px;color:var(--tk-warn)">已合并到 #{{ topicName(t.mergedIntoId) }}</div></td>',
            '          <td>{{ t.heat }}</td><td>{{ t.postCount }}</td>',
            '          <td><span class="bdg" :class="t.recommended ? \'g\' : \'a\'">{{ t.recommended ? "推荐中" : "未推荐" }}</span></td>',
            '          <td><span class="bdg" :class="t.status === \'ENABLED\' ? \'g\' : \'a\'">{{ t.status === "ENABLED" ? "启用" : "停用" }}</span></td>',
            '          <td class="r"><span class="op" @click="openTopic(t)">编辑</span> · <span class="op" @click="toggleRecommend(t)">{{ t.recommended ? "取消推荐" : "设为推荐" }}</span> · <span class="op" @click="openMerge(t)" :style="t.mergedIntoId ? \'color:var(--tk-muted);pointer-events:none\' : \'\'">合并</span> · <span class="op" @click="toggleTopic(t)">{{ t.status === "ENABLED" ? "停用" : "启用" }}</span> · <span class="op" style="color:var(--tk-bad)" @click="removeTopic(t)">删除</span></td></tr></tbody></table></div>',
            '  </div>',

            // 屏蔽词
            '  <div v-if="tab === \'words\'" class="panel" style="display:flex;flex-direction:column;min-height:0;flex:1">',
            '    <div class="cap">屏蔽词<span class="cnt" v-if="words.length">共 {{ words.length }} 条</span>',
            '      <span style="margin-left:auto;display:flex;gap:8px">',
            '        <button class="c" @click="downloadWords">导出 Excel</button>',
            '        <button class="c" @click="triggerImport">导入 Excel</button>',
            '        <button class="c blue" @click="openWord()">新增屏蔽词</button>',
            '      </span></div>',
            '    <input id="word-import-file" type="file" accept=".xlsx,.xls" style="display:none" @change="onImportFile" />',
            '    <div style="display:flex;gap:8px"><div class="ipt2" style="width:240px"><input v-model="wordKeyword" placeholder="搜索屏蔽词/备注" /></div></div>',
            '    <div style="color:var(--tk-muted);font-size:12px">命中后帖子/评论自动隐藏并通知作者；支持 LITERAL 字面量与 REGEX 正则（禁止环视/嵌套量词，≤200 字符）；命中次数用于排查误伤。Excel 首行为表头（屏蔽词/备注/状态/匹配方式/命中次数）。</div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto"><table class="pk"><thead><tr><th style="width:220px">屏蔽词</th><th style="width:90px">匹配方式</th><th>备注</th><th style="width:80px">状态</th><th style="width:90px">命中次数</th><th style="width:150px">最近命中</th><th style="width:150px" class="r">操作</th></tr></thead>',
            '      <tbody><tr v-if="!filteredWords.length"><td colspan="7" style="text-align:center;color:var(--tk-muted);padding:64px 0">暂无屏蔽词</td></tr>',
            '        <tr v-for="w in filteredWords" :key="w.id"><td>{{ w.word }}</td>',
            '          <td><span class="bdg" :class="w.matchType === \'REGEX\' ? \'a\' : \'g\'">{{ w.matchType === "REGEX" ? "正则" : "字面量" }}</span></td>',
            '          <td style="color:var(--tk-muted);font-size:12px">{{ w.remark || "-" }}</td>',
            '          <td><span class="bdg" :class="w.status === \'ENABLED\' ? \'g\' : \'a\'">{{ w.status === "ENABLED" ? "启用" : "停用" }}</span></td>',
            '          <td>{{ w.hitCount }}</td><td style="color:var(--tk-text3)">{{ fmt(w.lastHitAt) }}</td>',
            '          <td class="r"><span class="op" @click="openWord(w)">编辑</span> · <span class="op" @click="toggleWord(w)">{{ w.status === "ENABLED" ? "停用" : "启用" }}</span> · <span class="op" style="color:var(--tk-bad)" @click="removeWord(w)">删除</span></td></tr></tbody></table></div>',
            '  </div>',

            // 帖子详情弹层
            '  <div class="pmask" :class="{ show: postVisible }" @click.self="postVisible = false">',
            '    <div class="pcard" v-if="postDetail" style="max-width:720px">',
            '      <div class="ph"><b>{{ postDetail.title }}</b><span class="no">{{ postDetail.postType === "ARTICLE" ? "文章" : "动态" }}</span><span class="sp"></span><span class="x" @click="postVisible = false">✕</span></div>',
            '      <div class="psum"><div class="l1">{{ postDetail.authorName }} · {{ postDetail.sectionName }} · {{ fmt(postDetail.createdAt) }}</div><div class="l2">状态 {{ statusText(postDetail.status) }}<span v-if="postDetail.hiddenReason">（{{ postDetail.hiddenReason }}）</span> · 举报 {{ postDetail.reportCount }} · 评论 {{ postDetail.commentCount }} · 赞 {{ postDetail.likeCount }}</div></div>',
            '      <div style="max-height:420px;overflow:auto;padding:0 4px">',
            '        <template v-if="postDetail.contentBlocks && postDetail.contentBlocks.length"><div v-for="(b, i) in postDetail.contentBlocks" :key="i"><div v-if="b.type === \'TEXT\'" style="white-space:pre-wrap;line-height:1.7;margin:8px 0">{{ b.text }}</div><img v-else :src="b.url" style="max-width:100%;border-radius:8px;margin:8px 0" /></div></template>',
            '        <template v-else><div style="white-space:pre-wrap;line-height:1.7">{{ postDetail.content }}</div><img v-for="u in postDetail.imageUrls" :key="u" :src="u" style="max-width:100%;border-radius:8px;margin:8px 0" /></template>',
            '      </div>',
            '      <div class="pbtn"><span class="sp"></span>',
            '        <button v-if="postDetail.status === \'ENABLED\'" class="c ghost" style="color:var(--tk-bad)" @click="hideFromDetail">下架帖子</button>',
            '        <button v-if="postDetail.status === \'HIDDEN\'" class="c blue" @click="restoreFromDetail">恢复帖子</button>',
            '        <button class="c ghost" @click="postVisible = false">关闭</button></div>',
            '    </div>',
            '  </div>',

            // 申诉处理弹层
            '  <div class="pmask" :class="{ show: appealVisible }" @click.self="appealVisible = false">',
            '    <div class="pcard" v-if="appealTarget">',
            '      <div class="ph"><b>处理申诉</b><span class="no">AP-{{ appealTarget.appealId }}</span><span class="sp"></span><span class="x" @click="appealVisible = false">✕</span></div>',
            '      <div class="psum"><div class="l1">{{ appealTarget.targetTitle }}</div><div class="l2">{{ appealTarget.userName }} · {{ appealTarget.reason }}</div></div>',
            '      <div class="fld"><label>处理结论 <em>*</em></label><div class="pin"><select v-model="appealAction" style="width:100%"><option value="APPROVE">通过并恢复</option><option value="REJECT">驳回</option></select></div></div>',
            '      <div class="fld"><label>回复模板</label>',
            '        <el-select v-model="appealTemplateId" clearable placeholder="选择模板自动填充处理说明" style="width:100%" @change="applyAppealTemplate">',
            '          <el-option v-for="t in appealTemplates" :key="t.id" :label="t.templateName" :value="t.id"></el-option>',
            '        </el-select></div>',
            '      <div class="fld"><label>处理说明</label><textarea class="pta" v-model="appealNote" maxlength="500" placeholder="将通知用户"></textarea></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="appealVisible = false">取消</button><button class="c blue" :disabled="submitting" @click="submitAppeal">{{ submitting ? "提交中…" : "提交" }}</button></div>',
            '    </div>',
            '  </div>',

            // 分区弹层
            '  <div class="pmask" :class="{ show: sectionVisible }" @click.self="sectionVisible = false">',
            '    <div class="pcard" v-if="sectionForm">',
            '      <div class="ph"><b>{{ sectionForm.sectionId ? "编辑分区" : "新增分区" }}</b><span class="sp"></span><span class="x" @click="sectionVisible = false">✕</span></div>',
            '      <div class="fld"><label>分区编码 <em>*</em></label><div class="pin"><input v-model="sectionForm.sectionCode" :disabled="!!sectionForm.sectionId" maxlength="32" placeholder="如 LOST_FOUND" /></div></div>',
            '      <div class="fld"><label>分区名称 <em>*</em></label><div class="pin"><input v-model="sectionForm.sectionName" maxlength="64" /></div></div>',
            '      <div class="fld"><label>图标标识</label><div class="pin"><input v-model="sectionForm.icon" maxlength="128" placeholder="如 cat-lost-found" /></div></div>',
            '      <div class="fld"><label>简介</label><div class="pin"><input v-model="sectionForm.description" maxlength="255" /></div></div>',
            '      <div style="display:flex;gap:12px"><div class="fld" style="flex:1"><label>排序</label><div class="pin"><input type="number" v-model.number="sectionForm.sortOrder" /></div></div>',
            '        <div class="fld" style="flex:1"><label>状态</label><div class="pin"><select v-model="sectionForm.status" style="width:100%"><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></div></div></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="sectionVisible = false">取消</button><button class="c blue" :disabled="submitting" @click="submitSection">{{ submitting ? "提交中…" : "保存" }}</button></div>',
            '    </div>',
            '  </div>',

            // 话题弹层
            '  <div class="pmask" :class="{ show: topicVisible }" @click.self="topicVisible = false">',
            '    <div class="pcard" v-if="topicForm">',
            '      <div class="ph"><b>{{ topicForm.topicId ? "编辑话题" : "新增话题" }}</b><span class="sp"></span><span class="x" @click="topicVisible = false">✕</span></div>',
            '      <div class="fld"><label>话题名称 <em>*</em></label><div class="pin"><input v-model="topicForm.name" maxlength="32" /></div></div>',
            '      <div style="display:flex;gap:12px"><div class="fld" style="flex:1"><label>热度</label><div class="pin"><input type="number" v-model.number="topicForm.heat" /></div></div>',
            '        <div class="fld" style="flex:1"><label>状态</label><div class="pin"><select v-model="topicForm.status" style="width:100%"><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></div></div></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="topicVisible = false">取消</button><button class="c blue" :disabled="submitting" @click="submitTopic">{{ submitting ? "提交中…" : "保存" }}</button></div>',
            '    </div>',
            '  </div>',

            // 话题合并弹层
            '  <div class="pmask" :class="{ show: mergeVisible }" @click.self="mergeVisible = false">',
            '    <div class="pcard" v-if="mergeSource">',
            '      <div class="ph"><b>合并话题</b><span class="no"># {{ mergeSource.name }}</span><span class="sp"></span><span class="x" @click="mergeVisible = false">✕</span></div>',
            '      <div class="psum"><div class="l1">把「{{ mergeSource.name }}」的帖子标签迁移到目标话题，源话题停用并记录合并去向。</div>',
            '        <div class="l2">当前关联帖子 {{ mergeSource.postCount }} 条，热度 {{ mergeSource.heat }}；合并后热度累加到目标话题。</div></div>',
            '      <div class="fld"><label>目标话题 <em>*</em></label><div class="pin"><select v-model.number="mergeTargetId" style="width:100%"><option :value="null">请选择</option><option v-for="t in topics" :key="t.topicId" :value="t.topicId" :disabled="t.topicId === mergeSource.topicId || !!t.mergedIntoId">{{ t.name }}（{{ t.postCount }} 贴）</option></select></div></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="mergeVisible = false">取消</button><button class="c blue" :disabled="submitting" @click="submitMerge">{{ submitting ? "合并中…" : "确认合并" }}</button></div>',
            '    </div>',
            '  </div>',

            // 图片预览
            '  <div v-if="previewImage" class="pmask show" @click.self="previewImage = \'\'" style="z-index:60">',
            '    <img :src="previewImage" style="max-width:86vw;max-height:86vh;border-radius:8px;background:#fff" />',
            '  </div>',

            // 官方账号回复帖子（C12）
            '  <div class="pmask" :class="{ show: replyVisible }" @click.self="replyVisible = false">',
            '    <div class="pcard" v-if="replyTarget" style="max-width:620px">',
            '      <div class="ph"><b>官方回复</b><span class="no">{{ replyTarget.title }}</span><span class="sp"></span><span class="x" @click="replyVisible = false">✕</span></div>',
            '      <div class="psum"><div class="l2">以官方账号身份在帖子下生成一条官方评论，楼主会收到站内通知；命中屏蔽词会被拒绝。仅 OFFICIAL_AUTO / OFFICIAL_HUMAN 可发声。</div></div>',
            '      <div class="fld"><label>官方账号 <em>*</em></label><div class="pin"><select v-model.number="replyForm.officialUserId" style="width:100%"><option :value="null">请选择官方账号</option><option v-for="a in officialAccounts" :key="a.userId" :value="a.userId" :disabled="a.status !== \'ENABLED\'">{{ a.displayName }}（{{ a.accountType }} · {{ a.status === \'ENABLED\' ? \'启用\' : \'停用\' }}）</option></select></div></div>',
            '      <div class="fld"><label>回复内容 <em>*</em></label><textarea class="pta" v-model="replyForm.content" maxlength="500" placeholder="例如：已收到反馈，后勤将于今日 18:00 前上门处理。"></textarea></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="replyVisible = false">取消</button><button class="c blue" :disabled="replySubmitting" @click="submitReply">{{ replySubmitting ? "发送中…" : "发布官方回复" }}</button></div>',
            '    </div>',
            '  </div>',

            // 屏蔽词弹层
            '  <div class="pmask" :class="{ show: wordVisible }" @click.self="wordVisible = false">',
            '    <div class="pcard" v-if="wordForm">',
            '      <div class="ph"><b>{{ wordForm.id ? "编辑屏蔽词" : "新增屏蔽词" }}</b><span class="sp"></span><span class="x" @click="wordVisible = false">✕</span></div>',
            '      <div class="fld"><label>屏蔽词 <em>*</em></label><div class="pin"><input v-model="wordForm.word" maxlength="200" :placeholder="wordForm.matchType === \'REGEX\' ? \'例如 广告|推广\\s*文案\' : \'输入需要拦截的完整词\'" /></div></div>',
            '      <div class="fld"><label>匹配方式</label><div class="pin"><select v-model="wordForm.matchType" style="width:100%"><option value="LITERAL">字面量（包含即命中）</option><option value="REGEX">正则（高级，慎用）</option></select></div>',
            '        <div style="font-size:11px;color:var(--tk-muted);margin-top:4px">正则保存前会做语法与安全校验；命中统计在词表刷新后继续累计。</div></div>',
            '      <div class="fld"><label>备注</label><div class="pin"><input v-model="wordForm.remark" maxlength="255" /></div></div>',
            '      <div class="fld"><label>状态</label><div class="pin"><select v-model="wordForm.status" style="width:100%"><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></div></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="wordVisible = false">取消</button><button class="c blue" :disabled="submitting" @click="submitWord">{{ submitting ? "提交中…" : "保存" }}</button></div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var tab = Vue.ref(initialTab);
            var submitting = Vue.ref(false);

            var stats = Vue.ref(null);
            var refreshAt = Vue.ref('');
            // C01 概览：时间范围仅 7/30（与后端 days 参数一致），loading/error 独立控制
            var days = Vue.ref(7);
            var statsLoading = Vue.ref(false);
            var statsError = Vue.ref('');
            var posts = Vue.ref([]);
            var postTotal = Vue.ref(0);
            var postPage = Vue.ref(1);
            var postPageSize = Vue.ref(10);
            var postTotalPages = Vue.computed(function () {
                return Math.max(1, Math.ceil(postTotal.value / postPageSize.value));
            });
            var postPageNumbers = Vue.computed(function () {
                return pageNumbers(postPage.value, postTotalPages.value);
            });
            var postKeyword = Vue.ref('');
            var postSectionId = Vue.ref(null);
            var postStatus = Vue.ref('');
            var postType = Vue.ref('');
            var loadingPosts = Vue.ref(false);
            var postVisible = Vue.ref(false);
            var postDetail = Vue.ref(null);
            var officialAccounts = Vue.ref([]);
            var replyVisible = Vue.ref(false);
            var replyTarget = Vue.ref(null);
            var replySubmitting = Vue.ref(false);
            var replyForm = Vue.reactive({ officialUserId: null, content: '' });

            function loadOfficialAccounts() {
                api.get('/api/admin/community/official-accounts?pageNum=1&pageSize=100')
                    .then(function (page) { officialAccounts.value = (page && page.records) || []; })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }
            function togglePin(p) {
                var next = !p.pinned;
                El.ElMessageBox.confirm('确认' + (next ? '置顶' : '取消置顶') + '帖子「' + p.title + '」？',
                    next ? '置顶帖子' : '取消置顶', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' })
                    .then(function () {
                        return api.post('/api/admin/community/posts/' + p.postId + '/pin', { pinned: next });
                    })
                    .then(function () {
                        El.ElMessage.success(next ? '已置顶，信息流将优先展示' : '已取消置顶');
                        loadPosts(postPage.value);
                    })
                    .catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }
            function openReply(p) {
                if (!officialAccounts.value.length) loadOfficialAccounts();
                replyTarget.value = p;
                replyForm.officialUserId = null;
                replyForm.content = '';
                replyVisible.value = true;
            }
            function submitReply() {
                if (!replyForm.officialUserId) { El.ElMessage.warning('请选择官方账号'); return; }
                if (!replyForm.content.trim()) { El.ElMessage.warning('请填写回复内容'); return; }
                replySubmitting.value = true;
                api.post('/api/admin/community/posts/' + replyTarget.value.postId + '/official-reply', {
                    officialUserId: replyForm.officialUserId,
                    content: replyForm.content.trim()
                }).then(function (result) {
                    El.ElMessage.success('官方回复已发布（评论 #' + result.commentId + '）');
                    replyVisible.value = false;
                    loadPosts(postPage.value);
                }).catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { replySubmitting.value = false; });
            }
            var postSelected = Vue.ref([]);
            var previewImage = Vue.ref('');
            var postAllSelected = Vue.computed(function () {
                return posts.value.length > 0 && postSelected.value.length === posts.value.length;
            });
            function toggleAllPosts(event) {
                postSelected.value = event.target.checked ? posts.value.map(function (p) { return p.postId; }) : [];
            }
            function togglePost(p, event) {
                var list = postSelected.value.slice();
                if (event.target.checked) {
                    if (list.indexOf(p.postId) < 0) list.push(p.postId);
                } else {
                    list = list.filter(function (id) { return id !== p.postId; });
                }
                postSelected.value = list;
            }
            function batchPost(action) {
                if (!postSelected.value.length) return;
                El.ElMessageBox.confirm('确认批量' + (action === 'HIDE' ? '下架' : '恢复') + ' ' + postSelected.value.length + ' 条帖子？',
                    '批量处置', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' })
                    .then(function () {
                        return api.post('/api/admin/community/posts/batch-status', {
                            postIds: postSelected.value.slice(), action: action, reason: '管理端批量处置'
                        });
                    })
                    .then(function (result) {
                        El.ElMessage.success('批量完成：成功 ' + (result.updated || 0)
                            + ' 条，跳过 ' + (result.skipped || 0) + ' 条');
                        postSelected.value = [];
                        loadPosts(postPage.value);
                    })
                    .catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }

            var appeals = Vue.ref([]);
            var appealTotal = Vue.ref(0);
            var appealPage = Vue.ref(1);
            var appealSize = 10;
            var appealTotalPages = Vue.computed(function () {
                return Math.max(1, Math.ceil(appealTotal.value / appealSize));
            });
            var appealPageNumbers = Vue.computed(function () {
                return pageNumbers(appealPage.value, appealTotalPages.value);
            });
            var appealTabs = [
                { value: '', label: '全部' },
                { value: 'PENDING', label: '待处理' },
                { value: 'APPROVED', label: '已通过' },
                { value: 'REJECTED', label: '已驳回' }
            ];
            var appealStatus = Vue.ref('');
            var appealType = Vue.ref('');
            var loadingAppeals = Vue.ref(false);
            var appealVisible = Vue.ref(false);
            var appealTarget = Vue.ref(null);
            var appealAction = Vue.ref('APPROVE');
            var appealNote = Vue.ref('');
            var appealTemplateId = Vue.ref(null);
            var templates = Vue.ref([]);
            var appealTemplates = Vue.computed(function () {
                return templates.value.filter(function (t) { return t.templateType === 'APPEAL_REPLY'; });
            });
            var appealSelected = Vue.ref([]);
            var appealAllSelected = Vue.computed(function () {
                return appeals.value.length > 0 && appealSelected.value.length === appeals.value.length;
            });
            function toggleAllAppeals(event) {
                appealSelected.value = event.target.checked ? appeals.value
                    .filter(function (a) { return a.status === 'PENDING'; })
                    .map(function (a) { return a.appealId; }) : [];
            }
            function toggleAppeal(a, event) {
                var list = appealSelected.value.slice();
                if (event.target.checked) {
                    if (list.indexOf(a.appealId) < 0) list.push(a.appealId);
                } else {
                    list = list.filter(function (id) { return id !== a.appealId; });
                }
                appealSelected.value = list;
            }
            function batchAppeal(action) {
                if (!appealSelected.value.length) return;
                El.ElMessageBox.confirm('确认批量' + (action === 'APPROVE' ? '通过并恢复' : '驳回')
                    + ' ' + appealSelected.value.length + ' 条申诉？', '批量复核',
                    { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' })
                    .then(function () {
                        return api.post('/api/admin/community/appeals/batch-handle', {
                            appealIds: appealSelected.value.slice(), action: action, note: null
                        });
                    })
                    .then(function (result) {
                        El.ElMessage.success('批量完成：成功 ' + (result.updated || 0)
                            + ' 条，跳过 ' + (result.skipped || 0) + ' 条');
                        appealSelected.value = [];
                        loadAppeals(appealPage.value);
                    })
                    .catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }
            function loadTemplates() {
                api.get('/api/admin/community/moderation-templates')
                    .then(function (data) { templates.value = data || []; })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }
            function applyAppealTemplate(id) {
                var tpl = templates.value.filter(function (t) { return t.id === id; })[0];
                if (tpl) appealNote.value = tpl.content;
            }
            function slaRemaining(row) {
                return typeof row.remainingMinutes === 'number' ? row.remainingMinutes : null;
            }
            function formatMinutes(minutes) {
                var m = Math.max(0, Math.round(minutes));
                return m >= 60 ? (Math.floor(m / 60) + 'h ' + (m % 60) + 'm') : (m + 'm');
            }
            function slaText(row) {
                var remaining = slaRemaining(row);
                if (remaining === null) return '—';
                return remaining < 0 ? '已超时 ' + formatMinutes(-remaining) : '剩余 ' + formatMinutes(remaining);
            }
            function slaTone(row) {
                var remaining = slaRemaining(row);
                if (remaining === null) return 'muted';
                if (remaining < 0) return 'r';
                return remaining <= 240 ? 'a' : 'g';
            }

            var sections = Vue.ref([]);
            var sectionKeyword = Vue.ref('');
            var filteredSections = Vue.computed(function () {
                var keyword = sectionKeyword.value.trim().toLowerCase();
                if (!keyword) return sections.value;
                return sections.value.filter(function (s) {
                    return (s.sectionCode || '').toLowerCase().indexOf(keyword) >= 0
                        || (s.sectionName || '').toLowerCase().indexOf(keyword) >= 0;
                });
            });
            var sectionVisible = Vue.ref(false);
            var sectionForm = Vue.ref(null);
            var sectionOrderDirty = Vue.ref(false);
            var sectionSaving = Vue.ref(false);
            var dragIndex = -1;
            function dragStart(index) { dragIndex = index; }
            function dropAt(index) {
                if (dragIndex < 0 || dragIndex === index) return;
                var list = sections.value.slice();
                var moved = list.splice(dragIndex, 1)[0];
                list.splice(index, 0, moved);
                sections.value = list;
                sectionOrderDirty.value = true;
                dragIndex = -1;
            }
            function moveSection(index, delta) {
                var target = index + delta;
                if (target < 0 || target >= sections.value.length) return;
                var list = sections.value.slice();
                var moved = list.splice(index, 1)[0];
                list.splice(target, 0, moved);
                sections.value = list;
                sectionOrderDirty.value = true;
            }
            function saveSectionOrder() {
                sectionSaving.value = true;
                api.post('/api/admin/community/sections/reorder', {
                    sectionIds: sections.value.map(function (s) { return s.sectionId; })
                }).then(function (result) {
                    El.ElMessage.success('排序已保存：' + (result.updated || 0) + ' 个分区');
                    sectionOrderDirty.value = false;
                    loadSections();
                }).catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { sectionSaving.value = false; });
            }
            var topics = Vue.ref([]);
            var topicKeyword = Vue.ref('');
            var filteredTopics = Vue.computed(function () {
                var keyword = topicKeyword.value.trim().toLowerCase();
                if (!keyword) return topics.value;
                return topics.value.filter(function (t) {
                    return (t.name || '').toLowerCase().indexOf(keyword) >= 0;
                });
            });
            var words = Vue.ref([]);
            var wordKeyword = Vue.ref('');
            var filteredWords = Vue.computed(function () {
                var keyword = wordKeyword.value.trim().toLowerCase();
                if (!keyword) return words.value;
                return words.value.filter(function (w) {
                    return (w.word || '').toLowerCase().indexOf(keyword) >= 0
                        || (w.remark || '').toLowerCase().indexOf(keyword) >= 0;
                });
            });
            var topicVisible = Vue.ref(false);
            var topicForm = Vue.ref(null);
            var mergeVisible = Vue.ref(false);
            var mergeSource = Vue.ref(null);
            var mergeTargetId = Vue.ref(null);
            function topicName(id) {
                var found = topics.value.filter(function (t) { return t.topicId === id; })[0];
                return found ? found.name : id;
            }
            function toggleRecommend(t) {
                api.post('/api/admin/community/topics/' + t.topicId + '/recommend?recommended=' + (!t.recommended))
                    .then(function () { El.ElMessage.success('推荐位已更新'); loadTopics(); })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }
            function openMerge(t) {
                if (t.mergedIntoId) return;
                mergeSource.value = t;
                mergeTargetId.value = null;
                mergeVisible.value = true;
            }
            function submitMerge() {
                if (!mergeTargetId.value) { El.ElMessage.warning('请选择合并目标话题'); return; }
                submitting.value = true;
                api.post('/api/admin/community/topics/' + mergeSource.value.topicId + '/merge',
                    { targetTopicId: mergeTargetId.value })
                    .then(function () {
                        El.ElMessage.success('话题已合并，帖子标签已迁移');
                        mergeVisible.value = false;
                        loadTopics();
                    })
                    .catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { submitting.value = false; });
            }
            var wordVisible = Vue.ref(false);
            var wordForm = Vue.ref(null);

            function loadStats() {
                statsLoading.value = true;
                statsError.value = '';
                api.get('/api/admin/community/overview?days=' + days.value)
                    .then(function (data) {
                        stats.value = data;
                        refreshAt.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
                    })
                    .catch(function (e) {
                        statsError.value = e.message || '请求失败';
                        El.ElMessage.error(e.message);
                    })
                    .finally(function () { statsLoading.value = false; });
            }
            function setDays(value) {
                if (days.value === value) return;
                days.value = value;
                loadStats();
            }
            /** 导出 CSV：与页面同口径（days），后端返回 UTF-8 + BOM 文件 */
            function exportStats() {
                var headers = { Authorization: 'Bearer ' + api.getToken() };
                var campusId = api.getCampusId ? api.getCampusId() : null;
                if (campusId) headers['X-Campus-Id'] = campusId;
                fetch('/api/admin/community/overview/export?days=' + days.value, { headers: headers })
                    .then(function (res) {
                        if (!res.ok) throw new Error('导出失败（HTTP ' + res.status + '）');
                        return res.blob();
                    })
                    .then(function (blob) {
                        var url = URL.createObjectURL(blob);
                        var a = document.createElement('a');
                        a.href = url;
                        a.download = 'community-overview-' + days.value + 'd-' + new Date().toISOString().slice(0, 10) + '.csv';
                        document.body.appendChild(a);
                        a.click();
                        a.remove();
                        URL.revokeObjectURL(url);
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '导出失败'); });
            }
            function loadSections() {
                return api.get('/api/admin/community/sections')
                    .then(function (data) { sections.value = data || []; })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }
            function loadPosts(page) {
                postPage.value = page || 1;
                loadingPosts.value = true;
                var params = ['pageNum=' + postPage.value, 'pageSize=' + postPageSize.value];
                if (postKeyword.value) params.push('keyword=' + encodeURIComponent(postKeyword.value));
                if (postSectionId.value) params.push('sectionId=' + postSectionId.value);
                if (postStatus.value) params.push('status=' + postStatus.value);
                if (postType.value) params.push('postType=' + postType.value);
                api.get('/api/admin/community/posts?' + params.join('&'))
                    .then(function (data) { posts.value = data.records || []; postTotal.value = data.total || 0; })
                    .catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { loadingPosts.value = false; });
            }
            function loadAppeals(page) {
                appealPage.value = page || 1;
                loadingAppeals.value = true;
                var params = ['pageNum=' + appealPage.value, 'pageSize=' + appealSize];
                if (appealStatus.value) params.push('status=' + appealStatus.value);
                if (appealType.value) params.push('targetType=' + appealType.value);
                api.get('/api/admin/community/appeals?' + params.join('&'))
                    .then(function (data) { appeals.value = data.records || []; appealTotal.value = data.total || 0; })
                    .catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { loadingAppeals.value = false; });
            }
            function loadTopics() {
                api.get('/api/admin/community/topics').then(function (data) { topics.value = data || []; })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }
            function loadWords() {
                api.get('/api/admin/community/banned-words').then(function (data) { words.value = data || []; })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }
            function reload() {
                if (tab.value === 'overview') loadStats();
                if (tab.value === 'posts') loadPosts(postPage.value);
                if (tab.value === 'appeals') loadAppeals(appealPage.value);
                if (tab.value === 'sections') loadSections();
                if (tab.value === 'topics') loadTopics();
                if (tab.value === 'words') loadWords();
            }
            function switchTab(value) {
                tab.value = value;
                reload();
            }
            function pageNumbers(current, total) {
                var list = [];
                var start = Math.max(1, current - 2);
                var end = Math.min(total, start + 4);
                start = Math.max(1, end - 4);
                for (var i = start; i <= end; i++) {
                    list.push(i);
                }
                return list;
            }
            function switchAppealStatus(value) {
                appealStatus.value = value;
                loadAppeals(1);
            }

            function openPost(p) {
                api.get('/api/admin/community/posts/' + p.postId)
                    .then(function (data) { postDetail.value = data; postVisible.value = true; })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }
            function hidePost(p) {
                El.ElMessageBox.confirm('确认下架该帖子？', '下架帖子', { type: 'warning', confirmButtonText: '下架', cancelButtonText: '取消' })
                    .then(function () { return api.post('/api/admin/community/content/POST/' + p.postId + '/hide', { reason: '管理端下架' }); })
                    .then(function () { El.ElMessage.success('已下架'); loadPosts(postPage.value); })
                    .catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }
            function restorePost(p) {
                api.post('/api/admin/community/content/POST/' + p.postId + '/restore', { reason: '管理端恢复' })
                    .then(function () { El.ElMessage.success('已恢复'); loadPosts(postPage.value); })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }
            function hideFromDetail() {
                var detail = postDetail.value;
                if (!detail) return;
                El.ElMessageBox.confirm('确认下架该帖子？', '下架帖子', { type: 'warning', confirmButtonText: '下架', cancelButtonText: '取消' })
                    .then(function () { return api.post('/api/admin/community/content/POST/' + detail.postId + '/hide', { reason: '管理端下架' }); })
                    .then(function () { El.ElMessage.success('已下架'); detail.status = 'HIDDEN'; loadPosts(postPage.value); })
                    .catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }
            function restoreFromDetail() {
                var detail = postDetail.value;
                if (!detail) return;
                api.post('/api/admin/community/content/POST/' + detail.postId + '/restore', { reason: '管理端恢复' })
                    .then(function () { El.ElMessage.success('已恢复'); detail.status = 'ENABLED'; loadPosts(postPage.value); })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }

            function openAppeal(a) {
                appealTarget.value = a; appealAction.value = 'APPROVE'; appealNote.value = '';
                appealTemplateId.value = null; appealVisible.value = true;
            }
            function submitAppeal() {
                submitting.value = true;
                api.post('/api/admin/community/appeals/' + appealTarget.value.appealId + '/handle',
                    { action: appealAction.value, note: appealNote.value,
                        templateId: appealTemplateId.value || null })
                    .then(function () { El.ElMessage.success('已处理'); appealVisible.value = false; loadAppeals(appealPage.value); })
                    .catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { submitting.value = false; });
            }

            function openSection(s) {
                sectionForm.value = s ? {
                    sectionId: s.sectionId, sectionCode: s.sectionCode, sectionName: s.sectionName,
                    icon: s.icon, description: s.description, sortOrder: s.sortOrder, status: s.status,
                    recommendWeight: s.recommendWeight || 0
                } : { sectionId: null, sectionCode: '', sectionName: '', icon: '', description: '',
                    sortOrder: 0, status: 'ENABLED', recommendWeight: 0 };
                sectionVisible.value = true;
            }
            function submitSection() {
                submitting.value = true;
                var f = sectionForm.value;
                var payload = { sectionCode: f.sectionCode, sectionName: f.sectionName, icon: f.icon,
                    description: f.description, sortOrder: f.sortOrder, status: f.status,
                    recommendWeight: f.recommendWeight || 0 };
                var promise = f.sectionId ? api.put('/api/admin/community/sections/' + f.sectionId, payload)
                    : api.post('/api/admin/community/sections', payload);
                promise.then(function () { El.ElMessage.success('已保存'); sectionVisible.value = false; loadSections(); })
                    .catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { submitting.value = false; });
            }
            function removeSection(s) {
                El.ElMessageBox.confirm('确认删除分区「' + s.sectionName + '」？分区下有帖子时会被拒绝。', '删除分区', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
                    .then(function () { return api.delete('/api/admin/community/sections/' + s.sectionId); })
                    .then(function () { El.ElMessage.success('已删除'); loadSections(); })
                    .catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }
            function toggleSection(s) {
                api.put('/api/admin/community/sections/' + s.sectionId, {
                    sectionCode: s.sectionCode, sectionName: s.sectionName, icon: s.icon,
                    description: s.description, bannerTitle: s.bannerTitle, bannerSubtitle: s.bannerSubtitle,
                    bannerImage: s.bannerImage, sortOrder: s.sortOrder,
                    recommendWeight: s.recommendWeight || 0,
                    status: s.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
                }).then(function () { El.ElMessage.success('状态已更新'); loadSections(); })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }

            function openTopic(t) {
                topicForm.value = t ? { topicId: t.topicId, name: t.name, heat: t.heat, status: t.status }
                    : { topicId: null, name: '', heat: 0, status: 'ENABLED' };
                topicVisible.value = true;
            }
            function submitTopic() {
                submitting.value = true;
                var f = topicForm.value;
                var payload = { name: f.name, heat: f.heat, status: f.status };
                var promise = f.topicId ? api.put('/api/admin/community/topics/' + f.topicId, payload)
                    : api.post('/api/admin/community/topics', payload);
                promise.then(function () { El.ElMessage.success('已保存'); topicVisible.value = false; loadTopics(); })
                    .catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { submitting.value = false; });
            }
            function removeTopic(t) {
                El.ElMessageBox.confirm('确认删除话题「' + t.name + '」？', '删除话题', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
                    .then(function () { return api.delete('/api/admin/community/topics/' + t.topicId); })
                    .then(function () { El.ElMessage.success('已删除'); loadTopics(); })
                    .catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }
            function toggleTopic(t) {
                api.put('/api/admin/community/topics/' + t.topicId, {
                    name: t.name, heat: t.heat,
                    status: t.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
                }).then(function () { El.ElMessage.success('状态已更新'); loadTopics(); })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }

            function openWord(w) {
                wordForm.value = w ? { id: w.id, word: w.word, remark: w.remark, status: w.status,
                    matchType: w.matchType || 'LITERAL' }
                    : { id: null, word: '', remark: '', status: 'ENABLED', matchType: 'LITERAL' };
                wordVisible.value = true;
            }
            function submitWord() {
                submitting.value = true;
                var f = wordForm.value;
                var payload = { word: f.word, remark: f.remark, status: f.status,
                    matchType: f.matchType || 'LITERAL' };
                var promise = f.id ? api.put('/api/admin/community/banned-words/' + f.id, payload)
                    : api.post('/api/admin/community/banned-words', payload);
                promise.then(function () { El.ElMessage.success('已保存'); wordVisible.value = false; loadWords(); })
                    .catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { submitting.value = false; });
            }
            function removeWord(w) {
                El.ElMessageBox.confirm('确认删除屏蔽词「' + w.word + '」？', '删除屏蔽词', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
                    .then(function () { return api.delete('/api/admin/community/banned-words/' + w.id); })
                    .then(function () { El.ElMessage.success('已删除'); loadWords(); })
                    .catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }
            function toggleWord(w) {
                api.put('/api/admin/community/banned-words/' + w.id, {
                    word: w.word, remark: w.remark, matchType: w.matchType || 'LITERAL',
                    status: w.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
                }).then(function () { El.ElMessage.success('状态已更新'); loadWords(); })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }

            function triggerImport() {
                var input = document.getElementById('word-import-file');
                if (input) {
                    input.click();
                }
            }
            function onImportFile(event) {
                var file = event.target.files && event.target.files[0];
                if (!file) {
                    return;
                }
                if (!/\.xlsx?$/i.test(file.name)) {
                    El.ElMessage.warning('仅支持 .xlsx / .xls');
                    event.target.value = '';
                    return;
                }
                var form = new FormData();
                form.append('file', file);
                fetch('/api/admin/community/banned-words/import', {
                    method: 'POST',
                    headers: { Authorization: 'Bearer ' + api.getToken() },
                    body: form
                }).then(function (res) {
                    return res.json().then(function (payload) {
                        return { res: res, payload: payload };
                    });
                }).then(function (result) {
                    if (!result.res.ok || !result.payload || result.payload.success === false) {
                        throw new Error((result.payload && result.payload.message) || '导入失败');
                    }
                    var data = result.payload.data || {};
                    El.ElMessage.success('导入完成：新增 ' + (data.imported || 0)
                            + '，重复 ' + (data.duplicated || 0) + '，无效 ' + (data.invalid || 0));
                    loadWords();
                    loadStats();
                }).catch(function (error) {
                    El.ElMessage.error(error.message || '导入失败');
                }).finally(function () {
                    event.target.value = '';
                });
            }
            function downloadWords() {
                fetch('/api/admin/community/banned-words/export', {
                    headers: { Authorization: 'Bearer ' + api.getToken() }
                }).then(function (res) {
                    if (!res.ok) {
                        throw new Error('导出失败');
                    }
                    return res.blob();
                }).then(function (blob) {
                    var url = URL.createObjectURL(blob);
                    var a = document.createElement('a');
                    a.href = url;
                    a.download = 'banned-words.xlsx';
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                    URL.revokeObjectURL(url);
                }).catch(function (error) {
                    El.ElMessage.error(error.message || '导出失败');
                });
            }

            function rangePosts() {
                if (!stats.value) return 0;
                return stats.value.rangePosts != null ? +stats.value.rangePosts : weekTotal();
            }
            function maxTopic() {
                if (!stats.value || !stats.value.topics || !stats.value.topics.length) return 1;
                return Math.max(1, Math.max.apply(null, stats.value.topics.map(function (t) { return +t.count || 0; })));
            }
            function topicShare(topic) {
                if (!stats.value || !stats.value.topics || !stats.value.topics.length) return 0;
                var total = stats.value.topics.reduce(function (sum, t) { return sum + (+t.count || 0); }, 0);
                return total ? Math.round(((+topic.count || 0) * 100) / total) : 0;
            }
            /** 30 天视图下横轴过密：仅保留 5 天刻度与首末日，避免日期重叠 */
            function barDate(date, index) {
                var length = stats.value && stats.value.trend ? stats.value.trend.length : 0;
                if (days.value <= 7 || index % 5 === 0 || index === length - 1) {
                    return shortDate(date);
                }
                return '';
            }
            function maxTrend() {
                if (!stats.value || !stats.value.trend || !stats.value.trend.length) {
                    return 1;
                }
                return Math.max(1, Math.max.apply(null, stats.value.trend.map(function (d) { return d.count || 0; })));
            }
            function maxSection() {
                if (!stats.value || !stats.value.sections || !stats.value.sections.length) {
                    return 1;
                }
                return Math.max(1, Math.max.apply(null, stats.value.sections.map(function (s) { return s.count || 0; })));
            }
            function go(route) {
                location.hash = '#/' + route;
            }

            function barWidth(value, max) {
                return max <= 0 ? 0 : Math.round((value || 0) * 100 / max);
            }

            function weekTotal() {
                if (!stats.value || !stats.value.trend) return 0;
                return stats.value.trend.reduce(function (sum, d) { return sum + (+d.count || 0); }, 0);
            }
            function weekAvg() {
                var len = stats.value && stats.value.trend ? stats.value.trend.length : 0;
                return len ? Math.round(weekTotal() / len) : 0;
            }
            function weekPeak() {
                if (!stats.value || !stats.value.trend || !stats.value.trend.length) return 0;
                return Math.max.apply(null, stats.value.trend.map(function (d) { return +d.count || 0; }));
            }
            function barHeight(value) {
                return Math.max(2, barWidth(value, maxTrend()));
            }
            function shortDate(value) {
                return String(value || '').slice(5);
            }
            function hiddenTotal() {
                if (!stats.value) return 0;
                return (+stats.value.hiddenPosts || 0) + (+stats.value.hiddenComments || 0);
            }
            function pendingTotal() {
                if (!stats.value) return 0;
                return (+stats.value.pendingReports || 0) + (+stats.value.pendingAppeals || 0);
            }
            function healthScore() {
                if (!stats.value) return '100.0';
                var total = (+stats.value.totalPosts || 0) + (+stats.value.totalComments || 0);
                if (!total) return '100.0';
                return ((1 - hiddenTotal() / total) * 100).toFixed(1);
            }
            function avgComments() {
                if (!stats.value || !+stats.value.totalPosts) return '0.0';
                return ((+stats.value.totalComments || 0) / +stats.value.totalPosts).toFixed(1);
            }
            function sectionShare(section) {
                if (!stats.value || !stats.value.sections || !stats.value.sections.length) return 0;
                var total = stats.value.sections.reduce(function (sum, s) { return sum + (+s.count || 0); }, 0);
                return total ? Math.round(((+section.count || 0) * 100) / total) : 0;
            }

            function statusText(s) { return STATUS_MAP[s] || s; }
            function appealText(s) { return APPEAL_STATUS[s] || s; }
            function fmt(v) {
                if (!v) return '-';
                return String(v).replace('T', ' ').slice(0, 16);
            }

            loadStats();
            loadSections();
            loadTemplates();
            loadOfficialAccounts();
            // 深链接直接打开非概览页签时也要拉取对应数据；此前只有从概览切页签才触发。
            if (initialTab === 'posts') loadPosts(1);
            if (initialTab === 'appeals') loadAppeals(1);
            if (initialTab === 'topics') loadTopics();
            if (initialTab === 'words') loadWords();

            return {
                pageTitle: pageTitle, pageSub: pageSub,
                tabs: TABS, tab: tab, submitting: submitting, go: go,
                stats: stats, sections: sections, refreshAt: refreshAt,
                days: days, statsLoading: statsLoading, statsError: statsError,
                setDays: setDays, exportStats: exportStats, loadStats: loadStats, rangePosts: rangePosts,
                maxTopic: maxTopic, topicShare: topicShare, barDate: barDate,
                posts: posts, postTotal: postTotal, postPage: postPage,
                postPageSize: postPageSize, postTotalPages: postTotalPages, postPageNumbers: postPageNumbers,
                postKeyword: postKeyword, postSectionId: postSectionId, postStatus: postStatus,
                postType: postType, loadingPosts: loadingPosts, postVisible: postVisible, postDetail: postDetail,
                postSelected: postSelected, postAllSelected: postAllSelected, toggleAllPosts: toggleAllPosts,
                togglePost: togglePost, batchPost: batchPost, previewImage: previewImage,
                officialAccounts: officialAccounts, replyVisible: replyVisible, replyTarget: replyTarget,
                replyForm: replyForm, replySubmitting: replySubmitting,
                togglePin: togglePin, openReply: openReply, submitReply: submitReply,
                appeals: appeals, appealTotal: appealTotal, appealPage: appealPage, appealSize: appealSize,
                appealTabs: appealTabs, appealTotalPages: appealTotalPages, appealPageNumbers: appealPageNumbers,
                appealStatus: appealStatus, appealType: appealType, loadingAppeals: loadingAppeals,
                appealVisible: appealVisible, appealTarget: appealTarget, appealAction: appealAction, appealNote: appealNote,
                appealTemplates: appealTemplates, appealTemplateId: appealTemplateId,
                applyAppealTemplate: applyAppealTemplate,
                appealSelected: appealSelected, appealAllSelected: appealAllSelected,
                toggleAllAppeals: toggleAllAppeals, toggleAppeal: toggleAppeal, batchAppeal: batchAppeal,
                slaText: slaText, slaTone: slaTone,
                sectionKeyword: sectionKeyword, filteredSections: filteredSections,
                sectionVisible: sectionVisible, sectionForm: sectionForm,
                sectionOrderDirty: sectionOrderDirty, sectionSaving: sectionSaving,
                dragStart: dragStart, dropAt: dropAt, moveSection: moveSection,
                saveSectionOrder: saveSectionOrder,
                topics: topics, topicKeyword: topicKeyword, filteredTopics: filteredTopics,
                topicVisible: topicVisible, topicForm: topicForm,
                mergeVisible: mergeVisible, mergeSource: mergeSource, mergeTargetId: mergeTargetId,
                topicName: topicName, toggleRecommend: toggleRecommend, openMerge: openMerge, submitMerge: submitMerge,
                words: words, wordKeyword: wordKeyword, filteredWords: filteredWords,
                wordVisible: wordVisible, wordForm: wordForm,
                reload: reload, switchTab: switchTab, switchAppealStatus: switchAppealStatus,
                loadPosts: loadPosts, loadAppeals: loadAppeals, loadSections: loadSections,
                openPost: openPost, hidePost: hidePost, restorePost: restorePost,
                hideFromDetail: hideFromDetail, restoreFromDetail: restoreFromDetail,
                openAppeal: openAppeal, submitAppeal: submitAppeal,
                openSection: openSection, submitSection: submitSection, removeSection: removeSection, toggleSection: toggleSection,
                openTopic: openTopic, submitTopic: submitTopic, removeTopic: removeTopic, toggleTopic: toggleTopic,
                openWord: openWord, submitWord: submitWord, removeWord: removeWord, toggleWord: toggleWord,
                triggerImport: triggerImport, onImportFile: onImportFile, downloadWords: downloadWords,
                maxTrend: maxTrend, maxSection: maxSection, barWidth: barWidth,
                weekTotal: weekTotal, weekAvg: weekAvg, weekPeak: weekPeak, barHeight: barHeight,
                shortDate: shortDate, hiddenTotal: hiddenTotal, pendingTotal: pendingTotal,
                healthScore: healthScore, avgComments: avgComments, sectionShare: sectionShare,
                statusText: statusText, appealText: appealText, fmt: fmt
            };
        }
        };
    }

    window.AdminPages.CommunityOverviewPage = createCommunityPage(
        'CommunityOverviewPage', 'overview', '社区概览', '社区数据、趋势与分区分布');
    window.AdminPages.CommunityPostsPage = createCommunityPage(
        'CommunityPostsPage', 'posts', '帖子管理', '全量帖子、封面详情与下架恢复');
    window.AdminPages.CommunityAppealsPage = createCommunityPage(
        'CommunityAppealsPage', 'appeals', '申诉复核', '帖子/评论/处罚申诉的人工复核');
    window.AdminPages.CommunitySectionsPage = createCommunityPage(
        'CommunitySectionsPage', 'sections', '分区管理', '分区增删改、排序与启停');
    window.AdminPages.CommunityTopicsPage = createCommunityPage(
        'CommunityTopicsPage', 'topics', '话题管理', '话题增删改与热度');
    window.AdminPages.CommunityWordsPage = createCommunityPage(
        'CommunityWordsPage', 'words', '屏蔽词', '命中自动隐藏并通知作者');
})();
