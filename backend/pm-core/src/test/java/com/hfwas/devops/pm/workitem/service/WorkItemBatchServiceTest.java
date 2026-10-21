package com.hfwas.devops.pm.workitem.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.project.service.ProjectService;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.pm.workitem.model.AllowedTransitionsVO;
import com.hfwas.devops.pm.workitem.model.BatchActionConfigVO;
import com.hfwas.devops.pm.workitem.model.BatchOperationItemVO;
import com.hfwas.devops.pm.workitem.model.BatchOperationResultVO;
import com.hfwas.devops.pm.workitem.model.TransitionOptionVO;
import com.hfwas.devops.pm.workitem.model.WorkItemPatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkItemBatchServiceTest {

    @Mock
    private PmWorkItemMapper workItemMapper;
    @Mock
    private ProjectService projectService;
    @Mock
    private WorkItemFieldUpdateService fieldUpdateService;
    @Mock
    private WorkItemService workItemService;
    @Mock
    private StatusDefinitionService statusDefinitionService;
    @Mock
    private BatchActionConfigService batchActionConfigService;

    private WorkItemBatchService service;

    @BeforeEach
    void setUp() {
        service = new WorkItemBatchService(workItemMapper, projectService, fieldUpdateService,
                workItemService, statusDefinitionService, batchActionConfigService);
    }

    @Test
    void partialFailureDoesNotRollbackSuccesses() {
        List<PmWorkItem> items = List.of(item(1L, 10L, "open"), item(2L, 10L, "open"), item(3L, 10L, "open"));
        when(workItemMapper.selectBatchIds(any())).thenReturn(items);
        when(batchActionConfigService.assertActionAllowed(eq(10L), anyString(), eq(3)))
                .thenReturn(updateConfig());
        when(batchActionConfigService.isFieldAllowed(any(), eq("priority"))).thenReturn(true);
        when(fieldUpdateService.applyPatch(eq(1L), anyMap(), isNull(), anyString()))
                .thenReturn(new WorkItemPatchResult(List.of(), 2L));
        when(fieldUpdateService.applyPatch(eq(2L), anyMap(), isNull(), anyString()))
                .thenThrow(new BizException(ResultCode.WORK_ITEM_VERSION_CONFLICT));
        when(fieldUpdateService.applyPatch(eq(3L), anyMap(), isNull(), anyString()))
                .thenReturn(new WorkItemPatchResult(List.of(), 2L));

        BatchOperationResultVO result = service.batchUpdate(
                List.of(1L, 2L, 3L), Map.of("priority", "high"), null, false);

        assertThat(result.getSuccess()).isEqualTo(2);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getItems())
                .extracting(BatchOperationItemVO::getOutcome)
                .containsExactly(BatchOperationItemVO.SUCCESS, BatchOperationItemVO.FAILED,
                        BatchOperationItemVO.SUCCESS);
    }

    @Test
    void dryRunPerformsNoWrites() {
        when(workItemMapper.selectBatchIds(any())).thenReturn(List.of(item(1L, 10L, "open")));
        when(batchActionConfigService.assertActionAllowed(eq(10L), anyString(), eq(1)))
                .thenReturn(updateConfig());
        when(batchActionConfigService.isFieldAllowed(any(), eq("priority"))).thenReturn(true);
        when(fieldUpdateService.previewPatch(eq(1L), anyMap(), isNull()))
                .thenReturn(new WorkItemPatchResult(List.of(), 2L));

        BatchOperationResultVO result = service.batchUpdate(
                List.of(1L), Map.of("priority", "high"), null, true);

        assertThat(result.isDryRun()).isTrue();
        assertThat(result.getSuccess()).isEqualTo(1);
        verify(fieldUpdateService, never()).applyPatch(any(), anyMap(), any(), anyString());
    }

    @Test
    void rejectsCrossProjectBatch() {
        when(workItemMapper.selectBatchIds(any())).thenReturn(
                List.of(item(1L, 10L, "open"), item(2L, 20L, "open")));

        assertThatThrownBy(() -> service.batchUpdate(
                List.of(1L, 2L), Map.of("priority", "high"), null, false))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.BATCH_PROJECT_MISMATCH.getCode());
    }

    @Test
    void rejectsDisabledAction() {
        when(workItemMapper.selectBatchIds(any())).thenReturn(List.of(item(1L, 10L, "open")));
        when(batchActionConfigService.assertActionAllowed(eq(10L), anyString(), eq(1)))
                .thenThrow(new BizException(ResultCode.BATCH_ACTION_DISABLED));

        assertThatThrownBy(() -> service.batchUpdate(
                List.of(1L), Map.of("priority", "high"), null, false))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.BATCH_ACTION_DISABLED.getCode());
    }

    @Test
    void rejectsOverLimit() {
        when(workItemMapper.selectBatchIds(any())).thenReturn(List.of(item(1L, 10L, "open")));
        when(batchActionConfigService.assertActionAllowed(eq(10L), anyString(), eq(1)))
                .thenThrow(new BizException(ResultCode.BATCH_SIZE_EXCEEDED));

        assertThatThrownBy(() -> service.batchUpdate(
                List.of(1L), Map.of("priority", "high"), null, false))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.BATCH_SIZE_EXCEEDED.getCode());
    }

    @Test
    void skipsFieldOutsideWhitelist() {
        when(workItemMapper.selectBatchIds(any())).thenReturn(List.of(item(1L, 10L, "open")));
        when(batchActionConfigService.assertActionAllowed(eq(10L), anyString(), eq(1)))
                .thenReturn(updateConfig());
        when(batchActionConfigService.isFieldAllowed(any(), eq("risk"))).thenReturn(false);

        BatchOperationResultVO result = service.batchUpdate(
                List.of(1L), Map.of("risk", "high"), null, false);

        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getItems().get(0).getCode())
                .isEqualTo(ResultCode.BATCH_FIELD_NOT_ALLOWED.getCode());
        verify(fieldUpdateService, never()).applyPatch(any(), anyMap(), any(), anyString());
    }

    @Test
    void skipsIllegalTransition() {
        PmWorkItem item = item(1L, 10L, "open");
        when(workItemMapper.selectBatchIds(any())).thenReturn(List.of(item));
        when(batchActionConfigService.assertActionAllowed(eq(10L), anyString(), eq(1)))
                .thenReturn(updateConfig());
        when(statusDefinitionService.allowedTransitions(10L, "task", "open", 1L))
                .thenReturn(new AllowedTransitionsVO());

        BatchOperationResultVO result = service.batchTransition(
                List.of(1L), null, "done", null, null, false);

        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getItems().get(0).getCode())
                .isEqualTo(ResultCode.STATUS_TRANSITION_FORBIDDEN.getCode());
    }

    @Test
    void validatorFailureIsFailedOutcome() {
        PmWorkItem item = item(1L, 10L, "open");
        when(workItemMapper.selectBatchIds(any())).thenReturn(List.of(item));
        when(batchActionConfigService.assertActionAllowed(eq(10L), anyString(), eq(1)))
                .thenReturn(updateConfig());
        AllowedTransitionsVO allowed = new AllowedTransitionsVO();
        TransitionOptionVO option = new TransitionOptionVO();
        option.setId("task-open-done");
        option.setToStatus("done");
        allowed.setTransitions(List.of(option));
        when(statusDefinitionService.allowedTransitions(10L, "task", "open", 1L)).thenReturn(allowed);
        when(workItemService.transitionWithVersion(1L, "task-open-done", null, null))
                .thenThrow(new IllegalArgumentException("流转前必须填写「负责人」"));

        BatchOperationResultVO result = service.batchTransition(
                List.of(1L), null, "done", null, null, false);

        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getItems().get(0).getOutcome()).isEqualTo(BatchOperationItemVO.FAILED);
    }

    private BatchActionConfigVO updateConfig() {
        BatchActionConfigVO config = new BatchActionConfigVO();
        config.setActionCode(BatchActionConfigService.ACTION_UPDATE_FIELDS);
        config.setEnabled(true);
        config.setMaxItems(100);
        config.setFieldWhitelist(List.of("priority"));
        return config;
    }

    private PmWorkItem item(Long id, Long projectId, String status) {
        PmWorkItem item = new PmWorkItem();
        item.setId(id);
        item.setProjectId(projectId);
        item.setTypeCode("task");
        item.setStatus(status);
        item.setVersion(1L);
        return item;
    }
}
