package com.studenthub.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 调课通知：从青果调课监控页抓取的权威变更公告（补课/停课/调课） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("schedule_adjustment")
public class ScheduleAdjustmentEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String semesterCode;
    private String noticeTime;
    private String actionType;
    private String courseCode;
    private String courseName;
    private String classNo;
    private String targetClasses;
    private String detailJson;
    private String summary;
    private String rawText;
}
