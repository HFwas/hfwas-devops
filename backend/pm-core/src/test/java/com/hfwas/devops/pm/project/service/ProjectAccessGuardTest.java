package com.hfwas.devops.pm.project.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.project.entity.PmProject;
import com.hfwas.devops.pm.project.mapper.PmProjectMapper;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.spi.TenantAccessValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAccessGuardTest {

    @Mock
    private PmProjectMapper projectMapper;
    @Mock
    private CurrentUserAccessor currentUserAccessor;
    @Mock
    private TenantAccessValidator tenantAccessValidator;

    @InjectMocks
    private ProjectAccessGuard projectAccessGuard;

    @Test
    void missingProjectIsForbidden() {
        when(projectMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> projectAccessGuard.assertAccess(99L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.PROJECT_ACCESS_DENIED.getCode());
    }

    @Test
    void unauthenticatedIsUnauthorized() {
        PmProject project = project(1L, 10L);
        when(projectMapper.selectById(1L)).thenReturn(project);
        when(currentUserAccessor.current()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectAccessGuard.assertAccess(1L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    void nonMemberIsForbidden() {
        PmProject project = project(1L, 10L);
        UserContext user = UserContext.builder().userId(7L).role("user").tenantId(10L).build();
        when(projectMapper.selectById(1L)).thenReturn(project);
        when(currentUserAccessor.current()).thenReturn(Optional.of(user));
        doThrow(new BizException(ResultCode.TENANT_FORBIDDEN))
                .when(tenantAccessValidator).assertAccess(7L, "user", 10L);

        assertThatThrownBy(() -> projectAccessGuard.assertAccess(1L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.TENANT_FORBIDDEN.getCode());
    }

    @Test
    void memberIsAllowed() {
        PmProject project = project(1L, 10L);
        UserContext user = UserContext.builder().userId(7L).role("user").tenantId(10L).build();
        when(projectMapper.selectById(1L)).thenReturn(project);
        when(currentUserAccessor.current()).thenReturn(Optional.of(user));

        projectAccessGuard.assertAccess(1L);
    }

    private PmProject project(Long id, Long tenantId) {
        PmProject project = new PmProject();
        project.setId(id);
        project.setTenantId(tenantId);
        project.setName("项目");
        return project;
    }
}
