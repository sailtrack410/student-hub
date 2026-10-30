package com.studenthub.modules.appversion.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.appversion.controller.dto.AdminAppVersionRequest;
import com.studenthub.modules.appversion.controller.dto.AppVersionResponse;
import com.studenthub.modules.appversion.service.AppVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 管理端-版本发布：App 安装包版本创建、原始流上传、发布/下线/删除与下载。
 * 安装包上传不走 multipart（避开全局 5MB 限制），直接以 application/octet-stream 流式落对象存储。
 */
@RestController
@RequestMapping("/api/admin/app-versions")
@Tag(name = "管理端-版本发布", description = "App 安装包版本管理与发布")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:user:manage')")
public class AdminAppVersionController {

    private final AppVersionService appVersionService;

    @GetMapping
    @Operation(summary = "版本列表", description = "按 versionCode 倒序；可按平台与状态过滤。",
            operationId = "adminListAppVersions")
    public Result<List<AppVersionResponse>> list(@RequestParam(required = false) String platform,
                                                 @RequestParam(required = false) String status) {
        return Result.success(appVersionService.list(platform, status));
    }

    @PostMapping
    @Operation(summary = "创建版本草稿", description = "同一平台+渠道下版本号唯一；创建后先上传安装包或填写外链，再发布。",
            operationId = "adminCreateAppVersion")
    public Result<AppVersionResponse> create(@RequestBody @Valid AdminAppVersionRequest request) {
        return Result.success(appVersionService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新版本草稿", description = "仅 DRAFT 可改。", operationId = "adminUpdateAppVersion")
    public Result<AppVersionResponse> update(@PathVariable Long id,
                                             @RequestBody @Valid AdminAppVersionRequest request) {
        return Result.success(appVersionService.update(id, request));
    }

    @PostMapping(value = "/{id}/package", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "上传安装包（原始流）",
            description = "body 即文件字节，Content-Type: application/octet-stream；query fileName 必填（.apk/.ipa/.hap）；"
                    + "≤300MB；服务端校验 ZIP 魔数并流式计算 SHA-256。仅 DRAFT 可上传。",
            operationId = "adminUploadAppVersionPackage")
    public Result<AppVersionResponse> uploadPackage(@PathVariable Long id,
                                                    @RequestParam String fileName,
                                                    HttpServletRequest request) throws IOException {
        return Result.success(appVersionService.uploadPackage(id, fileName,
                request.getInputStream(), request.getContentLengthLong()));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布版本", description = "发布须已上传安装包或配置外部下载地址；同平台+渠道旧发布版本自动下线。",
            operationId = "adminPublishAppVersion")
    public Result<AppVersionResponse> publish(@PathVariable Long id) {
        return Result.success(appVersionService.publish(id));
    }

    @PostMapping("/{id}/offline")
    @Operation(summary = "下线版本", description = "仅 PUBLISHED 可下线。", operationId = "adminOfflineAppVersion")
    public Result<AppVersionResponse> offline(@PathVariable Long id) {
        return Result.success(appVersionService.offline(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除版本", description = "仅 DRAFT/OFFLINE 可删除，逻辑删除。",
            operationId = "adminDeleteAppVersion")
    public Result<Void> delete(@PathVariable Long id) {
        appVersionService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "下载安装包（管理端校验用）", description = "流式下载；任意状态可下载已上传的包。",
            operationId = "adminDownloadAppVersionPackage")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        appVersionService.downloadForAdmin(id, response);
    }
}
