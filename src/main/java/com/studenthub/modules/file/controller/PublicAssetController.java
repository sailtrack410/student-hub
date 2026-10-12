package com.studenthub.modules.file.controller;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.storage.BinaryStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 将 MinIO 中的静态小程序素材通过 API 域名公开读取，避免真机直连 MinIO 端口被网络规则拦截。
 */
@RestController
@RequestMapping("/api/common/assets")
@Tag(name = "通用-静态素材", description = "小程序静态素材公开直读")
@RequiredArgsConstructor
public class PublicAssetController {
    private final BinaryStorage binaryStorage;

    @GetMapping("/{*assetPath}")
    @Operation(summary = "读取小程序静态素材", description = "按相对路径直读 MinIO static/assets/ 下的素材，浏览器缓存 7 天。")
    public ResponseEntity<byte[]> get(@PathVariable String assetPath) {
        String normalizedPath = assetPath == null ? "" : assetPath.replaceFirst("^/", "");
        if (normalizedPath.isBlank() || normalizedPath.contains("..") || normalizedPath.startsWith("/")) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        byte[] content;
        try {
            try (var stream = binaryStorage.get("static/assets/" + normalizedPath)) {
                content = stream.readAllBytes();
            }
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MediaType contentType = MediaTypeFactory.getMediaType(normalizedPath).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .contentType(contentType)
                .contentLength(content.length)
                .body(content);
    }
}
