package com.studenthub.modules.file.controller;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.file.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 签名 URL 匿名访问：链接本身即凭证（短时 HMAC 签名），供小程序 image 组件直接加载。
 */
@Slf4j
@RestController
@RequestMapping("/api/common/files")
@Tag(name = "文件访问")
@RequiredArgsConstructor
public class FileViewController {

    private final FileStorageService fileStorageService;

    @GetMapping("/view/{fileId}")
    @Operation(summary = "按签名访问文件", description = "签名与有效期校验通过后返回文件内容。", operationId = "viewFile")
    public ResponseEntity<InputStreamResource> view(@PathVariable Long fileId,
                                                    @RequestParam("expires") long expires,
                                                    @RequestParam("sign") String sign) {
        FileStorageService.SignedContent content;
        try {
            content = fileStorageService.openBySignature(fileId, expires, sign);
        } catch (IllegalStateException exception) {
            log.warn("文件读取失败, fileId={}", fileId);
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=1800")
                .contentLength(content.fileSize())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.resource());
    }
}
