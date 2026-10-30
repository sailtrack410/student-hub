/* 组织架构：单根树 + 校区范围（campus_scope），校区管理并入本页。
   数据模型：学校 → 学院/部门 → 专业；校区是节点属性，不是树层级；业务数据仍按 campus_id 隔离。
   接口：GET/POST/PUT/DELETE /api/admin/organizations、POST /{id}/move、GET /leader-candidates；
         GET/POST /api/admin/campuses、POST /api/admin/campuses/{id}。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var TYPE_OPTIONS = ['COLLEGE', 'MAJOR', 'DEPARTMENT', 'OFFICE', 'CLASS', 'TEAM'];
    var SCOPE_OPTIONS = [
        { value: 'CAMPUS', label: '具体校区' },
        { value: 'SCHOOL', label: '校级' },
        { value: 'MULTI', label: '跨校区' }
    ];
    var STATUSES = [{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }];

    window.AdminPages.OrganizationsPage = {
        name: 'OrganizationsPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="page-h"><div><h1>组织架构</h1><div class="sub">学校 → 学院 → 专业 → 班级；校区是节点属性，业务数据仍按校区隔离</div></div>',
            '    <div class="r"><button class="btn2" @click="reload">刷新</button><button class="btn2" @click="openCampusManager">校区管理</button><button class="c blue" @click="openCreate(null)">新增组织</button></div></div>',

            '  <div class="org-campus-strip">',
            '    <div class="org-campus-card" :class="{on: filter === \'all\'}" @click="setFilter(\'all\')">',
            '      <div class="nm">全部组织 <span class="bdg plain">{{ rows.length }}</span></div>',
            '      <div class="meta">学校 → 学院 / 部门 → 专业 → 班级</div>',
            '    </div>',
            '    <div class="org-campus-card" :class="{on: filter === \'school\'}" @click="setFilter(\'school\')">',
            '      <div class="nm">校级 <span class="bdg n2">{{ counts.school }}</span></div>',
            '      <div class="meta">教务处 / 图书馆 / 根组织</div>',
            '    </div>',
            '    <div class="org-campus-card" :class="{on: filter === \'multi\'}" @click="setFilter(\'multi\')">',
            '      <div class="nm">跨校区 <span class="bdg i">{{ counts.multi }}</span></div>',
            '      <div class="meta">跨校区学院 / 专业</div>',
            '    </div>',
            '    <div class="org-campus-card" v-for="c in campuses" :key="c.campusId" :class="{on: filter === \'campus:\' + c.campusId}" @click="setFilter(\'campus:\' + c.campusId)">',
            '      <div class="nm">{{ c.campusName }} <span class="bdg" :class="c.status === \'ENABLED\' ? \'g\' : \'a\'">{{ c.status === "ENABLED" ? "启用" : "停用" }}</span>',
            '        <span class="op" style="margin-left:auto" @click.stop="openCampusEdit(c)">编辑</span></div>',
            '      <div class="meta">{{ c.campusCode }} · 组织 {{ counts.campus[c.campusId] || 0 }} · 用户 {{ c.userCount }}</div>',
            '    </div>',
            '    <div class="org-campus-card add" @click="openCampusCreate">＋ 新增校区</div>',
            '  </div>',

            '  <div class="lcard" style="flex:1;display:flex;flex-direction:column;min-height:0">',
            '    <div class="cap">组织树<span class="cnt" v-if="treeRows.length">当前视图 {{ treeRows.length }} / {{ rows.length }} 个节点</span></div>',
            '    <div style="display:flex;gap:8px;padding:0 16px 12px;align-items:center;flex-wrap:wrap">',
            '      <div class="srch" style="width:280px;background:var(--tk-input)"><input v-model.trim="searchKeyword" placeholder="搜索班级 / 专业 / 学院名称或编码" /></div>',
            '      <button class="os-btn sm" @click="expandAll(true)">展开全部</button>',
            '      <button class="os-btn sm" @click="expandAll(false)">收起全部</button>',
            '      <span style="font-size:12px;color:var(--tk-muted)">共 {{ rows.length }} 个节点（含班级）；搜索时自动展开命中路径</span>',
            '    </div>',
            '    <div class="tblw" style="flex:1;overflow:auto"><table class="pk">',
            '      <thead><tr><th>组织名称 / 校区范围</th><th style="width:150px">编码</th><th style="width:96px">类型</th><th style="width:130px">负责人</th><th style="width:86px">排序</th><th style="width:112px">子组织 / 用户</th><th style="width:84px">状态</th><th style="width:220px" class="r">操作</th></tr></thead>',
            '      <tbody>',
            '        <tr v-if="!treeRows.length && !loading"><td colspan="8" style="text-align:center;color:var(--tk-muted);padding:48px 0">{{ searchKeyword ? "没有匹配的组织" : "当前范围暂无组织" }}</td></tr>',
            '        <tr v-for="o in treeRows" :key="o.organizationId">',
            '          <td><div :style="{ paddingLeft: (o.depth * 18) + \'px\' }" class="org-scope-cell">',
            '            <span v-if="o.hasChildren" style="cursor:pointer;color:var(--tk-muted);width:14px;flex:none;text-align:center" @click="toggle(o)">{{ o.expanded ? "▾" : "▸" }}</span>',
            '            <span v-else style="width:14px;flex:none"></span>',
            '            <span class="org-name">{{ o.organizationName }}</span>',
            '            <span class="bdg n2" v-if="o.campusScope === \'SCHOOL\'">校级</span>',
            '            <span class="bdg i" v-else-if="o.campusScope === \'MULTI\'">跨校区</span>',
            '            <span class="bdg plain" v-else-if="o.campusScope === \'CAMPUS\'">{{ campusName(o.campusId) }}</span>',
            '            <span class="bdg plain" v-if="o.parentId == null">根</span>',
            '          </div></td>',
            '          <td style="font-family:monospace;color:var(--tk-muted)">{{ o.organizationCode }}</td>',
            '          <td><span class="bdg plain">{{ o.organizationType }}</span></td>',
            '          <td><span v-if="o.leaderName" style="font-size:12px;color:var(--tk-text2)">{{ o.leaderName }}</span><span v-else style="font-size:11px;color:var(--tk-muted)">未指定</span></td>',
            '          <td><div style="display:flex;align-items:center;gap:6px"><span style="font-size:11px;color:var(--tk-muted);min-width:22px">{{ o.sortOrder }}</span>',
            '            <template v-if="o.parentId != null"><button class="os-btn sm" style="padding:0 5px" title="上移" @click="move(o, \'UP\')">↑</button>',
            '            <button class="os-btn sm" style="padding:0 5px" title="下移" @click="move(o, \'DOWN\')">↓</button></template></div></td>',
            '          <td><span class="bdg plain" v-if="o.childCount">子 {{ o.childCount }}</span><span class="bdg plain" v-if="o.userCount" style="margin-left:4px">人 {{ o.userCount }}</span><span v-if="!o.childCount && !o.userCount" style="font-size:11px;color:var(--tk-muted)">—</span></td>',
            '          <td><span class="bdg" :class="o.status === \'ENABLED\' ? \'g\' : \'a\'">{{ o.status === "ENABLED" ? "启用" : "停用" }}</span></td>',
            '          <td class="r" style="white-space:nowrap"><template v-if="canManageNode(o)">',
            '            <span class="op" @click="openEdit(o)">编辑</span> · <span class="op" @click="openCreate(o)">添加子组织</span>',
            '            · <span v-if="o.parentId != null" class="op" :style="{ color: deleteBlocked(o) ? \'var(--tk-muted)\' : \'var(--tk-bad)\' }" @click="removeOrg(o)" :title="deleteBlocked(o) ? \'存在子组织或用户，不能删除\' : \'删除组织\'">删除</span>',
            '            <span v-else class="op" style="color:var(--tk-muted);cursor:default">根节点</span></template>',
            '            <span v-else class="op" style="color:var(--tk-muted);cursor:default">{{ o.parentId == null ? "平台维护" : "只读" }}</span></td>',
            '        </tr>',
            '      </tbody></table></div>',
            '  </div>',

            '  <div class="pmask" :class="{show: visible}" @click.self="visible = false">',
            '    <div class="pcard w560" v-if="visible">',
            '      <div class="ph"><b>{{ editing ? "编辑组织" : "新增组织" }}</b><span class="no">{{ form.organizationCode || "新组织" }}</span><span class="sp"></span><span class="x" @click="visible = false">✕</span></div>',
            '      <div style="display:flex;gap:10px">',
            '        <div class="fld" style="flex:1"><label>组织编码 <em>*</em></label><div class="pin"><input v-model.trim="form.organizationCode" maxlength="64" placeholder="如 COLLEGE_AI" /></div></div>',
            '        <div class="fld" style="flex:1"><label>组织类型</label><div class="pin"><select v-model="form.organizationType" style="width:100%" :disabled="isRootEdit"><option v-for="t in typeOptions" :key="t" :value="t">{{ t }}</option></select></div></div>',
            '      </div>',
            '      <div class="fld"><label>组织名称 <em>*</em></label><div class="pin"><input v-model.trim="form.organizationName" maxlength="128" placeholder="如 人工智能学院" /></div></div>',
            '      <div style="display:flex;gap:10px">',
            '        <div class="fld" style="flex:1"><label>校区范围</label><div class="pin"><select v-model="form.campusScope" style="width:100%" :disabled="isRootEdit" @change="onScopeChange"><option v-for="s in scopeOptions" :key="s.value" :value="s.value">{{ s.label }}</option></select></div></div>',
            '        <div class="fld" style="flex:1" v-if="form.campusScope === \'CAMPUS\'"><label>所属校区 <em>*</em></label><div class="pin"><select v-model="form.campusId" style="width:100%"><option :value="null">请选择校区</option><option v-for="c in campuses" :key="c.campusId" :value="c.campusId">{{ c.campusName }}</option></select></div></div>',
            '      </div>',
            '      <div class="fld"><label>父组织</label><div class="pin"><select v-model="form.parentId" style="width:100%" :disabled="isRootEdit"><option :value="null" :disabled="!isRootEdit">无（根节点，仅编辑根组织时可用）</option><option v-for="o in parentOptions" :key="o.organizationId" :value="o.organizationId">{{ indentLabel(o.depth) }}{{ o.organizationName }} · {{ scopeText(o) }}</option></select></div></div>',
            '      <div style="display:flex;gap:10px"><div class="fld" style="flex:1"><label>排序</label><div class="pin"><input type="number" v-model.number="form.sortOrder" /></div></div>',
            '        <div class="fld" style="flex:1"><label>状态</label><div class="pin"><select v-model="form.status" style="width:100%"><option v-for="s in statuses" :key="s.value" :value="s.value">{{ s.label }}</option></select></div></div></div>',
            '      <div class="fld"><label>组织负责人</label><div class="pin"><select v-model="form.leaderId" style="width:100%"><option :value="null">未指定</option>',
            '        <option v-for="u in candidates" :key="u.userId" :value="u.userId">{{ u.displayName }}（{{ u.username }}）{{ u.defaultOrganizationName ? " · " + u.defaultOrganizationName : "" }}</option></select></div></div>',
            '      <div class="infobar">校区范围决定节点归属：具体校区节点参与该校区数据范围；校级/跨校区节点两个校区都可见。负责人只用于展示与联系，不授予额外权限。</div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="visible = false">取消</button>',
            '        <button class="c blue" :disabled="saving" @click="save">{{ saving ? "保存中…" : "保存" }}</button></div>',
            '    </div>',
            '  </div>',

            '  <div class="pmask" :class="{show: campusVisible}" @click.self="campusVisible = false">',
            '    <div class="pcard w640" v-if="campusVisible">',
            '      <div class="ph"><b>{{ campusMode === \'edit\' ? (campusEditing ? "编辑校区" : "新增校区") : "校区管理" }}</b><span class="no">{{ campusMode === \'edit\' ? "校区编码创建后不可修改" : "组织节点属性 / 业务数据隔离维度" }}</span><span class="sp"></span><span class="x" @click="campusVisible = false">✕</span></div>',
            '      <template v-if="campusMode === \'list\'">',
            '        <div style="display:flex;align-items:center;gap:10px"><span style="font-size:12px;color:var(--tk-muted)">共 {{ campuses.length }} 个校区</span><span class="sp" style="flex:1"></span><button class="c blue" @click="openCampusCreate">新增校区</button></div>',
            '        <div class="tblw"><table class="pk">',
            '          <thead><tr><th style="width:130px">编码</th><th>名称</th><th style="width:80px">组织</th><th style="width:80px">用户</th><th style="width:86px">状态</th><th style="width:80px" class="r">操作</th></tr></thead>',
            '          <tbody><tr v-for="c in campuses" :key="c.campusId">',
            '            <td style="font-family:monospace;color:var(--tk-muted)">{{ c.campusCode }}</td>',
            '            <td style="font-weight:600">{{ c.campusName }}</td>',
            '            <td>{{ c.organizationCount }}</td><td>{{ c.userCount }}</td>',
            '            <td><span class="bdg" :class="c.status === \'ENABLED\' ? \'g\' : \'a\'">{{ c.status === "ENABLED" ? "启用" : "停用" }}</span></td>',
            '            <td class="r"><span class="op" @click="openCampusEdit(c)">编辑</span></td>',
            '          </tr></tbody></table></div>',
            '        <div class="infobar">停用前需至少保留一个启用校区；校区编码用于数据隔离与接口上下文，创建后不可修改。</div>',
            '      </template>',
            '      <template v-else>',
            '        <div class="fld"><label>校区编码 <em>*</em></label><div class="pin"><input v-model.trim="campusForm.campusCode" maxlength="32" placeholder="如 EAST" :disabled="!!campusEditing" /></div></div>',
            '        <div class="fld"><label>校区名称 <em>*</em></label><div class="pin"><input v-model.trim="campusForm.campusName" maxlength="128" placeholder="如 游仙校区" /></div></div>',
            '        <div class="fld"><label>状态</label><div class="pin"><select v-model="campusForm.status" style="width:100%"><option v-for="s in statuses" :key="s.value" :value="s.value">{{ s.label }}</option></select></div></div>',
            '        <div class="infobar">校区编码创建后不可修改；停用前需确保至少保留一个启用校区。组织、用户、服务目录、社区分区与广告均按 campus_id 隔离。</div>',
            '        <div class="pbtn"><button class="c ghost" @click="campusMode = \'list\'">返回列表</button><span class="sp"></span><button class="c blue" :disabled="campusSaving" @click="saveCampus">{{ campusSaving ? "保存中…" : "保存" }}</button></div>',
            '      </template>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;
            // 校级/跨校区组织只由平台管理员维护，普通管理员仅维护所属校区节点。
            // 页面可能在 /api/admin/context 返回前挂载，优先用登录态里的 platform:all 判断并保持响应式。
            var platformAdmin = Vue.computed(function () {
                var user = window.AdminApp && window.AdminApp.state && window.AdminApp.state.user;
                var roles = (user && user.roles) || [];
                if (roles.indexOf('PLATFORM_ADMIN') >= 0) return true;
                return !!(window.AdminContext && window.AdminContext.platformAdmin);
            });
            var rows = Vue.ref([]);
            var campuses = Vue.ref([]);
            var candidates = Vue.ref([]);
            var loading = Vue.ref(false);
            var filter = Vue.ref('all');
            var searchKeyword = Vue.ref('');
            var expanded = Vue.reactive({});
            var visible = Vue.ref(false);
            var editing = Vue.ref(null);
            var saving = Vue.ref(false);
            var campusVisible = Vue.ref(false);
            var campusMode = Vue.ref('list');
            var campusEditing = Vue.ref(null);
            var campusSaving = Vue.ref(false);

            var form = Vue.reactive({
                parentId: null, campusId: null, campusScope: 'SCHOOL',
                organizationCode: '', organizationName: '', organizationType: 'DEPARTMENT',
                sortOrder: 0, status: 'ENABLED', leaderId: null
            });
            var campusForm = Vue.reactive({ campusId: null, campusCode: '', campusName: '', status: 'ENABLED' });

            var rootOrg = Vue.computed(function () {
                return rows.value.filter(function (o) { return o.parentId == null; })[0] || null;
            });
            var campusMap = Vue.computed(function () {
                var map = {};
                campuses.value.forEach(function (c) { map[String(c.campusId)] = c; });
                return map;
            });
            var counts = Vue.computed(function () {
                var result = { school: 0, multi: 0, campus: {} };
                rows.value.forEach(function (o) {
                    if (o.campusScope === 'SCHOOL') result.school += 1;
                    else if (o.campusScope === 'MULTI') result.multi += 1;
                    else if (o.campusScope === 'CAMPUS' && o.campusId != null) {
                        var key = String(o.campusId);
                        result.campus[key] = (result.campus[key] || 0) + 1;
                    }
                });
                return result;
            });

            function campusName(campusId) {
                var campus = campusMap.value[String(campusId)];
                return campus ? campus.campusName : '未分配校区';
            }

            function scopeText(o) {
                if (o.campusScope === 'SCHOOL') return '校级';
                if (o.campusScope === 'MULTI') return '跨校区';
                if (o.campusScope === 'CAMPUS') return campusName(o.campusId);
                return o.campusId == null ? '校级' : campusName(o.campusId);
            }

            /** 按 parentId 组装树并扁平化，附带 depth / hasChildren / expanded。 */
            function buildOrdered(list, forceExpand) {
                var byParent = {};
                var seen = {};
                list.forEach(function (o) {
                    var key = o.parentId == null ? 'root' : String(o.parentId);
                    (byParent[key] = byParent[key] || []).push(o);
                });
                var result = [];
                function walk(key, depth) {
                    (byParent[key] || []).forEach(function (o) {
                        if (seen[String(o.organizationId)]) return;
                        seen[String(o.organizationId)] = true;
                        var children = byParent[String(o.organizationId)] || [];
                        var open = forceExpand || expanded[o.organizationId] !== false;
                        result.push(Object.assign({}, o, {
                            depth: depth, hasChildren: children.length > 0, expanded: open
                        }));
                        if (open) walk(String(o.organizationId), depth + 1);
                    });
                }
                walk('root', 0);
                // 父组织缺失（数据被删除或超出当前校区可见范围）的孤儿节点兜底展示。
                list.forEach(function (o) {
                    if (!seen[String(o.organizationId)]) {
                        seen[String(o.organizationId)] = true;
                        result.push(Object.assign({}, o, { depth: 0, hasChildren: false, expanded: true }));
                    }
                });
                return result;
            }

            var visibleRows = Vue.computed(function () {
                var key = filter.value;
                var base;
                if (key === 'all') {
                    base = rows.value;
                } else {
                    var byId = {};
                rows.value.forEach(function (o) { byId[String(o.organizationId)] = o; });
                function matches(o) {
                    if (key === 'school') return o.campusScope === 'SCHOOL';
                    if (key === 'multi') return o.campusScope === 'MULTI';
                    if (key.indexOf('campus:') === 0) {
                        return o.campusScope === 'CAMPUS' && String(o.campusId) === key.substring(7);
                    }
                    return true;
                }
                var visibleIds = {};
                rows.value.forEach(function (o) {
                    if (!matches(o)) return;
                    var current = o;
                    var guard = 0;
                    while (current && guard++ < 64) {
                        visibleIds[String(current.organizationId)] = true;
                        current = current.parentId == null ? null : byId[String(current.parentId)];
                    }
                });
                    base = rows.value.filter(function (o) { return visibleIds[String(o.organizationId)]; });
                }
                var keyword = searchKeyword.value.trim().toLowerCase();
                if (!keyword) return base;
                var byIdForSearch = {};
                base.forEach(function (o) { byIdForSearch[String(o.organizationId)] = o; });
                var matched = {};
                base.forEach(function (o) {
                    if (String(o.organizationName || '').toLowerCase().indexOf(keyword) < 0
                            && String(o.organizationCode || '').toLowerCase().indexOf(keyword) < 0) {
                        return;
                    }
                    var current = o;
                    var guard = 0;
                    while (current && guard++ < 64) {
                        matched[String(current.organizationId)] = true;
                        current = current.parentId == null ? null : byIdForSearch[String(current.parentId)];
                    }
                });
                return base.filter(function (o) { return matched[String(o.organizationId)]; });
            });

            var treeRows = Vue.computed(function () {
                return buildOrdered(visibleRows.value, searchKeyword.value.trim() !== '');
            });

            var parentOptions = Vue.computed(function () {
                var ordered = buildOrdered(rows.value, true);
                if (!platformAdmin.value) {
                    ordered = ordered.filter(function (o) {
                        return o.parentId == null || o.campusScope === 'CAMPUS';
                    });
                }
                var selfId = editing.value ? editing.value.organizationId : null;
                if (!selfId) return ordered;
                var children = {};
                rows.value.forEach(function (o) {
                    if (o.parentId != null) {
                        (children[String(o.parentId)] = children[String(o.parentId)] || []).push(o.organizationId);
                    }
                });
                var blocked = {};
                blocked[String(selfId)] = true;
                (function collect(id) {
                    (children[String(id)] || []).forEach(function (childId) {
                        blocked[String(childId)] = true;
                        collect(childId);
                    });
                })(selfId);
                return ordered.filter(function (o) { return !blocked[String(o.organizationId)]; });
            });

            var isRootEdit = Vue.computed(function () {
                return !!(editing.value && editing.value.parentId == null);
            });
            var typeOptions = Vue.computed(function () {
                return isRootEdit.value ? ['ROOT'] : TYPE_OPTIONS;
            });
            var scopeOptions = Vue.computed(function () {
                return platformAdmin.value ? SCOPE_OPTIONS
                    : SCOPE_OPTIONS.filter(function (item) { return item.value === 'CAMPUS'; });
            });

            function loadAll() {
                loading.value = true;
                Promise.all([
                    api.get('/api/admin/organizations'),
                    api.get('/api/admin/campuses')
                ]).then(function (result) {
                    rows.value = result[0] || [];
                    campuses.value = result[1] || [];
                }).catch(function (e) {
                    El.ElMessage.error(e.message);
                }).finally(function () {
                    loading.value = false;
                });
            }

            function loadCandidates() {
                api.get('/api/admin/organizations/leader-candidates')
                    .then(function (list) { candidates.value = list || []; })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }

            function setFilter(key) {
                filter.value = key;
            }

            function fallbackCampusId() {
                var stored = api.getCampusId();
                if (stored) return stored; // 接口返回的 campusId 为字符串，避免 select 值类型不一致
                return campuses.value.length ? campuses.value[0].campusId : null;
            }

            /** 新建时的默认范围/类型：跟随父节点；“新增组织”入口跟随当前校区筛选。 */
            function resetForm(parent) {
                var selectedCampus = filter.value.indexOf('campus:') === 0
                    ? filter.value.substring(7) : null;
                var root = rootOrg.value;
                form.parentId = parent ? parent.organizationId : (root ? root.organizationId : null);
                form.organizationCode = '';
                form.organizationName = '';
                form.sortOrder = 0;
                form.status = 'ENABLED';
                form.leaderId = null;
                form.campusId = null;

                if (!platformAdmin.value) {
                    // 普通管理员只在当前校区建组织，父节点默认为根或所属校区节点。
                    form.campusScope = 'CAMPUS';
                    form.campusId = selectedCampus || fallbackCampusId();
                    form.organizationType = 'COLLEGE';
                    return;
                }
                if (parent && root && parent.organizationId === root.organizationId) {
                    if (filter.value === 'multi') {
                        form.campusScope = 'MULTI';
                        form.organizationType = 'COLLEGE';
                    } else if (selectedCampus) {
                        form.campusScope = 'CAMPUS';
                        form.campusId = selectedCampus;
                        form.organizationType = 'COLLEGE';
                    } else {
                        form.campusScope = 'SCHOOL';
                        form.organizationType = 'DEPARTMENT';
                    }
                } else if (parent) {
                    form.campusScope = parent.campusScope || 'CAMPUS';
                    form.organizationType = parent.organizationType === 'COLLEGE' ? 'MAJOR'
                        : parent.organizationType === 'MAJOR' ? 'CLASS' : 'DEPARTMENT';
                    if (form.campusScope === 'CAMPUS') {
                        form.campusId = parent.campusId;
                    } else if (form.campusScope === 'MULTI') {
                        // 跨校区父节点下默认建具体校区子节点（班级/专业必须落到校区）。
                        form.campusScope = 'CAMPUS';
                        form.campusId = selectedCampus || fallbackCampusId();
                    }
                } else if (filter.value === 'multi') {
                    form.campusScope = 'MULTI';
                    form.organizationType = 'COLLEGE';
                } else if (selectedCampus) {
                    form.campusScope = 'CAMPUS';
                    form.campusId = selectedCampus;
                    form.organizationType = 'COLLEGE';
                } else {
                    form.campusScope = 'SCHOOL';
                    form.organizationType = 'DEPARTMENT';
                }
            }

            function openCreate(parent) {
                editing.value = null;
                resetForm(parent);
                visible.value = true;
            }

            function openEdit(o) {
                editing.value = o;
                form.parentId = o.parentId == null ? null : o.parentId;
                form.campusScope = o.campusScope || (o.campusId == null ? 'SCHOOL' : 'CAMPUS');
                form.campusId = o.campusId || null;
                form.organizationCode = o.organizationCode;
                form.organizationName = o.organizationName;
                form.organizationType = o.organizationType;
                form.sortOrder = o.sortOrder == null ? 0 : o.sortOrder;
                form.status = o.status;
                form.leaderId = o.leaderId || null;
                visible.value = true;
            }

            function onScopeChange() {
                if (form.campusScope !== 'CAMPUS') {
                    form.campusId = null;
                } else if (!form.campusId) {
                    form.campusId = fallbackCampusId();
                }
            }

            function save() {
                if (!form.organizationCode || !form.organizationName) {
                    El.ElMessage.warning('请填写组织编码和名称');
                    return;
                }
                if (form.campusScope === 'CAMPUS' && !form.campusId) {
                    El.ElMessage.warning('请选择所属校区');
                    return;
                }
                saving.value = true;
                var request = {
                    parentId: form.parentId == null ? null : form.parentId,
                    campusId: form.campusScope === 'CAMPUS' ? form.campusId : null,
                    campusScope: form.campusScope,
                    organizationCode: form.organizationCode,
                    organizationName: form.organizationName,
                    organizationType: form.organizationType,
                    sortOrder: form.sortOrder,
                    status: form.status,
                    leaderId: form.leaderId || null
                };
                var promise = editing.value
                    ? api.put('/api/admin/organizations/' + editing.value.organizationId, request)
                    : api.post('/api/admin/organizations', request);
                promise.then(function () {
                    El.ElMessage.success('组织已保存');
                    visible.value = false;
                    loadAll();
                }).catch(function (e) {
                    El.ElMessage.error(e.message);
                }).finally(function () {
                    saving.value = false;
                });
            }

            function toggle(o) {
                expanded[o.organizationId] = expanded[o.organizationId] === false;
            }

            function expandAll(open) {
                rows.value.forEach(function (o) { expanded[o.organizationId] = !!open; });
            }

            function move(o, direction) {
                api.post('/api/admin/organizations/' + o.organizationId + '/move?direction=' + direction)
                    .then(function () { loadAll(); })
                    .catch(function (e) { El.ElMessage.warning(e.message); });
            }

            function deleteBlocked(o) {
                return o.parentId == null || (o.childCount || 0) > 0 || (o.userCount || 0) > 0;
            }

            function canManageNode(o) {
                return platformAdmin.value || o.campusScope === 'CAMPUS';
            }

            function removeOrg(o) {
                if (o.parentId == null) {
                    El.ElMessage.warning('根组织不能删除');
                    return;
                }
                if (deleteBlocked(o)) {
                    El.ElMessage.warning('该组织下还有 '
                        + (o.childCount ? o.childCount + ' 个子组织' : '')
                        + (o.childCount && o.userCount ? '、' : '')
                        + (o.userCount ? o.userCount + ' 个用户' : '')
                        + '，请先迁移后再删除');
                    return;
                }
                El.ElMessageBox.confirm(
                        '确定删除组织「' + o.organizationName + '」？删除为软删，可在数据库中恢复。',
                        '删除组织', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
                    .then(function () { return api.delete('/api/admin/organizations/' + o.organizationId); })
                    .then(function () { El.ElMessage.success('组织已删除'); loadAll(); })
                    .catch(function (e) {
                        if (e !== 'cancel' && e !== 'close') {
                            El.ElMessage.error(e.message || '删除失败');
                        }
                    });
            }

            function indentLabel(depth) {
                var label = '';
                for (var i = 0; i < (depth || 0); i++) label += '　';
                return label;
            }

            /* ---------- 校区管理（并入党） ---------- */
            function openCampusManager() {
                campusMode.value = 'list';
                campusVisible.value = true;
                api.get('/api/admin/campuses')
                    .then(function (list) { campuses.value = list || []; })
                    .catch(function (e) { El.ElMessage.error(e.message); });
            }

            function openCampusCreate() {
                campusMode.value = 'edit';
                campusEditing.value = null;
                campusForm.campusId = null;
                campusForm.campusCode = '';
                campusForm.campusName = '';
                campusForm.status = 'ENABLED';
                campusVisible.value = true;
            }

            function openCampusEdit(campus) {
                campusMode.value = 'edit';
                campusEditing.value = campus;
                campusForm.campusId = campus.campusId;
                campusForm.campusCode = campus.campusCode;
                campusForm.campusName = campus.campusName;
                campusForm.status = campus.status;
                campusVisible.value = true;
            }

            function saveCampus() {
                if (!campusForm.campusCode || !campusForm.campusName) {
                    El.ElMessage.warning('请填写校区编码和名称');
                    return;
                }
                campusSaving.value = true;
                var request = {
                    campusCode: campusForm.campusCode,
                    campusName: campusForm.campusName,
                    status: campusForm.status
                };
                var promise = campusEditing.value
                    ? api.post('/api/admin/campuses/' + campusEditing.value.campusId, request)
                    : api.post('/api/admin/campuses', request);
                promise.then(function () {
                    El.ElMessage.success('校区已保存');
                    loadAll();
                    campusMode.value = 'list';
                }).catch(function (e) {
                    El.ElMessage.error(e.message);
                }).finally(function () {
                    campusSaving.value = false;
                });
            }

            loadAll();
            loadCandidates();

            return {
                rows: rows, campuses: campuses, candidates: candidates,
                loading: loading, filter: filter, searchKeyword: searchKeyword, expanded: expanded,
                visible: visible, editing: editing, saving: saving, form: form,
                campusVisible: campusVisible, campusMode: campusMode,
                campusEditing: campusEditing, campusSaving: campusSaving, campusForm: campusForm,
                counts: counts, treeRows: treeRows, parentOptions: parentOptions,
                typeOptions: typeOptions, scopeOptions: scopeOptions, statuses: STATUSES,
                canManageNode: canManageNode,
                isRootEdit: isRootEdit, campusName: campusName, scopeText: scopeText,
                setFilter: setFilter, openCreate: openCreate, openEdit: openEdit, save: save,
                toggle: toggle, expandAll: expandAll, move: move, deleteBlocked: deleteBlocked, removeOrg: removeOrg,
                indentLabel: indentLabel, reload: loadAll, onScopeChange: onScopeChange,
                openCampusManager: openCampusManager, openCampusCreate: openCampusCreate,
                openCampusEdit: openCampusEdit, saveCampus: saveCampus
            };
        }
    };
})();
