package com.studenthub.modules.systemconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 功能开关灰度比例（SYS02）：按校区 + 功能键记录 0-100 的放量百分比。 */
@Data
@TableName("sys_feature_rollout")
public class SysFeatureRolloutEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String featureKey;

    private Long campusId;

    private Integer rolloutPercent;

    private Long updatedBy;

    private LocalDateTime updatedAt;
}
