package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户导入逐行结果（U07）。
 *
 * <p>action 取值：CREATE（将新建）/ UPDATE（将覆盖已有用户）/ SKIP（冲突跳过）/ ERROR（数据不合法）。
 * 预览与正式导入返回同一结构，{@code userId} 仅正式导入的 CREATE/UPDATE 行有值。</p>
 */
@Schema(description = "用户导入逐行结果")
public record AdminUserImportRow(
        @Schema(description = "CSV 行号（含表头，数据行从 2 开始）") int lineNo,
        @Schema(description = "用户名") String username,
        @Schema(description = "昵称", nullable = true) String displayName,
        @Schema(description = "处理动作 CREATE/UPDATE/SKIP/ERROR") String action,
        @Schema(description = "说明") String message,
        @Schema(description = "用户 ID（仅正式导入且有落库时返回）", nullable = true) Long userId) {
}
