package com.studenthub.modules.servicerequest.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理端审批服务申请：通过（推进到处理中）或驳回。
 */
public record AdminReviewRequest(
        @NotBlank(message = "审批动作必填") String action,
        @Size(max = 512, message = "审批意见最长 512 字") String comment
) {
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_REJECT = "REJECT";
}
