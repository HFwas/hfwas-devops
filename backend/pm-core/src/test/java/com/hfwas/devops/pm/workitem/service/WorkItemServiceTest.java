package com.hfwas.devops.pm.workitem.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.project.service.ProjectService;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.pm.workitem.model.TransitionVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkItemServiceTest {

    @Mock
    private PmWorkItemMapper workItemMapper;
    @Mock
    private ProjectService projectService;
    @Mock
    private FieldDefinitionService fieldDefinitionService;
    @Mock
    private StatusDefinitionService statusDefinitionService;
    @Mock
    private TransitionConditionEvaluator transitionConditionEvaluator;
    @Mock
    private TransitionValidatorExecutor transitionValidatorExecutor;
    @Mock
    private TransitionPostFunctionExecutor transitionPostFunctionExecutor;
    @Mock
    private WorkItemActivityService activityService;
    @Mock
    private WorkItemKeyEnricher keyEnricher;

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

    @Test
    void transitionRejectsStaleVersionAtomically() {
        PmWorkItem item = new PmWorkItem();
        item.setId(1L);
        item.setProjectId(10L);
        item.setTypeCode("task");
        item.setStatus("open");
        item.setVersion(5L);
        when(workItemMapper.selectById(1L)).thenReturn(item);
        TransitionVO transition = new TransitionVO();
        transition.setId("task-open-done");
        transition.setToStatus("done");
        when(statusDefinitionService.findTransition(10L, "task", "open", "task-open-done"))
                .thenReturn(transition);
        when(fieldDefinitionService.listByProjectAndType(10L, "task")).thenReturn(List.of());
        when(workItemMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> workItemService.transitionWithVersion(1L, "task-open-done", null, 5L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.WORK_ITEM_VERSION_CONFLICT.getCode());
        verify(workItemMapper, never()).updateById(any(PmWorkItem.class));
    }
}
