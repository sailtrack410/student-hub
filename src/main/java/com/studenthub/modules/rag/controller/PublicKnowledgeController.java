package com.studenthub.modules.rag.controller;

import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.rag.controller.dto.RagTestRequest;
import com.studenthub.modules.rag.controller.dto.RagTestResponse;
import com.studenthub.modules.rag.entity.KnowledgeBaseEntity;
import com.studenthub.modules.rag.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/public/knowledge-bases")
@Tag(name = "公开-知识库", description = "公开知识库问答")
@RequiredArgsConstructor
public class PublicKnowledgeController {

    private final ObjectProvider<KnowledgeBaseService> serviceProvider;

    @PostMapping("/{id}/query")
    @Operation(summary = "查询公开知识库，混合检索并生成回答")
    public Result<RagTestResponse> query(@PathVariable Long id, @RequestBody RagTestRequest request) {
        KnowledgeBaseService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(com.studenthub.common.ErrorCode.NOT_FOUND, "知识库服务未启用");
        }
        KnowledgeBaseEntity base = service.requireBase(id);
        if (!Boolean.TRUE.equals(base.getEnabled()) || !"PUBLIC".equals(base.getVisibility())) {
            throw new BusinessException(com.studenthub.common.ErrorCode.NOT_FOUND, "公开知识库不存在");
        }
        return Result.success(service.test(id, request.question(), true));
    }
}