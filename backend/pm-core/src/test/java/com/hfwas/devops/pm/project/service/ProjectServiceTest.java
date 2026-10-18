package com.hfwas.devops.pm.project.service;

import com.hfwas.devops.pm.meta.ProjectIssueTypeService;
import com.hfwas.devops.pm.project.mapper.PmProjectMapper;
import com.hfwas.devops.pm.project.model.ProjectAccessContextVO;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.user.spi.TenantAccessValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Access-check behaviour lives in {@link ProjectAccessGuardTest}; here we only
 * verify that ProjectService keeps its public API delegating to the guard.
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private PmProjectMapper projectMapper;
    @Mock
    private CurrentUserAccessor currentUserAccessor;
    @Mock
    private TenantAccessValidator tenantAccessValidator;
    @Mock
    private ProjectIssueTypeService projectIssueTypeService;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void assertProjectAccessDelegatesToGuard() {
        projectService.assertProjectAccess(1L);

        verify(projectAccessGuard).assertAccess(1L);
    }

    @Test
    void resolveAccessContextDelegatesToGuard() {
        ProjectAccessContextVO expected = new ProjectAccessContextVO();
        expected.setProjectId(1L);
        when(projectAccessGuard.resolve(1L)).thenReturn(expected);

        assertThat(projectService.resolveAccessContext(1L)).isSameAs(expected);
    }
}
