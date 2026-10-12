package com.studenthub.modules.aichat.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.aichat.controller.dto.AiConversationResponse;
import com.studenthub.modules.aichat.controller.dto.AiAudioTranscribeRequest;
import com.studenthub.modules.aichat.controller.dto.AiMessageResponse;
import com.studenthub.modules.aichat.controller.dto.CreateConversationRequest;
import com.studenthub.modules.aichat.controller.dto.SendMessageRequest;
import com.studenthub.modules.aichat.service.AiConversationService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/app/ai")
@Tag(name = "小程序-AI助手", description = "小程序端 AI 对话与语音转写")
@RequiredArgsConstructor
public class AppAiChatController {

    private final AiConversationService aiConversationService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/conversations")
    @Operation(summary = "创建 AI 会话", description = "创建一个新的 AI 助手会话。", operationId = "createAiConversation")
    @PreAuthorize("hasAuthority('ai:chat')")
    public Result<AiConversationResponse> create(@RequestBody(required = false) @Valid CreateConversationRequest request) {
        Long userId = currentUserProvider.requireCurrentUser().userId();
        return Result.success(aiConversationService.createConversation(userId,
                request == null ? null : request.title()));
    }

    @GetMapping("/conversations")
    @Operation(summary = "分页查询我的会话", description = "按最近活跃倒序返回当前用户的会话。",
            operationId = "pageMyAiConversations")
    @PreAuthorize("hasAuthority('ai:chat')")
    public Result<PageResult<AiConversationResponse>> pageMy(Integer pageNum, Integer pageSize) {
        Long userId = currentUserProvider.requireCurrentUser().userId();
        return Result.success(aiConversationService.pageMyConversations(userId, pageNum, pageSize));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "查询会话消息", description = "按时间正序返回会话内全部消息，仅会话属主可查。",
            operationId = "listAiMessages")
    @PreAuthorize("hasAuthority('ai:chat')")
    public Result<List<AiMessageResponse>> messages(@PathVariable Long conversationId) {
        Long userId = currentUserProvider.requireCurrentUser().userId();
        return Result.success(aiConversationService.listMessages(userId, conversationId));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "发送消息", description = "发送用户消息并返回助手回复；AI 暂不可用时返回降级回复。",
            operationId = "sendAiMessage")
    @PreAuthorize("hasAuthority('ai:chat')")
    public Result<AiMessageResponse> send(@PathVariable Long conversationId,
                                          @RequestBody @Valid SendMessageRequest request) {
        var user = currentUserProvider.requireCurrentUser();
        return Result.success(aiConversationService.sendMessage(user, conversationId, request));
    }

    @PostMapping("/speech/transcribe")
    @Operation(summary = "语音转写", description = "上传音频文件（mp3/aac/m4a/wav）返回转写文本。",
            operationId = "transcribeAiAudio")
    @PreAuthorize("hasAuthority('ai:chat')")
    public Result<String> transcribe(@RequestBody @Valid AiAudioTranscribeRequest request) {
        return Result.success(aiConversationService.transcribeAudio(request.fileId()));
    }
}
