package com.hfwas.devops.pm.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.pm.common.PmPageRequest;
import com.hfwas.devops.pm.meta.ProjectIssueTypeService;
import com.hfwas.devops.pm.project.entity.PmProject;
import com.hfwas.devops.pm.project.mapper.PmProjectMapper;
import com.hfwas.devops.pm.project.model.ProjectAccessContextVO;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.spi.TenantAccessValidator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final PmProjectMapper projectMapper;
    private final CurrentUserAccessor currentUserAccessor;
    private final TenantAccessValidator tenantAccessValidator;
    private final ProjectIssueTypeService projectIssueTypeService;
    private final ProjectAccessGuard projectAccessGuard;

    /**
     * Resolves project tenant for deep links: does not require current tenant header to match.
     */
    public ProjectAccessContextVO resolveAccessContext(Long projectId) {
        return projectAccessGuard.resolve(projectId);
    }

    /**
     * Guard for every project-scoped entry point. Uses the project's tenant and
     * the user's membership, so deep links from another selected tenant work.
     */
    public void assertProjectAccess(Long projectId) {
        projectAccessGuard.assertAccess(projectId);
    }

    /** Project ids visible in the given tenant, for cross-module list filtering. */
    public List<Long> accessibleProjectIds(Long tenantId) {
        if (tenantId == null) {
            throw new BizException(ResultCode.TENANT_CONTEXT_MISSING);
        }
        return projectMapper.selectList(Wrappers.<PmProject>lambdaQuery()
                        .eq(PmProject::getTenantId, tenantId)
                        .select(PmProject::getId))
                .stream()
                .map(PmProject::getId)
                .toList();
    }

    public IPage<PmProject> page(PmPageRequest request, String keyword) {
        Long tenantId = requireTenantId();
        Page<PmProject> page = new Page<>(request.resolvePageNo(), request.resolvePageSize());
        return projectMapper.selectPage(page, Wrappers.<PmProject>lambdaQuery()
                .eq(PmProject::getTenantId, tenantId)
                .and(StringUtils.isNotBlank(keyword), w -> w
                        .like(PmProject::getName, keyword)
                        .or()
                        .like(PmProject::getCode, keyword))
                .orderByDesc(PmProject::getCreateTime));
    }

    public Long save(PmProject project) {
        Long tenantId = requireTenantId();
        if (StringUtils.isBlank(project.getCode()) || StringUtils.isBlank(project.getName())) {
            throw new IllegalArgumentException("项目编码和名称不能为空");
        }
        String code = project.getCode().trim();
        Long dup = projectMapper.selectCount(Wrappers.<PmProject>lambdaQuery()
                .eq(PmProject::getTenantId, tenantId)
                .eq(PmProject::getCode, code)
                .ne(project.getId() != null, PmProject::getId, project.getId()));
        if (dup != null && dup > 0) {
            throw new IllegalArgumentException("当前租户下项目编码已存在");
        }
        project.setCode(code);
        project.setName(project.getName().trim());
        if (project.getId() == null) {
            project.setTenantId(tenantId);
            projectMapper.insert(project);
            projectIssueTypeService.seedDefaultScheme(project.getId());
        } else {
            assertProjectTenant(project.getId(), tenantId);
            project.setTenantId(tenantId);
            projectMapper.updateById(project);
        }
        return project.getId();
    }

    public PmProject getById(Long id) {
        PmProject project = projectMapper.selectById(id);
        if (project == null || project.getTenantId() == null) {
            throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
        }
        UserContext ctx = currentUserAccessor.current()
                .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED));
        tenantAccessValidator.assertAccess(ctx.getUserId(), ctx.getRole(), project.getTenantId());
        return project;
    }

    public void delete(Long id) {
        assertProjectTenant(id, requireTenantId());
        projectMapper.deleteById(id);
    }

    private void assertProjectTenant(Long projectId, Long tenantId) {
        PmProject project = projectMapper.selectById(projectId);
        if (project == null || project.getTenantId() == null || !tenantId.equals(project.getTenantId())) {
            throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
        }
    }

    private Long requireTenantId() {
        Long tenantId = currentUserAccessor.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ResultCode.TENANT_CONTEXT_MISSING);
        }
        return tenantId;
    }
}
