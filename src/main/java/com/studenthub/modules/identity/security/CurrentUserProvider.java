package com.studenthub.modules.identity.security;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentUserProvider {

    public AuthenticatedUser requireCurrentUser() {
        return currentUser().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public Long currentUserIdOrSystem() {
        return currentUser().map(AuthenticatedUser::userId).orElse(0L);
    }
}
