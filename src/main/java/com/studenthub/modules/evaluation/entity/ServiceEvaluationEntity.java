package com.studenthub.modules.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("service_evaluation")
public class ServiceEvaluationEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long requestId;
    private Long workOrderId;
    private Long evaluatorId;
    private Long assigneeId;
    private String assigneeName;
    private Integer rating;
    private String tags;
    private String comment;

    /** 商家回复（管理端"回复评价"写入） */
    private String reply;

    /** 回复时间 */
    private java.time.LocalDateTime repliedAt;
}
