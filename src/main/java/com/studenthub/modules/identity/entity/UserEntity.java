package com.studenthub.modules.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class UserEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private Long defaultOrganizationId;
    private String username;
    private String passwordHash;
    private String displayName;
    private String phone;
    private String avatarUrl;
    private String college;
    private String gender;
    private String status;
    private String wechatOpenId;
    private String qqOpenId;
    private String qqUnionId;
    private String identityType;
    private String authorizationStatus;
    private LocalDateTime lastLoginAt;
    /** 佩戴勋章编码（V42，卸下置 NULL；社区作者勋章批量组装搭车字段） */
    private String wornBadgeCode;
}
