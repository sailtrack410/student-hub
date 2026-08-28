package com.studenthub.modules.jwauth;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.jw.KingosoftJwClient;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.AuthenticatedUserFactory;
import com.studenthub.modules.identity.security.SessionStore;
import com.studenthub.modules.jwauth.config.JwProperties;
import com.studenthub.modules.jwauth.controller.dto.JwAuthRequest;
import com.studenthub.modules.jwauth.controller.dto.JwAuthResult;
import com.studenthub.modules.jwauth.controller.dto.JwStatusResponse;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.jwauth.security.JwSessionStore;
import com.studenthub.modules.jwauth.service.impl.JwAuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwAuthServiceImplTest {

    private static final Long USER_ID = 1002L;
    private static final String RAW_TOKEN = "raw-token";

    @Mock
    private KingosoftJwClient jwClient;
    @Mock
    private JwBindingMapper jwBindingMapper;
    @Mock
    private JwSessionStore jwSessionStore;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuthenticatedUserFactory authenticatedUserFactory;
    @Mock
    private SessionStore sessionStore;

    private JwAuthServiceImpl jwAuthService;

    private UserEntity user;
    private JwAuthRequest request;
    private KingosoftJwClient.JwLoginResult loginResult;

    @BeforeEach
    void setUp() {
        JwProperties properties = new JwProperties();
        jwAuthService = new JwAuthServiceImpl(jwClient, properties, jwBindingMapper, jwSessionStore,
                userMapper, authenticatedUserFactory, sessionStore);

        user = new UserEntity();
        user.setId(USER_ID);
        user.setStatus("ENABLED");
        user.setIdentityType("STUDENT");
        user.setVersion(0);
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        request = new JwAuthRequest("2023590397", "secret-password", "captcha-uid", "11");
        loginResult = new KingosoftJwClient.JwLoginResult(
                "JSESSIONID=abc", "https://jwgl.mycc.edu.cn/frame/homes.action?v=1", "2023590397", "王同学", "STUDENT", "202500002077");
        when(jwClient.login(eq("2023590397"), any(), eq("captcha-uid"), eq("11"))).thenReturn(loginResult);

        AuthenticatedUser refreshed = new AuthenticatedUser(USER_ID, 1L, 10L, "requester",
                "王同学", "STUDENT", "BASIC", List.of(), List.of());
        when(authenticatedUserFactory.build(any())).thenReturn(refreshed);

        when(jwBindingMapper.findByUserId(USER_ID)).thenReturn(null);
        when(jwBindingMapper.findBySsoAccount("2023590397")).thenReturn(null);
    }

    @Test
    void authSavesBindingSessionAndKeepsStudentIdentity() {
        JwAuthResult result = jwAuthService.auth(USER_ID, RAW_TOKEN, request);

        assertEquals("AUTHENTICATED", result.status());
        assertEquals("王同学", result.jwName());
        assertEquals("202500002077", result.jwStudentNo());
        assertEquals(false, result.identityChanged());

        ArgumentCaptor<JwBindingEntity> captor = ArgumentCaptor.forClass(JwBindingEntity.class);
        verify(jwBindingMapper).insert(captor.capture());
        assertEquals("AUTHENTICATED", captor.getValue().getAuthStatus());
        assertNotNull(captor.getValue().getSessionExpiresAt());

        ArgumentCaptor<com.studenthub.modules.jwauth.security.JwSessionStore.JwSession> sessionCaptor =
                ArgumentCaptor.forClass(com.studenthub.modules.jwauth.security.JwSessionStore.JwSession.class);
        verify(jwSessionStore).save(eq(USER_ID), sessionCaptor.capture(), any());
        assertEquals("JSESSIONID=abc", sessionCaptor.getValue().cookieHeader());
        verify(sessionStore, never()).replace(eq(RAW_TOKEN), any());
    }

    @Test
    void authSwitchesIdentityToTeacherWhenDetected() {
        KingosoftJwClient.JwLoginResult teacherLogin = new KingosoftJwClient.JwLoginResult(
                "JSESSIONID=teacher", "https://jwgl.mycc.edu.cn/frame/homes.action?v=2", "T0001", "李老师", "TEACHER", null);
        when(jwClient.login(eq("T0001"), any(), eq("captcha-uid"), eq("11"))).thenReturn(teacherLogin);
        when(jwBindingMapper.findBySsoAccount("T0001")).thenReturn(null);

        JwAuthResult result = jwAuthService.auth(USER_ID, RAW_TOKEN,
                new JwAuthRequest("T0001", "secret-password", "captcha-uid", "11"));

        assertEquals("TEACHER", result.identityKind());
        assertEquals(true, result.identityChanged());
        // 平台会话刷新为新身份
        verify(sessionStore).replace(eq(RAW_TOKEN), any(AuthenticatedUser.class));
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertEquals("TEACHER", userCaptor.getValue().getIdentityType());
    }

    @Test
    void rejectsAccountBoundToAnotherUser() {
        JwBindingEntity other = new JwBindingEntity();
        other.setUserId(9999L);
        other.setSsoAccount("2023590397");
        when(jwBindingMapper.findBySsoAccount("2023590397")).thenReturn(other);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> jwAuthService.auth(USER_ID, RAW_TOKEN, request));
        assertEquals(ErrorCode.JW_ACCOUNT_ALREADY_BOUND.getCode(), exception.getCode());
        verify(jwClient, never()).login(any(), any(), any(), any());
    }

    @Test
    void remoteLoginFailureMapsToUnifiedMessage() {
        when(jwClient.login(any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.JW_AUTH_FAILED));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> jwAuthService.auth(USER_ID, RAW_TOKEN, request));
        assertEquals(ErrorCode.JW_AUTH_FAILED.getCode(), exception.getCode());
        verify(jwBindingMapper, never()).insert(any(JwBindingEntity.class));
    }

    @Test
    void statusReflectsUnboundAuthenticatedAndExpired() {
        assertEquals(JwStatusResponse.UNBOUND, jwAuthService.status(USER_ID).status());

        JwBindingEntity binding = new JwBindingEntity();
        binding.setUserId(USER_ID);
        binding.setSsoAccount("2023590397");
        binding.setJwName("王同学");
        binding.setIdentityKind("STUDENT");
        binding.setAuthStatus("AUTHENTICATED");
        binding.setLastAuthAt(LocalDateTime.now());
        binding.setSessionExpiresAt(LocalDateTime.now().plusHours(10));
        when(jwBindingMapper.findByUserId(USER_ID)).thenReturn(binding);
        when(jwSessionStore.find(USER_ID)).thenReturn(Optional.of(new com.studenthub.modules.jwauth.security.JwSessionStore.JwSession("JSESSIONID=abc", "https://jwgl.mycc.edu.cn/frame/homes.action?v=1")));
        assertEquals(JwStatusResponse.AUTHENTICATED, jwAuthService.status(USER_ID).status());

        when(jwSessionStore.find(USER_ID)).thenReturn(Optional.empty());
        assertEquals(JwStatusResponse.EXPIRED, jwAuthService.status(USER_ID).status());
    }

    @Test
    void unbindDeletesBindingAndSession() {
        JwBindingEntity binding = new JwBindingEntity();
        binding.setId(1L);
        binding.setUserId(USER_ID);
        when(jwBindingMapper.findByUserId(USER_ID)).thenReturn(binding);

        jwAuthService.unbind(USER_ID);

        verify(jwBindingMapper).deleteById(1L);
        verify(jwSessionStore).delete(USER_ID);
    }

    private void assertNotNull(Object value) {
        org.junit.jupiter.api.Assertions.assertNotNull(value);
    }
}
