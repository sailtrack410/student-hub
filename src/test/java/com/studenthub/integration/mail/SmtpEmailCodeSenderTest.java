package com.studenthub.integration.mail;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmtpEmailCodeSenderTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> noSender = mock(ObjectProvider.class);

    @Test
    void reportsNotConfiguredWhenDisabled() {
        MailProperties properties = new MailProperties();
        SmtpEmailCodeSender sender = new SmtpEmailCodeSender(properties, noSender, new MockEnvironment());

        assertFalse(sender.isConfigured());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> sender.sendVerificationCode("student@example.com", "123456", "REGISTER"));
        assertEquals(ErrorCode.EMAIL_NOT_CONFIGURED.getCode(), exception.getCode());
    }

    @Test
    void exposesDevCodeOnlyUnderDevOrTestProfiles() {
        MailProperties properties = new MailProperties();
        properties.setDevReturnCode(true);

        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        assertTrue(new SmtpEmailCodeSender(properties, noSender, dev).devCodeVisible());

        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertFalse(new SmtpEmailCodeSender(properties, noSender, prod).devCodeVisible(),
                "生产 profile 即使误配 dev-return-code=true 也不能返回验证码");
    }

    @Test
    void isConfiguredRequiresEnabledHostFromAndSenderBean() {
        MailProperties properties = new MailProperties();
        properties.setEnabled(true);
        properties.setHost("smtp.example.com");
        properties.setFrom("no-reply@example.com");
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(javaMailSender);

        assertTrue(new SmtpEmailCodeSender(properties, provider, new MockEnvironment()).isConfigured());
    }
}
