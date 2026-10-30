-- 按平台和渠道序列化发布；仅承担跨实例互斥，不缓存当前版本指针。
CREATE TABLE app_release_channel (
    platform VARCHAR(16) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    PRIMARY KEY (platform, channel)
);
