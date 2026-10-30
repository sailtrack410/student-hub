package com.studenthub.modules.servicerequest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("service_request")
public class ServiceRequestEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String requestNo;
    private Long campusId;
    private Long itemId;
    private String itemName;
    private Long categoryId;
    private Long requesterId;
    private String title;
    private String formData;
    private String status;
    private Long handlerOrganizationId;
    private String idempotencyKey;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private String rejectReason;
    private String cancelReason;

    /** 请假审批专属字段（H01）：仅请假事项写入，其余申请为 null。 */
    private String leaveType;
    private LocalDateTime leaveStartAt;
    private LocalDateTime leaveEndAt;
    private java.math.BigDecimal leaveDays;
    /** 销假时间与状态：NONE 未销假 / CANCELLED 已销假；不改变请假本身的审批结果。 */
    private LocalDateTime leaveCancelAt;
    private String leaveCancelStatus;
}
