package com.studenthub.modules.devtool;

import com.studenthub.common.Result;
import java.nio.charset.StandardCharsets;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.jwauth.config.JwProperties;
import com.studenthub.modules.jwauth.security.JwSessionStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开发期调试接口：用托管会话探测任意教务相对路径，返回状态/跳转/响应片段并转储。
 * 仅 dev 生效。
 */
@RestController
@RequestMapping("/api/dev/jw")
@Profile("dev")
@RequiredArgsConstructor
@Tag(name = "dev-调试", description = "开发期教务端点探测（仅 dev）")
public class DevJwProbeController {

    private final CurrentUserProvider currentUserProvider;
    private final JwSessionStore jwSessionStore;
    private final JwProperties properties;

    @GetMapping("/probe")
    @Operation(summary = "用托管教务会话探测任意教务相对路径，返回状态/跳转/响应片段")
    public Result<Map<String, Object>> probe(@RequestParam("path") String path) throws Exception {
        return probeInternal(path, null);
    }

    @org.springframework.web.bind.annotation.PostMapping("/probe-post")
    @Operation(summary = "同 probe，POST 方式探测教务相对路径")
    public Result<Map<String, Object>> probePost(
            @RequestParam("path") String path,
            @RequestParam(value = "body", required = false) String body) throws Exception {
        return probeInternal(path, body);
    }

    private Result<Map<String, Object>> probeInternal(String path, String body) throws Exception {
        Long userId = currentUserProvider.requireCurrentUser().userId();
        var session = jwSessionStore.find(userId).orElse(null);
        if (session == null) {
            return Result.failed("DEV_NO_SESSION", "无托管教务会话");
        }
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(properties.getJwBaseUrl() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Cookie", session.cookieHeader());
        if (body != null) {
            request.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            request.GET();
        }
        HttpResponse<byte[]> response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", response.statusCode());
        result.put("location", response.headers().firstValue("Location").orElse(null));
        result.put("contentType", response.headers().firstValue("Content-Type").orElse(null));
        result.put("size", response.body().length);

        String safe = path.replaceAll("[^A-Za-z0-9]", "_");
        Path dump = Path.of("/tmp/jw-probe-" + safe + ".bin");
        Files.write(dump, response.body());

        // GBK/UTF-8 各尝试一次，取正文前 1200 行内字符
        for (String charset : new String[]{"GBK", "UTF-8"}) {
            String text = new String(response.body(), charset);
            long printable = text.chars().filter(c -> c > 31 && c < 65535).count();
            if (printable > text.length() * 0.9) {
                result.put("preview", text.substring(0, Math.min(600, text.length())));
                break;
            }
        }
        return Result.success(result);
    }
}