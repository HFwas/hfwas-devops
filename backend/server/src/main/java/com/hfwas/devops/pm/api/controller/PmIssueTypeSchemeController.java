package com.hfwas.devops.pm.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.FieldDefinitionListDto;
import com.hfwas.devops.pm.api.dto.IssueTypeSchemeExportDto;
import com.hfwas.devops.pm.api.dto.IssueTypeSchemeImportDto;
import com.hfwas.devops.pm.api.dto.IssueTypeSchemePreviewDto;
import com.hfwas.devops.pm.scheme.model.IssueTypeSchemeExport;
import com.hfwas.devops.pm.scheme.model.IssueTypeSchemeImportPreview;
import com.hfwas.devops.pm.scheme.model.IssueTypeSchemeImportResult;
import com.hfwas.devops.pm.scheme.model.ProjectIssueTypeSchemeExport;
import com.hfwas.devops.pm.scheme.service.IssueTypeSchemeService;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pm/issue-type-schemes")
@RequiredArgsConstructor
public class PmIssueTypeSchemeController {

    private final IssueTypeSchemeService issueTypeSchemeService;

    @PostMapping("/export")
    public BaseResult<IssueTypeSchemeExport> exportType(@RequestBody IssueTypeSchemeExportDto dto) {
        return BaseResult.ok(issueTypeSchemeService.exportTypeScheme(dto.getProjectId(), dto.getTypeCode()));
    }

    @PostMapping("/export-project")
    public BaseResult<ProjectIssueTypeSchemeExport> exportProject(@RequestBody FieldDefinitionListDto dto) {
        return BaseResult.ok(issueTypeSchemeService.exportProjectSchemes(dto.getProjectId()));
    }

    @PostMapping("/preview")
    public BaseResult<IssueTypeSchemeImportPreview> preview(@RequestBody IssueTypeSchemePreviewDto dto) {
        IssueTypeSchemeExport payload = resolveScheme(dto.getScheme(), dto.getLegacyScheme());
        return BaseResult.ok(issueTypeSchemeService.previewTypeImport(
                dto.getProjectId(), dto.getTypeCode(), payload));
    }

    @OperLog(module = "pm", action = "import", bizType = "issue_type_scheme", summary = "导入事项类型方案")
    @PostMapping("/import")
    public BaseResult<IssueTypeSchemeImportResult> importType(@RequestBody IssueTypeSchemeImportDto dto) {
        IssueTypeSchemeExport payload = resolveScheme(dto.getScheme(), dto.getLegacyScheme());
        return BaseResult.ok(issueTypeSchemeService.importTypeScheme(
                dto.getProjectId(), dto.getTypeCode(), payload, dto.getMode()));
    }

    @OperLog(module = "pm", action = "import", bizType = "issue_type_scheme", summary = "批量导入项目事项类型方案")
    @PostMapping("/import-project")
    public BaseResult<List<IssueTypeSchemeImportResult>> importProject(@RequestBody IssueTypeSchemeImportDto dto) {
        if (dto.getProjectScheme() != null) {
            return BaseResult.ok(issueTypeSchemeService.importProjectSchemes(
                    dto.getProjectId(), dto.getProjectScheme(), dto.getMode()));
        }
        if (dto.getLegacyProjectScheme() != null) {
            return BaseResult.ok(issueTypeSchemeService.importProjectSchemes(
                    dto.getProjectId(),
                    issueTypeSchemeService.normalizeFromLegacy(dto.getLegacyProjectScheme()),
                    dto.getMode()));
        }
        throw new IllegalArgumentException("导入内容不能为空");
    }

    private IssueTypeSchemeExport resolveScheme(IssueTypeSchemeExport scheme,
                                                com.hfwas.devops.pm.field.model.TypeFieldSchemeExport legacy) {
        if (scheme != null) {
            return scheme;
        }
        if (legacy != null) {
            return issueTypeSchemeService.normalizeFromLegacy(legacy);
        }
        throw new IllegalArgumentException("导入内容不能为空");
    }
}
