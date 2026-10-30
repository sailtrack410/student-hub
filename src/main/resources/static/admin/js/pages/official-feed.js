/* 官方通知：抓取来源配置、手动同步、抓取记录，以及 C08 单篇 URL 导入/预览/编辑/定时/重试/版权确认。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    var ARTICLE_STATUS = {
        PUBLISHED: '已发布', SKIPPED: '已跳过', FAILED: '失败', DRAFT: '草稿', SCHEDULED: '定时发布'
    };

    window.AdminPages.OfficialFeedPage = {
        name: 'OfficialFeedPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="page-h"><div><h1>官方通知</h1><div class="sub">抓取学校官网通知，以官方账号发布到「校园通知」分区；支持单篇 URL 导入、预览、编辑、定时与版权确认</div></div>',
            '    <div class="r"><button class="btn2" @click="reload">刷新</button><button class="btn2" @click="openImport">导入单篇</button><button class="c blue" :disabled="syncingAll" @click="syncAll">{{ syncingAll ? "同步中…" : "同步全部" }}</button></div></div>',

            // 来源
            '  <div class="lcard">',
            '    <div class="cap">抓取来源<span class="cnt" v-if="sources.length">共 {{ sources.length }} 个</span>',
            '      <button class="c blue" style="margin-left:auto" @click="openCreate">新增来源</button></div>',
            '    <div class="tblw"><table class="pk">',
            '      <thead><tr><th style="width:110px">名称</th><th>列表页</th><th style="width:100px">发布分区</th><th style="width:130px">发布主体</th><th style="width:100px">单次上限</th><th style="width:150px">最近同步</th><th style="width:180px">状态</th><th style="width:150px" class="r">操作</th></tr></thead>',
            '      <tbody>',
            '        <tr v-if="!sources.length && !loading"><td colspan="8" style="text-align:center;color:var(--tk-muted);padding:48px 0">暂无抓取来源</td></tr>',
            '        <tr v-for="s in sources" :key="s.id">',
            '          <td style="color:var(--tk-text);font-weight:600">{{ s.sourceName }}</td>',
            '          <td style="color:var(--tk-muted);font-size:12px">{{ s.listUrl }}</td>',
            '          <td>{{ s.sectionName || "-" }}</td>',
            '          <td>{{ s.authorName }}<span style="color:var(--tk-muted)"> · {{ s.authorTitle || "官方" }}</span></td>',
            '          <td>{{ s.maxArticles }} 篇 / {{ s.maxPages }} 页</td>',
            '          <td style="color:var(--tk-text3)">{{ fmt(s.lastFetchAt) }}</td>',
            '          <td><span class="bdg" :class="s.enabled ? \'g\' : \'a\'">{{ s.enabled ? "启用" : "停用" }}</span>',
            '            <div style="white-space:normal;color:var(--tk-muted);font-size:12px;margin-top:4px;line-height:1.4">{{ s.lastStatus || "尚未同步" }}</div></td>',
            '          <td class="r"><span class="op" @click="syncOne(s)">{{ syncingId === s.id ? "同步中…" : "立即同步" }}</span>',
            '            · <span class="op" @click="openEdit(s)">编辑</span></td>',
            '        </tr>',
            '      </tbody></table></div>',
            '  </div>',

            // 抓取记录
            '  <div style="display:flex;flex-direction:column;gap:12px;flex:1;min-height:0">',
            '    <div class="lcard" style="display:flex;flex-direction:column;min-height:0;flex:1">',
            '    <div class="cap">抓取记录<span class="cnt" v-if="articleTotal">共 {{ articleTotal }} 条</span></div>',
            '    <div style="display:flex;gap:8px;flex-wrap:wrap">',
            '      <div class="ipt2" style="width:160px"><select v-model="articleSourceId" @change="loadArticles(1)" style="width:100%"><option value="">全部来源</option><option v-for="s in sources" :key="s.id" :value="String(s.id)">{{ s.sourceName }}</option></select></div>',
            '      <div class="ipt2" style="width:150px"><select v-model="articleStatus" @change="loadArticles(1)" style="width:100%"><option value="">全部状态</option><option value="DRAFT">草稿（导入待发布）</option><option value="SCHEDULED">定时发布</option><option value="PUBLISHED">已发布</option><option value="SKIPPED">已跳过</option><option value="FAILED">失败</option></select></div>',
            '    </div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto"><table class="pk">',
            '      <thead><tr><th>标题</th><th style="width:100px">来源</th><th style="width:70px">类型</th><th style="width:60px">图片</th><th style="width:70px">版权</th><th style="width:86px">状态</th><th style="width:140px">时间</th><th style="width:190px" class="r">操作</th></tr></thead>',
            '      <tbody>',
            '        <tr v-if="!articles.length && !loadingArticles"><td colspan="8" style="text-align:center;color:var(--tk-muted);padding:48px 0">暂无抓取记录</td></tr>',
            '        <tr v-for="a in articles" :key="a.id">',
            '          <td><div style="color:var(--tk-text)">{{ a.title }}</div><div style="color:var(--tk-muted);font-size:12px">{{ a.sourceUrl }}</div>',
            '            <div v-if="a.errorMessage" style="color:var(--tk-bad);font-size:12px">{{ a.errorMessage }}</div></td>',
            '          <td>{{ a.sourceName || "-" }}</td>',
            '          <td>{{ a.postType === "ARTICLE" ? "文章" : (a.postType === "DYNAMIC" ? "动态" : "-") }}</td>',
            '          <td>{{ a.imageCount || 0 }}</td>',
            '          <td><span class="bdg" :class="a.copyrightConfirmed ? \'g\' : \'a\'">{{ a.copyrightConfirmed ? "已确认" : "未确认" }}</span></td>',
            '          <td><span class="bdg" :class="statusTone(a.status)">{{ statusText(a.status) }}</span>',
            '            <div v-if="a.status === \'SCHEDULED\' && a.scheduledAt" style="font-size:10px;color:var(--tk-text3);margin-top:2px">{{ fmt(a.scheduledAt) }}</div></td>',
            '          <td style="color:var(--tk-text3)">{{ fmt(a.fetchedAt) }}</td>',
            '          <td class="r"><span class="op" @click="openPreview(a)">预览/编辑</span>',
            '            <template v-if="a.status === \'DRAFT\' || a.status === \'SCHEDULED\'"> · <span class="op" style="color:var(--tk-ok)" @click="publish(a)">发布</span></template>',
            '            <template v-if="a.status === \'FAILED\'"> · <span class="op" style="color:var(--tk-warn)" @click="retry(a)">重试</span></template>',
            '            <template v-if="a.postId"> · <a :href="\'#/community-posts\'" style="color:var(--tk-info)">帖子</a></template>',
            '          </td>',
            '        </tr>',
            '      </tbody></table></div>',
            '    </div>',
            '    <div class="pfoot"><div class="pgs"><b class="edge" v-if="pageNum > 1" @click="loadArticles(pageNum - 1)">‹</b><b class="on">{{ pageNum }}</b><b class="edge" v-if="articles.length >= pageSize" @click="loadArticles(pageNum + 1)">›</b></div><div class="tot">共 {{ articleTotal }} 条</div></div>',
            '  </div>',

            // 来源弹层
            '  <div class="pmask" :class="{ show: formVisible }" @click.self="formVisible = false">',
            '    <div class="pcard" v-if="form" style="max-width:620px">',
            '      <div class="ph"><b>{{ form.id ? "编辑抓取来源" : "新增抓取来源" }}</b><span class="sp"></span><span class="x" @click="formVisible = false">✕</span></div>',
            '      <div class="fld"><label>来源名称 <em>*</em></label><div class="pin"><input v-model="form.sourceName" maxlength="64" placeholder="如 通知公告" /></div></div>',
            '      <div class="fld"><label>列表页地址 <em>*</em></label><div class="pin"><input v-model="form.listUrl" maxlength="512" placeholder="https://www.mycc.edu.cn/index/tzgg.htm" /></div></div>',
            '      <div style="display:flex;gap:12px">',
            '        <div class="fld" style="flex:1"><label>发布分区 <em>*</em></label><div class="pin"><select v-model.number="form.sectionId" style="width:100%"><option v-for="s in sections" :key="s.sectionId" :value="s.sectionId">{{ s.sectionName }}</option></select></div></div>',
            '        <div class="fld" style="flex:1"><label>发布主体 <em>*</em></label><div class="pin"><select v-model.number="form.authorId" style="width:100%"><option v-for="u in officialUsers" :key="u.userId" :value="u.userId">{{ u.displayName }}（{{ u.officialTitle }}）</option></select></div></div>',
            '      </div>',
            '      <div style="display:flex;gap:12px">',
            '        <div class="fld" style="flex:1"><label>最多翻页</label><div class="pin"><input type="number" min="1" max="5" v-model.number="form.maxPages" /></div></div>',
            '        <div class="fld" style="flex:1"><label>单次最多发布</label><div class="pin"><input type="number" min="1" max="10" v-model.number="form.maxArticles" /></div></div>',
            '        <div class="fld" style="flex:1"><label>最大天数</label><div class="pin"><input type="number" min="1" max="3650" v-model.number="form.maxAgeDays" /></div></div>',
            '      </div>',
            '      <div class="fld"><label>状态</label><div class="pin"><select v-model="form.enabled" style="width:100%"><option :value="true">启用（参与定时同步）</option><option :value="false">停用</option></select></div></div>',
            '      <div class="infobar">仅允许抓取 *.mycc.edu.cn；首次同步从最新文章开始，按「单次最多发布」逐批补齐；超过最大天数的旧通知只记录不发布。</div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="formVisible = false">取消</button><button class="c blue" :disabled="submitting" @click="saveForm">{{ submitting ? "保存中…" : "保存" }}</button></div>',
            '    </div>',
            '  </div>',

            // 单篇导入弹层
            '  <div class="pmask" :class="{ show: importVisible }" @click.self="importVisible = false">',
            '    <div class="pcard" style="max-width:620px">',
            '      <div class="ph"><b>单篇 URL 导入</b><span class="no">C08</span><span class="sp"></span><span class="x" @click="importVisible = false">✕</span></div>',
            '      <div class="psum"><div class="l2">粘贴学校官网文章详情页地址，系统抓取标题/正文/图片后生成草稿，可在预览中编辑再发布。</div></div>',
            '      <div class="fld"><label>文章 URL <em>*</em></label><div class="pin"><input v-model="importForm.url" maxlength="512" placeholder="https://www.mycc.edu.cn/info/xxxx/yyyy.htm" /></div></div>',
            '      <div class="fld"><label>归属来源</label><div class="pin"><select v-model.number="importForm.sourceId" style="width:100%"><option :value="null">默认第一个启用来源</option><option v-for="s in sources" :key="s.id" :value="s.id">{{ s.sourceName }}（{{ s.sectionName }}）</option></select></div></div>',
            '      <div class="fld"><label style="display:flex;align-items:center;gap:8px"><input type="checkbox" v-model="importForm.copyrightConfirmed" /> 我确认该文章转载版权合规（未确认需导入后再确认）</label></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="importVisible = false">取消</button><button class="c blue" :disabled="importing" @click="submitImport">{{ importing ? "抓取中…" : "抓取并预览" }}</button></div>',
            '    </div>',
            '  </div>',

            // 预览/编辑弹层
            '  <div class="pmask" :class="{ show: previewVisible }" @click.self="previewVisible = false">',
            '    <div class="pcard" v-if="preview" style="max-width:760px">',
            '      <div class="ph"><b>文章预览与编辑</b><span class="no">{{ preview.status }}</span><span class="sp"></span><span class="x" @click="previewVisible = false">✕</span></div>',
            '      <div class="psum"><div class="l2">{{ preview.sourceName }} · 原文 {{ preview.sourceUrl }} · 图片 {{ preview.imageCount }} 张 · 正文 {{ preview.wordCount }} 字</div>',
            '        <div class="l2">版权：{{ preview.copyrightConfirmed ? "已确认" : "未确认（发布前必须确认）" }}',
            '          <template v-if="preview.scheduledAt"> · 定时：{{ fmt(preview.scheduledAt) }}</template>',
            '          <template v-if="preview.retryCount"> · 已重试 {{ preview.retryCount }} 次</template></div>',
            '        <div class="l2" v-if="preview.errorMessage" style="color:var(--tk-bad)">失败原因：{{ preview.errorMessage }}</div></div>',
            '      <div class="fld"><label>标题 <em>*</em></label><div class="pin"><input v-model="editForm.title" maxlength="255" /></div></div>',
            '      <div class="fld"><label>正文（纯文本，图片块保持不变）</label><textarea class="pta" v-model="editForm.plainText" rows="8" maxlength="20000"></textarea></div>',
            '      <div class="fld" v-if="previewCover"><label>封面</label><img :src="previewCover" style="width:160px;height:90px;object-fit:cover;border-radius:8px;border:1px solid var(--tk-stroke)" /></div>',
            '      <div class="fld" v-if="previewImages.length"><label>图片块（共 {{ previewImages.length }} 张）</label>',
            '        <div style="display:flex;gap:8px;flex-wrap:wrap"><img v-for="u in previewImages" :key="u" :src="u" style="width:90px;height:90px;object-fit:cover;border-radius:8px;border:1px solid var(--tk-stroke);cursor:zoom-in" @click="zoom = u" /></div></div>',
            '      <div style="display:flex;gap:8px;align-items:flex-end;flex-wrap:wrap;margin-top:6px">',
            '        <div style="flex:1;min-width:200px"><div style="font-size:12px;color:var(--tk-muted);margin-bottom:6px">定时发布时间（可选）</div><input class="os-input" type="datetime-local" v-model="editForm.scheduledAt" style="width:100%" /></div>',
            '        <button class="c" :disabled="saving" @click="saveDraft">{{ saving ? "保存中…" : "保存编辑" }}</button>',
            '        <button class="c" :disabled="!preview || preview.copyrightConfirmed" @click="confirmCopyright">{{ preview && preview.copyrightConfirmed ? "版权已确认" : "确认版权" }}</button>',
            '        <button class="c blue" :disabled="preview.status === \'PUBLISHED\'" @click="publishPreview">立即发布</button>',
            '        <button class="c" :disabled="preview.status === \'PUBLISHED\' || !editForm.scheduledAt" @click="schedulePreview">定时发布</button>',
            '      </div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="previewVisible = false">关闭</button></div>',
            '    </div>',
            '  </div>',

            '  <div v-if="zoom" class="pmask show" @click.self="zoom = \'\'" style="z-index:60">',
            '    <img :src="zoom" style="max-width:86vw;max-height:86vh;border-radius:8px;background:#fff" />',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var sources = Vue.ref([]);
            var sections = Vue.ref([]);
            var officialUsers = Vue.ref([]);
            var articles = Vue.ref([]);
            var articleTotal = Vue.ref(0);
            var pageNum = Vue.ref(1);
            var pageSize = 20;
            var articleSourceId = Vue.ref('');
            var articleStatus = Vue.ref('');
            var loading = Vue.ref(false);
            var loadingArticles = Vue.ref(false);
            var syncingId = Vue.ref(null);
            var syncingAll = Vue.ref(false);
            var formVisible = Vue.ref(false);
            var form = Vue.ref(null);
            var submitting = Vue.ref(false);

            var importVisible = Vue.ref(false);
            var importing = Vue.ref(false);
            var importForm = Vue.reactive({ url: '', sourceId: null, copyrightConfirmed: false });
            var previewVisible = Vue.ref(false);
            var preview = Vue.ref(null);
            var editForm = Vue.reactive({ title: '', plainText: '', scheduledAt: '' });
            var saving = Vue.ref(false);
            var zoom = Vue.ref('');

            var previewImages = Vue.computed(function () {
                if (!preview.value || !preview.value.blocks) return [];
                return preview.value.blocks
                    .filter(function (b) { return b.type === 'IMAGE' && b.url; })
                    .map(function (b) { return b.url; });
            });
            var previewCover = Vue.computed(function () {
                return preview.value && preview.value.coverUrl ? preview.value.coverUrl : '';
            });

            function loadSources() {
                loading.value = true;
                api.get('/api/admin/official-feed/sources')
                    .then(function (data) { sources.value = data || []; })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { loading.value = false; });
            }
            function loadOptions() {
                api.get('/api/admin/community/sections')
                    .then(function (data) { sections.value = data || []; })
                    .catch(function (error) { El.ElMessage.error(error.message); });
                api.get('/api/admin/official-feed/official-users')
                    .then(function (data) { officialUsers.value = data || []; })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }
            function loadArticles(page) {
                pageNum.value = page || 1;
                loadingArticles.value = true;
                var params = ['pageNum=' + pageNum.value, 'pageSize=' + pageSize];
                if (articleSourceId.value) params.push('sourceId=' + articleSourceId.value);
                if (articleStatus.value) params.push('status=' + articleStatus.value);
                api.get('/api/admin/official-feed/articles?' + params.join('&'))
                    .then(function (data) {
                        articles.value = data.records || [];
                        articleTotal.value = data.total || 0;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { loadingArticles.value = false; });
            }
            function reload() {
                loadSources();
                loadArticles(pageNum.value);
            }
            function openCreate() {
                form.value = {
                    id: null,
                    sourceName: '',
                    listUrl: 'https://www.mycc.edu.cn/index/tzgg.htm',
                    sectionId: sections.value.length ? sections.value[sections.value.length - 1].sectionId : null,
                    authorId: officialUsers.value.length ? officialUsers.value[0].userId : null,
                    maxPages: 1,
                    maxArticles: 3,
                    maxAgeDays: 120,
                    enabled: true
                };
                formVisible.value = true;
            }
            function openEdit(source) {
                form.value = {
                    id: source.id,
                    sourceName: source.sourceName,
                    listUrl: source.listUrl,
                    sectionId: source.sectionId,
                    authorId: source.authorId,
                    maxPages: source.maxPages,
                    maxArticles: source.maxArticles,
                    maxAgeDays: source.maxAgeDays,
                    enabled: source.enabled
                };
                formVisible.value = true;
            }
            function saveForm() {
                submitting.value = true;
                var payload = {
                    sourceName: form.value.sourceName,
                    listUrl: form.value.listUrl,
                    sectionId: form.value.sectionId,
                    authorId: form.value.authorId,
                    maxPages: form.value.maxPages,
                    maxArticles: form.value.maxArticles,
                    maxAgeDays: form.value.maxAgeDays,
                    enabled: form.value.enabled
                };
                var promise = form.value.id
                    ? api.put('/api/admin/official-feed/sources/' + form.value.id, payload)
                    : api.post('/api/admin/official-feed/sources', payload);
                promise.then(function () {
                    El.ElMessage.success('已保存');
                    formVisible.value = false;
                    loadSources();
                }).catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { submitting.value = false; });
            }

            // ---------- C08：单篇导入 ----------
            function openImport() {
                importForm.url = '';
                importForm.sourceId = null;
                importForm.copyrightConfirmed = false;
                importVisible.value = true;
            }
            function submitImport() {
                if (!importForm.url.trim()) {
                    El.ElMessage.warning('请填写文章 URL');
                    return;
                }
                importing.value = true;
                api.post('/api/admin/official-feed/articles/import-url', {
                    url: importForm.url.trim(),
                    sourceId: importForm.sourceId || null,
                    copyrightConfirmed: importForm.copyrightConfirmed
                }).then(function (data) {
                    El.ElMessage.success('抓取成功，已生成草稿');
                    importVisible.value = false;
                    fillPreview(data);
                    previewVisible.value = true;
                    reload();
                }).catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { importing.value = false; });
            }
            function openPreview(article) {
                api.get('/api/admin/official-feed/articles/' + article.id + '/preview')
                    .then(function (data) {
                        fillPreview(data);
                        previewVisible.value = true;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }
            function fillPreview(data) {
                preview.value = data;
                editForm.title = data.title || '';
                editForm.plainText = data.plainText || '';
                editForm.scheduledAt = data.scheduledAt ? String(data.scheduledAt).slice(0, 16) : '';
            }
            function saveDraft() {
                if (!editForm.title.trim()) {
                    El.ElMessage.warning('请填写标题');
                    return;
                }
                saving.value = true;
                api.put('/api/admin/official-feed/articles/' + preview.value.id, {
                    title: editForm.title.trim(),
                    plainText: editForm.plainText,
                    coverUrl: preview.value.coverUrl || null
                }).then(function (data) {
                    El.ElMessage.success('已保存');
                    fillPreview(data);
                    reload();
                }).catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { saving.value = false; });
            }
            function confirmCopyright() {
                api.post('/api/admin/official-feed/articles/' + preview.value.id + '/copyright',
                    { confirmed: true })
                    .then(function (data) {
                        El.ElMessage.success('版权已确认');
                        fillPreview(data);
                        reload();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }
            function publishPreview() {
                El.ElMessageBox.confirm('确认立即发布该官方通知？将生成社区文章/动态。', '立即发布', {
                    type: 'warning', confirmButtonText: '发布', cancelButtonText: '取消'
                }).then(function () {
                    return api.post('/api/admin/official-feed/articles/' + preview.value.id + '/publish');
                }).then(function (data) {
                    El.ElMessage.success('已发布' + (data.postId ? '，帖子 ID ' + data.postId : ''));
                    fillPreview(data);
                    reload();
                }).catch(function (e) { if (e !== 'cancel') El.ElMessage.error(e.message || String(e)); });
            }
            function schedulePreview() {
                if (!editForm.scheduledAt) {
                    El.ElMessage.warning('请选择定时发布时间');
                    return;
                }
                api.post('/api/admin/official-feed/articles/' + preview.value.id + '/schedule',
                    { scheduledAt: editForm.scheduledAt + ':00' })
                    .then(function (data) {
                        El.ElMessage.success('已设置定时发布');
                        fillPreview(data);
                        reload();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }
            function publish(article) {
                openPreview(article);
            }
            function retry(article) {
                api.post('/api/admin/official-feed/articles/' + article.id + '/retry')
                    .then(function (data) {
                        if (data.status === 'FAILED') {
                            El.ElMessage.error('重试仍失败：' + (data.errorMessage || '未知原因'));
                        } else {
                            El.ElMessage.success('重试成功，已重新抓取正文');
                        }
                        fillPreview(data);
                        previewVisible.value = true;
                        reload();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }

            function describe(result) {
                return '发现 ' + result.discovered + ' 篇，发布 ' + result.published + '，跳过 '
                    + result.skipped + '，失败 ' + result.failed;
            }
            function syncOne(source) {
                syncingId.value = source.id;
                api.post('/api/admin/official-feed/sources/' + source.id + '/sync')
                    .then(function (result) {
                        El.ElMessage.success(source.sourceName + '：' + describe(result));
                        reload();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { syncingId.value = null; });
            }
            function syncAll() {
                syncingAll.value = true;
                api.post('/api/admin/official-feed/sync-all')
                    .then(function (results) {
                        var published = (results || []).reduce(function (sum, item) { return sum + (item.published || 0); }, 0);
                        El.ElMessage.success('同步完成，共发布 ' + published + ' 篇');
                        reload();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { syncingAll.value = false; });
            }
            function statusText(value) { return ARTICLE_STATUS[value] || value; }
            function statusTone(value) {
                if (value === 'PUBLISHED') return 'g';
                if (value === 'DRAFT') return 'brand';
                if (value === 'SCHEDULED') return 'a';
                return value === 'SKIPPED' ? 'a' : 'r';
            }
            function fmt(value) {
                if (!value) return '-';
                return String(value).replace('T', ' ').slice(0, 16);
            }

            loadSources();
            loadOptions();
            loadArticles(1);

            return {
                sources: sources, sections: sections, officialUsers: officialUsers,
                articles: articles, articleTotal: articleTotal, pageNum: pageNum, pageSize: pageSize,
                articleSourceId: articleSourceId, articleStatus: articleStatus,
                loading: loading, loadingArticles: loadingArticles,
                syncingId: syncingId, syncingAll: syncingAll,
                formVisible: formVisible, form: form, submitting: submitting,
                reload: reload, loadArticles: loadArticles, openCreate: openCreate, openEdit: openEdit,
                saveForm: saveForm, syncOne: syncOne, syncAll: syncAll,
                importVisible: importVisible, importForm: importForm, importing: importing,
                openImport: openImport, submitImport: submitImport,
                previewVisible: previewVisible, preview: preview, editForm: editForm, saving: saving,
                previewImages: previewImages, previewCover: previewCover, zoom: zoom,
                openPreview: openPreview, saveDraft: saveDraft, confirmCopyright: confirmCopyright,
                publishPreview: publishPreview, schedulePreview: schedulePreview, publish: publish, retry: retry,
                statusText: statusText, statusTone: statusTone, fmt: fmt
            };
        }
    };
})();
