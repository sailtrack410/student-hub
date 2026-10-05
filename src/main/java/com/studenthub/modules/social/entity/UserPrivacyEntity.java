package com.studenthub.modules.social.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户隐私设置（API需求清单 2.4）：缺行=默认全公开，读取时无须插行。
 */
@Data
@TableName("user_privacy")
public class UserPrivacyEntity {

    @TableId
    private Long userId;

    private Boolean showAcademicInfo;
    private Boolean showMedals;
    private LocalDateTime updatedAt;
}
