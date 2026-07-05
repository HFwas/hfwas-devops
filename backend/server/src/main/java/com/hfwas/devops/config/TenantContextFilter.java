package com.hfwas.devops.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.user.security.AuthUserPrincipal;
import com.hfwas.devops.user.security.TenantContextService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantContextService tenantContextService;
    private final ObjectMapper objectMapper;

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
                writeError(response, 403, ex.getMessage());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), BaseResult.failed(message));
    }
}
