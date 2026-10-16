/* 服务目录管理页：分类 + 服务项 CRUD、上架/下架、动态表单 schema 配置。
   对接 /api/admin/catalog/**；formSchema 为 JSON 数组，驱动小程序端动态申请表单。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};
    var V = window.Vue, E = window.ElementPlus, api = window.AdminApi;
    window.AdminPages.ServiceCatalogPage = {
        name: 'ServiceCatalogPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '<div class="page-h"><div><h1>服务目录</h1><div class="sub">服务分类与服务项配置 · 动态申请表单由 formSchema 驱动，停用仅阻止新申请</div></div>',
            '<div class="r"><button class="btn2" @click="openCategoryCreate">+ 新建分类</button>',
            '<button class="btn2 pri" @click="openItemCreate">+ 新建服务项</button></div></div>',

            '<div class="duo" style="flex:1;min-height:0">',
            // 左侧：分类树
            '<aside class="lcard" style="width:240px;flex:none;gap:12px">',
            '<div class="cap" style="font-size:15px">服务分类<span class="cnt">{{ categories.length }} 个</span></div>',
            '<div style="display:flex;flex-direction:column;gap:4px;overflow:auto">',
            '<div class="node" :class="{on:!activeCategoryId}" @click="selectCategory(null)">',
            '<span class="dot" style="width:6px;height:6px;border-radius:999px;margin-top:6px;flex:none;background:var(--tk-info)"></span>',
            '<span style="font-size:13px;color:var(--tk-text)">全部服务项</span>',
            '<span style="margin-left:auto;font-size:11px;color:var(--tk-text3)">{{ totalCount }}</span></div>',
            '<div v-for="c in categories" :key="c.categoryId" class="node" :class="{on:activeCategoryId===c.categoryId}" @click="selectCategory(c.categoryId)">',
            '<span class="dot" :style="{width:\'6px\',height:\'6px\',borderRadius:\'999px\',marginTop:\'6px\',flex:\'none\',background:c.status===\'ENABLED\'?\'var(--tk-ok)\':\'var(--tk-muted)\'}"></span>',
            '<span style="display:flex;flex-direction:column;min-width:0"><b style="font-size:13px;color:var(--tk-text)">{{ c.categoryName }}</b>',
            '<span style="font-size:11px;color:var(--tk-text3)">{{ c.categoryCode }}</span></span>',
            '<span style="margin-left:auto;font-size:11px;color:var(--tk-text3)">{{ (c.items||[]).length }}</span></div>',
            '<div v-if="!categories.length" style="font-size:12px;color:var(--tk-muted);padding:12px 0;text-align:center">暂无分类，先新建一个</div>',
            '</div></aside>',

            // 右侧：服务项表格
            '<section style="flex:1;display:flex;flex-direction:column;gap:16px;min-width:0;min-height:0" v-loading="loading">',
            '<div class="lcard" style="gap:12px;flex:1;min-height:0">',
            '<div class="cap" style="font-size:15px">服务项<span class="cnt">{{ activeCategoryId ? categoryName(activeCategoryId) : "全部分类" }}</span>',
            '<span class="r"><span class="srch"><input v-model="keyword" placeholder="搜索名称 / 编码 / 简介" /></span></span></div>',
            '<div class="tblw" style="flex:1;min-height:0;overflow:auto"><table class="pk">',
            '<colgroup><col style="width:280px"><col style="width:120px"><col style="width:90px"><col style="width:90px"><col style="width:80px"><col style="width:150px"><col style="width:150px"></colgroup>',
            '<thead><tr><th>服务项</th><th>分类</th><th>状态</th><th>SLA</th><th>排序</th><th>更新时间</th><th class="r">操作</th></tr></thead>',
            '<tbody>',
            '<tr v-if="!filteredItems.length"><td colspan="7" style="text-align:center;color:var(--tk-muted);padding:24px 0">暂无服务项</td></tr>',
            '<tr v-for="it in filteredItems" :key="it.itemId">',
            '<td><div style="display:flex;flex-direction:column;gap:2px"><b style="font-size:13px;color:var(--tk-text)">{{ it.itemName }}</b>',
            '<span style="font-size:11px;color:var(--tk-text3)">{{ it.itemCode }}<template v-if="it.summary"> · {{ it.summary }}</template></span></div></td>',
            '<td style="color:var(--tk-text2)">{{ it.categoryName }}</td>',
            '<td><span class="bdg" :class="it.status===\'ENABLED\'?\'g\':\'i\'">{{ it.status===\'ENABLED\'?\'已上线\':\'已停用\' }}</span></td>',
            '<td style="color:var(--tk-text3)">{{ it.slaHours ? it.slaHours + "h" : "-" }}</td>',
            '<td style="color:var(--tk-text3)">{{ it.sortOrder }}</td>',
            '<td style="color:var(--tk-text3)">{{ fmt(it.updatedAt) }}</td>',
            '<td class="r"><span class="op" @click="openItemEdit(it)">编辑</span>',
            '<span class="op" @click="toggleStatus(it)">{{ it.status===\'ENABLED\'?\'停用\':\'上架\' }}</span>',
            '<span class="op r" @click="removeItem(it)">删除</span></td>',
            '</tr></tbody></table></div></div>',
            '</section>',
            '</div>',

            // 服务项弹窗
            '<div class="pmask" :class="{show:itemDialog}" @click.self="itemDialog=false">',
            '<div class="pcard w720">',
            '<div class="ph"><b>{{ itemEditId ? "编辑服务项" : "新建服务项" }}</b><span class="sp"></span><span class="x" @click="itemDialog=false">✕</span></div>',
            '<div class="frow">',
            '<div class="fld"><label>服务项编码 <em>*</em></label><div class="pin"><input v-model="itemForm.itemCode" maxlength="64" placeholder="如 REPAIR_DORM" /></div></div>',
            '<div class="fld"><label>服务项名称 <em>*</em></label><div class="pin"><input v-model="itemForm.itemName" maxlength="128" placeholder="如 宿舍报修" /></div></div>',
            '</div>',
            '<div class="frow">',
            '<div class="fld"><label>所属分类 <em>*</em></label><div class="pin"><select v-model="itemForm.categoryId" style="width:100%">',
            '<option :value="null" disabled>请选择分类</option>',
            '<option v-for="c in categories" :key="c.categoryId" :value="c.categoryId">{{ c.categoryName }}</option>',
            '</select></div></div>',
            '<div class="fld"><label>图标标识</label><div class="pin"><input v-model="itemForm.icon" maxlength="128" placeholder="可选，如 fix-water" /></div></div>',
            '</div>',
            '<div class="fld"><label>一句话简介</label><div class="pin"><input v-model="itemForm.summary" maxlength="512" placeholder="列表与详情页展示" /></div></div>',
            '<div class="fld"><label>办事指南</label><textarea class="pta" v-model="itemForm.description" placeholder="可选：办理说明、材料要求等"></textarea></div>',
            '<div class="frow">',
            '<div class="fld"><label>承诺办结时限（小时）</label><div class="pin"><input type="number" v-model.number="itemForm.slaHours" min="1" placeholder="如 24" /></div></div>',
            '<div class="fld"><label>排序值</label><div class="pin"><input type="number" v-model.number="itemForm.sortOrder" placeholder="越小越靠前" /></div></div>',
            '</div>',
            '<div class="fld"><label>动态表单 Schema（JSON 数组）</label>',
            '<textarea class="ta2" style="font-family:ui-monospace,SFMono-Regular,Menlo,monospace;min-height:120px" v-model="itemForm.formSchemaText" placeholder=\'[{"key":"reason","label":"报修事由","type":"textarea","required":true,"max":500}]\'></textarea>',
            '<div style="font-size:11px;color:var(--tk-text3);margin-top:4px">元素结构 {key, label, type(input|textarea|select|date|number|image), required, options, max, maxCount, placeholder}；留空表示无动态表单</div></div>',
            '<div class="pbtn"><span class="sp"></span><button class="c ghost" @click="itemDialog=false">取消</button><button class="c blue" @click="saveItem">保存</button></div>',
            '</div></div>',

            // 分类弹窗
            '<div class="pmask" :class="{show:categoryDialog}" @click.self="categoryDialog=false">',
            '<div class="pcard w440">',
            '<div class="ph"><b>新建分类</b><span class="sp"></span><span class="x" @click="categoryDialog=false">✕</span></div>',
            '<div class="fld"><label>分类编码 <em>*</em></label><div class="pin"><input v-model="categoryForm.categoryCode" maxlength="64" placeholder="如 REPAIR" /></div></div>',
            '<div class="fld"><label>分类名称 <em>*</em></label><div class="pin"><input v-model="categoryForm.categoryName" maxlength="128" placeholder="如 后勤报修" /></div></div>',
            '<div class="fld"><label>图标标识</label><div class="pin"><input v-model="categoryForm.icon" maxlength="128" placeholder="可选" /></div></div>',
            '<div class="fld"><label>分类描述</label><div class="pin"><input v-model="categoryForm.description" maxlength="512" /></div></div>',
            '<div class="fld"><label>排序值</label><div class="pin"><input type="number" v-model.number="categoryForm.sortOrder" placeholder="越小越靠前" /></div></div>',
            '<div class="pbtn"><span class="sp"></span><button class="c ghost" @click="categoryDialog=false">取消</button><button class="c blue" @click="saveCategory">保存</button></div>',
            '</div></div>',
            '</div>'
        ].join(''),
        setup: function () {
            var categories = V.ref([]), items = V.ref([]), loading = V.ref(false);
            var activeCategoryId = V.ref(null), keyword = V.ref('');
            var itemDialog = V.ref(false), itemEditId = V.ref(null);
            var itemForm = V.reactive({ categoryId: null, itemCode: '', itemName: '', summary: '', description: '', icon: '', slaHours: null, sortOrder: null, formSchemaText: '' });
            var categoryDialog = V.ref(false);
            var categoryForm = V.reactive({ categoryCode: '', categoryName: '', icon: '', description: '', sortOrder: null });

            var totalCount = V.computed(function () {
                return categories.value.reduce(function (sum, c) { return sum + (c.items || []).length; }, 0);
            });
            var filteredItems = V.computed(function () {
                var kw = keyword.value.trim().toLowerCase();
                if (!kw) return items.value;
                return items.value.filter(function (it) {
                    return [it.itemName, it.itemCode, it.summary].some(function (f) {
                        return f && f.toLowerCase().indexOf(kw) >= 0;
                    });
                });
            });

            function load() {
                loading.value = true;
                api.get('/api/admin/catalog/categories').then(function (x) {
                    categories.value = x || [];
                }).catch(function (e) { E.ElMessage.error(e.message); }).finally(function () { loading.value = false; });
            }
            function loadItems() {
                loading.value = true;
                var path = activeCategoryId.value ? '/api/admin/catalog/items?categoryId=' + activeCategoryId.value : '/api/admin/catalog/items';
                api.get(path).then(function (x) { items.value = x || []; })
                    .catch(function (e) { E.ElMessage.error(e.message); })
                    .finally(function () { loading.value = false; });
            }
            function selectCategory(id) {
                activeCategoryId.value = id;
                loadItems();
            }
            function categoryName(id) {
                var c = categories.value.find(function (x) { return x.categoryId === id; });
                return c ? c.categoryName : '';
            }
            function fmt(x) { return api.fmtDateTime(x); }

            function openItemCreate() {
                if (!categories.value.length) { E.ElMessage.warning('请先创建服务分类'); return; }
                itemEditId.value = null;
                Object.assign(itemForm, { categoryId: activeCategoryId.value || categories.value[0].categoryId, itemCode: '', itemName: '', summary: '', description: '', icon: '', slaHours: null, sortOrder: null, formSchemaText: '' });
                itemDialog.value = true;
            }
            function openItemEdit(it) {
                itemEditId.value = it.itemId;
                Object.assign(itemForm, {
                    categoryId: it.categoryId, itemCode: it.itemCode, itemName: it.itemName,
                    summary: it.summary || '', description: it.description || '', icon: it.icon || '',
                    slaHours: it.slaHours, sortOrder: it.sortOrder,
                    formSchemaText: it.formSchema && it.formSchema.length ? JSON.stringify(it.formSchema, null, 2) : ''
                });
                itemDialog.value = true;
            }
            function parseSchema() {
                var text = itemForm.formSchemaText.trim();
                if (!text) return null;
                var node;
                try { node = JSON.parse(text); } catch (e) {
                    throw new Error('表单 Schema 不是合法 JSON：' + e.message);
                }
                if (!Array.isArray(node)) throw new Error('表单 Schema 必须是 JSON 数组');
                node.forEach(function (f, i) {
                    if (!f || typeof f !== 'object' || !f.key || !f.label || !f.type) {
                        throw new Error('第 ' + (i + 1) + ' 个字段缺少 key/label/type');
                    }
                });
                return node;
            }
            function saveItem() {
                if (!itemForm.itemCode.trim()) { E.ElMessage.warning('请填写服务项编码'); return; }
                if (!itemForm.itemName.trim()) { E.ElMessage.warning('请填写服务项名称'); return; }
                if (!itemForm.categoryId) { E.ElMessage.warning('请选择所属分类'); return; }
                var schema;
                try { schema = parseSchema(); } catch (e) { E.ElMessage.error(e.message); return; }
                var payload = {
                    categoryId: itemForm.categoryId,
                    itemCode: itemForm.itemCode.trim(),
                    itemName: itemForm.itemName.trim(),
                    summary: itemForm.summary.trim() || null,
                    description: itemForm.description.trim() || null,
                    icon: itemForm.icon.trim() || null,
                    formSchema: schema,
                    slaHours: itemForm.slaHours || null,
                    sortOrder: itemForm.sortOrder
                };
                var req = itemEditId.value
                    ? api.post('/api/admin/catalog/items/update', Object.assign({ itemId: itemEditId.value }, payload))
                    : api.post('/api/admin/catalog/items', payload);
                req.then(function () {
                    itemDialog.value = false;
                    E.ElMessage.success('已保存');
                    load(); loadItems();
                }).catch(function (e) { E.ElMessage.error(e.message); });
            }
            function toggleStatus(it) {
                var next = it.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
                api.post('/api/admin/catalog/items/' + it.itemId + '/status', { status: next })
                    .then(function () {
                        E.ElMessage.success(next === 'ENABLED' ? '已上架' : '已停用');
                        load(); loadItems();
                    })
                    .catch(function (e) { E.ElMessage.error(e.message); });
            }
            function removeItem(it) {
                E.ElMessageBox.confirm('删除服务项「' + it.itemName + '」？已有申请记录不受影响，但小程序将不再展示。', '确认删除', { type: 'warning' })
                    .then(function () { return api.delete('/api/admin/catalog/items/' + it.itemId); })
                    .then(function () { E.ElMessage.success('已删除'); load(); loadItems(); })
                    .catch(function (e) { if (e && e.message) E.ElMessage.error(e.message); });
            }

            function openCategoryCreate() {
                Object.assign(categoryForm, { categoryCode: '', categoryName: '', icon: '', description: '', sortOrder: null });
                categoryDialog.value = true;
            }
            function saveCategory() {
                if (!categoryForm.categoryCode.trim()) { E.ElMessage.warning('请填写分类编码'); return; }
                if (!categoryForm.categoryName.trim()) { E.ElMessage.warning('请填写分类名称'); return; }
                api.post('/api/admin/catalog/categories', {
                    categoryCode: categoryForm.categoryCode.trim(),
                    categoryName: categoryForm.categoryName.trim(),
                    icon: categoryForm.icon.trim() || null,
                    description: categoryForm.description.trim() || null,
                    sortOrder: categoryForm.sortOrder
                }).then(function () {
                    categoryDialog.value = false;
                    E.ElMessage.success('分类已创建');
                    load();
                }).catch(function (e) { E.ElMessage.error(e.message); });
            }

            load(); loadItems();
            return {
                categories: categories, items: items, loading: loading, activeCategoryId: activeCategoryId, keyword: keyword,
                totalCount: totalCount, filteredItems: filteredItems,
                itemDialog: itemDialog, itemEditId: itemEditId, itemForm: itemForm,
                categoryDialog: categoryDialog, categoryForm: categoryForm,
                selectCategory: selectCategory, categoryName: categoryName, fmt: fmt,
                openItemCreate: openItemCreate, openItemEdit: openItemEdit, saveItem: saveItem,
                toggleStatus: toggleStatus, removeItem: removeItem,
                openCategoryCreate: openCategoryCreate, saveCategory: saveCategory
            };
        }
    };
})();
