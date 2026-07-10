package com.hfwas.devops.pm.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.pm.common.PmPageRequest;
import com.hfwas.devops.pm.meta.ProjectIssueTypeService;
import com.hfwas.devops.pm.project.entity.PmProject;
import com.hfwas.devops.pm.project.mapper.PmProjectMapper;
import com.hfwas.devops.pm.project.model.ProjectAccessContextVO;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.spi.TenantAccessValidator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final PmProjectMapper projectMapper;
    private final CurrentUserAccessor currentUserAccessor;
    private final TenantAccessValidator tenantAccessValidator;
    private final ProjectIssueTypeService projectIssueTypeService;

    /**
     * Resolves project tenant for deep links: does not require current tenant header to match.
     */
    public ProjectAccessContextVO resolveAccessContext(Long projectId) {
        PmProject project = projectMapper.selectById(projectId);
        if (project == null || project.getTenantId() == null) {
            throw new IllegalArgumentException("项目不存在");
        }
        UserContext ctx = currentUserAccessor.current()
                .orElseThrow(() -> new IllegalArgumentException("未登录或登录已过期"));
        tenantAccessValidator.assertAccess(ctx.getUserId(), ctx.getRole(), project.getTenantId());
        ProjectAccessContextVO vo = new ProjectAccessContextVO();
        vo.setProjectId(project.getId());
        vo.setProjectName(project.getName());
        vo.setTenantId(project.getTenantId());
        return vo;
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
        if (project == null) {
            return null;
        }
        Long tenantId = requireTenantId();
        if (project.getTenantId() != null && !tenantId.equals(project.getTenantId())) {
            throw new IllegalArgumentException("项目不存在或无权访问");
        }
        return project;
    }

    public void delete(Long id) {
        assertProjectTenant(id, requireTenantId());
        projectMapper.deleteById(id);
    }

    private void assertProjectTenant(Long projectId, Long tenantId) {
        PmProject project = projectMapper.selectById(projectId);
        if (project == null || project.getTenantId() == null || !tenantId.equals(project.getTenantId())) {
            throw new IllegalArgumentException("项目不存在或无权访问");
        }
    }

    private Long requireTenantId() {
        Long tenantId = currentUserAccessor.currentTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("未登录或租户上下文缺失");
        }
        return tenantId;
    }
}
