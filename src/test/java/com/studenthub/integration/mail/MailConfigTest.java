package com.studenthub.integration.mail;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailConfigTest {

    @Test
    void buildsSenderFromMailProperties() {
        MailProperties properties = new MailProperties();
        properties.setHost("smtp.example.com");
        properties.setPort(587);
        properties.setUsername("no-reply@example.com");
        properties.setPassword("secret");
        properties.setFrom("no-reply@example.com");
        properties.setSslEnabled(false);
        properties.setStarttlsEnabled(true);

        JavaMailSenderImpl sender = MailConfig.build(properties);

        assertEquals("smtp.example.com", sender.getHost());
        assertEquals(587, sender.getPort());
        assertEquals("no-reply@example.com", sender.getUsername());
        assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable"));
        assertEquals("false", sender.getJavaMailProperties().getProperty("mail.smtp.ssl.enable"));
    }

    @Test
    void applyReplacesCachedSessionAfterHotReload() {
        MailProperties properties = new MailProperties();
        properties.setHost("smtp-old.example.com");
        properties.setPort(465);
        JavaMailSenderImpl sender = MailConfig.build(properties);
        var oldSession = sender.getSession();

        properties.setHost("smtp-new.example.com");
        properties.setPort(587);
        properties.setConnectTimeoutMs(1234);
        MailConfig.apply(sender, properties);

        var newSession = sender.getSession();
        assertNotSame(oldSession, newSession, "热更新后必须替换缓存 Session");
        assertEquals("smtp-new.example.com", sender.getHost());
        assertEquals(587, sender.getPort());
        assertEquals("1234", newSession.getProperty("mail.smtp.connectiontimeout"));
        assertTrue(sender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable").equals("false"));
    }
}
