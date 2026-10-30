package com.studenthub.modules.identity.security;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.community.realtime.RedisRealtimeEventBus;
import com.studenthub.modules.community.realtime.RealtimeEventBus;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.ChannelTopic;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assumptions;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class DistributedCaptchaTest {
    @Test
    void memoryChallengeIsOneUseAndExpires() throws Exception {
        var store = new MemoryLoginCaptchaStore();
        store.put("once", "ABCD", Duration.ofSeconds(10));
        assertThat(store.consume("once")).isEqualTo("ABCD");
        assertThat(store.consume("once")).isNull();
        store.put("old", "ABCD", Duration.ofMillis(1));
        Thread.sleep(10);
        assertThat(store.consume("old")).isNull();
    }
    @Test
    void twoInstancesConsumeOneChallengeAtomically() throws Exception {
        String executable = System.getenv().getOrDefault("REDIS_SERVER_BIN", "/opt/homebrew/bin/redis-server");
        Assumptions.assumeTrue(java.nio.file.Files.isExecutable(java.nio.file.Path.of(executable)), "local Redis binary required for cross-instance integration");
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        var process = new ProcessBuilder(executable,"--port",Integer.toString(port),"--bind","127.0.0.1","--save","","--appendonly","no")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        var connection = new LettuceConnectionFactory("127.0.0.1",port);
        var executor = Executors.newFixedThreadPool(2);
        try {
            connection.afterPropertiesSet(); connection.start();
            var redis = new StringRedisTemplate(connection);
            for (int i=0;;i++) { try { redis.getConnectionFactory().getConnection().ping(); break; } catch (RuntimeException e) { if(i>=50)throw e; Thread.sleep(20); } }
            var first = new LoginCaptchaService(new RedisLoginCaptchaStore(redis));
            var second = new LoginCaptchaService(new RedisLoginCaptchaStore(redis));
            var challenge = first.issue();
            var start = new CountDownLatch(1);
            Callable<Boolean> consume = () -> { start.await(); try {second.verify(challenge.captchaId(),challenge.answer());return true;}catch(com.studenthub.exception.BusinessException e){return false;} };
            var one=executor.submit(consume);var two=executor.submit(consume);start.countDown();
            assertThat((one.get()?1:0)+(two.get()?1:0)).isEqualTo(1);
            assertThat(redis.getExpire("auth:login-captcha:"+challenge.captchaId())).isNegative();
            var invalid=first.issue();
            assertThatThrownBy(() -> second.verify(invalid.captchaId(),"wrong")).isInstanceOf(com.studenthub.exception.BusinessException.class);
            assertThatThrownBy(() -> first.verify(invalid.captchaId(),invalid.answer())).isInstanceOf(com.studenthub.exception.BusinessException.class);
            var received = new LinkedBlockingQueue<Object>();
            var nodeOne = new RedisRealtimeEventBus(redis,new ObjectMapper(),event -> {});
            var nodeTwo = new RedisRealtimeEventBus(redis,new ObjectMapper(),received::add);
            var listener = new RedisMessageListenerContainer();
            listener.setConnectionFactory(connection);
            listener.addMessageListener((message, pattern) -> nodeTwo.receive(new String(message.getBody(),StandardCharsets.UTF_8)),new ChannelTopic(RedisRealtimeEventBus.TOPIC));
            listener.afterPropertiesSet(); listener.start();
            try {
                nodeOne.presence(42L,true);
                assertThat(nodeTwo.isOnline(42L)).isTrue();
                nodeTwo.presence(42L,true);
                nodeOne.presence(42L,false);
                assertThat(nodeOne.isOnline(42L)).isTrue();
                nodeTwo.presence(42L,false);
                assertThat(nodeOne.isOnline(42L)).isFalse();
                nodeOne.publish(42L,"NOTIFICATION",java.util.Map.of("messageId",7));
                var delivery=(RealtimeEventBus.Delivery)received.poll(5,TimeUnit.SECONDS);
                assertThat(delivery).isNotNull();
                assertThat(delivery.recipientId()).isEqualTo(42L);
                assertThat(delivery.type()).isEqualTo("NOTIFICATION");
            } finally {listener.stop();listener.destroy();}
        } finally {executor.shutdownNow(); connection.destroy(); process.destroy(); process.waitFor(3,TimeUnit.SECONDS);if(process.isAlive())process.destroyForcibly();}
    }
}
