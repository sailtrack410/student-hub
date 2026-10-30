package com.studenthub.modules.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 拒绝原因模板（V77）：campus_id=0 为平台内置模板（builtin=1，不可删除）；
 * 各校区可新增自定义模板，模板文案由运营维护而不是写死在前端。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("identity_reject_reason_template")
public class IdentityRejectReasonEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private String templateCode;
    private String templateText;
    private Integer sortOrder;
    /** ENABLED / DISABLED */
    private String status;
    private Boolean builtin;
    private String remark;
}
