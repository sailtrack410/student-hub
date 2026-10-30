package com.studenthub.modules.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work_order")
public class WorkOrderEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String orderNo;
    private Long requestId;
    private Long campusId;
    private Long itemId;
    private String title;
    private Long assignedOrganizationId;
    private Long assigneeId;
    private String assigneeName;
    private String status;
    private String priority;
    /** SLA 承诺时限快照（小时），创建工单时按服务项 slaHours 写入，缺省 24 */
    private Integer slaHours;
    /** SLA 截止时间；历史数据为空时服务层按 created_at + slaHours 兜底计算 */
    private LocalDateTime dueAt;
    private String completeRemark;
    private Boolean photoWorkflowRequired;
    private Long arrivalPhotoFileId;
    private Long completionPhotoFileId;
    private String completionPhotoFileIds;
    private LocalDateTime completionSubmittedAt;
    private LocalDateTime confirmedAt;
    private String confirmationRejectReason;
    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
