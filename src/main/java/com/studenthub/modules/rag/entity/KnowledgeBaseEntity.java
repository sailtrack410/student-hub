package com.studenthub.modules.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_base")
public class KnowledgeBaseEntity extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String name;
    private String description;
    private String visibility;
    private Boolean enabled;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer topK;
    private java.math.BigDecimal scoreThreshold;
    private String systemPrompt;
    private Integer documentCount;
    private Integer chunkCount;
    /** 是否供 App AI 助手检索（与 visibility 解耦） */
    private Boolean assistantEnabled;
    /** 单次注入 AI 的 token 预算 */
    private Integer ragTokenBudget;
    /** 多知识库排序，越大越优先 */
    private Integer assistantPriority;
}
