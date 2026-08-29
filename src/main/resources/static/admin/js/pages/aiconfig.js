/* AI 配置页：多供应商管理（Cherry Studio 式布局）。
   Tab1 模型配置：左侧供应商列表，右侧状态信息 + 模型列表；支持从供应商拉取模型批量导入。
   Tab2 模型列表：跨供应商全部模型，标注所属供应商。
   Tab3 默认模型：按类型（聊天/图片识别）设置默认模型 + AI 全局设置。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    var TYPE_LABEL = { CHAT: '语言模型', VISION: '图片识别', ASR: '语音识别' };
    var TYPE_OPTIONS = [
        { value: 'CHAT', label: '语言模型' },
        { value: 'VISION', label: '图片识别' },
        { value: 'ASR', label: '语音识别' }
    ];

    function typeLabel(value) {
        return TYPE_LABEL[value] || value || '-';
    }

    /* ---------------- Tab1 模型配置 ---------------- */
    window.AdminPages.AiProvidersPage = {
        name: 'AiProvidersPage',
        template: [
            '<div class="ai-layout">',
            '  <aside class="ai-side">',
            '    <div class="ai-side-head">',
            '      <div>',
            '        <div class="ai-side-title">供应商</div>',
            '        <div class="muted">已接入 {{ providers.length }} 个</div>',
            '      </div>',
            '      <el-button circle :icon="PlusIcon" size="small" @click="openCreate" />',
            '    </div>',
            '    <div v-if="!providers.length" class="muted" style="padding:12px 4px">还没有供应商，点击右上角 + 新增</div>',
            '    <div v-for="p in providers" :key="p.providerId"',
            '         class="ai-provider-item" :class="{ active: p.providerId === selectedId }"',
            '         @click="select(p.providerId)">',
            '      <div class="ai-provider-name">',
            '        <span class="dot" :class="p.enabled && p.apiKeyConfigured ? \'ok\' : \'off\'"></span>',
            '        <span class="strong">{{ p.providerName }}</span>',
            '      </div>',
            '      <div class="muted ai-provider-code">{{ p.providerCode }} · {{ p.modelCount }} 个模型</div>',
            '    </div>',
            '  </aside>',

            '  <section class="ai-main" v-loading="loading">',
            '    <template v-if="current">',
            '      <div class="page-card">',
            '        <div class="ai-provider-head">',
            '          <div>',
            '            <h3 class="ai-title">{{ current.providerName }}</h3>',
            '            <div class="muted mono">{{ current.providerCode }}</div>',
            '          </div>',
            '          <div class="ai-head-actions">',
            '            <el-switch v-model="current.enabled" @change="toggleEnabled" />',
            '            <el-button :loading="checking" @click="check">检查连通性</el-button>',
            '            <el-button @click="openEdit">编辑</el-button>',
            '            <el-button type="danger" plain @click="removeProvider">删除</el-button>',
            '          </div>',
            '        </div>',
            '        <el-descriptions :column="3" border style="margin-top:12px">',
            '          <el-descriptions-item label="状态">',
            '            <span class="dot" :class="current.enabled ? \'ok\' : \'off\'"></span>',
            '            {{ current.enabled ? \'启用中\' : \'已停用\' }}',
            '          </el-descriptions-item>',
            '          <el-descriptions-item label="上次检查">{{ fmt(current.lastCheckAt) }}</el-descriptions-item>',
            '          <el-descriptions-item label="凭据状态">',
            '            {{ current.apiKeyConfigured ? \'已配置\' : \'未配置\' }}',
            '          </el-descriptions-item>',
            '          <el-descriptions-item label="Base URL" :span="3" class="mono">{{ current.baseUrl }}</el-descriptions-item>',
            '          <el-descriptions-item label="状态信息" :span="3">',
            '            <template v-if="current.lastCheckResult">',
            '              <span class="dot" :class="current.lastCheckOk ? \'ok\' : \'bad\'"></span>',
            '              {{ current.lastCheckResult }}',
            '            </template>',
            '            <span v-else class="muted">尚未检查</span>',
            '          </el-descriptions-item>',
            '        </el-descriptions>',
            '      </div>',

            '      <div class="page-card" style="margin-top:14px">',
            '        <div class="ai-models-head">',
            '          <div>',
            '            <h4 class="ai-title" style="font-size:15px">模型列表</h4>',
            '            <div class="muted">当前供应商下共 {{ providerModels.length }} 个模型</div>',
            '          </div>',
            '          <el-dropdown @command="addCommand">',
            '            <el-button type="primary"><el-icon><component :is="PlusIcon" /></el-icon>添加模型</el-button>',
            '            <template #dropdown>',
            '              <el-dropdown-menu>',
            '                <el-dropdown-item command="fetch">从供应商获取</el-dropdown-item>',
            '                <el-dropdown-item command="manual">手动添加</el-dropdown-item>',
            '              </el-dropdown-menu>',
            '            </template>',
            '          </el-dropdown>',
            '        </div>',
            '        <div v-if="!providerModels.length" class="muted" style="padding:16px 0">',
            '          还没有模型，点击「添加模型」从供应商拉取或手动添加',
            '        </div>',
            '        <div v-for="m in providerModels" :key="m.modelId" class="ai-model-row">',
            '          <div class="grow">',
            '            <div class="strong">{{ m.providerModelId }}</div>',
            '            <div class="muted mono">供应商模型 ID: {{ m.providerModelId }}</div>',
            '          </div>',
            '          <el-tag size="small" :type="m.modelType === \'VISION\' ? \'warning\' : \'info\'">{{ typeLabel(m.modelType) }}</el-tag>',
            '          <el-switch :model-value="m.enabled" @change="function (v) { toggleModel(m, v) }" style="margin-left:12px" />',
            '          <el-button link type="danger" style="margin-left:8px" @click="removeModel(m)">删除</el-button>',
            '        </div>',
            '      </div>',
            '    </template>',
            '    <el-empty v-else-if="!loading" description="选择或新增一个供应商" />',
            '  </section>',

            '  <el-dialog v-model="editVisible" :title="editId ? \'编辑供应商\' : \'新增供应商\'" width="520px">',
            '    <el-form label-width="100px">',
            '      <el-form-item label="名称" required>',
            '        <el-input v-model.trim="editForm.providerName" maxlength="64" placeholder="如 硅基流动" />',
            '      </el-form-item>',
            '      <el-form-item label="Base URL" required>',
            '        <el-input v-model.trim="editForm.baseUrl" placeholder="如 https://api.siliconflow.cn/v1" />',
            '      </el-form-item>',
            '      <el-form-item label="API Key" :required="!editId">',
            '        <el-input v-model="editForm.apiKey" type="password" show-password',
            '                  :placeholder="editId ? \'留空表示保持原值\' : \'sk-...\'" />',
            '      </el-form-item>',
            '      <el-form-item v-if="!editId" label="编码">',
            '        <el-input v-model.trim="editForm.providerCode" placeholder="留空自动生成，小写字母/数字/短横线" />',
            '      </el-form-item>',
            '    </el-form>',
            '    <template #footer>',
            '      <el-button @click="editVisible = false">取消</el-button>',
            '      <el-button type="primary" :loading="saving" @click="saveProvider">保存</el-button>',
            '    </template>',
            '  </el-dialog>',

            '  <el-dialog v-model="manualVisible" title="手动添加模型" width="460px">',
            '    <el-form label-width="100px">',
            '      <el-form-item label="模型 ID" required>',
            '        <el-input v-model.trim="manualModelId" placeholder="如 deepseek-ai/DeepSeek-V4" />',
            '      </el-form-item>',
            '      <el-form-item label="类型">',
            '        <el-select v-model="manualType" style="width:100%">',
            '          <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />',
            '        </el-select>',
            '      </el-form-item>',
            '    </el-form>',
            '    <template #footer>',
            '      <el-button @click="manualVisible = false">取消</el-button>',
            '      <el-button type="primary" :loading="saving" @click="saveManual">添加</el-button>',
            '    </template>',
            '  </el-dialog>',

            '  <el-dialog v-model="fetchVisible" title="从供应商获取模型" width="680px">',
            '    <div v-loading="fetching" style="min-height:200px">',
            '      <template v-if="remoteLoaded">',
            '        <div class="ai-fetch-head">',
            '          <div class="muted">共 {{ remoteModels.length }} 个模型 · 已选择 {{ selectedRemote.length }} 个</div>',
            '          <div>',
            '            <el-button size="small" @click="selectAllRemote">选择所有</el-button>',
            '            <el-button size="small" @click="selectedRemote = []">清空选择</el-button>',
            '          </div>',
            '        </div>',
            '        <el-input v-model="remoteKeyword" placeholder="搜索模型名称..." clearable style="margin:10px 0" />',
            '        <div class="ai-fetch-list">',
            '          <div v-for="m in filteredRemote" :key="m.id" class="ai-fetch-row">',
            '            <el-checkbox :model-value="selectedRemote.indexOf(m.id) >= 0" @change="function (v) { toggleRemote(m.id, v) }" />',
            '            <div class="grow strong" style="margin-left:10px">{{ m.id }}</div>',
            '            <el-select :model-value="remoteType[m.id] || \'CHAT\'" size="small" style="width:110px"',
            '                       @change="function (v) { remoteType[m.id] = v }">',
            '              <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />',
            '            </el-select>',
            '          </div>',
            '          <el-empty v-if="!filteredRemote.length" description="没有匹配的模型" />',
            '        </div>',
            '      </template>',
            '    </div>',
            '    <template #footer>',
            '      <span class="muted" style="float:left;line-height:32px">已选择 {{ selectedRemote.length }} 个模型</span>',
            '      <el-button @click="fetchVisible = false">关闭</el-button>',
            '      <el-button type="primary" :disabled="!selectedRemote.length" :loading="importing" @click="importRemote">导入</el-button>',
            '    </template>',
            '  </el-dialog>',
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
            var editForm = Vue.reactive({ providerName: '', baseUrl: '', apiKey: '', providerCode: '' });

            var manualVisible = Vue.ref(false);
            var manualModelId = Vue.ref('');
            var manualType = Vue.ref('CHAT');

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
                editVisible.value = true;
            }

            function openEdit() {
                editId.value = current.value.providerId;
                editForm.providerName = current.value.providerName;
                editForm.baseUrl = current.value.baseUrl;
                editForm.apiKey = '';
                editForm.providerCode = '';
                editVisible.value = true;
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
                        apiKey: editForm.apiKey || null
                    })
                    : api.post('/api/admin/ai/providers', {
                        providerName: editForm.providerName,
                        baseUrl: editForm.baseUrl,
                        apiKey: editForm.apiKey,
                        providerCode: editForm.providerCode || null
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
                    manualType.value = 'CHAT';
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
                        { models: [{ providerModelId: manualModelId.value, modelType: manualType.value }] })
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
                manualVisible: manualVisible, manualModelId: manualModelId, manualType: manualType,
                fetchVisible: fetchVisible, fetching: fetching, remoteLoaded: remoteLoaded,
                remoteModels: remoteModels, remoteKeyword: remoteKeyword,
                selectedRemote: selectedRemote, remoteType: remoteType, importing: importing,
                filteredRemote: filteredRemote,
                load: load, select: select, check: check, toggleEnabled: toggleEnabled,
                openCreate: openCreate, openEdit: openEdit, saveProvider: saveProvider,
                removeProvider: removeProvider, addCommand: addCommand, saveManual: saveManual,
                toggleRemote: toggleRemote, selectAllRemote: selectAllRemote, importRemote: importRemote,
                toggleModel: toggleModel, removeModel: removeModel,
                typeOptions: TYPE_OPTIONS, typeLabel: typeLabel,
                fmt: api.fmtDateTime,
                PlusIcon: ElementPlusIconsVue.Plus
            };
        }
    };

    /* ---------------- Tab2 模型列表 ---------------- */
    window.AdminPages.AiModelsPage = {
        name: 'AiModelsPage',
        template: [
            '<div class="page-card">',
            '  <div class="toolbar">',
            '    <el-input v-model.trim="keyword" placeholder="输入关键词搜索" clearable style="width:220px" @keyup.enter="load" @clear="load" />',
            '    <el-select v-model="modelType" placeholder="类型：全部" clearable style="width:140px" @change="load">',
            '      <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />',
            '    </el-select>',
            '    <el-button type="primary" :icon="SearchIcon" @click="load">搜索</el-button>',
            '    <div class="grow"></div>',
            '    <span class="muted">共 {{ rows.length }} 个模型</span>',
            '    <el-button :icon="RefreshIcon" @click="load">刷新</el-button>',
            '  </div>',
            '  <el-table :data="rows" v-loading="loading" stripe>',
            '    <el-table-column prop="providerModelId" label="模型" min-width="260" show-overflow-tooltip />',
            '    <el-table-column label="供应商" width="150">',
            '      <template #default="scope">',
            '        <el-tag size="small" effect="plain">{{ scope.row.providerName }}</el-tag>',
            '      </template>',
            '    </el-table-column>',
            '    <el-table-column label="类型" width="110">',
            '      <template #default="scope">',
            '        <el-tag size="small" :type="scope.row.modelType === \'VISION\' ? \'warning\' : \'info\'">',
            '          {{ typeLabel(scope.row.modelType) }}',
            '        </el-tag>',
            '      </template>',
            '    </el-table-column>',
            '    <el-table-column label="启用" width="90">',
            '      <template #default="scope">',
            '        <el-switch :model-value="scope.row.enabled" @change="function (v) { toggle(scope.row, v) }" />',
            '      </template>',
            '    </el-table-column>',
            '    <el-table-column label="操作" width="90" fixed="right">',
            '      <template #default="scope">',
            '        <el-button link type="danger" @click="remove(scope.row)">删除</el-button>',
            '      </template>',
            '    </el-table-column>',
            '  </el-table>',
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
            '<div class="page-card" style="max-width:860px">',
            '  <h4 class="ai-title" style="margin-top:0">默认模型</h4>',
            '  <p class="muted" style="margin-top:0">每个类型设置一个默认模型；AI 助手使用「聊天模型」的默认项，未设置时提示未配置。</p>',
            '  <div v-for="def in defaults" :key="def.typeCode" class="ai-default-row" v-loading="loading">',
            '    <div class="ai-default-label">',
            '      <div class="strong">{{ def.typeName }}</div>',
            '      <div class="muted mono">{{ def.model ? def.model.providerModelId : \'未设置\' }}</div>',
            '    </div>',
            '    <el-select :model-value="def.model ? def.model.modelId : null" placeholder="选择模型" filterable',
            '               style="width:340px" @change="function (v) { setDefault(def, v) }">',
            '      <el-option v-for="m in enabledModels" :key="m.modelId"',
            '                 :label="m.providerModelId + \'（\' + m.providerName + \'）\'"',
            '                 :value="m.modelId" />',
            '    </el-select>',
            '    <el-button :disabled="!def.model" style="margin-left:10px" @click="setDefault(def, null)">清除</el-button>',
            '  </div>',
            '  <el-divider />',
            '  <h4 class="ai-title">全局设置</h4>',
            '  <el-form label-width="150px">',
            '    <el-form-item label="启用 AI 助手">',
            '      <el-switch v-model="global.enabled" />',
            '    </el-form-item>',
            '    <div class="form-grid">',
            '      <el-form-item label="连接超时（秒）">',
            '        <el-input-number v-model="global.connectTimeoutSeconds" :min="1" :max="120" />',
            '      </el-form-item>',
            '      <el-form-item label="读取超时（秒）">',
            '        <el-input-number v-model="global.readTimeoutSeconds" :min="1" :max="600" />',
            '      </el-form-item>',
            '      <el-form-item label="最大输入字符数">',
            '        <el-input-number v-model="global.maxInputCharacters" :min="1" :max="100000" :step="1000" />',
            '      </el-form-item>',
            '      <el-form-item label="每小时消息上限">',
            '        <el-input-number v-model="global.hourlyMessageLimit" :min="1" :max="10000" />',
            '      </el-form-item>',
            '      <el-form-item label="携带历史条数">',
            '        <el-input-number v-model="global.historyWindow" :min="0" :max="100" />',
            '      </el-form-item>',
            '      <el-form-item label="单条消息上限字符">',
            '        <el-input-number v-model="global.maxMessageCharacters" :min="1" :max="100000" :step="100" />',
            '      </el-form-item>',
            '    </div>',
            '    <el-form-item>',
            '      <el-button type="primary" :loading="saving" @click="saveGlobal">保存设置</el-button>',
            '    </el-form-item>',
            '  </el-form>',
            '  <p class="form-hint">所有保存即时生效，无需重启。聊天当前生效模型：{{ activeChatModel || \'未配置\' }}</p>',
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
                    api.get('/api/admin/ai-config')
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
                api.put('/api/admin/ai-config', {
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
            '<el-tabs v-model="tab" class="ai-tabs">',
            '  <el-tab-pane label="模型配置" name="providers"><component :is="\'AiProvidersPage\'" /></el-tab-pane>',
            '  <el-tab-pane label="模型列表" name="models"><component :is="\'AiModelsPage\'" /></el-tab-pane>',
            '  <el-tab-pane label="默认模型" name="defaults"><component :is="\'AiDefaultsPage\'" /></el-tab-pane>',
            '</el-tabs>'
        ].join('\n'),
        setup: function () {
            var tab = window.Vue.ref('providers');
            return { tab: tab };
        }
    };
})();
