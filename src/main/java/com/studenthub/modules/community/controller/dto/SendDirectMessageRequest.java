package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "发送私信请求")
public record SendDirectMessageRequest(
        @NotNull Long receiverId,
        @Size(max = 2000) String content,
        List<Long> imageFileIds,
        @Schema(description = "关联业务类型：POST（其余值忽略）", nullable = true) String referenceType,
        @Schema(description = "关联业务 ID（与 referenceType 成对出现才生效）", nullable = true) Long referenceId) {
}
