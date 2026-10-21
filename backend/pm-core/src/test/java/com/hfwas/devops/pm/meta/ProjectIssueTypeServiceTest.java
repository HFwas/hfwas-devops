package com.hfwas.devops.pm.meta;

import com.hfwas.devops.pm.field.mapper.FieldDefinitionMapper;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.project.service.ProjectAccessGuard;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectIssueTypeServiceTest {

    @Mock
    private PmProjectIssueTypeMapper projectIssueTypeMapper;
    @Mock
    private PmMetaService metaService;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private PmWorkItemMapper workItemMapper;
    @Mock
    private FieldDefinitionMapper fieldDefinitionMapper;

    @InjectMocks
    private ProjectIssueTypeService service;

    @Test
    void enrichesTypeCounts() {
        when(projectIssueTypeMapper.selectList(any())).thenReturn(List.of());
        PmWorkItemType task = type("task");
        PmWorkItemType bug = type("bug");
        when(metaService.listTypes(false)).thenReturn(List.of(task, bug));
        when(workItemMapper.countByType(10L)).thenReturn(List.of(Map.of("typeCode", "task", "cnt", 5L)));
        when(fieldDefinitionMapper.selectList(any())).thenReturn(List.of(customField()));

        List<PmWorkItemType> result = service.listForProject(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getWorkItemCount()).isEqualTo(5);
        assertThat(result.get(0).getFieldCount()).isEqualTo(9);
        assertThat(result.get(1).getWorkItemCount()).isZero();
        assertThat(result.get(1).getFieldCount()).isEqualTo(8);
        org.mockito.Mockito.verify(projectAccessGuard).assertAccess(eq(10L));
    }

    private PmWorkItemType type(String code) {
        PmWorkItemType type = new PmWorkItemType();
        type.setCode(code);
        type.setName(code);
        return type;
    }

    private FieldDefinition customField() {
        FieldDefinition definition = new FieldDefinition();
        definition.setFieldKey("score");
        definition.setFieldName("故事点");
        definition.setFieldType("NUMBER");
        definition.setSystemFlag(0);
        definition.setApplicableTypes(List.of("task"));
        return definition;
    }
}
