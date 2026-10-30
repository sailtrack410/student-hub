package com.studenthub.modules.systemconfig.service;

import java.util.Map;
import java.util.Set;

/**
 * 外部集成在管理端配置中心中的配置键与环境变量映射：
 * QQ / 微信 / 邮箱 / 教务 JW / 高德 AMap / RAG 知识库 / 文件存储。
 * 应用启动时，空白的种子行会用对应环境变量初始化；之后数据库值优先。
 */
public final class AuthIntegrationConfigKeys {

    public static final String GROUP_QQ = "qq";
    public static final String GROUP_WECHAT = "wechat";
    public static final String GROUP_MAIL = "mail";
    public static final String GROUP_JW = "jw";
    public static final String GROUP_AMAP = "amap";
    public static final String GROUP_RAG = "rag";
    public static final String GROUP_FILE = "file";

    public static final String QQ_MODE = "qq.mode";
    public static final String QQ_CLIENT_ID = "qq.client_id";
    public static final String QQ_CLIENT_SECRET = "qq.client_secret";
    public static final String QQ_API_BASE_URL = "qq.api_base_url";
    public static final String QQ_DEFAULT_CAMPUS_ID = "qq.default_campus_id";
    public static final String QQ_CONNECT_TIMEOUT_SECONDS = "qq.connect_timeout_seconds";
    public static final String QQ_READ_TIMEOUT_SECONDS = "qq.read_timeout_seconds";

    public static final String WECHAT_MODE = "wechat.mode";
    public static final String WECHAT_APP_ID = "wechat.app_id";
    public static final String WECHAT_APP_SECRET = "wechat.app_secret";
    public static final String WECHAT_API_BASE_URL = "wechat.api_base_url";
    public static final String WECHAT_DEFAULT_CAMPUS_ID = "wechat.default_campus_id";
    public static final String WECHAT_CONNECT_TIMEOUT_SECONDS = "wechat.connect_timeout_seconds";
    public static final String WECHAT_READ_TIMEOUT_SECONDS = "wechat.read_timeout_seconds";

    public static final String MAIL_ENABLED = "mail.enabled";
    public static final String MAIL_HOST = "mail.host";
    public static final String MAIL_PORT = "mail.port";
    public static final String MAIL_USERNAME = "mail.username";
    public static final String MAIL_PASSWORD = "mail.password";
    public static final String MAIL_FROM = "mail.from";
    public static final String MAIL_FROM_NAME = "mail.from_name";
    public static final String MAIL_SSL_ENABLED = "mail.ssl_enabled";
    public static final String MAIL_STARTTLS_ENABLED = "mail.starttls_enabled";
    public static final String MAIL_CONNECT_TIMEOUT_MS = "mail.connect_timeout_ms";
    public static final String MAIL_READ_TIMEOUT_MS = "mail.read_timeout_ms";
    public static final String MAIL_WRITE_TIMEOUT_MS = "mail.write_timeout_ms";
    public static final String MAIL_DEV_RETURN_CODE = "mail.dev_return_code";

    public static final String JW_SSO_BASE_URL = "jw.sso_base_url";
    public static final String JW_JW_BASE_URL = "jw.jw_base_url";
    public static final String JW_CAS_SERVICE = "jw.cas_service";
    public static final String JW_RSA_MODULUS = "jw.rsa_modulus";
    public static final String JW_RSA_EXPONENT = "jw.rsa_exponent";
    public static final String JW_ENCRYPT_KEY = "jw.encrypt_key";
    public static final String JW_SESSION_TTL_HOURS = "jw.session_ttl_hours";
    public static final String JW_CONNECT_TIMEOUT_SECONDS = "jw.connect_timeout_seconds";
    public static final String JW_READ_TIMEOUT_SECONDS = "jw.read_timeout_seconds";
    public static final String JW_AUTH_RATE_LIMIT_PER_MINUTE = "jw.auth_rate_limit_per_minute";

    public static final String AMAP_WEB_SERVICE_KEY = "amap.web_service_key";
    public static final String AMAP_BASE_URL = "amap.base_url";
    public static final String AMAP_CONNECT_TIMEOUT_SECONDS = "amap.connect_timeout_seconds";
    public static final String AMAP_READ_TIMEOUT_SECONDS = "amap.read_timeout_seconds";

    public static final String RAG_ENABLED = "rag.enabled";
    public static final String RAG_JDBC_URL = "rag.jdbc_url";
    public static final String RAG_USERNAME = "rag.username";
    public static final String RAG_PASSWORD = "rag.password";
    public static final String RAG_MAX_DOCUMENT_SIZE_MB = "rag.max_document_size_mb";
    public static final String RAG_MAX_PARSED_CHARACTERS = "rag.max_parsed_characters";
    public static final String RAG_EMBEDDING_BATCH_SIZE = "rag.embedding_batch_size";
    public static final String RAG_LEXICAL_CANDIDATE_LIMIT = "rag.lexical_candidate_limit";
    public static final String RAG_VECTOR_CANDIDATE_LIMIT = "rag.vector_candidate_limit";
    public static final String RAG_LEXICAL_MIN_SIMILARITY = "rag.lexical_min_similarity";
    public static final String RAG_RRF_K = "rag.rrf_k";

