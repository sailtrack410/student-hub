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
    /** REPAIR / LEAVE_APPROVAL / MANUAL / GUIDE; new items default to MANUAL. */
    private String flowType;
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
    /** 默认优先级：NORMAL 普通 / URGENT 加急（创建工单时作为初始优先级） */
    private String defaultPriority;
    /** 是否需要现场照片 → 完工照片 → 申请人确认的拍照流程 */
    private Boolean photoWorkflowRequired;
    /** 办事指南（富文本/纯文本，S02） */
    private String guideContent;
    /** 材料清单 JSON 数组，元素 {name, required, note} */
    private String materialList;
    /** 常见问题 JSON 数组，元素 {question, answer} */
    private String faq;
    private Integer sortOrder;
    private String status;
}
