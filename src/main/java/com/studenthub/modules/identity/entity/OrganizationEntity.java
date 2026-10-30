package com.studenthub.modules.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_organization")
public class OrganizationEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    /** 校区范围：CAMPUS 具体校区 / SCHOOL 校级 / MULTI 跨校区（V85）。 */
    private String campusScope;
    private Long parentId;
    private String organizationCode;
    private String organizationName;
    private String organizationType;
    private Integer sortOrder;
    private String status;
    /** 组织负责人用户 ID（V70，可为空；指向 sys_user.id） */
    private Long leaderId;
}
