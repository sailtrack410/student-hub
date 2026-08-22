package com.studenthub.modules.identity;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.wechat.WechatIdentity;
import com.studenthub.integration.wechat.WechatIdentityProvider;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.entity.UserRoleEntity;
import com.studenthub.modules.identity.mapper.IdentityAccessMapper;
import com.studenthub.modules.identity.mapper.RoleMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.mapper.UserRoleMapper;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.identity.security.SecurityProperties;
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
    private CurrentUserProvider currentUserProvider;
    @Mock
    private WechatIdentityProvider wechatIdentityProvider;

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
}
