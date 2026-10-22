package com.hfwas.devops.pm.query.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.field.engine.FieldTypeRegistry;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.field.spi.handler.TextFieldTypeHandler;
import com.hfwas.devops.pm.query.model.QueryCondition;
import com.hfwas.devops.pm.query.model.QueryLogic;
import com.hfwas.devops.pm.query.model.QueryOperator;
import com.hfwas.devops.pm.query.model.QuerySpec;
import com.hfwas.devops.pm.query.model.SortSpec;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryEngineTest {

    @Mock
    private PmWorkItemMapper workItemMapper;
    @Mock
    private FieldDefinitionService fieldDefinitionService;
    @Mock
    private FieldResolver fieldResolver;

    @InjectMocks
    private QueryEngine queryEngine;

    @Test
    void missingTenantWithoutProjectIsRejected() {
        QuerySpec spec = new QuerySpec();
        when(fieldDefinitionService.listByProjectAndType(null, null)).thenReturn(List.of());

        assertThatThrownBy(() -> queryEngine.execute(spec, null))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.TENANT_CONTEXT_MISSING.getCode());
    }

    @Test
    void unsupportedSortFieldIsRejected() {
        QuerySpec spec = new QuerySpec();
        spec.setProjectId(1L);
        SortSpec sort = new SortSpec();
        sort.setField("id; DROP TABLE pm_work_item");
        spec.setSort(List.of(sort));
        when(fieldDefinitionService.listByProjectAndType(1L, null)).thenReturn(List.of());

        assertThatThrownBy(() -> queryEngine.execute(spec, 10L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }

    @Test
    void endTimeSortAccepted() {
        QuerySpec spec = new QuerySpec();
        spec.setProjectId(1L);
        SortSpec sort = new SortSpec();
        sort.setField("end_time");
        sort.setOrder("ASC");
        spec.setSort(List.of(sort));
        when(fieldDefinitionService.listByProjectAndType(1L, null)).thenReturn(List.of());
        when(workItemMapper.selectPage(any(), any())).thenReturn(new Page<>());

        assertThatCode(() -> queryEngine.execute(spec, 10L)).doesNotThrowAnyException();
    }

    @Test
    void topLevelOrCombinesConditions() {
        FieldResolver resolver = new FieldResolver(new FieldTypeRegistry(List.of(new TextFieldTypeHandler())));
        QueryEngine engine = new QueryEngine(workItemMapper, fieldDefinitionService, resolver);
        QuerySpec spec = new QuerySpec();
        spec.setProjectId(1L);
        spec.setLogic(QueryLogic.OR);
        spec.setConditions(List.of(
                condition("title", QueryOperator.LIKE, "%a%"),
                condition("status", QueryOperator.EQ, "open")));
        when(fieldDefinitionService.listByProjectAndType(1L, null)).thenReturn(List.of());
        when(workItemMapper.selectPage(any(), any())).thenReturn(new Page<>());

        engine.execute(spec, 10L);

        ArgumentCaptor<QueryWrapper<PmWorkItem>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(workItemMapper).selectPage(any(), captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .containsPattern("\\bOR\\b")
                .contains("project_id =");
    }

    @Test
    void topLevelOrSupportsNotLike() {
        FieldResolver resolver = new FieldResolver(new FieldTypeRegistry(List.of(new TextFieldTypeHandler())));
        QueryEngine engine = new QueryEngine(workItemMapper, fieldDefinitionService, resolver);
        QuerySpec spec = new QuerySpec();
        spec.setProjectId(1L);
        spec.setLogic(QueryLogic.OR);
        spec.setConditions(List.of(
                condition("title", QueryOperator.NOT_LIKE, "草稿"),
                condition("status", QueryOperator.EQ, "open")));
        when(fieldDefinitionService.listByProjectAndType(1L, null)).thenReturn(List.of());
        when(workItemMapper.selectPage(any(), any())).thenReturn(new Page<>());

        engine.execute(spec, 10L);

        ArgumentCaptor<QueryWrapper<PmWorkItem>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(workItemMapper).selectPage(any(), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("NOT LIKE").containsPattern("\\bOR\\b");
    }

    @Test
    void topLevelAndKeepsConditions() {
        FieldResolver resolver = new FieldResolver(new FieldTypeRegistry(List.of(new TextFieldTypeHandler())));
        QueryEngine engine = new QueryEngine(workItemMapper, fieldDefinitionService, resolver);
        QuerySpec spec = new QuerySpec();
        spec.setProjectId(1L);
        spec.setLogic(QueryLogic.AND);
        spec.setConditions(List.of(
                condition("title", QueryOperator.LIKE, "%a%"),
                condition("status", QueryOperator.EQ, "open")));
        when(fieldDefinitionService.listByProjectAndType(1L, null)).thenReturn(List.of());
        when(workItemMapper.selectPage(any(), any())).thenReturn(new Page<>());

        engine.execute(spec, 10L);

        ArgumentCaptor<QueryWrapper<PmWorkItem>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(workItemMapper).selectPage(any(), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).doesNotContainPattern("\\bOR\\b");
    }

    private QueryCondition condition(String field, QueryOperator operator, Object value) {
        QueryCondition condition = new QueryCondition();
        condition.setField(field);
        condition.setOperator(operator);
        condition.setValue(value);
        return condition;
    }

    @Test
    void unknownCustomSortFieldIsRejected() {
        QuerySpec spec = new QuerySpec();
        spec.setProjectId(1L);
        SortSpec sort = new SortSpec();
        sort.setField("custom.unknown");
        spec.setSort(List.of(sort));
        when(fieldDefinitionService.listByProjectAndType(1L, null)).thenReturn(List.of());

        assertThatThrownBy(() -> queryEngine.execute(spec, 10L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }
}
