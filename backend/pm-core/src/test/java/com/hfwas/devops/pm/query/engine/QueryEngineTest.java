package com.hfwas.devops.pm.query.engine;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.query.model.QuerySpec;
import com.hfwas.devops.pm.query.model.SortSpec;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
