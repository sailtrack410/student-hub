/* AI 配置页：读取当前生效配置并保存；保存即进程内生效，API Key 留空保持原值。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    window.AdminPages.AiConfigPage = {
        name: 'AiConfigPage',
        template: [
            '<div class="page-card" style="max-width:860px">',
            '  <el-alert v-if="source" :title="\'配置来源：\' + source + (apiKeyConfigured ? \'，API Key 已配置\' : \'，API Key 未配置\')"',
            '            :type="enabled ? \'success\' : \'info\'" show-icon :closable="false" style="margin-bottom:16px" />',
            '  <el-form label-width="150px" v-loading="loading">',
            '    <el-form-item label="启用 AI 助手">',
            '      <el-switch v-model="form.enabled" />',
            '    </el-form-item>',
            '    <el-form-item label="供应商">',
            '      <el-input v-model.trim="form.provider" maxlength="32" placeholder="如 deepseek / siliconflow" style="width:320px" />',
            '    </el-form-item>',
            '    <el-form-item label="服务地址">',
            '      <el-input v-model.trim="form.baseUrl" placeholder="https://api.deepseek.com" style="width:420px" />',
            '    </el-form-item>',
            '    <el-form-item label="API Key">',
            '      <el-input v-model="apiKeyInput" type="password" show-password placeholder="留空表示保持原值" style="width:420px" />',
            '    </el-form-item>',
            '    <el-form-item label="模型名">',
            '      <el-input v-model.trim="form.model" placeholder="deepseek-chat" style="width:320px" />',
            '    </el-form-item>',
            '    <div class="form-grid">',
            '      <el-form-item label="连接超时（秒）">',
            '        <el-input-number v-model="form.connectTimeoutSeconds" :min="1" :max="120" />',
            '      </el-form-item>',
            '      <el-form-item label="读取超时（秒）">',
            '        <el-input-number v-model="form.readTimeoutSeconds" :min="1" :max="600" />',
            '      </el-form-item>',
            '      <el-form-item label="最大输入字符数">',
            '        <el-input-number v-model="form.maxInputCharacters" :min="1" :max="100000" :step="1000" />',
            '      </el-form-item>',
            '      <el-form-item label="每小时消息上限">',
            '        <el-input-number v-model="form.hourlyMessageLimit" :min="1" :max="10000" />',
            '      </el-form-item>',
            '      <el-form-item label="携带历史条数">',
            '        <el-input-number v-model="form.historyWindow" :min="0" :max="100" />',
            '      </el-form-item>',
            '      <el-form-item label="单条消息上限字符">',
            '        <el-input-number v-model="form.maxMessageCharacters" :min="1" :max="100000" :step="100" />',
            '      </el-form-item>',
            '    </div>',
            '    <el-form-item>',
            '      <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>',
            '      <el-button @click="load">重置</el-button>',
            '    </el-form-item>',
            '  </el-form>',
            '  <p class="form-hint">保存后立即生效，无需重启服务。API Key 仅在填写时提交，留空则沿用已保存的密钥。</p>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var form = Vue.reactive({
                enabled: false,
                provider: '',
                baseUrl: '',
                model: '',
                connectTimeoutSeconds: 5,
                readTimeoutSeconds: 60,
                maxInputCharacters: 12000,
                hourlyMessageLimit: 20,
                historyWindow: 10,
                maxMessageCharacters: 2000
            });
            var apiKeyInput = Vue.ref('');
            var source = Vue.ref('');
            var apiKeyConfigured = Vue.ref(false);
            var loading = Vue.ref(false);
            var saving = Vue.ref(false);

            function apply(data) {
                form.enabled = data.enabled;
                form.provider = data.provider || '';
                form.baseUrl = data.baseUrl || '';
                form.model = data.model || '';
                form.connectTimeoutSeconds = data.connectTimeoutSeconds;
                form.readTimeoutSeconds = data.readTimeoutSeconds;
                form.maxInputCharacters = data.maxInputCharacters;
                form.hourlyMessageLimit = data.hourlyMessageLimit;
                form.historyWindow = data.historyWindow;
                form.maxMessageCharacters = data.maxMessageCharacters;
                source.value = data.source || '';
                apiKeyConfigured.value = data.apiKeyConfigured;
                apiKeyInput.value = '';
            }

            function load() {
                loading.value = true;
                api.get('/api/admin/ai-config')
                    .then(apply)
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { loading.value = false; });
            }

            function save() {
                if (!form.provider) { El.ElMessage.warning('请填写供应商'); return; }
                if (!form.baseUrl) { El.ElMessage.warning('请填写服务地址'); return; }
                if (!form.model) { El.ElMessage.warning('请填写模型名'); return; }
                saving.value = true;
                api.put('/api/admin/ai-config', {
                    enabled: form.enabled,
                    provider: form.provider,
                    baseUrl: form.baseUrl,
                    apiKey: apiKeyInput.value || null,
                    model: form.model,
                    connectTimeoutSeconds: form.connectTimeoutSeconds,
                    readTimeoutSeconds: form.readTimeoutSeconds,
                    maxInputCharacters: form.maxInputCharacters,
                    hourlyMessageLimit: form.hourlyMessageLimit,
                    historyWindow: form.historyWindow,
                    maxMessageCharacters: form.maxMessageCharacters
                }).then(function (data) {
                    apply(data);
                    El.ElMessage.success('已保存并即时生效');
                }).catch(function (error) {
                    El.ElMessage.error(error.message);
                }).finally(function () {
                    saving.value = false;
                });
            }

            load();

            return {
                form: form, apiKeyInput: apiKeyInput, source: source,
                apiKeyConfigured: apiKeyConfigured, loading: loading, saving: saving,
                load: load, save: save
            };
        }
    };
})();
