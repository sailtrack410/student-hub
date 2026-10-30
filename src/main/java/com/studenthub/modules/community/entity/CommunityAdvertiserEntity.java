package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 社区广告主（V68）：按校区维护的广告投放主体，官方直投广告可关联 advertiser_id。
 * status 只存启停配置态：ENABLED/DISABLED，不参与广告审核状态机。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_advertiser")
public class CommunityAdvertiserEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private String name;
    private String contactName;
    private String contactPhone;
    /** ENABLED / DISABLED */
    private String status;
    private String remark;
}
