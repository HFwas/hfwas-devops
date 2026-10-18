package com.hfwas.devops.pm.project.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.project.entity.PmProject;
import com.hfwas.devops.pm.project.mapper.PmProjectMapper;
import com.hfwas.devops.pm.project.model.ProjectAccessContextVO;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.spi.TenantAccessValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Project-scoped access guard.
 *
 * <p>Deliberately depends only on mappers and user context so that module services
 * can guard their entry points without depending back on {@link ProjectService},
 * which would create a bean construction cycle.</p>
 */
@Component
@RequiredArgsConstructor
public class ProjectAccessGuard {

    private final PmProjectMapper projectMapper;
    private final CurrentUserAccessor currentUserAccessor;
    private final TenantAccessValidator tenantAccessValidator;

    /**
     * Resolves project tenant for deep links: does not require current tenant header to match.
     */
    public ProjectAccessContextVO resolve(Long projectId) {
        PmProject project = projectMapper.selectById(projectId);
        if (project == null || project.getTenantId() == null) {
            throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
        }
        UserContext ctx = currentUserAccessor.current()
                .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED));
        tenantAccessValidator.assertAccess(ctx.getUserId(), ctx.getRole(), project.getTenantId());
        ProjectAccessContextVO vo = new ProjectAccessContextVO();
        vo.setProjectId(project.getId());
        vo.setProjectName(project.getName());
        vo.setTenantId(project.getTenantId());
        return vo;
    }

    /**
     * Guard for every project-scoped entry point. Uses the project's tenant and
     * the user's membership, so deep links from another selected tenant work.
     */
    public void assertAccess(Long projectId) {
        resolve(projectId);
    }
}
