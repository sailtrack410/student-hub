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
            '<div class="page-card">',
            '  <div class="toolbar">',
            '    <el-input v-model.trim="query.keyword" placeholder="用户名 / 昵称 / 手机号" clearable style="width:240px" @keyup.enter="reload" @clear="reload" />',
            '    <el-select v-model="query.status" placeholder="账号状态" clearable style="width:130px" @change="reload">',
            '      <el-option label="启用" value="ENABLED" />',
            '      <el-option label="停用" value="DISABLED" />',
            '    </el-select>',
            '    <el-select v-model="query.identityType" placeholder="身份类型" clearable style="width:130px" @change="reload">',
            '      <el-option v-for="item in identityOptions" :key="item.value" :label="item.label" :value="item.value" />',
            '    </el-select>',
            '    <el-select v-model="query.authorizationStatus" placeholder="授权状态" clearable style="width:130px" @change="reload">',
            '      <el-option v-for="item in authOptions" :key="item.value" :label="item.label" :value="item.value" />',
            '    </el-select>',
            '    <el-select v-model="query.roleId" placeholder="角色" clearable style="width:150px" @change="reload">',
            '      <el-option v-for="role in roles" :key="role.roleId" :label="role.roleName" :value="role.roleId" />',
            '    </el-select>',
            '    <el-button type="primary" :icon="SearchIcon" @click="reload">搜索</el-button>',
            '    <div class="grow"></div>',
            '    <el-button :icon="RefreshIcon" @click="reload">刷新</el-button>',
            '  </div>',

            '  <el-table :data="rows" v-loading="loading" stripe>',
            '    <el-table-column prop="username" label="用户名" width="170" show-overflow-tooltip />',
            '    <el-table-column prop="displayName" label="昵称" width="140" show-overflow-tooltip />',
            '    <el-table-column prop="phone" label="手机号" width="130">',
            '      <template #default="scope">{{ scope.row.phone || "-" }}</template>',
            '    </el-table-column>',
            '    <el-table-column label="身份" width="90">',
            '      <template #default="scope">{{ identityLabel(scope.row.identityType) }}</template>',
            '    </el-table-column>',
            '    <el-table-column label="角色" min-width="200">',
            '      <template #default="scope">',
            '        <div class="tag-row">',
            '          <el-tag v-for="role in scope.row.roles" :key="role.roleId" size="small" effect="plain">{{ role.roleName }}</el-tag>',
            '          <span v-if="!scope.row.roles.length" class="muted">无角色</span>',
            '        </div>',
            '      </template>',
            '    </el-table-column>',
            '    <el-table-column label="授权状态" width="100">',
            '      <template #default="scope">',
            '        <el-tag :type="authTag(scope.row.authorizationStatus)" size="small">{{ authLabel(scope.row.authorizationStatus) }}</el-tag>',
            '      </template>',
            '    </el-table-column>',
            '    <el-table-column label="账号状态" width="90">',
            '      <template #default="scope">',
            '        <el-tag :type="scope.row.status === \'ENABLED\' ? \'success\' : \'danger\'" size="small">',
            '          {{ scope.row.status === \'ENABLED\' ? \'启用\' : \'停用\' }}',
            '        </el-tag>',
            '      </template>',
            '    </el-table-column>',
            '    <el-table-column label="最近登录" width="150">',
            '      <template #default="scope">{{ fmt(scope.row.lastLoginAt) }}</template>',
            '    </el-table-column>',
            '    <el-table-column label="操作" width="230" fixed="right">',
            '      <template #default="scope">',
            '        <el-button link type="primary" @click="openRoles(scope.row)">分配角色</el-button>',
            '        <el-button link type="warning" @click="openReview(scope.row)">授权审核</el-button>',
            '        <el-button v-if="scope.row.status === \'ENABLED\'" link type="danger" @click="toggleStatus(scope.row)">停用</el-button>',
            '        <el-button v-else link type="success" @click="toggleStatus(scope.row)">启用</el-button>',
            '      </template>',
            '    </el-table-column>',
            '  </el-table>',
            '  <div class="pager">',
            '    <el-pagination layout="total, prev, pager, next, sizes" :total="total"',
            '                   v-model:current-page="pageNum" v-model:page-size="pageSize"',
            '                   :page-sizes="[10, 20, 50]" @current-change="load" @size-change="reload" />',
            '  </div>',

            '  <el-dialog v-model="rolesVisible" title="分配角色" width="480px">',
            '    <p class="muted" style="margin-top:0">用户：{{ rolesTarget.displayName }}（{{ rolesTarget.username }}）</p>',
            '    <el-checkbox-group v-model="selectedRoleIds">',
            '      <el-checkbox v-for="role in roles" :key="role.roleId" :value="role.roleId" :label="role.roleName"',
            '                   style="display:flex; margin-bottom:8px" />',
            '    </el-checkbox-group>',
            '    <p class="form-hint">授予服务人员/调度员会自动将身份联动为「后勤」并标记已授权；撤销后回落。调整立即对该用户的在线会话生效。</p>',
            '    <template #footer>',
            '      <el-button @click="rolesVisible = false">取消</el-button>',
            '      <el-button type="primary" :loading="rolesSubmitting" @click="submitRoles">保存</el-button>',
            '    </template>',
            '  </el-dialog>',

            '  <el-dialog v-model="reviewVisible" title="授权审核" width="440px">',
            '    <p class="muted" style="margin-top:0">用户：{{ reviewTarget.displayName }}（{{ reviewTarget.username }}），当前授权状态：{{ authLabel(reviewTarget.authorizationStatus) }}</p>',
            '    <el-input v-model="reviewReason" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="审核备注（可空，拒绝时建议填写）" />',
            '    <template #footer>',
            '      <el-button @click="reviewVisible = false">取消</el-button>',
            '      <el-button type="success" :loading="reviewSubmitting" @click="submitReview(\'APPROVE\')">通过授权</el-button>',
            '      <el-button type="danger" :loading="reviewSubmitting" @click="submitReview(\'REJECT\')">拒绝授权</el-button>',
            '    </template>',
            '  </el-dialog>',
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

            load();
            loadRoles();

            return {
                identityOptions: IDENTITY_OPTIONS,
                authOptions: AUTH_OPTIONS,
                query: query, rows: rows, total: total, pageNum: pageNum, pageSize: pageSize,
                loading: loading, roles: roles,
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
