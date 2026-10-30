/* 官方直投广告：不绑定账号、独立创建；管理端负责广告主关联、审核、排期、上下架、优先级与效果复盘。
   审核状态机（后端为准）：DRAFT/REJECTED --submit--> PENDING --review--> APPROVED/REJECTED；
   仅 DRAFT/REJECTED 可编辑；仅 APPROVED 可上架（未审核上架后端返回 409，前端不展示「上架」）。
   统计口径：IN_APP_WEBVIEW / APP_PAGE 可测；EXTERNAL_BROWSER / DEEPLINK 的跳出率与停留返回 null，
   页面明确显示「不可测」，禁止把不可测当 0。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var LANDING = {
        IN_APP_WEBVIEW: { label: 'App 内 WebView', measurable: true, hint: '可测停留与跳出' },
        APP_PAGE: { label: 'App 原生页', measurable: true, hint: '可测停留与跳出' },
        EXTERNAL_BROWSER: { label: '外部浏览器', measurable: false, hint: '跳出率不可测' },
        DEEPLINK: { label: '系统深链', measurable: false, hint: '跳出率不可测' }
    };
    var SLOT = { FEED: '信息流', BANNER: 'Banner', SPLASH: '开屏', SERVICE_HALL: '服务大厅' };
    var STATUS = { DRAFT: '草稿', ENABLED: '投放中', DISABLED: '已停用', EXPIRED: '已过期' };
    var STATUS_TONE = { DRAFT: 'muted', ENABLED: 'ok', DISABLED: 'warn', EXPIRED: 'bad' };
    var REVIEW = {
        DRAFT: { label: '草稿', tone: 'muted' },
        PENDING: { label: '待审核', tone: 'warn' },
        APPROVED: { label: '已通过', tone: 'ok' },
        REJECTED: { label: '已驳回', tone: 'bad' }
    };

    function pad(n) { return n < 10 ? '0' + n : '' + n; }
    function localInput(d) {
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    }
    function blank() {
        var now = new Date();
        var end = new Date(now.getTime() + 7 * 86400000);
        return {
            adId: null, advertiserId: '', title: '', brandName: '', description: '', coverUrl: '', targetUrl: '',
            landingType: 'IN_APP_WEBVIEW', sectionId: '', slot: 'FEED', priority: 0,
            startAt: localInput(now), endAt: localInput(end), status: 'DRAFT',
            bounceThresholdSeconds: 5, ctaText: '了解详情', sponsoredLabel: '推广',
            planId: '', materialId: ''
        };
    }

    function blankPlan() {
        var now = new Date();
        var end = new Date(now.getTime() + 14 * 86400000);
        return {
            planId: null, planName: '', advertiserId: '', objective: 'AWARENESS', slot: 'FEED',
            startAt: localInput(now), endAt: localInput(end), budgetCents: null,
            dailyBudgetCents: null, remark: ''
        };
    }
    function blankMaterial() {
        return {
            materialId: null, materialName: '', materialType: 'IMAGE', fileUrl: '', coverUrl: '',
            title: '', description: '', width: null, height: null
        };
    }
    var MATERIAL_TYPE = { IMAGE: '图片', VIDEO: '视频', TEXT: '文本' };

    function currentPermissions() {
        var user = (window.AdminApp && window.AdminApp.state && window.AdminApp.state.user) || {};
        var perms = (user.permissions || []).slice();
        var roles = user.roles || [];
        if (roles.indexOf('PLATFORM_ADMIN') >= 0 && perms.indexOf('platform:all') < 0) {
            perms.push('platform:all');
        }
        return perms;
    }
    function reviewOf(a) { return a && a.reviewStatus ? a.reviewStatus : 'DRAFT'; }
    function editable(a) { var s = reviewOf(a); return s === 'DRAFT' || s === 'REJECTED'; }
    function approved(a) { return reviewOf(a) === 'APPROVED'; }

    window.AdminPages.CommunityAdsPage = {
        name: 'CommunityAdsPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>信息流广告</h1><div class="sub">广告主 · 审核 · 排期 · 可测数据链路</div></div>',
            '    <div class="r"><button class="btn2" @click="load">刷新</button><button v-if="canManage" class="btn2 pri" @click="openCreate">新建广告</button></div></div>',

            '  <div class="tabs" style="margin-bottom:2px"><span class="tab" :class="{ on: activeTab === \'ads\' }" @click="switchTab(\'ads\')">直投广告</span><span class="tab" :class="{ on: activeTab === \'plans\' }" @click="switchTab(\'plans\')">投放计划</span><span class="tab" :class="{ on: activeTab === \'materials\' }" @click="switchTab(\'materials\')">素材库</span></div>',

            '  <div v-show="activeTab === \'ads\'" class="os-grid" style="grid-template-columns:repeat(6,1fr)">',
            '    <div class="os-kpi"><div class="k-label"><span class="dot"></span>广告总数</div><div class="k-val">{{ total }}<small>条</small></div><div class="k-foot"><span>官方直投，不占运营账号</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>待审核</div><div class="k-val">{{ pendingReview }}<small>条</small></div><div class="k-foot"><span>需 community:ad:review</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>投放中</div><div class="k-val">{{ activeCount }}<small>条</small></div><div class="k-foot"><span>当前页在投</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-info)"></span>曝光</div><div class="k-val">{{ sum.impressions }}<small>次</small></div><div class="k-foot"><span>当前页汇总</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>点击率</div><div class="k-val">{{ sum.ctr == null ? "—" : sum.ctr + "%" }}</div><div class="k-foot"><span>{{ sum.clicks }} 次点击 · 当前页</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-bad)"></span>跳出率</div><div class="k-val" :style="{ fontSize: sum.bounceRate == null ? \'15px\' : \'29px\' }">{{ sum.bounceText }}</div><div class="k-foot"><span>{{ sum.bounceFoot }}</span></div></div>',
            '  </div>',

            '  <div v-show="activeTab === \'ads\'" class="os-note"><b>可测边界</b> 仅在 App 内 WebView 与 App 原生页可采集落地停留、跳出；外部浏览器和系统深链只能记录点击，跳出率/停留时长显示「不可测」。当前页汇总只统计本次加载的广告，不代表全站。</div>',
            '  <div v-show="activeTab === \'ads\'" class="os-note"><b>审核规则</b> 新建/编辑一律保存为草稿；提交审核后进入待审核，待审核/已通过的广告不可编辑；仅审核通过（APPROVED）的广告可上架，未审核上架会被后端拒绝。</div>',
            '  <div v-if="!canManage && !canReview" class="os-note warn"><b>只读权限</b> 当前账号缺少 community:ad:manage / community:ad:review / platform:all，仅可查看列表与投放数据。</div>',

            '  <div v-show="activeTab === \'ads\'" class="os-panel" style="flex:1;min-height:0;display:flex;flex-direction:column">',
            '    <div class="os-panel-h" style="flex-wrap:wrap;row-gap:8px">',
            '      <h3>投放列表</h3><span class="hint">按优先级与更新时间排序</span>',
            '      <div class="r" style="flex-wrap:wrap">',
            '        <select class="os-select" style="width:108px" v-model="reviewFilter" @change="pageNum = 1; load()"><option value="">全部审核</option><option value="DRAFT">草稿</option><option value="PENDING">待审核</option><option value="APPROVED">已通过</option><option value="REJECTED">已驳回</option></select>',
            '        <select class="os-select" style="width:106px" v-model="statusFilter" @change="pageNum = 1; load()"><option value="">全部投放</option><option value="DRAFT">草稿</option><option value="ENABLED">投放中</option><option value="DISABLED">已停用</option></select>',
            '        <select class="os-select" style="width:104px" v-model="slotFilter" @change="pageNum = 1; load()"><option value="">全部位置</option><option v-for="(l,k) in SLOT" :key="k" :value="k">{{ l }}</option></select>',
            '        <el-select v-model="advertiserFilter" filterable remote clearable reserve-keyword placeholder="全部广告主" style="width:160px" :remote-method="searchAdvertisers" :loading="advertiserLoading" @change="pageNum = 1; load()">',
            '          <el-option v-for="adv in advertisers" :key="adv.advertiserId" :label="adv.name + (adv.status === \'DISABLED\' ? \'（停用）\' : \'\')" :value="String(adv.advertiserId)"></el-option>',
            '        </el-select>',
            '        <div class="srch" style="width:200px;background:var(--tk-input)"><el-icon :size="13"><Search /></el-icon><input v-model.trim="keyword" placeholder="搜索标题 / 品牌" @keyup.enter="pageNum = 1; load()" /></div>',
            '      </div>',
            '    </div>',
            '    <div style="overflow:auto;flex:1">',
            '      <table style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr><th style="width:23%">广告</th><th style="width:7.5%">审核</th><th style="width:7.5%">投放</th><th style="width:8.5%">广告主</th><th style="width:9%">落地</th><th style="width:9%">排期</th><th style="width:5.5%">优先</th><th style="width:12%">数据</th><th style="width:18%;text-align:right">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-for="a in rows" :key="a.adId">',
            '            <td><div style="display:flex;align-items:center;gap:8px;min-width:0"><span style="width:36px;height:28px;border-radius:5px;background:var(--tk-brand-soft);display:grid;place-items:center;font-size:10px;color:var(--tk-brand);flex:none;overflow:hidden"><img v-if="a.coverUrl" :src="a.coverUrl" style="width:100%;height:100%;object-fit:cover" /><span v-else>AD</span></span><div style="min-width:0;flex:1"><div style="display:flex;align-items:center;gap:6px;min-width:0"><b style="flex:1;min-width:0;font-weight:550;color:var(--tk-text);overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="a.title">{{ a.title }}</b><span class="os-tag info" style="flex:none">{{ SLOT[a.slot] || a.slot }}</span></div><div style="font-size:10px;color:var(--tk-muted);margin-top:3px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ a.brandName }} · <span class="os-mono">#{{ a.adId }}</span></div></div></div></td>',
            '            <td><span class="os-tag" :class="REVIEW[reviewOf(a)] ? REVIEW[reviewOf(a)].tone : \'muted\'" :title="a.reviewReason || \'\'">{{ REVIEW[reviewOf(a)] ? REVIEW[reviewOf(a)].label : reviewOf(a) }}</span></td>',
            '            <td><span class="os-tag" :class="STATUS_TONE[a.effectiveStatus] || STATUS_TONE[a.status] || \'muted\'">{{ STATUS[a.effectiveStatus] || STATUS[a.status] || a.status }}</span></td>',
            '            <td><div style="font-size:11px;color:var(--tk-text2);overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="a.advertiserName || \'\'">{{ a.advertiserName || "未关联" }}</div><div v-if="a.advertiserId" style="font-size:10px;color:var(--tk-muted);margin-top:2px" class="os-mono">#{{ a.advertiserId }}</div><div v-if="a.planName || a.materialName" style="font-size:10px;color:var(--tk-info);margin-top:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ a.planName || "" }}{{ a.planName && a.materialName ? " · " : "" }}{{ a.materialName || "" }}</div></td>',
            '            <td><div style="font-size:11px;color:var(--tk-text2)">{{ LANDING[a.landingType] ? LANDING[a.landingType].label : a.landingType }}</div><div style="font-size:10px;margin-top:2px" :style="{ color: a.bounceMeasurable ? \'var(--tk-ok)\' : \'var(--tk-muted)\' }">{{ a.bounceMeasurable ? "可测" : "不可测" }}</div></td>',
            '            <td style="font-size:10px;color:var(--tk-text3);line-height:1.6">{{ fmtShort(a.startAt) }}<br>{{ fmtShort(a.endAt) }}</td>',
            '            <td><span class="os-tag plain">{{ a.priority }}</span></td>',
            '            <td><div style="font-size:10px;color:var(--tk-text2);line-height:1.55"><span class="os-mono">{{ statOf(a.adId).impressions }}</span> 曝光 · <span class="os-mono">{{ statOf(a.adId).clicks }}</span> 点击</div><div style="font-size:10px;color:var(--tk-text3);line-height:1.55"><span class="os-mono">{{ statOf(a.adId).impressions > 0 ? pct(statOf(a.adId).ctr, 1) : "—" }}</span> CTR · <span v-if="bounceText(a.adId) === \'不可测\'">跳出不可测</span><span v-else class="os-mono">跳出 {{ bounceText(a.adId) }}</span></div></td>',
            '            <td style="text-align:right;white-space:nowrap">',
            '              <span style="font-size:11px;color:var(--tk-brand);cursor:pointer" @click="openStats(a)">数据</span>',
            '              <template v-if="canManage">',
            '                <span v-if="editable(a)" style="font-size:11px;color:var(--tk-info);cursor:pointer;margin-left:8px" @click="openEdit(a)">编辑</span>',
            '                <span v-if="editable(a)" style="font-size:11px;color:var(--tk-text2);cursor:pointer;margin-left:8px" @click="submitReview(a)">提交审核</span>',
            '                <span v-if="a.status === \'ENABLED\'" style="font-size:11px;color:var(--tk-warn);cursor:pointer;margin-left:8px" @click="toggle(a)">停投</span>',
            '                <span v-else-if="approved(a) && a.effectiveStatus !== \'EXPIRED\'" style="font-size:11px;color:var(--tk-ok);cursor:pointer;margin-left:8px" @click="toggle(a)">上架</span>',
            '                <span v-else-if="a.effectiveStatus === \'EXPIRED\'" style="font-size:11px;color:var(--tk-muted);margin-left:8px" title="投放结束时间已过，需调整排期">已过期</span>',
                        '                <span style="font-size:11px;color:var(--tk-bad);cursor:pointer;margin-left:8px" @click="remove(a)">删除</span>',
            '              </template>',
            '              <span v-if="canReview && reviewOf(a) === \'PENDING\'" style="font-size:11px;color:var(--tk-warn);cursor:pointer;margin-left:8px" @click="openReview(a)">审核</span>',
            '            </td>',
            '          </tr>',
            '          <tr v-if="!rows.length"><td colspan="9" class="os-empty">还没有广告，{{ canManage ? "点击右上角「新建广告」开始投放" : "请联系具备 community:ad:manage 权限的同事创建" }}</td></tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',
            '    <div style="display:flex;align-items:center;gap:10px;padding:11px 18px;border-top:1px solid var(--tk-stroke)">',
            '      <span style="font-size:11px;color:var(--tk-text3)">共 {{ total }} 条 · 第 {{ pageNum }} / {{ totalPages }} 页 · 数据刷新于 {{ refreshedAt }}</span><span style="flex:1"></span>',
            '      <button class="os-btn sm" :disabled="pageNum <= 1" @click="pageNum--; load()">上一页</button><button class="os-btn sm" :disabled="pageNum >= totalPages" @click="pageNum++; load()">下一页</button>',
            '    </div>',
            '  </div>',

            // ---------------- C13 投放计划 ----------------
            '  <div v-show="activeTab === \'plans\'" class="os-panel" style="flex:1;min-height:0;display:flex;flex-direction:column">',
            '    <div class="os-panel-h" style="flex-wrap:wrap;row-gap:8px">',
            '      <h3>投放计划<span class="hint" style="margin-left:8px">一个计划可挂多条直投广告，统一排期与状态</span></h3>',
            '      <span style="flex:1"></span>',
            '      <select class="os-select" style="width:118px" v-model="planStatusFilter" @change="loadPlans(1)"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select>',
            '      <div class="srch" style="width:190px;background:var(--tk-input)"><el-icon :size="13"><Search /></el-icon><input v-model.trim="planKeyword" placeholder="搜索计划名称" @keyup.enter="loadPlans(1)" /></div>',
            '      <button class="os-btn sm" @click="loadPlans(planPage)">刷新</button>',
            '      <button v-if="canManage" class="os-btn primary sm" @click="openPlan()">新建计划</button>',
            '    </div>',
            '    <div style="overflow:auto;flex:1">',
            '      <table style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr><th>计划</th><th style="width:150px">广告主</th><th style="width:190px">排期</th><th style="width:120px">预算</th><th style="width:70px">广告数</th><th style="width:88px">状态</th><th style="width:150px;text-align:right">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-if="planLoading"><td colspan="7" class="os-empty">加载中…</td></tr>',
            '          <tr v-else-if="planError"><td colspan="7" class="os-empty">投放计划加载失败：{{ planError }} <span style="color:var(--tk-info);cursor:pointer;margin-left:6px" @click="loadPlans(planPage)">重试</span></td></tr>',
            '          <tr v-else-if="!plans.length"><td colspan="7" class="os-empty">暂无投放计划，点击右上角「新建计划」创建</td></tr>',
            '          <tr v-for="p in plans" :key="p.planId">',
            '            <td><b style="font-weight:550;color:var(--tk-text)">{{ p.planName }}</b><div style="font-size:10px;color:var(--tk-muted);margin-top:2px">{{ p.objective }} · {{ SLOT[p.slot] || p.slot }}{{ p.remark ? " · " + p.remark : "" }}</div></td>',
            '            <td style="font-size:12px;color:var(--tk-text2)">{{ p.advertiserName || "未关联" }}</td>',
            '            <td style="font-size:11px;color:var(--tk-text3)">{{ fmtShort(p.startAt) }}<br>{{ fmtShort(p.endAt) }}</td>',
            '            <td style="font-size:11px;color:var(--tk-text3)">{{ budgetText(p) }}</td>',
            '            <td>{{ p.adCount }}</td>',
            '            <td><span class="os-tag" :class="planTone(p)">{{ planStatusText(p) }}</span></td>',
            '            <td style="text-align:right;white-space:nowrap">',
            '              <span style="font-size:11px;color:var(--tk-info);cursor:pointer" @click="openPlan(p)">编辑</span>',
            '              <span style="font-size:11px;margin-left:8px;cursor:pointer" :style="{ color: p.status === \'ENABLED\' ? \'var(--tk-warn)\' : \'var(--tk-ok)\' }" @click="togglePlanStatus(p)">{{ p.status === "ENABLED" ? "停用" : "启用" }}</span>',
            '              <span style="font-size:11px;margin-left:8px;color:var(--tk-bad);cursor:pointer" @click="removePlan(p)">删除</span>',
            '            </td>',
            '          </tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',
            '    <div style="display:flex;align-items:center;gap:10px;padding:11px 18px;border-top:1px solid var(--tk-stroke)">',
            '      <span style="font-size:11px;color:var(--tk-text3)">共 {{ planTotal }} 个计划 · 第 {{ planPage }} / {{ planTotalPages }} 页</span><span style="flex:1"></span>',
            '      <button class="os-btn sm" :disabled="planPage <= 1" @click="loadPlans(planPage - 1)">上一页</button><button class="os-btn sm" :disabled="planPage >= planTotalPages" @click="loadPlans(planPage + 1)">下一页</button>',
            '    </div>',
            '  </div>',

            // ---------------- C13 素材库 ----------------
            '  <div v-show="activeTab === \'materials\'" class="os-panel" style="flex:1;min-height:0;display:flex;flex-direction:column">',
            '    <div class="os-panel-h" style="flex-wrap:wrap;row-gap:8px">',
            '      <h3>素材库<span class="hint" style="margin-left:8px">图片/视频/文本素材独立审核；直投广告只能引用审核通过的素材</span></h3>',
            '      <span style="flex:1"></span>',
            '      <select class="os-select" style="width:120px" v-model="materialReviewFilter" @change="loadMaterials(1)"><option value="">全部审核</option><option value="PENDING">待审核</option><option value="APPROVED">已通过</option><option value="REJECTED">已驳回</option></select>',
            '      <select class="os-select" style="width:112px" v-model="materialTypeFilter" @change="loadMaterials(1)"><option value="">全部类型</option><option value="IMAGE">图片</option><option value="VIDEO">视频</option><option value="TEXT">文本</option></select>',
            '      <div class="srch" style="width:170px;background:var(--tk-input)"><el-icon :size="13"><Search /></el-icon><input v-model.trim="materialKeyword" placeholder="搜索素材/标题" @keyup.enter="loadMaterials(1)" /></div>',
            '      <button class="os-btn sm" @click="loadMaterials(materialPage)">刷新</button>',
            '      <button v-if="canManage" class="os-btn primary sm" @click="openMaterial()">新建素材</button>',
            '    </div>',
            '    <div style="overflow:auto;flex:1">',
            '      <table style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr><th style="width:64px">预览</th><th>素材</th><th style="width:84px">类型</th><th style="width:96px">审核</th><th style="width:70px">引用</th><th style="width:84px">状态</th><th style="width:170px;text-align:right">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-if="materialLoading"><td colspan="7" class="os-empty">加载中…</td></tr>',
            '          <tr v-else-if="materialError"><td colspan="7" class="os-empty">素材加载失败：{{ materialError }} <span style="color:var(--tk-info);cursor:pointer;margin-left:6px" @click="loadMaterials(materialPage)">重试</span></td></tr>',
            '          <tr v-else-if="!materials.length"><td colspan="7" class="os-empty">暂无素材，点击右上角「新建素材」上传/登记</td></tr>',
            '          <tr v-for="m in materials" :key="m.materialId">',
            '            <td><img v-if="m.coverUrl || m.fileUrl" :src="m.coverUrl || m.fileUrl" style="width:48px;height:48px;object-fit:cover;border-radius:6px;background:var(--tk-subtle)" @error="$event.target.style.display=\'none\'" /><span v-else style="font-size:11px;color:var(--tk-muted)">—</span></td>',
            '            <td><b style="font-weight:550;color:var(--tk-text)">{{ m.materialName }}</b><div style="font-size:10px;color:var(--tk-muted);margin-top:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ m.title || m.description || m.fileUrl || "—" }}</div></td>',
            '            <td><span class="os-tag">{{ MATERIAL_TYPE[m.materialType] || m.materialType }}</span></td>',
            '            <td><span class="os-tag" :class="reviewTone(m.reviewStatus)" :title="m.reviewReason || \'\'">{{ REVIEW[m.reviewStatus] ? REVIEW[m.reviewStatus].label : m.reviewStatus }}</span></td>',
            '            <td>{{ m.linkedAdCount }}</td>',
            '            <td><span class="os-dot" :class="m.status === \'ENABLED\' ? \'ok\' : \'muted\'" style="margin-right:4px"></span><span style="font-size:11px;color:var(--tk-text3)">{{ m.status === "ENABLED" ? "启用" : "停用" }}</span></td>',
            '            <td style="text-align:right;white-space:nowrap">',
            '              <span style="font-size:11px;color:var(--tk-info);cursor:pointer" @click="openMaterial(m)">编辑</span>',
            '              <span v-if="canReview && m.reviewStatus === \'PENDING\'" style="font-size:11px;color:var(--tk-brand);cursor:pointer;margin-left:8px" @click="openMaterialReview(m)">审核</span>',
            '              <span style="font-size:11px;margin-left:8px;cursor:pointer" :style="{ color: m.status === \'ENABLED\' ? \'var(--tk-warn)\' : \'var(--tk-ok)\' }" @click="toggleMaterialStatus(m)">{{ m.status === "ENABLED" ? "停用" : "启用" }}</span>',
            '              <span style="font-size:11px;margin-left:8px;color:var(--tk-bad);cursor:pointer" @click="removeMaterial(m)">删除</span>',
            '            </td>',
            '          </tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',
            '    <div style="display:flex;align-items:center;gap:10px;padding:11px 18px;border-top:1px solid var(--tk-stroke)">',
            '      <span style="font-size:11px;color:var(--tk-text3)">共 {{ materialTotal }} 个素材 · 第 {{ materialPage }} / {{ materialTotalPages }} 页</span><span style="flex:1"></span>',
            '      <button class="os-btn sm" :disabled="materialPage <= 1" @click="loadMaterials(materialPage - 1)">上一页</button><button class="os-btn sm" :disabled="materialPage >= materialTotalPages" @click="loadMaterials(materialPage + 1)">下一页</button>',
            '    </div>',
            '  </div>',

            '  <div v-if="editorOpen" class="cmd-mask" style="padding-top:4vh" @click.self="editorOpen = false">',
            '    <div class="cmd-panel" style="width:700px;max-height:90vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">{{ form.adId ? "编辑广告" : "新建官方直投广告" }}</b><span style="flex:1"></span><span class="os-tag info">保存为草稿</span><span style="cursor:pointer;color:var(--tk-muted);margin-left:10px" @click="editorOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>广告标题 <em>*</em></label><input class="os-input" v-model.trim="form.title" maxlength="64" placeholder="面向学生的一句话标题" /></div>',
            '          <div class="os-field"><label>广告主</label><el-select v-model="form.advertiserId" filterable remote clearable reserve-keyword placeholder="不关联广告主（可搜索名称/联系人）" style="width:100%" :remote-method="searchAdvertisers" :loading="advertiserLoading" @change="onAdvertiserChange">',
            '            <el-option v-for="adv in advertisers" :key="adv.advertiserId" :label="adv.name + (adv.status === \'DISABLED\' ? \'（停用）\' : \'\')" :value="String(adv.advertiserId)"></el-option>',
            '          </el-select></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>关联投放计划（可空）</label><select class="os-select" v-model="form.planId"><option value="">不关联计划</option><option v-for="p in adPlanOptions" :key="p.planId" :value="String(p.planId)">{{ p.planName }}（{{ p.status }}）</option></select></div>',
            '          <div class="os-field"><label>关联素材（可空，仅审核通过）</label><select class="os-select" v-model="form.materialId"><option value="">不关联素材</option><option v-for="m in adMaterialOptions" :key="m.materialId" :value="String(m.materialId)">{{ m.materialName }}（{{ MATERIAL_TYPE[m.materialType] || m.materialType }}）</option></select></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>品牌 / 投放方 <em>*</em></label><input class="os-input" v-model.trim="form.brandName" maxlength="64" placeholder="例如：学校后勤集团" /></div>',
            '          <div class="os-field"><label>投放位置</label><select class="os-select" v-model="form.slot"><option v-for="(l,k) in SLOT" :key="k" :value="k">{{ l }}</option></select></div>',
            '        </div>',
            '        <div class="os-field"><label>广告描述</label><textarea class="os-textarea" v-model.trim="form.description" maxlength="255" placeholder="展示在信息流广告卡内的补充说明，建议 20-60 字"></textarea></div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>封面图 URL</label><input class="os-input" v-model.trim="form.coverUrl" maxlength="512" placeholder="https://..." /></div>',
            '          <div class="os-field"><label>点击跳转 URL <em>*</em></label><input class="os-input" v-model.trim="form.targetUrl" maxlength="512" placeholder="https://..." /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>落地类型 <em>*</em></label><select class="os-select" v-model="form.landingType"><option v-for="(l,k) in LANDING" :key="k" :value="k">{{ l.label }} · {{ l.hint }}</option></select></div>',
            '          <div class="os-field"><label>限定分区</label><select class="os-select" v-model="form.sectionId"><option value="">全部分区</option><option v-for="s in sections" :key="s.sectionId" :value="s.sectionId">{{ s.sectionName }}</option></select></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>优先级（越大越靠前）</label><input class="os-input" type="number" v-model.number="form.priority" /></div>',
            '          <div class="os-field"><label>跳出阈值 N（秒）</label><input class="os-input" type="number" min="1" max="60" v-model.number="form.bounceThresholdSeconds" /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>开始时间 <em>*</em></label><input class="os-input" type="datetime-local" v-model="form.startAt" /></div>',
            '          <div class="os-field"><label>结束时间 <em>*</em></label><input class="os-input" type="datetime-local" v-model="form.endAt" /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>CTA 文案</label><input class="os-input" v-model.trim="form.ctaText" maxlength="24" placeholder="了解详情" /></div>',
            '          <div class="os-field"><label>推广标识</label><input class="os-input" v-model.trim="form.sponsoredLabel" maxlength="16" placeholder="推广" /></div>',
            '        </div>',
            '        <div class="os-note" :class="{ warn: !LANDING[form.landingType].measurable }"><b>测量说明</b> {{ LANDING[form.landingType].measurable ? "该落地类型在 App 内可采集停留与跳出，统计接口会返回真实数据。" : "该落地类型离开 App，无法采集落地停留与跳出；统计接口会标记为不可测并返回 null，不会显示 0%。" }}</div>',
            '        <div class="os-note"><b>审核说明</b> 保存后广告为草稿，需在列表中「提交审核」；待审核/已通过的广告不可编辑，审核通过后才能上架。{{ form.adId ? "已驳回的广告修改后可重新提交。" : "" }}</div>',
            '        <div style="display:flex;justify-content:flex-end;gap:8px"><button class="os-btn" @click="editorOpen = false">取消</button><button class="os-btn primary" :disabled="submitting" @click="submit">{{ submitting ? "提交中…" : "保存草稿" }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            '  <div v-if="reviewOpen && reviewAd" class="cmd-mask" style="padding-top:16vh" @click.self="reviewOpen = false">',
            '    <div class="cmd-panel" style="width:520px;max-height:70vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">审核广告 · {{ reviewAd.title }}</b><span style="flex:1"></span><span class="os-tag warn">待审核</span><span style="cursor:pointer;color:var(--tk-muted);margin-left:10px" @click="reviewOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-field"><label>审核结论</label><div style="display:flex;gap:8px">',
            '          <button class="os-btn" :class="{ primary: reviewAction === \'APPROVE\' }" style="flex:1;height:38px" @click="reviewAction = \'APPROVE\'">通过</button>',
            '          <button class="os-btn" :class="{ primary: reviewAction === \'REJECT\' }" style="flex:1;height:38px" @click="reviewAction = \'REJECT\'">驳回</button>',
            '        </div></div>',
            '        <div class="os-field"><label>驳回原因 <em v-if="reviewAction === \'REJECT\'">*</em></label><textarea class="os-textarea" v-model.trim="reviewReason" maxlength="200" :placeholder="reviewAction === \'REJECT\' ? \'请写明驳回原因，作者修改后可重新提交\' : \'可选：填写审核备注（通过时不会展示给作者）\'"></textarea></div>',
            '        <div class="os-note"><b>状态流转</b> 通过后广告变为 APPROVED，可在列表中上架；驳回后作者可编辑并重新提交。审核动作会写入审计日志。</div>',
            '        <div style="display:flex;justify-content:flex-end;gap:8px"><button class="os-btn" @click="reviewOpen = false">取消</button><button class="os-btn primary" :disabled="reviewing" @click="submitReviewAction">{{ reviewing ? "提交中…" : (reviewAction === "APPROVE" ? "确认通过" : "确认驳回") }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            // 投放计划编辑弹层
            '  <div v-if="planEditorOpen" class="cmd-mask" style="padding-top:6vh" @click.self="planEditorOpen = false">',
            '    <div class="cmd-panel" style="width:600px;max-height:84vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">{{ planForm.planId ? "编辑投放计划" : "新建投放计划" }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="planEditorOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-field"><label>计划名称 <em>*</em></label><input class="os-input" v-model.trim="planForm.planName" maxlength="100" placeholder="例如：开学季校园服务推广" /></div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>广告主</label><el-select v-model="planForm.advertiserId" filterable remote clearable reserve-keyword placeholder="不关联广告主（可搜索）" style="width:100%" :remote-method="searchAdvertisers" :loading="advertiserLoading">',
            '            <el-option v-for="adv in advertisers" :key="adv.advertiserId" :label="adv.name" :value="String(adv.advertiserId)"></el-option>',
            '          </el-select></div>',
            '          <div class="os-field"><label>投放目标</label><select class="os-select" v-model="planForm.objective"><option value="AWARENESS">品牌曝光</option><option value="TRAFFIC">引导访问</option><option value="CONVERSION">转化</option></select></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>广告位</label><select class="os-select" v-model="planForm.slot"><option v-for="(label, k) in SLOT" :key="k" :value="k">{{ label }}</option></select></div>',
            '          <div class="os-field"><label>备注</label><input class="os-input" v-model.trim="planForm.remark" maxlength="500" placeholder="排期说明/投放诉求" /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>开始时间 <em>*</em></label><input class="os-input" type="datetime-local" v-model="planForm.startAt" /></div>',
            '          <div class="os-field"><label>结束时间 <em>*</em></label><input class="os-input" type="datetime-local" v-model="planForm.endAt" /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>总预算（分，可空）</label><input class="os-input" type="number" min="0" v-model.number="planForm.budgetCents" placeholder="例如 500000 = 5000 元" /></div>',
            '          <div class="os-field"><label>日预算（分，可空）</label><input class="os-input" type="number" min="0" v-model.number="planForm.dailyBudgetCents" /></div>',
            '        </div>',
            '        <div class="os-note"><b>说明</b> 计划只做排期/预算/状态管理，不产生曝光或点击数据；实际效果以直投广告的 community_ad_event 统计为准，未接入真实投放渠道时不会伪造指标。</div>',
            '        <div style="display:flex;justify-content:flex-end;gap:8px"><button class="os-btn" @click="planEditorOpen = false">取消</button><button class="os-btn primary" :disabled="planSubmitting" @click="submitPlan">{{ planSubmitting ? "保存中…" : "保存计划" }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            // 素材编辑弹层
            '  <div v-if="materialEditorOpen" class="cmd-mask" style="padding-top:6vh" @click.self="materialEditorOpen = false">',
            '    <div class="cmd-panel" style="width:620px;max-height:84vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">{{ materialForm.materialId ? "编辑广告素材" : "新建广告素材" }}</b><span style="flex:1"></span><span class="os-tag warn">保存后进入待审核</span><span style="cursor:pointer;color:var(--tk-muted);margin-left:10px" @click="materialEditorOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>素材名称 <em>*</em></label><input class="os-input" v-model.trim="materialForm.materialName" maxlength="100" placeholder="例如：开学季主视觉-横版" /></div>',
            '          <div class="os-field"><label>素材类型 <em>*</em></label><select class="os-select" v-model="materialForm.materialType"><option value="IMAGE">图片</option><option value="VIDEO">视频</option><option value="TEXT">文本</option></select></div>',
            '        </div>',
            '        <div class="os-field" v-if="materialForm.materialType !== \'TEXT\'"><label>素材文件地址 <em>*</em></label><input class="os-input" v-model.trim="materialForm.fileUrl" maxlength="512" placeholder="https://.../material.jpg（图片/视频）" /></div>',
            '        <div class="os-field" v-if="materialForm.materialType !== \'TEXT\'"><label>封面地址（视频可选）</label><input class="os-input" v-model.trim="materialForm.coverUrl" maxlength="512" placeholder="https://.../cover.jpg" /></div>',
            '        <div class="os-field"><label>素材标题</label><input class="os-input" v-model.trim="materialForm.title" maxlength="100" placeholder="文本素材必填标题或正文" /></div>',
            '        <div class="os-field"><label>描述 / 文本正文</label><textarea class="os-textarea" v-model.trim="materialForm.description" maxlength="500" rows="4" placeholder="文本素材正文，或图片/视频的投放说明"></textarea></div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>宽（像素）</label><input class="os-input" type="number" min="0" v-model.number="materialForm.width" /></div>',
            '          <div class="os-field"><label>高（像素）</label><input class="os-input" type="number" min="0" v-model.number="materialForm.height" /></div>',
            '        </div>',
            '        <div class="os-note"><b>审核链路</b> 新建/编辑后素材回到 PENDING；审核通过（APPROVED）后才能被直投广告引用；驳回必须填写原因，广告列表只展示可测数据，不伪造投放效果。</div>',
            '        <div style="display:flex;justify-content:flex-end;gap:8px"><button class="os-btn" @click="materialEditorOpen = false">取消</button><button class="os-btn primary" :disabled="materialSubmitting" @click="submitMaterial">{{ materialSubmitting ? "保存中…" : "保存素材" }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            // 素材审核弹层
            '  <div v-if="materialReviewOpen && materialReviewTarget" class="cmd-mask" style="padding-top:16vh" @click.self="materialReviewOpen = false">',
            '    <div class="cmd-panel" style="width:520px">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">审核素材 · {{ materialReviewTarget.materialName }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="materialReviewOpen = false">✕</span></div>',
            '      <div style="padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-field"><label>审核结论 <em>*</em></label><select class="os-select" v-model="materialReviewAction"><option value="APPROVE">通过（可用于投放）</option><option value="REJECT">驳回（需填写原因）</option></select></div>',
            '        <div class="os-field" v-if="materialReviewAction === \'REJECT\'"><label>驳回原因 <em>*</em></label><textarea class="os-textarea" v-model.trim="materialReviewReason" maxlength="255" rows="3" placeholder="例如：素材含绝对化用语，请修改文案后重新提交"></textarea></div>',
            '        <div class="os-note">审核动作会写入审计日志；通过后素材仍可被停用，已引用的广告不受影响但新广告无法再选择停用素材。</div>',
            '        <div style="display:flex;justify-content:flex-end;gap:8px"><button class="os-btn" @click="materialReviewOpen = false">取消</button><button class="os-btn primary" :disabled="materialReviewing" @click="submitMaterialReview">{{ materialReviewing ? "提交中…" : "确认审核" }}</button></div>',
            '      </div>',
            '    </div>',
            '  </div>',

            '  <div v-if="statsOpen && statsAd" class="cmd-mask" style="padding-top:5vh" @click.self="statsOpen = false">',
            '    <div class="cmd-panel" style="width:760px;max-height:88vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">投放数据 · {{ statsAd.title }}</b><span style="flex:1"></span><select class="os-select" style="width:118px" v-model.number="statsThreshold" @change="loadStatsFor(statsAd.adId, true)"><option :value="3">阈值 3 秒</option><option :value="5">阈值 5 秒</option><option :value="8">阈值 8 秒</option><option :value="10">阈值 10 秒</option></select><span style="cursor:pointer;color:var(--tk-muted);margin-left:10px" @click="statsOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:16px">',
            '        <div v-if="stats" class="os-grid" style="grid-template-columns:repeat(4,1fr)">',
            '          <div class="os-kpi" style="box-shadow:none"><div class="k-label">曝光</div><div class="k-val" style="font-size:24px">{{ stats.impressions }}</div></div>',
            '          <div class="os-kpi" style="box-shadow:none"><div class="k-label">点击</div><div class="k-val" style="font-size:24px">{{ stats.clicks }}</div></div>',
            '          <div class="os-kpi" style="box-shadow:none"><div class="k-label">CTR</div><div class="k-val" style="font-size:24px">{{ stats.impressions > 0 ? pct(stats.ctr, 1) : "—" }}</div></div>',
            '          <div class="os-kpi" style="box-shadow:none"><div class="k-label">跳出率</div><div class="k-val" :style="{ fontSize: stats.bounceRate == null ? \'14px\' : \'24px\' }">{{ stats.bounceMeasurable ? (stats.bounceRate == null ? "暂无数据" : pct(stats.bounceRate, 1)) : "不可测" }}</div><div class="k-foot">{{ stats.bounceMeasurable ? (stats.bounced + " / " + stats.measurableLandings + " 次可测落地") : "全部落地类型不可测" }}</div></div>',
            '        </div>',
            '        <div v-if="stats" style="display:grid;grid-template-columns:repeat(3,1fr);gap:12px">',
            '          <div class="os-panel" style="box-shadow:none"><div class="os-panel-b" style="padding:13px 15px"><div style="font-size:10px;color:var(--tk-muted)">平均停留</div><b style="font-size:16px;color:var(--tk-text)">{{ stats.avgDwellMs == null ? (stats.bounceMeasurable ? "暂无数据" : "不可测") : (stats.avgDwellMs / 1000).toFixed(1) + " s" }}</b></div></div>',
            '          <div class="os-panel" style="box-shadow:none"><div class="os-panel-b" style="padding:13px 15px"><div style="font-size:10px;color:var(--tk-muted)">不可测点击</div><b style="font-size:16px;color:var(--tk-text)">{{ stats.unmeasurableClicks }}</b></div></div>',
            '          <div class="os-panel" style="box-shadow:none"><div class="os-panel-b" style="padding:13px 15px"><div style="font-size:10px;color:var(--tk-muted)">跳出阈值</div><b style="font-size:16px;color:var(--tk-text)">{{ stats.bounceThresholdSeconds }} s</b></div></div>',
            '        </div>',
            '        <div v-if="stats" class="os-panel" style="box-shadow:none">',
            '          <div class="os-panel-h"><h3>落地类型拆分</h3><span class="hint">外部浏览器 / 深链无真实跳出数据</span></div>',
            '          <table style="width:100%;border-collapse:collapse"><thead><tr><th>落地类型</th><th>点击</th><th>可测落地</th><th>跳出</th><th>跳出率</th><th>平均停留</th></tr></thead><tbody>',
            '            <tr v-for="b in stats.landingBreakdown" :key="b.landingType"><td>{{ LANDING[b.landingType] ? LANDING[b.landingType].label : b.landingType }}</td><td class="os-mono">{{ b.clicks }}</td><td class="os-mono">{{ b.measurable ? b.measurableLandings : "—" }}</td><td class="os-mono">{{ b.measurable ? b.bounced : "—" }}</td><td><span v-if="!b.measurable" class="os-tag" style="font-size:10px">不可测</span><span v-else-if="b.bounceRate == null" style="color:var(--tk-muted);font-size:10px">暂无</span><span v-else class="os-mono">{{ pct(b.bounceRate, 1) }}</span></td><td class="os-mono">{{ b.measurable ? (b.avgDwellMs == null ? "—" : (b.avgDwellMs/1000).toFixed(1)+"s") : "不可测" }}</td></tr>',
            '            <tr v-if="!stats.landingBreakdown.length"><td colspan="6" class="os-empty">暂无数据</td></tr>',
            '          </tbody></table>',
            '        </div>',
            '        <div v-if="stats && !stats.bounceMeasurable" class="os-note warn"><b>该广告当前全部落地类型均不可测</b> 系统只记录点击，不能计算跳出率或停留时长。这是测量边界，不代表 0% 跳出。</div>',
            '        <div v-else-if="stats && stats.measurableLandings === 0" class="os-note"><b>暂无可测落地数据</b> 已产生 {{ stats.clicks }} 次点击，但还没有进入可测落地页的加载事件，因此不计算跳出率。</div>',
            '        <div v-if="!stats" class="os-empty">暂无统计数据</div>',
            '      </div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var rows = Vue.ref([]);
            var total = Vue.ref(0);
            var pendingReview = Vue.ref(0);
            var pageNum = Vue.ref(1);
            var pageSize = 10;
            var totalPages = Vue.computed(function () { return Math.max(1, Math.ceil(total.value / pageSize)); });
            var statusFilter = Vue.ref('');
            var reviewFilter = Vue.ref('');
            var slotFilter = Vue.ref('');
            var advertiserFilter = Vue.ref('');
            var keyword = Vue.ref('');
            var statsMap = Vue.reactive({});
            var refreshedAt = Vue.ref('—');
            var sections = Vue.ref([]);
            var advertisers = Vue.ref([]);
            var advertiserLoading = Vue.ref(false);
            var advertiserSearchSeq = 0;

            var editorOpen = Vue.ref(false);
            var submitting = Vue.ref(false);
            var form = Vue.reactive(blank());
            var activeTab = Vue.ref('ads');
            var plans = Vue.ref([]);
            var planTotal = Vue.ref(0);
            var planPage = Vue.ref(1);
            var planPageSize = 10;
            var planLoading = Vue.ref(false);
            var planError = Vue.ref('');
            var planKeyword = Vue.ref('');
            var planStatusFilter = Vue.ref('');
            var planEditorOpen = Vue.ref(false);
            var planSubmitting = Vue.ref(false);
            var planForm = Vue.reactive(blankPlan());
            var planTotalPages = Vue.computed(function () {
                return Math.max(1, Math.ceil(planTotal.value / planPageSize));
            });
            var materials = Vue.ref([]);
            var materialTotal = Vue.ref(0);
            var materialPage = Vue.ref(1);
            var materialPageSize = 12;
            var materialLoading = Vue.ref(false);
            var materialError = Vue.ref('');
            var materialKeyword = Vue.ref('');
            var materialReviewFilter = Vue.ref('');
            var materialTypeFilter = Vue.ref('');
            var materialEditorOpen = Vue.ref(false);
            var materialSubmitting = Vue.ref(false);
            var materialForm = Vue.reactive(blankMaterial());
            var materialReviewOpen = Vue.ref(false);
            var materialReviewTarget = Vue.ref(null);
            var materialReviewAction = Vue.ref('APPROVE');
            var materialReviewReason = Vue.ref('');
            var materialReviewing = Vue.ref(false);
            var materialTotalPages = Vue.computed(function () {
                return Math.max(1, Math.ceil(materialTotal.value / materialPageSize));
            });
            var adPlanOptions = Vue.ref([]);
            var adMaterialOptions = Vue.ref([]);

            var reviewOpen = Vue.ref(false);
            var reviewAd = Vue.ref(null);
            var reviewAction = Vue.ref('APPROVE');
            var reviewReason = Vue.ref('');
            var reviewing = Vue.ref(false);

            var statsOpen = Vue.ref(false);
            var statsAd = Vue.ref(null);
            var stats = Vue.ref(null);
            var statsThreshold = Vue.ref(5);

            var canManage = Vue.computed(function () {
                var perms = currentPermissions();
                return perms.indexOf('platform:all') >= 0 || perms.indexOf('community:ad:manage') >= 0;
            });
            var canReview = Vue.computed(function () {
                var perms = currentPermissions();
                return perms.indexOf('platform:all') >= 0 || perms.indexOf('community:ad:review') >= 0;
            });

            var activeCount = Vue.computed(function () {
                return rows.value.filter(function (a) { return a.effectiveStatus === 'ENABLED'; }).length;
            });
            var sum = Vue.computed(function () {
                var out = { impressions: 0, clicks: 0, ctr: null, measurableLandings: 0, bounced: 0, bounceRate: null, hasMeasurable: false };
                var landed = 0;
                rows.value.forEach(function (a) {
                    var s = statsMap[a.adId];
                    if (!s) return;
                    out.impressions += Number(s.impressions || 0);
                    out.clicks += Number(s.clicks || 0);
                    out.measurableLandings += Number(s.measurableLandings || 0);
                    out.bounced += Number(s.bounced || 0);
                    landed += Number(s.measurableLandings || 0);
                    out.hasMeasurable = out.hasMeasurable || !!s.bounceMeasurable;
                });
                out.ctr = out.impressions > 0 ? +(out.clicks / out.impressions * 100).toFixed(2) : null;
                out.bounceRate = landed > 0 ? +(out.bounced / landed * 100).toFixed(1) : null;
                out.bounceText = !out.hasMeasurable ? '不可测' : (out.bounceRate == null ? '暂无数据' : out.bounceRate + '%');
                out.bounceFoot = !out.hasMeasurable ? '当前页广告均不可测' : (landed > 0 ? landed + ' 次可测落地' : '等待可测落地');
                return out;
            });

            function statOf(adId) {
                return statsMap[adId] || { impressions: '—', clicks: '—', ctr: null, bounceRate: null, measurableLandings: 0 };
            }
            function bounceText(adId) {
                var s = statsMap[adId];
                if (!s) return '—';
                if (!s.bounceMeasurable) return '不可测';
                return s.bounceRate == null ? '—' : pct(s.bounceRate, 1);
            }
            function fmtShort(v) { return v ? String(v).replace('T', ' ').slice(5, 16) : '—'; }
            // 后端 ctr / bounceRate 为 0-1 比例，展示时换算为百分比。
            function pct(v, digits) {
                if (v == null) return '—';
                return (Number(v) * 100).toFixed(digits == null ? 1 : digits) + '%';
            }

            function load() {
                var p = new URLSearchParams();
                p.set('pageNum', String(pageNum.value)); p.set('pageSize', String(pageSize));
                if (statusFilter.value) p.set('status', statusFilter.value);
                if (reviewFilter.value) p.set('reviewStatus', reviewFilter.value);
                if (slotFilter.value) p.set('slot', slotFilter.value);
                if (advertiserFilter.value) p.set('advertiserId', advertiserFilter.value);
                if (keyword.value) p.set('keyword', keyword.value);
                api.get('/api/admin/community/direct-ads?' + p.toString())
                    .then(function (data) {
                        rows.value = (data && data.records) || [];
                        total.value = (data && data.total) || 0;
                        refreshedAt.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
                        rows.value.forEach(function (a) { loadStatsFor(a.adId, false); });
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '广告列表加载失败'); });
                loadPendingCount();
            }
            function loadPendingCount() {
                api.get('/api/admin/community/direct-ads?pageNum=1&pageSize=1&reviewStatus=PENDING')
                    .then(function (data) { pendingReview.value = (data && data.total) || 0; })
                    .catch(function () { pendingReview.value = 0; });
            }
            function loadStatsFor(adId, force) {
                if (!force && statsMap[adId]) return Promise.resolve(statsMap[adId]);
                return api.get('/api/admin/community/direct-ads/' + adId + '/stats?bounceThresholdSeconds=' + statsThreshold.value)
                    .then(function (s) {
                        statsMap[adId] = s;
                        if (statsAd.value && String(statsAd.value.adId) === String(adId)) stats.value = s;
                        return s;
                    })
                    .catch(function () { return null; });
            }
            function loadSections() {
                api.get('/api/admin/community/sections').then(function (data) { sections.value = data || []; }).catch(function () {});
            }
            // 广告主数量可能超过 50：下拉改用远程搜索，已选/已过滤项合并保留，避免搜索后失去显示名。
            function mergeAdvertisers(records, replace) {
                var map = {};
                var previous = [];
                advertisers.value.forEach(function (adv) {
                    map[String(adv.advertiserId)] = adv;
                    previous.push(adv);
                });
                if (replace) map = {};
                records.forEach(function (adv) { if (adv && adv.advertiserId != null) map[String(adv.advertiserId)] = adv; });
                var selected = [advertiserFilter.value, form.advertiserId, planForm.advertiserId];
                var kept = [];
                selected.forEach(function (id) {
                    if (id === null || id === undefined || id === '') return;
                    var found = map[String(id)];
                    if (!found) {
                        found = previous.find(function (adv) { return String(adv.advertiserId) === String(id); });
                    }
                    if (found && kept.every(function (adv) { return String(adv.advertiserId) !== String(id); })) kept.push(found);
                });
                Object.keys(map).forEach(function (id) {
                    if (kept.every(function (adv) { return String(adv.advertiserId) !== id; })) kept.push(map[id]);
                });
                advertisers.value = kept.slice(0, 100);
            }
            function loadAdvertisers(keyword) {
                var seq = ++advertiserSearchSeq;
                advertiserLoading.value = true;
                var p = new URLSearchParams();
                p.set('pageNum', '1');
                p.set('pageSize', '30');
                if (keyword) p.set('keyword', keyword);
                api.get('/api/admin/community/advertisers?' + p.toString())
                    .then(function (data) {
                        if (seq !== advertiserSearchSeq) return; // 忽略过期搜索结果
                        mergeAdvertisers((data && data.records) || [], Boolean(keyword));
                    })
                    .catch(function () { if (seq === advertiserSearchSeq) advertisers.value = advertisers.value || []; })
                    .finally(function () { if (seq === advertiserSearchSeq) advertiserLoading.value = false; });
            }
            function searchAdvertisers(query) {
                loadAdvertisers((query || '').trim());
            }
            function ensureAdvertiserOption(advertiserId) {
                if (advertiserId === null || advertiserId === undefined || advertiserId === '') return;
                var exists = advertisers.value.some(function (adv) {
                    return String(adv.advertiserId) === String(advertiserId);
                });
                if (exists) return;
                api.get('/api/admin/community/advertisers/' + advertiserId)
                    .then(function (adv) { if (adv) mergeAdvertisers([adv]); })
                    .catch(function () {});
            }
            function switchTab(tab) {
                activeTab.value = tab;
                if (tab === 'plans' && !plans.value.length) loadPlans(1);
                if (tab === 'materials' && !materials.value.length) loadMaterials(1);
            }
            function loadPlans(page) {
                planPage.value = page || 1;
                planLoading.value = true;
                planError.value = '';
                var p = new URLSearchParams();
                p.set('pageNum', String(planPage.value));
                p.set('pageSize', String(planPageSize));
                if (planStatusFilter.value) p.set('status', planStatusFilter.value);
                if (planKeyword.value) p.set('keyword', planKeyword.value);
                api.get('/api/admin/community/ad-plans?' + p.toString())
                    .then(function (data) {
                        plans.value = (data && data.records) || [];
                        planTotal.value = (data && data.total) || 0;
                    })
                    .catch(function (error) {
                        plans.value = [];
                        planTotal.value = 0;
                        planError.value = error.message || '加载失败';
                    })
                    .finally(function () { planLoading.value = false; });
            }
            function openPlan(plan) {
                Object.assign(planForm, blankPlan());
                if (plan) {
                    planForm.planId = plan.planId;
                    planForm.planName = plan.planName;
                    planForm.advertiserId = plan.advertiserId == null ? '' : String(plan.advertiserId);
                    ensureAdvertiserOption(planForm.advertiserId);
                    planForm.objective = plan.objective || 'AWARENESS';
                    planForm.slot = plan.slot || 'FEED';
                    planForm.startAt = String(plan.startAt || '').slice(0, 16);
                    planForm.endAt = String(plan.endAt || '').slice(0, 16);
                    planForm.budgetCents = plan.budgetCents;
                    planForm.dailyBudgetCents = plan.dailyBudgetCents;
                    planForm.remark = plan.remark || '';
                }
                planEditorOpen.value = true;
            }
            function submitPlan() {
                if (!canManage.value) { El.ElMessage.warning('当前账号没有广告管理权限'); return; }
                if (!planForm.planName) { El.ElMessage.warning('请填写计划名称'); return; }
                if (!planForm.startAt || !planForm.endAt) { El.ElMessage.warning('请选择计划开始与结束时间'); return; }
                if (planForm.endAt <= planForm.startAt) { El.ElMessage.warning('结束时间必须晚于开始时间'); return; }
                var payload = {
                    planName: planForm.planName,
                    advertiserId: planForm.advertiserId === '' ? null : Number(planForm.advertiserId),
                    objective: planForm.objective, slot: planForm.slot,
                    startAt: planForm.startAt + ':00', endAt: planForm.endAt + ':00',
                    budgetCents: planForm.budgetCents || null,
                    dailyBudgetCents: planForm.dailyBudgetCents || null,
                    remark: planForm.remark || null
                };
                planSubmitting.value = true;
                var req = planForm.planId
                    ? api.post('/api/admin/community/ad-plans/' + planForm.planId, payload)
                    : api.post('/api/admin/community/ad-plans', payload);
                req.then(function () {
                    El.ElMessage.success(planForm.planId ? '计划已更新' : '计划已创建（草稿）');
                    planEditorOpen.value = false;
                    loadPlans(planPage.value);
                    loadAdOptions();
                }).catch(function (error) { El.ElMessage.error(error.message || '保存失败'); })
                    .finally(function () { planSubmitting.value = false; });
            }
            function togglePlanStatus(plan) {
                var next = plan.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
                api.post('/api/admin/community/ad-plans/' + plan.planId + '/status', { status: next })
                    .then(function () {
                        El.ElMessage.success('计划已' + (next === 'ENABLED' ? '启用' : '停用'));
                        loadPlans(planPage.value);
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '状态更新失败'); });
            }
            function removePlan(plan) {
                El.ElMessageBox.confirm('确认删除计划「' + plan.planName + '」？计划下仍有广告时会被拒绝。',
                    '删除计划', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
                    .then(function () { return api.delete('/api/admin/community/ad-plans/' + plan.planId); })
                    .then(function () {
                        El.ElMessage.success('计划已删除');
                        loadPlans(planPage.value);
                        loadAdOptions();
                    })
                    .catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }
            function loadMaterials(page) {
                materialPage.value = page || 1;
                materialLoading.value = true;
                materialError.value = '';
                var p = new URLSearchParams();
                p.set('pageNum', String(materialPage.value));
                p.set('pageSize', String(materialPageSize));
                if (materialReviewFilter.value) p.set('reviewStatus', materialReviewFilter.value);
                if (materialTypeFilter.value) p.set('materialType', materialTypeFilter.value);
                if (materialKeyword.value) p.set('keyword', materialKeyword.value);
                api.get('/api/admin/community/ad-materials?' + p.toString())
                    .then(function (data) {
                        materials.value = (data && data.records) || [];
                        materialTotal.value = (data && data.total) || 0;
                    })
                    .catch(function (error) {
                        materials.value = [];
                        materialTotal.value = 0;
                        materialError.value = error.message || '加载失败';
                    })
                    .finally(function () { materialLoading.value = false; });
            }
            function openMaterial(material) {
                Object.assign(materialForm, blankMaterial());
                if (material) {
                    materialForm.materialId = material.materialId;
                    materialForm.materialName = material.materialName;
                    materialForm.materialType = material.materialType || 'IMAGE';
                    materialForm.fileUrl = material.fileUrl || '';
                    materialForm.coverUrl = material.coverUrl || '';
                    materialForm.title = material.title || '';
                    materialForm.description = material.description || '';
                    materialForm.width = material.width;
                    materialForm.height = material.height;
                }
                materialEditorOpen.value = true;
            }
            function submitMaterial() {
                if (!canManage.value) { El.ElMessage.warning('当前账号没有广告管理权限'); return; }
                if (!materialForm.materialName) { El.ElMessage.warning('请填写素材名称'); return; }
                if (materialForm.materialType !== 'TEXT' && !materialForm.fileUrl) {
                    El.ElMessage.warning('图片/视频素材必须填写文件地址'); return;
                }
                if (materialForm.materialType === 'TEXT' && !materialForm.title && !materialForm.description) {
                    El.ElMessage.warning('文本素材需填写标题或正文'); return;
                }
                var payload = {
                    materialName: materialForm.materialName,
                    materialType: materialForm.materialType,
                    fileUrl: materialForm.fileUrl || null,
                    coverUrl: materialForm.coverUrl || null,
                    title: materialForm.title || null,
                    description: materialForm.description || null,
                    width: materialForm.width || null,
                    height: materialForm.height || null
                };
                materialSubmitting.value = true;
                var req = materialForm.materialId
                    ? api.post('/api/admin/community/ad-materials/' + materialForm.materialId, payload)
                    : api.post('/api/admin/community/ad-materials', payload);
                req.then(function () {
                    El.ElMessage.success(materialForm.materialId ? '素材已更新，重新进入待审核' : '素材已创建，等待审核');
                    materialEditorOpen.value = false;
                    loadMaterials(materialPage.value);
                    loadAdOptions();
                }).catch(function (error) { El.ElMessage.error(error.message || '保存失败'); })
                    .finally(function () { materialSubmitting.value = false; });
            }
            function openMaterialReview(material) {
                materialReviewTarget.value = material;
                materialReviewAction.value = 'APPROVE';
                materialReviewReason.value = '';
                materialReviewOpen.value = true;
            }
            function submitMaterialReview() {
                if (materialReviewAction.value === 'REJECT' && !materialReviewReason.value.trim()) {
                    El.ElMessage.warning('驳回必须填写原因'); return;
                }
                materialReviewing.value = true;
                api.post('/api/admin/community/ad-materials/' + materialReviewTarget.value.materialId + '/review', {
                    action: materialReviewAction.value,
                    reason: materialReviewReason.value || null
                }).then(function () {
                    El.ElMessage.success(materialReviewAction.value === 'APPROVE' ? '素材已通过审核' : '素材已驳回');
                    materialReviewOpen.value = false;
                    loadMaterials(materialPage.value);
                    loadAdOptions();
                }).catch(function (error) { El.ElMessage.error(error.message || '审核失败'); })
                    .finally(function () { materialReviewing.value = false; });
            }
            function toggleMaterialStatus(material) {
                var next = material.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
                api.post('/api/admin/community/ad-materials/' + material.materialId + '/status', { status: next })
                    .then(function () {
                        El.ElMessage.success('素材已' + (next === 'ENABLED' ? '启用' : '停用'));
                        loadMaterials(materialPage.value);
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '状态更新失败'); });
            }
            function removeMaterial(material) {
                El.ElMessageBox.confirm('确认删除素材「' + material.materialName + '」？被广告引用时会被拒绝。',
                    '删除素材', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
                    .then(function () { return api.delete('/api/admin/community/ad-materials/' + material.materialId); })
                    .then(function () {
                        El.ElMessage.success('素材已删除');
                        loadMaterials(materialPage.value);
                        loadAdOptions();
                    })
                    .catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }
            function loadAdOptions() {
                api.get('/api/admin/community/ad-plans?pageNum=1&pageSize=100')
                    .then(function (data) { adPlanOptions.value = (data && data.records) || []; })
                    .catch(function () { adPlanOptions.value = []; });
                api.get('/api/admin/community/ad-materials?pageNum=1&pageSize=100&reviewStatus=APPROVED')
                    .then(function (data) { adMaterialOptions.value = (data && data.records) || []; })
                    .catch(function () { adMaterialOptions.value = []; });
            }
            function planTone(plan) {
                if (plan.effectiveStatus === 'EXPIRED') return 'bad';
                if (plan.status === 'ENABLED') return 'ok';
                if (plan.status === 'DRAFT') return 'muted';
                return 'warn';
            }
            function planStatusText(plan) {
                if (plan.effectiveStatus === 'EXPIRED') return '已过期';
                return { DRAFT: '草稿', ENABLED: '启用', DISABLED: '停用' }[plan.status] || plan.status;
            }
            function reviewTone(status) {
                if (status === 'APPROVED') return 'ok';
                if (status === 'REJECTED') return 'bad';
                return 'warn';
            }
            function budgetText(plan) {
                function money(cents) { return cents == null ? '不限' : '¥' + (cents / 100).toFixed(2); }
                if (plan.budgetCents == null && plan.dailyBudgetCents == null) return '不限';
                return money(plan.budgetCents) + ' / 日 ' + money(plan.dailyBudgetCents);
            }

            function openCreate() {
                if (!canManage.value) { El.ElMessage.warning('当前账号没有广告管理权限'); return; }
                Object.assign(form, blank());
                form.advertiserId = '';
                form.planId = '';
                form.materialId = '';
                editorOpen.value = true;
            }
            function openEdit(a) {
                if (!canManage.value) { El.ElMessage.warning('当前账号没有广告管理权限'); return; }
                if (!editable(a)) {
                    El.ElMessage.warning(reviewOf(a) === 'PENDING' ? '审核中的广告不可编辑，请等待审核结果' : '审核通过的广告不可编辑，请先停投或联系管理员');
                    return;
                }
                Object.assign(form, blank());
                form.adId = a.adId;
                form.advertiserId = a.advertiserId == null ? '' : String(a.advertiserId);
                ensureAdvertiserOption(form.advertiserId);
                form.planId = a.planId == null ? '' : String(a.planId);
                form.materialId = a.materialId == null ? '' : String(a.materialId);
                form.title = a.title;
                form.brandName = a.brandName;
                form.description = a.description || '';
                form.coverUrl = a.coverUrl || '';
                form.targetUrl = a.targetUrl || '';
                form.landingType = a.landingType;
                form.sectionId = a.sectionId || '';
                form.slot = a.slot || 'FEED';
                form.priority = a.priority || 0;
                form.startAt = a.startAt ? String(a.startAt).slice(0, 16) : '';
                form.endAt = a.endAt ? String(a.endAt).slice(0, 16) : '';
                form.bounceThresholdSeconds = a.bounceThresholdSeconds || 5;
                form.ctaText = a.ctaText || '了解详情';
                form.sponsoredLabel = a.sponsoredLabel || '推广';
                editorOpen.value = true;
            }
            function onAdvertiserChange() {
                if (!form.advertiserId) return;
                var found = advertisers.value.filter(function (adv) { return String(adv.advertiserId) === String(form.advertiserId); })[0];
                if (found && !form.brandName) form.brandName = found.name;
            }
            function submit() {
                if (!canManage.value) { El.ElMessage.warning('当前账号没有广告管理权限'); return; }
                if (!form.title || !form.brandName || !form.targetUrl) { El.ElMessage.warning('标题、品牌、跳转 URL 为必填项'); return; }
                if (!form.startAt || !form.endAt) { El.ElMessage.warning('请选择投放开始与结束时间'); return; }
                if (form.endAt <= form.startAt) { El.ElMessage.warning('结束时间必须晚于开始时间'); return; }
                var payload = {
                    title: form.title, brandName: form.brandName, description: form.description,
                    coverUrl: form.coverUrl, targetUrl: form.targetUrl, landingType: form.landingType,
                    advertiserId: form.advertiserId === '' ? null : Number(form.advertiserId),
                    sectionId: form.sectionId === '' ? null : form.sectionId, slot: form.slot,
                    priority: form.priority || 0, startAt: form.startAt + ':00', endAt: form.endAt + ':00',
                    // 保存一律为草稿：上架只能走「审核通过 + /status」链路，避免未审核直接投放。
                    status: 'DRAFT', bounceThresholdSeconds: form.bounceThresholdSeconds || 5,
                    ctaText: form.ctaText, sponsoredLabel: form.sponsoredLabel,
                    planId: form.planId === '' ? null : Number(form.planId),
                    materialId: form.materialId === '' ? null : Number(form.materialId)
                };
                submitting.value = true;
                var req = form.adId
                    ? api.post('/api/admin/community/direct-ads/' + form.adId, payload)
                    : api.post('/api/admin/community/direct-ads', payload);
                req.then(function () {
                    El.ElMessage.success(form.adId ? '广告已更新为草稿内容' : '广告已创建为草稿，请在列表中提交审核');
                    editorOpen.value = false;
                    load();
                }).catch(function (error) { El.ElMessage.error(error.message || '保存失败'); })
                    .finally(function () { submitting.value = false; });
            }
            function submitReview(a) {
                if (!canManage.value) return;
                if (!editable(a)) { El.ElMessage.warning('仅草稿或已驳回的广告可提交审核'); return; }
                El.ElMessageBox.confirm('提交后进入待审核，审核通过前不能上架；待审核期间不可编辑。确定提交「' + a.title + '」？', '提交审核', { type: 'info' })
                    .then(function () { return api.post('/api/admin/community/direct-ads/' + a.adId + '/submit'); })
                    .then(function () { El.ElMessage.success('已提交审核'); load(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '提交失败'); });
            }
            function openReview(a) {
                if (!canReview.value) { El.ElMessage.warning('当前账号没有广告审核权限（community:ad:review）'); return; }
                reviewAd.value = a;
                reviewAction.value = 'APPROVE';
                reviewReason.value = a.reviewReason || '';
                reviewOpen.value = true;
            }
            function submitReviewAction() {
                if (!canReview.value) { El.ElMessage.warning('当前账号没有广告审核权限'); return; }
                if (reviewAction.value === 'REJECT' && !reviewReason.value) { El.ElMessage.warning('驳回必须填写原因'); return; }
                reviewing.value = true;
                api.post('/api/admin/community/direct-ads/' + reviewAd.value.adId + '/review', {
                    action: reviewAction.value,
                    reason: reviewAction.value === 'REJECT' ? reviewReason.value : null
                }).then(function () {
                    El.ElMessage.success(reviewAction.value === 'APPROVE' ? '审核已通过，可上架投放' : '已驳回，作者可修改后重新提交');
                    reviewOpen.value = false;
                    load();
                }).catch(function (error) { El.ElMessage.error(error.message || '审核失败'); })
                    .finally(function () { reviewing.value = false; });
            }
            function toggle(a) {
                if (!canManage.value) { El.ElMessage.warning('当前账号没有广告管理权限'); return; }
                var next = a.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
                if (next === 'ENABLED' && !approved(a)) {
                    El.ElMessage.warning('广告审核通过后才能上架，请先提交审核');
                    return;
                }
                api.post('/api/admin/community/direct-ads/' + a.adId + '/status', { status: next })
                    .then(function () { El.ElMessage.success(next === 'ENABLED' ? '广告已上架' : '广告已停投'); load(); })
                    .catch(function (error) { El.ElMessage.error(error.message || '状态更新失败'); });
            }
            function remove(a) {
                if (!canManage.value) { El.ElMessage.warning('当前账号没有广告管理权限'); return; }
                El.ElMessageBox.confirm('删除后广告将从投放池移除，历史统计数据保留。确定删除「' + a.title + '」？', '删除广告', { type: 'warning' })
                    .then(function () { return api.delete('/api/admin/community/direct-ads/' + a.adId); })
                    .then(function () { El.ElMessage.success('广告已删除'); load(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '删除失败'); });
            }
            function openStats(a) {
                statsAd.value = a;
                stats.value = statsMap[a.adId] || null;
                statsOpen.value = true;
                loadStatsFor(a.adId, true);
            }

            loadSections();
            loadAdvertisers('');
            load();
            return {
                rows: rows, total: total, pendingReview: pendingReview, pageNum: pageNum, totalPages: totalPages,
                statusFilter: statusFilter, reviewFilter: reviewFilter, slotFilter: slotFilter,
                advertiserFilter: advertiserFilter, keyword: keyword, sum: sum, activeCount: activeCount,
                refreshedAt: refreshedAt, statOf: statOf, bounceText: bounceText, pct: pct, fmtShort: fmtShort,
                load: load, sections: sections, advertisers: advertisers,
                advertiserLoading: advertiserLoading, searchAdvertisers: searchAdvertisers, editorOpen: editorOpen,
                form: form, submitting: submitting, openCreate: openCreate, openEdit: openEdit,
                onAdvertiserChange: onAdvertiserChange, submit: submit, submitReview: submitReview,
                reviewOpen: reviewOpen, reviewAd: reviewAd, reviewAction: reviewAction, reviewReason: reviewReason,
                reviewing: reviewing, openReview: openReview, submitReviewAction: submitReviewAction,
                toggle: toggle, remove: remove, statsOpen: statsOpen, statsAd: statsAd, stats: stats,
                statsThreshold: statsThreshold, loadStatsFor: loadStatsFor, openStats: openStats,
                canManage: canManage, canReview: canReview, reviewOf: reviewOf, editable: editable,
                approved: approved, LANDING: LANDING, SLOT: SLOT, STATUS: STATUS,
                STATUS_TONE: STATUS_TONE, REVIEW: REVIEW,
                // C13 投放计划 / 素材库
                activeTab: activeTab, switchTab: switchTab,
                plans: plans, planTotal: planTotal, planPage: planPage, planTotalPages: planTotalPages,
                planLoading: planLoading, planError: planError, planKeyword: planKeyword,
                planStatusFilter: planStatusFilter, planEditorOpen: planEditorOpen,
                planSubmitting: planSubmitting, planForm: planForm,
                loadPlans: loadPlans, openPlan: openPlan, submitPlan: submitPlan,
                togglePlanStatus: togglePlanStatus, removePlan: removePlan,
                planTone: planTone, planStatusText: planStatusText, budgetText: budgetText,
                materials: materials, materialTotal: materialTotal, materialPage: materialPage,
                materialTotalPages: materialTotalPages, materialLoading: materialLoading,
                materialError: materialError, materialKeyword: materialKeyword,
                materialReviewFilter: materialReviewFilter, materialTypeFilter: materialTypeFilter,
                materialEditorOpen: materialEditorOpen, materialSubmitting: materialSubmitting,
                materialForm: materialForm, materialReviewOpen: materialReviewOpen,
                materialReviewTarget: materialReviewTarget, materialReviewAction: materialReviewAction,
                materialReviewReason: materialReviewReason, materialReviewing: materialReviewing,
                loadMaterials: loadMaterials, openMaterial: openMaterial, submitMaterial: submitMaterial,
                openMaterialReview: openMaterialReview, submitMaterialReview: submitMaterialReview,
                toggleMaterialStatus: toggleMaterialStatus, removeMaterial: removeMaterial,
                reviewTone: reviewTone, MATERIAL_TYPE: MATERIAL_TYPE,
                adPlanOptions: adPlanOptions, adMaterialOptions: adMaterialOptions
            };
        }
    };
})();
