package com.studenthub.modules.systemconfig.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 集成连通性测试结果：只返回真实配置与连通状态，不伪造成功。 */
@Schema(description = "外部集成连通性测试结果")
public record AdminIntegrationTestResponse(
        @Schema(description = "集成编码") String code,
        @Schema(description = "集成名称") String name,
        @Schema(description = "状态：CONFIGURED/NOT_CONFIGURED/DISABLED/ERROR") String status,
        @Schema(description = "是否已具备必要配置") boolean configured,
        @Schema(description = "连通性：true 可达 / false 不可达 / null 未做网络探测（仅凭证类）") Boolean reachable,
        @Schema(description = "探测耗时毫秒") Long latencyMs,
        @Schema(description = "结果说明（不含密钥）") String detail,
        @Schema(description = "检测时间") LocalDateTime checkedAt) {
}
