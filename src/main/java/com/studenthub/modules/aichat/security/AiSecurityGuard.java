package com.studenthub.modules.aichat.security;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.aichat.mapper.AiMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 消息安全守卫：敏感词拦截、每分钟限流、单用户每日配额。
 *
 * <p>集成位置（{@code AiConversationServiceImpl} 的非流式与流式两条入口，均在消息落库前）：
 * 安全策略关闭、阈值配 0、敏感词为空时全部放行，因此默认配置与改造前行为一致。
 * 工具白名单当前只做配置与展示（工具注册表尚未落地），见管理端状态接口的
 * {@code toolWhitelistEnforced=false}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiSecurityGuard {

    private final AiMessageMapper messageMapper;

    /**
     * 校验一条用户消息；命中策略时抛出业务异常（对用户即 400/429，不会写入消息）。
     *
     * @param userId  当前用户
     * @param content 用户消息内容
     */
    public void checkUserMessage(Long userId, String content) {
        AiSecurityRuntime.Snapshot policy = AiSecurityRuntime.current();
        if (!policy.enabled()) {
            return;
        }
        if (policy.blockOnHit() && hitsSensitiveWord(policy.sensitiveWords(), content)) {
            log.warn("AI 消息命中敏感词策略, userId={}", userId);
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "消息包含不允许的内容，已被安全策略拦截");
        }
        if (policy.rateLimitPerMinute() > 0) {
            long lastMinute = messageMapper.countUserMessagesSince(userId, LocalDateTime.now().minusMinutes(1));
            if (lastMinute >= policy.rateLimitPerMinute()) {
                log.info("AI 消息触发分钟级限流, userId={}, limit={}", userId, policy.rateLimitPerMinute());
                throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
            }
        }
        if (policy.dailyQuotaPerUser() > 0) {
            LocalDateTime dayStart = LocalDate.now().atStartOfDay();
            long today = messageMapper.countUserMessagesSince(userId, dayStart);
            if (today >= policy.dailyQuotaPerUser()) {
                log.info("AI 消息触发每日配额, userId={}, quota={}", userId, policy.dailyQuotaPerUser());
                throw new BusinessException(ErrorCode.AI_RATE_LIMITED, "今日 AI 使用次数已达上限，请明天再试");
            }
        }
    }

    private boolean hitsSensitiveWord(List<String> words, String content) {
        if (words == null || words.isEmpty() || content == null || content.isEmpty()) {
            return false;
        }
        String normalized = content.toLowerCase();
        for (String word : words) {
            if (word != null && !word.isBlank() && normalized.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
