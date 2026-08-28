package com.studenthub.modules.servicecatalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("service_category")
public class ServiceCategoryEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private String categoryCode;
    private String categoryName;
    private String icon;
    private String description;
    private Integer sortOrder;
    private String status;
}
