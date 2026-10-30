package com.studenthub.modules.dict.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "字典类型")
public record DictTypeResponse(
        @Schema(description = "主键") Long id,
        @Schema(description = "字典编码，如 REPAIR_FAULT_TYPE") String typeCode,
        @Schema(description = "字典名称") String typeName,
        @Schema(description = "备注") String remark,
        @Schema(description = "状态：ENABLED/DISABLED") String status,
        @Schema(description = "是否内置（内置类型禁止删除）") Boolean builtin,
        @Schema(description = "字典项数量") Long itemCount,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt,
        @Schema(description = "来源：MANUAL 通用字典 / ACHIEVEMENT 成就模块") String source,
        @Schema(description = "是否只读（只读字典由业务模块维护，管理端不能增删改）") Boolean readonly) {
}
