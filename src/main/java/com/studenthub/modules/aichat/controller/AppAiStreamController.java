package com.studenthub.modules.aichat.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.aichat.controller.dto.SendMessageRequest;
import com.studenthub.modules.aichat.service.AiConversationService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * App AI 增强（API需求清单 6.x）：SSE 流式对话 + 空态场景推荐。
 * SSE 事件：delta=增量文本；done=结束（messageId/content/model）；error=失败原因。
 * 结构化卡片与追问建议由 actions 字段承载（随 done 事件的 message 一并下发），
 * 前端按 action.type 渲染日程/工单/入口卡片。
 */
@Slf4j
@RestController
@RequestMapping("/api/app/ai")
@Tag(name = "小程序-AI 助手增强", description = "SSE 流式对话与空态推荐")
@RequiredArgsConstructor
public class AppAiStreamController {

    private final AiConversationService aiConversationService;
    private final CurrentUserProvider currentUserProvider;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "app-ai-stream");
        t.setDaemon(true);
        return t;
    });

    @PostMapping(value = "/conversations/{conversationId}/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "App 端流式对话", description = "SSE 事件流：delta=增量文本，done=结束（含完整消息，actions 承载结构化卡片），error=失败原因。"
            + "入参与普通发送一致（content/attachments）。", operationId = "appChatStream")
    @PreAuthorize("hasAuthority('ai:chat')")
    public SseEmitter chatStream(@PathVariable Long conversationId,
                                 @RequestBody @Valid SendMessageRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        var user = currentUserProvider.requireCurrentUser();
        streamExecutor.execute(() -> aiConversationService.sendMessageStream(user, conversationId, request,
                delta -> send(emitter, "delta", delta),
                message -> {
                    send(emitter, "done", message);
                    emitter.complete();
                },
                error -> {
                    send(emitter, "error", error);
                    emitter.complete();
                }));
        return emitter;
    }

    @GetMapping("/recommendations")
    @Operation(summary = "AI 空态 2×2 场景推荐", description = "四类固定推荐卡（查课表/查进度/办事指引/校园生活），前端空态直接渲染。",
            operationId = "aiRecommendations")
    @PreAuthorize("hasAuthority('ai:chat')")
    public Result<List<Map<String, String>>> recommendations() {
        return Result.success(List.of(
                Map.of("category", "SCHEDULE", "title", "查课表与作息", "subtitle", "快速获取今日课程与空教室",
                        "icon", "calendar", "promptText", "查一下我今天下午有什么课？"),
                Map.of("category", "WORK_ORDER", "title", "报修与办事进度", "subtitle", "实时跟进维修与审批节点",
                        "icon", "tool", "promptText", "查一下我正在处理中的报修工单进度"),
                Map.of("category", "GUIDE", "title", "办事指引问答", "subtitle", "证明开具、请假与场地流程",
                        "icon", "document", "promptText", "在读证明怎么申请？需要带什么材料？"),
                Map.of("category", "CAMPUS", "title", "校园生活咨询", "subtitle", "校车时刻、电费充值与图书馆",
                        "icon", "compass", "promptText", "新校区去老校区的校车发车时间表")));
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data == null ? "" : data));
        } catch (Exception ignored) {
            // 客户端断开时 send 抛异常，忽略即可；emitter 由超时/complete 回收
        }
    }
}
