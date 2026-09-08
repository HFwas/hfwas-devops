package com.hfwas.devops.user.security;

import com.hfwas.devops.user.context.TenantHttpHeaders;
import com.hfwas.devops.user.model.TenantOptionVO;
import com.hfwas.devops.user.service.TenantMemberService;
import com.hfwas.devops.user.service.TenantService;
import com.hfwas.devops.user.spi.TenantAccessValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantContextService {

    private final TenantAccessValidator tenantAccessValidator;
    private final TenantMemberService tenantMemberService;

    /**
     * Resolves the effective tenant: {@link TenantHttpHeaders#TENANT_ID} first, then JWT,
     * then the user's default membership (Keycloak tokens have no tenant claim).
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
            tenantId = resolveDefaultTenant(userId, role);
        }
        if (tenantId == null) {
            return null;
        }
        tenantAccessValidator.assertAccess(userId, role, tenantId);
        return tenantId;
    }

    private Long resolveDefaultTenant(Long userId, String role) {
        List<TenantOptionVO> tenants = tenantMemberService.listEnabledTenantsByUser(userId);
        if (tenants != null && !tenants.isEmpty()) {
            for (TenantOptionVO tenant : tenants) {
                if (tenant.getId() != null && tenant.getId() == TenantService.DEFAULT_TENANT_ID) {
                    return tenant.getId();
                }
            }
            return tenants.getFirst().getId();
        }
        if ("admin".equalsIgnoreCase(role)) {
            return TenantService.DEFAULT_TENANT_ID;
        }
        return null;
    }
}
