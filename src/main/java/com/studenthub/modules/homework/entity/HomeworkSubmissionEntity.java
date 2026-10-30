package com.studenthub.modules.homework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业提交：同一学生同一作业最多一条记录（数据库唯一键 homework_id+user_id），
 * 重复提交走更新而非新增。该表按需求只保留创建/更新时间与逻辑删除，不含操作人字段。
 */
@Data
@TableName("homework_submission")
public class HomeworkSubmissionEntity {

    public static final String STATUS_SUBMITTED = "SUBMITTED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long homeworkId;
    private Long userId;
    private String content;
    private LocalDateTime submittedAt;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    @Version
    private Integer version;
}
