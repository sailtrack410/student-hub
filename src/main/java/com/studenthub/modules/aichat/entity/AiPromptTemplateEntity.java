package com.studenthub.modules.aichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 版本化提示词（V73）：同一 prompt_code 下多版本，active=1 为运行时生效版本。
 *
 * <p>版本不可原地修改内容：内容调整请新建版本再激活，保证审计可回溯。
 * 未激活任何数据库版本时，运行时回退到代码内置基线（CampusAssistantPrompts）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_prompt_template")
public class AiPromptTemplateEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private String promptCode;
    private String promptName;
    /** 场景：CAMPUS_ASSISTANT / SERVICE_GUIDE / DOC_SUMMARY ... */
    private String scene;
    private String systemPrompt;
    private String userPromptTemplate;
    private String variables;
    private Integer versionNo;
    private Boolean active;
    /** ENABLED / DISABLED */
    private String status;
    private String changeNote;
    private String remark;
}
