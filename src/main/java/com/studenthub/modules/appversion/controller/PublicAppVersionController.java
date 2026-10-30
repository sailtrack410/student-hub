package com.studenthub.modules.appversion.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.appversion.controller.dto.AppVersionCheckResponse;
import com.studenthub.modules.appversion.service.AppVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开-版本发布：App 启动检查更新与安装包下载（免登录，见 SecurityConfig 白名单）。
 */
@RestController
@RequestMapping("/api/public/app-version")
@Tag(name = "公开-版本发布", description = "App 更新检查与安装包下载（免登录）")
@RequiredArgsConstructor
public class PublicAppVersionController {

    private final AppVersionService appVersionService;

    @GetMapping("/latest")
    @Operation(summary = "检查最新版本",
            description = "传 platform（必填）、channel（默认 OFFICIAL）、versionCode（当前版本号）；"
                    + "返回 hasUpdate / forceUpdate（强更或低于最低支持版本）/ latest。无发布版本时 latest=null。",
            operationId = "getLatestAppVersion")
    public Result<AppVersionCheckResponse> latest(@RequestParam String platform,
                                                  @RequestParam(required = false) String channel,
                                                  @RequestParam(required = false) Integer versionCode, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Result.success(appVersionService.check(platform, channel, versionCode));
    }

    @GetMapping("/latest/package")
    @Operation(summary = "稳定下载当前发布版", operationId = "downloadLatestAppVersionPackage")
    public void latestPackage(@RequestParam(defaultValue = "ANDROID") String platform,
                              @RequestParam(required = false) String channel, HttpServletResponse response) {
        appVersionService.downloadLatest(platform, channel, response);
    }

    @GetMapping("/{id}/package")
    @Operation(summary = "下载已发布安装包", description = "仅 PUBLISHED 版本；流式返回并累加下载量。",
            operationId = "downloadAppVersionPackage")
    public void packageDownload(@PathVariable Long id, HttpServletResponse response) {
        appVersionService.downloadPublic(id, response);
    }
}