    public static final String FILE_STORAGE = "file.storage";
    public static final String FILE_STORAGE_DIR = "file.storage_dir";
    public static final String FILE_SIGN_SECRET = "file.sign_secret";
    public static final String FILE_MAX_SIZE_MB = "file.max_size_mb";
    public static final String FILE_VIEW_TTL_MINUTES = "file.view_ttl_minutes";
    public static final String FILE_MINIO_ENDPOINT = "file.minio_endpoint";
    public static final String FILE_MINIO_PUBLIC_ENDPOINT = "file.minio_public_endpoint";
    public static final String FILE_MINIO_ACCESS_KEY = "file.minio_access_key";
    public static final String FILE_MINIO_SECRET_KEY = "file.minio_secret_key";
    public static final String FILE_MINIO_BUCKET = "file.minio_bucket";
    public static final String FILE_OSS_ENDPOINT = "file.oss_endpoint";
    public static final String FILE_OSS_PUBLIC_ENDPOINT = "file.oss_public_endpoint";
    public static final String FILE_OSS_BUCKET = "file.oss_bucket";
    public static final String FILE_OSS_ACCESS_KEY_ID = "file.oss_access_key_id";
    public static final String FILE_OSS_ACCESS_KEY_SECRET = "file.oss_access_key_secret";

    private static final Map<String, String> ENV_PROPERTIES = Map.ofEntries(
            Map.entry(QQ_MODE, "studenthub.qq.mode"),
            Map.entry(QQ_CLIENT_ID, "studenthub.qq.client-id"),
            Map.entry(QQ_CLIENT_SECRET, "studenthub.qq.client-secret"),
            Map.entry(QQ_API_BASE_URL, "studenthub.qq.api-base-url"),
            Map.entry(QQ_DEFAULT_CAMPUS_ID, "studenthub.qq.default-campus-id"),
            Map.entry(QQ_CONNECT_TIMEOUT_SECONDS, "studenthub.qq.connect-timeout-seconds"),
            Map.entry(QQ_READ_TIMEOUT_SECONDS, "studenthub.qq.read-timeout-seconds"),
            Map.entry(WECHAT_MODE, "studenthub.wechat.mode"),
            Map.entry(WECHAT_APP_ID, "studenthub.wechat.app-id"),
            Map.entry(WECHAT_APP_SECRET, "studenthub.wechat.app-secret"),
            Map.entry(WECHAT_API_BASE_URL, "studenthub.wechat.api-base-url"),
            Map.entry(WECHAT_DEFAULT_CAMPUS_ID, "studenthub.wechat.default-campus-id"),
            Map.entry(WECHAT_CONNECT_TIMEOUT_SECONDS, "studenthub.wechat.connect-timeout-seconds"),
            Map.entry(WECHAT_READ_TIMEOUT_SECONDS, "studenthub.wechat.read-timeout-seconds"),
            Map.entry(MAIL_ENABLED, "studenthub.mail.enabled"),
            Map.entry(MAIL_HOST, "studenthub.mail.host"),
            Map.entry(MAIL_PORT, "studenthub.mail.port"),
            Map.entry(MAIL_USERNAME, "studenthub.mail.username"),
            Map.entry(MAIL_PASSWORD, "studenthub.mail.password"),
            Map.entry(MAIL_FROM, "studenthub.mail.from"),
            Map.entry(MAIL_FROM_NAME, "studenthub.mail.from-name"),
            Map.entry(MAIL_SSL_ENABLED, "studenthub.mail.ssl-enabled"),
            Map.entry(MAIL_STARTTLS_ENABLED, "studenthub.mail.starttls-enabled"),
            Map.entry(MAIL_CONNECT_TIMEOUT_MS, "studenthub.mail.connect-timeout-ms"),
            Map.entry(MAIL_READ_TIMEOUT_MS, "studenthub.mail.read-timeout-ms"),
            Map.entry(MAIL_WRITE_TIMEOUT_MS, "studenthub.mail.write-timeout-ms"),
            Map.entry(MAIL_DEV_RETURN_CODE, "studenthub.mail.dev-return-code"),
            Map.entry(JW_SSO_BASE_URL, "studenthub.jw.sso-base-url"),
            Map.entry(JW_JW_BASE_URL, "studenthub.jw.jw-base-url"),
            Map.entry(JW_CAS_SERVICE, "studenthub.jw.cas-service"),
            Map.entry(JW_RSA_MODULUS, "studenthub.jw.rsa-modulus"),
            Map.entry(JW_RSA_EXPONENT, "studenthub.jw.rsa-exponent"),
            Map.entry(JW_ENCRYPT_KEY, "studenthub.jw.encrypt-key"),
            Map.entry(JW_SESSION_TTL_HOURS, "studenthub.jw.session-ttl-hours"),
            Map.entry(JW_CONNECT_TIMEOUT_SECONDS, "studenthub.jw.connect-timeout-seconds"),
            Map.entry(JW_READ_TIMEOUT_SECONDS, "studenthub.jw.read-timeout-seconds"),
            Map.entry(JW_AUTH_RATE_LIMIT_PER_MINUTE, "studenthub.jw.auth-rate-limit-per-minute"),
            Map.entry(AMAP_WEB_SERVICE_KEY, "studenthub.amap.web-service-key"),
            Map.entry(AMAP_BASE_URL, "studenthub.amap.base-url"),
            Map.entry(AMAP_CONNECT_TIMEOUT_SECONDS, "studenthub.amap.connect-timeout-seconds"),
            Map.entry(AMAP_READ_TIMEOUT_SECONDS, "studenthub.amap.read-timeout-seconds"),
            Map.entry(RAG_ENABLED, "studenthub.rag.enabled"),
            Map.entry(RAG_JDBC_URL, "studenthub.rag.jdbc-url"),
            Map.entry(RAG_USERNAME, "studenthub.rag.username"),
            Map.entry(RAG_PASSWORD, "studenthub.rag.password"),
            Map.entry(RAG_MAX_DOCUMENT_SIZE_MB, "studenthub.rag.max-document-size-mb"),
            Map.entry(RAG_MAX_PARSED_CHARACTERS, "studenthub.rag.max-parsed-characters"),
            Map.entry(RAG_EMBEDDING_BATCH_SIZE, "studenthub.rag.embedding-batch-size"),
            Map.entry(RAG_LEXICAL_CANDIDATE_LIMIT, "studenthub.rag.lexical-candidate-limit"),
            Map.entry(RAG_VECTOR_CANDIDATE_LIMIT, "studenthub.rag.vector-candidate-limit"),
            Map.entry(RAG_LEXICAL_MIN_SIMILARITY, "studenthub.rag.lexical-min-similarity"),
            Map.entry(RAG_RRF_K, "studenthub.rag.rrf-k"),
            Map.entry(FILE_STORAGE, "studenthub.file.storage"),
            Map.entry(FILE_STORAGE_DIR, "studenthub.file.storage-dir"),
            Map.entry(FILE_SIGN_SECRET, "studenthub.file.sign-secret"),
            Map.entry(FILE_MAX_SIZE_MB, "studenthub.file.max-file-size-mb"),
            Map.entry(FILE_VIEW_TTL_MINUTES, "studenthub.file.view-ttl-minutes"),
            Map.entry(FILE_MINIO_ENDPOINT, "studenthub.file.minio-endpoint"),
            Map.entry(FILE_MINIO_PUBLIC_ENDPOINT, "studenthub.file.minio-public-endpoint"),
            Map.entry(FILE_MINIO_ACCESS_KEY, "studenthub.file.minio-access-key"),
            Map.entry(FILE_MINIO_SECRET_KEY, "studenthub.file.minio-secret-key"),
            Map.entry(FILE_MINIO_BUCKET, "studenthub.file.minio-bucket"),
            Map.entry(FILE_OSS_ENDPOINT, "studenthub.file.oss-endpoint"),
            Map.entry(FILE_OSS_PUBLIC_ENDPOINT, "studenthub.file.oss-public-endpoint"),
            Map.entry(FILE_OSS_BUCKET, "studenthub.file.oss-bucket"),
            Map.entry(FILE_OSS_ACCESS_KEY_ID, "studenthub.file.oss-access-key-id"),
            Map.entry(FILE_OSS_ACCESS_KEY_SECRET, "studenthub.file.oss-access-key-secret"));

