package com.studenthub.modules.screen.controller;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.aichat.service.AiSpeechService;
import com.studenthub.modules.screen.controller.dto.PublicChatRequest;
import com.studenthub.modules.screen.controller.dto.PublicChatResponse;
import com.studenthub.modules.screen.controller.dto.ScreenCatalogResponse;
import com.studenthub.modules.screen.controller.dto.ScreenNoticeResponse;
import com.studenthub.modules.screen.controller.dto.ScreenOrderProgressResponse;
import com.studenthub.modules.screen.controller.dto.ScreenOverviewResponse;
import com.studenthub.modules.screen.service.ScreenRateLimiter;
import com.studenthub.modules.screen.service.ScreenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 公开大屏只读/互动接口：免登录，白名单 + IP 限流，仅返回脱敏数据与公开办事信息。
 */
@Slf4j
@RestController
@RequestMapping("/api/public/screen")
@Tag(name = "公开-数据大屏")
@RequiredArgsConstructor
public class PublicScreenController {

    /** 问答与语音的窗口内限额（免费 AI 预算保护），普通统计查询为 60 */
    private static final int AI_LIMIT = 20;

    private final ScreenService screenService;
    private final ScreenRateLimiter rateLimiter;
    private final AiSpeechService aiSpeechService;

    @GetMapping("/overview")
    @Operation(summary = "大屏运营总览", description = "免登录只读；按 IP 限流；仅返回脱敏统计数据。",
            operationId = "getPublicScreenOverview")
    public Result<ScreenOverviewResponse> overview(HttpServletRequest request) {
        String key = clientKey(request);
        if (!rateLimiter.tryAcquire(key)) {
            throw new BusinessException(ErrorCode.PUBLIC_RATE_LIMITED);
        }
        return Result.success(screenService.overview());
    }

    @GetMapping("/catalog")
    @Operation(summary = "公开服务目录", description = "免登录只读；分类与事项的公开办事指南。",
            operationId = "getPublicScreenCatalog")
    public Result<ScreenCatalogResponse> catalog(HttpServletRequest request) {
        if (!rateLimiter.tryAcquire(clientKey(request))) {
            throw new BusinessException(ErrorCode.PUBLIC_RATE_LIMITED);
        }
        return Result.success(screenService.catalog());
    }

    @GetMapping("/track/{no}")
    @Operation(summary = "单号查进度", description = "免登录；凭 WO-/SR- 单号查询公开办理时间线（已脱敏）。",
            operationId = "getPublicScreenTrack")
    public Result<ScreenOrderProgressResponse> track(@PathVariable String no, HttpServletRequest request) {
        if (!rateLimiter.tryAcquire(clientKey(request))) {
            throw new BusinessException(ErrorCode.PUBLIC_RATE_LIMITED);
        }
        return Result.success(screenService.track(no));
    }

    @GetMapping("/notices")
    @Operation(summary = "信息要闻", description = "免登录只读；公开通知/要闻/活动，供终端待机屏与主控台轮播。",
            operationId = "getPublicScreenNotices")
    public Result<ScreenNoticeResponse> notices(HttpServletRequest request) {
        if (!rateLimiter.tryAcquire(clientKey(request))) {
            throw new BusinessException(ErrorCode.PUBLIC_RATE_LIMITED);
        }
        return Result.success(screenService.notices());
    }

    @PostMapping("/chat")
    @Operation(summary = "公开 AI 问答", description = "免登录；基于公开办事指南回答，不编造。",
            operationId = "postPublicScreenChat")
    public Result<PublicChatResponse> chat(@Valid @RequestBody PublicChatRequest body,
                                           HttpServletRequest request) {
        String key = clientKey(request);
        String chatKey = "chat:" + key;
        if (!rateLimiter.tryAcquire(chatKey, AI_LIMIT) || !rateLimiter.tryAcquire(key)) {
            throw new BusinessException(ErrorCode.PUBLIC_RATE_LIMITED);
        }
        List<ScreenService.ChatTurn> history = body.history() == null ? List.of()
                : body.history().stream()
                        .map(turn -> new ScreenService.ChatTurn(turn.role(), turn.content()))
                        .toList();
        return Result.success(screenService.chat(body.question(), history));
    }

    @PostMapping(value = "/chat/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "公开语音问答（转写为文字）", description = "免登录；将现场录音转写为文字后由前端带文字提问。",
            operationId = "postPublicScreenAudio")
    public Result<Map<String, String>> audio(@RequestPart("file") MultipartFile file,
                                             HttpServletRequest request) {
        if (!rateLimiter.tryAcquire("audio:" + clientKey(request), AI_LIMIT)) {
            throw new BusinessException(ErrorCode.PUBLIC_RATE_LIMITED);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "音频内容为空");
        }
        try {
            String text = aiSpeechService.transcribe(file.getBytes(), file.getOriginalFilename(),
                    file.getContentType());
            return Result.success(Map.of("text", text));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "音频读取失败");
        }
    }

    /** 优先取代理透传的真实 IP */
    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}