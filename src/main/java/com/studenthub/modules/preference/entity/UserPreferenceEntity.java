package com.studenthub.modules.preference.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_preference")
public class UserPreferenceEntity {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private Boolean chatPush;
    private Boolean repairPush;
    private Boolean workOrderPush;
    private Boolean chatReminder;
    private Boolean showAcademicInfo;
    private Boolean showMedals;
    private LocalDateTime updatedAt;
}
