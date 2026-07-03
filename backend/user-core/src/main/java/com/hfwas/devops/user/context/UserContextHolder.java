package com.hfwas.devops.user.context;

import com.hfwas.devops.user.security.AuthUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Bridges Spring Security to {@link UserContext} for the user module and SPI consumers. */
public final class UserContextHolder {

    private UserContextHolder() {
    }

    public static Optional<UserContext> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUserPrincipal principal) {
            return Optional.of(principal.toContext());
        }
        return Optional.empty();
    }

    public static UserContext require() {
        return current().orElseThrow(() -> new IllegalArgumentException("未登录或登录已过期"));
    }
}
