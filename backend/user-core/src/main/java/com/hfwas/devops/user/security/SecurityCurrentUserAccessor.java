package com.hfwas.devops.user.security;

import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.context.UserContextHolder;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Primary
public class SecurityCurrentUserAccessor implements CurrentUserAccessor {

    @Override
    public Optional<UserContext> current() {
        return UserContextHolder.current();
    }
}
