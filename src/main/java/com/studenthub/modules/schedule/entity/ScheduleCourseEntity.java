package com.studenthub.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 个人课表（导入自教务系统的教学安排） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("schedule_course")
public class ScheduleCourseEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private String semesterCode;
    private String semesterName;
    private String courseCode;
    private String courseName;
    private String teacher;
    private String classCode;
    private String courseNature;
    private String location;
    private Integer dayOfWeek;
    private Integer startSection;
    private Integer endSection;
    /** 上课周次表达式，如 1-5,7,9-16 */
    private String weeks;
    /** 0=每周, 1=单周, 2=双周 */
    private Integer weekParity;
    private String timeRange;
    private String rawText;
}