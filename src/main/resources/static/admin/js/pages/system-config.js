/* 系统配置中心：运行配置、功能开关、外部集成与实时健康。
   数据全部来自 /api/admin/system/**，配置变更写回 sys_config 并落审计。 */
(function () {
    'use strict';
    window.AdminPages = window.AdminPages || {};

    var GROUP_LABEL = {
        platform: '平台信息',
        feature: '功能开关',
        community: '社区与广告',
        security: '登录与安全',
        file: '文件存储',
        sla: 'SLA 与工作日历',
        qq: 'QQ 登录',
        wechat: '微信登录',
        mail: '邮箱 SMTP',
        jw: '教务系统 JW',
        amap: '高德地图',
        rag: 'RAG 知识库'
    };
    var GROUP_HINT = {
        platform: '品牌、Logo/Favicon/主题、联系与法律信息；由 App / 管理端读取。',
        feature: '控制客户端能力开关；关闭后客户端应隐藏入口并停止调用。',
        community: '信息流广告插入频率、跳出统计口径，以及事件限流与点击归因开关。',
        security: '密码策略、验证码、设备上限、会话 TTL 与 CORS；保存后登录/跨域链路立即读取，无需重启。',
        file: '本地目录 / MinIO / 阿里云 OSS；保存后热更新，存储路由立即切换。',
        sla: 'SLA 服务时段与周末规则，SLA 看板与工作台超时预警实时读取。',
        qq: 'QQ 互联移动应用凭证与模式；APP KEY 加密落库，保存后登录链路热更新，无需重启。',
        wechat: '微信小程序 AppID/AppSecret；Secret 加密落库，保存后 jscode2session 热更新。',
        mail: '校园邮箱 SMTP 主机、账号、授权码与发件人；密码加密落库，保存后发信链路热更新。',
        jw: '教务 SSO/JW 地址、RSA 公钥、会话密钥与超时；保存后登录链路热更新，加密密钥修改后旧会话需重新登录。',
        amap: '高德 Web 服务 Key、API 地址与超时；Key 加密落库，保存后定位链路热更新。',
        rag: 'RAG 独立 PostgreSQL、文档解析与召回参数；保存后数据源按新配置重建。嵌入模型在「AI 配置 → 类型默认模型」中选择，产生向量后自动锁定。'
    };
    var GROUP_SECTIONS = {
        platform: [
            { title: '品牌信息', match: function (key) { return ['platform.name', 'platform.short_name', 'platform.default_campus_id', 'brand.primary_color', 'platform.theme_mode'].indexOf(key) >= 0; } },
            { title: 'Logo 与图标', match: function (key) { return key === 'platform.logo_url' || key === 'platform.favicon_url'; } },
            { title: '联系与法律', match: function (key) { return key.indexOf('contact.') === 0 || key.indexOf('legal.') === 0; } }
        ],
        feature: [
            { title: '客户端功能', match: function (key) { return key !== 'feature.maintenance' && key !== 'feature.password_login'; } },
            { title: '平台模式', match: function (key) { return key === 'feature.maintenance' || key === 'feature.password_login'; } }
        ],
        community: [
            { title: '广告频控', match: function (key) { return key !== 'community.ad_bounce_seconds'; } },
            { title: '统计口径', match: function (key) { return key === 'community.ad_bounce_seconds'; } }
        ],
        security: [
            { title: '密码策略', match: function (key) { return key.indexOf('security.password_') === 0; } },
            { title: '会话与设备', match: function (key) { return ['security.session_ttl_hours', 'security.max_devices_per_user'].indexOf(key) >= 0; } },
            { title: '登录与跨域', match: function (key) { return key.indexOf('security.login_') === 0 || key === 'security.cors_allowed_origins'; } }
        ],
        file: [
            { title: '存储与容量', match: function (key) { return ['file.storage', 'file.storage_dir', 'file.max_size_mb'].indexOf(key) >= 0; } },
            { title: '访问签名', match: function (key) { return ['file.view_ttl_minutes', 'file.sign_secret'].indexOf(key) >= 0; } },
            { title: 'MinIO', match: function (key) { return key.indexOf('file.minio_') === 0; } },
            { title: '阿里云 OSS', match: function (key) { return key.indexOf('file.oss_') === 0; } }
        ],
        qq: [
            { title: '登录模式与凭证', match: function (key) { return ['qq.mode', 'qq.client_id', 'qq.client_secret'].indexOf(key) >= 0; } },
            { title: '接入参数', match: function (key) { return ['qq.api_base_url', 'qq.default_campus_id'].indexOf(key) >= 0; } },
            { title: '超时', match: function (key) { return key.indexOf('qq.') === 0 && key.indexOf('_timeout_') >= 0; } }
        ],
        wechat: [
            { title: '登录模式与凭证', match: function (key) { return ['wechat.mode', 'wechat.app_id', 'wechat.app_secret'].indexOf(key) >= 0; } },
            { title: '接入参数', match: function (key) { return ['wechat.api_base_url', 'wechat.default_campus_id'].indexOf(key) >= 0; } },
            { title: '超时', match: function (key) { return key.indexOf('wechat.') === 0 && key.indexOf('_timeout_') >= 0; } }
        ],
        mail: [
            { title: '发信开关与服务器', match: function (key) { return ['mail.enabled', 'mail.host', 'mail.port', 'mail.username', 'mail.password'].indexOf(key) >= 0; } },
            { title: '发件人与安全', match: function (key) { return ['mail.from', 'mail.from_name', 'mail.ssl_enabled', 'mail.starttls_enabled'].indexOf(key) >= 0; } },
            { title: '超时与调试', match: function (key) { return key.indexOf('mail.') === 0 && (key.indexOf('_timeout_') >= 0 || key === 'mail.dev_return_code'); } }
        ],
        jw: [
            { title: '服务地址', match: function (key) { return ['jw.sso_base_url', 'jw.jw_base_url', 'jw.cas_service'].indexOf(key) >= 0; } },
            { title: 'SSO 公钥与密钥', match: function (key) { return ['jw.rsa_modulus', 'jw.rsa_exponent', 'jw.encrypt_key'].indexOf(key) >= 0; } },
            { title: '会话、限流与超时', match: function (key) { return ['jw.session_ttl_hours', 'jw.auth_rate_limit_per_minute', 'jw.connect_timeout_seconds', 'jw.read_timeout_seconds'].indexOf(key) >= 0; } }
        ],
        amap: [
            { title: '凭证与地址', match: function (key) { return ['amap.web_service_key', 'amap.base_url'].indexOf(key) >= 0; } },
            { title: '超时', match: function (key) { return ['amap.connect_timeout_seconds', 'amap.read_timeout_seconds'].indexOf(key) >= 0; } }
        ],
        rag: [
            { title: '连接与开关', match: function (key) { return ['rag.enabled', 'rag.jdbc_url', 'rag.username', 'rag.password'].indexOf(key) >= 0; } },
            { title: '文档与向量化', match: function (key) { return ['rag.max_document_size_mb', 'rag.max_parsed_characters', 'rag.embedding_batch_size'].indexOf(key) >= 0; } },
            { title: '召回与融合', match: function (key) { return ['rag.lexical_candidate_limit', 'rag.lexical_min_similarity', 'rag.vector_candidate_limit', 'rag.rrf_k'].indexOf(key) >= 0; } }
        ]
    };
    var STATUS_LABEL = { CONFIGURED: '已配置', NOT_CONFIGURED: '未配置', DISABLED: '已停用', ERROR: '异常' };
    var STATUS_TONE = { CONFIGURED: 'ok', NOT_CONFIGURED: 'muted', DISABLED: 'warn', ERROR: 'bad' };

    window.AdminPages.SystemConfigPage = {
        name: 'SystemConfigPage',
        template: [
            '<div class="pgw" style="display:flex;flex-direction:column;gap:16px;min-height:0">',
            '  <div class="page-h"><div><h1>系统配置</h1><div class="sub">运行配置、功能开关与外部集成</div></div>',
            '    <div class="r"><span class="os-tag info" v-if="lastChecked">检查于 {{ lastChecked }}</span><button class="btn2" @click="loadAll">刷新</button></div></div>',

            '  <div class="os-grid" style="grid-template-columns:repeat(4,1fr)">',
            '    <div class="os-kpi"><div class="k-label"><span class="dot"></span>配置项</div><div class="k-val">{{ configs.length }}<small>项</small></div><div class="k-foot"><span>{{ dirtyCount ? dirtyCount + " 项待保存" : "全部已持久化" }}</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-ok)"></span>已开启功能</div><div class="k-val">{{ enabledFeatures }}<small>/ {{ features.length }}</small></div><div class="k-foot"><span>客户端能力开关</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" style="background:var(--tk-info)"></span>外部集成</div><div class="k-val">{{ configuredIntegrations }}<small>/ {{ integrations.length }}</small></div><div class="k-foot"><span>已配置可用的适配器</span></div></div>',
            '    <div class="os-kpi"><div class="k-label"><span class="dot" :style="{ background: healthColor }"></span>系统健康</div><div class="k-val" style="font-size:22px">{{ healthText }}</div><div class="k-foot"><span>{{ healthDetail }}</span></div></div>',
            '  </div>',

            '  <div style="display:flex;align-items:center;gap:4px">',
            '    <button v-for="t in tabs" :key="t.value" class="tab" :class="{ on: tab === t.value }" @click="tab = t.value">{{ t.label }}<span v-if="t.count !== undefined" class="n">{{ t.count }}</span></button>',
            '    <span style="flex:1"></span>',
            '    <span v-if="tab === \'config\' && dirtyCount" class="os-tag warn">{{ dirtyCount }} 项未保存</span>',
            '    <button v-if="tab === \'config\'" class="os-btn primary" :disabled="!dirtyCount || saving" @click="saveConfigs">{{ saving ? "保存中…" : "保存变更" }}</button>',
            '  </div>',

            '  <div v-if="tab === \'config\'" style="display:grid;grid-template-columns:214px minmax(0,1fr);gap:16px;align-items:start">',
            '    <div class="os-panel" style="align-self:start;position:sticky;top:0">',
            '      <div class="os-panel-h"><h3>配置分组</h3></div>',
            '      <div style="padding:7px">',
            '        <div v-for="g in groups" :key="g.key" class="cmd-item" :class="{ on: activeGroup === g.key }" style="min-height:44px" @click="selectGroup(g.key)">',
            '          <span class="ic"><el-icon :size="14"><Folder /></el-icon></span>',
            '          <span>{{ g.label }}</span><span class="grp">{{ g.count }}</span>',
            '        </div>',
            '      </div>',
            '    </div>',

            '    <div class="os-panel">',
            '      <div class="os-panel-h"><h3>{{ activeGroupLabel }}</h3><span class="hint">{{ activeGroupHint }}</span><div class="r"><button v-if="activeTestCode" class="os-btn sm" :disabled="groupTesting" @click="testGroup">{{ groupTesting ? "测试中…" : "测试本组" }}</button><div class="srch" style="width:220px;background:var(--tk-input)"><el-icon :size="13"><Search /></el-icon><input v-model.trim="keyword" placeholder="筛选配置键 / 名称" /></div></div></div>',
            '      <div v-if="groupTestResult" class="os-note" :class="{ warn: groupTestResult.status === \'ERROR\' }" style="margin:12px 18px 0"><b>本组测试</b> {{ groupTestResult.status }}<span v-if="groupTestResult.reachable != null"> · {{ groupTestResult.reachable ? "可达" : "不可达" }}</span><span v-if="groupTestResult.latencyMs != null"> · {{ groupTestResult.latencyMs }}ms</span><div style="margin-top:3px">{{ groupTestResult.detail }}</div></div>',
            '      <div v-if="activeGroup === \'rag\'" class="os-note" style="margin:12px 18px 0"><b>嵌入模型锁定</b> RAG 使用的嵌入模型在「AI 配置 → 类型默认模型」中选择；一旦知识库产生向量，模型即锁定，不能更换、清除、停用或删除。需要更换时先删除全部知识库文档。<a href="#/ai" style="margin-left:4px">去 AI 配置</a></div>',
            '      <div>',
            '        <div v-for="section in configSections" :key="section.title" class="cfg-sec">',
            '          <div v-if="showConfigSectionHeadings" class="cfg-sec-h"><span>{{ section.title }}</span><small>{{ section.items.length }} 项</small></div>',
            '        <div v-for="item in section.items" :key="item.key" style="display:grid;grid-template-columns:minmax(200px,1.25fr) minmax(220px,1.4fr) 140px;gap:16px;align-items:center;padding:15px 18px;border-bottom:1px solid var(--tk-row)">',
            '          <div style="min-width:0">',
            '            <div style="display:flex;align-items:center;gap:6px"><b style="font-size:13px;font-weight:600;color:var(--tk-text)">{{ item.description || item.key }}</b><span v-if="isDirty(item)" class="os-dot warn" title="有未保存变更"></span></div>',
            '            <div style="margin-top:4px;display:flex;align-items:center;gap:6px;flex-wrap:wrap"><code class="os-code">{{ item.key }}</code><span class="os-tag" :class="item.status === \'ENABLED\' ? \'ok\' : \'warn\'">{{ item.status === \'ENABLED\' ? \'生效\' : \'停用\' }}</span><span class="os-tag" :class="sourceTone(item.source)">{{ sourceLabel(item.source) }}</span></div>',
            '          </div>',
            '          <div style="min-width:0">',
            '            <div v-if="item.secret" style="display:flex;flex-direction:column;gap:5px">',
            '              <div style="display:flex;align-items:center;gap:8px"><input class="os-input" style="flex:1;min-width:0" type="password" autocomplete="new-password" v-model="item.value" :placeholder="item.configured ? \'已配置，输入新值覆盖（不回显）；留空不修改\' : \'未配置，输入后保存\'" /><span class="os-tag" style="white-space:nowrap" :class="item.configured ? \'ok\' : \'muted\'">{{ item.configured ? "已配置" : "未配置" }}</span></div>',
            '              <div v-if="item.source === \'ERROR\'" style="font-size:10px;color:var(--tk-bad);line-height:1.5">{{ item.error || "当前配置异常" }}</div>',
            '            </div>',
            '            <button v-else-if="item.valueType === \'BOOLEAN\'" class="os-switch" :class="{ on: item.value === \'true\' }" @click="item.value = item.value === \'true\' ? \'false\' : \'true\'"></button>',
            '            <select v-else-if="item.key === \'platform.theme_mode\'" class="os-select" v-model="item.value"><option value="light">light 浅色</option><option value="dark">dark 深色</option></select>',
            '            <div v-else-if="item.key === \'platform.logo_url\' || item.key === \'platform.favicon_url\'" style="display:flex;align-items:center;gap:8px"><input class="os-input" v-model="item.value" placeholder="https://..." /><img v-if="item.value" :src="item.value" :style="{ width: item.key === \'platform.favicon_url\' ? \'28px\' : \'40px\', height: item.key === \'platform.favicon_url\' ? \'28px\' : \'40px\', objectFit: \'contain\', borderRadius: \'6px\', border: \'1px solid var(--tk-stroke)\', background: \'var(--tk-panel)\' }" /></div>',
            '            <input v-else-if="item.valueType === \'INT\' || item.valueType === \'LONG\'" class="os-input" type="number" v-model="item.value" />',
            '            <input v-else class="os-input" v-model="item.value" :placeholder="item.value === null ? \'未配置\' : \'\'" />',
            '          </div>',
            '          <div style="display:flex;justify-content:flex-end;align-items:center;gap:8px">',
            '            <span style="font-size:11px;color:var(--tk-muted)">{{ item.valueType }}</span>',
            '            <button class="os-btn sm ghost" :disabled="!isDirty(item)" @click="resetItem(item)">撤销</button>',
            '          </div>',
            '        </div>',
            '        </div>',
            '        <div v-if="!filteredConfigs.length" class="os-empty">该分组没有匹配的配置项</div>',
            '      </div>',
            '    </div>',
            '  </div>',

            '  <div v-else-if="tab === \'features\'" class="os-panel">',
            '    <div class="os-panel-h"><h3>客户端功能开关</h3><span class="hint">切换后立即写回配置中心；App 下次拉取配置时生效</span></div>',
            '    <div style="display:grid;grid-template-columns:repeat(2,1fr);gap:0">',
            '      <div v-for="f in features" :key="f.key" style="display:flex;align-items:center;gap:12px;padding:17px 19px;border-bottom:1px solid var(--tk-row);border-right:1px solid var(--tk-row)">',
            '        <div style="flex:1;min-width:0"><div style="display:flex;align-items:center;gap:8px"><b style="font-size:13px;font-weight:600;color:var(--tk-text)">{{ f.name }}</b><span class="os-tag" :class="f.enabled ? \'ok\' : \'muted\'">{{ f.enabled ? "已开启" : "已关闭" }}</span><span v-if="f.rolloutPercent < 100" class="os-tag info">灰度 {{ f.rolloutPercent }}%</span></div>',
            '          <div style="font-size:11px;color:var(--tk-text3);margin-top:5px;line-height:1.5">{{ f.description || f.key }}</div></div>',
            '        <button class="os-btn sm" style="font-size:10px" :disabled="!f.enabled || featureSaving === f.key" @click="editRollout(f)">灰度</button>',
            '        <button class="os-switch" :class="{ on: f.enabled }" :disabled="featureSaving === f.key" @click="toggleFeature(f)"></button>',
            '      </div>',
            '    </div>',
            '  </div>',

            '  <div v-else-if="tab === \'integrations\'" style="display:flex;flex-direction:column;gap:12px">',
            '    <div class="os-note"><b>集成状态说明</b> 状态由后端按“数据库 + 环境变量”合并后的有效配置实时判定；QQ/微信/邮箱/教务/高德/RAG/文件存储已可在「运行配置」或文件存储页修改并热更新；AI、官方信息源由各自页面管理。密钥只写不读。外部服务不可用时，客户端应明确提示暂不可用。</div>',
            '    <div style="display:grid;grid-template-columns:repeat(2,1fr);gap:12px">',
            '      <div v-for="it in integrations" :key="it.code" class="os-panel" style="box-shadow:none">',
            '        <div class="os-panel-b" style="display:flex;flex-direction:column;gap:8px">',
            '          <div style="display:flex;align-items:center;gap:8px"><span class="os-dot" :class="integrationDot(it.status)"></span><b style="font-size:13px;font-weight:600;color:var(--tk-text)">{{ it.name }}</b><span class="os-tag" :class="STATUS_TONE[it.status] || \'muted\'" style="margin-left:auto">{{ STATUS_LABEL[it.status] || it.status }}</span></div>',
            '          <div style="font-size:12px;color:var(--tk-text3);line-height:1.6">{{ it.detail || "暂无说明" }}</div>',
            '          <div style="display:flex;align-items:center;gap:8px;margin-top:2px"><code class="os-code">{{ it.code }}</code><span v-if="it.mode" class="os-tag info">{{ it.mode }}</span><span style="margin-left:auto;font-size:10px;color:var(--tk-muted)">{{ it.editable ? "页面可改 · 热更新" : "环境变量管理" }}</span><button v-if="it.editable" class="os-btn sm ghost" @click="configureIntegration(it)">去配置</button><button class="os-btn sm" :disabled="testing[it.code]" @click="testIntegration(it)">{{ testing[it.code] ? "测试中…" : "测试连通" }}</button></div>',
            '          <div v-if="testResults[it.code]" class="os-note" :class="{ warn: testResults[it.code].status === \'ERROR\' }" style="padding:8px 10px;margin-top:2px"><b>测试结果</b> {{ testResults[it.code].status }}<span v-if="testResults[it.code].reachable != null"> · {{ testResults[it.code].reachable ? "可达" : "不可达" }}</span><span v-if="testResults[it.code].latencyMs != null"> · {{ testResults[it.code].latencyMs }}ms</span><div style="margin-top:3px">{{ testResults[it.code].detail }}</div></div>',
            '        </div>',
            '      </div>',
            '    </div>',
            '    <div v-if="!integrations.length" class="os-empty">暂无集成状态数据</div>',
            '  </div>',

            '  <div v-else class="os-grid" style="grid-template-columns:1.2fr .8fr">',
            '    <div class="os-panel">',
            '      <div class="os-panel-h"><h3>组件健康</h3><span class="hint">实时检测，失败会返回 DEGRADED / DOWN</span><div class="r"><span class="os-tag" :class="healthTone">{{ healthText }}</span></div></div>',
            '      <div style="padding:5px 18px 14px">',
            '        <div v-for="c in healthComponents" :key="c.name" style="display:flex;align-items:center;gap:10px;padding:14px 0;border-bottom:1px solid var(--tk-row)">',
            '          <span class="os-dot" :class="c.status === \'UP\' ? \'ok\' : (c.status === \'NOT_CONFIGURED\' ? \'muted\' : \'bad\')"></span>',
            '          <div style="flex:1;min-width:0"><b style="font-size:13px;font-weight:550;color:var(--tk-text)">{{ c.name }}</b><div style="font-size:11px;color:var(--tk-text3);margin-top:3px">{{ c.detail }}</div></div>',
            '          <span style="font-size:11px;color:var(--tk-muted)">{{ c.latencyMs != null ? c.latencyMs + " ms" : "—" }}</span>',
            '          <span class="os-tag" :class="c.status === \'UP\' ? \'ok\' : (c.status === \'NOT_CONFIGURED\' ? \'muted\' : \'bad\')">{{ c.status === "UP" ? "正常" : (c.status === "NOT_CONFIGURED" ? "未配置" : "异常") }}</span>',
            '        </div>',
            '        <div v-if="!healthComponents.length" class="os-empty">暂无健康数据，请点击刷新</div>',
            '      </div>',
            '    </div>',
            '    <div style="display:flex;flex-direction:column;gap:12px">',
            '      <div class="os-panel"><div class="os-panel-h"><h3>配置审计</h3></div><div class="os-panel-b" style="display:flex;flex-direction:column;gap:10px">',
            '        <div style="font-size:12px;color:var(--tk-text3);line-height:1.7">每次配置保存都会写入 sys_audit_log，记录操作人、变更前后值、IP 与 traceId；密钥值只记录 ******。可在「审计日志」按 action=CONFIG_UPDATE 检索。</div>',
            '        <button class="os-btn" @click="$root && null" style="display:none"></button>',
            '        <button class="os-btn" onclick="location.hash=\'#/audit\'"><el-icon :size="13"><Document /></el-icon>查看审计日志</button>',
            '      </div></div>',
            '      <div class="os-panel"><div class="os-panel-h"><h3>环境约束</h3></div><div class="os-panel-b" style="display:flex;flex-direction:column;gap:8px">',
            '        <div style="display:flex;justify-content:space-between;font-size:12px"><span style="color:var(--tk-text3)">配置存储</span><span style="color:var(--tk-text2)">MySQL · sys_config</span></div>',
            '        <div style="display:flex;justify-content:space-between;font-size:12px"><span style="color:var(--tk-text3)">敏感值</span><span style="color:var(--tk-text2)">AES-256-GCM 加密</span></div>',
            '        <div style="display:flex;justify-content:space-between;font-size:12px"><span style="color:var(--tk-text3)">生效方式</span><span style="color:var(--tk-text2)">保存后热更新</span></div>',
            '        <div style="display:flex;justify-content:space-between;font-size:12px"><span style="color:var(--tk-text3)">加密主密钥</span><span style="color:var(--tk-text2)">CONFIG_SECRET_KEY</span></div>',
            '        <div style="display:flex;justify-content:space-between;font-size:12px"><span style="color:var(--tk-text3)">当前管理员</span><span style="color:var(--tk-text2)">{{ userName }}</span></div>',
            '      </div></div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('\n'),
        setup: function () {
            var Vue = window.Vue;
            var El = window.ElementPlus;
            var api = window.AdminApi;

            var tab = Vue.ref('config');
            var configs = Vue.ref([]);
            var original = Vue.ref({});
            var features = Vue.ref([]);
            var integrations = Vue.ref([]);
            var health = Vue.ref(null);
            var activeGroup = Vue.ref('platform');
            var keyword = Vue.ref('');
            var saving = Vue.ref(false);
            var featureSaving = Vue.ref('');
            var lastChecked = Vue.ref('');
            var testing = Vue.reactive({});
            var testResults = Vue.reactive({});
            var groupTesting = Vue.ref(false);
            var groupTestResult = Vue.ref(null);
            var GROUP_TEST_CODE = { qq: 'qq', wechat: 'wechat', mail: 'mail', jw: 'jw', amap: 'amap', rag: 'rag', file: 'storage' };
            var activeTestCode = Vue.computed(function () { return GROUP_TEST_CODE[activeGroup.value] || null; });
            Vue.watch(activeGroup, function () { groupTestResult.value = null; });
            var userName = Vue.computed(function () {
                var u = window.AdminApp.state.user;
                return (u && (u.displayName || u.username)) || '—';
            });

            var tabs = Vue.computed(function () {
                return [
                    { value: 'config', label: '运行配置', count: configs.value.length },
                    { value: 'features', label: '功能开关', count: features.value.length },
                    { value: 'integrations', label: '外部集成', count: integrations.value.length },
                    { value: 'health', label: '系统健康' }
                ];
            });

            function snapshot() {
                var map = {};
                configs.value.forEach(function (c) { map[c.key] = c.value; });
                original.value = map;
            }

            function groupOf(item) { return item.group || 'platform'; }
            var groups = Vue.computed(function () {
                var counts = {};
                configs.value.forEach(function (c) { counts[groupOf(c)] = (counts[groupOf(c)] || 0) + 1; });
                var order = ['platform', 'feature', 'community', 'security', 'file', 'qq', 'wechat', 'mail', 'jw', 'amap', 'rag', 'sla'];
                var seen = {};
                var out = [];
                order.forEach(function (g) {
                    if (counts[g]) { out.push({ key: g, label: GROUP_LABEL[g] || g, count: counts[g] }); seen[g] = true; }
                });
                Object.keys(counts).forEach(function (g) {
                    if (!seen[g]) out.push({ key: g, label: GROUP_LABEL[g] || g, count: counts[g] });
                });
                return out;
            });
            var activeGroupLabel = Vue.computed(function () { return GROUP_LABEL[activeGroup.value] || activeGroup.value; });
            var activeGroupHint = Vue.computed(function () { return GROUP_HINT[activeGroup.value] || ''; });

            function selectGroup(key) {
                activeGroup.value = key;
                var content = document.querySelector('.content');
                if (content) content.scrollTop = 0;
            }

            var filteredConfigs = Vue.computed(function () {
                var q = keyword.value.toLowerCase();
                return configs.value.filter(function (c) {
                    if (groupOf(c) !== activeGroup.value) return false;
                    if (!q) return true;
                    return ((c.description || '') + ' ' + c.key).toLowerCase().indexOf(q) >= 0;
                });

            });
            var configSections = Vue.computed(function () {
                var items = filteredConfigs.value;
                if (!items.length) return [];
                var rules = GROUP_SECTIONS[activeGroup.value] || [];
                var sections = rules.map(function (rule) { return { title: rule.title, items: [] }; });
                var rest = { title: '其他参数', items: [] };
                items.forEach(function (item) {
                    var hit = -1;
                    for (var i = 0; i < rules.length; i++) {
                        if (rules[i].match(item.key)) { hit = i; break; }
                    }
                    if (hit >= 0) sections[hit].items.push(item); else rest.items.push(item);
                });
                sections = sections.filter(function (section) { return section.items.length > 0; });
                if (rest.items.length) sections.push(rest);
                return sections;
            });
            var showConfigSectionHeadings = Vue.computed(function () { return configSections.value.length > 1; });

            function isDirty(item) {
                return String(item.value == null ? '' : item.value) !== String(original.value[item.key] == null ? '' : original.value[item.key]);
            }
            var dirtyCount = Vue.computed(function () {
                return configs.value.filter(isDirty).length;
            });
            function resetItem(item) {
                item.value = original.value[item.key];
            }

            function loadConfigs() {
                return api.get('/api/admin/system/config?campusId=0').then(function (data) {
                    configs.value = data || [];
                    snapshot();
                });
            }
            function loadFeatures() {
                return api.get('/api/admin/system/features?campusId=0').then(function (data) { features.value = data || []; });
            }
            function loadIntegrations() {
                return api.get('/api/admin/system/integrations').then(function (data) { integrations.value = data || []; });
            }
            function loadHealth() {
                return api.get('/api/admin/system/health').then(function (data) {
                    health.value = data;
                    var d = data && data.checkedAt ? new Date(data.checkedAt) : new Date();
                    lastChecked.value = d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
                });
            }
            function loadAll() {
                Promise.all([loadConfigs(), loadFeatures(), loadIntegrations(), loadHealth()]).catch(function (error) {
                    El.ElMessage.error(error.message || '配置中心加载失败');
                });
            }

            var enabledFeatures = Vue.computed(function () {
                return features.value.filter(function (f) { return f.enabled; }).length;
            });
            var configuredIntegrations = Vue.computed(function () {
                return integrations.value.filter(function (i) { return i.status === 'CONFIGURED'; }).length;
            });
            var healthComponents = Vue.computed(function () {
                return (health.value && health.value.components) || [];
            });
            var healthTone = Vue.computed(function () {
                var s = health.value && health.value.status;
                return s === 'UP' ? 'ok' : (s === 'DOWN' ? 'bad' : 'warn');
            });
            var healthText = Vue.computed(function () {
                var s = health.value && health.value.status;
                return s === 'UP' ? '运行正常' : (s === 'DOWN' ? '服务异常' : (s === 'DEGRADED' ? '部分降级' : '待检测'));
            });
            var healthColor = Vue.computed(function () {
                var s = health.value && health.value.status;
                return s === 'UP' ? 'var(--tk-ok)' : (s === 'DOWN' ? 'var(--tk-bad)' : 'var(--tk-warn)');
            });
            var healthDetail = Vue.computed(function () {
                var comps = healthComponents.value;
                if (!comps.length) return '等待检测';
                var up = comps.filter(function (c) { return c.status === 'UP'; }).length;
                return up + '/' + comps.length + ' 组件正常';
            });

            function saveConfigs() {
                var items = configs.value.filter(isDirty).map(function (c) {
                    return { key: c.key, value: c.value, campusId: c.campusId || 0 };
                });
                if (!items.length) return;
                saving.value = true;
                api.post('/api/admin/system/config', { items: items })
                    .then(function (data) {
                        configs.value = data || [];
                        snapshot();
                        El.ElMessage.success('配置已保存并写入审计日志');
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '保存失败'); })
                    .finally(function () { saving.value = false; });
            }

            function toggleFeature(f) {
                var next = !f.enabled;
                featureSaving.value = f.key;
                api.post('/api/admin/system/config', { items: [{ key: f.key, value: next ? 'true' : 'false', campusId: 0 }] })
                    .then(function () {
                        f.enabled = next;
                        El.ElMessage.success((f.name || f.key) + ' 已' + (next ? '开启' : '关闭'));
                        loadConfigs().catch(function () {});
                        loadHealth().catch(function () {});
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '更新失败'); })
                    .finally(function () { featureSaving.value = ''; });
            }

            function editRollout(f) {
                if (!f || !f.enabled) { El.ElMessage.warning('请先开启功能开关再设置灰度'); return; }
                El.ElMessageBox.prompt('输入灰度比例（0-100，按用户稳定分桶；100 为全量）', '设置「' + f.name + '」灰度',
                    { inputValue: String(f.rolloutPercent == null ? 100 : f.rolloutPercent), inputPattern: /^(100|[1-9]?\d)$/, inputErrorMessage: '请输入 0-100 的整数' })
                    .then(function (result) {
                        var percent = Number(result.value);
                        featureSaving.value = f.key;
                        return api.post('/api/admin/system/features/' + encodeURIComponent(f.key) + '/rollout?percent=' + percent, { campusId: 0 });
                    })
                    .then(function () { return api.get('/api/admin/system/features?campusId=0'); })
                    .then(function (data) {
                        features.value = data || [];
                        El.ElMessage.success('灰度比例已更新');
                    })
                    .catch(function (err) {
                        if (err !== 'cancel' && err !== 'close') El.ElMessage.error((err && err.message) || '灰度设置失败');
                    })
                    .finally(function () { featureSaving.value = ''; });
            }

            function testIntegration(it) {
                if (!it || !it.code) return;
                testing[it.code] = true;
                api.post('/api/admin/system/integrations/' + encodeURIComponent(it.code) + '/test')
                    .then(function (result) {
                        testResults[it.code] = result;
                        if (result.status === 'ERROR') {
                            El.ElMessage.warning(it.name + ' 连通性异常：' + result.detail);
                        } else {
                            El.ElMessage.success(it.name + ' 测试完成：' + result.status);
                        }
                    })
                    .catch(function (error) { El.ElMessage.error(error.message || '连通性测试失败'); })
                    .finally(function () { testing[it.code] = false; });
            }

            function testGroup() {
                var code = activeTestCode.value;
                if (!code) return;
                groupTesting.value = true;
                groupTestResult.value = null;
                api.post('/api/admin/system/integrations/' + encodeURIComponent(code) + '/test')
                    .then(function (result) { groupTestResult.value = result; })
                    .catch(function (error) { El.ElMessage.error(error.message || '本组连通性测试失败'); })
                    .finally(function () { groupTesting.value = false; });
            }

            function sourceLabel(source) {
                if (source === 'DATABASE') return '数据库';
                if (source === 'ENVIRONMENT') return '环境变量';
                if (source === 'ERROR') return '异常';
                return '未配置';
            }
            function sourceTone(source) {
                if (source === 'DATABASE') return 'info';
                if (source === 'ENVIRONMENT') return 'warn';
                if (source === 'ERROR') return 'bad';
                return 'muted';
            }
            function configureIntegration(it) {
                if (!it || !it.editable) return;
                var map = { qq: 'qq', wechat: 'wechat', mail: 'mail', jw: 'jw', amap: 'amap', rag: 'rag' };
                tab.value = 'config';
                if (map[it.code]) activeGroup.value = map[it.code];
            }

            function integrationDot(status) {
                if (status === 'CONFIGURED') return 'ok';
                if (status === 'ERROR') return 'bad';
                if (status === 'DISABLED') return 'warn';
                return 'muted';
            }

            loadAll();

            return {
                tab: tab, tabs: tabs, configs: configs, features: features, integrations: integrations,
                health: health, groups: groups, activeGroup: activeGroup, activeGroupLabel: activeGroupLabel,
                activeGroupHint: activeGroupHint, selectGroup: selectGroup, keyword: keyword, filteredConfigs: filteredConfigs,
                configSections: configSections, showConfigSectionHeadings: showConfigSectionHeadings,
                isDirty: isDirty, dirtyCount: dirtyCount, resetItem: resetItem, saveConfigs: saveConfigs,
                saving: saving, featureSaving: featureSaving, toggleFeature: toggleFeature, editRollout: editRollout, loadAll: loadAll,
                lastChecked: lastChecked, enabledFeatures: enabledFeatures, configuredIntegrations: configuredIntegrations,
                healthComponents: healthComponents, healthTone: healthTone, healthText: healthText,
                healthColor: healthColor, healthDetail: healthDetail, integrationDot: integrationDot,
                sourceLabel: sourceLabel, sourceTone: sourceTone, configureIntegration: configureIntegration,
                testing: testing, testResults: testResults, testIntegration: testIntegration,
                groupTesting: groupTesting, groupTestResult: groupTestResult, activeTestCode: activeTestCode, testGroup: testGroup,
                STATUS_LABEL: STATUS_LABEL, STATUS_TONE: STATUS_TONE, userName: userName
            };
        }
    };
})();
