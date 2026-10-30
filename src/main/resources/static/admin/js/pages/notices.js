/* 通知公告管理：App 公告与终端大屏同一数据源，支持草稿、定时、发布、下线与删除。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var TYPE_TEXT = { NOTICE: '通知', NEWS: '要闻', ACTIVITY: '活动' };
    var STATUS_TEXT = { DRAFT: '草稿', SCHEDULED: '定时发布', PUBLISHED: '已发布', OFFLINE: '已下线' };
    var STATUS_TONE = { DRAFT: 'muted', SCHEDULED: 'warn', PUBLISHED: 'ok', OFFLINE: 'bad' };
    var CHANNEL_TEXT = { APP: 'App 公告', SCREEN: '终端大屏' };

    function blankForm() {
        return {
            id: null,
            noticeType: 'NOTICE',
            channel: 'APP',
            title: '',
            summary: '',
            author: '学生工作处',
            publishDate: new Date().toISOString().slice(0, 10),
            action: 'PUBLISH',
            publishAt: '',
            sortOrder: 0,
            enabled: true,
            content: '',
            coverImageUrl: ''
        };
    }

    window.AdminPages.NoticesPage = {
        name: 'NoticesPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>通知公告</h1><div class="sub">App 与终端大屏公告发布</div></div>',
            '    <div class="r"><button class="btn2" @click="load">刷新</button><button class="btn2 pri" @click="openCreate">新建公告</button></div></div>',

            '  <div class="os-grid" style="grid-template-columns:repeat(4,1fr)">',
            '    <div class="os-kpi"><div class="k-label"><span class="dot"></span>公告总数</div><div class="k-val">{{ rows.length }}<small>条</small></div><div class="k-foot"><span>App + 终端大屏</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>已发布</div><div class="k-val">{{ countBy(\'PUBLISHED\') }}<small>条</small></div><div class="k-foot"><span>当前在线可见</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>定时 / 草稿</div><div class="k-val">{{ countBy(\'SCHEDULED\') + countBy(\'DRAFT\') }}<small>条</small></div><div class="k-foot"><span>等待发布或继续编辑</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-bad)"></span>已下线</div><div class="k-val">{{ countBy(\'OFFLINE\') }}<small>条</small></div><div class="k-foot"><span>保留数据，可恢复</span></div></div>',
            '  </div>',

            '  <div class="os-panel" style="flex:1;min-height:0;display:flex;flex-direction:column">',
            '    <div class="os-panel-h" style="flex-wrap:wrap;row-gap:8px">',
            '      <div style="display:flex;gap:4px"><button v-for="t in channels" :key="t.value" class="tab" :class="{ on: channel === t.value }" style="background:transparent;border-color:transparent;color:var(--tk-text3)" :style="channel === t.value ? \'background:var(--tk-text);color:var(--tk-panel)\' : \'\'" @click="channel = t.value; load()">{{ t.label }}</button></div>',
            '      <span style="flex:1"></span>',
            '      <select class="os-select" style="width:128px" v-model="status" @change="load"><option value="">全部状态</option><option value="PUBLISHED">已发布</option><option value="SCHEDULED">定时发布</option><option value="DRAFT">草稿</option><option value="OFFLINE">已下线</option></select>',
            '      <select class="os-select" style="width:118px" v-model="noticeType" @change="load"><option value="">全部类型</option><option value="NOTICE">通知</option><option value="NEWS">要闻</option><option value="ACTIVITY">活动</option></select>',
            '      <div class="srch" style="width:210px;background:var(--tk-input)"><el-icon :size="13"><Search /></el-icon><input v-model.trim="keyword" placeholder="搜索标题 / 发布方" /></div>',
            '    </div>',
            '    <div style="overflow:auto;flex:1">',
            '      <table style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr><th style="width:38%">标题</th><th style="width:92px">类型</th><th style="width:104px">渠道</th><th style="width:108px">状态</th><th style="width:110px">发布日期</th><th style="width:110px">发布方</th><th style="width:70px">排序</th><th style="width:150px;text-align:right">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-for="n in filtered" :key="n.id">',
            '            <td><div style="display:flex;align-items:center;gap:8px;min-width:0"><span style="width:6px;height:6px;border-radius:50%;flex:none" :style="{ background: n.publishStatus === \'PUBLISHED\' ? \'var(--tk-ok)\' : (n.publishStatus === \'SCHEDULED\' ? \'var(--tk-warn)\' : \'var(--tk-muted)\') }"></span><b style="font-weight:550;color:var(--tk-text);overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ n.title }}</b><img v-if="n.coverImageUrl" :src="n.coverImageUrl" style="width:34px;height:22px;object-fit:cover;border-radius:4px;flex:none" /></div><div style="font-size:11px;color:var(--tk-text3);margin-top:3px;padding-left:15px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ n.summary || "—" }} · 正文 {{ n.contentLength || 0 }} 字</div></td>',
            '            <td><span class="os-tag" :class="n.noticeType === \'NEWS\' ? \'info\' : (n.noticeType === \'ACTIVITY\' ? \'warn\' : \'\')">{{ TYPE_TEXT[n.noticeType] || n.noticeType }}</span></td>',
            '            <td style="color:var(--tk-text3)">{{ CHANNEL_TEXT[n.channel] || n.channel }}</td>',
            '            <td><span class="os-tag" :class="STATUS_TONE[n.publishStatus] || \'muted\'">{{ STATUS_TEXT[n.publishStatus] || n.publishStatus }}</span><div v-if="n.publishStatus === \'SCHEDULED\' && n.publishAt" style="font-size:10px;color:var(--tk-text3);margin-top:3px">{{ fmt(n.publishAt) }}</div></td>',
            '            <td style="color:var(--tk-text3)">{{ n.publishDate || "—" }}</td>',
            '            <td style="color:var(--tk-text3)">{{ n.author || "—" }}</td>',
            '            <td class="os-mono" style="color:var(--tk-muted)">{{ n.sortOrder }}</td>',
            '            <td style="text-align:right;white-space:nowrap">',
            '              <span style="font-size:11px;color:var(--tk-text3);cursor:pointer;margin-left:8px" @click="openPreview(n)">预览</span>',
            '              <span style="font-size:11px;color:var(--tk-info);cursor:pointer;margin-left:8px" @click="openEdit(n)">编辑</span>',
            '              <span v-if="n.publishStatus !== \'PUBLISHED\'" style="font-size:11px;color:var(--tk-ok);cursor:pointer;margin-left:8px" @click="publish(n)">发布</span>',
            '              <span v-else style="font-size:11px;color:var(--tk-warn);cursor:pointer;margin-left:8px" @click="offline(n)">下线</span>',
            '              <span style="font-size:11px;color:var(--tk-bad);cursor:pointer;margin-left:8px" @click="remove(n)">删除</span>',
            '            </td>',
            '          </tr>',
            '          <tr v-if="!filtered.length"><td colspan="8" class="os-empty">没有匹配的公告</td></tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',
            '  </div>',

            '  <div v-if="previewVisible" class="cmd-mask" style="padding-top:6vh" @click.self="previewVisible = false">',
            '    <div class="cmd-panel" style="width:680px;max-height:84vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">公告预览</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="previewVisible = false">✕</span></div>',
            '      <div style="overflow:auto;padding:20px 24px">',
            '        <div style="font-size:20px;font-weight:700;color:var(--tk-text);line-height:1.4">{{ previewData.title }}</div>',
            '        <div style="margin-top:8px;font-size:12px;color:var(--tk-text3)">{{ TYPE_TEXT[previewData.noticeType] || previewData.noticeType }} · {{ previewData.author || "—" }} · {{ previewData.publishDate || "—" }} · 正文 {{ previewData.contentLength || 0 }} 字</div>',
            '        <img v-if="previewData.coverImageUrl" :src="previewData.coverImageUrl" style="width:100%;max-height:260px;object-fit:cover;border-radius:10px;margin:14px 0" />',
            '        <div style="margin-top:12px;font-size:14px;line-height:1.9;color:var(--tk-text2);white-space:pre-wrap">{{ previewData.content || "（暂无正文，可在编辑中补充）" }}</div>',
            '      </div>',
            '      <div style="display:flex;gap:8px;justify-content:flex-end;padding:12px 20px;border-top:1px solid var(--tk-stroke)"><button class="os-btn" @click="previewVisible = false">关闭</button></div>',
            '    </div>',
            '  </div>',

            '  <div v-if="editorOpen" class="cmd-mask" style="padding-top:6vh" @click.self="editorOpen = false">',
            '    <div class="cmd-panel" style="width:620px;max-height:84vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">{{ form.id ? "编辑公告" : "新建公告" }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="editorOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>公告类型 <em>*</em></label><select class="os-select" v-model="form.noticeType"><option value="NOTICE">通知</option><option value="NEWS">要闻</option><option value="ACTIVITY">活动</option></select></div>',
            '          <div class="os-field"><label>投放渠道 <em>*</em></label><select class="os-select" v-model="form.channel"><option value="APP">App 公告</option><option value="SCREEN">终端大屏</option></select></div>',
            '        </div>',
            '        <div class="os-field"><label>标题 <em>*</em></label><input class="os-input" v-model.trim="form.title" maxlength="128" placeholder="例如：关于 2026 年秋季学期期中教学检查的通知" /></div>',
            '        <div class="os-field"><label>摘要</label><textarea class="os-textarea" v-model.trim="form.summary" maxlength="512" placeholder="列表页展示的摘要，建议 60-120 字"></textarea></div>',
            '        <div class="os-field"><label>封面图（列表/详情展示）</label>',
            '          <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">',
            '            <img v-if="form.coverImageUrl" :src="form.coverImageUrl" style="width:132px;height:74px;object-fit:cover;border-radius:8px;border:1px solid var(--tk-stroke)" />',
            '            <button class="os-btn" :disabled="coverUploading" @click="pickCover">{{ coverUploading ? "上传中…" : (form.coverImageUrl ? "替换封面" : "上传封面") }}</button>',
            '            <button v-if="form.coverImageUrl" class="os-btn" @click="form.coverImageUrl = \'\'">移除</button>',
            '            <span style="font-size:11px;color:var(--tk-text3)">PNG/JPEG/WebP，≤2MB</span>',
            '          </div></div>',
            '        <input ref="coverInput" type="file" accept=".png,.jpg,.jpeg,.webp" style="display:none" @change="onCoverFile" />',
            '        <div class="os-field"><label>正文（纯文本，保留换行）</label><textarea class="os-textarea" v-model="form.content" maxlength="20000" rows="8" placeholder="公告正文，发布后可在 App 公告详情/终端大屏展示"></textarea>',
            '          <div style="font-size:11px;color:var(--tk-text3);margin-top:4px">当前 {{ (form.content || \'\').length }} / 20000 字</div></div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>发布方</label><input class="os-input" v-model.trim="form.author" maxlength="64" placeholder="例如：教务处" /></div>',
            '          <div class="os-field"><label>发布日期</label><input class="os-input" type="date" v-model="form.publishDate" /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>发布动作</label><select class="os-select" v-model="form.action"><option value="PUBLISH">立即发布</option><option value="SCHEDULE">定时发布</option><option value="DRAFT">存草稿</option><option value="OFFLINE">建档并下线</option></select></div>',
            '          <div class="os-field"><label>定时发布时间</label><input class="os-input" type="datetime-local" v-model="form.publishAt" :disabled="form.action !== \'SCHEDULE\'" /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>排序值（大屏轮播）</label><input class="os-input" type="number" v-model.number="form.sortOrder" /></div>',
            '          <div class="os-field"><label>大屏启用</label><div style="display:flex;align-items:center;gap:8px;height:36px"><button class="os-switch" :class="{ on: form.enabled }" @click="form.enabled = !form.enabled"></button><span style="font-size:12px;color:var(--tk-text3)">{{ form.enabled ? "启用" : "停用" }}</span></div></div>',
            '        </div>',
            '        <div class="os-note"><b>发布规则</b> 定时公告由后端定时任务每 30 秒检查；发布后 App 与终端大屏按渠道读取。下线只移除展示，不删除数据。</div>',
            '        <div style="display:flex;gap:8px;justify-content:flex-end;padding-top:2px"><button class="os-btn" @click="editorOpen = false">取消</button><button class="os-btn primary" :disabled="submitting" @click="submit">{{ submitting ? "提交中…" : "提交" }}</button></div>',
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
            var channel = Vue.ref('APP');
            var status = Vue.ref('');
            var noticeType = Vue.ref('');
            var keyword = Vue.ref('');
            var editorOpen = Vue.ref(false);
            var submitting = Vue.ref(false);
            var form = Vue.reactive(blankForm());
            var previewVisible = Vue.ref(false);
            var previewData = Vue.ref({});
            var coverUploading = Vue.ref(false);
            var coverInput = Vue.ref(null);

            var channels = [
                { value: 'APP', label: 'App 公告' },
                { value: 'SCREEN', label: '终端大屏' }
            ];

            var filtered = Vue.computed(function () {
                var q = keyword.value.toLowerCase();
                return rows.value.filter(function (n) {
                    if (channel.value && n.channel !== channel.value) return false;
                    if (status.value && n.publishStatus !== status.value) return false;
                    if (noticeType.value && n.noticeType !== noticeType.value) return false;
                    if (q && ((n.title || '') + ' ' + (n.summary || '') + ' ' + (n.author || '')).toLowerCase().indexOf(q) < 0) return false;
                    return true;
                });
            });

            function countBy(s) {
                return rows.value.filter(function (n) { return n.channel === channel.value && n.publishStatus === s; }).length;
            }
            function fmt(v) { return v ? String(v).replace('T', ' ').slice(0, 16) : '—'; }

            function load() {
                var qs = [];
                if (channel.value) qs.push('channel=' + encodeURIComponent(channel.value));
                if (status.value) qs.push('publishStatus=' + encodeURIComponent(status.value));
                if (noticeType.value) qs.push('noticeType=' + encodeURIComponent(noticeType.value));
                api.get('/api/admin/notices' + (qs.length ? '?' + qs.join('&') : ''))
                    .then(function (data) { rows.value = data || []; })
                    .catch(function (error) { El.ElMessage.error(error.message || '公告加载失败'); });
            }
            function resetForm() {
                Object.assign(form, blankForm());
                form.id = null;
            }
            function openPreview(n) {
                previewData.value = n;
                previewVisible.value = true;
            }
            function pickCover() {
                if (coverInput.value) coverInput.value.click();
            }
            function onCoverFile(event) {
                var file = event.target && event.target.files && event.target.files[0];
                if (!file) return;
                var name = (file.name || '').toLowerCase();
                if (!/\.(png|jpe?g|webp)$/.test(name)) {
                    El.ElMessage.warning('封面仅支持 PNG/JPEG/WebP');
                    event.target.value = '';
                    return;
                }
                coverUploading.value = true;
                api.upload('/api/admin/notices/cover', file)
                    .then(function (url) {
                        form.coverImageUrl = url;
                        El.ElMessage.success('封面上传成功');
                    })
                    .catch(function (e) { El.ElMessage.error(e.message || '封面上传失败'); })
                    .finally(function () {
                        coverUploading.value = false;
                        event.target.value = '';
                    });
            }
            function openCreate() {
                resetForm();
                form.channel = channel.value;
                editorOpen.value = true;
            }
            function openEdit(n) {
                resetForm();
                form.id = n.id;
                form.noticeType = n.noticeType;
                form.channel = n.channel;
                form.title = n.title;
                form.summary = n.summary || '';
                form.author = n.author || '';
                form.publishDate = n.publishDate || '';
                form.action = n.publishStatus === 'PUBLISHED' ? 'PUBLISH' : (n.publishStatus === 'SCHEDULED' ? 'SCHEDULE' : (n.publishStatus === 'OFFLINE' ? 'OFFLINE' : 'DRAFT'));
                form.publishAt = n.publishAt ? String(n.publishAt).slice(0, 16) : '';
                form.sortOrder = n.sortOrder || 0;
                form.enabled = !!n.enabled;
                form.content = n.content || '';
                form.coverImageUrl = n.coverImageUrl || '';
                editorOpen.value = true;
            }
            function submit() {
                if (!form.title) { El.ElMessage.warning('请填写公告标题'); return; }
                if (form.action === 'SCHEDULE' && !form.publishAt) { El.ElMessage.warning('定时发布需要选择发布时间'); return; }
                var payload = {
                    noticeType: form.noticeType,
                    title: form.title,
                    summary: form.summary,
                    publishDate: form.publishDate || null,
                    channel: form.channel,
                    action: form.action,
                    publishAt: form.action === 'SCHEDULE' ? form.publishAt + ':00' : null,
                    sortOrder: form.sortOrder || 0,
                    author: form.author,
                    enabled: form.enabled,
                    content: form.content || null,
                    coverImageUrl: form.coverImageUrl || null
                };
                submitting.value = true;
                var request = form.id
                    ? api.put('/api/admin/notices/' + form.id, payload)
                    : api.post('/api/admin/notices', payload);
                request.then(function () {
                    El.ElMessage.success(form.id ? '公告已更新' : '公告已创建');
                    editorOpen.value = false;
                    load();
                }).catch(function (error) { El.ElMessage.error(error.message || '保存失败'); })
                    .finally(function () { submitting.value = false; });
            }
            function publish(n) {
                api.post('/api/admin/notices/' + n.id + '/publish')
                    .then(function () { El.ElMessage.success('公告已发布'); load(); })
                    .catch(function (error) { El.ElMessage.error(error.message || '发布失败'); });
            }
            function offline(n) {
                El.ElMessageBox.confirm('下线后该公告将从展示端移除，数据保留，可再次发布。', '下线公告', { type: 'warning' })
                    .then(function () {
                        return api.post('/api/admin/notices/' + n.id + '/offline');
                    }).then(function () { El.ElMessage.success('公告已下线'); load(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '下线失败'); });
            }
            function remove(n) {
                El.ElMessageBox.confirm('删除为软删除，列表将不再出现。确定删除「' + n.title + '」？', '删除公告', { type: 'warning' })
                    .then(function () { return api.delete('/api/admin/notices/' + n.id); })
                    .then(function () { El.ElMessage.success('公告已删除'); load(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '删除失败'); });
            }

            load();
            return {
                rows: rows, channel: channel, status: status, noticeType: noticeType, keyword: keyword,
                channels: channels, filtered: filtered, load: load, countBy: countBy, fmt: fmt,
                editorOpen: editorOpen, form: form, submitting: submitting, openCreate: openCreate,
                previewVisible: previewVisible, previewData: previewData, openPreview: openPreview,
                coverUploading: coverUploading, coverInput: coverInput, pickCover: pickCover,
                onCoverFile: onCoverFile,
                openEdit: openEdit, submit: submit, publish: publish, offline: offline, remove: remove,
                TYPE_TEXT: TYPE_TEXT, STATUS_TEXT: STATUS_TEXT, STATUS_TONE: STATUS_TONE, CHANNEL_TEXT: CHANNEL_TEXT
            };
        }
    };
})();
