package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_topic")
public class CommunityTopicEntity extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long campusId;
    private String name;
    private Integer heat;
    private String status;
    /** 推荐位（V71）：1 = 运营推荐 */
    private Boolean recommended;
    /** 合并去向（V71）：非空表示该话题已合并到目标话题 */
    private Long mergedIntoId;
}