    public static final Set<String> ALL_KEYS = ENV_PROPERTIES.keySet();

    private AuthIntegrationConfigKeys() {
    }

    public static boolean isAuthGroup(String group) {
        return GROUP_QQ.equals(group) || GROUP_WECHAT.equals(group) || GROUP_MAIL.equals(group);
    }

    public static boolean isManagedGroup(String group) {
        return isAuthGroup(group) || GROUP_JW.equals(group) || GROUP_AMAP.equals(group)
                || GROUP_RAG.equals(group) || GROUP_FILE.equals(group);
    }

    public static boolean isAuthKey(String key) {
        return ENV_PROPERTIES.containsKey(key);
    }

    public static boolean isManagedKey(String key) {
        return ENV_PROPERTIES.containsKey(key);
    }

    public static boolean isSecretKey(String key) {
        return QQ_CLIENT_SECRET.equals(key) || WECHAT_APP_SECRET.equals(key)
                || MAIL_PASSWORD.equals(key) || JW_ENCRYPT_KEY.equals(key)
                || AMAP_WEB_SERVICE_KEY.equals(key) || RAG_PASSWORD.equals(key)
                || FILE_SIGN_SECRET.equals(key) || FILE_MINIO_ACCESS_KEY.equals(key)
                || FILE_MINIO_SECRET_KEY.equals(key) || FILE_OSS_ACCESS_KEY_ID.equals(key)
                || FILE_OSS_ACCESS_KEY_SECRET.equals(key);
    }

    public static String envProperty(String key) {
        return ENV_PROPERTIES.get(key);
    }
}
