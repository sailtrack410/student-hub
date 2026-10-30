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
    private String email;
    private String studentNo;
    private String avatarUrl;
    /** 昵称是否被用户主动设置过（V81）：false 时 QQ 登录可用 QQ 昵称覆盖默认昵称 */
    private Boolean displayNameCustomized;
    /** 头像是否被用户主动设置过（V81）：false 时 QQ 登录可用 QQ 头像覆盖默认头像 */
    private Boolean avatarCustomized;
    private String college;
    private String gender;
    private String status;
    private String wechatOpenId;
    private String qqOpenId;
    private String qqUnionId;
    private String identityType;
    private String authorizationStatus;
    /** 身份授权申请提交时间（V77；历史数据按注册时间回填） */
    private LocalDateTime authorizationSubmittedAt;
    /** 最近一次审核备注（V77：模板文案或自定义原因） */
    private String authorizationNote;
    /** 最近一次审核人（V77） */
    private Long authorizationReviewedBy;
    /** 最近一次审核时间（V77） */
    private LocalDateTime authorizationReviewedAt;
    private LocalDateTime lastLoginAt;
    /** 佩戴勋章编码（V42，卸下置 NULL；社区作者勋章批量组装搭车字段） */
    private String wornBadgeCode;
    /** 官方账号标识（V60）：社区展示「官方」标识替代等级；官方账号仅作发布主体 */
    private Boolean official;
    /** 官方头衔，如「教务处」「学生工作处」 */
    private String officialTitle;
    /** 社区账号类型（V66）：NORMAL / OFFICIAL_AUTO / OFFICIAL_HUMAN / AD_SERVICE */
    private String accountType;
    /** 官方账号运营人（V66） */
    private String accountOperator;
    /** 官方账号简介（V66） */
    private String accountDescription;
    /** 官方账号认证状态（V66）：NONE / PENDING / APPROVED / REJECTED / DISABLED */
    private String verificationStatus;
}
