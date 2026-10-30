/* 字典管理（管理端）：真实接口 /api/admin/dicts。左侧字典类型、右侧字典项维护。
   编码创建后不可修改；内置字典不可删除；extra 必须是合法 JSON。 */
(function () {
    'use strict';

    window.AdminPages = window.AdminPages || {};

    window.AdminPages.DictsPage = {
        name: 'DictsPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '  <div class="page-h">',
            '    <div><h1>字典管理</h1><div class="sub">系统全量字典：通用字典可维护，成就/成长字典来自业务模块并只读展示</div></div>',
            '    <div class="r"><button class="btn2" @click="exportCsv">导出 CSV</button><button class="btn2" @click="openImport">导入 CSV</button><button class="btn2" @click="refreshCache">刷新缓存</button><button class="btn2" @click="refreshAll">刷新</button></div>',
            '  </div>',

            '  <div style="display:grid;grid-template-columns:460px 1fr;gap:16px;flex:1;min-height:0">',
            '    <div class="lcard" style="display:flex;flex-direction:column;min-height:0">',
            '      <div class="cap">字典类型<span class="cnt" v-if="types.length">共 {{ types.length }} 类</span></div>',
            '      <div style="display:flex;gap:8px;padding:0 16px 12px">',
            '        <div class="ipt2" style="flex:1"><input v-model="typeKeyword" placeholder="编码/名称搜索" @keyup.enter="loadTypes" /></div>',
            '        <button class="c blue" @click="openTypeCreate">新增</button>',
            '      </div>',
            '      <div class="tblw" style="flex:1;min-height:0;overflow:auto">',
            '        <table class="pk">',
            '          <thead><tr><th>名称 / 编码</th><th style="width:64px">项数</th><th style="width:70px">状态</th><th style="width:112px" class="r">操作</th></tr></thead>',
            '          <tbody>',
            '            <tr v-if="!types.length && !loadingTypes"><td colspan="4" style="text-align:center;color:var(--tk-muted);padding:24px 0">暂无字典</td></tr>',
            '            <tr v-for="t in types" :key="t.typeCode" :style="t.typeCode === activeTypeCode ? \'background:var(--tk-subtle)\' : \'\'" style="cursor:pointer" @click="selectType(t)">',
            '              <td>',
            '                <div style="font-weight:600">{{ t.typeName }}<span v-if="t.readonly" class="bdg i" style="margin-left:6px">只读</span><span v-else-if="t.builtin" class="bdg" style="margin-left:6px">内置</span></div>',
            '                <div style="color:var(--tk-muted);font-size:12px">{{ t.typeCode }}</div>',
            '              </td>',
            '              <td style="color:var(--tk-text3)">{{ t.itemCount }}</td>',
            '              <td><span class="bdg" :class="t.status === \'ENABLED\' ? \'g\' : \'a\'">{{ t.status === "ENABLED" ? "启用" : "停用" }}</span></td>',
            '              <td class="r" @click.stop>',
            '                <template v-if="t.readonly"><span style="color:var(--tk-muted)">只读</span></template>',
            '                <template v-else><span class="op" @click="openTypeEdit(t)">编辑</span>',
            '                  <template v-if="!t.builtin"> · <span class="op" style="color:var(--tk-bad)" @click="removeType(t)">删除</span></template></template>',
            '              </td>',
            '            </tr>',
            '          </tbody>',
            '        </table>',
            '      </div>',
            '    </div>',

            '    <div class="lcard" style="display:flex;flex-direction:column;min-height:0">',
            '      <div class="cap">',
            '        <span v-if="activeType">字典项 · {{ activeType.typeName }} <span style="color:var(--tk-muted);font-weight:400">({{ activeType.typeCode }})</span></span>',
            '        <span v-else>字典项（请先选择左侧字典类型）</span>',
            '        <span class="cnt" v-if="activeType">共 {{ items.length }} 项</span>',
            '      </div>',
            '      <div style="display:flex;gap:8px;padding:0 16px 12px;align-items:center">',
            '        <button v-if="activeType && !activeType.readonly" class="c blue" @click="openItemCreate">新增字典项</button>',
            '        <label style="display:flex;align-items:center;gap:6px;color:var(--tk-text3);font-size:13px">',
            '          <input type="checkbox" v-model="includeDisabled" @change="loadItems" /> 显示停用项',
            '        </label>',
            '        <span v-if="activeType && activeType.readonly" class="os-note" style="margin:0;padding:4px 10px">{{ activeType.source === \'ACHIEVEMENT\' ? \'该字典由「成就管理」维护，此处只读；编辑请到「成就管理」\' : \'该字典由对应业务模块维护，此处只读；编辑请到对应业务页面\' }}</span>',
            '      </div>',
            '      <div class="tblw" style="flex:1;min-height:0;overflow:auto">',
            '        <table class="pk">',
            '          <thead><tr><th style="width:190px">编码</th><th style="width:140px">名称</th><th style="width:190px">值</th><th style="width:64px">排序</th><th style="width:70px">状态</th><th>扩展</th><th style="width:96px" class="r">操作</th></tr></thead>',
            '          <tbody>',
            '            <tr v-if="!items.length && activeType && !loadingItems"><td colspan="7" style="text-align:center;color:var(--tk-muted);padding:24px 0">该字典暂无字典项</td></tr>',
            '            <tr v-for="item in items" :key="item.id">',
            '              <td style="color:var(--tk-text3)">{{ item.itemCode }}</td>',
            '              <td>{{ item.itemLabel }}</td>',
            '              <td style="color:var(--tk-text3)">{{ item.itemValue || "-" }}</td>',
            '              <td style="color:var(--tk-text3)">{{ item.sortOrder }}</td>',
            '              <td><span class="bdg" :class="item.status === \'ENABLED\' ? \'g\' : \'a\'">{{ item.status === "ENABLED" ? "启用" : "停用" }}</span></td>',
            '              <td :title="item.extra || \'\'" style="color:var(--tk-muted);font-family:ui-monospace,Menlo,monospace;font-size:12px">{{ item.extra || "-" }}</td>',
            '              <td class="r">',
            '                <template v-if="activeType && activeType.readonly"><span style="color:var(--tk-muted)">只读</span></template>',
            '                <template v-else><span class="op" @click="openItemEdit(item)">编辑</span> · <span class="op" style="color:var(--tk-bad)" @click="removeItem(item)">删除</span></template>',
            '              </td>',
            '            </tr>',
            '          </tbody>',
            '        </table>',
            '      </div>',
            '    </div>',
            '  </div>',

            // 字典类型弹层
            '  <div class="pmask" :class="{ show: typeDialogVisible }" @click.self="typeDialogVisible = false">',
            '    <div class="pcard" v-if="typeForm">',
            '      <div class="ph"><b>{{ typeForm.id ? "编辑字典类型" : "新增字典类型" }}</b><span class="sp"></span><span class="x" @click="typeDialogVisible = false">✕</span></div>',
            '      <div class="fld"><label>字典编码 <em>*</em></label>',
            '        <div class="pin"><input v-model="typeForm.typeCode" :disabled="!!typeForm.id" maxlength="64" placeholder="大写字母开头，如 REPAIR_FAULT_TYPE" /></div>',
            '        <div v-if="typeForm.id" style="color:var(--tk-muted);font-size:12px;margin-top:4px">编码创建后不可修改</div>',
            '      </div>',
            '      <div class="fld"><label>字典名称 <em>*</em></label><div class="pin"><input v-model="typeForm.typeName" maxlength="64" /></div></div>',
            '      <div class="fld"><label>备注</label><div class="pin"><input v-model="typeForm.remark" maxlength="255" /></div></div>',
            '      <div class="fld"><label>状态</label>',
            '        <div class="pin"><select v-model="typeForm.status" style="width:100%"><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></div>',
            '      </div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="typeDialogVisible = false">取消</button>',
            '        <button class="c blue" :disabled="submitting" @click="submitType">{{ submitting ? "提交中…" : "保存" }}</button></div>',
            '    </div>',
            '  </div>',

            // 字典项弹层
            '  <div class="pmask" :class="{ show: itemDialogVisible }" @click.self="itemDialogVisible = false">',
            '    <div class="pcard" v-if="itemForm">',
            '      <div class="ph"><b>{{ itemForm.id ? "编辑字典项" : "新增字典项" }}</b><span class="no">{{ itemForm.typeCode }}</span><span class="sp"></span><span class="x" @click="itemDialogVisible = false">✕</span></div>',
            '      <div class="fld"><label>字典项编码 <em>*</em></label>',
            '        <div class="pin"><input v-model="itemForm.itemCode" :disabled="!!itemForm.id" maxlength="64" placeholder="如 POWER_OUTAGE" /></div>',
            '      </div>',
            '      <div class="fld"><label>显示名称 <em>*</em></label><div class="pin"><input v-model="itemForm.itemLabel" maxlength="128" /></div></div>',
            '      <div class="fld"><label>字典值</label><div class="pin"><input v-model="itemForm.itemValue" maxlength="255" placeholder="留空则与显示名称一致" /></div></div>',
            '      <div style="display:flex;gap:12px">',
            '        <div class="fld" style="flex:1"><label>排序</label><div class="pin"><input type="number" v-model.number="itemForm.sortOrder" /></div></div>',
            '        <div class="fld" style="flex:1"><label>状态</label>',
            '          <div class="pin"><select v-model="itemForm.status" style="width:100%"><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></div>',
            '        </div>',
            '      </div>',
            '      <div class="fld"><label>扩展 JSON</label><textarea class="pta" v-model="itemForm.extra" placeholder=\'留空或合法 JSON，如 {"emergency":true}\'></textarea></div>',
            '      <div class="fld"><label>备注</label><div class="pin"><input v-model="itemForm.remark" maxlength="255" /></div></div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="itemDialogVisible = false">取消</button>',
            '        <button class="c blue" :disabled="submitting" @click="submitItem">{{ submitting ? "提交中…" : "保存" }}</button></div>',
            '    </div>',
            '  </div>',
            // 导入弹层
            '  <div class="pmask" :class="{ show: importDialogVisible }" @click.self="importDialogVisible = false">',
            '    <div class="pcard">',
            '      <div class="ph"><b>导入字典 CSV</b><span class="sp"></span><span class="x" @click="importDialogVisible = false">✕</span></div>',
            '      <div class="fld"><label>CSV 文件 <em>*</em></label>',
            '        <input type="file" accept=".csv,text/csv" @change="onImportFile" />',
            '        <div style="color:var(--tk-muted);font-size:12px;margin-top:6px">列：typeCode,typeName,itemCode,itemLabel,itemValue,sortOrder,status,extra,remark；可先导出模板。</div>',
            '      </div>',
            '      <div class="fld"><label><input type="checkbox" v-model="importOverwrite" /> 覆盖已存在的字典项（否则跳过）</label></div>',
            '      <div v-if="importResult" class="fld" style="font-size:12px;line-height:1.8">',
            '        <div>共处理 {{ importResult.total }} 行：新增 {{ importResult.created }}，更新 {{ importResult.updated }}，跳过 {{ importResult.skipped }}</div>',
            '        <div v-if="importResult.errors && importResult.errors.length" style="color:var(--tk-bad);max-height:160px;overflow:auto;margin-top:6px">',
            '          <div v-for="(e, idx) in importResult.errors" :key="idx">{{ e }}</div>',
            '        </div>',
            '      </div>',
            '      <div class="pbtn"><span class="sp"></span><button class="c ghost" @click="importDialogVisible = false">关闭</button>',
            '        <button class="c blue" :disabled="importing || !importFile" @click="submitImport">{{ importing ? "导入中…" : "开始导入" }}</button></div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var types = Vue.ref([]);
            var items = Vue.ref([]);
            var loadingTypes = Vue.ref(false);
            var loadingItems = Vue.ref(false);
            var typeKeyword = Vue.ref('');
            var includeDisabled = Vue.ref(true);
            var activeTypeCode = Vue.ref(null);
            var activeType = Vue.ref(null);

            var typeDialogVisible = Vue.ref(false);
            var typeForm = Vue.ref(null);
            var itemDialogVisible = Vue.ref(false);
            var itemForm = Vue.ref(null);
            var submitting = Vue.ref(false);
            var importDialogVisible = Vue.ref(false);
            var importFile = Vue.ref(null);
            var importOverwrite = Vue.ref(false);
            var importing = Vue.ref(false);
            var importResult = Vue.ref(null);

            function exportCsv() {
                var token = window.AdminApi.getToken();
                fetch('/api/admin/dicts/export', { headers: token ? { Authorization: 'Bearer ' + token } : {} })
                    .then(function (res) {
                        if (!res.ok) { throw new Error('导出失败（HTTP ' + res.status + '）'); }
                        return res.blob();
                    })
                    .then(function (blob) {
                        var url = URL.createObjectURL(blob);
                        var link = document.createElement('a');
                        link.href = url;
                        link.download = 'dicts-' + new Date().toISOString().slice(0, 10) + '.csv';
                        document.body.appendChild(link);
                        link.click();
                        document.body.removeChild(link);
                        setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }

            function openImport() {
                importFile.value = null;
                importOverwrite.value = false;
                importResult.value = null;
                importDialogVisible.value = true;
            }

            function onImportFile(event) {
                var files = event.target.files || [];
                importFile.value = files.length ? files[0] : null;
                importResult.value = null;
            }

            function submitImport() {
                if (!importFile.value) { El.ElMessage.warning('请选择 CSV 文件'); return; }
                importing.value = true;
                var url = '/api/admin/dicts/import?overwrite=' + (importOverwrite.value ? 'true' : 'false');
                api.upload(url, importFile.value)
                    .then(function (data) {
                        importResult.value = data;
                        El.ElMessage.success('导入完成：新增 ' + data.created + '，更新 ' + data.updated + '，跳过 ' + data.skipped);
                        if (data.errors && data.errors.length) { El.ElMessage.warning('有 ' + data.errors.length + ' 行未导入，见结果'); }
                        return loadTypes();
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { importing.value = false; });
            }

            function refreshCache() {
                api.post('/api/admin/dicts/cache/refresh')
                    .then(function () { El.ElMessage.success('字典缓存已刷新'); })
                    .catch(function (error) { El.ElMessage.error(error.message); });
            }

            function loadTypes() {
                loadingTypes.value = true;
                return api.get('/api/admin/dicts/types' + (typeKeyword.value ? '?keyword=' + encodeURIComponent(typeKeyword.value) : ''))
                    .then(function (data) {
                        types.value = data || [];
                        if (activeTypeCode.value) {
                            var current = types.value.find(function (t) { return t.typeCode === activeTypeCode.value; });
                            if (current) { activeType.value = current; }
                        }
                        if (!activeType.value && types.value.length) {
                            selectType(types.value[0]);
                        }
                    })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { loadingTypes.value = false; });
            }

            function selectType(type) {
                activeTypeCode.value = type.typeCode;
                activeType.value = type;
                loadItems();
            }

            function loadItems() {
                if (!activeType.value) { items.value = []; return; }
                loadingItems.value = true;
                api.get('/api/admin/dicts/types/' + encodeURIComponent(activeType.value.typeCode)
                        + '/items?includeDisabled=' + (includeDisabled.value ? 'true' : 'false'))
                    .then(function (data) { items.value = data || []; })
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { loadingItems.value = false; });
            }

            function refreshAll() {
                loadTypes().then(loadItems);
            }

            function openTypeCreate() {
                typeForm.value = { id: null, typeCode: '', typeName: '', remark: '', status: 'ENABLED' };
                typeDialogVisible.value = true;
            }

            function openTypeEdit(type) {
                if (type.readonly) { El.ElMessage.info('该字典由业务模块维护，只读'); return; }
                typeForm.value = { id: type.id, typeCode: type.typeCode, typeName: type.typeName,
                    remark: type.remark || '', status: type.status };
                typeDialogVisible.value = true;
            }

            function submitType() {
                var form = typeForm.value;
                if (!form.typeCode || !form.typeName) {
                    El.ElMessage.warning('字典编码和名称必填');
                    return;
                }
                submitting.value = true;
                var payload = { typeCode: form.typeCode, typeName: form.typeName, remark: form.remark, status: form.status };
                var promise = form.id
                    ? api.put('/api/admin/dicts/types/' + form.id, payload)
                    : api.post('/api/admin/dicts/types', payload);
                promise.then(function () {
                    El.ElMessage.success('已保存');
                    typeDialogVisible.value = false;
                    return loadTypes();
                }).catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { submitting.value = false; });
            }

            function removeType(type) {
                if (type.readonly) { El.ElMessage.info('该字典由业务模块维护，只读'); return; }
                El.ElMessageBox.confirm('删除字典「' + type.typeName + '」会同时删除其下全部字典项，确认继续？', '删除字典类型', {
                    type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
                }).then(function () {
                    return api.delete('/api/admin/dicts/types/' + type.id);
                }).then(function () {
                    El.ElMessage.success('已删除');
                    if (activeTypeCode.value === type.typeCode) { activeType.value = null; activeTypeCode.value = null; items.value = []; }
                    return loadTypes();
                }).catch(function (error) {
                    if (error !== 'cancel') { El.ElMessage.error(error.message || String(error)); }
                });
            }

            function openItemCreate() {
                if (activeType.value && activeType.value.readonly) { El.ElMessage.info('该字典由业务模块维护，只读'); return; }
                itemForm.value = { id: null, typeCode: activeType.value.typeCode, itemCode: '', itemLabel: '',
                    itemValue: '', sortOrder: 0, status: 'ENABLED', extra: '', remark: '' };
                itemDialogVisible.value = true;
            }

            function openItemEdit(item) {
                if (activeType.value && activeType.value.readonly) { El.ElMessage.info('该字典由业务模块维护，只读'); return; }
                itemForm.value = { id: item.id, typeCode: item.typeCode, itemCode: item.itemCode,
                    itemLabel: item.itemLabel, itemValue: item.itemValue || '', sortOrder: item.sortOrder || 0,
                    status: item.status, extra: item.extra || '', remark: item.remark || '' };
                itemDialogVisible.value = true;
            }

            function submitItem() {
                var form = itemForm.value;
                if (!form.itemCode || !form.itemLabel) {
                    El.ElMessage.warning('字典项编码和名称必填');
                    return;
                }
                if (form.extra) {
                    try { JSON.parse(form.extra); } catch (e) {
                        El.ElMessage.warning('扩展字段不是合法 JSON');
                        return;
                    }
                }
                submitting.value = true;
                var payload = {
                    typeCode: form.typeCode, itemCode: form.itemCode, itemLabel: form.itemLabel,
                    itemValue: form.itemValue, sortOrder: form.sortOrder, status: form.status,
                    extra: form.extra || null, remark: form.remark
                };
                var promise = form.id
                    ? api.put('/api/admin/dicts/items/' + form.id, payload)
                    : api.post('/api/admin/dicts/items', payload);
                promise.then(function () {
                    El.ElMessage.success('已保存');
                    itemDialogVisible.value = false;
                    return loadItems();
                }).then(loadTypes)
                    .catch(function (error) { El.ElMessage.error(error.message); })
                    .finally(function () { submitting.value = false; });
            }

            function removeItem(item) {
                if (activeType.value && activeType.value.readonly) { El.ElMessage.info('该字典由业务模块维护，只读'); return; }
                El.ElMessageBox.confirm('确认删除字典项「' + item.itemLabel + '」？', '删除字典项', {
                    type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
                }).then(function () {
                    return api.delete('/api/admin/dicts/items/' + item.id);
                }).then(function () {
                    El.ElMessage.success('已删除');
                    return loadItems();
                }).then(loadTypes)
                    .catch(function (error) {
                        if (error !== 'cancel') { El.ElMessage.error(error.message || String(error)); }
                    });
            }

            loadTypes();

            return {
                types: types, items: items, loadingTypes: loadingTypes, loadingItems: loadingItems,
                typeKeyword: typeKeyword, includeDisabled: includeDisabled,
                activeTypeCode: activeTypeCode, activeType: activeType,
                typeDialogVisible: typeDialogVisible, typeForm: typeForm,
                itemDialogVisible: itemDialogVisible, itemForm: itemForm, submitting: submitting,
                loadTypes: loadTypes, loadItems: loadItems, refreshAll: refreshAll, selectType: selectType,
                exportCsv: exportCsv, openImport: openImport, onImportFile: onImportFile, submitImport: submitImport,
                refreshCache: refreshCache, importDialogVisible: importDialogVisible, importFile: importFile,
                importOverwrite: importOverwrite, importing: importing, importResult: importResult,
                openTypeCreate: openTypeCreate, openTypeEdit: openTypeEdit, submitType: submitType, removeType: removeType,
                openItemCreate: openItemCreate, openItemEdit: openItemEdit, submitItem: submitItem, removeItem: removeItem
            };
        }
    };
})();
