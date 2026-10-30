package com.studenthub.modules.homework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 作业：面向某个校区发布的课程作业，status 只允许 PUBLISHED/CLOSED。
 * 逻辑删除与乐观锁字段由 {@link BaseEntity} 统一提供。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("homework")
public class HomeworkEntity extends BaseEntity {

    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_CLOSED = "CLOSED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private String courseName;
    private String title;
    private String content;
    private String teacherName;
    private LocalDateTime publishAt;
    private LocalDateTime dueAt;
    private String status;
}
