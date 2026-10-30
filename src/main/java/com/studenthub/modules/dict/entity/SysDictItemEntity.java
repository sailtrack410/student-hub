package com.studenthub.modules.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典项：同一 typeCode 下 itemCode 唯一；extra 存扩展 JSON（如报修类型的 emergency 标记）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_item")
public class SysDictItemEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String typeCode;
    private String itemCode;
    private String itemLabel;
    private String itemValue;
    private Integer sortOrder;
    private String status;
    private String extra;
    private String remark;
}
