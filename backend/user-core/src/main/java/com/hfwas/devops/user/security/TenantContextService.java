package com.hfwas.devops.user.security;

import com.hfwas.devops.user.context.TenantHttpHeaders;
import com.hfwas.devops.user.spi.TenantAccessValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantContextService {

    private final TenantAccessValidator tenantAccessValidator;

    /**
     * Resolves the effective tenant for the request: prefers {@link TenantHttpHeaders#TENANT_ID}
     * when present, otherwise falls back to the JWT tenant.
     */
    public Long resolveAndValidate(HttpServletRequest request, Long userId, String role, Long jwtTenantId) {
        Long tenantId = jwtTenantId;
        String header = request.getHeader(TenantHttpHeaders.TENANT_ID);
        if (StringUtils.isNotBlank(header)) {
            try {
                tenantId = Long.parseLong(header.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("无效的租户 ID");
            }
        }
        if (tenantId == null) {
            return null;
        }
        tenantAccessValidator.assertAccess(userId, role, tenantId);
        return tenantId;
    }
}
