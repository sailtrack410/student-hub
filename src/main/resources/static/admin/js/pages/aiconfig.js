/* AI 配置页：多供应商管理（Cherry Studio 式布局）。
   Tab1 模型配置：左侧供应商列表，右侧状态信息 + 模型列表；支持从供应商拉取模型批量导入。
   Tab2 模型列表：跨供应商全部模型，标注所属供应商。
   Tab3 默认模型：按类型（聊天/图片识别）设置默认模型 + AI 全局设置。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    var TYPE_LABEL = { CHAT: '语言模型', VISION: '图片识别', ASR: '语音识别', TTS: '语音合成', EMBEDDING: '向量模型', RERANK: '精排' };
    var TYPE_OPTIONS = [
        { value: 'CHAT', label: '语言模型' },
        { value: 'VISION', label: '图片识别' },
        { value: 'ASR', label: '语音识别' }
        ,{ value: 'TTS', label: '语音合成' }
        ,{ value: 'EMBEDDING', label: '向量模型' }
        ,{ value: 'RERANK', label: '精排' }
    ];

    function typeLabel(value) {
        return TYPE_LABEL[value] || value || '-';
    }

    // 上下文策略默认值：与后端 AiConfig 默认值保持一致；后端未返回新字段时前端兜底
    var CONTEXT_DEFAULTS = {
        maxContextTokens: 60000,
        contextSafetyMarginTokens: 512,
        maxOutputTokens: 2048,
        historyTokenRatio: 0.4,
        ragTokenRatio: 0.25,
        attachmentTokenRatio: 0.15,
        summaryEnabled: true,
        summaryTriggerRatio: 0.8,
        summaryKeepRecentTurns: 4,
        tokenEstimateCharsPerToken: 1.5
    };
    var RATIO_FIELDS = ['historyTokenRatio', 'ragTokenRatio', 'attachmentTokenRatio', 'summaryTriggerRatio'];
    var INT_FIELDS = ['maxContextTokens', 'contextSafetyMarginTokens', 'maxOutputTokens', 'summaryKeepRecentTurns'];
    var CONTEXT_FIELD_LABELS = {
        maxContextTokens: '最大上下文 tokens',
        contextSafetyMarginTokens: '安全余量 tokens',
        maxOutputTokens: '最大输出 tokens',
        summaryKeepRecentTurns: '摘要保留最近轮数',
        historyTokenRatio: '历史预算比例',
        ragTokenRatio: '知识库预算比例',
        attachmentTokenRatio: '附件预算比例',
        summaryTriggerRatio: '摘要触发比例',
        tokenEstimateCharsPerToken: '估算系数'
    };

    /* ---------------- Tab1 模型配置 ---------------- */
    window.AdminPages.AiProvidersPage = {
        name: 'AiProvidersPage',
        template: [
            '<div class="ax-pane ax-providers">',
            '  <div class="ax-split">',
            '    <aside class="ax-side">',
            '      <div class="ax-side-head">',
            '        <div class="ax-side-title"><b>供应商</b><span class="ax-count">{{ providers.length }}</span></div>',
            '        <button class="ax-icon-btn" type="button" @click="openCreate" title="新增供应商">+</button>',
            '      </div>',
            '      <div class="ax-search"><input v-model.trim="providerKeyword" placeholder="筛选供应商" /><span>{{ filteredProviders.length }}</span></div>',
            '      <div class="ax-provider-list" v-loading="loading">',
            '        <div v-for="p in filteredProviders" :key="p.providerId" class="ax-provider" :class="{ on: p.providerId === selectedId }" @click="select(p.providerId)">',
            '          <span class="ax-avatar"><img v-if="p.icon" :src="providerIconUrl(p.icon)" alt="" /><template v-else>{{ providerInitial(p) }}</template></span>',
            '          <span class="ax-p-main"><b>{{ p.providerName }}</b><em>{{ p.providerCode }}</em></span>',
            '          <span class="ax-p-side"><i class="ax-dot" :class="providerState(p)"></i><span>{{ p.modelCount }}</span></span>',
            '        </div>',
            '        <div v-if="!filteredProviders.length" class="ax-empty-sm">没有匹配的供应商</div>',
            '      </div>',
            '      <div class="ax-side-foot"><span><i class="ax-dot ok"></i>{{ enabledProviderCount }} 启用</span><span><i class="ax-dot mute"></i>{{ providers.length - enabledProviderCount }} 停用</span></div>',
            '    </aside>',
            '    <section class="ax-main">',
            '      <div v-if="current" class="ax-hero">',
            '        <div class="ax-hero-top">',
            '          <span class="ax-avatar lg"><img v-if="current.icon" :src="providerIconUrl(current.icon)" alt="" /><template v-else>{{ providerInitial(current) }}</template></span>',
            '          <span class="ax-hero-name"><b>{{ current.providerName }}</b><em>{{ current.providerCode }} · {{ protocolLabel(current.protocol) }}</em></span>',
            '          <span class="ax-hero-actions">',
            '            <span class="ax-status" :class="providerState(current)">{{ current.enabled ? "启用中" : "已停用" }}</span>',
            '            <button class="ax-btn" type="button" :disabled="checking" @click="check">{{ checking ? "检查中…" : "检查连通性" }}</button>',
            '            <button class="ax-btn" type="button" @click="openEdit">编辑</button>',
            '            <button class="ax-btn danger" type="button" @click="removeProvider">删除</button>',
            '          </span>',
            '        </div>',
            '        <div class="ax-meta">',
            '          <div class="ax-meta-item wide"><label>Base URL</label><span class="ax-mono" :title="current.baseUrl">{{ current.baseUrl }}</span></div>',
            '          <div class="ax-meta-item"><label>凭据</label><span :class="credentialClass(current)">{{ current.apiKeyConfigured ? "已配置" : "未配置" }}</span></div>',
            '          <div class="ax-meta-item"><label>模型</label><span>{{ current.modelCount }} 个</span></div>',
            '          <div class="ax-meta-item"><label>上次检查</label><span>{{ fmt(current.lastCheckAt) || "尚未检查" }}</span></div>',
            '          <div class="ax-meta-item"><label>检查结果</label><span :class="checkClass(current.lastCheckOk)" :title="current.lastCheckResult">{{ current.lastCheckResult || "尚未检查" }}</span></div>',
            '        </div>',
            '        <div class="ax-hero-foot">',
            '          <span class="ax-hero-hint">启用状态与密钥即时生效；连通性检查会请求供应商 /models 接口。</span>',
            '          <span class="ax-toggle-label"><span class="switch" :class="{ off: !current.enabled }" @click="toggleEnabled(!current.enabled)"><i></i></span>{{ current.enabled ? "已启用" : "已停用" }}</span>',
            '        </div>',
            '      </div>',
            '      <div v-else class="ax-hero ax-empty-hero">选择或新增一个供应商，配置模型接入</div>',
            '      <div class="ax-panel ax-model-panel">',
            '        <div class="ax-panel-head">',
            '          <div><b class="ax-panel-title">模型</b><span class="ax-panel-sub">{{ providerModels.length }} 个 · 启用 {{ enabledModelCount }}</span></div>',
            '          <div class="ax-panel-actions">',
            '            <button class="ax-btn primary" type="button" @click="fetchModels">+ 获取模型</button>',
            '            <button class="ax-btn" type="button" @click="openManual">手动添加</button>',
            '            <button class="ax-btn ghost" type="button" @click="goModels">全部模型</button>',
            '          </div>',
            '        </div>',
            '        <div class="ax-table-wrap">',
            '          <table class="ax-table">',
            '            <colgroup><col style="width:27%"><col style="width:10%"><col style="width:16%"><col style="width:13%"><col style="width:13%"><col style="width:8%"><col style="width:13%"></colgroup>',
            '            <thead><tr><th>模型 ID</th><th>类型</th><th>能力</th><th>上下文</th><th>最大输出</th><th>启用</th><th class="r">操作</th></tr></thead>',
            '            <tbody>',
            '              <tr v-if="!providerModels.length"><td colspan="7" class="ax-empty">还没有模型，点击「获取模型」从供应商拉取或手动添加</td></tr>',
            '              <tr v-for="m in providerModels" :key="m.modelId">',
            '                <td><b class="ax-mono" :title="m.providerModelId">{{ m.providerModelId }}</b><span class="ax-sub">ID {{ m.modelId }}</span></td>',
            '                <td><span class="ax-tag" :class="typeClass(m.modelType)">{{ typeLabel(m.modelType) }}</span></td>',
            '                <td class="ax-muted">{{ capsLabel(m.capabilities) }}</td>',
            '                <td class="ax-num">{{ fmtTokens(m.contextWindowTokens) }}</td>',
            '                <td class="ax-num">{{ fmtTokens(m.maxOutputTokens) }}</td>',
            '                <td><span class="switch" :class="{ off: !m.enabled }" @click="toggleModel(m, !m.enabled)"><i></i></span></td>',
            '                <td class="r"><button class="ax-link" type="button" @click="goModel(m)">编辑</button><button class="ax-link danger" type="button" @click="removeModel(m)">删除</button></td>',
            '              </tr>',
            '            </tbody>',
            '          </table>',
            '        </div>',
            '      </div>',
            '      <div class="ax-provider-bottom" v-if="current">',
            '        <div class="ax-bottom-head"><b>接入概览</b><span>能力覆盖、网关检查与默认模型路由规则</span></div>',
            '        <div class="ax-bottom-grid">',
            '          <section class="ax-subpanel ax-coverage">',
            '            <div class="ax-subhead"><b>能力覆盖</b><span>{{ coveredTypeCount }}/6 类已接入</span></div>',
            '            <div class="ax-cover-grid">',
            '              <div class="ax-cover-item" v-for="row in providerTypeStats" :key="row.value">',
            '                <span class="ax-cover-top"><i class="ax-tag" :class="row.tone">{{ row.label }}</i><b>{{ row.count }}</b></span>',
            '                <span class="ax-cap-bar"><i :style="{ width: row.pct + \'%\' }"></i></span>',
            '              </div>',
            '            </div>',
            '          </section>',
            '          <section class="ax-subpanel ax-connect">',
            '            <div class="ax-subhead"><b>接入检查</b><span class="ax-mono" :title="current.baseUrl">{{ providerHost }}</span></div>',
            '            <div class="ax-kv-list">',
            '              <div><span>调用协议</span><b>{{ protocolLabel(current.protocol) }}</b></div>',
            '              <div><span>凭证状态</span><b :class="credentialClass(current)">{{ current.apiKeyConfigured ? "已配置" : "未配置" }}</b></div>',
            '              <div><span>上次检查</span><b>{{ fmt(current.lastCheckAt) || "尚未检查" }}</b></div>',
            '              <div><span>检查结果</span><b :class="checkClass(current.lastCheckOk)" :title="current.lastCheckResult">{{ current.lastCheckResult || "尚未检查" }}</b></div>',
            '            </div>',
            '            <div class="ax-subfoot"><span>检查会请求供应商 /models 接口</span><button class="ax-btn" type="button" :disabled="checking" @click="check">{{ checking ? "检查中…" : "重新检查" }}</button></div>',
            '          </section>',
            '          <section class="ax-subpanel ax-rules">',
            '            <div class="ax-subhead"><b>路由规则</b><span>默认模型生效条件</span></div>',
            '            <ul class="ax-rule-list">',
            '              <li><i>1</i><span>启用供应商后，其「已启用」模型才会进入默认模型选择。</span></li>',
            '              <li><i>2</i><span>停用只阻断路由，不删除模型；聊天会回退到首个可用模型。</span></li>',
            '              <li><i>3</i><span>修改 Base URL 或 Key 后重新检查，避免网关缓存旧凭证。</span></li>',
            '            </ul>',
            '          </section>',
            '        </div>',
            '      </div>',
            '    </section>',
            '  </div>',
            // 弹层: 新增/编辑供应商（新增时可选预置模板，自动填 URL/协议/图标）
            '  <div class="pmask" :class="{ show: editVisible }" @click.self="editVisible = false">',
            '    <div class="pcard w560">',
            '      <div class="ph"><b>{{ editId ? "编辑供应商" : "新增供应商" }}</b><span class="sp"></span><span class="x" @click="editVisible = false">✕</span></div>',
            '      <template v-if="!editId">',
            '        <div class="plbl">选择预置供应商（自动填好地址与协议），或选择自定义</div>',
            '        <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px">',
            '          <div v-for="t in providerTemplates" :key="t.key" @click="applyTemplate(t)"',
            '               style="display:flex;flex-direction:column;align-items:center;gap:6px;padding:12px 6px;border:1px solid #E0E0E0;border-radius:10px;cursor:pointer;background:#fff"',
            '               :style="editForm.icon === t.icon ? \'border-color:#0066CC;box-shadow:0 0 0 2px rgba(0,102,204,.15)\' : \'\'">',
            '            <img :src="providerIconUrl(t.icon)" width="28" height="28" style="border-radius:6px" />',
            '            <span style="font-size:12px;color:#1D1D1F">{{ t.name }}</span>',
            '          </div>',
            '        </div>',
            '      </template>',
            '      <div class="fld"><label>名称 <em>*</em></label><div class="pin"><input v-model.trim="editForm.providerName" maxlength="64" placeholder="如 硅基流动" /></div></div>',
            '      <div class="fld"><label>Base URL <em>*</em></label><div class="pin"><input v-model.trim="editForm.baseUrl" placeholder="如 https://api.siliconflow.cn/v1" /></div></div>',
            '      <div class="frow">',
            '        <div class="fld"><label>调用协议</label><div class="pin"><select v-model="editForm.protocol" style="width:100%">',
            '          <option value="OPENAI_CHAT">OpenAI · /chat/completions</option>',
            '          <option value="ANTHROPIC_MESSAGES">Anthropic · /messages</option>',
            '          <option value="OPENAI_RESPONSES">OpenAI · /responses</option>',
            '        </select></div></div>',
            '        <div class="fld"><label>流式输出</label><div style="display:flex;align-items:center;gap:10px;height:40px">',
            '          <span class="switch" :class="{ off: !editForm.streaming }" @click="editForm.streaming = !editForm.streaming"><i></i></span>',
            '          <span style="font-size:13px;color:#3A3A3C">SSE 流式</span></div></div>',
            '      </div>',
            '      <div class="fld"><label>API Key <em v-if="!editId">*</em></label><div class="pin"><input v-model="editForm.apiKey" type="password" :placeholder="editId ? \'留空表示保持原值\' : \'sk-...\'" /></div></div>',
            '      <div style="font-size:12px;color:#9B9B9B" v-if="editId">调用第三方 API 的访问密钥，保存后仅显示尾号。</div>',
            '      <div class="fld" v-if="!editId"><label>编码</label><div class="pin"><input v-model.trim="editForm.providerCode" placeholder="留空自动生成，小写字母/数字/短横线" /></div></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="editVisible = false">取消</button>',
            '        <button class="c blue" :disabled="saving" @click="saveProvider">{{ saving ? "保存中…" : "保存" }}</button></div>',
            '    </div>',
            '  </div>',

            // 弹层: 手动添加模型
            '  <div class="pmask" :class="{ show: manualVisible }" @click.self="manualVisible = false">',
            '    <div class="pcard w440">',
            '      <div class="ph"><b>手动添加模型</b><span class="sp"></span><span class="x" @click="manualVisible = false">✕</span></div>',
            '      <div class="fld"><label>模型 ID <em>*</em></label><div class="pin"><input v-model.trim="manualModelId" placeholder="如 deepseek-ai/DeepSeek-V4" @input="guessManualType" /></div></div>',
            '      <div style="font-size:12px;color:#9B9B9B;margin-top:-8px">类型按模型 ID 自动推断（可手动调整），支持多选</div>',
            '      <div class="fld"><label>类型（可多选）</label>',
            '        <div style="display:flex;flex-wrap:wrap;gap:8px">',
            '          <label v-for="t in typeOptions" :key="t.value" style="display:inline-flex;align-items:center;gap:6px;font-size:13px;color:#3A3A3C;cursor:pointer">',
            '            <span class="chk" @click.prevent="toggleManualType(t.value)">',
            '              <span class="bx" :class="{ off: manualTypes.indexOf(t.value) < 0 }">',
            '                <svg v-if="manualTypes.indexOf(t.value) >= 0" width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round"><path d="M1.5 5.5l2.5 2.5 4.5-5"/></svg>',
            '              </span></span>{{ t.label }}',
            '          </label>',
            '        </div></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="manualVisible = false">取消</button>',
            '        <button class="c blue" :disabled="saving" @click="saveManual">{{ saving ? "添加中…" : "添加" }}</button></div>',
            '    </div>',
            '  </div>',

            // 弹层: 从供应商获取模型
            '  <div class="pmask" :class="{ show: fetchVisible }" @click.self="fetchVisible = false">',
            '    <div class="pcard w720" style="max-height:80vh">',
            '      <div class="ph"><b>从供应商获取模型</b><span class="no" v-if="remoteLoaded">共 {{ remoteModels.length }} 个 · 已选 {{ selectedRemote.length }}</span><span class="sp"></span><span class="x" @click="fetchVisible = false">✕</span></div>',
            '      <div v-if="fetching" style="text-align:center;color:#9B9B9B;padding:40px 0">正在拉取模型列表…</div>',
            '      <template v-else-if="remoteLoaded">',
            '        <div style="display:flex;gap:8px;align-items:center">',
            '          <div class="pin" style="flex:1"><input v-model="remoteKeyword" placeholder="搜索模型名称..." /></div>',
            '          <button class="c ghost" style="height:40px;border-radius:8px" @click="selectAllRemote">选择所有</button>',
            '          <button class="c ghost" style="height:40px;border-radius:8px" @click="selectedRemote = []">清空选择</button>',
            '        </div>',
            '        <div style="display:flex;flex-direction:column;gap:2px;max-height:320px;overflow:auto">',
            '          <div v-for="m in filteredRemote" :key="m.id" style="display:flex;align-items:center;gap:10px;padding:8px 4px;border-bottom:1px solid #F0F0F5">',
            '            <span class="chk" style="flex:none" @click="toggleRemote(m.id, selectedRemote.indexOf(m.id) < 0)">',
            '              <span class="bx" :class="{ off: selectedRemote.indexOf(m.id) < 0 }">',
            '                <svg v-if="selectedRemote.indexOf(m.id) >= 0" width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round"><path d="M1.5 5.5l2.5 2.5 4.5-5"/></svg>',
            '              </span></span>',
            '            <b style="flex:1;font-size:13px;font-weight:500;color:#1D1D1F">{{ m.id }}</b>',
            '            <div class="pin" style="width:130px"><select :value="remoteType[m.id] || \'CHAT\'" @change="function (e) { remoteType[m.id] = e.target.value }" style="width:100%">',
            '              <option v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</option></select></div>',
            '          </div>',
            '          <div v-if="!filteredRemote.length" style="text-align:center;color:#9B9B9B;padding:24px 0">没有匹配的模型</div>',
            '        </div>',
            '      </template>',
            '      <div class="pbtn"><span style="font-size:12px;color:#9B9B9B;line-height:40px">已选择 {{ selectedRemote.length }} 个模型</span>',
            '        <span class="sp"></span><button class="c ghost" @click="fetchVisible = false">关闭</button>',
            '        <button class="c blue" :disabled="!selectedRemote.length || importing" @click="importRemote">{{ importing ? "导入中…" : "导入" }}</button></div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var providers = Vue.ref([]);
            var selectedId = Vue.ref(null);
            var providerKeyword = Vue.ref('');
            var allModels = Vue.ref([]);
            var openModelsTab = Vue.inject ? Vue.inject('axOpenModels', null) : null;
            var loading = Vue.ref(false);
            var checking = Vue.ref(false);
            var saving = Vue.ref(false);

            var editVisible = Vue.ref(false);
            var editId = Vue.ref(null);
            var editForm = Vue.reactive({ providerName: '', baseUrl: '', apiKey: '', providerCode: '', icon: '', protocol: 'OPENAI_CHAT', streaming: true });
            var providerTemplates = [
                { key: 'siliconflow', name: '硅基流动', icon: 'siliconcloud-color', baseUrl: 'https://api.siliconflow.cn/v1', protocol: 'OPENAI_CHAT' },
                { key: 'openai', name: 'OpenAI', icon: 'openai', baseUrl: 'https://api.openai.com/v1', protocol: 'OPENAI_CHAT' },
                { key: 'anthropic', name: 'Anthropic', icon: 'anthropic', baseUrl: 'https://api.anthropic.com/v1', protocol: 'ANTHROPIC_MESSAGES' },
                { key: 'deepseek', name: 'DeepSeek', icon: 'deepseek-color', baseUrl: 'https://api.deepseek.com/v1', protocol: 'OPENAI_CHAT' },
                { key: 'zhipu', name: '智谱 AI', icon: 'zhipu-color', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', protocol: 'OPENAI_CHAT' },
                { key: 'moonshot', name: 'Moonshot', icon: 'moonshot', baseUrl: 'https://api.moonshot.cn/v1', protocol: 'OPENAI_CHAT' },
                { key: 'qwen', name: '阿里百炼', icon: 'qwen-color', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', protocol: 'OPENAI_CHAT' },
                { key: 'doubao', name: '火山方舟', icon: 'doubao-color', baseUrl: 'https://ark.cn-beijing.volces.com/api/v3', protocol: 'OPENAI_CHAT' }
            ];

            var manualVisible = Vue.ref(false);
            var manualModelId = Vue.ref('');
            var manualTypes = Vue.ref(['CHAT']);

            // 前端预推断：输入模型 ID 时即时勾选建议类型
            function guessManualType() {
                var id = (manualModelId.value || '').toLowerCase();
                if (!id) return;
                var types = ['CHAT'];
                if (/vl|vision|image|4v|-v-/.test(id)) types.push('VISION');
                if (/asr|whisper|tts|audio|speech|voice/.test(id)) types.push('ASR');
                if (/embedding|embed|bge-|m3e/.test(id)) { manualTypes.value = ['EMBEDDING']; return; }
                manualTypes.value = types;
            }
            function inferTypeFromId(id) {
                var s = String(id || '').toLowerCase();
                if (/rerank|reranker/.test(s)) return 'RERANK';
                if (/embedding|embed|bge-|m3e|gte-|text-embedding/.test(s)) return 'EMBEDDING';
                if (/asr|whisper/.test(s)) return 'ASR';
                if (/tts|audio|speech|voice/.test(s)) return 'TTS';
                if (/vl|vision|image|4v|ocr/.test(s)) return 'VISION';
                return 'CHAT';
            }
            function toggleManualType(v) {
                var i = manualTypes.value.indexOf(v);
                if (i >= 0) {
                    if (manualTypes.value.length > 1) manualTypes.value.splice(i, 1); // 至少保留一个
                } else {
                    manualTypes.value.push(v);
                }
            }
            // 勾选类型 → 后端 capabilities 标签（主类型推断也在后端按 capabilities 生成）
            function capOf(typeValue) {
                return { CHAT: 'stream', VISION: 'vision', ASR: 'audio', EMBEDDING: 'embedding' }[typeValue] || 'stream';
            }
            // capabilities 逗号串 → 中文标签列表
            var CAP_TEXT = { stream: '流式', vision: '视觉', audio: '语音', tools: '工具', structured: '结构化', reasoning: '推理', embedding: '向量' };
            function capLabels(caps) {
                if (!caps) return '';
                return caps.split(',').map(function (c) { return CAP_TEXT[c.trim()] || c.trim(); }).join(' · ');
            }

            var fetchVisible = Vue.ref(false);
            var fetching = Vue.ref(false);
            var remoteLoaded = Vue.ref(false);
            var remoteModels = Vue.ref([]);
            var remoteKeyword = Vue.ref('');
            var selectedRemote = Vue.ref([]);
            var remoteType = Vue.reactive({});
            var importing = Vue.ref(false);

            var current = Vue.computed(function () {
                var id = selectedId.value;
                return providers.value.find(function (p) { return p.providerId === id; }) || null;
            });

            var filteredProviders = Vue.computed(function () {
                var kw = providerKeyword.value.trim().toLowerCase();
                if (!kw) return providers.value;
                return providers.value.filter(function (p) {
                    return String(p.providerName || '').toLowerCase().indexOf(kw) >= 0
                        || String(p.providerCode || '').toLowerCase().indexOf(kw) >= 0;
                });
            });
            var enabledProviderCount = Vue.computed(function () {
                return providers.value.filter(function (p) { return p.enabled; }).length;
            });
            var providerModels = Vue.computed(function () {
                var id = selectedId.value;
                return allModels.value.filter(function (m) { return m.providerId === id; });
            });
            var enabledModelCount = Vue.computed(function () {
                return providerModels.value.filter(function (m) { return m.enabled; }).length;
            });
            var PROVIDER_CAP_TYPES = [
                { value: 'CHAT', label: '语言', tone: 'chat' },
                { value: 'VISION', label: '图片', tone: 'vision' },
                { value: 'ASR', label: '转写', tone: 'audio' },
                { value: 'TTS', label: '合成', tone: 'audio' },
                { value: 'EMBEDDING', label: '向量', tone: 'embed' },
                { value: 'RERANK', label: '精排', tone: 'rerank' }
            ];
            var providerTypeStats = Vue.computed(function () {
                var counts = {};
                providerModels.value.forEach(function (m) {
                    var type = m.modelType || 'CHAT';
                    counts[type] = (counts[type] || 0) + 1;
                });
                var max = 1;
                PROVIDER_CAP_TYPES.forEach(function (t) { max = Math.max(max, counts[t.value] || 0); });
                return PROVIDER_CAP_TYPES.map(function (t) {
                    var count = counts[t.value] || 0;
                    return { value: t.value, label: t.label, tone: t.tone, count: count, pct: count ? Math.max(10, Math.round(count / max * 100)) : 0 };
                });
            });
            var coveredTypeCount = Vue.computed(function () {
                return providerTypeStats.value.filter(function (row) { return row.count > 0; }).length;
            });
            var providerHost = Vue.computed(function () {
                var url = current.value && current.value.baseUrl ? String(current.value.baseUrl) : '';
                var match = url.match(/^https?:\/\/([^/]+)/i);
                return match ? match[1] : (url || '-');
            });

            function providerInitial(p) { return String((p && p.providerName) || '?').slice(0, 1); }
            function providerState(p) {
                if (!p || !p.enabled) return 'mute';
                return p.apiKeyConfigured ? 'ok' : 'warn';
            }
            function statusLabel(on) { return on ? '启用中' : '已停用'; }
            var PROTOCOL_LABELS = { OPENAI_CHAT: 'OpenAI Chat', ANTHROPIC_MESSAGES: 'Anthropic Messages', OPENAI_RESPONSES: 'OpenAI Responses' };
            function protocolLabel(value) { return PROTOCOL_LABELS[value] || value || '-'; }
            function credentialClass(p) { return p && p.apiKeyConfigured ? 'ax-ok' : 'ax-bad'; }
            function checkClass(ok) { return ok === true ? 'ax-ok' : (ok === false ? 'ax-bad' : 'ax-muted'); }
            function fmtTokens(value) {
                if (value === null || value === undefined || value === '') return '未配置';
                var num = Number(value);
                return isFinite(num) ? num.toLocaleString('en-US') + ' tokens' : String(value);
            }
            function capsLabel(caps) { return capLabels(caps) || '—'; }
            var TYPE_TONE = { CHAT: 'chat', VISION: 'vision', ASR: 'audio', TTS: 'audio', EMBEDDING: 'embed', RERANK: 'rerank' };
            function typeClass(type) { return TYPE_TONE[type] || 'chat'; }
            function fetchModels() { addCommand('fetch'); }
            function openManual() { addCommand('manual'); }
            function goModels() { if (openModelsTab) openModelsTab(''); }
            function goModel(m) { if (openModelsTab) openModelsTab(m.providerModelId); }

            var filteredRemote = Vue.computed(function () {
                var kw = remoteKeyword.value.trim().toLowerCase();
                if (!kw) return remoteModels.value;
                return remoteModels.value.filter(function (m) {
                    return m.id.toLowerCase().indexOf(kw) >= 0;
                });
            });

            function load() {
                loading.value = true;
                Promise.all([
                    api.get('/api/admin/ai/providers'),
                    api.get('/api/admin/ai/models')
                ]).then(function (results) {
                    providers.value = results[0] || [];
                    allModels.value = results[1] || [];
                    if (!selectedId.value && providers.value.length) {
                        var preferred = providers.value.find(function (p) { return p.enabled && p.apiKeyConfigured; })
                            || providers.value.find(function (p) { return p.enabled; })
                            || providers.value[0];
                        selectedId.value = preferred.providerId;
                    }
                }).catch(function (error) {
                    El.ElMessage.error(error.message);
                }).finally(function () {
                    loading.value = false;
                });
            }

            function select(providerId) {
                selectedId.value = providerId;
            }

            function check() {
                if (!current.value) return;
                checking.value = true;
                api.post('/api/admin/ai/providers/' + current.value.providerId + '/check')
                    .then(function (result) {
                        if (current.value) {
                            current.value.lastCheckAt = result.checkedAt;
                            current.value.lastCheckOk = result.ok;
                            current.value.lastCheckResult = result.result;
                        }
                        if (result.ok) {
                            El.ElMessage.success(result.result);
                        } else {
                            El.ElMessage.error(result.result);
                        }
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { checking.value = false; });
            }

            function toggleEnabled(value) {
                api.put('/api/admin/ai/providers/' + current.value.providerId, { enabled: value })
                    .then(function (updated) {
                        if (current.value) current.value.enabled = value;
                        if (updated && updated.lastCheckAt !== undefined) current.value.lastCheckAt = updated.lastCheckAt;
                        El.ElMessage.success(value ? '供应商已启用' : '供应商已停用');
                    })
                    .catch(function (error) {
                        El.ElMessage.error(error.message);
                    });
            }

            function openCreate() {
                editId.value = null;
                editForm.providerName = '';
                editForm.baseUrl = '';
                editForm.apiKey = '';
                editForm.providerCode = '';
                editForm.icon = '';
                editForm.protocol = 'OPENAI_CHAT';
                editForm.streaming = true;
                editVisible.value = true;
            }

            function openEdit() {
                editId.value = current.value.providerId;
                editForm.providerName = current.value.providerName;
                editForm.baseUrl = current.value.baseUrl;
                editForm.apiKey = '';
                editForm.providerCode = '';
                editForm.icon = current.value.icon || '';
                editForm.protocol = current.value.protocol || 'OPENAI_CHAT';
                editForm.streaming = current.value.streaming !== false;
                editVisible.value = true;
            }

            // 预置供应商模板：点选后自动填名称前缀/Base URL/协议/图标
            var providerTemplates = [
                { key: 'siliconflow', name: '硅基流动', icon: 'siliconcloud-color', baseUrl: 'https://api.siliconflow.cn/v1', protocol: 'OPENAI_CHAT' },
                { key: 'openai', name: 'OpenAI', icon: 'openai', baseUrl: 'https://api.openai.com/v1', protocol: 'OPENAI_CHAT' },
                { key: 'anthropic', name: 'Anthropic', icon: 'anthropic', baseUrl: 'https://api.anthropic.com/v1', protocol: 'ANTHROPIC_MESSAGES' },
                { key: 'deepseek', name: 'DeepSeek', icon: 'deepseek-color', baseUrl: 'https://api.deepseek.com/v1', protocol: 'OPENAI_CHAT' },
                { key: 'zhipu', name: '智谱 AI', icon: 'zhipu-color', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', protocol: 'OPENAI_CHAT' },
                { key: 'moonshot', name: 'Moonshot', icon: 'moonshot', baseUrl: 'https://api.moonshot.cn/v1', protocol: 'OPENAI_CHAT' },
                { key: 'qwen', name: '阿里百炼', icon: 'qwen-color', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', protocol: 'OPENAI_CHAT' },
                { key: 'doubao', name: '火山方舟', icon: 'doubao-color', baseUrl: 'https://ark.cn-beijing.volces.com/api/v3', protocol: 'OPENAI_CHAT' }
            ];
            function applyTemplate(t) {
                editForm.icon = t.icon;
                editForm.baseUrl = t.baseUrl;
                editForm.protocol = t.protocol;
                // 名称：为空或仍是某个模板的预填名（用户未手动改过）时跟随切换，否则保留用户手输名称
                var isAutoName = !editForm.providerName ||
                    providerTemplates.some(function (x) { return x.name === editForm.providerName; });
                if (isAutoName) editForm.providerName = t.name;
            }
            function providerIconUrl(icon) {
                // lobe-icons 静态包：icon 值已含变体后缀（如 siliconcloud-color / openai）
                return 'https://cdn.jsdelivr.net/npm/@lobehub/icons-static-svg@1.95.1/icons/' + icon + '.svg';
            }

            function saveProvider() {
                if (!editForm.providerName || !editForm.baseUrl) {
                    El.ElMessage.warning('请填写名称和 Base URL');
                    return;
                }
                if (!editId.value && !editForm.apiKey) {
                    El.ElMessage.warning('新供应商需要填写 API Key');
                    return;
                }
                saving.value = true;
                var request = editId.value
                    ? api.put('/api/admin/ai/providers/' + editId.value, {
                        providerName: editForm.providerName,
                        baseUrl: editForm.baseUrl,
                        apiKey: editForm.apiKey || null,
                        icon: editForm.icon || null,
                        protocol: editForm.protocol,
                        streaming: editForm.streaming
                    })
                    : api.post('/api/admin/ai/providers', {
                        providerName: editForm.providerName,
                        baseUrl: editForm.baseUrl,
                        apiKey: editForm.apiKey,
                        providerCode: editForm.providerCode || null,
                        icon: editForm.icon || null,
                        protocol: editForm.protocol,
                        streaming: editForm.streaming
                    });
                request.then(function () {
                    El.ElMessage.success('已保存');
                    editVisible.value = false;
                    load();
                }).catch(function (error) {
                    El.ElMessage.error(error.message);
                }).finally(function () {
                    saving.value = false;
                });
            }

            function removeProvider() {
                El.ElMessageBox.confirm(
                        '删除供应商将同时删除其下 ' + current.value.modelCount + ' 个模型，确定删除「' +
                        current.value.providerName + '」？', '确认删除',
                        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
                    .then(function () {
                        return api.delete('/api/admin/ai/providers/' + current.value.providerId);
                    })
                    .then(function () {
                        El.ElMessage.success('已删除');
                        selectedId.value = null;
                        load();
                    })
                    .catch(function (error) {
                        if (error !== 'cancel' && error !== 'close') {
                            El.ElMessage.error(error.message || '删除失败');
                        }
                    });
            }

            function addCommand(command) {
                if (command === 'fetch') {
                    fetchVisible.value = true;
                    remoteLoaded.value = false;
                    remoteModels.value = [];
                    selectedRemote.value = [];
                    remoteKeyword.value = '';
                    fetching.value = true;
                    api.get('/api/admin/ai/providers/' + current.value.providerId + '/remote-models')
                        .then(function (ids) {
                            var list = ids || [];
                            remoteModels.value = list.map(function (id) { return { id: id }; });
                            list.forEach(function (id) { remoteType[id] = inferTypeFromId(id); });
                            remoteLoaded.value = true;
                        })
                        .catch(function (error) {
                            El.ElMessage.error(error.message);
                            fetchVisible.value = false;
                        })
                        .finally(function () { fetching.value = false; });
                } else if (command === 'manual') {
                    manualModelId.value = '';
                    manualTypes.value = ['CHAT'];
                    manualVisible.value = true;
                }
            }

            function saveManual() {
                if (!manualModelId.value) {
                    El.ElMessage.warning('请填写模型 ID');
                    return;
                }
                saving.value = true;
                api.post('/api/admin/ai/providers/' + current.value.providerId + '/models/import',
                        { models: [{ providerModelId: manualModelId.value, capabilities: manualTypes.value.map(capOf) }] })
                    .then(function (added) {
                        if (added > 0) {
                            El.ElMessage.success('已添加');
                        } else {
                            El.ElMessage.warning('该模型已存在');
                        }
                        manualVisible.value = false;
                        load();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { saving.value = false; });
            }

            function toggleRemote(modelId, checked) {
                var index = selectedRemote.value.indexOf(modelId);
                if (checked && index < 0) {
                    selectedRemote.value = selectedRemote.value.concat([modelId]);
                } else if (!checked && index >= 0) {
                    selectedRemote.value = selectedRemote.value.slice(0, index)
                        .concat(selectedRemote.value.slice(index + 1));
                }
            }

            function selectAllRemote() {
                selectedRemote.value = filteredRemote.value.map(function (m) { return m.id; });
            }

            function importRemote() {
                importing.value = true;
                var models = selectedRemote.value.map(function (id) {
                    return { providerModelId: id, modelType: remoteType[id] || 'CHAT' };
                });
                api.post('/api/admin/ai/providers/' + current.value.providerId + '/models/import', { models: models })
                    .then(function (added) {
                        El.ElMessage.success('已导入 ' + added + ' 个模型（重复自动跳过）');
                        fetchVisible.value = false;
                        load();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { importing.value = false; });
            }

            function toggleModel(model, value) {
                api.put('/api/admin/ai/models/' + model.modelId, { enabled: value })
                    .then(function () {
                        model.enabled = value;
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }

            function removeModel(model) {
                El.ElMessageBox.confirm('确定删除模型「' + model.providerModelId + '」？', '确认删除',
                        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
                    .then(function () {
                        return api.delete('/api/admin/ai/models/' + model.modelId);
                    })
                    .then(function () {
                        El.ElMessage.success('已删除');
                        load();
                    })
                    .catch(function (error) {
                        if (error !== 'cancel' && error !== 'close') {
                            El.ElMessage.error(error.message || '删除失败');
                        }
                    });
            }

            load();

            return {
                providers: providers, selectedId: selectedId, allModels: allModels, loading: loading,
                checking: checking, saving: saving,
                current: current, providerModels: providerModels, providerKeyword: providerKeyword,
                filteredProviders: filteredProviders, enabledProviderCount: enabledProviderCount,
                enabledModelCount: enabledModelCount, providerTypeStats: providerTypeStats,
                coveredTypeCount: coveredTypeCount, providerHost: providerHost,
                providerInitial: providerInitial, providerState: providerState,
                statusLabel: statusLabel, protocolLabel: protocolLabel, credentialClass: credentialClass,
                checkClass: checkClass, fmtTokens: fmtTokens, capsLabel: capsLabel, typeClass: typeClass,
                fetchModels: fetchModels, openManual: openManual, goModels: goModels, goModel: goModel,
                editVisible: editVisible, editId: editId, editForm: editForm,
                providerTemplates: providerTemplates, applyTemplate: applyTemplate, providerIconUrl: providerIconUrl,
                manualVisible: manualVisible, manualModelId: manualModelId, manualTypes: manualTypes,
                guessManualType: guessManualType, toggleManualType: toggleManualType,
                fetchVisible: fetchVisible, fetching: fetching, remoteLoaded: remoteLoaded,
                remoteModels: remoteModels, remoteKeyword: remoteKeyword,
                selectedRemote: selectedRemote, remoteType: remoteType, importing: importing,
                filteredRemote: filteredRemote,
                load: load, select: select, check: check, toggleEnabled: toggleEnabled,
                openCreate: openCreate, openEdit: openEdit, saveProvider: saveProvider,
                removeProvider: removeProvider, addCommand: addCommand, saveManual: saveManual,
                toggleRemote: toggleRemote, selectAllRemote: selectAllRemote, importRemote: importRemote,
                toggleModel: toggleModel, removeModel: removeModel,
                typeOptions: TYPE_OPTIONS, typeLabel: typeLabel, capLabels: capLabels,
                fmt: api.fmtDateTime,
                PlusIcon: ElementPlusIconsVue.Plus
            };
        }
    };

    /* ---------------- Tab2 模型列表 ---------------- */
    window.AdminPages.AiModelsPage = {
        name: 'AiModelsPage',
        template: [
            '<div class="ax-pane ax-models-page">',
            '  <div class="ax-strip">',
            '    <div class="ax-stat"><span>模型总数</span><b>{{ rows.length }}</b></div>',
            '    <div class="ax-stat"><span>已启用</span><b class="ax-accent">{{ enabledCount }}</b></div>',
            '    <div class="ax-stat"><span>语言模型</span><b>{{ chatCount }}</b></div>',
            '    <div class="ax-stat"><span>供应商</span><b>{{ providerCount }}</b></div>',
            '    <span class="ax-strip-sp"></span>',
            '    <span class="ax-strip-note">跨供应商统一模型视图 · 改动即时生效</span>',
            '    <button class="ax-btn" type="button" @click="load">刷新</button>',
            '  </div>',
            '  <div class="ax-panel ax-grow">',
            '    <div class="ax-panel-head ax-toolbar-head">',
            '      <div><b class="ax-panel-title">模型汇总</b><span class="ax-panel-sub">共 {{ rows.length }} 个模型</span></div>',
            '      <div class="ax-toolbar">',
            '        <div class="ax-search wide"><input v-model.trim="keyword" placeholder="按模型 ID、供应商搜索" @keyup.enter="load" /></div>',
            '        <select class="ax-select" v-model="modelType" @change="load">',
            '          <option value="">全部类型</option>',
            '          <option v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</option>',
            '        </select>',
            '        <button class="ax-btn primary" type="button" @click="load">搜索</button>',
            '        <button v-if="keyword || modelType" class="ax-link" type="button" @click="resetFilters">重置</button>',
            '      </div>',
            '    </div>',
            '    <div class="ax-table-wrap">',
            '      <table class="ax-table">',
            '        <colgroup><col style="width:30%"><col style="width:15%"><col style="width:10%"><col style="width:15%"><col style="width:15%"><col style="width:7%"><col style="width:8%"></colgroup>',
            '        <thead><tr><th>模型</th><th>供应商</th><th>类型</th><th>上下文窗口</th><th>最大输出</th><th>启用</th><th class="r">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-if="!rows.length && !loading"><td colspan="7" class="ax-empty">暂无模型，先在「模型配置」中获取或手动添加</td></tr>',
            '          <tr v-if="loading"><td colspan="7" class="ax-empty">加载中…</td></tr>',
            '          <tr v-for="row in rows" :key="row.modelId">',
            '            <td><b class="ax-mono">{{ row.providerModelId }}</b><span class="ax-sub">ID {{ row.modelId }}</span></td>',
            '            <td><span class="ax-provider-tag">{{ row.providerName }}</span></td>',
            '            <td><span class="ax-tag" :class="typeClass(row.modelType)">{{ typeLabel(row.modelType) }}</span></td>',
            '            <td class="ax-num">{{ fmtTokens(row.contextWindowTokens) }}</td>',
            '            <td class="ax-num">{{ fmtTokens(row.maxOutputTokens) }}</td>',
            '            <td><span class="switch" :class="{ off: !row.enabled }" @click="toggle(row, !row.enabled)"><i></i></span></td>',
            '            <td class="r"><button class="ax-link" type="button" @click="openEdit(row)">编辑</button><button class="ax-link danger" type="button" @click="remove(row)">删除</button></td>',
            '          </tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',
            '  </div>',
            '  <div class="ax-model-bottom" v-if="rows.length">',
            '    <section class="ax-subpanel">',
            '      <div class="ax-subhead"><b>类型分布</b><span>{{ rows.length }} 个模型</span></div>',
            '      <div class="ax-model-bar-list">',
            '        <div class="ax-model-bar" v-for="t in modelTypeStats" :key="t.value">',
            '          <span>{{ t.label }}</span><i><b :style="{ width: t.pct + \'%\' }"></b></i><em>{{ t.count }}</em>',
            '        </div>',
            '      </div>',
            '    </section>',
            '    <section class="ax-subpanel">',
            '      <div class="ax-subhead"><b>供应商分布</b><span>{{ supplierStats.length }} 个供应商</span></div>',
            '      <div class="ax-model-bar-list">',
            '        <div class="ax-model-bar" v-for="s in supplierStats" :key="s.name">',
            '          <span :title="s.name">{{ s.name }}</span><i><b :style="{ width: s.pct + \'%\' }"></b></i><em>{{ s.count }}</em>',
            '        </div>',
            '      </div>',
            '    </section>',
            '    <section class="ax-subpanel">',
            '      <div class="ax-subhead"><b>操作提示</b><span>启用与路由</span></div>',
            '      <ul class="ax-rule-list">',
            '        <li><i>1</i><span>启用开关只影响路由，不删除模型；停用供应商会连带阻断其模型。</span></li>',
            '        <li><i>2</i><span>上下文窗口与最大输出仅对语言模型（CHAT）生效。</span></li>',
            '        <li><i>3</i><span>默认模型只列出已启用供应商下的已启用模型。</span></li>',
            '      </ul>',
            '    </section>',
            '  </div>',
            // 弹层: 编辑模型（上下文窗口 / 最大输出）
            '  <div class="pmask" :class="{ show: editVisible }" @click.self="editVisible = false">',
            '    <div class="pcard w440">',
            '      <div class="ph"><b>编辑模型</b><span class="no">{{ editForm.providerModelId }}</span><span class="sp"></span><span class="x" @click="editVisible = false">✕</span></div>',
            '      <div class="fld"><label>模型类型</label><div class="pin"><select v-model="editForm.modelType" style="width:100%"><option v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</option></select></div></div>',
            '      <div class="fld"><label>启用</label><div style="display:flex;align-items:center;gap:12px;height:40px">',
            '        <span class="switch" :class="{ off: !editForm.enabled }" @click="editForm.enabled = !editForm.enabled"><i></i></span>',
            '        <span style="font-size:13px;color:#3A3A3C">{{ editForm.enabled ? "启用" : "停用" }}</span>',
            '      </div></div>',
            '      <template v-if="editForm.modelType === \'CHAT\'">',
            '        <div class="frow">',
            '          <div class="fld"><label>上下文窗口 tokens</label><div class="pin"><input type="number" min="1" step="1000" v-model.number="editForm.contextWindowTokens" placeholder="如 128000" /></div></div>',
            '          <div class="fld"><label>最大输出 tokens</label><div class="pin"><input type="number" min="1" step="256" v-model.number="editForm.maxOutputTokens" placeholder="如 2048" /></div></div>',
            '        </div>',
            '        <div style="font-size:12px;color:#9B9B9B">上下文窗口留空时使用全局「最大上下文 tokens」兜底；最大输出留空时使用全局默认值。</div>',
            '      </template>',
            '      <div v-else style="font-size:12px;color:#9B9B9B">仅语言模型（CHAT）需要配置上下文窗口与最大输出；改为其他类型后保存会清空这两项。</div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="editVisible = false">取消</button>',
            '        <button class="c blue" :disabled="saving" @click="saveEdit">{{ saving ? "保存中…" : "保存" }}</button></div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var keyword = Vue.ref('');
            var modelType = Vue.ref('');
            var rows = Vue.ref([]);
            var loading = Vue.ref(false);
            var saving = Vue.ref(false);
            var editVisible = Vue.ref(false);
            var loadSeq = 0;
            var editForm = Vue.reactive({ modelId: null, providerModelId: '', modelType: 'CHAT', enabled: true,
                contextWindowTokens: null, maxOutputTokens: null });

            var enabledCount = Vue.computed(function () {
                return rows.value.filter(function (r) { return r.enabled; }).length;
            });
            var chatCount = Vue.computed(function () {
                return rows.value.filter(function (r) { return r.modelType === 'CHAT'; }).length;
            });
            var providerCount = Vue.computed(function () {
                var seen = {};
                rows.value.forEach(function (r) { seen[r.providerId] = true; });
                return Object.keys(seen).length;
            });
            var TYPE_TONE = { CHAT: 'chat', VISION: 'vision', ASR: 'audio', TTS: 'audio', EMBEDDING: 'embed', RERANK: 'rerank' };
            function typeClass(type) { return TYPE_TONE[type] || 'chat'; }
            var modelTypeStats = Vue.computed(function () {
                var counts = {};
                rows.value.forEach(function (row) { counts[row.modelType || 'CHAT'] = (counts[row.modelType || 'CHAT'] || 0) + 1; });
                var total = Math.max(1, rows.value.length);
                return TYPE_OPTIONS.map(function (t) {
                    var count = counts[t.value] || 0;
                    return { value: t.value, label: t.label, count: count, pct: Math.round(count / total * 100) };
                });
            });
            var supplierStats = Vue.computed(function () {
                var counts = {};
                var order = [];
                rows.value.forEach(function (row) {
                    var name = row.providerName || '未命名供应商';
                    if (!(name in counts)) order.push(name);
                    counts[name] = (counts[name] || 0) + 1;
                });
                var total = Math.max(1, rows.value.length);
                return order.map(function (name) {
                    return { name: name, count: counts[name], pct: Math.round(counts[name] / total * 100) };
                });
            });
            function fmtTokens(value) {
                if (value === null || value === undefined || value === '') return '未配置';
                var num = Number(value);
                return isFinite(num) ? num.toLocaleString('en-US') + ' tokens' : String(value);
            }
            function resetFilters() {
                keyword.value = '';
                modelType.value = '';
                load();
            }
            function onFilterModels(event) {
                keyword.value = (event && event.detail) || '';
                load();
            }
            Vue.onMounted(function () { window.addEventListener('ax-ai-filter-models', onFilterModels); });
            Vue.onUnmounted(function () { window.removeEventListener('ax-ai-filter-models', onFilterModels); });

            function load() {
                var seq = ++loadSeq;
                loading.value = true;
                var params = [];
                if (keyword.value) params.push('keyword=' + encodeURIComponent(keyword.value));
                if (modelType.value) params.push('modelType=' + modelType.value);
                api.get('/api/admin/ai/models' + (params.length ? '?' + params.join('&') : ''))
                    .then(function (list) { if (seq === loadSeq) rows.value = list || []; })
                    .catch(function (error) { if (seq === loadSeq) El.ElMessage.error(error.message); })
                    .finally(function () { if (seq === loadSeq) loading.value = false; });
            }

            function toggle(model, value) {
                api.put('/api/admin/ai/models/' + model.modelId, { enabled: value })
                    .then(function () { model.enabled = value; })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }

            function remove(model) {
                El.ElMessageBox.confirm('确定删除模型「' + model.providerModelId + '」？', '确认删除',
                        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
                    .then(function () { return api.delete('/api/admin/ai/models/' + model.modelId); })
                    .then(function () {
                        El.ElMessage.success('已删除');
                        load();
                    })
                    .catch(function (error) {
                        if (error !== 'cancel' && error !== 'close') {
                            El.ElMessage.error(error.message || '删除失败');
                        }
                    });
            }

            function blank(value) { return value === null || value === undefined || value === ''; }

            function openEdit(model) {
                editForm.modelId = model.modelId;
                editForm.providerModelId = model.providerModelId || '';
                editForm.modelType = model.modelType || 'CHAT';
                editForm.enabled = !!model.enabled;
                editForm.contextWindowTokens = model.contextWindowTokens == null ? null : model.contextWindowTokens;
                editForm.maxOutputTokens = model.maxOutputTokens == null ? null : model.maxOutputTokens;
                editVisible.value = true;
            }

            function saveEdit() {
                var isChat = editForm.modelType === 'CHAT';
                var contextWindow = isChat && !blank(editForm.contextWindowTokens) ? Number(editForm.contextWindowTokens) : null;
                var maxOutput = isChat && !blank(editForm.maxOutputTokens) ? Number(editForm.maxOutputTokens) : null;
                if (contextWindow !== null && (!isFinite(contextWindow) || Math.floor(contextWindow) !== contextWindow || contextWindow < 1)) {
                    El.ElMessage.warning('上下文窗口必须是不小于 1 的整数');
                    return;
                }
                if (maxOutput !== null && (!isFinite(maxOutput) || Math.floor(maxOutput) !== maxOutput || maxOutput < 1)) {
                    El.ElMessage.warning('最大输出必须是不小于 1 的整数');
                    return;
                }
                if (contextWindow !== null && maxOutput !== null && maxOutput >= contextWindow) {
                    El.ElMessage.warning('最大输出 tokens 必须小于上下文窗口 tokens');
                    return;
                }
                saving.value = true;
                api.put('/api/admin/ai/models/' + editForm.modelId, {
                    enabled: editForm.enabled,
                    modelType: editForm.modelType,
                    contextWindowTokens: contextWindow,
                    maxOutputTokens: maxOutput
                }).then(function () {
                    El.ElMessage.success('已保存');
                    editVisible.value = false;
                    load();
                }).catch(function (error) {
                    El.ElMessage.error(error.message);
                }).finally(function () {
                    saving.value = false;
                });
            }

            load();

            return {
                keyword: keyword, modelType: modelType, rows: rows, loading: loading,
                saving: saving, editVisible: editVisible, editForm: editForm,
                enabledCount: enabledCount, chatCount: chatCount, providerCount: providerCount,
                resetFilters: resetFilters, typeClass: typeClass, fmtTokens: fmtTokens,
                modelTypeStats: modelTypeStats, supplierStats: supplierStats,
                load: load, toggle: toggle, remove: remove, openEdit: openEdit, saveEdit: saveEdit,
                typeOptions: TYPE_OPTIONS, typeLabel: typeLabel,
                SearchIcon: ElementPlusIconsVue.Search,
                RefreshIcon: ElementPlusIconsVue.Refresh
            };
        }
    };

    /* ---------------- Tab3 默认模型 + 全局设置 ---------------- */
    window.AdminPages.AiDefaultsPage = {
        name: 'AiDefaultsPage',
        template: `
<div class="ax-pane ax-defaults">
  <div class="ax-default-grid">
    <section class="ax-panel">
      <div class="ax-panel-head">
        <div><b class="ax-panel-title">类型默认模型</b><span class="ax-panel-sub">按能力设置默认绑定；仅列出已启用供应商的模型</span></div>
        <span class="ax-status" :class="configuredDefaults === defaults.length ? 'ok' : 'warn'">{{ configuredDefaults }}/{{ defaults.length }} 已配置</span>
      </div>
      <div class="ax-default-list" v-loading="loading">
        <div class="ax-default-row" v-for="def in defaults" :key="def.typeCode">
          <span class="ax-def-ico" :class="defTone(def)">{{ defInitial(def) }}</span>
          <span class="ax-def-main"><b>{{ def.typeName }}</b><em>{{ def.model ? def.model.providerModelId : "未选择模型" }}</em></span>
          <span style="display:flex;flex-direction:column;gap:2px;min-width:0">
            <select class="ax-select" :value="def.model ? def.model.modelId : ''" :disabled="def.locked" :title="def.lockMessage || ''" @change="changeDefault(def, $event)">
              <option value="" disabled>选择模型</option>
              <option v-for="m in modelsFor(def)" :key="m.modelId" :value="m.modelId">{{ m.providerModelId }} · {{ m.providerName }}</option>
            </select>
            <span v-if="def.locked" style="font-size:9px;color:#B45309">已锁定</span>
          </span>
          <button class="ax-link" type="button" :disabled="!def.model || def.locked" @click="clearDefault(def)">清除</button>
          <div v-if="def.locked" style="grid-column:1 / -1;font-size:10px;line-height:1.6;color:#B45309;background:#FFF8EB;border:1px solid #FDE7BF;border-radius:5px;padding:5px 8px;margin-bottom:6px">{{ def.lockMessage }}</div>
        </div>
        <div v-if="!defaults.length" class="ax-empty">暂无默认模型配置</div>
      </div>
      <div class="ax-panel-foot">
        <span class="ax-muted">当前生效语言模型</span>
        <b class="ax-mono">{{ activeChatModel || "未配置" }}</b>
      </div>
    </section>

    <section class="ax-panel">
      <div class="ax-panel-head">
        <div><b class="ax-panel-title">全局设置</b><span class="ax-panel-sub">总开关、超时与上下文预算；保存后即时生效</span></div>
        <span class="ax-status" :class="global.enabled ? 'ok' : 'mute'">{{ global.enabled ? "AI 已启用" : "AI 已停用" }}</span>
      </div>
      <div class="ax-form-body">
        <div class="ax-form-row switch-row">
          <span class="switch" :class="{ off: !global.enabled }" @click="global.enabled = !global.enabled"><i></i></span>
          <span class="ax-form-label">启用 AI 助手</span>
          <em>关闭后所有 AI 能力返回未启用</em>
        </div>
        <div class="ax-form-grid">
          <label class="ax-field"><span>连接超时（秒）</span><input type="number" v-model.number="global.connectTimeoutSeconds" min="1" max="120" /></label>
          <label class="ax-field"><span>读取超时（秒）</span><input type="number" v-model.number="global.readTimeoutSeconds" min="1" max="600" /></label>
          <label class="ax-field"><span>最大输入字符数</span><input type="number" v-model.number="global.maxInputCharacters" min="1" max="100000" step="1000" /></label>
          <label class="ax-field"><span>每小时消息上限</span><input type="number" v-model.number="global.hourlyMessageLimit" min="1" max="10000" /></label>
          <label class="ax-field"><span>携带历史条数</span><input type="number" v-model.number="global.historyWindow" min="0" max="100" /></label>
          <label class="ax-field"><span>单条消息上限字符</span><input type="number" v-model.number="global.maxMessageCharacters" min="1" max="100000" step="100" /></label>
        </div>

        <div class="ax-section-title">上下文策略 <span>按 token 预算分配历史 / 知识库 / 附件；模型级上下文窗口优先</span></div>
        <div class="ax-form-grid">
          <label class="ax-field"><span>最大上下文 tokens</span><input type="number" v-model.number="global.maxContextTokens" min="1000" max="1000000" step="1000" /></label>
          <label class="ax-field"><span>安全余量 tokens</span><input type="number" v-model.number="global.contextSafetyMarginTokens" min="0" max="100000" step="64" /></label>
          <label class="ax-field"><span>最大输出 tokens</span><input type="number" v-model.number="global.maxOutputTokens" min="1" max="100000" step="256" /></label>
          <label class="ax-field"><span>历史预算比例</span><input type="number" v-model.number="global.historyTokenRatio" min="0" max="1" step="0.05" /></label>
          <label class="ax-field"><span>知识库预算比例</span><input type="number" v-model.number="global.ragTokenRatio" min="0" max="1" step="0.05" /></label>
          <label class="ax-field"><span>附件预算比例</span><input type="number" v-model.number="global.attachmentTokenRatio" min="0" max="1" step="0.05" /></label>
          <label class="ax-field"><span>摘要触发比例</span><input type="number" v-model.number="global.summaryTriggerRatio" min="0" max="1" step="0.05" /></label>
          <label class="ax-field"><span>摘要保留最近轮数</span><input type="number" v-model.number="global.summaryKeepRecentTurns" min="0" max="50" /></label>
          <label class="ax-field"><span>估算系数（字符 / token）</span><input type="number" v-model.number="global.tokenEstimateCharsPerToken" min="0.1" max="10" step="0.1" /></label>
        </div>
        <div class="ax-form-row switch-row">
          <span class="switch" :class="{ off: !global.summaryEnabled }" @click="global.summaryEnabled = !global.summaryEnabled"><i></i></span>
          <span class="ax-form-label">启用会话摘要</span>
          <em>历史超过历史预算的 {{ Math.round((global.summaryTriggerRatio || 0) * 100) }}% 时异步生成摘要，摘要替代更早的对话轮次</em>
        </div>
      </div>
      <div class="ax-panel-foot">
        <span class="ax-muted">保存即时生效，无需重启</span>
        <button class="ax-btn primary" type="button" :disabled="saving" @click="saveGlobal">{{ saving ? "保存中…" : "保存设置" }}</button>
      </div>
    </section>
  </div>
</div>
        `,
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var defaults = Vue.ref([]);
            var enabledModels = Vue.ref([]);
            var enabledProviderIds = Vue.ref([]);
            var loading = Vue.ref(false);
            var saving = Vue.ref(false);
            var activeChatModel = Vue.ref(null);
            var configuredDefaults = Vue.computed(function () {
                return defaults.value.filter(function (d) { return !!d.model; }).length;
            });
            var DEFAULT_TONE = { CHAT: 'chat', VISION: 'vision', ASR: 'audio', TTS: 'audio', EMBEDDING: 'embed', RERANK: 'rerank' };
            function defTone(def) { return DEFAULT_TONE[def.typeCode] || 'chat'; }
            function defInitial(def) { return String(def.typeName || def.typeCode || '?').slice(0, 1); }
            function modelsFor(def) {
                return enabledModels.value.filter(function (m) { return m.modelType === def.typeCode; });
            }
            function changeDefault(def, event) { setDefault(def, event.target.value || null); }
            function clearDefault(def) { setDefault(def, null); }
            var global = Vue.reactive({
                enabled: false,
                connectTimeoutSeconds: 5,
                readTimeoutSeconds: 60,
                maxInputCharacters: 12000,
                hourlyMessageLimit: 20,
                historyWindow: 10,
                maxMessageCharacters: 2000,
                maxContextTokens: CONTEXT_DEFAULTS.maxContextTokens,
                contextSafetyMarginTokens: CONTEXT_DEFAULTS.contextSafetyMarginTokens,
                maxOutputTokens: CONTEXT_DEFAULTS.maxOutputTokens,
                historyTokenRatio: CONTEXT_DEFAULTS.historyTokenRatio,
                ragTokenRatio: CONTEXT_DEFAULTS.ragTokenRatio,
                attachmentTokenRatio: CONTEXT_DEFAULTS.attachmentTokenRatio,
                summaryEnabled: CONTEXT_DEFAULTS.summaryEnabled,
                summaryTriggerRatio: CONTEXT_DEFAULTS.summaryTriggerRatio,
                summaryKeepRecentTurns: CONTEXT_DEFAULTS.summaryKeepRecentTurns,
                tokenEstimateCharsPerToken: CONTEXT_DEFAULTS.tokenEstimateCharsPerToken
            });

            function contextValue(conf, key) {
                var value = conf ? conf[key] : null;
                if (value === null || value === undefined || value === '') {
                    return CONTEXT_DEFAULTS[key];
                }
                return value;
            }

            function load() {
                loading.value = true;
                Promise.all([
                    api.get('/api/admin/ai/defaults'),
                    api.get('/api/admin/ai/models'),
                    api.get('/api/admin/ai/config'),
                    api.get('/api/admin/ai/providers')
                ]).then(function (results) {
                    defaults.value = results[0] || [];
                    enabledProviderIds.value = (results[3] || [])
                        .filter(function (p) { return p.enabled; })
                        .map(function (p) { return String(p.providerId); });
                    enabledModels.value = (results[1] || []).filter(function (m) {
                        return m.enabled && enabledProviderIds.value.indexOf(String(m.providerId)) >= 0;
                    });
                    var conf = results[2];
                    global.enabled = conf.enabled;
                    global.connectTimeoutSeconds = conf.connectTimeoutSeconds;
                    global.readTimeoutSeconds = conf.readTimeoutSeconds;
                    global.maxInputCharacters = conf.maxInputCharacters;
                    global.hourlyMessageLimit = conf.hourlyMessageLimit;
                    global.historyWindow = conf.historyWindow;
                    global.maxMessageCharacters = conf.maxMessageCharacters;
                    global.maxContextTokens = contextValue(conf, 'maxContextTokens');
                    global.contextSafetyMarginTokens = contextValue(conf, 'contextSafetyMarginTokens');
                    global.maxOutputTokens = contextValue(conf, 'maxOutputTokens');
                    global.historyTokenRatio = contextValue(conf, 'historyTokenRatio');
                    global.ragTokenRatio = contextValue(conf, 'ragTokenRatio');
                    global.attachmentTokenRatio = contextValue(conf, 'attachmentTokenRatio');
                    global.summaryEnabled = conf && conf.summaryEnabled !== null && conf.summaryEnabled !== undefined
                        ? !!conf.summaryEnabled : CONTEXT_DEFAULTS.summaryEnabled;
                    global.summaryTriggerRatio = contextValue(conf, 'summaryTriggerRatio');
                    global.summaryKeepRecentTurns = contextValue(conf, 'summaryKeepRecentTurns');
                    global.tokenEstimateCharsPerToken = contextValue(conf, 'tokenEstimateCharsPerToken');
                    activeChatModel.value = conf.activeChatModel;
                }).catch(function (error) {
                    El.ElMessage.error(error.message);
                }).finally(function () {
                    loading.value = false;
                });
            }

            function setDefault(def, modelId) {
                api.put('/api/admin/ai/defaults', { typeCode: def.typeCode, modelId: modelId })
                    .then(function () {
                        El.ElMessage.success(modelId ? '已设为默认' : '已清除默认');
                        load();
                    })
                    .catch(function (error) {
                        El.ElMessage.error(error.message);
                        load();
                    });
            }

            function numberOr(value, fallback) {
                if (value === null || value === undefined || value === '') return fallback;
                var num = Number(value);
                return isFinite(num) ? num : fallback;
            }

            function saveGlobal() {
                var context = {};
                var invalid = null;
                INT_FIELDS.forEach(function (key) {
                    var num = numberOr(global[key], CONTEXT_DEFAULTS[key]);
                    if (Math.floor(num) !== num || num < 0) {
                        invalid = '「' + CONTEXT_FIELD_LABELS[key] + '」必须是不小于 0 的整数';
                    }
                    context[key] = Math.floor(num);
                });
                RATIO_FIELDS.forEach(function (key) {
                    var num = numberOr(global[key], CONTEXT_DEFAULTS[key]);
                    if (num < 0 || num > 1) {
                        invalid = '「' + CONTEXT_FIELD_LABELS[key] + '」必须在 0 ~ 1 之间';
                    }
                    context[key] = num;
                });
                var charsPerToken = numberOr(global.tokenEstimateCharsPerToken, CONTEXT_DEFAULTS.tokenEstimateCharsPerToken);
                if (!(charsPerToken > 0)) {
                    invalid = '「' + CONTEXT_FIELD_LABELS.tokenEstimateCharsPerToken + '」必须大于 0';
                }
                context.tokenEstimateCharsPerToken = charsPerToken;
                context.summaryEnabled = !!global.summaryEnabled;
                if (invalid) {
                    El.ElMessage.warning(invalid);
                    return;
                }
                saving.value = true;
                api.put('/api/admin/ai/config', {
                    enabled: global.enabled,
                    connectTimeoutSeconds: numberOr(global.connectTimeoutSeconds, 5),
                    readTimeoutSeconds: numberOr(global.readTimeoutSeconds, 60),
                    maxInputCharacters: numberOr(global.maxInputCharacters, 12000),
                    hourlyMessageLimit: numberOr(global.hourlyMessageLimit, 20),
                    historyWindow: numberOr(global.historyWindow, 10),
                    maxMessageCharacters: numberOr(global.maxMessageCharacters, 2000),
                    maxContextTokens: context.maxContextTokens,
                    contextSafetyMarginTokens: context.contextSafetyMarginTokens,
                    maxOutputTokens: context.maxOutputTokens,
                    historyTokenRatio: context.historyTokenRatio,
                    ragTokenRatio: context.ragTokenRatio,
                    attachmentTokenRatio: context.attachmentTokenRatio,
                    summaryEnabled: context.summaryEnabled,
                    summaryTriggerRatio: context.summaryTriggerRatio,
                    summaryKeepRecentTurns: context.summaryKeepRecentTurns,
                    tokenEstimateCharsPerToken: context.tokenEstimateCharsPerToken
                }).then(function (conf) {
                    activeChatModel.value = conf.activeChatModel;
                    El.ElMessage.success('已保存并即时生效');
                }).catch(function (error) {
                    El.ElMessage.error(error.message);
                }).finally(function () {
                    saving.value = false;
                });
            }

            load();

            return {
                defaults: defaults, enabledModels: enabledModels, loading: loading, saving: saving,
                global: global, activeChatModel: activeChatModel, configuredDefaults: configuredDefaults,
                defTone: defTone, defInitial: defInitial, modelsFor: modelsFor,
                changeDefault: changeDefault, clearDefault: clearDefault,
                setDefault: setDefault, saveGlobal: saveGlobal
            };
        }
    };

    /* ---------------- 外壳 Tab ---------------- */
    window.AdminPages.AiConfigPage = {
        name: 'AiConfigPage',
        template: [
            '<div class="pgw ax-page">',
            '  <div class="ax-tabs-row">',
            '    <div class="ax-tabs">',
            '      <button v-for="t in tabs" :key="t.key" type="button" class="ax-tab" :class="{ on: tab === t.key }" @click="tab = t.key">{{ t.label }}</button>',
            '    </div>',
            '    <span class="ax-tabs-meta">模型网关配置 · 保存即时生效</span>',
            '  </div>',
            '  <component :is="paneComponent" />',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var tab = Vue.ref('providers');
            var tabs = [
                { key: 'providers', label: '模型配置' },
                { key: 'models', label: '模型列表' },
                { key: 'defaults', label: '默认模型' },
                { key: 'test', label: '测试' }
            ];
            // 直接返回组件对象：这些子页只注册在 window.AdminPages，不是全局组件，
            // 传字符串会让 <component :is> 解析失败，整页只剩 Tab 空白。
            var paneMap = {
                providers: window.AdminPages.AiProvidersPage,
                models: window.AdminPages.AiModelsPage,
                defaults: window.AdminPages.AiDefaultsPage,
                test: window.AdminPages.AiTestPage
            };
            var paneComponent = Vue.computed(function () { return paneMap[tab.value]; });
            // 供应商页的「编辑模型 / 全部模型」跳转到模型列表并带入筛选词
            function openModels(term) {
                tab.value = 'models';
                Vue.nextTick(function () {
                    window.dispatchEvent(new CustomEvent('ax-ai-filter-models', { detail: term || '' }));
                });
            }
            Vue.provide('axOpenModels', openModels);
            return { tab: tab, tabs: tabs, paneComponent: paneComponent };
        }
    };
})();
