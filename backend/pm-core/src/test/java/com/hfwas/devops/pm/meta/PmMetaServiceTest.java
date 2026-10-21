package com.hfwas.devops.pm.meta;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.pm.workitem.service.StatusDefinitionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PmMetaServiceTest {

    @Mock
    private PmWorkItemTypeMapper workItemTypeMapper;
    @Mock
    private PmWorkItemMapper workItemMapper;
    @Mock
    private PmProjectIssueTypeMapper projectIssueTypeMapper;
    @Mock
    private StatusDefinitionService statusDefinitionService;

    @InjectMocks
    private PmMetaService metaService;

    @Test
    void rejectsDeletingBuiltInType() {
        PmWorkItemType builtIn = type("task");
        builtIn.setBuiltIn(1);
        when(workItemTypeMapper.selectOne(any())).thenReturn(builtIn);

        assertThatThrownBy(() -> metaService.deleteType("task"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.BUILT_IN_TYPE_PROTECTED.getCode());
        verify(workItemTypeMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void clonesWorkflowFromTemplate() {
        PmWorkItemType template = type("iteration");
        template.setBuiltIn(1);
        when(workItemTypeMapper.selectOne(any())).thenReturn(null, template);
        when(workItemTypeMapper.selectList(any())).thenReturn(List.of());
        when(workItemTypeMapper.insert(any(PmWorkItemType.class))).thenReturn(1);
        PmWorkItemType input = new PmWorkItemType();
        input.setCode("custom");
        input.setName("自定义");
        input.setColor("#123456");
        input.setTemplateCode("iteration");

        metaService.saveType(input);

        verify(statusDefinitionService).cloneSystemWorkflow("iteration", "custom");
        verify(workItemTypeMapper).insert(any(PmWorkItemType.class));
    }

    @Test
    void rejectsUnavailableTemplate() {
        PmWorkItemType input = new PmWorkItemType();
        input.setCode("custom");
        input.setName("自定义");
        input.setColor("#123456");
        input.setTemplateCode("missing");

        assertThatThrownBy(() -> metaService.saveType(input))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.TYPE_TEMPLATE_INVALID.getCode());
    }

    @Test
    void listsBuiltInTemplates() {
        PmWorkItemType iteration = type("iteration");
        iteration.setBuiltIn(1);
        iteration.setDescription("按周期规划交付范围");
        iteration.setTemplateCode("iteration");
        when(workItemTypeMapper.selectList(any())).thenReturn(List.of(iteration));

        List<PmWorkItemTypeTemplateVO> templates = metaService.listTemplates();

        assertThat(templates).hasSize(1);
        assertThat(templates.get(0).getWorkflowTemplateCode()).isEqualTo("iteration");
    }

    private PmWorkItemType type(String code) {
        PmWorkItemType type = new PmWorkItemType();
        type.setId(1L);
        type.setCode(code);
        type.setName(code);
        type.setColor("#2080f0");
        type.setSortOrder(1);
        type.setEnabled(1);
        return type;
    }
}
