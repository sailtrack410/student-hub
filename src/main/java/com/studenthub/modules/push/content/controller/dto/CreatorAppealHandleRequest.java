package com.studenthub.modules.push.content.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "创作者申诉处理（管理端）")
public record CreatorAppealHandleRequest(
        @Schema(description = "是否支持申诉：true 通过并撤销处罚，false 驳回") Boolean approve,
        @Schema(description = "处理结论") @Size(max = 500) String result) {
}
