package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "提交内容申诉请求")
public record CreateCommunityAppealRequest(
        @Schema(description = "申诉对象类型：POST 帖子 / COMMENT 评论 / SANCTION 处罚") @Size(max = 16) String targetType,
        @Schema(description = "申诉对象 ID（帖子/评论/处罚记录 ID）") @NotNull Long targetId,
        @Schema(description = "复核原因编码：MISJUDGED/FIXED/ACCOUNT/OTHER，可空") @Size(max = 32) String reasonCode,
        @Schema(description = "补充说明，可空") @Size(max = 500) String reason,
        @Schema(description = "证明材料文件 ID，最多 3 张") @Size(max = 3) List<Long> evidenceFileIds) {
}
