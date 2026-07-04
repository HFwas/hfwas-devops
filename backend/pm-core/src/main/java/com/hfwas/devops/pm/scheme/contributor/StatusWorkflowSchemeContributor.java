package com.hfwas.devops.pm.scheme.contributor;

import com.hfwas.devops.pm.field.model.FieldSchemeImportMode;
import com.hfwas.devops.pm.scheme.model.ExportedStatusDefinition;
import com.hfwas.devops.pm.scheme.model.IssueTypeSchemeExport;
import com.hfwas.devops.pm.scheme.model.IssueTypeSchemeImportPreview;
import com.hfwas.devops.pm.scheme.model.IssueTypeSchemeImportResult;
import com.hfwas.devops.pm.scheme.model.StatusWorkflowSection;
import com.hfwas.devops.pm.scheme.spi.IssueTypeSchemeContributor;
import com.hfwas.devops.pm.workitem.model.StatusDefinitionVO;
import com.hfwas.devops.pm.workitem.model.StatusWorkflowVO;
import com.hfwas.devops.pm.workitem.service.StatusDefinitionService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StatusWorkflowSchemeContributor implements IssueTypeSchemeContributor {

    public static final String KEY = "statusWorkflow";

    private final StatusDefinitionService statusDefinitionService;

    @Override
    public String sectionKey() {
        return KEY;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void exportSection(IssueTypeSchemeExport target, Long projectId, String typeCode) {
        StatusWorkflowVO workflow = statusDefinitionService.getWorkflow(projectId, typeCode);
        StatusWorkflowSection section = new StatusWorkflowSection();
        section.setStatuses(workflow.getStatuses().stream()
                .map(this::toExported)
                .toList());
        target.setStatusWorkflow(section);
    }

    @Override
    public boolean importSection(IssueTypeSchemeExport source, Long projectId, String typeCode,
                                 FieldSchemeImportMode mode, IssueTypeSchemeImportResult result) {
        if (!hasSection(source)) {
            return false;
        }
        List<StatusDefinitionVO> statuses = source.getStatusWorkflow().getStatuses().stream()
                .map(this::toVo)
                .toList();
        statusDefinitionService.saveWorkflow(projectId, typeCode, statuses);
        result.setStatusWorkflowApplied(true);
        result.setStatusCount(statuses.size());
        result.getSectionsApplied().add(KEY);
        return true;
    }

    @Override
    public void previewSection(IssueTypeSchemeExport source, Long projectId, String typeCode,
                               IssueTypeSchemeImportPreview preview) {
        if (!hasSection(source)) {
            return;
        }
        int count = source.getStatusWorkflow().getStatuses() != null
                ? source.getStatusWorkflow().getStatuses().size() : 0;
        preview.getSections().add(KEY);
        preview.setStatusCount(count);
        preview.setStatusWorkflowWillApply(count > 0);
        if (count == 0) {
            preview.getWarnings().add("状态流转配置为空，将跳过");
        }
    }

    @Override
    public boolean hasSection(IssueTypeSchemeExport source) {
        return source != null && source.getStatusWorkflow() != null
                && source.getStatusWorkflow().getStatuses() != null
                && !source.getStatusWorkflow().getStatuses().isEmpty();
    }

    private ExportedStatusDefinition toExported(StatusDefinitionVO vo) {
        ExportedStatusDefinition exported = new ExportedStatusDefinition();
        exported.setStatusCode(vo.getStatusCode());
        exported.setStatusName(vo.getStatusName());
        exported.setSortOrder(vo.getSortOrder());
        exported.setIsInitial(vo.getIsInitial());
        exported.setIsFinal(vo.getIsFinal());
        exported.setTransitions(vo.getTransitions() != null ? new ArrayList<>(vo.getTransitions()) : null);
        return exported;
    }

    private StatusDefinitionVO toVo(ExportedStatusDefinition exported) {
        StatusDefinitionVO vo = new StatusDefinitionVO();
        vo.setStatusCode(StringUtils.trimToEmpty(exported.getStatusCode()));
        vo.setStatusName(StringUtils.defaultIfBlank(exported.getStatusName(), exported.getStatusCode()).trim());
        vo.setSortOrder(exported.getSortOrder());
        vo.setIsInitial(exported.getIsInitial());
        vo.setIsFinal(exported.getIsFinal());
        vo.setTransitions(exported.getTransitions() != null ? new ArrayList<>(exported.getTransitions()) : null);
        return vo;
    }
}
