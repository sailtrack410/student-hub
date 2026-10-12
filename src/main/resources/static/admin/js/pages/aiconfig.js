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

    /* ---------------- Tab1 模型配置 ---------------- */
    window.AdminPages.AiProvidersPage = {
        name: 'AiProvidersPage',
        template: [
            '<div class="duo" style="flex:1;min-height:0">',
            '  <aside class="lcard" style="width:302px;flex:none;gap:12px;align-self:stretch">',
            '    <div class="cap" style="font-size:15px">供应商',
            '      <span class="r" style="gap:6px"><span style="font-size:12px;color:var(--tk-text3)">已接入 {{ providers.length }} 个</span>',
            '      <span class="op" style="display:inline-flex;align-items:center;justify-content:center;width:26px;height:26px;border-radius:8px;background:var(--tk-info-bg);cursor:pointer" @click="openCreate" title="新增供应商">',
            '        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--tk-info)" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
            '      </span></span>',
            '    </div>',
            '    <div style="display:flex;flex-direction:column;gap:6px;overflow:auto">',
            '      <div v-if="!providers.length" style="font-size:12px;color:var(--tk-muted);padding:12px 0;text-align:center">还没有供应商，点击右上角 + 新增</div>',
            '      <div v-for="p in providers" :key="p.providerId" class="node"',
            '           :class="{ on: p.providerId === selectedId }" style="align-items:flex-start;gap:10px" @click="select(p.providerId)">',
            '        <img v-if="p.icon" :src="providerIconUrl(p.icon)" width="22" height="22" style="border-radius:5px;flex:none;margin-top:1px" />',
            '        <span v-else style="width:6px;height:6px;border-radius:999px;margin-top:7px;flex:none;',
            '          background: p.enabled && p.apiKeyConfigured ? \'var(--tk-ok)\' : \'var(--tk-muted)\'"></span>',
            '        <span style="display:flex;flex-direction:column;gap:1px;min-width:0">',
            '          <b style="font-size:13px;color:var(--tk-text)">{{ p.providerName }}</b>',
            '          <span style="font-size:11px;color:var(--tk-text3)">{{ p.providerCode }} · {{ p.modelCount }} 个模型</span>',
            '        </span>',
            '      </div>',
            '    </div>',
            '  </aside>',

            '  <section style="flex:1;display:flex;flex-direction:column;gap:16px;min-width:0;min-height:0" v-loading="loading">',
            '    <template v-if="current">',
            '      <div class="lcard" style="gap:14px">',
            '        <div class="cap" style="font-size:15px">',
            '          <img v-if="current.icon" :src="providerIconUrl(current.icon)" width="38" height="38" style="border-radius:10px;background:var(--tk-info-bg);padding:4px;box-sizing:border-box" />',
            '          <span v-else style="width:38px;height:38px;border-radius:10px;background:var(--tk-info-bg);display:grid;place-items:center;font-size:15px;font-weight:650;color:var(--tk-info)">{{ (current.providerName || "?").slice(0, 1) }}</span>',
            '          <span style="display:flex;flex-direction:column"><b style="font-size:15px">{{ current.providerName }}</b><span style="font-size:11px;color:var(--tk-text3);font-weight:400">{{ current.providerCode }}</span></span>',
            '          <span class="r" style="gap:10px">',
            '            <span class="switch" :class="{off:!current.enabled}" @click="toggleEnabled(!current.enabled)"><i></i></span>',
            '            <button class="btn2 sm" style="height:30px" :disabled="checking" @click="check">{{ checking ? "检查中…" : "检查连通性" }}</button>',
            '            <button class="btn2 sm" style="height:30px" @click="openEdit">编辑</button>',
            '            <span class="op r" style="font-size:12px;cursor:pointer" @click="removeProvider">删除</span>',
            '          </span>',
            '        </div>',
            '        <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:12px">',
            '          <div><div style="font-size:12px;color:var(--tk-text3)">状态</div><div style="margin-top:4px"><span class="bdg" :class="current.enabled ? \'g\' : \'n2\'">{{ current.enabled ? \'启用中\' : \'已停用\' }}</span></div></div>',
            '          <div><div style="font-size:12px;color:var(--tk-text3)">上次检查</div><div style="font-size:14px;color:var(--tk-text);margin-top:4px">{{ fmt(current.lastCheckAt) || "尚未检查" }}</div></div>',
            '          <div><div style="font-size:12px;color:var(--tk-text3)">凭据状态</div><div style="font-size:14px;color:var(--tk-text);margin-top:4px">{{ current.apiKeyConfigured ? "已配置" : "未配置" }}</div></div>',
            '          <div><div style="font-size:12px;color:var(--tk-text3)">代理</div><div style="font-size:14px;color:var(--tk-muted);margin-top:4px">未配置</div></div>',
            '        </div>',
            '        <div style="display:flex;gap:12px;font-size:12px"><span style="color:var(--tk-text3);width:90px;flex:none">Base URL</span><span style="color:var(--tk-text2)">{{ current.baseUrl }}</span></div>',
            '        <div style="display:flex;gap:12px;font-size:12px;align-items:center"><span style="color:var(--tk-text3);width:90px;flex:none">状态信息</span>',
            '          <span v-if="current.lastCheckResult" class="bdg" :class="current.lastCheckOk ? \'g\' : \'r\'">{{ current.lastCheckResult }}</span>',
            '          <span v-else style="color:var(--tk-muted)">尚未检查</span></div>',
            '      </div>',

            '      <div class="lcard" style="gap:12px;flex:1;min-height:0">',
            '        <div class="cap" style="font-size:15px">模型列表<span class="cnt">当前供应商下共 {{ providerModels.length }} 个模型</span>',
            '          <span class="r" style="gap:8px">',
            '            <button class="btn2 sm pri" style="height:30px" @click="addCommand(\'fetch\')">+ 获取模型</button>',
            '            <button class="btn2 sm" style="height:30px" @click="addCommand(\'manual\')">手动添加</button>',
            '          </span>',
            '        </div>',
            '        <div v-if="!providerModels.length" style="font-size:12px;color:var(--tk-muted);padding:16px 0">还没有模型，点击「获取模型」从供应商拉取或手动添加</div>',
            '        <div style="display:flex;flex-direction:column;gap:2px;overflow:auto">',
            '          <div v-for="m in providerModels" :key="m.modelId" style="display:flex;align-items:center;gap:10px;padding:10px 12px;border-radius:8px">',
            '            <span style="display:flex;flex-direction:column;gap:2px;flex:1;min-width:0">',
            '              <b style="font-size:13px;font-weight:500;color:var(--tk-text)">{{ m.providerModelId }}</b>',
            '              <span style="font-size:11px;color:var(--tk-text3)">供应商模型 ID: {{ m.providerModelId }} · {{ capLabels(m.capabilities) || typeLabel(m.modelType) }}</span>',
            '            </span>',
            '            <span class="bdg plain" :class="m.modelType === \'VISION\' ? \'\' : \'\'">{{ typeLabel(m.modelType) }}</span>',
            '            <span class="switch" :class="{off:!m.enabled}" @click="toggleModel(m, !m.enabled)"><i></i></span>',
            '            <span class="op r" style="font-size:12px;cursor:pointer;padding:0 4px" @click="removeModel(m)">删除</span>',
            '          </div>',
            '        </div>',
            '      </div>',
            '    </template>',
            '    <div v-else-if="!loading" class="lcard" style="align-items:center;justify-content:center;min-height:200px;color:var(--tk-muted)">选择或新增一个供应商</div>',
            '  </section>',

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
            '            <div class="pin" style="width:130px;height:36px"><select :value="remoteType[m.id] || \'CHAT\'" @change="function (e) { remoteType[m.id] = e.target.value }" style="width:100%">',
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
            var allModels = Vue.ref([]);
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

            var providerModels = Vue.computed(function () {
                var id = selectedId.value;
                return allModels.value.filter(function (m) { return m.providerId === id; });
            });

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
                        selectedId.value = providers.value[0].providerId;
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
                    .catch(function (error) {
                        El.ElMessage.error(error.message);
                        current.value.enabled = !value;
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
                            remoteModels.value = (ids || []).map(function (id) { return { id: id }; });
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
                current: current, providerModels: providerModels,
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
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="toolbar">',
            '    <div class="srch"><input v-model.trim="keyword" placeholder="输入关键词搜索" @keyup.enter="load" /></div>',
            '    <div class="fchip"><select v-model="modelType" @change="load" style="border:0;background:none;font:inherit;color:inherit;outline:none">',
            '      <option value="">类型：全部</option>',
            '      <option v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</option></select></div>',
            '    <button class="btn2 pri" @click="load">搜索</button>',
            '    <span style="flex:1"></span>',
            '    <span style="font-size:12px;color:var(--tk-muted)">共 {{ rows.length }} 个模型</span>',
            '    <button class="btn2" @click="load">刷新</button>',
            '  </div>',
            '  <div class="lcard" style="flex:1">',
            '    <div class="cap">模型汇总<span class="cnt">跨供应商全部模型</span></div>',
            '    <div class="tblw">',
            '      <table class="pk">',
            '        <colgroup><col><col style="width:150px"><col style="width:120px"><col style="width:90px"><col style="width:90px"></colgroup>',
            '        <thead><tr><th>模型</th><th>供应商</th><th>类型</th><th>启用</th><th class="r">操作</th></tr></thead>',
            '        <tbody>',
            '          <tr v-if="!rows.length && !loading"><td colspan="5" style="text-align:center;color:var(--tk-muted);padding:32px 0">暂无模型</td></tr>',
            '          <tr v-if="loading"><td colspan="5" style="text-align:center;color:var(--tk-muted);padding:32px 0">加载中…</td></tr>',
            '          <tr v-for="row in rows" :key="row.modelId">',
            '            <td style="color:var(--tk-text);font-weight:500">{{ row.providerModelId }}</td>',
            '            <td><span class="bdg plain">{{ row.providerName }}</span></td>',
            '            <td><span class="bdg i" v-if="row.modelType !== \'VISION\'">{{ typeLabel(row.modelType) }}</span><span class="bdg a" v-else>{{ typeLabel(row.modelType) }}</span></td>',
            '            <td><span class="switch" :class="{off:!row.enabled}" @click="toggle(row, !row.enabled)"><i></i></span></td>',
            '            <td class="r"><span class="op r" @click="remove(row)">删除</span></td>',
            '          </tr>',
            '        </tbody>',
            '      </table>',
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

            function load() {
                loading.value = true;
                var params = [];
                if (keyword.value) params.push('keyword=' + encodeURIComponent(keyword.value));
                if (modelType.value) params.push('modelType=' + modelType.value);
                api.get('/api/admin/ai/models' + (params.length ? '?' + params.join('&') : ''))
                    .then(function (list) { rows.value = list || []; })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { loading.value = false; });
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

            load();

            return {
                keyword: keyword, modelType: modelType, rows: rows, loading: loading,
                load: load, toggle: toggle, remove: remove,
                typeOptions: TYPE_OPTIONS, typeLabel: typeLabel,
                SearchIcon: ElementPlusIconsVue.Search,
                RefreshIcon: ElementPlusIconsVue.Refresh
            };
        }
    };

    /* ---------------- Tab3 默认模型 + 全局设置 ---------------- */
    window.AdminPages.AiDefaultsPage = {
        name: 'AiDefaultsPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="lcard" style="gap:14px;max-width:860px">',
            '    <div class="cap" style="font-size:15px">默认模型</div>',
            '    <div style="font-size:12px;color:var(--tk-text3)">每个类型设置一个默认模型；AI 助手使用「语言模型」的默认项，未设置时提示未配置。</div>',
            '    <div v-for="def in defaults" :key="def.typeCode" style="display:grid;grid-template-columns:220px 1fr 80px;gap:12px;align-items:center;background:var(--tk-subtle);border-radius:10px;padding:12px 16px" v-loading="loading">',
            '      <span style="display:flex;flex-direction:column;gap:2px">',
            '        <b style="font-size:13px;font-weight:600;color:var(--tk-text)">{{ def.typeName }}</b>',
            '        <span style="font-size:11px;color:var(--tk-text3)">{{ def.model ? def.model.providerModelId : "未设置" }}</span>',
            '      </span>',
            '      <div class="ipt2"><select :value="def.model ? def.model.modelId : null" @change="function (e) { setDefault(def, e.target.value || null) }" style="width:100%">',
            '        <option :value="null" disabled>选择模型</option>',
            '        <option v-for="m in enabledModels" :key="m.modelId" :value="m.modelId">{{ m.providerModelId }}（{{ m.providerName }}）</option>',
            '      </select></div>',
            '      <button class="btn2 sm" style="height:32px" :disabled="!def.model" @click="setDefault(def, null)">清除</button>',
            '    </div>',
            '  </div>',
            '  <div class="lcard" style="gap:14px;max-width:860px">',
            '    <div class="cap" style="font-size:15px">全局设置</div>',
            '    <div style="display:flex;align-items:center;gap:12px"><span class="switch" :class="{off:!global.enabled}" @click="global.enabled=!global.enabled"><i></i></span><span style="font-size:14px;color:var(--tk-text2)">启用 AI 助手</span></div>',
            '    <div class="frow" style="grid-template-columns:repeat(3,1fr);gap:12px">',
            '      <div class="fld"><label>连接超时（秒）</label><div class="ipt2"><input type="number" v-model.number="global.connectTimeoutSeconds" min="1" max="120" /></div></div>',
            '      <div class="fld"><label>读取超时（秒）</label><div class="ipt2"><input type="number" v-model.number="global.readTimeoutSeconds" min="1" max="600" /></div></div>',
            '      <div class="fld"><label>最大输入字符数</label><div class="ipt2"><input type="number" v-model.number="global.maxInputCharacters" min="1" max="100000" step="1000" /></div></div>',
            '      <div class="fld"><label>每小时消息上限</label><div class="ipt2"><input type="number" v-model.number="global.hourlyMessageLimit" min="1" max="10000" /></div></div>',
            '      <div class="fld"><label>携带历史条数</label><div class="ipt2"><input type="number" v-model.number="global.historyWindow" min="0" max="100" /></div></div>',
            '      <div class="fld"><label>单条消息上限字符</label><div class="ipt2"><input type="number" v-model.number="global.maxMessageCharacters" min="1" max="100000" step="100" /></div></div>',
            '    </div>',
            '    <div style="display:flex;align-items:center;gap:12px">',
            '      <button class="btn2 pri" :disabled="saving" @click="saveGlobal">{{ saving ? "保存中…" : "保存设置" }}</button>',
            '      <span style="font-size:12px;color:var(--tk-muted)">所有保存即时生效，无需重启。聊天当前生效模型：{{ activeChatModel || "未配置" }}</span>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var defaults = Vue.ref([]);
            var enabledModels = Vue.ref([]);
            var loading = Vue.ref(false);
            var saving = Vue.ref(false);
            var activeChatModel = Vue.ref(null);
            var global = Vue.reactive({
                enabled: false,
                connectTimeoutSeconds: 5,
                readTimeoutSeconds: 60,
                maxInputCharacters: 12000,
                hourlyMessageLimit: 20,
                historyWindow: 10,
                maxMessageCharacters: 2000
            });

            function load() {
                loading.value = true;
                Promise.all([
                    api.get('/api/admin/ai/defaults'),
                    api.get('/api/admin/ai/models'),
                    api.get('/api/admin/ai/config')
                ]).then(function (results) {
                    defaults.value = results[0] || [];
                    enabledModels.value = (results[1] || []).filter(function (m) { return m.enabled; });
                    var conf = results[2];
                    global.enabled = conf.enabled;
                    global.connectTimeoutSeconds = conf.connectTimeoutSeconds;
                    global.readTimeoutSeconds = conf.readTimeoutSeconds;
                    global.maxInputCharacters = conf.maxInputCharacters;
                    global.hourlyMessageLimit = conf.hourlyMessageLimit;
                    global.historyWindow = conf.historyWindow;
                    global.maxMessageCharacters = conf.maxMessageCharacters;
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

            function saveGlobal() {
                saving.value = true;
                api.put('/api/admin/ai/config', {
                    enabled: global.enabled,
                    connectTimeoutSeconds: global.connectTimeoutSeconds,
                    readTimeoutSeconds: global.readTimeoutSeconds,
                    maxInputCharacters: global.maxInputCharacters,
                    hourlyMessageLimit: global.hourlyMessageLimit,
                    historyWindow: global.historyWindow,
                    maxMessageCharacters: global.maxMessageCharacters
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
                global: global, activeChatModel: activeChatModel,
                setDefault: setDefault, saveGlobal: saveGlobal
            };
        }
    };

    /* ---------------- 外壳 Tab ---------------- */
    window.AdminPages.AiConfigPage = {
        name: 'AiConfigPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="ptabs">',
            '    <span v-for="t in tabs" :key="t.key" class="ptab" :class="{ on: tab === t.key }" @click="tab = t.key">{{ t.label }}</span>',
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
            var paneComponent = Vue.computed(function () {
                return { providers: 'AiProvidersPage', models: 'AiModelsPage', defaults: 'AiDefaultsPage', test: 'AiTestPage' }[tab.value];
            });
            return { tab: tab, tabs: tabs, paneComponent: paneComponent };
        }
    };
})();
