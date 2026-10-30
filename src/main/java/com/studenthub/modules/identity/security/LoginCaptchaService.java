package com.studenthub.modules.identity.security;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * 登录图形验证码：服务端生成一次性 4 位字符，渲染 SVG data URI，2 分钟内有效。
 *
 * <p>验证码只在启用 {@code security.login_captcha_enabled} 时由登录接口消费；无论校验成功
 * 与否都立即失效，防止同一张图被暴力枚举。验证码按 session-store 选择本机或 Redis 存储，不下发到响应。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginCaptchaService {

    private static final int WIDTH = 124;
    private static final int HEIGHT = 40;
    private static final int CODE_LENGTH = 4;
    private static final int TTL_SECONDS = 120;
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final String[] COLORS = {"#1F4E79", "#8A3B12", "#355E3B", "#5B3A8E", "#8B1E3F"};

    private final SecureRandom random = new SecureRandom();
    private final LoginCaptchaStore store;

    /** 服务内部使用；{@code answer} 不得序列化到 HTTP 响应。 */
    public record CaptchaChallenge(String captchaId, String answer, String image, int expiresInSeconds) {
    }

    public CaptchaChallenge issue() {
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        store.put(captchaId, code, java.time.Duration.ofSeconds(TTL_SECONDS));
        String svg = renderSvg(code);
        String image = "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return new CaptchaChallenge(captchaId, code, image, TTL_SECONDS);
    }

    /**
     * 校验并立即消费验证码。失败统一抛 {@link ErrorCode#CAPTCHA_INVALID}，
     * 不区分“不存在/过期/错误”，避免向调用方泄露验证码生命周期。
     */
    public void verify(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
        }
        String answer = store.consume(captchaId.trim());
        if (answer == null || !answer.equalsIgnoreCase(captchaCode.trim())) {
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
        }
    }

    private String randomCode() {
        StringBuilder out = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            out.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return out.toString();
    }

    private String renderSvg(String code) {
        StringBuilder svg = new StringBuilder(512);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(WIDTH)
                .append("\" height=\"").append(HEIGHT)
                .append("\" viewBox=\"0 0 ").append(WIDTH).append(' ').append(HEIGHT).append("\">");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#F5F7FA\"/>");
        for (int i = 0; i < 5; i++) {
            svg.append("<line x1=\"").append(random.nextInt(WIDTH))
                    .append("\" y1=\"").append(random.nextInt(HEIGHT))
                    .append("\" x2=\"").append(random.nextInt(WIDTH))
                    .append("\" y2=\"").append(random.nextInt(HEIGHT))
                    .append("\" stroke=\"").append(COLORS[random.nextInt(COLORS.length)])
                    .append("\" stroke-width=\"1\" opacity=\"0.45\"/>");
        }
        for (int i = 0; i < 22; i++) {
            svg.append("<circle cx=\"").append(random.nextInt(WIDTH))
                    .append("\" cy=\"").append(random.nextInt(HEIGHT))
                    .append("\" r=\"").append(1 + random.nextInt(2))
                    .append("\" fill=\"").append(COLORS[random.nextInt(COLORS.length)])
                    .append("\" opacity=\"0.35\"/>");
        }
        for (int i = 0; i < code.length(); i++) {
            int x = 18 + i * 26 + random.nextInt(4);
            int y = 27 + random.nextInt(5);
            int angle = -18 + random.nextInt(37);
            svg.append("<text x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" font-family=\"Menlo, Consolas, monospace\" font-size=\"24\" font-weight=\"700\"")
                    .append(" fill=\"").append(COLORS[random.nextInt(COLORS.length)])
                    .append("\" transform=\"rotate(").append(angle).append(' ').append(x).append(' ').append(y)
                    .append(")\">").append(code.charAt(i)).append("</text>");
        }
        svg.append("</svg>");
        return svg.toString();
    }

}
