package com.hfwas.devops.config;

import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.security.AuthUserPrincipal;
import com.hfwas.devops.user.security.KeycloakUserProvisioningService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KeycloakJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final KeycloakUserProvisioningService provisioningService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String sub = jwt.getSubject();
        SysUser user = provisioningService.ensureUser(
                sub,
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                firstNonBlank(jwt.getClaimAsString("name"), jwt.getClaimAsString("given_name")));
        if (user.getEnabled() != null && user.getEnabled() != 1) {
            throw new DisabledException("用户已停用");
        }
        AuthUserPrincipal principal = new AuthUserPrincipal(user, null);
        return new UsernamePasswordAuthenticationToken(principal, jwt, principal.getAuthorities());
    }

    private static String firstNonBlank(String first, String second) {
        if (StringUtils.isNotBlank(first)) {
            return first;
        }
        return StringUtils.trimToNull(second);
    }
}
