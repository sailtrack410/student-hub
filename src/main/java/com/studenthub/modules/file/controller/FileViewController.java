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

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 签名 URL 匿名访问：链接本身即凭证（短时 HMAC 签名），供小程序 image 组件直接加载。
 */
@Slf4j
@RestController
@RequestMapping("/api/common/files")
@Tag(name = "通用-文件访问", description = "签名 URL 匿名直读文件")
@RequiredArgsConstructor
public class FileViewController {

    private final FileStorageService fileStorageService;

    @GetMapping("/view/{fileId}")
    @Operation(summary = "按签名访问文件", description = "签名与有效期校验通过后返回文件内容。", operationId = "viewFile")
    public ResponseEntity<InputStreamResource> view(@PathVariable Long fileId,
                                                    @RequestParam("expires") long expires,
                                                    @RequestParam("sign") String sign,
                                                    @RequestParam(value = "thumb", defaultValue = "false") boolean thumb) {
        FileStorageService.SignedContent content;
        try {
            content = fileStorageService.openBySignature(fileId, expires, sign);
        } catch (IllegalStateException exception) {
            log.warn("文件读取失败, fileId={}", fileId);
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (thumb && content.contentType().startsWith("image/")) {
            try {
                BufferedImage source = ImageIO.read(content.resource().getInputStream());
                if (source != null) {
                    int maxEdge = 480;
                    double scale = Math.min(1d, maxEdge / (double) Math.max(source.getWidth(), source.getHeight()));
                    int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                    int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                    boolean png = content.contentType().equalsIgnoreCase("image/png");
                    BufferedImage target = new BufferedImage(width, height, png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = target.createGraphics();
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(source, 0, 0, width, height, null);
                    graphics.dispose();
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    ImageIO.write(target, png ? "png" : "jpg", output);
                    byte[] bytes = output.toByteArray();
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=1800")
                            .contentLength(bytes.length)
                            .contentType(MediaType.parseMediaType(content.contentType()))
                            .body(new InputStreamResource(new ByteArrayInputStream(bytes)));
                }
            } catch (IOException ignored) {
                // 缩略图失败时回退原图，避免影响帖子浏览。
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=1800")
                .contentLength(content.fileSize())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.resource());
    }
}
