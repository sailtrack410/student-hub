-- 发帖定位：地点名称、地址、经纬度（GCJ-02）、高德 POI ID
-- H2/MySQL 兼容：逐列 ALTER，避免 H2 不支持多列 ADD COLUMN 语法
ALTER TABLE community_post ADD COLUMN location_name VARCHAR(128) NULL;
ALTER TABLE community_post ADD COLUMN location_address VARCHAR(255) NULL;
ALTER TABLE community_post ADD COLUMN latitude DECIMAL(10,7) NULL;
ALTER TABLE community_post ADD COLUMN longitude DECIMAL(10,7) NULL;
ALTER TABLE community_post ADD COLUMN poi_id VARCHAR(64) NULL;
