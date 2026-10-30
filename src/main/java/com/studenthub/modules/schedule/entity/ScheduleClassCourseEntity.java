package com.studenthub.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 班级课表缓存：一个班级每学期的一份课表（供同学共享/同步） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("schedule_class_course")
public class ScheduleClassCourseEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String classCode;
    private String semesterCode;
    private String semesterName;
    private String courseCode;
    private String courseName;
    private String teacher;
    private String courseNature;
    private String location;
    private Integer dayOfWeek;
    private Integer startSection;
    private Integer endSection;
    private String weeks;
    private Integer weekParity;
    private String timeRange;
}