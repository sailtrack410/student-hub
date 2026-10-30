package com.studenthub.modules.push.provider;

import java.util.Map;

/**
 * 单条推送给一个用户的载荷。
 *
 * @param taskId  任务 ID（日志与排查用）
 * @param userId  接收人
 * @param title   标题（已完成变量替换）
 * @param content 正文（已完成变量替换）
 * @param data    结构化附加数据（通知类型、关联业务等）
 */
public record PushMessage(
        Long taskId,
        Long userId,
        String title,
        String content,
        Map<String, Object> data) {
}
