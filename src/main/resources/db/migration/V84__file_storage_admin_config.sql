-- V84：文件存储完整配置进入管理端配置中心，并新增阿里云 OSS 实现。
-- 运行时由 AuthIntegrationRuntimeConfig 在启动后及保存事务提交后写入 FileProperties，
-- 存储路由按最新 storage 选择 local / MinIO / OSS，无需重启；敏感字段加密落库、页面只写不读。
-- 初始值留空，启动时把环境变量/application.yaml 中的非空值迁移到种子行（updated_by 仍为 0）。

INSERT IGNORE INTO sys_config
    (id, campus_id, config_group, config_key, config_value, value_type, is_secret, description, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (6681, 0, 'file', 'file.storage_dir', '', 'STRING', 0, '本地存储目录（storage=local 时使用，保存后热更新）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6682, 0, 'file', 'file.sign_secret', '', 'STRING', 1, '短时签名 HMAC 密钥（加密存储，多实例必须一致；修改后新链接生效）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6683, 0, 'file', 'file.minio_endpoint', '', 'STRING', 0, 'MinIO 后端访问端点，如 http://host:9000', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6684, 0, 'file', 'file.minio_public_endpoint', '', 'STRING', 0, 'MinIO 对客户端公开地址（留空使用访问端点）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6685, 0, 'file', 'file.minio_access_key', '', 'STRING', 1, 'MinIO AccessKey（加密存储，仅后端使用）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6686, 0, 'file', 'file.minio_secret_key', '', 'STRING', 1, 'MinIO SecretKey（加密存储，仅后端使用）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6687, 0, 'file', 'file.oss_endpoint', '', 'STRING', 0, '阿里云 OSS Endpoint，如 https://oss-cn-chengdu.aliyuncs.com', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6688, 0, 'file', 'file.oss_public_endpoint', '', 'STRING', 0, 'OSS 对客户端公开域名/CDN（留空使用 Endpoint）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6689, 0, 'file', 'file.oss_bucket', '', 'STRING', 0, 'OSS 桶名称，如 smartcampus', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6690, 0, 'file', 'file.oss_access_key_id', '', 'STRING', 1, 'OSS AccessKeyId（加密存储，仅后端使用）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6691, 0, 'file', 'file.oss_access_key_secret', '', 'STRING', 1, 'OSS AccessKeySecret（加密存储，仅后端使用）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

UPDATE sys_config
SET description = '存储实现：local / minio / oss（保存后热切换，无需重启）'
WHERE config_key = 'file.storage';

UPDATE sys_config
SET description = '单文件大小上限（MB，保存后热更新）'
WHERE config_key = 'file.max_size_mb';

UPDATE sys_config
SET description = '签名访问链接有效期（分钟，保存后热更新）'
WHERE config_key = 'file.view_ttl_minutes';

UPDATE sys_config
SET description = 'MinIO 桶名称（保存后热更新）'
WHERE config_key = 'file.minio_bucket';
