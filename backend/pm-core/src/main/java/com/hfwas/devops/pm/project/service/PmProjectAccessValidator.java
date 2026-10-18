package com.hfwas.devops.pm.project.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.user.spi.ProjectAccessValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PmProjectAccessValidator implements ProjectAccessValidator {

    private final ProjectService projectService;
    private final CurrentUserAccessor currentUserAccessor;

    @Override
    public void assertAccess(Long projectId) {
        projectService.assertProjectAccess(projectId);
    }

    @Override
    public List<Long> accessibleProjectIds() {
        Long tenantId = currentUserAccessor.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ResultCode.TENANT_CONTEXT_MISSING);
        }
        return projectService.accessibleProjectIds(tenantId);
    }

    @Override
    public Long currentTenantId() {
        return currentUserAccessor.currentTenantId();
    }
}
