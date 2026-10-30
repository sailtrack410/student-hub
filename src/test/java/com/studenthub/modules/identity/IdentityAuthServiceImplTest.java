package com.studenthub.modules.identity;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.mail.SmtpEmailCodeSender;
import com.studenthub.integration.qq.QqIdentity;
import com.studenthub.integration.qq.QqIdentityProvider;
import com.studenthub.integration.wechat.WechatIdentity;
import com.studenthub.integration.wechat.WechatIdentityProvider;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.identity.controller.dto.SendEmailCodeRequest;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.entity.UserRoleEntity;
import com.studenthub.modules.identity.mapper.IdentityAccessMapper;
import com.studenthub.modules.identity.mapper.RoleMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.mapper.UserRoleMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.AuthenticatedUserFactory;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.identity.security.SecurityProperties;
import com.studenthub.modules.identity.security.SessionClientPolicy;
import com.studenthub.modules.identity.security.SessionStore;
import com.studenthub.modules.identity.security.SessionToken;
import com.studenthub.modules.identity.service.impl.IdentityAuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityAuthServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private IdentityAccessMapper identityAccessMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SessionStore sessionStore;
    @Mock
    private SecurityProperties securityProperties;
    @Mock
    private SessionClientPolicy sessionClientPolicy;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private WechatIdentityProvider wechatIdentityProvider;
    @Mock
    private QqIdentityProvider qqIdentityProvider;
    @Mock
    private AuthenticatedUserFactory authenticatedUserFactory;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private SmtpEmailCodeSender emailCodeSender;

    @InjectMocks
    private IdentityAuthServiceImpl service;

    @Test
    void wechatLoginFailsWhenRequesterRoleMissing() {
        // Arrange: exchange returns a valid identity, no existing user
        when(wechatIdentityProvider.exchange("valid-code"))
                .thenReturn(new WechatIdentity("open-id-123"));
        when(wechatIdentityProvider.defaultCampusId()).thenReturn(1L);
        when(userMapper.findByWechatOpenId("open-id-123")).thenReturn(null);
        when(roleMapper.findByRoleCode("REQUESTER")).thenReturn(null);

        // Act & Assert: should throw, not silently succeed
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.wechatLogin("valid-code"));
        assertEquals(ErrorCode.ROLE_NOT_CONFIGURED.getCode(), ex.getCode());

        // User was inserted but role binding (and thus the transaction) failed
        verify(userMapper).insert(any(UserEntity.class));
        verify(userRoleMapper, never()).insert(any(UserRoleEntity.class));
    }

    @Test
    void sendEmailCodeFailsWhenMailNotConfiguredInNonDevEnvironment() {
        when(emailCodeSender.isConfigured()).thenReturn(false);
        when(emailCodeSender.devCodeVisible()).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendEmailCode(new SendEmailCodeRequest("student@example.com", "REGISTER")));

        assertEquals(ErrorCode.EMAIL_NOT_CONFIGURED.getCode(), ex.getCode());
        verify(emailCodeSender, never()).sendVerificationCode(anyString(), anyString(), anyString());
    }

    @Test
    void sendEmailCodeReturnsDevCodeOnlyWhenDevFallbackAllowed() {
        when(emailCodeSender.isConfigured()).thenReturn(false);
        when(emailCodeSender.devCodeVisible()).thenReturn(true);

        var response = service.sendEmailCode(new SendEmailCodeRequest("dev@example.com", "BIND"));

        org.junit.jupiter.api.Assertions.assertNotNull(response.devCode());
        org.junit.jupiter.api.Assertions.assertTrue(response.devCode().matches("\\d{6}"));
        org.junit.jupiter.api.Assertions.assertTrue(response.message().contains("未配置邮件服务"));
    }

    @Test
    void bindQqOverwritesDefaultProfileFields() {
        UserEntity user = enabledUser();
        user.setDisplayName("用户123456");
        user.setDisplayNameCustomized(false);
        user.setAvatarCustomized(false);
        when(qqIdentityProvider.exchange("qq-token"))
                .thenReturn(new QqIdentity("openid-1", null, "QQ昵称", "https://qq.example/avatar.png"));
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userMapper.findByQqOpenId("openid-1")).thenReturn(null);
        when(authenticatedUserFactory.build(any(UserEntity.class))).thenReturn(currentUser("QQ昵称", null));

        service.bindQq("raw-token", currentUser("用户123456", null), "qq-token", null);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateById(captor.capture());
        UserEntity update = captor.getValue();
        assertEquals("openid-1", update.getQqOpenId());
        assertEquals("QQ昵称", update.getDisplayName());
        assertEquals("https://qq.example/avatar.png", update.getAvatarUrl());
    }

    @Test
    void bindQqKeepsCustomizedProfileFields() {
        UserEntity user = enabledUser();
        user.setDisplayName("自定义昵称");
        user.setDisplayNameCustomized(true);
        user.setAvatarUrl("12345");
        user.setAvatarCustomized(true);
        when(qqIdentityProvider.exchange("qq-token"))
                .thenReturn(new QqIdentity("openid-2", null, "QQ昵称", "https://qq.example/avatar.png"));
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userMapper.findByQqOpenId("openid-2")).thenReturn(null);
        when(authenticatedUserFactory.build(any(UserEntity.class))).thenReturn(currentUser("自定义昵称", "12345"));

        service.bindQq("raw-token", currentUser("自定义昵称", "12345"), "qq-token", null);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateById(captor.capture());
        UserEntity update = captor.getValue();
        assertEquals("openid-2", update.getQqOpenId());
        org.junit.jupiter.api.Assertions.assertNull(update.getDisplayName());
        org.junit.jupiter.api.Assertions.assertNull(update.getAvatarUrl());
    }

    private static final Long USER_ID = 42L;

    private UserEntity enabledUser() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setCampusId(1L);
        user.setDefaultOrganizationId(10L);
        user.setUsername("requester");
        user.setDisplayName("开发学生");
        user.setStatus("ENABLED");
        user.setVersion(0);
        return user;
    }

    private AuthenticatedUser currentUser(String displayName, String avatarFileId) {
        return new AuthenticatedUser(USER_ID, 1L, 10L, "requester", displayName, "STUDENT", "BASIC",
                List.of(), List.of(), avatarFileId, null);
    }
}
