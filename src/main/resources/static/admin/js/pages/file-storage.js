/* 文件存储 SYS05：文件列表/容量统计/逻辑删除 + 存储设置（sys_config）。
   保存后 FileProperties 热更新，存储路由按最新 storage 切换 local / MinIO / OSS，无需重启。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    function fmtSize(bytes) {
        if (bytes == null) return '—';
        var value = Number(bytes);
        if (value < 1024) return value + ' B';
        if (value < 1024 * 1024) return (value / 1024).toFixed(1) + ' KB';
        if (value < 1024 * 1024 * 1024) return (value / 1024 / 1024).toFixed(1) + ' MB';
        return (value / 1024 / 1024 / 1024).toFixed(2) + ' GB';
    }
    function fmtTime(v) { return v ? String(v).replace('T', ' ').slice(0, 16) : '—'; }

    window.AdminPages.FileStoragePage = {
        name: 'FileStoragePage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>文件存储</h1><div class="sub">存储配置 · 容量统计 · 文件治理</div></div>',
            '    <div class="r"><button class="btn2" @click="loadAll">刷新</button></div></div>',

            '  <div v-if="loading" class="os-panel"><div class="os-empty">正在加载文件数据…</div></div>',
            '  <div v-else-if="error" class="os-panel"><div class="os-empty">{{ error }} <button class="os-btn sm" style="margin-left:8px" @click="loadAll">重试</button></div></div>',

            '  <template v-else>',
            '    <div class="os-grid" style="grid-template-columns:repeat(4,1fr)">',
            '      <div class="os-kpi"><div class="k-label"><span class="dot"></span>文件总数</div><div class="k-val">{{ settings ? settings.totalFiles : "—" }}<small>个</small></div><div class="k-foot"><span>逻辑删除后不计入</span></div></div>',
            '      <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-info)"></span>占用容量</div><div class="k-val" style="font-size:22px">{{ settings ? fmtSize(settings.totalBytes) : "—" }}</div><div class="k-foot"><span>按 file_storage 记录汇总</span></div></div>',
            '      <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-warn)"></span>存储实现</div><div class="k-val" style="font-size:22px">{{ settings ? String(settings.storage).toUpperCase() : "—" }}</div><div class="k-foot"><span>{{ settings ? "单文件上限 " + settings.maxSizeMb + "MB" : "" }}</span></div></div>',
            '      <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>磁盘可用</div><div class="k-val" style="font-size:20px">{{ settings && settings.diskTotalBytes ? fmtSize(settings.diskUsableBytes) : "—" }}</div><div class="k-foot"><span>{{ settings && settings.diskTotalBytes ? "共 " + fmtSize(settings.diskTotalBytes) : "磁盘信息不可用" }}</span></div></div>',
            '    </div>',

            '    <div style="display:grid;grid-template-columns:minmax(0,1.15fr) minmax(0,.85fr);gap:16px">',
            '      <div class="os-panel">',
            '        <div class="os-panel-h"><h3>存储设置</h3><span class="hint">sys_config · file 分组 · 保存后热更新</span><div class="r"><button class="os-btn sm" :disabled="testingStorage !== \'\'" @click="testCurrentStorage">{{ testingStorage ? "测试中…" : "测试当前存储" }}</button><button v-if="canManage" class="os-btn sm primary" :disabled="saving" @click="saveSettings">{{ saving ? "保存中…" : "保存设置" }}</button></div></div>',
            '        <div class="os-panel-b" style="display:flex;flex-direction:column;gap:12px">',
            '          <div class="os-form-grid">',
            '            <div class="os-field"><label>单文件上限（MB）</label><input class="os-input" type="number" min="1" max="1024" v-model.number="form.maxSizeMb" :disabled="!canManage" /></div>',
            '            <div class="os-field"><label>签名有效期（分钟）</label><input class="os-input" type="number" min="1" max="1440" v-model.number="form.viewTtlMinutes" :disabled="!canManage" /></div>',
            '          </div>',
            '          <div class="os-form-grid">',
            '            <div class="os-field"><label>存储实现</label><select class="os-select" v-model="form.storage" :disabled="!canManage"><option value="local">local（本地目录）</option><option value="minio">minio（MinIO）</option><option value="oss">oss（阿里云 OSS）</option></select></div>',
            '            <div class="os-field"><label>本地目录</label><input class="os-input" v-model.trim="form.storageDir" maxlength="255" :disabled="!canManage" /></div>',
            '          </div>',
            '          <div v-if="settings && settings.storage === \'local\' && (settings.ossAccessKeyIdConfigured || settings.minioAccessKeyConfigured)" class="os-note warn"><b>当前生效存储是 LOCAL</b> MinIO/OSS 配置已保存但不会自动启用；要切换请选择对应存储实现并保存，或先在下方「存储连通性测试」分别测试。</div>',
            '          <div v-if="form.storage === \'minio\'" style="display:flex;flex-direction:column;gap:10px">',
            '            <div class="os-form-grid">',
            '              <div class="os-field"><label>MinIO Endpoint</label><input class="os-input" v-model.trim="form.minioEndpoint" placeholder="http://host:9000" :disabled="!canManage" /></div>',
            '              <div class="os-field"><label>对外公开地址（可选）</label><input class="os-input" v-model.trim="form.minioPublicEndpoint" placeholder="留空使用 Endpoint" :disabled="!canManage" /></div>',
            '            </div>',
            '            <div class="os-form-grid">',
            '              <div class="os-field"><label>MinIO 桶</label><input class="os-input" v-model.trim="form.minioBucket" maxlength="64" :disabled="!canManage" /></div>',
            '              <div class="os-field"><label>AccessKey</label><input class="os-input" type="password" v-model.trim="form.minioAccessKey" :placeholder="settings && settings.minioAccessKeyConfigured ? \'已配置，留空不修改\' : \'未配置，请输入\'" :disabled="!canManage" /></div>',
            '            </div>',
            '            <div class="os-field"><label>SecretKey</label><input class="os-input" type="password" v-model.trim="form.minioSecretKey" :placeholder="settings && settings.minioSecretKeyConfigured ? \'已配置，留空不修改\' : \'未配置，请输入\'" :disabled="!canManage" /></div>',
            '          </div>',
            '          <div v-if="form.storage === \'oss\'" style="display:flex;flex-direction:column;gap:10px">',
            '            <div class="os-form-grid">',
            '              <div class="os-field"><label>OSS Endpoint</label><input class="os-input" v-model.trim="form.ossEndpoint" placeholder="https://oss-cn-chengdu.aliyuncs.com" :disabled="!canManage" /></div>',
            '              <div class="os-field"><label>对外公开域名/CDN（可选）</label><input class="os-input" v-model.trim="form.ossPublicEndpoint" placeholder="留空使用 Endpoint" :disabled="!canManage" /></div>',
            '            </div>',
            '            <div class="os-form-grid">',
            '              <div class="os-field"><label>OSS 桶</label><input class="os-input" v-model.trim="form.ossBucket" maxlength="64" placeholder="如 smartcampus" :disabled="!canManage" /></div>',
            '              <div class="os-field"><label>AccessKeyId</label><input class="os-input" type="password" v-model.trim="form.ossAccessKeyId" :placeholder="settings && settings.ossAccessKeyIdConfigured ? \'已配置，留空不修改\' : \'未配置，请输入\'" :disabled="!canManage" /></div>',
            '            </div>',
            '            <div class="os-field"><label>AccessKeySecret</label><input class="os-input" type="password" v-model.trim="form.ossAccessKeySecret" :placeholder="settings && settings.ossAccessKeySecretConfigured ? \'已配置，留空不修改\' : \'未配置，请输入\'" :disabled="!canManage" /></div>',
            '          </div>',
            '          <div class="os-form-grid">',
            '            <div class="os-field"><label>签名密钥</label><input class="os-input" type="password" v-model.trim="form.signSecret" :placeholder="settings && settings.signSecretConfigured ? \'已替换默认值，留空不修改\' : \'仍为开发默认值，建议替换\'" :disabled="!canManage" /></div>',
            '            <div class="os-field"><label>当前有效存储</label><input class="os-input" :value="settings ? String(settings.storage).toUpperCase() : \'—\'" disabled /></div>',
            '          </div>',
            '          <div class="os-note warn"><b>生效说明</b> {{ settings ? settings.runtimeNote : "" }}</div>',
            '          <div class="os-note"><b>MIME 白名单</b> {{ settings ? settings.mimePolicy : "—" }}</div>',

            '          <div style="font-size:11px;color:var(--tk-text3);line-height:1.7">当前运行时：{{ settings ? settings.storage : "—" }} · 上限 {{ settings ? settings.maxSizeMb : "—" }}MB · TTL {{ settings ? settings.viewTtlMinutes : "—" }} 分钟 · 签名密钥{{ settings && settings.signSecretConfigured ? "已替换默认值" : "仍为开发默认值（生产必须替换）" }}<br>本地目录：{{ settings ? settings.storageDir : "—" }}<span v-if="settings && settings.storage === \'minio\'"> · MinIO：{{ settings.minioEndpoint }} / {{ settings.minioBucket }} · AccessKey{{ settings.minioAccessKeyConfigured ? "已配置" : "未配置" }}</span><span v-if="settings && settings.storage === \'oss\'"> · OSS：{{ settings.ossEndpoint }} / {{ settings.ossBucket }} · AK{{ settings.ossAccessKeyIdConfigured ? "已配置" : "未配置" }}</span></div>',
            '        </div>',
            '      </div>',
            '      <div class="os-panel">',
            '        <div class="os-panel-h"><h3>内容类型分布</h3><span class="hint">按文件数</span></div>',
            '        <div class="os-panel-b" style="display:flex;flex-direction:column;gap:9px">',
            '          <div v-for="t in (settings ? settings.byType : [])" :key="t.contentType">',
            '            <div style="display:flex;justify-content:space-between;font-size:11px;color:var(--tk-text3)"><span>{{ t.contentType }}</span><span>{{ t.count }} 个 · {{ fmtSize(t.bytes) }}</span></div>',
            '            <div style="height:6px;background:var(--tk-subtle);border-radius:3px;margin-top:4px;overflow:hidden"><div :style="{ width: typePercent(t) + \'%\', height:\'6px\', background:\'var(--tk-brand)\' }"></div></div>',
            '          </div>',
            '          <div v-if="!settings || !settings.byType.length" class="os-empty">暂无文件</div>',
            '        </div>',
            '      </div>',
            '    </div>',

            '    <div class="os-panel">',
            '      <div class="os-panel-h"><h3>存储连通性测试</h3><span class="hint">分别使用已保存配置测试 local / MinIO / OSS；不会切换当前生效存储</span></div>',
            '      <div class="os-panel-b" style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px">',
            '        <div v-for="t in storageTargets" :key="t.key" style="border:1px solid var(--tk-stroke);border-radius:8px;padding:12px;display:flex;flex-direction:column;gap:7px;min-width:0">',
            '          <div style="display:flex;align-items:center;gap:7px;min-width:0"><b style="font-size:12px;color:var(--tk-text)">{{ t.label }}</b><span :style="{ fontSize: \'9.5px\', padding: \'1px 6px\', borderRadius: \'999px\', color: t.active ? \'var(--tk-ok)\' : \'var(--tk-text3)\', background: t.active ? \'var(--tk-ok-bg, var(--tk-subtle))\' : \'var(--tk-subtle)\' }">{{ t.active ? "当前生效" : "未启用" }}</span><span style="flex:1"></span><button class="os-btn sm" :disabled="testingStorage !== \'\'" @click="testStorage(t.key)">{{ testingStorage === t.key ? "测试中…" : "测试" }}</button></div>',
            '          <div style="font-size:10.5px;color:var(--tk-text3);overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="t.target">{{ t.target }}</div>',
            '          <div style="font-size:10.5px;color:var(--tk-text3)">{{ t.configured ? "配置完整" : "配置不完整" }} · {{ t.credentialHint }}</div>',
            '          <div v-if="testResults[t.key]" class="os-note" :class="{ warn: !testResults[t.key].reachable }" style="display:block;padding:7px 9px;line-height:1.55"><b>{{ testResults[t.key].label }} · {{ testResults[t.key].reachable ? "可达" : "不可达" }}</b><span v-if="testResults[t.key].latencyMs != null"> · {{ testResults[t.key].latencyMs }}ms</span><br>{{ testResults[t.key].detail }}</div>',
            '        </div>',
            '      </div>',
            '    </div>',

            '    <div class="os-panel" style="flex:1;min-height:0;display:flex;flex-direction:column">',
            '      <div class="os-panel-h" style="flex-wrap:wrap;row-gap:9px"><h3>文件列表</h3><span class="hint">按上传时间倒序</span>',
            '        <div class="r" style="flex-wrap:wrap">',
            '          <select class="os-select" style="width:130px" v-model="contentType" @change="pageNum = 1; loadFiles()"><option value="">全部类型</option><option value="image/">图片</option><option value="audio/">音频</option><option value="application/">文档</option><option value="text/">文本</option></select>',
            '          <div class="srch" style="height:34px;width:220px;background:var(--tk-input)"><el-icon :size="13"><Search /></el-icon><input v-model.trim="keyword" placeholder="搜索文件名" @keyup.enter="pageNum = 1; loadFiles()" /></div>',
            '        </div>',
            '      </div>',
            '      <div style="overflow:auto;flex:1">',
            '        <table style="width:100%;border-collapse:collapse;table-layout:fixed"><thead><tr><th style="width:34%">文件名</th><th style="width:150px">类型</th><th style="width:100px">大小</th><th style="width:110px">上传人</th><th style="width:140px">上传时间</th><th style="width:120px;text-align:right">操作</th></tr></thead><tbody>',
            '          <tr v-for="f in rows" :key="f.fileId"><td><div style="display:flex;align-items:center;gap:9px;min-width:0"><span style="width:30px;height:24px;border-radius:4px;background:var(--tk-subtle);display:grid;place-items:center;font-size:9px;color:var(--tk-brand);flex:none">{{ typeShort(f.contentType) }}</span><div style="min-width:0"><b style="font-weight:550;color:var(--tk-text);display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="f.originalName">{{ f.originalName }}</b><span class="os-mono" style="font-size:9.5px;color:var(--tk-muted)">#{{ f.fileId }}</span></div></div></td><td style="font-size:11px;color:var(--tk-text3)">{{ f.contentType }}</td><td class="os-mono">{{ fmtSize(f.fileSize) }}</td><td class="os-mono" style="color:var(--tk-text3)">{{ f.uploaderId || "—" }}</td><td style="font-size:11px;color:var(--tk-text3)">{{ fmtTime(f.createdAt) }}</td><td style="text-align:right;white-space:nowrap"><a v-if="f.viewPath" :href="f.viewPath" target="_blank" rel="noopener" style="font-size:11px;color:var(--tk-brand);text-decoration:none">预览</a><span v-if="canManage" style="font-size:11px;color:var(--tk-bad);cursor:pointer;margin-left:10px" @click="remove(f)">删除</span></td></tr>',
            '          <tr v-if="!rows.length"><td colspan="6" class="os-empty">没有匹配的文件</td></tr>',
            '        </tbody></table>',
            '      </div>',
            '      <div style="display:flex;align-items:center;gap:10px;padding:11px 18px;border-top:1px solid var(--tk-stroke)"><span style="font-size:11px;color:var(--tk-text3)">共 {{ total }} 个文件 · 第 {{ pageNum }} / {{ totalPages }} 页</span><span style="flex:1"></span><button class="os-btn sm" :disabled="pageNum <= 1" @click="pageNum--; loadFiles()">上一页</button><button class="os-btn sm" :disabled="pageNum >= totalPages" @click="pageNum++; loadFiles()">下一页</button></div>',
            '    </div>',
            '  </template>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var settings = Vue.ref(null);
            var rows = Vue.ref([]);
            var total = Vue.ref(0);
            var pageNum = Vue.ref(1);
            var totalPages = Vue.ref(1);
            var pageSize = 20;
            var keyword = Vue.ref('');
            var contentType = Vue.ref('');
            var loading = Vue.ref(false);
            var error = Vue.ref('');
            var saving = Vue.ref(false);
            var testingStorage = Vue.ref('');
            var testResults = Vue.reactive({});
            var form = Vue.reactive({
                maxSizeMb: 5, viewTtlMinutes: 30, storage: 'local', storageDir: './data/files', signSecret: '',
                minioEndpoint: '', minioPublicEndpoint: '', minioBucket: 'student-hub',
                minioAccessKey: '', minioSecretKey: '',
                ossEndpoint: '', ossPublicEndpoint: '', ossBucket: '',
                ossAccessKeyId: '', ossAccessKeySecret: ''
            });

            function permissions() {
                var user = (window.AdminApp && window.AdminApp.state && window.AdminApp.state.user) || {};
                var perms = (user.permissions || []).slice();
                var roles = user.roles || [];
                if (roles.indexOf('PLATFORM_ADMIN') >= 0 && perms.indexOf('platform:all') < 0) perms.push('platform:all');
                return perms;
            }
            var canManage = Vue.computed(function () {
                var perms = permissions();
                return perms.indexOf('platform:all') >= 0 || perms.indexOf('file:manage') >= 0;
            });
            var storageTargets = Vue.computed(function () {
                var s = settings.value;
                if (!s) return [];
                return [
                    {
                        key: 'local', label: '本地目录', active: s.storage === 'local',
                        target: s.storageDir || '未配置',
                        configured: !!s.storageDir, credentialHint: '无需 AK/SK'
                    },
                    {
                        key: 'minio', label: 'MinIO', active: s.storage === 'minio',
                        target: (s.minioEndpoint || '未配置 Endpoint') + ' · 桶 ' + (s.minioBucket || '未配置'),
                        configured: !!(s.minioEndpoint && s.minioBucket && s.minioAccessKeyConfigured && s.minioSecretKeyConfigured),
                        credentialHint: s.minioAccessKeyConfigured && s.minioSecretKeyConfigured ? 'AK/SK 已配置' : 'AK/SK 未配置完整'
                    },
                    {
                        key: 'oss', label: '阿里云 OSS', active: s.storage === 'oss',
                        target: (s.ossEndpoint || '未配置 Endpoint') + ' · 桶 ' + (s.ossBucket || '未配置'),
                        configured: !!(s.ossEndpoint && s.ossBucket && s.ossAccessKeyIdConfigured && s.ossAccessKeySecretConfigured),
                        credentialHint: s.ossAccessKeyIdConfigured && s.ossAccessKeySecretConfigured ? 'AK/SK 已配置' : 'AK/SK 未配置完整'
                    }
                ];
            });
            function typeShort(type) {
                if (!type) return 'FILE';
                if (type.indexOf('image/') === 0) return 'IMG';
                if (type.indexOf('audio/') === 0) return 'AUD';
                if (type.indexOf('video/') === 0) return 'VID';
                if (type.indexOf('text/') === 0) return 'TXT';
                return 'DOC';
            }
            function typePercent(t) {
                if (!settings.value || !settings.value.totalFiles) return 0;
                return Math.max(2, Math.round(t.count / settings.value.totalFiles * 100));
            }
            function applySettings(data) {
                settings.value = data;
                form.maxSizeMb = data.maxSizeMb;
                form.viewTtlMinutes = data.viewTtlMinutes;
                form.storage = data.storage;
                form.storageDir = data.storageDir || './data/files';
                form.minioEndpoint = data.minioEndpoint || '';
                form.minioPublicEndpoint = data.minioPublicEndpoint || '';
                form.minioBucket = data.minioBucket || 'student-hub';
                form.ossEndpoint = data.ossEndpoint || '';
                form.ossPublicEndpoint = data.ossPublicEndpoint || '';
                form.ossBucket = data.ossBucket || '';
                // 密钥字段后端只返回“是否已配置”，不回显明文；输入框保持空白，留空即不修改。
                form.signSecret = '';
                form.minioAccessKey = '';
                form.minioSecretKey = '';
                form.ossAccessKeyId = '';
                form.ossAccessKeySecret = '';
            }
            function loadSettings() {
                return api.get('/api/admin/files/settings').then(function (data) { applySettings(data); });
            }
            function loadFiles() {
                var query = 'pageNum=' + pageNum.value + '&pageSize=' + pageSize;
                if (keyword.value) query += '&keyword=' + encodeURIComponent(keyword.value);
                if (contentType.value) query += '&contentType=' + encodeURIComponent(contentType.value);
                return api.get('/api/admin/files?' + query).then(function (data) {
                    rows.value = (data && data.records) || [];
                    total.value = (data && data.total) || 0;
                    totalPages.value = Math.max(1, Math.ceil(total.value / pageSize));
                });
            }
            function loadAll() {
                loading.value = true;
                error.value = '';
                Promise.all([loadSettings(), loadFiles()])
                    .catch(function (err) {
                        error.value = (err && err.message) || '文件数据加载失败';
                        El.ElMessage.error(error.value);
                    })
                    .finally(function () { loading.value = false; });
            }
            function saveSettings() {
                if (!canManage.value) { El.ElMessage.warning('当前账号没有文件管理权限'); return; }
                var payload = {
                    maxSizeMb: form.maxSizeMb,
                    viewTtlMinutes: form.viewTtlMinutes,
                    storage: form.storage,
                    storageDir: form.storageDir,
                    signSecret: form.signSecret || undefined
                };
                if (form.storage === 'minio') {
                    payload.minioEndpoint = form.minioEndpoint;
                    payload.minioPublicEndpoint = form.minioPublicEndpoint;
                    payload.minioBucket = form.minioBucket;
                    payload.minioAccessKey = form.minioAccessKey || undefined;
                    payload.minioSecretKey = form.minioSecretKey || undefined;
                }
                if (form.storage === 'oss') {
                    payload.ossEndpoint = form.ossEndpoint;
                    payload.ossPublicEndpoint = form.ossPublicEndpoint;
                    payload.ossBucket = form.ossBucket;
                    payload.ossAccessKeyId = form.ossAccessKeyId || undefined;
                    payload.ossAccessKeySecret = form.ossAccessKeySecret || undefined;
                }
                saving.value = true;
                api.post('/api/admin/files/settings', payload).then(function (data) {
                    applySettings(data);
                    Object.keys(testResults).forEach(function (key) { delete testResults[key]; });
                    El.ElMessage.success('设置已保存并热更新，存储实现已按最新配置生效');
                }).catch(function (err) { El.ElMessage.error((err && err.message) || '保存失败'); })
                    .finally(function () { saving.value = false; });
            }
            function testStorage(target) {
                if (!target) return;
                testingStorage.value = target;
                api.post('/api/admin/files/settings/test?storage=' + encodeURIComponent(target))
                    .then(function (data) {
                        testResults[target] = data;
                        El.ElMessage.success(data.label + (data.reachable ? ' 连通性测试通过' : ' 连通性测试未通过'));
                    })
                    .catch(function (err) { El.ElMessage.error((err && err.message) || '连通性测试失败'); })
                    .finally(function () { testingStorage.value = ''; });
            }
            function testCurrentStorage() {
                if (settings.value && settings.value.storage) testStorage(settings.value.storage);
            }
            function remove(file) {
                if (!canManage.value) return;
                El.ElMessageBox.confirm('逻辑删除后列表不再展示，对象存储中的物理文件与业务引用保留。确定删除「' + file.originalName + '」？',
                    '删除文件', { type: 'warning' })
                    .then(function () { return api.delete('/api/admin/files/' + file.fileId); })
                    .then(function () {
                        El.ElMessage.success('文件已逻辑删除');
                        loadAll();
                    })
                    .catch(function (err) {
                        if (err !== 'cancel' && err !== 'close') El.ElMessage.error((err && err.message) || '删除失败');
                    });
            }

            loadAll();
            return {
                settings: settings, rows: rows, total: total, pageNum: pageNum, totalPages: totalPages,
                keyword: keyword, contentType: contentType, loading: loading, error: error, saving: saving,
                testingStorage: testingStorage, testResults: testResults, storageTargets: storageTargets,
                form: form, canManage: canManage,
                loadAll: loadAll, loadFiles: loadFiles, saveSettings: saveSettings,
                testStorage: testStorage, testCurrentStorage: testCurrentStorage,
                remove: remove, fmtSize: fmtSize, fmtTime: fmtTime, typeShort: typeShort, typePercent: typePercent
            };
        }
    };
})();
