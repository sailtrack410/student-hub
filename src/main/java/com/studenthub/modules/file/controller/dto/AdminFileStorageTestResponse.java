package com.studenthub.modules.file.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 单个文件存储实现的连通性测试结果；不包含任何 AccessKey/Secret。 */
@Schema(description = "文件存储连通性测试结果")
public record AdminFileStorageTestResponse(
        @Schema(description = "测试的存储实现：local / minio / oss") String storage,
        @Schema(description = "存储实现展示名") String label,
        @Schema(description = "测试目标：本地目录或 Endpoint/桶") String target,
        @Schema(description = "该实现是否为当前生效存储") boolean active,
        @Schema(description = "是否可达/可写") boolean reachable,
        @Schema(description = "探测耗时毫秒") long latencyMs,
        @Schema(description = "脱敏后的探测说明") String detail,
        @Schema(description = "测试时间") LocalDateTime testedAt) {
}
