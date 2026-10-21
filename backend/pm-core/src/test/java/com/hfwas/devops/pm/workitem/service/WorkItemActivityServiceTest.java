package com.hfwas.devops.pm.workitem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.project.service.ProjectService;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.entity.PmWorkItemActivity;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemActivityMapper;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.pm.workitem.model.WorkItemActivityVo;
import com.hfwas.devops.pm.workitem.model.WorkItemFieldChange;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkItemActivityServiceTest {

    @Mock
    private PmWorkItemActivityMapper activityMapper;
    @Mock
    private PmWorkItemMapper workItemMapper;
    @Mock
    private WorkItemChangeDetector changeDetector;
    @Mock
    private WorkItemFieldDisplayResolver displayResolver;
    @Mock
    private WorkItemKeyEnricher keyEnricher;
    @Mock
    private CurrentUserAccessor currentUserAccessor;
    @Mock
    private ProjectService projectService;

    private WorkItemActivityService service;

    @BeforeEach
    void setUp() {
        service = new WorkItemActivityService(activityMapper, workItemMapper, changeDetector,
                displayResolver, keyEnricher, currentUserAccessor, projectService);
    }

    @Test
    void pagesActivitiesNewestFirst() {
        when(workItemMapper.selectById(1L)).thenReturn(item());
        Page<PmWorkItemActivity> page = new Page<>(2, 5, 12);
        List<PmWorkItemActivity> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            PmWorkItemActivity row = new PmWorkItemActivity();
            row.setId((long) i);
            row.setWorkItemId(1L);
            row.setEventType("FIELD_CHANGE");
            row.setActorName("张三");
            rows.add(row);
        }
        page.setRecords(rows);
        when(activityMapper.selectPage(any(), any())).thenReturn(page);

        IPage<WorkItemActivityVo> result = service.pageByWorkItem(1L, 2, 5);

        assertThat(result.getTotal()).isEqualTo(12);
        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(5);
        verify(projectService).assertProjectAccess(10L);
    }

    @Test
    void sharesBatchIdAcrossItems() {
        FieldDefinition def = new FieldDefinition();
        def.setFieldKey("priority");
        def.setFieldName("优先级");
        def.setFieldType("PRIORITY");
        when(changeDetector.detect(any(), any(), any())).thenReturn(List.of(
                new WorkItemFieldChange("priority", "优先级", "PRIORITY", "medium", "high")));
        when(displayResolver.serializeValue(any(Object.class))).thenReturn("v");
        when(displayResolver.toLabel(any(), any(), any())).thenReturn("中");
        when(activityMapper.insert(any(PmWorkItemActivity.class))).thenReturn(1);

        service.recordChanges(item(), item(), List.of(def), "batch-x");

        ArgumentCaptor<PmWorkItemActivity> captor = ArgumentCaptor.forClass(PmWorkItemActivity.class);
        verify(activityMapper).insert(captor.capture());
        assertThat(captor.getValue().getBatchId()).isEqualTo("batch-x");
        assertThat(captor.getValue().getFieldKey()).isEqualTo("priority");
    }

    private PmWorkItem item() {
        PmWorkItem item = new PmWorkItem();
        item.setId(1L);
        item.setProjectId(10L);
        item.setTypeCode("task");
        item.setPriority("medium");
        return item;
    }
}
