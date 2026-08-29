package com.studenthub.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户导入过课表的学期登记（含未排课的空学期，供下拉切换） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("schedule_semester")
public class ScheduleSemesterEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private String semesterCode;
    private String semesterName;
    private java.time.LocalDateTime lastSyncedAt;
}