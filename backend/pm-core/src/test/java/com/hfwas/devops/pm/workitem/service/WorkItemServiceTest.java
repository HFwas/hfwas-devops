package com.hfwas.devops.pm.workitem.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.project.service.ProjectService;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkItemServiceTest {

    @Mock
    private PmWorkItemMapper workItemMapper;
    @Mock
    private ProjectService projectService;

    @InjectMocks
    private WorkItemService workItemService;

    @Test
    void updateCannotMoveAcrossProjects() {
        PmWorkItem old = new PmWorkItem();
        old.setId(1L);
        old.setProjectId(10L);

        PmWorkItem update = new PmWorkItem();
        update.setId(1L);
        update.setProjectId(20L);

        when(workItemMapper.selectById(1L)).thenReturn(old);

        assertThatThrownBy(() -> workItemService.save(update))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.PROJECT_ACCESS_DENIED.getCode());
        verify(projectService).assertProjectAccess(10L);
    }
}
