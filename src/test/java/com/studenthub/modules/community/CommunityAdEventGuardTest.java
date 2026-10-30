package com.studenthub.modules.community;

import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.security.CommunityAdEventGuard;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.systemconfig.service.SystemConfigService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunityAdEventGuardTest {

    @Test
    void impressionRateLimitRejectsBurst() {
        CommunityAdEventGuard guard = new CommunityAdEventGuard(mock(SystemConfigService.class));
        AuthenticatedUser user = user();
        for (int i = 0; i < 120; i++) {
            guard.checkImpression(user);
        }
        BusinessException error = assertThrows(BusinessException.class, () -> guard.checkImpression(user));
        assertEquals("AD_429_01", error.getCode());
    }

    @Test
    void clickRateLimitUsesSeparateWindow() {
        CommunityAdEventGuard guard = new CommunityAdEventGuard(mock(SystemConfigService.class));
        AuthenticatedUser user = user();
        for (int i = 0; i < 30; i++) {
            guard.checkClick(user);
        }
        BusinessException error = assertThrows(BusinessException.class, () -> guard.checkClick(user));
        assertEquals("AD_429_01", error.getCode());
        // 点击窗口打满不影响曝光窗口
        guard.checkImpression(user);
    }

    @Test
    void configChangeInvalidatesCachedLimits() {
        SystemConfigService config = mock(SystemConfigService.class);
        CommunityAdEventGuard guard = new CommunityAdEventGuard(config);
        guard.checkImpression(user());
        when(config.getValue(CommunityAdEventGuard.KEY_EVENT_RATE, null)).thenReturn("1");
        guard.onConfigChanged(null);
        AuthenticatedUser secondUser = new AuthenticatedUser(43L, 1L, null, "tester2", "测试用户2", "STUDENT", "BASIC",
                List.of(), List.of(), null, null);
        guard.checkImpression(secondUser);
        BusinessException error = assertThrows(BusinessException.class, () -> guard.checkImpression(secondUser));
        assertEquals("AD_429_01", error.getCode());
    }


    @Test
    void zeroMeansUnlimited() {
        SystemConfigService config = mock(SystemConfigService.class);
        when(config.getValue(CommunityAdEventGuard.KEY_EVENT_RATE, null)).thenReturn("0");
        when(config.getValue(CommunityAdEventGuard.KEY_CLICK_RATE, null)).thenReturn("0");
        CommunityAdEventGuard guard = new CommunityAdEventGuard(config);
        for (int i = 0; i < 500; i++) {
            guard.checkImpression(user());
            guard.checkClick(user());
        }
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(42L, 1L, null, "tester", "测试用户", "STUDENT", "BASIC",
                List.of(), List.of(), null, null);
    }
}
