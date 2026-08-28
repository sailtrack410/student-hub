package com.studenthub.modules.servicecatalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("service_item")
public class ServiceItemEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private Long categoryId;
    private String itemCode;
    private String itemName;
    private String summary;
    private String description;
    private String icon;
    /**
     * JSON 字符串：动态申请表单字段定义，
     * 元素结构 {key,label,type(input|textarea|select|date|number|image),required,options,max,maxCount,placeholder}
     */
    private String formSchema;
    private Long handlerOrganizationId;
    private Integer slaHours;
    private Integer sortOrder;
    private String status;
}
