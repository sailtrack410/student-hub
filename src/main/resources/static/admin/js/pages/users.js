/* 用户管理页：分页搜索、启用/停用、授权审核（APPROVE/REJECT）、角色分配（联动身份）。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    var IDENTITY_OPTIONS = [
        { value: 'STUDENT', label: '学生' },
        { value: 'TEACHER', label: '教师' },
        { value: 'LOGISTICS', label: '后勤' },
        { value: 'ADMIN', label: '管理员' }
    ];

    var AUTH_OPTIONS = [
        { value: 'BASIC', label: '基础权限' },
        { value: 'PENDING', label: '待审核' },
        { value: 'AUTHORIZED', label: '已授权' },
        { value: 'REJECTED', label: '已拒绝' }
    ];

    function labelOf(options, value) {
        for (var i = 0; i < options.length; i++) {
            if (options[i].value === value) return options[i].label;
        }
        return value || '-';
    }

    window.AdminPages.UsersPage = {
        name: 'UsersPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="page-h">',
            '    <div>',
            '      <h1>用户管理</h1>',
            '      <div class="sub">平台用户与角色授权统一管理<span v-if="total"> · 共 {{ total }} 位用户</span></div>',
            '    </div>',
            '    <div class="r"><button class="btn2" @click="reload">刷新</button></div>',
            '  </div>',

            '  <div class="toolbar">',
            '    <div class="srch"><input v-model.trim="query.keyword" placeholder="用户名 / 昵称 / 手机号" @keyup.enter="reload" /></div>',
            '    <div class="fchip"><select v-model="query.status" @change="reload" style="border:0;background:none;font:inherit;color:inherit;outline:none">',
            '      <option value="">账号状态</option><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></div>',
            '    <div class="fchip"><select v-model="query.identityType" @change="reload" style="border:0;background:none;font:inherit;color:inherit;outline:none">',
            '      <option value="">全部身份</option><option v-for="item in identityOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></div>',
            '    <div class="fchip"><select v-model="query.authorizationStatus" @change="reload" style="border:0;background:none;font:inherit;color:inherit;outline:none">',
            '      <option value="">授权状态</option><option v-for="item in authOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></div>',
            '    <div class="fchip"><select v-model="query.roleId" @change="reload" style="border:0;background:none;font:inherit;color:inherit;outline:none">',
            '      <option value="">全部角色</option><option v-for="role in roles" :key="role.roleId" :value="role.roleId">{{ role.roleName }}</option></select></div>',
            '    <span style="flex:1"></span>',
            '    <button class="btn2 pri" @click="reload">搜索</button>',
            '  </div>',

            '  <div class="lcard" style="flex:1">',
            '    <div class="cap">用户列表</div>',
            '    <div class="tblw">',
            '      <table class="pk">',
            '        <colgroup><col style="width:150px"><col style="width:130px"><col style="width:130px"><col style="width:80px"><col><col style="width:100px"><col style="width:90px"><col style="width:150px"><col style="width:220px"></colgroup>',
            '        <thead><tr><th>用户名</th><th>昵称</th><th>手机号</th><th>身份</th><th>角色</th><th>授权状态</th><th>账号状态</th><th>最近登录</th><th class="r">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-if="!rows.length && !loading"><td colspan="9" style="text-align:center;color:var(--tk-muted);padding:32px 0">暂无用户</td></tr>',
            '          <tr v-if="loading"><td colspan="9" style="text-align:center;color:var(--tk-muted);padding:32px 0">加载中…</td></tr>',
            '          <tr v-for="row in rows" :key="row.userId">',
            '            <td style="color:var(--tk-text);font-weight:600">{{ row.username }}</td>',
            '            <td>{{ row.displayName }}</td>',
            '            <td>{{ row.phone || "-" }}</td>',
            '            <td>{{ identityLabel(row.identityType) }}</td>',
            '            <td><span class="bdg plain" v-for="role in row.roles" :key="role.roleId" style="margin-right:6px">{{ role.roleName }}</span>',
            '                <span v-if="!row.roles.length" style="color:var(--tk-muted)">无角色</span></td>',
            '            <td><span class="bdg" :class="authTone(row.authorizationStatus)">{{ authLabel(row.authorizationStatus) }}</span></td>',
            '            <td><span class="bdg" :class="row.status === \'ENABLED\' ? \'g\' : \'r\'">{{ row.status === \'ENABLED\' ? \'启用\' : \'停用\' }}</span></td>',
            '            <td style="color:var(--tk-text3)">{{ fmt(row.lastLoginAt) }}</td>',
            '            <td class="r">',
            '              <span class="op" @click="openRoles(row)">分配角色</span>',
            '              <span v-if="row.authorizationStatus === \'PENDING\'"> · <span class="op" @click="openReview(row)">授权审核</span></span>',
            '              <span> · <span class="op r" @click="toggleStatus(row)">{{ row.status === \'ENABLED\' ? \'停用\' : \'启用\' }}</span></span>',
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

            // 弹层: 分配角色
            '  <div class="pmask" :class="{ show: rolesVisible }" @click.self="rolesVisible = false">',
            '    <div class="pcard w440" v-if="rolesTarget">',
            '      <div class="ph"><b>分配角色</b><span class="no">{{ rolesTarget.displayName }}（{{ rolesTarget.username }}）</span><span class="sp"></span><span class="x" @click="rolesVisible = false">✕</span></div>',
            '      <div style="display:flex;flex-direction:column;gap:8px">',
            '        <label v-for="role in roles" :key="role.roleId" class="chk" style="cursor:pointer">',
            '          <span class="bx" :class="{ off: selectedRoleIds.indexOf(role.roleId) < 0 }" @click.prevent="toggleRole(role.roleId)">',
            '            <svg v-if="selectedRoleIds.indexOf(role.roleId) >= 0" width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round"><path d="M1.5 5.5l2.5 2.5 4.5-5"/></svg>',
            '          </span>{{ role.roleName }}',
            '        </label>',
            '      </div>',
            '      <div class="infobar">授予服务人员/调度员会自动将身份联动为「后勤」并标记已授权；撤销后回落。调整立即对该用户的在线会话生效。</div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="rolesVisible = false">取消</button>',
            '        <button class="c blue" :disabled="rolesSubmitting" @click="submitRoles">{{ rolesSubmitting ? "保存中…" : "保存" }}</button></div>',
            '    </div>',
            '  </div>',

            // 弹层: 授权审核
            '  <div class="pmask" :class="{ show: reviewVisible }" @click.self="reviewVisible = false">',
            '    <div class="pcard w440" v-if="reviewTarget">',
            '      <div class="ph"><b>授权审核</b><span class="sp"></span><span class="x" @click="reviewVisible = false">✕</span></div>',
            '      <div class="psum"><div class="l1">{{ reviewTarget.displayName }}（{{ reviewTarget.username }}）</div>',
            '        <div class="l2">当前授权状态：{{ authLabel(reviewTarget.authorizationStatus) }}</div></div>',
            '      <div class="fld"><label>审核备注</label><textarea class="pta" v-model="reviewReason" maxlength="200" placeholder="可空，拒绝时建议填写"></textarea></div>',
            '      <div class="pbtn"><button class="c green" :disabled="reviewSubmitting" @click="submitReview(\'APPROVE\')">通过授权</button>',
            '        <span class="sp"></span><button class="c redline" :disabled="reviewSubmitting" @click="submitReview(\'REJECT\')">拒绝授权</button>',
            '        <button class="c ghost" @click="reviewVisible = false">取消</button></div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var query = Vue.reactive({ keyword: '', status: '', identityType: '', authorizationStatus: '', roleId: '' });
            var rows = Vue.ref([]);
            var total = Vue.ref(0);
            var pageNum = Vue.ref(1);
            var pageSize = Vue.ref(10);
            var loading = Vue.ref(false);
            var roles = Vue.ref([]);

            var rolesVisible = Vue.ref(false);
            var rolesTarget = Vue.ref({});
            var selectedRoleIds = Vue.ref([]);
            var rolesSubmitting = Vue.ref(false);

            var reviewVisible = Vue.ref(false);
            var reviewTarget = Vue.ref({});
            var reviewReason = Vue.ref('');
            var reviewSubmitting = Vue.ref(false);

            function toQuery() {
                var params = ['pageNum=' + pageNum.value, 'pageSize=' + pageSize.value];
                if (query.keyword) params.push('keyword=' + encodeURIComponent(query.keyword));
                if (query.status) params.push('status=' + query.status);
                if (query.identityType) params.push('identityType=' + query.identityType);
                if (query.authorizationStatus) params.push('authorizationStatus=' + query.authorizationStatus);
                if (query.roleId) params.push('roleId=' + query.roleId);
                return params.join('&');
            }

            function load() {
                loading.value = true;
                api.get('/api/admin/users?' + toQuery())
                    .then(function (page) {
                        rows.value = page.records || [];
                        total.value = page.total || 0;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { loading.value = false; });
            }

            function reload() {
                pageNum.value = 1;
                load();
            }

            function loadRoles() {
                api.get('/api/admin/roles')
                    .then(function (list) { roles.value = list || []; })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }

            function openRoles(row) {
                rolesTarget.value = row;
                selectedRoleIds.value = (row.roles || []).map(function (role) { return role.roleId; });
                rolesVisible.value = true;
            }

            function submitRoles() {
                rolesSubmitting.value = true;
                api.put('/api/admin/users/' + rolesTarget.value.userId + '/roles',
                        { roleIds: selectedRoleIds.value })
                    .then(function (updated) {
                        El.ElMessage.success('角色已更新，在线会话已刷新');
                        rolesVisible.value = false;
                        load();
                        void updated;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { rolesSubmitting.value = false; });
            }

            function openReview(row) {
                reviewTarget.value = row;
                reviewReason.value = '';
                reviewVisible.value = true;
            }

            function submitReview(action) {
                reviewSubmitting.value = true;
                api.post('/api/admin/users/' + reviewTarget.value.userId + '/authorization',
                        { action: action, reason: reviewReason.value.trim() || null })
                    .then(function () {
                        El.ElMessage.success(action === 'APPROVE' ? '已通过授权' : '已拒绝授权');
                        reviewVisible.value = false;
                        load();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { reviewSubmitting.value = false; });
            }

            function toggleStatus(row) {
                var disabling = row.status === 'ENABLED';
                El.ElMessageBox.confirm(
                        (disabling ? '停用后该用户将立即被强制下线，确定停用「' : '确定启用「') +
                        row.displayName + '」？', '确认操作',
                        { type: disabling ? 'warning' : 'info', confirmButtonText: '确定', cancelButtonText: '取消' })
                    .then(function () {
                        return api.post('/api/admin/users/' + row.userId + '/status',
                            { status: disabling ? 'DISABLED' : 'ENABLED' });
                    })
                    .then(function () {
                        El.ElMessage.success(disabling ? '已停用并强制下线' : '已启用');
                        load();
                    })
                    .catch(function (error) {
                        if (error !== 'cancel' && error !== 'close') {
                            El.ElMessage.error(error.message || '操作失败');
                        }
                    });
            }

            function toggleRole(roleId) {
                var idx = selectedRoleIds.value.indexOf(roleId);
                if (idx >= 0) selectedRoleIds.value.splice(idx, 1);
                else selectedRoleIds.value.push(roleId);
            }

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

            function authTone(value) {
                return { BASIC: 'i', PENDING: 'a', AUTHORIZED: 'g', REJECTED: 'r' }[value] || 'n2';
            }

            load();
            loadRoles();

            return {
                identityOptions: IDENTITY_OPTIONS,
                authOptions: AUTH_OPTIONS,
                query: query, rows: rows, total: total, pageNum: pageNum, pageSize: pageSize,
                loading: loading, roles: roles,
                totalPages: totalPages, pageNumbers: pageNumbers, goPage: goPage, toggleRole: toggleRole,
                authTone: authTone,
                rolesVisible: rolesVisible, rolesTarget: rolesTarget, selectedRoleIds: selectedRoleIds,
                rolesSubmitting: rolesSubmitting,
                reviewVisible: reviewVisible, reviewTarget: reviewTarget, reviewReason: reviewReason,
                reviewSubmitting: reviewSubmitting,
                load: load, reload: reload,
                openRoles: openRoles, submitRoles: submitRoles,
                openReview: openReview, submitReview: submitReview, toggleStatus: toggleStatus,
                identityLabel: function (value) { return labelOf(IDENTITY_OPTIONS, value); },
                authLabel: function (value) { return labelOf(AUTH_OPTIONS, value); },
                authTag: function (value) {
                    return { BASIC: 'info', PENDING: 'warning', AUTHORIZED: 'success', REJECTED: 'danger' }[value] || 'info';
                },
                fmt: api.fmtDateTime,
                SearchIcon: ElementPlusIconsVue.Search,
                RefreshIcon: ElementPlusIconsVue.Refresh
            };
        }
    };
})();
