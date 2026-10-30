package com.studenthub.config;

import com.studenthub.modules.community.realtime.RedisRealtimeEventBus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(prefix="studenthub.security", name="session-store", havingValue="redis")
public class RealtimeRedisConfig {
    @Bean
    RedisMessageListenerContainer realtimeRedisListener(RedisConnectionFactory connections, RedisRealtimeEventBus bus) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connections);
        container.addMessageListener((message, pattern) -> bus.receive(new String(message.getBody(), StandardCharsets.UTF_8)),
            new ChannelTopic(RedisRealtimeEventBus.TOPIC));
        return container;
    }
}
