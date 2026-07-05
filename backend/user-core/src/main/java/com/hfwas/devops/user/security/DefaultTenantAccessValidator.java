package com.hfwas.devops.user.security;

import com.hfwas.devops.user.service.TenantMemberService;
import com.hfwas.devops.user.service.TenantService;
import com.hfwas.devops.user.spi.TenantAccessValidator;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultTenantAccessValidator implements TenantAccessValidator {

    private final TenantService tenantService;
    private final TenantMemberService tenantMemberService;

    @Override
    public void assertAccess(Long userId, String role, Long tenantId) {
        tenantService.requireEnabled(tenantId);
        if (!"admin".equalsIgnoreCase(role) && !tenantMemberService.isActiveMember(tenantId, userId)) {
            throw new IllegalArgumentException("无权访问该租户");
        }
    }
}
