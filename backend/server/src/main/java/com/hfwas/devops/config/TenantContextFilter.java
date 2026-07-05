package com.hfwas.devops.config;

import com.hfwas.devops.common.core.exception.ApiErrorWriter;
import com.hfwas.devops.common.core.exception.LegacyErrorCodeResolver;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.user.security.AuthUserPrincipal;
import com.hfwas.devops.user.security.TenantContextService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantContextService tenantContextService;
    private final ApiErrorWriter apiErrorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUserPrincipal principal) {
            try {
                Long tenantId = tenantContextService.resolveAndValidate(
                        request,
                        principal.getUserId(),
                        principal.getUser().getRole(),
                        principal.getLoginTenantId());
                if (tenantId != null && !tenantId.equals(principal.getLoginTenantId())) {
                    AuthUserPrincipal updated = new AuthUserPrincipal(principal.getUser(), tenantId);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(updated, auth.getCredentials(), updated.getAuthorities());
                    authentication.setDetails(auth.getDetails());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (IllegalArgumentException ex) {
                BizException biz = LegacyErrorCodeResolver.resolve(ex);
                apiErrorWriter.write(response, HttpStatus.FORBIDDEN, biz);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
