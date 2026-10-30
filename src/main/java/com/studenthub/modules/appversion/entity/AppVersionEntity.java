package com.studenthub.modules.appversion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** App 安装包版本（管理端发布 → App 启动检查更新） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_version")
public class AppVersionEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** ANDROID / IOS / HARMONY */
    private String platform;
    private String channel;
    private Integer versionCode;
    private String versionName;
    private Integer minSupportedVersionCode;
    private Boolean forceUpdate;
    private String updateLog;
    private String packageKey;
    private String packageFileName;
    private Long packageSize;
    private String packageSha256;
    private String downloadUrl;
    /** DRAFT / PUBLISHED / OFFLINE */
    private String status;
    private LocalDateTime publishedAt;
    private Long downloadCount;
}
