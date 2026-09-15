package com.studenthub.modules.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_document")
public class KnowledgeDocumentEntity extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private Long knowledgeBaseId;
    private Long fileId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String contentHash;
    private String status;
    private Integer chunkCount;
    private String errorMessage;
    private LocalDateTime indexedAt;
}
