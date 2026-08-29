package com.studenthub.modules.file.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/app/files")
@Tag(name = "小程序-文件上传")
@RequiredArgsConstructor
public class AppFileController {

    private final FileStorageService fileStorageService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @Operation(summary = "上传图片", description = "上传申请材料或现场照片，仅支持 jpg/png/webp 且不超过大小限制，"
            + "返回文件 ID 与带签名的短时访问路径。", operationId = "uploadFile")
    public Result<FileStorageService.StoredFile> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Long uploaderId = currentUserProvider.requireCurrentUser().userId();
        return Result.success(fileStorageService.store(file.getBytes(),
                file.getOriginalFilename(), file.getContentType(), uploaderId));
    }

    @PostMapping("/audio")
    @Operation(summary = "上传音频", description = "上传语音音频（mp3/aac/m4a/wav/ogg），供 AI 语音转写使用。",
            operationId = "uploadAudio")
    public Result<FileStorageService.StoredFile> uploadAudio(@RequestParam("file") MultipartFile file) throws IOException {
        Long uploaderId = currentUserProvider.requireCurrentUser().userId();
        return Result.success(fileStorageService.storeAudio(file.getBytes(),
                file.getOriginalFilename(), file.getContentType(), uploaderId));
    }

    @GetMapping("/view-url")
    @Operation(summary = "换取签名访问路径", description = "按文件 ID 换取带签名的短时访问路径（如头像展示）。",
            operationId = "getSignedViewUrl")
    public Result<String> viewUrl(@org.springframework.web.bind.annotation.RequestParam("fileId") Long fileId) {
        return Result.success(fileStorageService.signViewPath(fileId));
    }
}
