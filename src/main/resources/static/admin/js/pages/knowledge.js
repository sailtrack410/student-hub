(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};
    var V = window.Vue, E = window.ElementPlus, api = window.AdminApi;
    window.AdminPages.KnowledgeBasePage = {
        name: 'KnowledgeBasePage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;flex:1;min-height:0">',
            '<div class="page-h"><div><h1>知识库</h1><div class="sub">校园服务知识文档与向量索引 · 上传后自动解析、分块并向量化</div></div></div>',
            '<div class="duo" style="flex:1;min-height:0">',
            '<aside class="lcard" style="width:300px;flex:none;gap:12px">',
            '<div class="cap" style="font-size:15px">知识库<span class="r"><span class="op" style="display:inline-flex;align-items:center;justify-content:center;width:26px;height:26px;border-radius:8px;background:var(--tk-info-bg);cursor:pointer" @click="openCreate" title="新建知识库"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--tk-info)" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg></span></span></div>',
            '<div style="display:flex;flex-direction:column;gap:6px;overflow:auto">',
            '<div v-for="b in bases" :key="b.id" class="node" :class="{on:b.id===selectedId}" style="align-items:flex-start;gap:10px" @click="select(b.id)">',
            '<span class="dot" :style="{width:\'6px\',height:\'6px\',borderRadius:\'999px\',marginTop:\'6px\',flex:\'none\',background:b.enabled?\'var(--tk-ok)\':\'var(--tk-muted)\'}"></span>',
            '<span style="display:flex;flex-direction:column;gap:1px;min-width:0"><b style="font-size:13px;color:var(--tk-text)">{{ b.name }}</b>',
            '<span style="font-size:11px;color:var(--tk-text3)">{{ b.documentCount || 0 }} 文档 · {{ b.chunkCount || 0 }} 分块</span></span></div>',
            '<div v-if="!bases.length" style="font-size:12px;color:var(--tk-muted);padding:12px 0;text-align:center">创建第一个知识库</div>',
            '</div></aside>',

            '<section style="flex:1;display:flex;flex-direction:column;gap:16px;min-width:0;min-height:0" v-loading="loading">',
            '<template v-if="current">',
            '<div class="lcard" style="gap:14px">',
            '<div class="cap" style="font-size:15px"><span style="width:38px;height:38px;border-radius:10px;background:var(--tk-info-bg);display:grid;place-items:center;font-size:15px;font-weight:650;color:var(--tk-info)">知</span>',
            '<span style="display:flex;flex-direction:column"><b style="font-size:15px">{{ current.name }}</b><span style="font-size:11px;color:var(--tk-text3);font-weight:400">{{ current.description || \'未填写描述\' }} · {{ current.visibility === \'PUBLIC\' ? \'公开\' : \'内部\' }}</span></span>',
            '<span class="r" style="gap:10px"><span class="switch" :class="{off:!current.enabled}" @click="current.enabled=!current.enabled;saveEnabled()"><i></i></span>',
            '<button class="btn2 sm" style="height:30px" @click="editBase">编辑</button>',
            '<button class="btn2 sm" style="height:30px;color:var(--tk-bad)" @click="removeBase">删除</button></span></div>',
            '<div style="display:grid;grid-template-columns:repeat(4,1fr);gap:12px">',
            '<div style="background:var(--tk-subtle);border-radius:10px;padding:12px 14px"><div style="font-size:12px;color:var(--tk-text3)">分块大小</div><div style="font-size:16px;font-weight:650;color:var(--tk-text);margin-top:2px">{{ current.chunkSize }}</div></div>',
            '<div style="background:var(--tk-subtle);border-radius:10px;padding:12px 14px"><div style="font-size:12px;color:var(--tk-text3)">重叠字符</div><div style="font-size:16px;font-weight:650;color:var(--tk-text);margin-top:2px">{{ current.chunkOverlap }}</div></div>',
            '<div style="background:var(--tk-subtle);border-radius:10px;padding:12px 14px"><div style="font-size:12px;color:var(--tk-text3)">召回 Top K</div><div style="font-size:16px;font-weight:650;color:var(--tk-text);margin-top:2px">{{ current.topK }}</div></div>',
            '<div style="background:var(--tk-subtle);border-radius:10px;padding:12px 14px"><div style="font-size:12px;color:var(--tk-text3)">相似度阈值</div><div style="font-size:16px;font-weight:650;color:var(--tk-text);margin-top:2px">{{ current.scoreThreshold }}</div></div>',
            '</div></div>',

            '<div class="lcard" style="gap:12px;flex:1;min-height:0">',
            '<div class="cap" style="font-size:15px">文档与索引<span class="cnt">支持 PDF、DOCX、TXT、MD</span>',
            '<span class="r"><el-upload :show-file-list="false" :http-request="upload"><button class="btn2 sm pri" style="height:32px" :disabled="uploading">{{ uploading ? "上传中…" : "+ 上传文档" }}</button></el-upload></span></div>',
            '<div class="tblw" style="flex:1;min-height:0;overflow:auto"><table class="pk">',
            '<colgroup><col style="width:220px"><col style="width:100px"><col style="width:80px"><col><col style="width:170px"><col style="width:130px"></colgroup>',
            '<thead><tr><th>文件名</th><th>状态</th><th>分块</th><th>错误信息</th><th>索引时间</th><th class="r">操作</th></tr></thead>',
            '<tbody>',
            '<tr v-if="!documents.length"><td colspan="6" style="text-align:center;color:var(--tk-muted);padding:24px 0">暂无文档</td></tr>',
            '<tr v-for="d in documents" :key="d.id">',
            '<td style="color:var(--tk-text)">{{ d.fileName }}</td>',
            '<td><span class="bdg" :class="statusTone(d.status)">{{ statusText(d.status) }}</span></td>',
            '<td>{{ d.chunkCount }}</td>',
            '<td style="color:var(--tk-bad)">{{ d.errorMessage || "-" }}</td>',
            '<td style="color:var(--tk-text3)">{{ fmt(d.indexedAt) }}</td>',
            '<td class="r"><span class="op" @click="reindex(d)">重建</span> · <span class="op r" @click="removeDocument(d)">删除</span></td>',
            '</tr></tbody></table></div></div>',

            '<div class="lcard" style="gap:12px">',
            '<div class="cap" style="font-size:15px">检索测试<span class="cnt">查看召回片段、分数和最终回答，便于调节分块与阈值</span></div>',
            '<textarea class="ta2" v-model="question" maxlength="2000" placeholder="输入问题，例如：图书馆周末几点闭馆？"></textarea>',
            '<div style="display:flex;align-items:center;gap:12px">',
            '<button class="btn2 sm pri" style="height:32px" :disabled="testing" @click="test">{{ testing ? "检索中…" : "开始检索与回答" }}</button>',
            '<span v-if="result" style="font-size:12px;color:var(--tk-muted)">召回 {{ result.hits.length }} 条 · 检索 {{ result.retrievalMs }}ms · 生成 {{ result.generationMs }}ms</span>',
            '</div>',
            '<div v-if="result && result.answer" class="psum" style="background:var(--tk-ok-bg)"><div class="l1" style="color:var(--tk-ok)">{{ result.answer }}</div></div>',
            '<div v-if="result" style="display:flex;flex-direction:column;gap:8px;max-height:260px;overflow:auto">',
            '<div v-for="(h,i) in result.hits" :key="h.chunkId" class="psum" style="gap:3px;padding:10px 12px">',
            '<div class="l1" style="display:flex;align-items:center;gap:8px"><b style="font-size:12px">#{{ i+1 }}</b>{{ h.fileName }}<span style="margin-left:auto;font-size:12px;color:var(--tk-info)">分数 {{ h.score.toFixed(3) }}</span></div>',
            '<div class="l2">{{ h.titlePath || \'\' }} · 向量 {{ h.vectorScore.toFixed(3) }} · 关键词 {{ h.keywordScore.toFixed(3) }}</div>',
            '<div class="l1" style="white-space:pre-wrap;font-weight:400">{{ h.content }}</div>',
            '</div></div>',
            '</div>',
            '</template>',
            '<div v-else-if="!loading" class="lcard" style="align-items:center;justify-content:center;min-height:200px;color:var(--tk-muted)">请创建或选择知识库</div>',
            '</section>',

            '<div class="pmask" :class="{show:dialog}" @click.self="dialog=false">',
            '<div class="pcard w560">',
            '<div class="ph"><b>{{ editId ? "编辑知识库" : "创建知识库" }}</b><span class="sp"></span><span class="x" @click="dialog=false">✕</span></div>',
            '<div class="fld"><label>名称 <em>*</em></label><div class="pin"><input v-model="form.name" maxlength="100" /></div></div>',
            '<div class="fld"><label>描述</label><div class="pin"><input v-model="form.description" maxlength="500" /></div></div>',
            '<div class="fld"><label>可见性</label><div style="display:flex;gap:16px">',
            '<label class="radio" :class="{on:form.visibility===\'INTERNAL\'}" style="cursor:pointer" @click="form.visibility=\'INTERNAL\'"><span class="dot"></span>内部</label>',
            '<label class="radio" :class="{on:form.visibility===\'PUBLIC\'}" style="cursor:pointer" @click="form.visibility=\'PUBLIC\'"><span class="dot"></span>公开</label></div></div>',
            '<div class="frow">',
            '<div class="fld"><label>分块大小</label><div class="pin"><input type="number" v-model.number="form.chunkSize" min="300" max="3000" /></div></div>',
            '<div class="fld"><label>重叠字符</label><div class="pin"><input type="number" v-model.number="form.chunkOverlap" min="0" max="600" /></div></div>',
            '<div class="fld"><label>Top K</label><div class="pin"><input type="number" v-model.number="form.topK" min="1" max="20" /></div></div>',
            '<div class="fld"><label>相似度阈值</label><div class="pin"><input type="number" v-model.number="form.scoreThreshold" min="0" max="1" step="0.05" /></div></div>',
            '</div>',
            '<div class="fld"><label>回答提示词</label><textarea class="pta" v-model="form.systemPrompt" placeholder="可选：限定回答风格"></textarea></div>',
            '<div class="pbtn"><span class="sp"></span><button class="c ghost" @click="dialog=false">取消</button><button class="c blue" @click="saveBase">保存</button></div>',
            '</div></div>',
            '</div>'
        ].join(''),
        setup: function () {
            var bases=V.ref([]), selectedId=V.ref(null), documents=V.ref([]), loading=V.ref(false), uploading=V.ref(false), testing=V.ref(false), question=V.ref(''), result=V.ref(null), dialog=V.ref(false), editId=V.ref(null), form=V.reactive({name:'',description:'',visibility:'INTERNAL',enabled:true,chunkSize:800,chunkOverlap:120,topK:6,scoreThreshold:0.3,systemPrompt:''});
            var current=V.computed(function(){return bases.value.find(function(x){return x.id===selectedId.value})||null;});
            function load(){loading.value=true;api.get('/api/admin/knowledge-bases').then(function(x){bases.value=x||[];if(!selectedId.value&&bases.value.length)select(bases.value[0].id);else if(selectedId.value)loadDocs();}).catch(function(e){E.ElMessage.error(e.message);}).finally(function(){loading.value=false;});}
            function select(id){selectedId.value=id;loadDocs();result.value=null;}
            function loadDocs(){if(!selectedId.value)return;api.get('/api/admin/knowledge-bases/'+selectedId.value+'/documents').then(function(x){documents.value=x||[];});}
            function openCreate(){editId.value=null;Object.assign(form,{name:'',description:'',visibility:'INTERNAL',enabled:true,chunkSize:800,chunkOverlap:120,topK:6,scoreThreshold:0.3,systemPrompt:''});dialog.value=true;}
            function editBase(){editId.value=current.value.id;Object.assign(form,current.value);dialog.value=true;}
            function requestPayload(){return {name:form.name,description:form.description,visibility:form.visibility,enabled:form.enabled,chunkSize:form.chunkSize,chunkOverlap:form.chunkOverlap,topK:form.topK,scoreThreshold:form.scoreThreshold,systemPrompt:form.systemPrompt};}
            function saveBase(){var req=requestPayload();(editId.value?api.put('/api/admin/knowledge-bases/'+editId.value,req):api.post('/api/admin/knowledge-bases',req)).then(function(){dialog.value=false;load();E.ElMessage.success('已保存');}).catch(function(e){E.ElMessage.error(e.message);});}
            function saveEnabled(){var b=current.value;api.put('/api/admin/knowledge-bases/'+b.id,{name:b.name,description:b.description,visibility:b.visibility,enabled:b.enabled,chunkSize:b.chunkSize,chunkOverlap:b.chunkOverlap,topK:b.topK,scoreThreshold:b.scoreThreshold,systemPrompt:b.systemPrompt}).catch(function(e){E.ElMessage.error(e.message);load();});}
            function removeBase(){E.ElMessageBox.confirm('删除知识库及其索引？','确认',{type:'warning'}).then(function(){return api.delete('/api/admin/knowledge-bases/'+current.value.id);}).then(function(){selectedId.value=null;load();});}
            function upload(o){uploading.value=true;api.upload('/api/admin/knowledge-bases/'+selectedId.value+'/documents',o.file).then(function(d){if(d&&d.status==='FAILED'){E.ElMessage.error(d.errorMessage||'文档索引失败');}else if(d&&d.status==='PROCESSING'){E.ElMessage.success('文档已进入索引队列');}else{E.ElMessage.success('文档已完成索引');}load();loadDocs();}).catch(function(e){E.ElMessage.error(e.message);}).finally(function(){uploading.value=false;});}
            function reindex(d){api.post('/api/admin/knowledge-bases/'+selectedId.value+'/documents/'+d.id+'/reindex').then(function(){E.ElMessage.success('已重建');loadDocs();}).catch(function(e){E.ElMessage.error(e.message);});}
            function removeDocument(d){E.ElMessageBox.confirm('删除该文档及索引？','确认',{type:'warning'}).then(function(){return api.delete('/api/admin/knowledge-bases/'+selectedId.value+'/documents/'+d.id);}).then(loadDocs);}
            function test(){if(!question.value.trim())return;testing.value=true;api.post('/api/admin/knowledge-bases/'+selectedId.value+'/test',{question:question.value,generateAnswer:true}).then(function(x){result.value=x;}).catch(function(e){E.ElMessage.error(e.message);}).finally(function(){testing.value=false;});}
            function fmt(x){return api.fmtDateTime(x);} function statusText(x){return {PENDING:'待处理',PROCESSING:'处理中',SUCCESS:'成功',FAILED:'失败'}[x]||x;} function statusTone(x){return {SUCCESS:'g',FAILED:'r',PROCESSING:'a'}[x]||'i';} function statusType(x){return x==='SUCCESS'?'success':x==='FAILED'?'danger':x==='PROCESSING'?'warning':'info';}
            load(); return {bases:bases,current:current,selectedId:selectedId,documents:documents,loading:loading,uploading:uploading,testing:testing,question:question,result:result,dialog:dialog,editId:editId,form:form,select:select,openCreate:openCreate,editBase:editBase,saveBase:saveBase,saveEnabled:saveEnabled,removeBase:removeBase,upload:upload,reindex:reindex,removeDocument:removeDocument,test:test,fmt:fmt,statusText:statusText,statusTone:statusTone,statusType:statusType,Plus:ElementPlusIconsVue.Plus};
        }
    };
})();
