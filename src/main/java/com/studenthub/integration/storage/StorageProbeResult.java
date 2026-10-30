package com.studenthub.integration.storage;

/** 存储连通性探测结果（不含任何密钥明文）。 */
public record StorageProbeResult(boolean reachable, long latencyMs, String storage, String detail) {
}
