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

    var SCOPE_OPTIONS = [
        { value: 'SELF', label: '仅本人' },
        { value: 'ASSIGNED', label: '被指派数据' },
        { value: 'ORGANIZATION', label: '本组织' },
        { value: 'ORGANIZATION_AND_CHILDREN', label: '本组织及下级' },
        { value: 'ALL', label: '全部数据' }
    ];

    var AUTH_OPTIONS = [
        { value: 'BASIC', label: '基础权限' },
        { value: 'PENDING', label: '待审核' },
        { value: 'AUTHORIZED', label: '已授权' },
        { value: 'REJECTED', label: '已拒绝' }
    ];

    var MATERIAL_OPTIONS = [
        { value: 'STUDENT_CARD', label: '学生证' },
        { value: 'ID_CARD', label: '身份证' },
        { value: 'ENROLLMENT_PROOF', label: '在读证明' },
        { value: 'TEACHER_CERT', label: '教师资格证' },
        { value: 'WORK_BADGE', label: '工作证/在职证明' },
        { value: 'OTHER', label: '其他材料' }
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
            '    <div><h1>用户管理</h1><div class="sub">账号、身份、角色授权与社区发布主体<span v-if="total"> · 共 {{ total }} 位用户</span></div></div>',
            '    <div class="r"><button class="btn2" @click="reload">刷新</button><button class="btn2" @click="openSso">SSO/花名册</button><button class="btn2" @click="openImport">导入用户</button><button class="btn2" @click="goCommunity">账号治理</button></div>',
            '  </div>',

            '  <div class="os-grid" style="grid-template-columns:repeat(4,1fr)">',
            '    <div class="os-kpi"><div class="k-label"><span class="dot"></span>本页用户</div><div class="k-val">{{ rows.length }}<small>人</small></div><div class="k-foot"><span>共 {{ total }} 位匹配用户</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-info)"></span>运营账号</div><div class="k-val">{{ pageOfficial }}<small>个</small></div><div class="k-foot"><span>抓取号 / 人工号 / 广告服务</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>待授权</div><div class="k-val">{{ pagePending }}<small>人</small></div><div class="k-foot"><span>本页待审核申请</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-bad)"></span>已停用</div><div class="k-val">{{ pageDisabled }}<small>人</small></div><div class="k-foot"><span>本页账号状态</span></div></div>',
            '  </div>',

            '  <div class="os-note"><b>身份层级</b> 平台身份（学生 / 教师 / 后勤 / 管理员）决定业务权限；社区账号类型（普通 / 抓取号 / 人工运营号 / 广告服务号）决定社区发布主体；角色与组织数据范围决定能看到和操作什么。官方与广告账号请到「账号治理」维护。</div>',

            '  <div class="os-note" v-if="sso" style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">',
            '    <b>SSO / 花名册</b>',
            '    <span class="os-tag" :class="sso.configured ? \'warn\' : \'muted\'">{{ sso.configured ? "已配置地址 · 未对接" : "未接入（NOT_CONFIGURED）" }}</span>',
            '    <span style="font-size:11px;color:var(--tk-text3);flex:1;min-width:220px">{{ sso.message }}</span>',
            '    <button class="os-btn sm" @click="openSso">查看说明</button>',
            '  </div>',

            '  <div class="os-note" style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">',
            '    <b>用户导入</b><span style="font-size:11px;color:var(--tk-text3);flex:1;min-width:240px">CSV 模板导入（同步执行，单次 ≤ 500 行 / 2MB，UTF-8）；支持 SKIP 跳过冲突、UPDATE 覆盖资料并追加角色。</span>',
            '    <button class="os-btn sm" @click="openImport">上传并预览</button>',
            '  </div>',

            '  <div class="os-panel" style="flex:1;min-height:0;display:flex;flex-direction:column">',
            '    <div class="os-panel-h" style="flex-wrap:wrap;row-gap:8px">',
            '      <h3>用户列表</h3><span class="hint">按官方账号与注册时间排序</span>',
            '      <div class="r" style="flex-wrap:wrap;gap:6px">',
            '        <div class="srch" style="width:206px;background:var(--tk-input)"><el-icon :size="13"><Search /></el-icon><input v-model.trim="query.keyword" placeholder="用户名 / 昵称 / 手机号" @keyup.enter="reload" /></div>',
            '        <select class="os-select" style="width:104px" v-model="query.status" @change="reload"><option value="">账号状态</option><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select>',
            '        <select class="os-select" style="width:104px" v-model="query.identityType" @change="reload"><option value="">全部身份</option><option v-for="item in identityOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select>',
            '        <select class="os-select" style="width:106px" v-model="query.authorizationStatus" @change="reload"><option value="">授权状态</option><option v-for="item in authOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select>',
            '        <select class="os-select" style="width:108px" v-model="query.official" @change="reload"><option value="">全部账号</option><option value="true">仅官方</option><option value="false">仅普通</option></select>',
            '        <select class="os-select" style="width:108px" v-model="query.roleId" @change="reload"><option value="">全部角色</option><option v-for="role in roles" :key="role.roleId" :value="role.roleId">{{ role.roleName }}</option></select>',
            '        <button class="os-btn primary sm" @click="reload">搜索</button>',
            '      </div>',
            '    </div>',
            '    <div style="overflow:auto;flex:1">',
            '      <table style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr><th style="width:24%">用户</th><th style="width:6%">身份</th><th style="width:8%">账号类型</th><th style="width:19%">角色</th><th style="width:7%">授权</th><th style="width:6%">状态</th><th style="width:10%">最近登录</th><th style="width:20%;text-align:right">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-if="!rows.length && !loading"><td colspan="8" class="os-empty">暂无用户</td></tr>',
            '          <tr v-if="loading"><td colspan="8" class="os-empty">加载中…</td></tr>',
            '          <tr v-for="row in rows" :key="row.userId">',
            '            <td><div style="display:flex;align-items:center;gap:8px;min-width:0"><span style="width:28px;height:28px;border-radius:7px;background:var(--tk-subtle);display:grid;place-items:center;font-size:11px;color:var(--tk-brand);flex:none">{{ (row.displayName || row.username || "?").slice(0,1) }}</span><div style="min-width:0"><b style="font-weight:550;color:var(--tk-text);display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ row.displayName || "未命名" }}</b><span class="os-mono" style="font-size:10px;color:var(--tk-muted)">@{{ row.username }}<span v-if="row.phone" style="margin-left:7px">{{ row.phone }}</span></span></div></div></td>',
            '            <td><span style="font-size:11px;color:var(--tk-text2)">{{ identityLabel(row.identityType) }}</span></td>',
            '            <td><span class="os-tag" :class="accountTypeTone(row)">{{ accountTypeLabel(row) }}</span><div v-if="row.officialTitle" style="font-size:10px;color:var(--tk-muted);margin-top:3px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ row.officialTitle }}</div></td>',
            '            <td><span class="os-tag plain" v-for="role in row.roles" :key="role.roleId" style="margin-right:4px;margin-bottom:2px">{{ role.roleName }}</span><span v-if="!row.roles.length" style="color:var(--tk-muted);font-size:11px">无角色</span></td>',
            '            <td><span class="os-tag" :class="authTone(row.authorizationStatus)">{{ authLabel(row.authorizationStatus) }}</span></td>',
            '            <td><span class="os-dot" :class="row.status === \'ENABLED\' ? \'ok\' : \'bad\'" style="margin-right:5px"></span><span style="font-size:11px;color:var(--tk-text3)">{{ row.status === "ENABLED" ? "启用" : "停用" }}</span></td>',
            '            <td style="font-size:10px;color:var(--tk-text3)">{{ fmt(row.lastLoginAt) }}</td>',
            '            <td style="text-align:right;white-space:nowrap">',
            '              <span style="font-size:11px;color:var(--tk-info);cursor:pointer" @click="openDetail(row)">详情</span>',
            '              <span style="font-size:11px;color:var(--tk-info);cursor:pointer;margin-left:8px" @click="openRoles(row)">身份角色</span>',
            '              <span v-if="row.authorizationStatus === \'PENDING\' || row.authorizationStatus === \'REJECTED\'" style="font-size:11px;color:var(--tk-warn);cursor:pointer;margin-left:8px" @click="openReview(row)">授权审核</span>',
            '              <span v-if="isOperations(row)" style="font-size:11px;color:var(--tk-brand);cursor:pointer;margin-left:8px" @click="goCommunity">账号治理</span>',
            '              <span v-else style="font-size:11px;color:var(--tk-text3);cursor:pointer;margin-left:8px" @click="openOfficial(row)">官方标识</span>',
            '              <span style="font-size:11px;margin-left:8px;cursor:pointer" :style="{ color: row.status === \'ENABLED\' ? \'var(--tk-bad)\' : \'var(--tk-ok)\' }" @click="toggleStatus(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</span>',
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
            '      <div v-if="selectedRoleIds.length" style="margin-top:12px;display:flex;flex-direction:column;gap:8px">',
            '        <div style="font-size:12px;font-weight:700;color:var(--tk-text)">组织与数据范围</div>',
            '        <div v-for="role in roles" :key="role.roleId" v-show="selectedRoleIds.indexOf(role.roleId) >= 0" style="display:flex;gap:8px;align-items:center;padding:8px 10px;border:1px solid var(--tk-line);border-radius:8px">',
            '          <b style="width:88px;font-size:12px;color:var(--tk-text)">{{ role.roleName }}</b>',
            '          <select :value="bindingOf(role.roleId).organizationId" @change="setBinding(role.roleId, \'organizationId\', $event.target.value || null)" style="flex:1;min-width:0;padding:5px 6px;border:1px solid var(--tk-line);border-radius:6px;background:var(--tk-bg2);color:var(--tk-text)">',
            '            <option value="">默认组织</option><option v-for="o in orgs" :key="o.organizationId" :value="o.organizationId">{{ o.organizationName }}</option></select>',
            '          <select :value="bindingOf(role.roleId).dataScope" @change="setBinding(role.roleId, \'dataScope\', $event.target.value)" style="width:132px;padding:5px 6px;border:1px solid var(--tk-line);border-radius:6px;background:var(--tk-bg2);color:var(--tk-text)">',
            '            <option v-for="s in scopeOptions" :key="s.value" :value="s.value">{{ s.label }}</option></select>',
            '        </div>',
            '      </div>',
            '      <div class="infobar">授予服务人员/调度员会自动将身份联动为「后勤」并标记已授权；撤销后回落。数据范围随角色绑定立即生效。</div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="rolesVisible = false">取消</button>',
            '        <button class="c blue" :disabled="rolesSubmitting" @click="submitRoles">{{ rolesSubmitting ? "保存中…" : "保存" }}</button></div>',
            '    </div>',
            '  </div>',

            // 弹层: 授权审核（U04：材料预览 / 拒绝原因模板 / 审核历史）
            '  <div class="pmask" :class="{ show: reviewVisible }" @click.self="closeReview">',
            '    <div class="pcard" style="width:780px;max-width:96vw" v-if="reviewTarget">',
            '      <div class="ph"><b>授权审核</b><span class="no">{{ reviewTarget.displayName }}（{{ reviewTarget.username }}）</span><span class="sp"></span><span class="x" @click="closeReview">✕</span></div>',
            '      <div v-if="reviewLoading" style="padding:42px;text-align:center;color:var(--tk-muted)">审核资料加载中…</div>',
            '      <div v-else-if="reviewError" style="padding:26px;text-align:center">',
            '        <div style="color:var(--tk-bad);font-size:12px;margin-bottom:10px">{{ reviewError }}</div>',
            '        <button class="os-btn sm" @click="loadReviewDetail">重试</button>',
            '      </div>',
            '      <template v-else-if="reviewDetail">',
            '        <div class="psum" style="display:flex;flex-wrap:wrap;gap:6px 18px;align-items:center">',
            '          <div class="l1" style="font-size:13px">{{ reviewDetail.displayName }}<span style="font-size:11px;color:var(--tk-muted);margin-left:6px">@{{ reviewDetail.username }}</span></div>',
            '          <div class="l2">身份：{{ identityLabel(reviewDetail.identityType) }}</div>',
            '          <div class="l2">学号：{{ reviewDetail.studentNo || "—" }}</div>',
            '          <div class="l2">院系：{{ reviewDetail.college || "—" }}</div>',
            '          <div class="l2">手机：{{ reviewDetail.phone || "未绑定" }}</div>',
            '          <div class="l2">授权：<span class="os-tag" :class="authTone(reviewDetail.authorizationStatus)">{{ authLabel(reviewDetail.authorizationStatus) }}</span></div>',
            '          <div class="l2">申请时间：{{ fmt(reviewDetail.submittedAt) }}</div>',
            '          <div class="l2" v-if="reviewDetail.reviewedAt">最近审核：{{ fmt(reviewDetail.reviewedAt) }} · {{ reviewDetail.reviewerName || "—" }}</div>',
            '        </div>',
            '        <div class="tabs" style="margin-bottom:10px">',
            '          <span class="tab" :class="{ on: reviewTab === \'materials\' }" @click="reviewTab = \'materials\'">申请材料（{{ (reviewDetail.materials || []).length }}）</span>',
            '          <span class="tab" :class="{ on: reviewTab === \'history\' }" @click="reviewTab = \'history\'">审核历史（{{ (reviewDetail.timeline || []).length }}）</span>',
            '        </div>',
            // 材料
            '        <div v-show="reviewTab === \'materials\'">',
            '          <div v-if="!(reviewDetail.materials || []).length" class="os-empty" style="padding:22px">未上传任何审核材料 · 系统不会展示占位图<br/><span style="font-size:11px;color:var(--tk-muted)">学生端上传后自动出现在这里；也可在下方登记已上传文件</span></div>',
            '          <div v-else style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px">',
            '            <div v-for="m in reviewDetail.materials" :key="m.id" style="border:1px solid var(--tk-line);border-radius:8px;padding:8px;display:flex;flex-direction:column;gap:6px;background:var(--tk-bg2)">',
            '              <div style="height:86px;border:1px solid var(--tk-line);border-radius:6px;overflow:hidden;display:grid;place-items:center;background:var(--tk-subtle)">',
            '                <img v-if="m.image && m.previewAvailable && m.thumbnailPath" :src="m.thumbnailPath" alt="材料预览" style="width:100%;height:100%;object-fit:cover" />',
            '                <div v-else-if="m.previewAvailable" style="display:flex;flex-direction:column;align-items:center;gap:4px;color:var(--tk-text2)">',
            '                  <el-icon :size="20"><Document /></el-icon><span style="font-size:10px">{{ (m.originalName || m.materialName || "文档").slice(-18) }}</span></div>',
            '                <div v-else style="font-size:11px;color:var(--tk-bad)">文件缺失（不可预览）</div>',
            '              </div>',
            '              <div style="font-size:11px;color:var(--tk-text);font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ m.materialName || materialLabel(m.materialType) }}</div>',
            '              <div style="font-size:10px;color:var(--tk-muted)">{{ materialLabel(m.materialType) }} · {{ fileSizeText(m.fileSize) }} · {{ fmt(m.uploadedAt) }}</div>',
            '              <div style="display:flex;gap:8px;font-size:11px">',
            '                <a v-if="m.previewAvailable" :href="m.viewPath" target="_blank" rel="noopener" style="color:var(--tk-info)">预览</a>',
            '                <span v-if="m.previewAvailable" style="color:var(--tk-info);cursor:pointer" @click="downloadEvidence(m)">下载</span>',
            '                <span style="color:var(--tk-bad);cursor:pointer;margin-left:auto" @click="removeEvidence(m)">移除</span>',
            '              </div>',
            '            </div>',
            '          </div>',
            '          <div style="display:flex;gap:8px;align-items:flex-end;flex-wrap:wrap;margin-top:10px;padding-top:10px;border-top:1px dashed var(--tk-line)">',
            '            <div class="fld" style="width:180px;margin:0"><label>材料文件 ID</label><div class="pin"><input v-model.trim="evidenceFileId" placeholder="file_storage.id" /></div></div>',
            '            <div class="fld" style="width:150px;margin:0"><label>材料类型</label><div class="pin"><select v-model="evidenceType" style="width:100%;border:0;background:none;outline:none"><option v-for="o in materialOptions" :key="o.value" :value="o.value">{{ o.label }}</option></select></div></div>',
            '            <div class="fld" style="flex:1;min-width:160px;margin:0"><label>材料说明</label><div class="pin"><input v-model.trim="evidenceName" maxlength="128" placeholder="如 学生证正面" /></div></div>',
            '            <button class="os-btn sm" :disabled="evidenceSubmitting || !evidenceFileId" @click="addEvidence">{{ evidenceSubmitting ? "登记中…" : "登记材料" }}</button>',
            '          </div>',
            '          <div class="infobar" style="margin-top:8px">材料只登记真实已上传文件（校验 file_storage）；文件被清理后显示「文件缺失」，不会生成占位图。</div>',
            '        </div>',
            // 历史
            '        <div v-show="reviewTab === \'history\'">',
            '          <div v-if="!(reviewDetail.timeline || []).length" class="os-empty" style="padding:22px">暂无审核记录 · 完成首次审核后写入审核历史</div>',
            '          <div v-else style="display:flex;flex-direction:column">',
            '            <div v-for="t in reviewDetail.timeline" :key="t.id" style="display:flex;gap:10px;padding:9px 0;border-bottom:1px solid var(--tk-line)">',
            '              <div style="width:8px;height:8px;border-radius:50%;margin-top:5px;flex:none" :style="{ background: t.action === \'APPROVE\' ? \'var(--tk-ok)\' : \'var(--tk-bad)\' }"></div>',
            '              <div style="flex:1;min-width:0">',
            '                <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">',
            '                  <b style="font-size:12px;color:var(--tk-text)">{{ t.action === \'APPROVE\' ? "通过授权" : "拒绝授权" }}</b>',
            '                  <span class="os-tag" :class="authTone(t.fromStatus)">{{ authLabel(t.fromStatus) }}</span>',
            '                  <span style="color:var(--tk-muted);font-size:11px">→</span>',
            '                  <span class="os-tag" :class="authTone(t.toStatus)">{{ authLabel(t.toStatus) }}</span>',
            '                  <span class="os-tag plain" v-if="t.reasonCode">{{ t.reasonCode }}</span>',
            '                  <span style="font-size:10px;color:var(--tk-muted);margin-left:auto">{{ fmt(t.reviewedAt) }} · {{ t.reviewerName || "—" }}</span>',
            '                </div>',
            '                <div v-if="t.reason" style="font-size:11px;color:var(--tk-text2);margin-top:4px;line-height:1.5">{{ t.reason }}</div>',
            '                <div v-if="t.evidenceSnapshot" style="font-size:10px;color:var(--tk-muted);margin-top:3px">材料快照：{{ t.evidenceSnapshot }}</div>',
            '              </div>',
            '            </div>',
            '          </div>',
            '        </div>',
            // 决策区
            '        <div style="display:flex;gap:12px;margin-top:12px">',
            '          <div class="fld" style="flex:1;margin:0"><label>拒绝原因模板 <span style="color:var(--tk-muted);font-weight:400">（拒绝时必选，或填写右侧自定义）</span></label>',
            '            <div class="pin"><select v-model="reviewReasonCode" style="width:100%;border:0;background:none;outline:none"><option value="">不选择模板</option><option v-for="tpl in reviewTemplates" :key="tpl.id" :value="tpl.templateCode">{{ tpl.builtin ? "[内置] " : "" }}{{ tpl.templateText }}</option></select></div></div>',
            '          <div class="fld" style="flex:1;margin:0"><label>自定义备注 / 补充说明</label><textarea class="pta" style="height:64px" v-model="reviewReason" maxlength="200" placeholder="通过时作为备注；拒绝时作为模板补充说明"></textarea></div>',
            '        </div>',
            '        <div class="infobar" style="margin-top:8px">审核结果将通过<b>站内通知</b>发送给申请人，并写入独立审核记录（可在「审核历史」查看）。拒绝必须提供模板或自定义原因。</div>',
            '        <div class="pbtn">',
            '          <button class="c ghost" @click="openTemplateManager">模板管理</button>',
            '          <span class="sp"></span>',
            '          <button class="c green" :disabled="reviewSubmitting" @click="submitReview(\'APPROVE\')">{{ reviewSubmitting ? "提交中…" : "通过授权" }}</button>',
            '          <button class="c redline" :disabled="reviewSubmitting" @click="submitReview(\'REJECT\')">{{ reviewSubmitting ? "提交中…" : "拒绝授权" }}</button>',
            '          <button class="c ghost" @click="closeReview">取消</button>',
            '        </div>',
            '      </template>',
            '    </div>',
            '  </div>',

            // 弹层: 拒绝原因模板管理（真实持久化，内置模板只读）
            '  <div class="pmask" :class="{ show: templateVisible }" @click.self="templateVisible = false">',
            '    <div class="pcard" style="width:680px;max-width:94vw" v-if="templateVisible">',
            '      <div class="ph"><b>拒绝原因模板</b><span class="no">平台内置只读 · 本校区可新增</span><span class="sp"></span><span class="x" @click="templateVisible = false">✕</span></div>',
            '      <div class="infobar">模板文案存储于数据库，审核弹窗实时读取；内置模板不可修改/删除，如需调整请新增本校区模板。</div>',
            '      <div style="max-height:230px;overflow:auto;margin:10px 0">',
            '        <table class="pk" style="width:100%"><thead><tr><th style="width:22%">编码</th><th>文案</th><th style="width:11%">状态</th><th style="width:14%;text-align:right">操作</th></tr></thead>',
            '          <tbody><tr v-for="tpl in templateList" :key="tpl.id">',
            '            <td style="font-family:monospace;font-size:11px">{{ tpl.templateCode }}<span v-if="tpl.builtin" class="os-tag plain" style="margin-left:4px">内置</span></td>',
            '            <td style="font-size:11px;color:var(--tk-text2)">{{ tpl.templateText }}</td>',
            '            <td><span class="os-tag" :class="templateStatusTone(tpl)">{{ templateStatusLabel(tpl) }}</span></td>',
            '            <td style="text-align:right;white-space:nowrap">',
            '              <template v-if="!tpl.builtin">',
            '                <span style="font-size:11px;color:var(--tk-info);cursor:pointer" @click="editTemplate(tpl)">编辑</span>',
            '                <span style="font-size:11px;color:var(--tk-bad);cursor:pointer;margin-left:8px" @click="deleteTemplate(tpl)">删除</span>',
            '              </template>',
            '              <span v-else style="font-size:11px;color:var(--tk-muted)">只读</span>',
            '            </td>',
            '          </tr></tbody></table>',
            '      </div>',
            '      <div style="display:flex;gap:8px;align-items:flex-end;flex-wrap:wrap;border-top:1px dashed var(--tk-line);padding-top:10px">',
            '        <div class="fld" style="width:168px;margin:0"><label>模板编码</label><div class="pin"><input v-model.trim="templateForm.templateCode" :disabled="!!templateEditingId" maxlength="64" placeholder="CUSTOM_REASON" /></div></div>',
            '        <div class="fld" style="flex:1;min-width:200px;margin:0"><label>原因文案</label><div class="pin"><input v-model.trim="templateForm.templateText" maxlength="255" placeholder="展示给审核人并写入拒绝通知" /></div></div>',
            '        <div class="fld" style="width:92px;margin:0"><label>排序</label><div class="pin"><input type="number" v-model.number="templateForm.sortOrder" /></div></div>',
            '        <div class="fld" style="width:108px;margin:0"><label>状态</label><div class="pin"><select v-model="templateForm.status" style="width:100%;border:0;background:none;outline:none"><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></div></div>',
            '        <button class="os-btn primary sm" :disabled="templateSaving" @click="saveTemplate">{{ templateSaving ? "保存中…" : (templateEditingId ? "保存修改" : "新增模板") }}</button>',
            '        <button v-if="templateEditingId" class="os-btn sm" @click="resetTemplateForm">取消编辑</button>',
            '      </div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="templateVisible = false">关闭</button></div>',
            '    </div>',
            '  </div>',

            // 弹层: 官方账号
            '  <div class="pmask" :class="{ show: officialVisible }" @click.self="officialVisible = false">',
            '    <div class="pcard w440" v-if="officialTarget">',
            '      <div class="ph"><b>官方账号标识</b><span class="no">{{ officialTarget.displayName }}（{{ officialTarget.username }}）</span><span class="sp"></span><span class="x" @click="officialVisible = false">✕</span></div>',
            '      <div class="fld"><label>官方头衔 <em>*</em></label><div class="pin"><input v-model="officialTitle" maxlength="64" placeholder="如 教务处 / 学生工作处" /></div></div>',
            '      <div class="infobar">官方账号在社区展示「官方 · 头衔」，不再显示 Lv 等级；本校官方通知默认以官方账号发布。</div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="officialVisible = false">取消</button>',
            '        <button v-if="officialTarget.official" class="c redline" :disabled="officialSubmitting" @click="submitOfficial(false)">取消官方</button>',
            '        <button class="c blue" :disabled="officialSubmitting" @click="submitOfficial(true)">{{ officialSubmitting ? "保存中…" : "保存为官方" }}</button></div>',
            '    </div>',
            '  </div>',

            // 弹层: 用户详情（资料 / 角色·数据范围 / 登录设备）
            '  <div class="pmask" :class="{ show: detailVisible }" @click.self="closeDetail">',
            '    <div class="pcard" style="width:700px;max-width:94vw" v-if="detail">',
            '      <div class="ph"><b>用户详情</b><span class="no">{{ detail.displayName }}（{{ detail.username }}）</span><span class="sp"></span><span class="x" @click="closeDetail">✕</span></div>',
            '      <div class="tabs" style="margin-bottom:12px">',
            '        <span class="tab" :class="{ on: detailTab === \'profile\' }" @click="switchDetailTab(\'profile\')">资料</span>',
            '        <span class="tab" :class="{ on: detailTab === \'roles\' }" @click="switchDetailTab(\'roles\')">角色 · 数据范围</span>',
            '        <span class="tab" :class="{ on: detailTab === \'devices\' }" @click="switchDetailTab(\'devices\')">登录设备（{{ devices.length }}）</span>',
            '      </div>',
            '      <div v-if="detailLoading" style="padding:42px;text-align:center;color:var(--tk-muted)">加载中…</div>',
            '      <template v-else>',
            '        <div v-show="detailTab === \'profile\'" style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:11px 18px">',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">用户名</div><div style="font-size:12px;color:var(--tk-text);font-family:monospace">{{ detail.username }}</div></div>',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">昵称</div><div style="font-size:12px;color:var(--tk-text)">{{ detail.displayName || "-" }}</div></div>',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">手机号（脱敏）</div><div style="font-size:12px;color:var(--tk-text)">{{ detail.phone || "未绑定" }}</div></div>',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">邮箱</div><div style="font-size:12px;color:var(--tk-text)">{{ detail.email || "-" }}</div></div>',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">学号</div><div style="font-size:12px;color:var(--tk-text)">{{ detail.studentNo || "-" }}</div></div>',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">院系</div><div style="font-size:12px;color:var(--tk-text)">{{ detail.college || "-" }}</div></div>',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">校区</div><div style="font-size:12px;color:var(--tk-text)">{{ detail.campusName || detail.campusId || "-" }}</div></div>',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">默认组织</div><div style="font-size:12px;color:var(--tk-text)">{{ detail.defaultOrganizationName || "未绑定" }}</div></div>',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">身份 / 授权</div><div style="font-size:12px;color:var(--tk-text)">{{ identityLabel(detail.identityType) }} · {{ authLabel(detail.authorizationStatus) }}</div></div>',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">账号状态</div><div style="font-size:12px;color:var(--tk-text)">{{ detail.status === "ENABLED" ? "启用" : "停用" }}</div></div>',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">账号类型 / 官方</div><div style="font-size:12px;color:var(--tk-text)">{{ accountTypeLabel(detail) }}<span v-if="detail.official"> · 官方{{ detail.officialTitle ? "（" + detail.officialTitle + "）" : "" }}</span></div></div>',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">认证状态</div><div style="font-size:12px;color:var(--tk-text)">{{ detail.verificationStatus || "NONE" }}</div></div>',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">官方运营人 / 简介</div><div style="font-size:12px;color:var(--tk-text)">{{ detail.accountOperator || "-" }}<span v-if="detail.accountDescription"> · {{ detail.accountDescription }}</span></div></div>',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">最近登录</div><div style="font-size:12px;color:var(--tk-text)">{{ fmt(detail.lastLoginAt) }}</div></div>',
            '          <div><div style="font-size:10px;color:var(--tk-muted)">注册 / 设备数</div><div style="font-size:12px;color:var(--tk-text)">{{ fmt(detail.createdAt) }} · {{ detail.deviceCount }} 台</div></div>',
            '        </div>',
            '        <div v-show="detailTab === \'roles\'">',
            '          <div v-if="!detail.roles || !detail.roles.length" class="os-empty">未绑定角色；可在「身份角色」中分配</div>',
            '          <table class="pk" v-else style="width:100%"><thead><tr><th style="width:30%">角色</th><th style="width:26%">绑定组织</th><th style="width:22%">数据范围</th><th>角色编码</th></tr></thead>',
            '            <tbody><tr v-for="r in detail.roles" :key="r.roleId"><td style="color:var(--tk-text);font-weight:600">{{ r.roleName }}</td>',
            '              <td>{{ r.organizationName || "默认组织" }}</td><td><span class="bdg plain">{{ scopeLabel(r.dataScope) }}</span></td>',
            '              <td style="font-family:monospace;color:var(--tk-muted)">{{ r.roleCode }}</td></tr></tbody></table>',
            '          <div class="infobar" style="margin-top:10px">数据范围随角色绑定生效；授予服务人员/调度员会自动联动身份为「后勤」并标记已授权。</div>',
            '        </div>',
            '        <div v-show="detailTab === \'devices\'">',
            '          <div v-for="d in devices" :key="d.deviceId" style="display:flex;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid var(--tk-line)">',
            '            <div style="flex:1;min-width:0">',
            '              <div style="display:flex;align-items:center;gap:6px"><b style="font-size:12px;color:var(--tk-text)">{{ d.deviceName || "未知设备" }}</b>',
            '                <span class="os-tag" :class="d.status === \'ONLINE\' ? \'ok\' : \'muted\'">{{ d.status === "ONLINE" ? "在线" : "离线" }}</span></div>',
            '              <div style="font-size:11px;color:var(--tk-text3);margin-top:3px">{{ d.osType || "-" }} {{ d.osVersion || "" }} · {{ d.ipAddress || "IP 未知" }} · {{ d.clientType || "-" }}</div>',
            '              <div style="font-size:10px;color:var(--tk-muted);margin-top:2px">最近活跃 {{ fmt(d.lastActiveAt) }} · 登记 {{ fmt(d.createdAt) }}</div>',
            '            </div>',
            '            <button class="os-btn sm" @click="revokeDevice(d)">下线</button>',
            '          </div>',
            '          <div v-if="!devices.length" class="os-empty">暂无登录设备记录（用户从未登录或记录已清理）</div>',
            '          <div style="display:flex;align-items:center;gap:8px;margin-top:10px">',
            '            <span style="font-size:11px;color:var(--tk-text3);flex:1">在线状态由服务端会话实时探测，按最近活跃排序；下线会立即终止对应会话。</span>',
            '            <button class="os-btn sm" :disabled="!devices.length || revokingAll" @click="revokeAllDevices">{{ revokingAll ? "处理中…" : "全部下线" }}</button>',
            '          </div>',
            '        </div>',
            '      </template>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="closeDetail">关闭</button></div>',
            '    </div>',
            '  </div>',

            // 弹层: 用户导入
            '  <div class="pmask" :class="{ show: importVisible }" @click.self="importVisible = false">',
            '    <div class="pcard" style="width:740px;max-width:94vw" v-if="importVisible">',
            '      <div class="ph"><b>导入用户</b><span class="no">CSV 同步导入</span><span class="sp"></span><span class="x" @click="importVisible = false">✕</span></div>',
            '      <div class="infobar">同步执行（非异步任务、无任务持久化）：单次最多 500 行 / 2MB；文件需为 UTF-8 CSV（Excel 请另存为「CSV UTF-8」）。</div>',
            '      <div style="display:flex;gap:10px;align-items:flex-end;flex-wrap:wrap;margin:12px 0">',
            '        <div class="fld" style="flex:1;min-width:240px;margin:0"><label>CSV 文件</label><div class="pin"><input type="file" accept=".csv,text/csv" @change="onImportFileChange" /></div></div>',
            '        <div class="fld" style="width:178px;margin:0"><label>冲突策略</label><div class="pin"><select v-model="importStrategy" style="width:100%;border:0;background:none;outline:none"><option value="SKIP">SKIP 跳过已存在</option><option value="UPDATE">UPDATE 覆盖资料</option></select></div></div>',
            '        <button class="btn2" @click="downloadTemplate">下载模板</button>',
            '        <button class="os-btn primary sm" :disabled="!importFile || importBusy" @click="previewImport">{{ previewing ? "预览中…" : "预览" }}</button>',
            '        <button class="os-btn primary sm" :disabled="!importFile || importBusy" @click="submitImport">{{ importing ? "导入中…" : "开始导入" }}</button>',
            '      </div>',
            '      <div v-if="importRows.length" style="max-height:46vh;overflow:auto">',
            '        <div style="font-size:12px;color:var(--tk-text2);margin-bottom:6px">{{ importSummary }}</div>',
            '        <table class="pk" style="width:100%"><thead><tr><th style="width:56px">行号</th><th style="width:150px">用户名</th><th style="width:130px">昵称</th><th style="width:90px">动作</th><th>说明</th></tr></thead>',
            '          <tbody><tr v-for="r in importRows" :key="r.lineNo"><td>{{ r.lineNo }}</td><td style="font-family:monospace">{{ r.username }}</td><td>{{ r.displayName || "-" }}</td>',
            '            <td><span class="os-tag" :class="importTone(r.action)">{{ importLabel(r.action) }}</span></td>',
            '            <td style="font-size:11px;color:var(--tk-text3)">{{ r.message }}</td></tr></tbody></table>',
            '      </div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="importVisible = false">关闭</button></div>',
            '    </div>',
            '  </div>',

            // 弹层: SSO / 花名册状态
            '  <div class="pmask" :class="{ show: ssoVisible }" @click.self="ssoVisible = false">',
            '    <div class="pcard w440" v-if="ssoVisible">',
            '      <div class="ph"><b>SSO / 花名册接入状态</b><span class="sp"></span><span class="x" @click="ssoVisible = false">✕</span></div>',
            '      <template v-if="sso">',
            '        <div class="fld"><label>状态</label><div style="font-size:12px;color:var(--tk-text)">{{ sso.status }}（configured={{ sso.configured }} / implemented={{ sso.implemented }}）</div></div>',
            '        <div class="fld"><label>对接对象</label><div style="font-size:12px;color:var(--tk-text)">{{ sso.provider }}</div></div>',
            '        <div class="fld"><label>所需环境变量</label><div v-for="k in sso.envKeys" :key="k" style="font-family:monospace;font-size:11px;color:var(--tk-text2)">{{ k }}</div></div>',
            '        <div class="infobar">{{ sso.message }}</div>',
            '        <div class="infobar">本页只读展示接入状态，不发起任何外部请求，也不会返回伪造的成功结果；学校提供接口后再实现字段映射与导入预览。</div>',
            '      </template>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="ssoVisible = false">关闭</button></div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var query = Vue.reactive({ keyword: '', status: '', identityType: '', authorizationStatus: '', official: '', roleId: '' });
            var rows = Vue.ref([]);
            var total = Vue.ref(0);
            var pageNum = Vue.ref(1);
            var pageSize = Vue.ref(10);
            var loading = Vue.ref(false);
            var roles = Vue.ref([]);
            var orgs = Vue.ref([]);
            var roleBindings = Vue.ref({});

            var rolesVisible = Vue.ref(false);
            var rolesTarget = Vue.ref({});
            var selectedRoleIds = Vue.ref([]);
            var rolesSubmitting = Vue.ref(false);

            var reviewVisible = Vue.ref(false);
            var reviewTarget = Vue.ref({});
            var reviewReason = Vue.ref('');
            var reviewReasonCode = Vue.ref('');
            var reviewSubmitting = Vue.ref(false);
            var reviewLoading = Vue.ref(false);
            var reviewError = Vue.ref('');
            var reviewDetail = Vue.ref(null);
            var reviewTab = Vue.ref('materials');
            var reviewTemplates = Vue.computed(function () {
                return (reviewDetail.value && reviewDetail.value.rejectReasons) || [];
            });
            var evidenceFileId = Vue.ref('');
            var evidenceType = Vue.ref('STUDENT_CARD');
            var evidenceName = Vue.ref('');
            var evidenceSubmitting = Vue.ref(false);
            var templateVisible = Vue.ref(false);
            var templateList = Vue.ref([]);
            var templateEditingId = Vue.ref(null);
            var templateSaving = Vue.ref(false);
            var templateForm = Vue.reactive({ templateCode: '', templateText: '', sortOrder: 100, status: 'ENABLED' });

            var officialVisible = Vue.ref(false);
            var officialTarget = Vue.ref({});
            var officialTitle = Vue.ref('');
            var officialSubmitting = Vue.ref(false);

            var pageOfficial = Vue.computed(function () {
                return rows.value.filter(function (row) { return isOperations(row); }).length;
            });
            var pagePending = Vue.computed(function () {
                return rows.value.filter(function (row) { return row.authorizationStatus === 'PENDING'; }).length;
            });
            var pageDisabled = Vue.computed(function () {
                return rows.value.filter(function (row) { return row.status === 'DISABLED'; }).length;
            });

            var ACCOUNT_TYPE = {
                NORMAL: { label: '普通用户', tone: 'plain' },
                OFFICIAL_AUTO: { label: '抓取号', tone: 'info' },
                OFFICIAL_HUMAN: { label: '人工运营号', tone: 'ok' },
                AD_SERVICE: { label: '广告服务号', tone: 'warn' }
            };
            function accountTypeOf(row) {
                return ACCOUNT_TYPE[row.accountType] ? row.accountType : (row.official ? 'OFFICIAL_HUMAN' : 'NORMAL');
            }
            function accountTypeLabel(row) { return ACCOUNT_TYPE[accountTypeOf(row)].label; }
            function accountTypeTone(row) { return ACCOUNT_TYPE[accountTypeOf(row)].tone; }
            function isOperations(row) { return accountTypeOf(row) !== 'NORMAL'; }
            function goCommunity() { location.hash = '#/community-accounts'; }

            function toQuery() {
                var params = ['pageNum=' + pageNum.value, 'pageSize=' + pageSize.value];
                if (query.keyword) params.push('keyword=' + encodeURIComponent(query.keyword));
                if (query.status) params.push('status=' + query.status);
                if (query.identityType) params.push('identityType=' + query.identityType);
                if (query.authorizationStatus) params.push('authorizationStatus=' + query.authorizationStatus);
                if (query.official) params.push('official=' + query.official);
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
                api.get('/api/admin/organizations')
                    .then(function (list) { orgs.value = list || []; })
                    .catch(function () { orgs.value = []; });
            }

            function bindingOf(roleId) {
                return roleBindings.value[roleId] || { organizationId: null, dataScope: 'SELF' };
            }

            function setBinding(roleId, key, value) {
                var b = Object.assign({ organizationId: null, dataScope: 'SELF' }, roleBindings.value[roleId] || {});
                b[key] = value;
                roleBindings.value = Object.assign({}, roleBindings.value, (function () {
                    var map = {};
                    map[roleId] = b;
                    return map;
                })());
            }

            function openRoles(row) {
                rolesTarget.value = row;
                selectedRoleIds.value = (row.roles || []).map(function (role) { return role.roleId; });
                var map = {};
                (row.roles || []).forEach(function (role) {
                    map[role.roleId] = {
                        organizationId: role.organizationId || null,
                        dataScope: role.dataScope || 'SELF'
                    };
                });
                roleBindings.value = map;
                rolesVisible.value = true;
            }

            function submitRoles() {
                rolesSubmitting.value = true;
                var bindings = selectedRoleIds.value.map(function (roleId) {
                    var b = roleBindings.value[roleId] || { organizationId: null, dataScope: 'SELF' };
                    return { roleId: roleId, organizationId: b.organizationId || null, dataScope: b.dataScope || 'SELF' };
                });
                api.put('/api/admin/users/' + rolesTarget.value.userId + '/role-bindings',
                        { bindings: bindings })
                    .then(function (updated) {
                        El.ElMessage.success('角色已更新，在线会话已刷新');
                        rolesVisible.value = false;
                        load();
                        void updated;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { rolesSubmitting.value = false; });
            }

            function materialLabel(type) {
                return labelOf(MATERIAL_OPTIONS, type) || type || '其他材料';
            }

            function fileSizeText(size) {
                if (size === null || size === undefined) return '大小未知';
                if (size < 1024) return size + ' B';
                if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB';
                return (size / 1024 / 1024).toFixed(2) + ' MB';
            }

            function templateStatusLabel(tpl) { return tpl.status === 'ENABLED' ? '启用' : '停用'; }
            function templateStatusTone(tpl) { return tpl.status === 'ENABLED' ? 'ok' : 'muted'; }

            function openReview(row) {
                reviewTarget.value = row;
                reviewReason.value = '';
                reviewReasonCode.value = '';
                reviewTab.value = 'materials';
                reviewDetail.value = null;
                reviewError.value = '';
                evidenceFileId.value = '';
                evidenceName.value = '';
                evidenceType.value = 'STUDENT_CARD';
                reviewVisible.value = true;
                loadReviewDetail();
            }

            function closeReview() {
                reviewVisible.value = false;
                reviewLoading.value = false;
            }

            function loadReviewDetail() {
                if (!reviewTarget.value || !reviewTarget.value.userId) return;
                reviewLoading.value = true;
                reviewError.value = '';
                api.get('/api/admin/identity-review/users/' + reviewTarget.value.userId)
                    .then(function (data) { reviewDetail.value = data; })
                    .catch(function (error) {
                        reviewError.value = error.message || '审核资料加载失败';
                    })
                    .finally(function () { reviewLoading.value = false; });
            }

            function submitReview(action) {
                var reason = reviewReason.value.trim();
                if (action === 'REJECT' && !reviewReasonCode.value && !reason) {
                    El.ElMessage.warning('拒绝时必须选择原因模板或填写自定义原因');
                    return;
                }
                var tip = action === 'APPROVE'
                    ? '通过后该用户将获得对应身份权限，审核结果会通过站内通知告知本人，确定通过？'
                    : '拒绝后该用户需重新提交材料，审核结果会通过站内通知告知本人，确定拒绝？';
                El.ElMessageBox.confirm(tip, action === 'APPROVE' ? '通过授权' : '拒绝授权',
                        { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
                    .then(function () {
                        reviewSubmitting.value = true;
                        return api.post('/api/admin/users/' + reviewTarget.value.userId + '/authorization', {
                            action: action,
                            reasonCode: reviewReasonCode.value || null,
                            reason: reason || null
                        });
                    })
                    .then(function () {
                        El.ElMessage.success(action === 'APPROVE' ? '已通过授权，结果通知已发送' : '已拒绝授权，结果通知已发送');
                        reviewVisible.value = false;
                        load();
                    })
                    .catch(function (error) {
                        if (error !== 'cancel' && error !== 'close') {
                            El.ElMessage.error((error && error.message) || '审核提交失败');
                        }
                    })
                    .finally(function () { reviewSubmitting.value = false; });
            }

            function addEvidence() {
                // 服务端 Long 以字符串下发（JacksonConfig 全局 ToStringSerializer），不能用 Number 转换，避免雪花 ID 精度丢失
                var fileId = String(evidenceFileId.value || '').trim();
                if (!/^\d+$/.test(fileId)) {
                    El.ElMessage.warning('请填写有效的材料文件 ID（数字）');
                    return;
                }
                evidenceSubmitting.value = true;
                api.post('/api/admin/identity-review/users/' + reviewTarget.value.userId + '/evidence', {
                    fileId: fileId,
                    materialType: evidenceType.value,
                    materialName: evidenceName.value.trim() || null
                }).then(function (data) {
                    reviewDetail.value = data;
                    evidenceFileId.value = '';
                    evidenceName.value = '';
                    El.ElMessage.success('材料已登记');
                }).catch(function (error) {
                    El.ElMessage.error(error.message || '材料登记失败');
                }).finally(function () { evidenceSubmitting.value = false; });
            }

            function removeEvidence(material) {
                El.ElMessageBox.confirm('移除后该材料不再参与本次审核（审核历史中的材料快照保留），确定移除？',
                        '移除材料', { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' })
                    .then(function () {
                        return api.delete('/api/admin/identity-review/users/' + reviewTarget.value.userId
                            + '/evidence/' + material.id);
                    })
                    .then(function (data) {
                        reviewDetail.value = data;
                        El.ElMessage.success('材料已移除');
                    })
                    .catch(function (error) {
                        if (error !== 'cancel' && error !== 'close') {
                            El.ElMessage.error((error && error.message) || '移除失败');
                        }
                    });
            }

            function downloadEvidence(material) {
                if (!material.previewAvailable || !material.viewPath) {
                    El.ElMessage.warning('文件缺失，无法下载');
                    return;
                }
                var name = material.originalName || material.materialName || ('evidence-' + material.id);
                fetch(material.viewPath)
                    .then(function (response) {
                        if (!response.ok) throw new Error('文件下载失败');
                        return response.blob();
                    })
                    .then(function (blob) {
                        var url = URL.createObjectURL(blob);
                        var anchor = document.createElement('a');
                        anchor.href = url;
                        anchor.download = name;
                        document.body.appendChild(anchor);
                        anchor.click();
                        document.body.removeChild(anchor);
                        URL.revokeObjectURL(url);
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '文件下载失败'); });
            }

            function openTemplateManager() {
                templateVisible.value = true;
                resetTemplateForm();
                loadTemplates();
            }

            function loadTemplates() {
                api.get('/api/admin/identity-review/reason-templates')
                    .then(function (list) { templateList.value = list || []; })
                    .catch(function (error) { El.ElMessage.error(error.message || '原因模板加载失败'); });
            }

            function resetTemplateForm() {
                templateEditingId.value = null;
                templateForm.templateCode = '';
                templateForm.templateText = '';
                templateForm.sortOrder = 100;
                templateForm.status = 'ENABLED';
            }

            function editTemplate(tpl) {
                templateEditingId.value = tpl.id;
                templateForm.templateCode = tpl.templateCode;
                templateForm.templateText = tpl.templateText;
                templateForm.sortOrder = tpl.sortOrder || 0;
                templateForm.status = tpl.status || 'ENABLED';
            }

            function saveTemplate() {
                if (!templateForm.templateCode || !templateForm.templateText) {
                    El.ElMessage.warning('模板编码与原因文案为必填');
                    return;
                }
                templateSaving.value = true;
                var payload = {
                    templateCode: templateForm.templateCode,
                    templateText: templateForm.templateText,
                    sortOrder: templateForm.sortOrder,
                    status: templateForm.status
                };
                var request = templateEditingId.value
                    ? api.put('/api/admin/identity-review/reason-templates/' + templateEditingId.value, payload)
                    : api.post('/api/admin/identity-review/reason-templates', payload);
                request.then(function () {
                    El.ElMessage.success(templateEditingId.value ? '模板已更新' : '模板已新增');
                    resetTemplateForm();
                    loadTemplates();
                    if (reviewVisible.value && reviewDetail.value) loadReviewDetail();
                }).catch(function (error) {
                    El.ElMessage.error(error.message || '模板保存失败');
                }).finally(function () { templateSaving.value = false; });
            }

            function deleteTemplate(tpl) {
                El.ElMessageBox.confirm('删除后审核弹窗不再提供该原因，确定删除「' + tpl.templateText + '」？',
                        '删除模板', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
                    .then(function () { return api.delete('/api/admin/identity-review/reason-templates/' + tpl.id); })
                    .then(function () {
                        El.ElMessage.success('模板已删除');
                        loadTemplates();
                        if (reviewVisible.value && reviewDetail.value) loadReviewDetail();
                    })
                    .catch(function (error) {
                        if (error !== 'cancel' && error !== 'close') {
                            El.ElMessage.error((error && error.message) || '删除失败');
                        }
                    });
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

            function openOfficial(row) {
                officialTarget.value = row;
                officialTitle.value = row.officialTitle || '';
                officialVisible.value = true;
            }

            function submitOfficial(official) {
                officialSubmitting.value = true;
                api.post('/api/admin/users/' + officialTarget.value.userId + '/official',
                        { official: official, officialTitle: official ? officialTitle.value.trim() : null })
                    .then(function () {
                        El.ElMessage.success(official ? '已设为官方账号' : '已取消官方标识');
                        officialVisible.value = false;
                        load();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { officialSubmitting.value = false; });
            }

            // ---- 用户详情 / 登录设备（U01 / U05）----
            var detailVisible = Vue.ref(false);
            var detailLoading = Vue.ref(false);
            var detail = Vue.ref(null);
            var detailTab = Vue.ref('profile');
            var devices = Vue.ref([]);
            var revokingAll = Vue.ref(false);

            function scopeLabel(value) {
                return labelOf(SCOPE_OPTIONS, value);
            }

            function openDetail(row) {
                detailVisible.value = true;
                detailTab.value = 'profile';
                detail.value = null;
                devices.value = [];
                detailLoading.value = true;
                api.get('/api/admin/users/' + row.userId)
                    .then(function (data) { detail.value = data; })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { detailLoading.value = false; });
            }

            function closeDetail() {
                detailVisible.value = false;
            }

            function switchDetailTab(tab) {
                detailTab.value = tab;
                if (tab === 'devices') {
                    loadDevices();
                }
            }

            function loadDevices() {
                if (!detail.value) return;
                devices.value = [];
                api.get('/api/admin/users/' + detail.value.userId + '/devices')
                    .then(function (list) { devices.value = list || []; })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }

            function revokeDevice(device) {
                if (!detail.value) return;
                El.ElMessageBox.confirm(
                        '确定下线「' + (device.deviceName || device.deviceId) + '」？该设备上的登录会话会立即失效。',
                        '下线登录设备',
                        { type: 'warning', confirmButtonText: '确定下线', cancelButtonText: '取消' })
                    .then(function () {
                        return api.post('/api/admin/users/' + detail.value.userId
                            + '/devices/' + encodeURIComponent(device.deviceId) + '/revoke');
                    })
                    .then(function (revoked) {
                        El.ElMessage.success(revoked > 0 ? '设备已下线，会话已失效' : '设备记录已移除（会话此前已过期）');
                        loadDevices();
                    })
                    .catch(function (error) {
                        if (error !== 'cancel' && error !== 'close') {
                            El.ElMessage.error(error.message || '下线失败');
                        }
                    });
            }

            function revokeAllDevices() {
                if (!detail.value) return;
                revokingAll.value = true;
                El.ElMessageBox.confirm(
                        '确定下线「' + detail.value.displayName + '」的全部登录设备？所有在线会话会立即失效。',
                        '全部下线', { type: 'warning', confirmButtonText: '全部下线', cancelButtonText: '取消' })
                    .then(function () {
                        return api.post('/api/admin/users/' + detail.value.userId + '/devices/revoke-all');
                    })
                    .then(function (revoked) {
                        El.ElMessage.success('已下线 ' + (revoked || 0) + ' 个在线会话');
                        loadDevices();
                    })
                    .catch(function (error) {
                        if (error !== 'cancel' && error !== 'close') {
                            El.ElMessage.error(error.message || '操作失败');
                        }
                    })
                    .finally(function () { revokingAll.value = false; });
            }

            // ---- SSO / 花名册状态（U06，只读）----
            var sso = Vue.ref(null);
            var ssoVisible = Vue.ref(false);

            function loadSso() {
                api.get('/api/admin/users/sso-status')
                    .then(function (data) { sso.value = data; })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }

            function openSso() {
                ssoVisible.value = true;
                if (!sso.value) loadSso();
            }

            // ---- 用户导入（U07，同步执行）----
            var importVisible = Vue.ref(false);
            var importFile = Vue.ref(null);
            var importStrategy = Vue.ref('SKIP');
            var importRows = Vue.ref([]);
            var importResult = Vue.ref(null);
            var previewing = Vue.ref(false);
            var importing = Vue.ref(false);
            var importBusy = Vue.computed(function () { return previewing.value || importing.value; });
            var importSummary = Vue.computed(function () {
                if (!importResult.value) return '';
                var result = importResult.value;
                return (result.dryRun ? '预览结果' : '导入结果')
                    + '：共 ' + result.total + ' 行 · 新建 ' + result.created + ' · 更新 ' + result.updated
                    + ' · 跳过 ' + result.skipped + ' · 失败 ' + result.failed
                    + '（策略 ' + result.strategy + '）';
            });

            function openImport() {
                importVisible.value = true;
                importRows.value = [];
                importResult.value = null;
            }

            function onImportFileChange(event) {
                var files = event && event.target ? event.target.files : null;
                importFile.value = files && files.length ? files[0] : null;
                importRows.value = [];
                importResult.value = null;
            }

            function downloadTemplate() {
                var headers = {};
                var token = localStorage.getItem('studenthub_admin_token');
                if (token) headers.Authorization = 'Bearer ' + token;
                var campusId = api.getCampusId();
                if (campusId) headers['X-Campus-Id'] = campusId;
                fetch('/api/admin/users/import-template', { headers: headers })
                    .then(function (response) {
                        if (!response.ok) throw new Error('模板下载失败（HTTP ' + response.status + '）');
                        return response.blob();
                    })
                    .then(function (blob) {
                        var anchor = document.createElement('a');
                        var url = URL.createObjectURL(blob);
                        anchor.href = url;
                        anchor.download = 'users-import-template.csv';
                        document.body.appendChild(anchor);
                        anchor.click();
                        document.body.removeChild(anchor);
                        URL.revokeObjectURL(url);
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }

            function requestImport(dryRun) {
                if (!importFile.value) {
                    El.ElMessage.warning('请先选择 CSV 文件');
                    return;
                }
                var flag = dryRun ? previewing : importing;
                flag.value = true;
                var path = (dryRun ? '/api/admin/users/import/preview' : '/api/admin/users/import')
                    + '?strategy=' + encodeURIComponent(importStrategy.value);
                api.upload(path, importFile.value)
                    .then(function (result) {
                        importResult.value = result;
                        importRows.value = result.rows || [];
                        if (dryRun) {
                            El.ElMessage.success('预览完成，确认无误后可开始导入');
                        } else {
                            El.ElMessage.success('导入完成：新建 ' + result.created + '，更新 ' + result.updated
                                + '，跳过 ' + result.skipped + '，失败 ' + result.failed);
                            load();
                        }
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { flag.value = false; });
            }

            function previewImport() {
                requestImport(true);
            }

            function submitImport() {
                El.ElMessageBox.confirm(
                        '将按当前文件与策略「' + importStrategy.value + '」同步写入数据库，确定继续？',
                        '确认导入', { type: 'warning', confirmButtonText: '开始导入', cancelButtonText: '取消' })
                    .then(function () { requestImport(false); })
                    .catch(function () { /* 用户取消 */ });
            }

            function importLabel(action) {
                return {
                    CREATE: '新建', UPDATE: '更新', SKIP: '跳过', ERROR: '错误'
                }[action] || action;
            }

            function importTone(action) {
                return {
                    CREATE: 'ok', UPDATE: 'info', SKIP: 'muted', ERROR: 'bad'
                }[action] || 'plain';
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
                return { BASIC: 'muted', PENDING: 'warn', AUTHORIZED: 'ok', REJECTED: 'bad' }[value] || 'muted';
            }

            load();
            loadRoles();
            loadSso();

            return {
                identityOptions: IDENTITY_OPTIONS,
                pageOfficial: pageOfficial, pagePending: pagePending, pageDisabled: pageDisabled,
                accountTypeLabel: accountTypeLabel, accountTypeTone: accountTypeTone, isOperations: isOperations,
                goCommunity: goCommunity,
                authOptions: AUTH_OPTIONS,
                query: query, rows: rows, total: total, pageNum: pageNum, pageSize: pageSize,
                loading: loading, roles: roles,
                totalPages: totalPages, pageNumbers: pageNumbers, goPage: goPage, toggleRole: toggleRole,
                authTone: authTone,
                rolesVisible: rolesVisible, rolesTarget: rolesTarget, selectedRoleIds: selectedRoleIds,
                orgs: orgs, bindingOf: bindingOf, setBinding: setBinding, scopeOptions: SCOPE_OPTIONS,
                rolesSubmitting: rolesSubmitting,
                reviewVisible: reviewVisible, reviewTarget: reviewTarget, reviewReason: reviewReason,
                reviewReasonCode: reviewReasonCode, reviewSubmitting: reviewSubmitting,
                reviewLoading: reviewLoading, reviewError: reviewError, reviewDetail: reviewDetail,
                reviewTab: reviewTab, reviewTemplates: reviewTemplates,
                materialOptions: MATERIAL_OPTIONS, materialLabel: materialLabel, fileSizeText: fileSizeText,
                evidenceFileId: evidenceFileId, evidenceType: evidenceType, evidenceName: evidenceName,
                evidenceSubmitting: evidenceSubmitting, addEvidence: addEvidence, removeEvidence: removeEvidence,
                downloadEvidence: downloadEvidence, loadReviewDetail: loadReviewDetail, closeReview: closeReview,
                templateVisible: templateVisible, templateList: templateList, templateEditingId: templateEditingId,
                templateSaving: templateSaving, templateForm: templateForm,
                openTemplateManager: openTemplateManager, loadTemplates: loadTemplates,
                editTemplate: editTemplate, resetTemplateForm: resetTemplateForm, saveTemplate: saveTemplate,
                deleteTemplate: deleteTemplate,
                templateStatusLabel: templateStatusLabel, templateStatusTone: templateStatusTone,
                officialVisible: officialVisible, officialTarget: officialTarget, officialTitle: officialTitle,
                officialSubmitting: officialSubmitting, openOfficial: openOfficial, submitOfficial: submitOfficial,
                load: load, reload: reload,
                openRoles: openRoles, submitRoles: submitRoles,
                detailVisible: detailVisible, detailLoading: detailLoading, detail: detail,
                detailTab: detailTab, devices: devices, revokingAll: revokingAll,
                openDetail: openDetail, closeDetail: closeDetail, switchDetailTab: switchDetailTab,
                loadDevices: loadDevices, revokeDevice: revokeDevice, revokeAllDevices: revokeAllDevices,
                scopeLabel: scopeLabel,
                sso: sso, ssoVisible: ssoVisible, openSso: openSso, loadSso: loadSso,
                importVisible: importVisible, importFile: importFile, importStrategy: importStrategy,
                importRows: importRows, importResult: importResult, importSummary: importSummary,
                importBusy: importBusy, previewing: previewing, importing: importing,
                openImport: openImport, onImportFileChange: onImportFileChange,
                downloadTemplate: downloadTemplate, previewImport: previewImport, submitImport: submitImport,
                importLabel: importLabel, importTone: importTone,
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
