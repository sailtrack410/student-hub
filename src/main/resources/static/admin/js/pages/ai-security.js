/* AI 安全（I04）：版本化提示词 + 安全策略（敏感词/工具白名单/限流/配额/内容保留）。
   真实性约束：
   - 提示词激活后立即刷新运行时，AI 对话的 prompt_version 记录实际生效版本；
   - 敏感词/分钟限流/每日配额已接入 AI 对话入口；工具白名单当前仅配置（无工具注册表）；
   - 内容保留天数默认不自动清理，需显式开启 retentionEnabled，由每日任务执行；
   - 运行时状态以后端 /status 为准，页面不自行推断「已生效」。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var SCENES = ['CAMPUS_ASSISTANT', 'SERVICE_GUIDE', 'DOC_SUMMARY'];
    var PROMPT_STATUS = { ENABLED: { label: '启用', tone: 'ok' }, DISABLED: { label: '停用', tone: 'muted' } };

    function blankPrompt() {
        return {
            id: null, promptCode: 'CAMPUS_ASSISTANT', promptName: '', scene: 'CAMPUS_ASSISTANT',
            systemPrompt: '', userPromptTemplate: '', variables: [], changeNote: ''
        };
    }

    window.AdminPages.AiSecurityPage = {
        name: 'AiSecurityPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>AI 提示词与安全</h1><div class="sub">版本化 Prompt / 敏感词 / 限流配额 / 内容保留</div></div>',
            '    <div class="r"><button class="btn2" @click="reloadAll">刷新</button><button class="btn2 pri" @click="openCreatePrompt">新建提示词版本</button></div></div>',

            '  <div class="os-grid" style="grid-template-columns:repeat(5,1fr)">',
            '    <div class="os-kpi"><div class="k-label"><span class="dot"></span>安全策略</div><div class="k-val" style="font-size:18px">{{ config.enabled ? "已启用" : "已关闭" }}</div><div class="k-foot"><span>关闭后敏感词与限流均不生效</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>敏感词</div><div class="k-val">{{ (config.sensitiveWords || []).length }}<small>个</small></div><div class="k-foot"><span>{{ status.sensitiveWordEnforced ? "已接入运行时" : "未生效" }}</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-info)"></span>分钟限流</div><div class="k-val">{{ config.rateLimitPerMinute || "不限" }}<small>/分钟</small></div><div class="k-foot"><span>按 ai_message 实际消息数</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>单用户日配额</div><div class="k-val">{{ config.dailyQuotaPerUser || "不限" }}<small>条/日</small></div><div class="k-foot"><span>超出返回 429</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-text3)"></span>内容保留</div><div class="k-val">{{ config.contentRetentionDays || "—" }}<small>天</small></div><div class="k-foot"><span>{{ config.retentionEnabled ? "自动清理已开启" : "仅配置，未自动清理" }}</span></div></div>',
            '  </div>',

            '  <div class="os-panel" style="flex:1;min-height:0;display:flex;flex-direction:column">',
            '    <div class="os-panel-h"><div class="tabs" style="display:flex;gap:4px">',
            '      <button class="tab" :class="{ on: activeTab === \'prompts\' }" @click="activeTab = \'prompts\'">提示词版本</button>',
            '      <button class="tab" :class="{ on: activeTab === \'policy\' }" @click="activeTab = \'policy\'">安全策略</button>',
            '      <button class="tab" :class="{ on: activeTab === \'runtime\' }" @click="activeTab = \'runtime\'">运行时状态</button>',
            '    </div><span style="flex:1"></span>',
            '      <span v-if="activeTab === \'prompts\'" style="font-size:11px;color:var(--tk-text3)">当前生效：{{ status.activePrompt || "代码内置基线" }}（{{ status.promptSource === "DATABASE" ? "数据库版本" : "代码回退" }}）</span>',
            '    </div>',

            /* ---------------- 提示词版本 ---------------- */
            '    <div v-if="activeTab === \'prompts\'" style="overflow:auto;flex:1">',
            '      <table class="pk" style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr><th style="width:15%">编码</th><th style="width:9%">版本</th><th style="width:16%">名称</th><th style="width:13%">场景</th><th style="width:9%">状态</th><th style="width:8%">生效</th><th style="width:14%">变更说明</th><th style="width:16%;text-align:right">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-for="p in prompts" :key="p.id">',
            '            <td class="os-mono" style="color:var(--tk-text)">{{ p.promptCode }}</td>',
            '            <td class="os-mono">v{{ p.versionNo }}</td>',
            '            <td><b style="font-weight:550">{{ p.promptName }}</b></td>',
            '            <td><span class="os-tag">{{ p.scene }}</span></td>',
            '            <td><span class="os-tag" :class="(PROMPT_STATUS[p.status]||{}).tone || \'muted\'">{{ (PROMPT_STATUS[p.status]||{}).label || p.status }}</span></td>',
            '            <td><span v-if="p.active" class="os-tag ok">当前生效</span><span v-else style="font-size:11px;color:var(--tk-text3)">—</span></td>',
            '            <td style="color:var(--tk-text3);overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ p.changeNote || "—" }}</td>',
            '            <td style="text-align:right;white-space:nowrap">',
            '              <span style="font-size:11px;color:var(--tk-info);cursor:pointer;margin-left:8px" @click="openViewPrompt(p)">查看</span>',
            '              <span v-if="!p.active && p.status === \'ENABLED\'" style="font-size:11px;color:var(--tk-ok);cursor:pointer;margin-left:8px" @click="activatePrompt(p)">激活</span>',
            '              <span v-if="p.status === \'ENABLED\'" style="font-size:11px;color:var(--tk-warn);cursor:pointer;margin-left:8px" @click="disablePrompt(p)">停用</span>',
            '              <span v-if="!p.active" style="font-size:11px;color:var(--tk-bad);cursor:pointer;margin-left:8px" @click="removePrompt(p)">删除</span>',
            '            </td>',
            '          </tr>',
            '          <tr v-if="!promptLoading && !prompts.length"><td colspan="8" class="os-empty">暂无数据库提示词版本，运行时使用代码内置基线。点击「新建提示词版本」创建第一版。</td></tr>',
            '          <tr v-if="promptLoading"><td colspan="8" class="os-empty">加载中…</td></tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',

            /* ---------------- 安全策略 ---------------- */
            '    <div v-if="activeTab === \'policy\'" style="overflow:auto;flex:1;padding:16px 18px">',
            '      <div class="os-form-grid">',
            '        <div class="os-field"><label>策略总开关</label><div style="display:flex;align-items:center;gap:8px;height:36px"><button class="os-switch" :class="{ on: config.enabled }" @click="config.enabled = !config.enabled"></button><span style="font-size:12px;color:var(--tk-text3)">{{ config.enabled ? "启用" : "关闭" }}</span></div></div>',
            '        <div class="os-field"><label>命中敏感词直接拦截</label><div style="display:flex;align-items:center;gap:8px;height:36px"><button class="os-switch" :class="{ on: config.blockOnHit }" @click="config.blockOnHit = !config.blockOnHit"></button><span style="font-size:12px;color:var(--tk-text3)">{{ config.blockOnHit ? "拦截并返回 400" : "仅记录不拦截" }}</span></div></div>',
            '      </div>',
            '      <div class="os-field" style="margin-top:8px"><label>敏感词（每行一个，或英文逗号分隔）</label><textarea class="os-textarea" style="height:110px" v-model="sensitiveWordsText" placeholder="例如：&#10;代写&#10;刷单&#10;身份证号"></textarea></div>',
            '      <div class="os-field" style="margin-top:8px"><label>工具白名单（逗号分隔，仅配置与展示）</label><input class="os-input" v-model.trim="toolWhitelistText" placeholder="AI_SEARCH_CAMPUS,CALENDAR_QUERY,SERVICE_GUIDE" /></div>',
            '      <div class="os-form-grid" style="margin-top:8px">',
            '        <div class="os-field"><label>每分钟每用户上限（0=不限）</label><input class="os-input" type="number" min="0" max="600" v-model.number="config.rateLimitPerMinute" /></div>',
            '        <div class="os-field"><label>单用户每日配额（0=不限）</label><input class="os-input" type="number" min="0" max="10000" v-model.number="config.dailyQuotaPerUser" /></div>',
            '      </div>',
            '      <div class="os-form-grid" style="margin-top:8px">',
            '        <div class="os-field"><label>内容保留天数</label><input class="os-input" type="number" min="0" max="3650" v-model.number="config.contentRetentionDays" /></div>',
            '        <div class="os-field"><label>启用历史内容自动清理</label><div style="display:flex;align-items:center;gap:8px;height:36px"><button class="os-switch" :class="{ on: config.retentionEnabled }" @click="config.retentionEnabled = !config.retentionEnabled"></button><span style="font-size:12px;color:var(--tk-text3)">{{ config.retentionEnabled ? "已开启（每日 03:30 执行）" : "未开启（只保留配置）" }}</span></div></div>',
            '      </div>',
            '      <div class="os-field" style="margin-top:8px"><label>备注</label><input class="os-input" v-model.trim="config.remark" maxlength="500" /></div>',
            '      <div class="os-note" style="margin-top:12px"><b>生效说明</b> 保存后立即刷新运行时：敏感词与限流/配额对下一次 AI 对话生效，无需重启。工具白名单当前没有运行时工具注册表，仅做配置存档。</div>',
            '      <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:14px"><button class="os-btn" @click="loadConfig">重置</button><button class="os-btn primary" :disabled="saving" @click="saveConfig">{{ saving ? "保存中…" : "保存策略" }}</button></div>',
            '    </div>',

            /* ---------------- 运行时状态 ---------------- */
            '    <div v-if="activeTab === \'runtime\'" style="overflow:auto;flex:1;padding:16px 18px">',
            '      <table class="pk" style="width:100%;border-collapse:collapse;table-layout:fixed">',
            '        <thead><tr><th style="width:30%">能力</th><th style="width:16%">状态</th><th>说明</th></tr></thead>',
            '        <tbody>',
            '          <tr><td>敏感词拦截</td><td><span class="os-tag" :class="status.sensitiveWordEnforced ? \'ok\' : \'warn\'">{{ status.sensitiveWordEnforced ? "已生效" : "未生效" }}</span></td><td style="color:var(--tk-text3)">命中即拒绝写入消息并返回 400</td></tr>',
            '          <tr><td>分钟级限流 / 日配额</td><td><span class="os-tag" :class="status.rateLimitEnforced ? \'ok\' : \'warn\'">{{ status.rateLimitEnforced ? "已生效" : "未生效" }}</span></td><td style="color:var(--tk-text3)">超出返回 429（AI_RATE_LIMITED）</td></tr>',
            '          <tr><td>工具白名单</td><td><span class="os-tag warn">{{ status.toolWhitelistEnforced ? "已生效" : "仅配置" }}</span></td><td style="color:var(--tk-text3)">平台暂无 AI 工具注册表，未做运行时拦截</td></tr>',
            '          <tr><td>提示词来源</td><td><span class="os-tag" :class="status.promptSource === \'DATABASE\' ? \'ok\' : \'\'">{{ status.promptSource }}</span></td><td style="color:var(--tk-text3)">当前 {{ status.activePrompt }}，代码基线 {{ status.codeBaselineVersion }}</td></tr>',
            '          <tr><td>内容保留清理</td><td><span class="os-tag" :class="status.retentionEnabled ? \'ok\' : \'muted\'">{{ status.retentionEnabled ? "执行中" : "未开启" }}</span></td><td style="color:var(--tk-text3)">{{ status.contentRetentionDays }} 天后逻辑删除，单轮最多 500 条</td></tr>',
            '        </tbody>',
            '      </table>',
            '      <div class="os-note" style="margin-top:14px"><b>后端说明</b>',
            '        <div v-for="(note,i) in status.notes" :key="i" style="margin-top:4px">· {{ note }}</div>',
            '      </div>',
            '    </div>',
            '  </div>',

            /* ---------------- 提示词编辑器 ---------------- */
            '  <div v-if="promptOpen" class="cmd-mask" style="padding-top:4vh" @click.self="promptOpen = false">',
            '    <div class="cmd-panel" style="width:780px;max-height:90vh">',
            '      <div class="cmd-input"><b style="font-size:14px;color:var(--tk-text)">{{ promptView ? "提示词 v" + promptView.versionNo + " · " + promptView.promptCode : "新建提示词版本" }}</b><span style="flex:1"></span><span style="cursor:pointer;color:var(--tk-muted)" @click="promptOpen = false">✕</span></div>',
            '      <div style="overflow:auto;padding:18px 20px;display:flex;flex-direction:column;gap:12px">',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>提示词编码 <em>*</em></label><input class="os-input os-mono" v-model.trim="promptForm.promptCode" :disabled="!creatingPrompt" maxlength="64" placeholder="CAMPUS_ASSISTANT" /></div>',
            '          <div class="os-field"><label>名称 <em>*</em></label><input class="os-input" v-model.trim="promptForm.promptName" maxlength="100" /></div>',
            '        </div>',
            '        <div class="os-form-grid">',
            '          <div class="os-field"><label>场景 <em>*</em></label><select class="os-select" v-model="promptForm.scene"><option v-for="s in scenes" :key="s" :value="s">{{ s }}</option></select></div>',
            '          <div class="os-field"><label>变更说明</label><input class="os-input" v-model.trim="promptForm.changeNote" maxlength="200" placeholder="例如：补充隐私与越权约束" /></div>',
            '        </div>',
            '        <div class="os-field"><label>系统提示词 <em>*</em></label><textarea class="os-textarea" style="height:260px;font-family:ui-monospace,monospace;font-size:12px" v-model="promptForm.systemPrompt" placeholder="你是校园小助手……"></textarea></div>',
            '        <div class="os-field"><label>用户模板（可选，支持 {变量}）</label><textarea class="os-textarea" style="height:80px" v-model="promptForm.userPromptTemplate"></textarea></div>',
            '        <div class="os-note"><b>版本规则</b> 已生效版本不可原地修改：调整内容请新建版本后激活；激活后 AI 对话的 prompt_version 会记录实际版本，未激活任何版本时运行时使用代码内置基线。</div>',
            '        <div style="display:flex;gap:8px;justify-content:flex-end">',
            '          <button class="os-btn" @click="promptOpen = false">{{ creatingPrompt ? "取消" : "关闭" }}</button>',
            '          <button v-if="!creatingPrompt && !promptView.active" class="os-btn" :disabled="promptSaving" @click="savePrompt(false)">保存内容</button>',
            '          <button v-if="creatingPrompt" class="os-btn primary" :disabled="promptSaving" @click="savePrompt(true)">{{ promptSaving ? "保存中…" : "创建版本" }}</button>',
            '        </div>',
            '      </div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var activeTab = Vue.ref('prompts');
            var prompts = Vue.ref([]);
            var promptLoading = Vue.ref(false);
            var promptOpen = Vue.ref(false);
            var promptSaving = Vue.ref(false);
            var creatingPrompt = Vue.ref(true);
            var promptView = Vue.ref(null);
            var promptForm = Vue.reactive(blankPrompt());
            var status = Vue.reactive({
                sensitiveWordEnforced: false, rateLimitEnforced: false, toolWhitelistEnforced: false,
                promptSource: 'CODE_FALLBACK', activePrompt: '', codeBaselineVersion: '', notes: [],
                retentionEnabled: false, contentRetentionDays: 0
            });
            var config = Vue.reactive({
                enabled: true, sensitiveWords: [], toolWhitelist: [],
                rateLimitPerMinute: 30, dailyQuotaPerUser: 200,
                contentRetentionDays: 90, retentionEnabled: false, blockOnHit: true, remark: ''
            });
            var sensitiveWordsText = Vue.ref('');
            var toolWhitelistText = Vue.ref('');
            var saving = Vue.ref(false);
            var scenes = SCENES;

            function loadPrompts() {
                promptLoading.value = true;
                api.get('/api/admin/ai/security/prompts')
                    .then(function (data) { prompts.value = data || []; })
                    .catch(function (error) { El.ElMessage.error(error.message || '提示词加载失败'); })
                    .finally(function () { promptLoading.value = false; });
            }
            function loadStatus() {
                api.get('/api/admin/ai/security/status')
                    .then(function (data) {
                        if (!data) return;
                        status.sensitiveWordEnforced = !!data.sensitiveWordEnforced;
                        status.rateLimitEnforced = !!data.rateLimitEnforced;
                        status.toolWhitelistEnforced = !!data.toolWhitelistEnforced;
                        status.promptSource = data.promptSource;
                        status.activePrompt = data.activePrompt;
                        status.codeBaselineVersion = data.codeBaselineVersion;
                        status.notes = data.notes || [];
                        status.retentionEnabled = !!data.retentionEnabled;
                        status.contentRetentionDays = data.contentRetentionDays;
                    })
                    .catch(function () { /* 状态非关键路径，失败不打断页面 */ });
            }
            function loadConfig() {
                api.get('/api/admin/ai/security/config')
                    .then(function (data) {
                        if (!data) return;
                        config.enabled = !!data.enabled;
                        config.sensitiveWords = data.sensitiveWords || [];
                        config.toolWhitelist = data.toolWhitelist || [];
                        config.rateLimitPerMinute = data.rateLimitPerMinute;
                        config.dailyQuotaPerUser = data.dailyQuotaPerUser;
                        config.contentRetentionDays = data.contentRetentionDays;
                        config.retentionEnabled = !!data.retentionEnabled;
                        config.blockOnHit = !!data.blockOnHit;
                        config.remark = data.remark || '';
                        sensitiveWordsText.value = (config.sensitiveWords || []).join('\n');
                        toolWhitelistText.value = (config.toolWhitelist || []).join(',');
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '安全策略加载失败'); });
            }
            function reloadAll() { loadPrompts(); loadConfig(); loadStatus(); }

            function openCreatePrompt() {
                Object.assign(promptForm, blankPrompt());
                promptView.value = null;
                creatingPrompt.value = true;
                promptOpen.value = true;
            }
            function openViewPrompt(p) {
                Object.assign(promptForm, {
                    id: p.id, promptCode: p.promptCode, promptName: p.promptName, scene: p.scene,
                    systemPrompt: p.systemPrompt, userPromptTemplate: p.userPromptTemplate || '',
                    variables: p.variables || [], changeNote: p.changeNote || ''
                });
                promptView.value = p;
                creatingPrompt.value = false;
                promptOpen.value = true;
            }
            function savePrompt(createNew) {
                if (!promptForm.promptCode || !promptForm.promptName || !promptForm.systemPrompt) {
                    El.ElMessage.warning('编码、名称、系统提示词均为必填');
                    return;
                }
                var payload = {
                    promptCode: promptForm.promptCode, promptName: promptForm.promptName, scene: promptForm.scene,
                    systemPrompt: promptForm.systemPrompt, userPromptTemplate: promptForm.userPromptTemplate,
                    changeNote: promptForm.changeNote
                };
                promptSaving.value = true;
                var request = createNew ? api.post('/api/admin/ai/security/prompts', payload)
                    : api.put('/api/admin/ai/security/prompts/' + promptForm.id, payload);
                request.then(function () {
                    El.ElMessage.success(createNew ? '新版本已创建（未激活）' : '版本内容已保存');
                    promptOpen.value = false;
                    loadPrompts();
                }).catch(function (error) { El.ElMessage.error(error.message || '保存失败'); })
                    .finally(function () { promptSaving.value = false; });
            }
            function activatePrompt(p) {
                El.ElMessageBox.confirm('激活 v' + p.versionNo + ' 后，AI 对话将立即使用该提示词（同编码旧版本自动失活），确定继续？', '激活提示词', { type: 'warning' })
                    .then(function () { return api.post('/api/admin/ai/security/prompts/' + p.id + '/activate'); })
                    .then(function () { El.ElMessage.success('已激活'); loadPrompts(); loadStatus(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '激活失败'); });
            }
            function disablePrompt(p) {
                El.ElMessageBox.confirm(p.active
                    ? '停用当前生效版本后，运行时将回退代码内置基线，确定继续？'
                    : '确定停用该版本？', '停用提示词', { type: 'warning' })
                    .then(function () { return api.post('/api/admin/ai/security/prompts/' + p.id + '/disable'); })
                    .then(function () { El.ElMessage.success('已停用'); loadPrompts(); loadStatus(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '停用失败'); });
            }
            function removePrompt(p) {
                El.ElMessageBox.confirm('删除 v' + p.versionNo + '？该操作不可恢复。', '删除提示词版本', { type: 'warning' })
                    .then(function () { return api.delete('/api/admin/ai/security/prompts/' + p.id); })
                    .then(function () { El.ElMessage.success('已删除'); loadPrompts(); })
                    .catch(function (error) { if (error !== 'cancel' && error !== 'close') El.ElMessage.error((error && error.message) || '删除失败'); });
            }
            function saveConfig() {
                var words = sensitiveWordsText.value.split(/[\n,]/).map(function (v) { return v.trim(); }).filter(Boolean);
                var tools = toolWhitelistText.value.split(',').map(function (v) { return v.trim(); }).filter(Boolean);
                var payload = {
                    enabled: config.enabled,
                    sensitiveWords: words,
                    toolWhitelist: tools,
                    rateLimitPerMinute: config.rateLimitPerMinute,
                    dailyQuotaPerUser: config.dailyQuotaPerUser,
                    contentRetentionDays: config.contentRetentionDays,
                    retentionEnabled: config.retentionEnabled,
                    blockOnHit: config.blockOnHit,
                    remark: config.remark
                };
                saving.value = true;
                api.put('/api/admin/ai/security/config', payload)
                    .then(function () {
                        El.ElMessage.success('策略已保存，运行时已刷新');
                        loadConfig();
                        loadStatus();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '保存失败'); })
                    .finally(function () { saving.value = false; });
            }

            loadPrompts();
            loadConfig();
            loadStatus();

            return {
                activeTab: activeTab, prompts: prompts, promptLoading: promptLoading, promptOpen: promptOpen,
                promptSaving: promptSaving, creatingPrompt: creatingPrompt, promptView: promptView,
                promptForm: promptForm, status: status, config: config,
                sensitiveWordsText: sensitiveWordsText, toolWhitelistText: toolWhitelistText,
                saving: saving, scenes: scenes,
                loadPrompts: loadPrompts, loadConfig: loadConfig, loadStatus: loadStatus, reloadAll: reloadAll,
                openCreatePrompt: openCreatePrompt, openViewPrompt: openViewPrompt, savePrompt: savePrompt,
                activatePrompt: activatePrompt, disablePrompt: disablePrompt, removePrompt: removePrompt,
                saveConfig: saveConfig, PROMPT_STATUS: PROMPT_STATUS
            };
        }
    };
})();
