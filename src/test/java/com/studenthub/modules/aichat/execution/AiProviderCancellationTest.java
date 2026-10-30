package com.studenthub.modules.aichat.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.integration.ai.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class AiProviderCancellationTest {
    @Test void cancellingSilentProviderClosesResponseBodyWithoutWaitingForReadTimeout() throws Exception {
        CountDownLatch delta = new CountDownLatch(1), releaseServer = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var body = exchange.getResponseBody()) {
                body.write("data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n".getBytes(StandardCharsets.UTF_8));
                body.flush();
                try { releaseServer.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        server.start();
        var executor = Executors.newSingleThreadExecutor();
        var cancellation = new AiCallCancellation();
        try {
            var provider = new OpenAiCompatibleProvider(new AiProperties(), new ObjectMapper(), HttpClient.newBuilder());
            var binding = new AiModelBinding(998899L, "http://127.0.0.1:" + server.getAddress().getPort(), "test", "test", "OPENAI_CHAT", true);
            Future<?> task = executor.submit(() -> cancellation.run(() -> provider.chatStreamWithBinding(binding,
                    new AiChatRequest("test", List.of(new AiMessage("user", "hello"))),
                    text -> delta.countDown(), (model, input, output) -> fail("Cancelled provider must not complete"))));
            assertTrue(delta.await(3, TimeUnit.SECONDS));
            cancellation.cancel();
            var error = assertThrows(ExecutionException.class, () -> task.get(2, TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class, error.getCause());
        } finally { releaseServer.countDown(); executor.shutdownNow(); server.stop(0); }
    }
}
