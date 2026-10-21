package com.hfwas.devops.pm.workitem.service;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.field.engine.FieldTypeRegistry;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.field.spi.handler.DateFieldTypeHandler;
import com.hfwas.devops.pm.field.spi.handler.DateTimeFieldTypeHandler;
import com.hfwas.devops.pm.field.spi.handler.NumberFieldTypeHandler;
import com.hfwas.devops.pm.field.spi.handler.TextFieldTypeHandler;
import com.hfwas.devops.pm.project.service.ProjectService;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.pm.workitem.model.AllowedTransitionsVO;
import com.hfwas.devops.pm.workitem.model.TransitionOptionVO;
import com.hfwas.devops.pm.workitem.model.TransitionVO;
import com.hfwas.devops.pm.workitem.model.WorkItemFieldUpdateVO;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkItemFieldUpdateServiceTest {

    @Mock
    private PmWorkItemMapper workItemMapper;
    @Mock
    private ProjectService projectService;
    @Mock
    private FieldDefinitionService fieldDefinitionService;
    @Mock
    private WorkItemActivityService activityService;
    @Mock
    private WorkItemFieldDisplayResolver displayResolver;
    @Mock
    private WorkItemService workItemService;
    @Mock
    private StatusDefinitionService statusDefinitionService;
    @Mock
    private CurrentUserAccessor currentUserAccessor;

    private WorkItemFieldUpdateService service;

    @BeforeEach
    void setUp() {
        FieldTypeRegistry registry = new FieldTypeRegistry(List.of(
                new TextFieldTypeHandler(),
                new NumberFieldTypeHandler(),
                new DateFieldTypeHandler(),
                new DateTimeFieldTypeHandler()));
        WorkItemFieldApplicator applicator = new WorkItemFieldApplicator(registry);
        service = new WorkItemFieldUpdateService(workItemMapper, projectService, fieldDefinitionService,
                applicator, activityService, displayResolver, workItemService, statusDefinitionService,
                currentUserAccessor);
    }

    @Test
    void updatesSystemFieldAndBumpsVersion() {
        PmWorkItem item = item(3L, "medium", "in_progress");
        when(workItemMapper.selectById(1L)).thenReturn(item);
        when(fieldDefinitionService.listByProjectAndType(10L, "task")).thenReturn(definitions());
        when(workItemMapper.update(isNull(), any())).thenReturn(1);
        when(workItemService.getById(1L)).thenReturn(item);
        when(currentUserAccessor.currentUserId()).thenReturn(9L);
        when(displayResolver.toLabel(any(), any(), any())).thenReturn("label");

        WorkItemFieldUpdateVO vo = service.updateField(1L, "priority", "high", 3L, null);

        assertThat(item.getPriority()).isEqualTo("high");
        assertThat(vo.getNewValue()).isEqualTo("high");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PmWorkItem>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(workItemMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("version").contains("priority");
        assertThat(((AbstractWrapper<?, ?, ?>) captor.getValue()).getParamNameValuePairs().values())
                .contains(4L, "high");
        verify(activityService).recordChanges(any(), any(), any(), any());
    }

    @Test
    void rejectsStaleVersion() {
        when(workItemMapper.selectById(1L)).thenReturn(item(3L, "medium", "open"));

        assertThatThrownBy(() -> service.updateField(1L, "priority", "high", 2L, null))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.WORK_ITEM_VERSION_CONFLICT.getCode());
        verify(workItemMapper, never()).update(any(), any());
    }

    @Test
    void clearsFieldWithNull() {
        PmWorkItem item = item(3L, "medium", "open");
        item.setAssigneeId(5L);
        when(workItemMapper.selectById(1L)).thenReturn(item);
        when(fieldDefinitionService.listByProjectAndType(10L, "task")).thenReturn(definitions());
        when(workItemMapper.update(isNull(), any())).thenReturn(1);
        when(workItemService.getById(1L)).thenReturn(item);
        when(currentUserAccessor.currentUserId()).thenReturn(9L);
        when(displayResolver.toLabel(any(), any(), any())).thenReturn("label");

        service.updateField(1L, "assignee_id", null, 3L, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PmWorkItem>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(workItemMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("assignee_id");
    }

    @Test
    void mergesCustomFieldWithoutLosingOthers() {
        PmWorkItem item = item(3L, "medium", "open");
        Map<String, Object> custom = new HashMap<>();
        custom.put("score", 5);
        custom.put("other", "keep");
        item.setCustomFields(custom);
        when(workItemMapper.selectById(1L)).thenReturn(item);
        when(fieldDefinitionService.listByProjectAndType(10L, "task")).thenReturn(definitions());
        when(workItemMapper.update(isNull(), any())).thenReturn(1);
        when(workItemService.getById(1L)).thenReturn(item);
        when(currentUserAccessor.currentUserId()).thenReturn(9L);
        when(displayResolver.toLabel(any(), any(), any())).thenReturn("label");

        service.updateField(1L, "score", 8, 3L, null);

        assertThat(item.getCustomFields()).containsEntry("score", 8).containsEntry("other", "keep");
    }

    @Test
    void rejectsUnknownCustomField() {
        when(workItemMapper.selectById(1L)).thenReturn(item(3L, "medium", "open"));
        when(fieldDefinitionService.listByProjectAndType(10L, "task")).thenReturn(definitions());

        assertThatThrownBy(() -> service.updateField(1L, "unknown", 1, 3L, null))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.FIELD_UNKNOWN.getCode());
    }

    @Test
    void statusUpdateUsesWorkflowTransition() {
        PmWorkItem item = item(3L, "medium", "in_progress");
        when(workItemMapper.selectById(1L)).thenReturn(item);
        AllowedTransitionsVO allowed = new AllowedTransitionsVO();
        TransitionOptionVO option = new TransitionOptionVO();
        option.setId("task-in_progress-done");
        option.setToStatus("done");
        allowed.setTransitions(List.of(option));
        when(statusDefinitionService.allowedTransitions(10L, "task", "in_progress", 1L)).thenReturn(allowed);
        TransitionVO transition = new TransitionVO();
        transition.setId("task-in_progress-done");
        transition.setToStatus("done");
        when(workItemService.transitionWithVersion(1L, "task-in_progress-done", null, 3L))
                .thenReturn(transition);
        PmWorkItem updated = item(3L, "medium", "done");
        when(workItemService.getById(1L)).thenReturn(updated);
        when(statusDefinitionService.statusLabelMap(10L, "task")).thenReturn(Map.of(
                "in_progress", "进行中", "done", "已完成"));

        WorkItemFieldUpdateVO vo = service.updateField(1L, "status", "done", 3L, null);

        assertThat(vo.getNewValue()).isEqualTo("done");
        assertThat(vo.getNewLabel()).isEqualTo("已完成");
        verify(workItemService).transitionWithVersion(1L, "task-in_progress-done", null, 3L);
    }

    @Test
    void statusUpdateComparesLongVersionByValue() {
        PmWorkItem item = item(200L, "medium", "in_progress");
        when(workItemMapper.selectById(1L)).thenReturn(item);
        AllowedTransitionsVO allowed = new AllowedTransitionsVO();
        TransitionOptionVO option = new TransitionOptionVO();
        option.setId("task-in_progress-done");
        option.setToStatus("done");
        allowed.setTransitions(List.of(option));
        when(statusDefinitionService.allowedTransitions(10L, "task", "in_progress", 1L)).thenReturn(allowed);
        TransitionVO transition = new TransitionVO();
        transition.setId("task-in_progress-done");
        transition.setToStatus("done");
        when(workItemService.transitionWithVersion(1L, "task-in_progress-done", null, 200L))
                .thenReturn(transition);
        PmWorkItem updated = item(201L, "medium", "done");
        when(workItemService.getById(1L)).thenReturn(updated);
        when(statusDefinitionService.statusLabelMap(10L, "task")).thenReturn(Map.of(
                "in_progress", "进行中", "done", "已完成"));

        WorkItemFieldUpdateVO vo = service.updateField(1L, "status", "done", 200L, null);

        assertThat(vo.getNewValue()).isEqualTo("done");
        verify(workItemService).transitionWithVersion(1L, "task-in_progress-done", null, 200L);
    }

    @Test
    void ambiguousStatusRequiresTransitionId() {
        PmWorkItem item = item(3L, "medium", "open");
        when(workItemMapper.selectById(1L)).thenReturn(item);
        AllowedTransitionsVO allowed = new AllowedTransitionsVO();
        TransitionOptionVO first = new TransitionOptionVO();
        first.setId("a");
        first.setToStatus("closed");
        TransitionOptionVO second = new TransitionOptionVO();
        second.setId("b");
        second.setToStatus("closed");
        allowed.setTransitions(List.of(first, second));
        when(statusDefinitionService.allowedTransitions(10L, "task", "open", 1L)).thenReturn(allowed);

        assertThatThrownBy(() -> service.updateField(1L, "status", "closed", 3L, null))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.STATUS_TRANSITION_FORBIDDEN.getCode());
        verify(workItemService, never()).transitionWithVersion(any(), any(), any(), any());
    }

    @Test
    void rejectsBlankTitle() {
        PmWorkItem item = item(3L, "medium", "open");
        when(workItemMapper.selectById(1L)).thenReturn(item);
        when(fieldDefinitionService.listByProjectAndType(10L, "task")).thenReturn(definitions());

        assertThatThrownBy(() -> service.updateField(1L, "title", "  ", 3L, null))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.TITLE_REQUIRED.getCode());
    }

    private PmWorkItem item(Long version, String priority, String status) {
        PmWorkItem item = new PmWorkItem();
        item.setId(1L);
        item.setProjectId(10L);
        item.setTypeCode("task");
        item.setTitle("登录超时");
        item.setPriority(priority);
        item.setStatus(status);
        item.setVersion(version);
        return item;
    }

    private List<FieldDefinition> definitions() {
        return List.of(
                definition("priority", "PRIORITY", 1),
                definition("assignee_id", "USER", 1),
                definition("title", "TEXT", 1),
                definition("score", "NUMBER", 0),
                definition("other", "TEXT", 0));
    }

    private FieldDefinition definition(String key, String type, int systemFlag) {
        FieldDefinition definition = new FieldDefinition();
        definition.setFieldKey(key);
        definition.setFieldName(key);
        definition.setFieldType(type);
        definition.setSystemFlag(systemFlag);
        return definition;
    }
}
