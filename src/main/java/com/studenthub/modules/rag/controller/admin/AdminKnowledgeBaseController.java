package com.studenthub.modules.rag.controller.admin;

import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.rag.controller.dto.*;
import com.studenthub.modules.rag.entity.KnowledgeBaseEntity;
import com.studenthub.modules.rag.entity.KnowledgeDocumentEntity;
import com.studenthub.modules.rag.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@RestController @RequestMapping("/api/admin/knowledge-bases") @Tag(name="管理端-RAG 知识库")
@RequiredArgsConstructor @PreAuthorize("hasAuthority('ai:manage')")
public class AdminKnowledgeBaseController {
    private final ObjectProvider<KnowledgeBaseService> serviceProvider;
    private final CurrentUserProvider users;

    private KnowledgeBaseService service() {
        KnowledgeBaseService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(com.studenthub.common.ErrorCode.NOT_FOUND, "知识库服务未启用");
        }
        return service;
    }

    @GetMapping public Result<List<KnowledgeBaseResponse>> list(){return Result.success(service().listBases().stream().map(KnowledgeBaseResponse::from).toList());}
    @PostMapping public Result<KnowledgeBaseResponse> create(@RequestBody @Valid KnowledgeBaseRequest r){return Result.success(KnowledgeBaseResponse.from(service().create(users.currentUserIdOrSystem(),r)));}
    @PutMapping("/{id}") public Result<KnowledgeBaseResponse> update(@PathVariable Long id,@RequestBody @Valid KnowledgeBaseRequest r){return Result.success(KnowledgeBaseResponse.from(service().update(id,r)));}
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable Long id){service().delete(id);return Result.success();}
    @GetMapping("/{id}/documents") public Result<List<KnowledgeDocumentResponse>> documents(@PathVariable Long id){return Result.success(service().documents(id).stream().map(KnowledgeDocumentResponse::from).toList());}
    @PostMapping(value="/{id}/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @Operation(summary="上传并索引文档")
    public Result<KnowledgeDocumentResponse> upload(@PathVariable Long id,@RequestPart("file") MultipartFile file) throws IOException {if(file.isEmpty()) throw new IllegalArgumentException("文件不能为空");KnowledgeDocumentEntity d=service().upload(users.currentUserIdOrSystem(),id,file.getBytes(),file.getOriginalFilename(),file.getContentType());return Result.success(KnowledgeDocumentResponse.from(d));}
    @PostMapping("/{id}/documents/{docId}/reindex") public Result<KnowledgeDocumentResponse> reindex(@PathVariable Long id,@PathVariable Long docId){return Result.success(KnowledgeDocumentResponse.from(service().reindex(id,docId)));}
    @DeleteMapping("/{id}/documents/{docId}") public Result<Void> deleteDocument(@PathVariable Long id,@PathVariable Long docId){service().deleteDocument(id,docId);return Result.success();}
    @PostMapping("/{id}/test") public Result<RagTestResponse> test(@PathVariable Long id,@RequestBody @Valid RagTestRequest r){return Result.success(service().test(id,r.question(),r.generateAnswer()==null||r.generateAnswer()));}
}