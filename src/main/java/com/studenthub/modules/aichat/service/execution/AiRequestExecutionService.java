package com.studenthub.modules.aichat.service.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.ai.AiCallCancellation;
import com.studenthub.modules.aichat.controller.dto.AiMessageResponse;
import com.studenthub.modules.aichat.controller.dto.SendMessageRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

/** 独立已提交的请求占位防止跨实例并发；消息写入与重放结果在同一事务内提交。 */
@Service
public class AiRequestExecutionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate isolated;

    public AiRequestExecutionService(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        isolated = new TransactionTemplate(manager);
        isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public AiMessageResponse execute(Long userId, Long conversationId, SendMessageRequest request,
                                     Supplier<AiMessageResponse> work) {
        AiCallCancellation.checkCurrent();
        String requestId = request.clientRequestId();
        if (requestId == null || requestId.isBlank()) {
            return isolated.execute(status -> {
                AiMessageResponse response = work.get();
                AiCallCancellation.checkCurrent();
                return response;
            });
        }
        if (requestId.length() > 96 || !requestId.equals(requestId.trim())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "请求标识格式不合法");
        }
        String hash = payloadHash(request);
        boolean owner;
        try {
            isolated.executeWithoutResult(status -> jdbc.update("""
                    INSERT INTO ai_chat_request(user_id, conversation_id, client_request_id, payload_hash, status)
                    VALUES (?, ?, ?, ?, 'RUNNING')
                    """, userId, conversationId, requestId, hash));
            owner = true;
        } catch (DuplicateKeyException exception) { owner = false; }
        if (!owner) return replay(userId, conversationId, requestId, hash);
        try {
            return isolated.execute(status -> {
                AiMessageResponse response = work.get();
                AiCallCancellation.checkCurrent();
                jdbc.update("""
                        UPDATE ai_chat_request SET status='COMPLETED', response_json=?, updated_at=CURRENT_TIMESTAMP
                        WHERE user_id=? AND conversation_id=? AND client_request_id=?
                        """, serialize(response), userId, conversationId, requestId);
                return response;
            });
        } catch (RuntimeException exception) {
            // 清除中断仅用于终态持久化，结束后恢复；否则驱动可能拒绝取消后的数据库操作。
            boolean interrupted = Thread.interrupted();
            boolean cancelled = interrupted || exception instanceof CancellationException;
            try {
                isolated.executeWithoutResult(status -> jdbc.update("""
                        UPDATE ai_chat_request SET status=?, updated_at=CURRENT_TIMESTAMP
                        WHERE user_id=? AND conversation_id=? AND client_request_id=? AND status='RUNNING'
                        """, cancelled ? "CANCELLED" : "FAILED", userId, conversationId, requestId));
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
            throw exception;
        }
    }

    private AiMessageResponse replay(Long userId, Long conversationId, String requestId, String hash) {
        RequestState row = jdbc.queryForObject("""
                SELECT payload_hash,status,response_json,created_at FROM ai_chat_request
                WHERE user_id=? AND conversation_id=? AND client_request_id=?
                """, (result, index) -> new RequestState(result.getString(1), result.getString(2),
                result.getString(3), result.getTimestamp(4)), userId, conversationId, requestId);
        if (row == null || !hash.equals(row.hash())) throw new BusinessException(ErrorCode.AI_REQUEST_CONFLICT);
        return switch (row.status()) {
            case "COMPLETED" -> deserialize(row.response());
            case "CANCELLED" -> throw new BusinessException(ErrorCode.AI_REQUEST_CANCELLED);
            case "FAILED" -> throw new BusinessException(ErrorCode.AI_REQUEST_FAILED);
            // 崩溃遗留的占位也不再次调用供应商；用户可明确选择发起新问题。
            default -> throw new BusinessException(row.createdAt().toLocalDateTime().isBefore(LocalDateTime.now().minusMinutes(5))
                    ? ErrorCode.AI_REQUEST_FAILED : ErrorCode.AI_REQUEST_IN_PROGRESS);
        };
    }

    private String payloadHash(SendMessageRequest request) {
        try {
            byte[] canonical = mapper.writeValueAsBytes(List.of(
                    request.content() == null ? "" : request.content().trim(),
                    request.effectiveImageIds().stream().filter(java.util.Objects::nonNull).distinct().toList(),
                    request.effectiveDocumentIds().stream().filter(java.util.Objects::nonNull).distinct().toList()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception exception) { throw new IllegalStateException("消息摘要计算失败", exception); }
    }

    private String serialize(AiMessageResponse response) {
        try { return mapper.writeValueAsString(response); }
        catch (Exception exception) { throw new IllegalStateException("消息结果保存失败", exception); }
    }

    private AiMessageResponse deserialize(String json) {
        try { return mapper.readValue(json, AiMessageResponse.class); }
        catch (Exception exception) { throw new BusinessException(ErrorCode.AI_REQUEST_FAILED); }
    }

    private record RequestState(String hash, String status, String response, Timestamp createdAt) { }
}
