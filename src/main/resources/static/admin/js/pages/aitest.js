/* AI 测试页签：流式对话验证面。
   调 /api/admin/ai/chat-stream（SSE），验证协议分流与逐 token 流式输出。
   事件约定：delta=增量文本；done=结束（JSON：model/promptTokens/completionTokens）；error=失败原因。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    window.AdminPages.AiTestPage = {
        name: 'AiTestPage',
        template: `
<div class="ax-pane ax-test">
  <div class="ax-test-grid">
    <section class="ax-panel ax-chat">
      <div class="ax-panel-head">
        <div><b class="ax-panel-title">流式对话</b><span class="ax-panel-sub">验证协议分流与 SSE 逐 token 输出</span></div>
        <div class="ax-hero-actions">
          <span class="ax-status" :class="running ? 'warn' : 'ok'">{{ running ? "生成中" : "就绪" }}</span>
          <button class="ax-btn" type="button" :disabled="running" @click="clearChat">清空</button>
        </div>
      </div>
      <div ref="box" class="ax-chat-log">
        <div v-if="!messages.length" class="ax-chat-empty">
          <span class="ax-chat-empty-ico">AI</span>
          <b>发送一条消息开始测试</b>
          <em>流式开启时逐 token 显示；不选供应商时走默认语言模型</em>
        </div>
        <div v-for="(m, i) in messages" :key="i" class="ax-msg" :class="m.role === 'user' ? 'user' : 'ai'">
          <span v-if="m.role === 'ai'" class="ax-msg-role">AI</span>
          <div class="ax-msg-body">
            <md-view v-if="m.role === 'ai'" :source="m.text" :streaming="m.streaming" />
            <template v-else>{{ m.text }}</template>
            <span v-if="m.streaming" class="ax-cursor">▍</span>
            <div v-if="m.role === 'ai' && m.sources && m.sources.length" class="ax-sources">
              <div class="ax-sources-h">引用片段 {{ m.sources.length }}</div>
              <div v-for="(s, si) in m.sources" :key="si" class="ax-source">
                <span class="ax-source-n">[{{ si + 1 }}]</span>{{ s.fileName }}<template v-if="s.page"> 第 {{ s.page }} 页</template>
                <em>相关度 {{ (s.score * 100).toFixed(0) }}% · {{ s.snippet }}</em>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-if="errorText" class="ax-error">{{ errorText }}</div>
      <div class="ax-composer">
        <input v-model="input" :disabled="running" @keyup.enter="send" placeholder="输入测试内容，Enter 发送" />
        <button class="ax-btn primary" type="button" :disabled="running" @click="send">{{ running ? "生成中…" : "发送" }}</button>
      </div>
      <div class="ax-chat-foot">
        <span>SSE · delta / done / error 事件</span>
        <span v-if="meta">{{ meta }}</span>
        <span v-else>当前会话 {{ messages.length }} 条消息</span>
      </div>
    </section>

    <aside class="ax-test-side">
      <section class="ax-panel">
        <div class="ax-panel-head"><div><b class="ax-panel-title">测试目标</b></div></div>
        <div class="ax-side-body">
          <label class="ax-field"><span>供应商</span>
            <select v-model="providerId" @change="onProviderChange">
              <option :value="null">默认语言模型</option>
              <option v-for="p in providers" :key="p.providerId" :value="p.providerId">{{ p.providerName }}{{ p.enabled ? "" : "（停用）" }}</option>
            </select>
          </label>
          <label class="ax-field"><span>模型</span>
            <select v-model="modelId" :disabled="!providerId">
              <option value="">该供应商默认（首个启用模型）</option>
              <option v-for="m in providerModels" :key="m.modelId" :value="m.providerModelId">{{ m.providerModelId }}</option>
            </select>
          </label>
          <label class="ax-field"><span>知识库联动</span>
            <select v-model="knowledgeBaseId">
              <option :value="null">不联动知识库</option>
              <option v-for="k in knowledgeBases" :key="k.id" :value="k.id">{{ k.name }}（Top {{ k.topK || 5 }}）</option>
            </select>
          </label>
          <div class="ax-hint">指定供应商和模型可验证协议分流；联动知识库会先检索再生成，并返回引用片段。</div>
        </div>
      </section>

      <section class="ax-panel">
        <div class="ax-panel-head"><div><b class="ax-panel-title">运行信息</b></div></div>
        <div class="ax-run-list">
          <div><span>协议</span><b>{{ selectedProtocol }}</b></div>
          <div><span>模型</span><b>{{ modelId || "供应商默认" }}</b></div>
          <div><span>知识库</span><b>{{ selectedKnowledgeName }}</b></div>
          <div><span>最近用量</span><b>{{ meta || "—" }}</b></div>
        </div>
      </section>

      <section class="ax-panel ax-quick-panel">
        <div class="ax-panel-head"><div><b class="ax-panel-title">快捷测试</b></div></div>
        <div class="ax-quick">
          <button v-for="p in quickPrompts" :key="p" type="button" @click="quick(p)">{{ p }}</button>
        </div>
      </section>
    </aside>
  </div>
</div>
        `,
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var messages = Vue.reactive([]);
            var input = Vue.ref('');
            var running = Vue.ref(false);
            var meta = Vue.ref('');
            var errorText = Vue.ref('');
            var box = Vue.ref(null);
            var providers = Vue.ref([]);
            var allModels = Vue.ref([]);
            var providerId = Vue.ref(null);
            var modelId = Vue.ref('');
            var knowledgeBases = Vue.ref([]);
            var knowledgeBaseId = Vue.ref(null);
            var quickPrompts = ['只回复：OK', '用一句话解释什么是 SSE 流式输出', '列出当前可用的语言模型'];

            var providerModels = Vue.computed(function () {
                if (!providerId.value) return [];
                return allModels.value.filter(function (m) {
                    return m.providerId === providerId.value && m.enabled;
                });
            });
            function onProviderChange() { modelId.value = ''; }
            var selectedProvider = Vue.computed(function () {
                var pid = providerId.value;
                if (!pid) return null;
                return providers.value.find(function (p) { return String(p.providerId) === String(pid); }) || null;
            });
            var selectedProtocol = Vue.computed(function () {
                return selectedProvider.value ? (selectedProvider.value.protocol || 'OPENAI_CHAT') : '默认模型路由';
            });
            var selectedKnowledgeName = Vue.computed(function () {
                if (!knowledgeBaseId.value) return '不联动';
                var base = knowledgeBases.value.find(function (k) { return String(k.id) === String(knowledgeBaseId.value); });
                return base ? base.name : '已选择';
            });
            function clearChat() {
                if (running.value) return;
                messages.splice(0, messages.length);
                meta.value = '';
                errorText.value = '';
            }
            function quick(text) {
                if (running.value) return;
                input.value = text;
                send();
            }

            function loadTargets() {
                api.get('/api/admin/ai/providers').then(function (list) { providers.value = list || []; })
                    .catch(function () { });
                api.get('/api/admin/ai/models').then(function (list) { allModels.value = list || []; })
                    .catch(function () { });
                api.get('/api/admin/knowledge-bases').then(function (list) { knowledgeBases.value = list || []; })
                    .catch(function () { });
            }

            function scrollBottom() {
                Vue.nextTick(function () {
                    if (box.value) box.value.scrollTop = box.value.scrollHeight;
                });
            }

            function send() {
                var text = input.value.trim();
                if (!text || running.value) return;
                errorText.value = '';
                meta.value = '';
                messages.push({ role: 'user', text: text });
                var aiMsg = Vue.reactive({ role: 'ai', text: '', streaming: true });
                var finished = false;
                messages.push(aiMsg);
                input.value = '';
                running.value = true;
                scrollBottom();

                // 组历史（user/ai 交替，最后一条是刚推的空 ai 消息需排除）
                var history = [];
                for (var i = 0; i < messages.length - 1; i++) {
                    history.push({ role: messages[i].role === 'user' ? 'user' : 'assistant', content: messages[i].text });
                }

                fetch('/api/admin/ai/chat-stream', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': 'Bearer ' + localStorage.getItem('studenthub_admin_token')
                    },
                    body: JSON.stringify({
                        messages: history,
                        providerId: providerId.value || null,
                        model: modelId.value || null,
                        knowledgeBaseId: knowledgeBaseId.value || null
                    })
                }).then(function (resp) {
                    if (!resp.ok || !resp.body) {
                        return resp.json().catch(function () { return null; }).then(function (payload) {
                            throw new Error((payload && payload.message) || 'HTTP ' + resp.status);
                        });
                    }
                    var reader = resp.body.getReader();
                    var decoder = new TextDecoder();
                    var buf = '';
                    var eventName = 'message';
                    var dataLines = [];
                    function dispatch() {
                        // SSE 规范：一个事件的多行 data 用 \n 拼回（markdown 换行不能丢）
                        var data = dataLines.join('\n');
                        if (eventName === 'delta' && data) {
                            aiMsg.text += data;
                            scrollBottom();
                        } else if (eventName === 'done') {
                            finished = true;
                            try {
                                var d = JSON.parse(data);
                                meta.value = '模型 ' + (d.model || '-') + ' · 输入 ' + (d.promptTokens ?? '-') + ' tokens · 输出 ' + (d.completionTokens ?? '-') + ' tokens';
                            } catch (ignored) { meta.value = '已完成'; }
                        } else if (eventName === 'sources') {
                            // 知识库召回片段：挂到当前 AI 气泡下方展示
                            try { aiMsg.sources = JSON.parse(data); } catch (ignored) { }
                        } else if (eventName === 'error') {
                            finished = true;
                            errorText.value = data || '生成失败';
                        }
                        eventName = 'message';
                        dataLines = [];
                    }
                    function pump() {
                        return reader.read().then(function (chunk) {
                            if (chunk.done) {
                                aiMsg.streaming = false;
                                running.value = false;
                                return;
                            }
                            buf += decoder.decode(chunk.value, { stream: true });
                            var lines = buf.split('\n');
                            buf = lines.pop();
                            lines.forEach(function (line) {
                                if (line.startsWith('event:')) {
                                    eventName = line.slice(6).trim();
                                } else if (line.startsWith('data:')) {
                                    // 按 SSE 规范剥掉一个前导空格，保留内部空格（markdown 缩进）
                                    var payload = line.slice(5);
                                    if (payload.startsWith(' ')) payload = payload.slice(1);
                                    dataLines.push(payload);
                                } else if (line === '' && (dataLines.length || eventName !== 'message')) {
                                    // 空行 = 事件结束
                                    dispatch();
                                }
                            });
                            scrollBottom();
                            return pump();
                        });
                    }
                    return pump();
                }).catch(function (error) {
                    // 部分代理会在一段完整 SSE 之后以网络错误关闭读流；done 已到达时不再提示错误。
                    if (!finished) {
                        errorText.value = error.message || '请求失败';
                    }
                    aiMsg.streaming = false;
                    running.value = false;
                });
            }

            loadTargets();

            return { messages: messages, input: input, running: running, meta: meta, errorText: errorText, box: box, send: send,
                providers: providers, providerId: providerId, modelId: modelId, providerModels: providerModels, onProviderChange: onProviderChange,
                selectedProvider: selectedProvider, selectedProtocol: selectedProtocol, selectedKnowledgeName: selectedKnowledgeName,
                clearChat: clearChat, quick: quick, quickPrompts: quickPrompts,
                knowledgeBases: knowledgeBases, knowledgeBaseId: knowledgeBaseId };
        }
    };
})();
