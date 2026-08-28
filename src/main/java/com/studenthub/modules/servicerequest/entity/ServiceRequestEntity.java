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
}
