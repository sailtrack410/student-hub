/* 版本发布（管理端）：App 安装包版本草稿、安装包上传、发布/下线与下载。
   接口 /api/admin/app-versions；上传安装包走原始二进制流（application/octet-stream），
   不使用 AdminApi.upload（FormData 会触发全局 multipart 限制）。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    var PLATFORMS = [
        { value: 'ANDROID', label: 'Android' },
        { value: 'IOS', label: 'iOS' },
        { value: 'HARMONY', label: 'HarmonyOS' }
    ];
    var STATUS_LABELS = { DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' };
    var STATUS_BADGES = { DRAFT: 'n2', PUBLISHED: 'g', OFFLINE: 'a' };
    var MAX_PACKAGE_BYTES = 300 * 1024 * 1024;

    window.AdminPages.AppVersionsPage = {
        name: 'AppVersionsPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="page-h">',
            '    <div><h1>版本发布</h1><div class="sub">维护 App 安装包与外链下载地址；新建草稿 → 上传安装包或填外链 → 发布。同一平台同一渠道同时只保留一个已发布版本</div></div>',
            '    <div class="r">',
            '      <button class="btn2" :disabled="loading" @click="load">{{ loading ? "刷新中…" : "刷新" }}</button>',
            '      <button class="c blue" @click="openCreate">新建版本</button>',
            '    </div>',
            '  </div>',

            '  <div class="lcard" style="display:flex;flex-direction:column;min-height:0;flex:1">',
            '    <div class="cap">版本列表<span class="cnt" v-if="rows.length">共 {{ rows.length }} 个版本</span></div>',
            '    <div style="display:flex;gap:8px;padding:0 16px 12px;align-items:center">',
            '      <div class="ipt2" style="width:150px">',
            '        <select v-model="filterPlatform" style="width:100%" @change="load">',
            '          <option value="">全部平台</option>',
            '          <option v-for="p in platforms" :key="p.value" :value="p.value">{{ p.label }}</option>',
            '        </select>',
            '      </div>',
            '      <div class="ipt2" style="width:140px">',
            '        <select v-model="filterStatus" style="width:100%" @change="load">',
            '          <option value="">全部状态</option>',
            '          <option value="DRAFT">草稿</option>',
            '          <option value="PUBLISHED">已发布</option>',
            '          <option value="OFFLINE">已下线</option>',
            '        </select>',
            '      </div>',
            '      <span class="hint2">安装包以原始二进制流直传，支持 .apk / .ipa / .hap，单包不超过 300MB</span>',
            '    </div>',
            '    <div class="tblw" style="flex:1;min-height:0;overflow:auto">',
            '      <table class="pk">',
            '        <thead><tr>',
            '          <th style="width:110px">平台 / 渠道</th>',
            '          <th style="width:150px">版本</th>',
            '          <th style="width:230px">安装包</th>',
            '          <th style="width:130px">强更 / 最低版本</th>',
            '          <th style="width:90px">状态</th>',
            '          <th style="width:140px">发布时间</th>',
            '          <th style="width:80px">下载量</th>',
            '          <th style="width:250px" class="r">操作</th>',
            '        </tr></thead>',
            '        <tbody>',
            '          <tr v-if="!rows.length && !loading"><td colspan="8" style="text-align:center;color:var(--tk-muted);padding:24px 0">暂无版本记录，点击右上角「新建版本」开始</td></tr>',
            '          <tr v-for="row in rows" :key="row.id">',
            '            <td>',
            '              <span class="bdg plain">{{ platformLabel(row.platform) }}</span>',
            '              <div style="color:var(--tk-muted);font-size:12px;margin-top:4px">{{ row.channel || "OFFICIAL" }}</div>',
            '            </td>',
            '            <td>',
            '              <div style="font-weight:600;color:var(--tk-text)">{{ row.versionName }}</div>',
            '              <div style="color:var(--tk-muted);font-size:12px">code {{ row.versionCode }}</div>',
            '            </td>',
            '            <td>',
            '              <template v-if="row.hasPackage">',
            '                <div style="word-break:break-all">{{ row.packageFileName || "-" }}</div>',
            '                <div style="color:var(--tk-muted);font-size:12px">{{ formatSize(row.packageSize) }}</div>',
            '              </template>',
            '              <template v-else-if="row.downloadUrl">',
            '                <div style="word-break:break-all;color:var(--tk-info)">{{ row.downloadUrl }}</div>',
            '                <div style="color:var(--tk-muted);font-size:12px">外链下载（未上传安装包）</div>',
            '              </template>',
            '              <span v-else style="color:var(--tk-muted)">未上传</span>',
            '            </td>',
            '            <td>',
            '              <span class="bdg" :class="row.forceUpdate ? \'r\' : \'plain\'">{{ row.forceUpdate ? "强制" : "可选" }}</span>',
            '              <div style="color:var(--tk-muted);font-size:12px;margin-top:4px">最低 {{ row.minSupportedVersionCode == null ? "-" : row.minSupportedVersionCode }}</div>',
            '            </td>',
            '            <td><span class="bdg" :class="statusBadge(row.status)">{{ statusLabel(row.status) }}</span></td>',
            '            <td style="color:var(--tk-text3)">{{ fmtDate(row.publishedAt) }}</td>',
            '            <td style="color:var(--tk-text3)">{{ row.downloadCount || 0 }}</td>',
            '            <td class="r">',
            '              <template v-for="(action, index) in actionsOf(row)" :key="action.key">',
            '                <span v-if="index" style="color:var(--tk-muted);margin:0 4px">·</span>',
            '                <span class="op" :class="{ dis: action.disabled }" :style="action.color ? { color: action.color } : {}" @click="action.run && action.run()">{{ action.label }}</span>',
            '              </template>',
            '            </td>',
            '          </tr>',
            '        </tbody>',
            '      </table>',
            '    </div>',
            '  </div>',

            // 新建 / 编辑弹层
            '  <div class="pmask" :class="{ show: visible }" @click.self="visible = false">',
            '    <div class="pcard w560" v-if="form">',
            '      <div class="ph"><b>{{ form.id ? "编辑版本" : "新建版本" }}</b><span class="sp"></span><span class="x" @click="visible = false">✕</span></div>',
            '      <div style="display:flex;gap:12px">',
            '        <div class="fld" style="flex:1"><label>平台 <em>*</em></label>',
            '          <div class="pin"><select v-model="form.platform" style="width:100%">',
            '            <option v-for="p in platforms" :key="p.value" :value="p.value">{{ p.label }}（{{ p.value }}）</option>',
            '          </select></div>',
            '        </div>',
            '        <div class="fld" style="flex:1"><label>渠道</label><div class="pin"><input v-model="form.channel" maxlength="32" placeholder="OFFICIAL" /></div></div>',
            '      </div>',
            '      <div style="display:flex;gap:12px">',
            '        <div class="fld" style="flex:1"><label>versionCode <em>*</em></label><div class="pin"><input type="number" min="1" step="1" v-model.number="form.versionCode" placeholder="整数，如 12" /></div></div>',
            '        <div class="fld" style="flex:1"><label>版本名称 <em>*</em></label><div class="pin"><input v-model="form.versionName" maxlength="32" placeholder="如 1.2.0" /></div></div>',
            '      </div>',
            '      <div style="display:flex;gap:12px">',
            '        <div class="fld" style="flex:1"><label>最低支持 versionCode</label><div class="pin"><input type="number" min="0" step="1" v-model.number="form.minSupportedVersionCode" placeholder="留空表示不限制" /></div></div>',
            '        <div class="fld" style="flex:1"><label>强制更新</label>',
            '          <div style="display:flex;align-items:center;gap:12px;height:40px">',
            '            <span class="switch" :class="{ off: !form.forceUpdate }" @click="form.forceUpdate = !form.forceUpdate"><i></i></span>',
            '            <span style="font-size:13px;color:var(--tk-text2)">{{ form.forceUpdate ? "强制用户更新" : "普通更新，可稍后" }}</span>',
            '          </div>',
            '        </div>',
            '      </div>',
            '      <div class="fld"><label>更新说明</label><textarea class="pta" v-model="form.updateLog" maxlength="2000" placeholder="换行分隔的更新内容；App 更新弹层按纯文本展示"></textarea></div>',
            '      <div class="fld"><label>外链下载地址</label><div class="pin"><input v-model="form.downloadUrl" maxlength="512" placeholder="可选：App Store / TestFlight / CDN 地址；与安装包二选一即可发布" /></div></div>',
            '      <div class="infobar">保存草稿后，在列表点击「上传安装包」上传文件；发布时同平台同渠道的旧版本会自动下线。</div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="visible = false">取消</button>',
            '        <button class="c blue" :disabled="submitting" @click="submit">{{ submitting ? "提交中…" : "保存" }}</button></div>',
            '    </div>',
            '  </div>',

            // 安装包选择（原生 file input + 裸 fetch 原始流，避免 multipart）
            '  <input id="app-version-package" type="file" accept=".apk,.ipa,.hap" style="display:none" @change="onPackageSelected" />',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var rows = Vue.ref([]);
            var loading = Vue.ref(false);
            var filterPlatform = Vue.ref('');
            var filterStatus = Vue.ref('');
            var visible = Vue.ref(false);
            var form = Vue.ref(null);
            var submitting = Vue.ref(false);
            var uploadingId = Vue.ref(null);
            var uploadTarget = Vue.ref(null);

            function load() {
                loading.value = true;
                var params = [];
                if (filterPlatform.value) params.push('platform=' + encodeURIComponent(filterPlatform.value));
                if (filterStatus.value) params.push('status=' + encodeURIComponent(filterStatus.value));
                api.get('/api/admin/app-versions' + (params.length ? '?' + params.join('&') : ''))
                    .then(function (data) { rows.value = data || []; })
                    .catch(function (error) { El.ElMessage.error(error.message || '版本列表加载失败'); })
                    .finally(function () { loading.value = false; });
            }

            function platformLabel(value) {
                var found = PLATFORMS.filter(function (p) { return p.value === value; })[0];
                return found ? found.label : (value || '-');
            }
            function statusLabel(value) { return STATUS_LABELS[value] || value || '-'; }
            function statusBadge(value) { return STATUS_BADGES[value] || 'n2'; }
            function fmtDate(value) { return api.fmtDateTime(value); }
            function formatSize(bytes) {
                var size = Number(bytes || 0);
                if (!isFinite(size) || size <= 0) return '-';
                if (size < 1024) return size + ' B';
                if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB';
                if (size < 1024 * 1024 * 1024) return (size / 1024 / 1024).toFixed(1) + ' MB';
                return (size / 1024 / 1024 / 1024).toFixed(2) + ' GB';
            }

            function openCreate() {
                form.value = {
                    id: null, platform: 'ANDROID', channel: 'OFFICIAL', versionCode: null, versionName: '',
                    minSupportedVersionCode: null, forceUpdate: false, updateLog: '', downloadUrl: ''
                };
                visible.value = true;
            }

            function openEdit(row) {
                form.value = {
                    id: row.id, platform: row.platform || 'ANDROID', channel: row.channel || 'OFFICIAL',
                    versionCode: row.versionCode, versionName: row.versionName || '',
                    minSupportedVersionCode: row.minSupportedVersionCode == null ? null : row.minSupportedVersionCode,
                    forceUpdate: !!row.forceUpdate, updateLog: row.updateLog || '',
                    downloadUrl: row.downloadUrl || ''
                };
                visible.value = true;
            }

            function isBlank(value) { return value === null || value === undefined || value === ''; }

            function submit() {
                var f = form.value;
                if (!isBlank(f.versionCode)) {
                    var code = Number(f.versionCode);
                    if (!isFinite(code) || Math.floor(code) !== code || code < 1) {
                        El.ElMessage.warning('versionCode 必须是大于 0 的整数');
                        return;
                    }
                }
                if (isBlank(f.versionCode)) {
                    El.ElMessage.warning('versionCode 必填');
                    return;
                }
                if (!f.versionName || !String(f.versionName).trim()) {
                    El.ElMessage.warning('版本名称必填');
                    return;
                }
                var minCode = null;
                if (!isBlank(f.minSupportedVersionCode)) {
                    minCode = Number(f.minSupportedVersionCode);
                    if (!isFinite(minCode) || Math.floor(minCode) !== minCode || minCode < 0) {
                        El.ElMessage.warning('最低支持 versionCode 必须是不小于 0 的整数');
                        return;
                    }
                    if (minCode > Number(f.versionCode)) {
                        El.ElMessage.warning('最低支持 versionCode 不能大于当前 versionCode');
                        return;
                    }
                }
                var payload = {
                    platform: f.platform,
                    channel: (f.channel || 'OFFICIAL').trim() || 'OFFICIAL',
                    versionCode: Number(f.versionCode),
                    versionName: String(f.versionName).trim(),
                    minSupportedVersionCode: minCode,
                    forceUpdate: !!f.forceUpdate,
                    updateLog: f.updateLog ? String(f.updateLog) : null,
                    downloadUrl: f.downloadUrl && f.downloadUrl.trim() ? f.downloadUrl.trim() : null
                };
                submitting.value = true;
                var promise = f.id
                    ? api.put('/api/admin/app-versions/' + f.id, payload)
                    : api.post('/api/admin/app-versions', payload);
                promise.then(function () {
                    El.ElMessage.success('已保存');
                    visible.value = false;
                    load();
                }).catch(function (error) {
                    El.ElMessage.error(error.message || '保存失败');
                }).finally(function () {
                    submitting.value = false;
                });
            }

            function choosePackage(row) {
                if (uploadingId.value) {
                    El.ElMessage.warning('安装包上传中，请稍候');
                    return;
                }
                uploadTarget.value = row;
                var input = document.getElementById('app-version-package');
                if (input) {
                    input.value = '';
                    input.click();
                }
            }

            function uploadPackage(row, file) {
                uploadingId.value = row.id;
                var url = '/api/admin/app-versions/' + row.id + '/package?fileName=' + encodeURIComponent(file.name);
                fetch(url, {
                    method: 'POST',
                    headers: {
                        Authorization: 'Bearer ' + api.getToken(),
                        'Content-Type': 'application/octet-stream'
                    },
                    body: file
                }).then(function (res) {
                    return res.json().catch(function () { return null; }).then(function (payload) {
                        if (res.status === 401) {
                            api.setToken('');
                            location.hash = '#/login';
                            throw new Error('登录已过期，请重新登录');
                        }
                        if (!res.ok || !payload || payload.success === false) {
                            throw new Error((payload && payload.message) || ('上传失败（HTTP ' + res.status + '）'));
                        }
                        return payload.data;
                    });
                }).then(function () {
                    El.ElMessage.success('安装包已上传');
                    load();
                }).catch(function (error) {
                    El.ElMessage.error(error.message || '上传失败');
                }).finally(function () {
                    uploadingId.value = null;
                    uploadTarget.value = null;
                });
            }

            function onPackageSelected(event) {
                var file = event.target.files && event.target.files[0];
                event.target.value = '';
                var row = uploadTarget.value;
                if (!file || !row) return;
                if (!/\.(apk|ipa|hap)$/i.test(file.name || '')) {
                    El.ElMessage.warning('仅支持 .apk / .ipa / .hap 安装包');
                    return;
                }
                if (file.size > MAX_PACKAGE_BYTES) {
                    El.ElMessage.warning('安装包不能超过 300MB');
                    return;
                }
                uploadPackage(row, file);
            }

            function publish(row) {
                if (!row.hasPackage && !row.downloadUrl) {
                    El.ElMessage.warning('该版本尚未上传安装包，也没有外链下载地址，无法发布');
                    return;
                }
                El.ElMessageBox.confirm('发布 v' + row.versionName + '（' + platformLabel(row.platform)
                        + ' / ' + (row.channel || 'OFFICIAL') + '）后，同平台同渠道的已发布版本会自动下线。确认发布？',
                    '发布版本', { type: 'warning', confirmButtonText: '发布', cancelButtonText: '取消' })
                    .then(function () { return api.post('/api/admin/app-versions/' + row.id + '/publish'); })
                    .then(function () {
                        El.ElMessage.success('已发布');
                        load();
                    })
                    .catch(function (error) {
                        if (error !== 'cancel') El.ElMessage.error(error.message || '发布失败');
                    });
            }

            function offline(row) {
                El.ElMessageBox.confirm('下线后 App 端将无法再检查到该版本（已下载的安装包不受影响），确认下线 v'
                        + row.versionName + '？', '下线版本', {
                    type: 'warning', confirmButtonText: '下线', cancelButtonText: '取消'
                }).then(function () {
                    return api.post('/api/admin/app-versions/' + row.id + '/offline');
                }).then(function () {
                    El.ElMessage.success('已下线');
                    load();
                }).catch(function (error) {
                    if (error !== 'cancel') El.ElMessage.error(error.message || '下线失败');
                });
            }

            function remove(row) {
                El.ElMessageBox.confirm('删除后记录与已上传安装包不可恢复，确认删除 v' + row.versionName
                        + '（code ' + row.versionCode + '）？', '删除版本', {
                    type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
                }).then(function () {
                    return api.delete('/api/admin/app-versions/' + row.id);
                }).then(function () {
                    El.ElMessage.success('已删除');
                    load();
                }).catch(function (error) {
                    if (error !== 'cancel') El.ElMessage.error(error.message || '删除失败');
                });
            }

            function download(row) {
                fetch('/api/admin/app-versions/' + row.id + '/download', {
                    headers: { Authorization: 'Bearer ' + api.getToken() }
                }).then(function (res) {
                    if (!res.ok) {
                        return res.json().catch(function () { return null; }).then(function (payload) {
                            throw new Error((payload && payload.message) || ('下载失败（HTTP ' + res.status + '）'));
                        });
                    }
                    return res.blob();
                }).then(function (blob) {
                    var url = URL.createObjectURL(blob);
                    var a = document.createElement('a');
                    a.href = url;
                    a.download = row.packageFileName || ('app-' + row.versionName + '-' + row.versionCode);
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                    URL.revokeObjectURL(url);
                }).catch(function (error) {
                    El.ElMessage.error(error.message || '下载失败');
                });
            }

            function actionsOf(row) {
                if (uploadingId.value === row.id) {
                    return [{ key: 'uploading', label: '上传中…', disabled: true }];
                }
                var list = [];
                if (row.status === 'DRAFT') {
                    list.push({ key: 'edit', label: '编辑', run: function () { openEdit(row); } });
                    list.push({
                        key: 'upload',
                        label: row.hasPackage ? '重新上传' : '上传安装包',
                        run: function () { choosePackage(row); }
                    });
                    list.push({
                        key: 'publish', label: '发布', color: 'var(--tk-ok)',
                        run: function () { publish(row); }
                    });
                }
                if (row.status === 'PUBLISHED') {
                    list.push({
                        key: 'offline', label: '下线', color: 'var(--tk-warn)',
                        run: function () { offline(row); }
                    });
                }
                if (row.hasPackage) {
                    list.push({ key: 'download', label: '下载', run: function () { download(row); } });
                }
                if (row.status !== 'PUBLISHED') {
                    list.push({
                        key: 'remove', label: '删除', color: 'var(--tk-bad)',
                        run: function () { remove(row); }
                    });
                }
                return list;
            }

            load();

            return {
                rows: rows, loading: loading, platforms: PLATFORMS,
                filterPlatform: filterPlatform, filterStatus: filterStatus,
                visible: visible, form: form, submitting: submitting, uploadingId: uploadingId,
                load: load, openCreate: openCreate, openEdit: openEdit, submit: submit,
                choosePackage: choosePackage, onPackageSelected: onPackageSelected,
                publish: publish, offline: offline, remove: remove, download: download,
                actionsOf: actionsOf, platformLabel: platformLabel, statusLabel: statusLabel,
                statusBadge: statusBadge, fmtDate: fmtDate, formatSize: formatSize
            };
        }
    };
})();
