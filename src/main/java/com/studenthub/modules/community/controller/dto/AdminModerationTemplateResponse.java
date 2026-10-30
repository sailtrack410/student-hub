package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 社区处理模板响应；builtin=true 表示平台内置模板（campus_id=0，不可修改/删除）。 */
@Schema(description = "社区处理模板")
public record AdminModerationTemplateResponse(
        @Schema(description = "模板 ID") Long id,
        @Schema(description = "所属校区 ID（0 = 平台内置）") Long campusId,
        @Schema(description = "模板类型") String templateType,
        @Schema(description = "模板名称") String templateName,
        @Schema(description = "关联原因编码") String reasonCode,
        @Schema(description = "模板正文") String content,
        @Schema(description = "排序值") Integer sortOrder,
        @Schema(description = "状态") String status,
        @Schema(description = "是否平台内置（不可编辑）") boolean builtin,
        @Schema(description = "创建时间") LocalDateTime createdAt) {
}
