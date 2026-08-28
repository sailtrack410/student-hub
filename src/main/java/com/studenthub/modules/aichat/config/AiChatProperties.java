package com.studenthub.modules.aichat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studenthub.ai-chat")
public class AiChatProperties {

    /** 单用户每小时允许发送的消息条数上限 */
    private int hourlyMessageLimit = 20;

    /** 每次请求携带的历史消息条数（不含当前消息） */
    private int historyWindow = 10;

    /** 单条用户消息最大字符数 */
    private int maxMessageCharacters = 2000;
}
