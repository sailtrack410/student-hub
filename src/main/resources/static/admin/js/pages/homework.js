/* 作业管理：发布作业、查看状态、关闭作业。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};
    var STATUS = { PUBLISHED: '进行中', CLOSED: '已关闭' };

    window.AdminPages.HomeworkAdminPage = {
        name: 'HomeworkAdminPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="page-h"><div><h1>作业管理</h1><div class="sub">发布课程作业、截止时间与关闭状态；学生端仅显示当前校区进行中作业</div></div>',
            '    <div class="r"><button class="btn2" @click="reload">刷新</button><button class="c blue" @click="openCreate">发布作业</button></div></div>',
            '  <div class="lcard" style="flex:1;display:flex;flex-direction:column;min-height:0">',
            '    <div class="cap">作业列表<span class="cnt" v-if="total">共 {{ total }} 条</span></div>',
            '    <div class="tblw" style="flex:1;overflow:auto"><table class="pk">',
            '      <thead><tr><th style="width:140px">课程</th><th>标题</th><th style="width:110px">教师</th><th style="width:150px">截止时间</th><th style="width:90px">状态</th><th style="width:150px">发布时间</th><th style="width:120px" class="r">操作</th></tr></thead>',
            '      <tbody>',
            '        <tr v-if="!rows.length && !loading"><td colspan="7" style="text-align:center;color:var(--tk-muted);padding:48px 0">暂无作业，点击右上角发布</td></tr>',
            '        <tr v-for="h in rows" :key="h.homeworkId">',
            '          <td style="color:var(--tk-text);font-weight:600">{{ h.courseName }}</td>',
            '          <td>{{ h.title }}</td><td>{{ h.teacherName || "-" }}</td>',
            '          <td style="color:var(--tk-text3)">{{ fmt(h.dueAt) }}</td>',
            '          <td><span class="bdg" :class="h.status===\'PUBLISHED\' ? \'g\' : \'a\'">{{ statusText(h.status) }}</span></td>',
            '          <td style="color:var(--tk-text3)">{{ fmt(h.publishAt) }}</td>',
            '          <td class="r"><span v-if="h.status===\'PUBLISHED\'" class="op" @click="close(h)">关闭</span><span v-else style="color:var(--tk-muted)">已关闭</span></td>',
            '        </tr>',
            '      </tbody></table></div>',
            '    <div class="pfoot"><div class="pgs"><b class="edge" v-if="pageNum>1" @click="go(pageNum-1)">‹</b><b class="on">{{ pageNum }}</b><b class="edge" v-if="rows.length>=pageSize" @click="go(pageNum+1)">›</b></div><div class="tot">共 {{ total }} 条</div></div>',
            '  </div>',
            '  <div class="pmask" :class="{show: visible}" @click.self="visible=false"><div class="pcard w520" v-if="visible">',
            '    <div class="ph"><b>发布作业</b><span class="sp"></span><span class="x" @click="visible=false">✕</span></div>',
            '    <div style="display:flex;gap:10px"><div class="fld" style="flex:1"><label>课程名称 <em>*</em></label><div class="pin"><input v-model.trim="form.courseName" placeholder="如 软件工程导论" /></div></div>',
            '      <div class="fld" style="flex:1"><label>任课教师</label><div class="pin"><input v-model.trim="form.teacherName" placeholder="默认当前管理员" /></div></div></div>',
            '    <div class="fld"><label>作业标题 <em>*</em></label><div class="pin"><input v-model.trim="form.title" placeholder="如 第三章课后作业" /></div></div>',
            '    <div class="fld"><label>作业内容 <em>*</em></label><textarea class="pta" v-model="form.content" rows="4" placeholder="作业要求、提交格式等"></textarea></div>',
            '    <div class="fld"><label>截止时间 <em>*</em></label><div class="pin"><input type="datetime-local" v-model="form.dueAt" /></div></div>',
            '    <div class="infobar">发布后立即对学生可见；截止后自动禁止提交。</div>',
            '    <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="visible=false">取消</button><button class="c blue" :disabled="saving" @click="save">{{ saving ? "发布中…" : "发布" }}</button></div>',
            '  </div></div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;
            var rows = Vue.ref([]);
            var total = Vue.ref(0);
            var pageNum = Vue.ref(1);
            var pageSize = 20;
            var loading = Vue.ref(false);
            var visible = Vue.ref(false);
            var saving = Vue.ref(false);
            var form = Vue.reactive({ courseName: '', title: '', content: '', teacherName: '', dueAt: '' });
            function load() {
                loading.value = true;
                api.get('/api/admin/homework?pageNum=' + pageNum.value + '&pageSize=' + pageSize)
                    .then(function (page) { rows.value = page.records || []; total.value = page.total || 0; })
                    .catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { loading.value = false; });
            }
            function openCreate() {
                form.courseName = ''; form.title = ''; form.content = ''; form.teacherName = '';
                form.dueAt = '';
                visible.value = true;
            }
            function save() {
                if (!form.courseName || !form.title || !form.content || !form.dueAt) {
                    El.ElMessage.warning('请填写课程、标题、内容和截止时间'); return;
                }
                saving.value = true;
                api.post('/api/admin/homework', {
                    courseName: form.courseName, title: form.title, content: form.content,
                    teacherName: form.teacherName || null, dueAt: form.dueAt.length === 16 ? form.dueAt + ':00' : form.dueAt
                }).then(function () { El.ElMessage.success('作业已发布'); visible.value = false; load(); })
                    .catch(function (e) { El.ElMessage.error(e.message); })
                    .finally(function () { saving.value = false; });
            }
            function close(h) {
                api.post('/api/admin/homework/' + h.homeworkId + '/close')
                    .then(function () { El.ElMessage.success('作业已关闭'); load(); })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }
            function go(p) { if (p < 1) return; pageNum.value = p; load(); }
            function fmt(v) { return v ? String(v).replace('T', ' ').slice(0, 16) : '-'; }
            function statusText(s) { return STATUS[s] || s; }
            load();
            return { rows: rows, total: total, pageNum: pageNum, pageSize: pageSize, loading: loading, visible: visible, saving: saving, form: form, openCreate: openCreate, save: save, close: close, go: go, fmt: fmt, statusText: statusText, reload: load };
        }
    };
})();
