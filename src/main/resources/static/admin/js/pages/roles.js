/* 角色权限页：角色基本信息、权限矩阵、整体覆盖保存。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var ROLE_TYPES = ['ADMIN', 'REQUESTER', 'WORKER', 'DISPATCHER'];
    var STATUSES = [{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }];

    window.AdminPages.RolesPage = {
        name: 'RolesPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="page-h"><div><h1>角色与权限</h1><div class="sub">角色、权限矩阵与数据范围；用户可在「用户管理」中多角色绑定</div></div>',
            '    <div class="r"><button class="btn2" @click="reload">刷新</button><button class="btn2" @click="openCreate">新建角色</button></div></div>',
            '  <div class="statgrid">',
            '    <div class="stat"><div class="lb">角色总数</div><div class="v"><b>{{ roles.length }}</b><span>个</span></div></div>',
            '    <div class="stat"><div class="lb">权限项</div><div class="v"><b>{{ permissions.length }}</b><span>项</span></div></div>',
            '    <div class="stat"><div class="lb">启用角色</div><div class="v"><b>{{ roles.filter(function(r){return r.status==="ENABLED"}).length }}</b><span>个</span></div></div>',
            '    <div class="stat"><div class="lb">当前选择</div><div class="v"><b>{{ current ? current.roleName : "未选择" }}</b><span>{{ current ? current.permissionCodes.length + " 项权限" : "" }}</span></div></div>',
            '  </div>',
            '  <div style="display:flex;gap:16px;flex:1;min-height:0">',
            '    <div class="lcard" style="width:360px;display:flex;flex-direction:column;min-height:0">',
            '      <div class="cap">角色列表</div>',
            '      <div style="flex:1;overflow:auto">',
            '        <div v-for="role in roles" :key="role.roleId" @click="selectRole(role)"',
            '             :style="{padding: \'14px 16px\', borderBottom: \'1px solid var(--tk-line)\', cursor: \'pointer\', background: current && current.roleId === role.roleId ? \'var(--tk-bg2)\' : \'transparent\'}">',
            '          <div style="display:flex;align-items:center;gap:8px">',
            '            <b style="color:var(--tk-text)">{{ role.roleName }}</b>',
            '            <span class="bdg" :class="role.status===\'ENABLED\' ? \'g\' : \'a\'">{{ role.status === "ENABLED" ? "启用" : "停用" }}</span>',
            '            <span style="flex:1"></span>',
            '            <span style="font-size:12px;color:var(--tk-muted)">{{ role.roleType }}</span>',
            '          </div>',
            '          <div style="font-size:12px;color:var(--tk-muted);margin-top:5px;font-family:monospace">{{ role.roleCode }}</div>',
            '          <div style="display:flex;gap:12px;margin-top:6px;font-size:11px">',
            '            <span style="color:var(--tk-info);cursor:pointer" @click.stop="openCopy(role)">复制模板</span>',
            '            <span style="color:var(--tk-bad);cursor:pointer" @click.stop="removeRole(role)">删除</span>',
            '          </div>',
            '        </div>',
            '      </div>',
            '    </div>',
            '    <div class="lcard" style="flex:1;display:flex;flex-direction:column;min-height:0">',
            '      <div class="cap">权限配置 <span class="cnt" v-if="current">{{ current.roleName }} · {{ current.permissionCodes.length }} 项</span></div>',
            '      <div v-if="!current" style="padding:48px;text-align:center;color:var(--tk-muted)">选择左侧角色查看并配置权限</div>',
            '      <div v-else style="padding:16px 20px;overflow:auto;flex:1">',
            '        <div style="display:flex;gap:12px;flex-wrap:wrap;margin-bottom:16px">',
            '          <div class="fld" style="flex:1;min-width:180px;margin:0"><label>角色名称</label><div class="pin"><input v-model="form.roleName" maxlength="128" /></div></div>',
            '          <div class="fld" style="width:150px;margin:0"><label>角色类型</label><div class="pin"><select v-model="form.roleType" style="width:100%;border:0;background:none;outline:none"><option v-for="t in roleTypes" :key="t" :value="t">{{ t }}</option></select></div></div>',
            '          <div class="fld" style="width:130px;margin:0"><label>状态</label><div class="pin"><select v-model="form.status" style="width:100%;border:0;background:none;outline:none"><option v-for="s in statuses" :key="s.value" :value="s.value">{{ s.label }}</option></select></div></div>',
            '        </div>',
            '        <div style="font-size:13px;font-weight:700;color:var(--tk-text);margin:4px 0 10px">权限矩阵</div>',
            '        <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:8px">',
            '          <label v-for="p in permissions" :key="p.permissionId" class="chk" style="display:flex;align-items:center;gap:8px;padding:9px 10px;border:1px solid var(--tk-line);border-radius:8px;cursor:pointer">',
            '            <input type="checkbox" :value="p.permissionId" v-model="selectedPermissionIds" />',
            '            <span style="display:flex;flex-direction:column"><b style="font-size:12px;color:var(--tk-text)">{{ p.permissionName }}</b><span style="font-size:11px;color:var(--tk-muted);font-family:monospace">{{ p.permissionCode }}</span></span>',
            '          </label>',
            '        </div>',
            '        <div class="infobar" style="margin-top:14px">保存会整体覆盖该角色权限，并立即刷新该角色下所有在线会话的权限。</div>',
            '        <div class="pbtn" style="margin-top:14px"><span class="sp"></span>',
            '          <button class="c ghost" @click="selectRole(current)">重置</button>',
            '          <button class="c blue" :disabled="saving" @click="save">{{ saving ? "保存中…" : "保存角色与权限" }}</button>',
            '        </div>',
            '      </div>',
            '    </div>',
            '  </div>',

            // 弹层: 新建角色
            '  <div class="pmask" :class="{ show: createVisible }" @click.self="createVisible = false">',
            '    <div class="pcard w440" v-if="createVisible">',
            '      <div class="ph"><b>新建角色</b><span class="no">角色编码创建后不可修改</span><span class="sp"></span><span class="x" @click="createVisible = false">✕</span></div>',
            '      <div class="fld"><label>角色编码 <em>*</em></label><div class="pin"><input v-model.trim="createForm.roleCode" maxlength="64" placeholder="如 COLLEGE_STAFF（大写字母开头）" /></div></div>',
            '      <div class="fld"><label>角色名称 <em>*</em></label><div class="pin"><input v-model.trim="createForm.roleName" maxlength="128" placeholder="如 学院管理员" /></div></div>',
            '      <div style="display:flex;gap:10px"><div class="fld" style="flex:1"><label>角色类型</label><div class="pin"><select v-model="createForm.roleType" style="width:100%"><option v-for="t in roleTypes" :key="t" :value="t">{{ t }}</option></select></div></div>',
            '        <div class="fld" style="flex:1"><label>状态</label><div class="pin"><select v-model="createForm.status" style="width:100%"><option v-for="s in statuses" :key="s.value" :value="s.value">{{ s.label }}</option></select></div></div></div>',
            '      <div class="infobar">新建角色默认无权限；创建后在右侧权限矩阵中勾选并保存。内置角色（平台管理员/申请人/服务人员/调度员）不可删除，只能停用。</div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="createVisible = false">取消</button><button class="c blue" :disabled="submitting" @click="submitCreate">{{ submitting ? "创建中…" : "创建角色" }}</button></div>',
            '    </div>',
            '  </div>',

            // 弹层: 复制角色模板
            '  <div class="pmask" :class="{ show: copyVisible }" @click.self="copyVisible = false">',
            '    <div class="pcard w440" v-if="copyVisible && copySource">',
            '      <div class="ph"><b>复制角色模板</b><span class="no">来源：{{ copySource.roleName }}（{{ copySource.roleCode }}）</span><span class="sp"></span><span class="x" @click="copyVisible = false">✕</span></div>',
            '      <div class="fld"><label>新角色编码</label><div class="pin"><input v-model.trim="copyForm.roleCode" maxlength="64" placeholder="留空自动生成 源编码_COPY" /></div></div>',
            '      <div class="fld"><label>新角色名称</label><div class="pin"><input v-model.trim="copyForm.roleName" maxlength="128" placeholder="留空自动生成「源角色名 副本」" /></div></div>',
            '      <div class="infobar">复制会带上源角色全部 {{ copySource.permissionCodes.length }} 项权限，不会复制用户绑定；复制后可继续调整权限矩阵。</div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="copyVisible = false">取消</button><button class="c blue" :disabled="submitting" @click="submitCopy">{{ submitting ? "复制中…" : "复制并创建" }}</button></div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;
            var roles = Vue.ref([]);
            var permissions = Vue.ref([]);
            var current = Vue.ref(null);
            var selectedPermissionIds = Vue.ref([]);
            var form = Vue.reactive({ roleName: '', roleType: 'ADMIN', status: 'ENABLED' });
            var saving = Vue.ref(false);

            function load() {
                api.get('/api/admin/roles').then(function (list) {
                    roles.value = list || [];
                    if (!current.value && roles.value.length) selectRole(roles.value[0]);
                }).catch(function (e) { El.ElMessage.error(e.message); });
                api.get('/api/admin/roles/permissions').then(function (list) {
                    permissions.value = list || [];
                }).catch(function (e) { El.ElMessage.error(e.message); });
            }

            function selectRole(role) {
                if (!role) return;
                api.get('/api/admin/roles/' + role.roleId).then(function (detail) {
                    current.value = detail;
                    form.roleName = detail.roleName;
                    form.roleType = detail.roleType;
                    form.status = detail.status;
                    selectedPermissionIds.value = detail.permissionIds || [];
                }).catch(function (e) { El.ElMessage.error(e.message); });
            }

            function save() {
                if (!current.value) return;
                saving.value = true;
                api.put('/api/admin/roles/' + current.value.roleId, {
                    roleName: form.roleName, roleType: form.roleType, status: form.status
                }).then(function () {
                    return api.put('/api/admin/roles/' + current.value.roleId + '/permissions',
                        { permissionIds: selectedPermissionIds.value });
                }).then(function (detail) {
                    El.ElMessage.success('角色与权限已保存');
                    current.value = detail;
                    selectedPermissionIds.value = detail.permissionIds || [];
                    load();
                }).catch(function (e) { El.ElMessage.error(e.message); })
                  .finally(function () { saving.value = false; });
            }

            // ---- 新建 / 复制 / 删除（U02）----
            var createVisible = Vue.ref(false);
            var copyVisible = Vue.ref(false);
            var submitting = Vue.ref(false);
            var createForm = Vue.reactive({ roleCode: '', roleName: '', roleType: 'ADMIN', status: 'ENABLED' });
            var copyForm = Vue.reactive({ roleCode: '', roleName: '' });
            var copySource = Vue.ref(null);

            function openCreate() {
                createForm.roleCode = '';
                createForm.roleName = '';
                createForm.roleType = 'ADMIN';
                createForm.status = 'ENABLED';
                createVisible.value = true;
            }

            function submitCreate() {
                if (!createForm.roleCode || !createForm.roleName) {
                    El.ElMessage.warning('请填写角色编码和角色名称');
                    return;
                }
                submitting.value = true;
                api.post('/api/admin/roles', {
                    roleCode: createForm.roleCode,
                    roleName: createForm.roleName,
                    roleType: createForm.roleType,
                    status: createForm.status
                }).then(function (detail) {
                    El.ElMessage.success('角色已创建，请在右侧配置权限');
                    createVisible.value = false;
                    current.value = detail;
                    form.roleName = detail.roleName;
                    form.roleType = detail.roleType;
                    form.status = detail.status;
                    selectedPermissionIds.value = detail.permissionIds || [];
                    load();
                }).catch(function (e) { El.ElMessage.error(e.message); })
                  .finally(function () { submitting.value = false; });
            }

            function openCopy(role) {
                api.get('/api/admin/roles/' + role.roleId)
                    .then(function (detail) {
                        copySource.value = detail;
                        copyForm.roleCode = '';
                        copyForm.roleName = '';
                        copyVisible.value = true;
                    })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }

            function submitCopy() {
                if (!copySource.value) return;
                submitting.value = true;
                api.post('/api/admin/roles/' + copySource.value.roleId + '/copy', {
                    roleCode: copyForm.roleCode || null,
                    roleName: copyForm.roleName || null
                }).then(function (detail) {
                    El.ElMessage.success('已按模板复制角色：' + detail.roleCode);
                    copyVisible.value = false;
                    current.value = detail;
                    form.roleName = detail.roleName;
                    form.roleType = detail.roleType;
                    form.status = detail.status;
                    selectedPermissionIds.value = detail.permissionIds || [];
                    load();
                }).catch(function (e) { El.ElMessage.error(e.message); })
                  .finally(function () { submitting.value = false; });
            }

            function removeRole(role) {
                El.ElMessageBox.confirm(
                        '确定删除角色「' + role.roleName + '」？内置角色与仍被用户使用的角色会被服务端拒绝。',
                        '删除角色', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
                    .then(function () {
                        return api.delete('/api/admin/roles/' + role.roleId);
                    })
                    .then(function () {
                        El.ElMessage.success('角色已删除');
                        if (current.value && current.value.roleId === role.roleId) {
                            current.value = null;
                        }
                        load();
                    })
                    .catch(function (e) {
                        if (e !== 'cancel' && e !== 'close') {
                            El.ElMessage.error(e.message || '删除失败');
                        }
                    });
            }

            load();
            return {
                roles: roles, permissions: permissions, current: current,
                selectedPermissionIds: selectedPermissionIds, form: form, saving: saving,
                roleTypes: ROLE_TYPES, statuses: STATUSES,
                selectRole: selectRole, save: save, reload: load,
                createVisible: createVisible, createForm: createForm, openCreate: openCreate, submitCreate: submitCreate,
                copyVisible: copyVisible, copyForm: copyForm, copySource: copySource,
                openCopy: openCopy, submitCopy: submitCopy, removeRole: removeRole, submitting: submitting
            };
        }
    };
})();
