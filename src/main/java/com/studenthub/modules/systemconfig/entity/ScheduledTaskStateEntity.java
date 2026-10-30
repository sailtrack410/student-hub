package com.studenthub.modules.systemconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 定时任务启停状态：按 Spring 注册 key（类名#方法名）覆盖自动触发，未命中时默认启用。 */
@Data
@TableName("sys_scheduled_task_state")
public class ScheduledTaskStateEntity {

    @TableId(value = "task_key", type = IdType.INPUT)
    private String taskKey;

    private Integer enabled;

    private String note;

    private Long updatedBy;

    private LocalDateTime updatedAt;
}
