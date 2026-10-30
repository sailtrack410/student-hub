-- V83：教务 JW / 高德 AMap / RAG 知识库配置进入管理端配置中心。
-- 敏感值（教务会话加密密钥、高德 Web 服务 Key、RAG PostgreSQL 密码）AES-256-GCM 加密落库，
-- 页面只写不读；RAG 嵌入模型本身不在这里配置，由 AI 默认模型统一管理，并在产生向量后锁定。
-- 初始值留空：启动时把当前环境变量中的非空值迁移到种子行（updated_by 仍为 0），
-- 之后数据库值优先；管理员保存后 updated_by 记录操作人，空值即视为显式清空。

INSERT IGNORE INTO sys_config
    (id, campus_id, config_group, config_key, config_value, value_type, is_secret, description, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    -- 教务 JW
    (6641, 0, 'jw', 'jw.sso_base_url', '', 'STRING', 0, '学校统一身份认证 SSO 基地址', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6642, 0, 'jw', 'jw.jw_base_url', '', 'STRING', 0, '教务系统基地址（连通性测试探测该地址）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6643, 0, 'jw', 'jw.cas_service', '', 'STRING', 0, 'CAS 登录后的 service 回跳地址', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6644, 0, 'jw', 'jw.rsa_modulus', '', 'STRING', 0, 'SSO 登录 RSA 公钥模数（十六进制），修改后会重建登录公钥', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6645, 0, 'jw', 'jw.rsa_exponent', '', 'STRING', 0, 'SSO 登录 RSA 公钥指数（十六进制，通常 010001）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6646, 0, 'jw', 'jw.encrypt_key', '', 'STRING', 1, '教务会话 AES 加密密钥（加密存储，修改后仅影响新写入会话，旧会话需重新登录）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6647, 0, 'jw', 'jw.session_ttl_hours', '', 'INT', 0, '教务会话保活时长上限（小时，1-720）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6648, 0, 'jw', 'jw.connect_timeout_seconds', '', 'INT', 0, '教务 SSO/JW 连接超时（秒）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6649, 0, 'jw', 'jw.read_timeout_seconds', '', 'INT', 0, '教务 SSO/JW 读取超时（秒）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6650, 0, 'jw', 'jw.auth_rate_limit_per_minute', '', 'INT', 0, '单用户教务认证尝试限流（次/分钟）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),

    -- 高德地图
    (6651, 0, 'amap', 'amap.web_service_key', '', 'STRING', 1, '高德 Web 服务 Key（加密存储，仅后端调用，不下发客户端）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6652, 0, 'amap', 'amap.base_url', '', 'STRING', 0, '高德 Web 服务 API 地址（默认 https://restapi.amap.com）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6653, 0, 'amap', 'amap.connect_timeout_seconds', '', 'INT', 0, '高德 API 连接超时（秒）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6654, 0, 'amap', 'amap.read_timeout_seconds', '', 'INT', 0, '高德 API 读取超时（秒）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),

    -- RAG 知识库（嵌入模型在 AI 默认模型页配置，产生向量后锁定）
    (6661, 0, 'rag', 'rag.enabled', '', 'BOOLEAN', 0, '启用 RAG 知识库；修改后热更新并初始化向量表', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6662, 0, 'rag', 'rag.jdbc_url', '', 'STRING', 0, 'RAG 独立 PostgreSQL JDBC 地址（jdbc:postgresql://...）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6663, 0, 'rag', 'rag.username', '', 'STRING', 0, 'RAG PostgreSQL 用户名', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6664, 0, 'rag', 'rag.password', '', 'STRING', 1, 'RAG PostgreSQL 密码（加密存储）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6665, 0, 'rag', 'rag.max_document_size_mb', '', 'INT', 0, '单个知识库文档大小上限（MB）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6666, 0, 'rag', 'rag.max_parsed_characters', '', 'INT', 0, '单个文档解析字符上限', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6667, 0, 'rag', 'rag.embedding_batch_size', '', 'INT', 0, '向量化批大小（1-256）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6668, 0, 'rag', 'rag.lexical_candidate_limit', '', 'INT', 0, '词法召回候选数（1-500）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6669, 0, 'rag', 'rag.vector_candidate_limit', '', 'INT', 0, '向量召回候选数（1-500）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6670, 0, 'rag', 'rag.lexical_min_similarity', '', 'STRING', 0, '词法召回最小相似度（0-1）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6671, 0, 'rag', 'rag.rrf_k', '', 'INT', 0, 'RRF 融合常数，默认 60', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);
