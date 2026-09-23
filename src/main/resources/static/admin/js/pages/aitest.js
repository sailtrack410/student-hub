/* AI 测试页签：流式对话验证面。
   调 /api/admin/ai/chat-stream（SSE），验证协议分流与逐 token 流式输出。
   事件约定：delta=增量文本；done=结束（JSON：model/promptTokens/completionTokens）；error=失败原因。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    window.AdminPages.AiTestPage = {
        name: 'AiTestPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="lcard" style="flex:1;min-height:0;gap:12px">',
            '    <div class="cap" style="font-size:15px">流式对话测试<span class="cnt">验证协议分流与 SSE 逐 token 输出 · 不选则走默认语言模型</span></div>',
            '    <div style="display:flex;gap:8px">',
            '      <div class="pin" style="flex:1.2"><select v-model="providerId" @change="onProviderChange" style="width:100%">',
            '        <option :value="null">默认语言模型</option>',
            '        <option v-for="p in providers" :key="p.providerId" :value="p.providerId">{{ p.providerName }}（{{ p.protocol || \'OPENAI_CHAT\' }}）</option>',
            '      </select></div>',
            '      <div class="pin" style="flex:1"><select v-model="modelId" :disabled="!providerId" style="width:100%">',
            '        <option value="">该供应商默认（第一个启用模型）</option>',
            '        <option v-for="m in providerModels" :key="m.modelId" :value="m.providerModelId">{{ m.providerModelId }}</option>',
            '      </select></div>',
            '      <div class="pin" style="flex:1.2"><select v-model="knowledgeBaseId" style="width:100%">',
            '        <option :value="null">不联动知识库</option>',
            '        <option v-for="k in knowledgeBases" :key="k.id" :value="k.id">{{ k.name }}（Top {{ k.topK || 5 }}）</option>',
            '      </select></div>',
            '    </div>',
            '    <div ref="box" style="flex:1;min-height:0;overflow:auto;border:1px solid var(--tk-stroke);border-radius:10px;padding:14px;display:flex;flex-direction:column;gap:10px">',
            '      <div v-if="!messages.length" style="margin:auto;text-align:center;color:var(--tk-muted)">',
            '        <div style="font-size:14px;margin-bottom:6px">发送一条消息开始测试</div>',
            '        <div style="font-size:12px">流式开启：逐 token 显示 · 流式关闭：整段返回</div>',
            '      </div>',
            '      <div v-for="(m, i) in messages" :key="i" :style="m.role === \'user\'',
            '        ? \'align-self:flex-end;max-width:75%;background:var(--tk-brand);color:#fff;border-radius:12px 4px 12px 12px;padding:10px 14px;font-size:13px;white-space:pre-wrap\'',
            '        : \'align-self:flex-start;max-width:85%;background:var(--tk-subtle);color:var(--tk-text2);border-radius:4px 12px 12px 12px;padding:10px 14px;font-size:13px\'">',
            '        <md-view v-if="m.role === \'ai\'" :source="m.text" :streaming="m.streaming" />',
            '        <template v-else>{{ m.text }}</template>',
            '        <span v-if="m.streaming" style="color:var(--tk-info)">▍</span>',
            '        <div v-if="m.role === \'ai\' && m.sources && m.sources.length" style="margin-top:8px;padding-top:8px;border-top:1px dashed var(--tk-stroke)">',
            '          <div style="font-size:11px;color:var(--tk-muted);margin-bottom:4px">引用片段（{{ m.sources.length }}）</div>',
            '          <div v-for="(s, si) in m.sources" :key="si" style="font-size:11px;color:var(--tk-text3);margin:3px 0;line-height:1.5">',
            '            <span style="color:var(--tk-info)">[{{ si + 1 }}]</span> {{ s.fileName }}<template v-if="s.page"> 第 {{ s.page }} 页</template> · 相关度 {{ (s.score * 100).toFixed(0) }}% — {{ s.snippet }}',
            '          </div>',
            '        </div>',
            '      </div>',
            '    </div>',
            '    <div v-if="meta" style="font-size:12px;color:var(--tk-muted)">{{ meta }}</div>',
            '    <div v-if="errorText" style="font-size:13px;color:var(--tk-bad)">{{ errorText }}</div>',
            '    <div style="display:flex;gap:10px;align-items:center;height:44px;padding:0 14px;border:1px solid var(--tk-stroke);border-radius:10px;background:var(--tk-input)">',
            '      <input v-model="input" :disabled="running" @keyup.enter="send"',
            '             style="flex:1;border:0;outline:0;background:none;font:inherit;font-size:13px;color:var(--tk-text)"',
            '             placeholder="输入测试内容，Enter 发送" />',
            '      <button class="btn2 sm pri" style="height:30px" :disabled="running" @click="send">{{ running ? "生成中…" : "发送" }}</button>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
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

            var providerModels = Vue.computed(function () {
                if (!providerId.value) return [];
                return allModels.value.filter(function (m) {
                    return m.providerId === providerId.value && m.enabled;
                });
            });
            function onProviderChange() { modelId.value = ''; }

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
                            try {
                                var d = JSON.parse(data);
                                meta.value = '模型 ' + (d.model || '-') + ' · 输入 ' + (d.promptTokens ?? '-') + ' tokens · 输出 ' + (d.completionTokens ?? '-') + ' tokens';
                            } catch (ignored) { meta.value = '已完成'; }
                        } else if (eventName === 'sources') {
                            // 知识库召回片段：挂到当前 AI 气泡下方展示
                            try { aiMsg.sources = JSON.parse(data); } catch (ignored) { }
                        } else if (eventName === 'error') {
                            errorText.value = data;
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
                    errorText.value = error.message || '请求失败';
                    aiMsg.streaming = false;
                    running.value = false;
                });
            }

            loadTargets();

            return { messages: messages, input: input, running: running, meta: meta, errorText: errorText, box: box, send: send,
                providers: providers, providerId: providerId, modelId: modelId, providerModels: providerModels, onProviderChange: onProviderChange,
                knowledgeBases: knowledgeBases, knowledgeBaseId: knowledgeBaseId };
        }
    };
})();
