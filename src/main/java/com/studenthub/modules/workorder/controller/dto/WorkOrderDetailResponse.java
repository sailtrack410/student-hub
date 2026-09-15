package com.studenthub.modules.workorder.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "工单详情（含关联申请信息）")
public record WorkOrderDetailResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        @Schema(description = "工单 ID") Long orderId,
        @Schema(description = "工单号") String orderNo,
        @JsonSerialize(using = ToStringSerializer.class)
        @Schema(description = "关联申请 ID") Long requestId,
        @Schema(description = "申请编号", nullable = true) String requestNo,
        @Schema(description = "工单标题") String title,
        @Schema(description = "工单状态") String status,
        @Schema(description = "优先级") String priority,
        @Schema(description = "当前处理人", nullable = true) String assigneeName,
        @Schema(description = "申请人", nullable = true) String requesterName,
        @Schema(description = "报修人联系方式", nullable = true) String requesterPhone,
        @Schema(description = "报修地址", nullable = true) String repairAddress,
        @Schema(description = "申请人填写的表单内容", nullable = true) JsonNode formData,
        @Schema(description = "表单字段定义", nullable = true) JsonNode formSchema,
        @Schema(description = "是否需要拍照确认") boolean photoWorkflowRequired,
        @Schema(description = "现场照片地址", nullable = true) String arrivalPhotoUrl,
        @Schema(description = "完工照片地址", nullable = true) String completionPhotoUrl,
        @Schema(description = "全部完工照片地址", nullable = true) List<String> completionPhotoUrls,
        @Schema(description = "完工提交时间", nullable = true) LocalDateTime completionSubmittedAt,
        @Schema(description = "报修人确认时间", nullable = true) LocalDateTime confirmedAt,
        @Schema(description = "确认退回原因", nullable = true) String confirmationRejectReason,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "派单时间", nullable = true) LocalDateTime assignedAt,
        @Schema(description = "开始时间", nullable = true) LocalDateTime startedAt,
        @Schema(description = "完成时间", nullable = true) LocalDateTime completedAt,
        @Schema(description = "完成说明", nullable = true) String completeRemark) {
}
