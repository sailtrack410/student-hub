package com.studenthub.integration.mail;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;

/**
 * 校园邮箱验证码发信器。生产环境必须配置 SMTP；开发/测试环境未配置时由调用方决定
 * 是否回退为响应返回 devCode，绝不伪报「已发送」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpEmailCodeSender {

    private final MailProperties properties;
    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
    private final Environment environment;

    public boolean isConfigured() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getHost())
                && StringUtils.hasText(fromAddress())
                && javaMailSenderProvider.getIfAvailable() != null;
    }

    /** 只在 dev/test 且显式允许时把验证码返回客户端；生产 profile 永不返回。 */
    public boolean devCodeVisible() {
        return properties.isDevReturnCode()
                && environment.acceptsProfiles(Profiles.of("dev", "test"));
    }

    public void sendVerificationCode(String email, String code, String purpose) {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_CONFIGURED);
        }
        JavaMailSender sender = javaMailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_CONFIGURED);
        }
        String purposeLabel = purposeLabel(purpose);
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress(), StringUtils.hasText(properties.getFromName())
                    ? properties.getFromName() : fromAddress());
            helper.setTo(email);
            helper.setSubject("【绵城一码通】" + purposeLabel + "验证码");
            helper.setText(plainText(code, purposeLabel), htmlText(code, purposeLabel));
            sender.send(message);
            log.info("邮箱验证码已发送, recipient={}, purpose={}", maskEmail(email), purpose);
        } catch (MailException | MessagingException | UnsupportedEncodingException exception) {
            log.warn("邮箱验证码发送失败, recipient={}, purpose={}, error={}",
                    maskEmail(email), purpose, exception.getMessage());
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private String fromAddress() {
        return StringUtils.hasText(properties.getFrom())
                ? properties.getFrom()
                : properties.getUsername();
    }

    private String purposeLabel(String purpose) {
        return switch (purpose) {
            case "REGISTER" -> "注册账号";
            case "RESET" -> "找回密码";
            case "BIND" -> "绑定邮箱";
            default -> "邮箱";
        };
    }

    private String plainText(String code, String purposeLabel) {
        return """
                你正在进行%s操作，验证码为：%s

                验证码 10 分钟内有效，请勿转发给他人。
                如非本人操作，请忽略本邮件。

                绵城一码通
                """.formatted(purposeLabel, code);
    }

    private String htmlText(String code, String purposeLabel) {
        return """
                <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Noto Sans SC',sans-serif;max-width:520px;margin:0 auto;padding:24px;color:#0f172a;">
                  <h2 style="font-size:18px;margin:0 0 12px;">绵城一码通</h2>
                  <p style="font-size:14px;line-height:1.7;color:#334155;">你正在进行%s操作，验证码为：</p>
                  <div style="font-size:32px;font-weight:700;letter-spacing:8px;color:#2563eb;background:#eff6ff;border-radius:12px;padding:16px 20px;text-align:center;margin:18px 0;">%s</div>
                  <p style="font-size:12px;line-height:1.7;color:#64748b;">验证码 10 分钟内有效，请勿转发给他人。如非本人操作，请忽略本邮件。</p>
                </div>
                """.formatted(purposeLabel, code);
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
