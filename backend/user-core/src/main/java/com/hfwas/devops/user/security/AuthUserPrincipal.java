package com.hfwas.devops.user.security;

import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.context.UserContext;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class AuthUserPrincipal implements UserDetails {

    private final SysUser user;
    /** Login tenant context from JWT, not user's home tenant. */
    private final Long loginTenantId;

    public AuthUserPrincipal(SysUser user) {
        this(user, null);
    }

    public AuthUserPrincipal(SysUser user, Long loginTenantId) {
        this.user = user;
        this.loginTenantId = loginTenantId;
    }

    public Long getUserId() {
        return user.getId();
    }

    public String getDisplayName() {
        return user.getDisplayName();
    }

    public UserContext toContext() {
        return UserContext.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .tenantId(loginTenantId)
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getEnabled() == null || user.getEnabled() == 1;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isAccountNonLocked();
    }
}
