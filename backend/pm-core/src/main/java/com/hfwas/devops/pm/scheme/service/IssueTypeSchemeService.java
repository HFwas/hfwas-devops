package com.hfwas.devops.pm.scheme.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.field.model.FieldSchemeImportMode;
import com.hfwas.devops.pm.field.model.ProjectFieldSchemeExport;
import com.hfwas.devops.pm.field.model.TypeFieldSchemeExport;
import com.hfwas.devops.pm.meta.PmWorkItemType;
import com.hfwas.devops.pm.meta.PmWorkItemTypeMapper;
import com.hfwas.devops.pm.scheme.model.*;
import com.hfwas.devops.pm.scheme.spi.IssueTypeSchemeContributor;
import com.hfwas.devops.pm.scheme.spi.IssueTypeSchemeContributorRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IssueTypeSchemeService {

    private final IssueTypeSchemeContributorRegistry contributorRegistry;
    private final PmWorkItemTypeMapper workItemTypeMapper;

    public IssueTypeSchemeExport exportTypeScheme(Long projectId, String typeCode) {
        validateTypeCode(typeCode);
        IssueTypeSchemeExport export = new IssueTypeSchemeExport();
        export.setTypeCode(typeCode);
        export.setTypeName(resolveTypeName(typeCode));
        for (IssueTypeSchemeContributor contributor : contributorRegistry.all()) {
            contributor.exportSection(export, projectId, typeCode);
        }
        export.setExportedAt(LocalDateTime.now());
        return export;
    }

    public ProjectIssueTypeSchemeExport exportProjectSchemes(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId 不能为空");
        }
        List<PmWorkItemType> types = workItemTypeMapper.selectList(
                Wrappers.<PmWorkItemType>lambdaQuery()
                        .eq(PmWorkItemType::getEnabled, 1)
                        .orderByAsc(PmWorkItemType::getSortOrder));
        ProjectIssueTypeSchemeExport export = new ProjectIssueTypeSchemeExport();
        for (PmWorkItemType type : types) {
            export.getSchemes().add(exportTypeScheme(projectId, type.getCode()));
        }
        export.setExportedAt(LocalDateTime.now());
        return export;
    }

    public IssueTypeSchemeImportPreview previewTypeImport(Long projectId, String typeCode,
                                                          IssueTypeSchemeExport payload) {
        IssueTypeSchemeExport normalized = normalizePayload(payload);
        validateTypePayload(normalized);
        IssueTypeSchemeImportPreview preview = new IssueTypeSchemeImportPreview();
        preview.setTypeCode(typeCode);
        preview.setSourceTypeCode(normalized.getTypeCode());
        if (!StringUtils.equals(typeCode, normalized.getTypeCode())) {
            preview.getWarnings().add("导入源事项类型为「" + normalized.getTypeCode() + "」，将应用到当前「" + typeCode + "」");
        }
        for (IssueTypeSchemeContributor contributor : contributorRegistry.all()) {
            contributor.previewSection(normalized, projectId, typeCode, preview);
        }
        if (preview.getSections().isEmpty()) {
            preview.getWarnings().add("导入包中未包含可识别的配置节");
        }
        return preview;
    }

    @Transactional
    public IssueTypeSchemeImportResult importTypeScheme(Long projectId, String typeCode,
                                                        IssueTypeSchemeExport payload,
                                                        FieldSchemeImportMode mode) {
        IssueTypeSchemeExport normalized = normalizePayload(payload);
        validateTypePayload(normalized);
        if (projectId == null || StringUtils.isBlank(typeCode)) {
            throw new IllegalArgumentException("projectId 与 typeCode 不能为空");
        }
        IssueTypeSchemeImportResult result = new IssueTypeSchemeImportResult();
        result.setTypeCode(typeCode);
        for (IssueTypeSchemeContributor contributor : contributorRegistry.all()) {
            contributor.importSection(normalized, projectId, typeCode, mode, result);
        }
        if (result.getSectionsApplied().isEmpty()) {
            result.getWarnings().add("未导入任何配置节，请检查文件格式");
        }
        return result;
    }

    @Transactional
    public List<IssueTypeSchemeImportResult> importProjectSchemes(Long projectId,
                                                                  ProjectIssueTypeSchemeExport payload,
                                                                  FieldSchemeImportMode mode) {
        ProjectIssueTypeSchemeExport normalized = normalizeProjectPayload(payload);
        validateProjectPayload(normalized);
        List<IssueTypeSchemeImportResult> results = new ArrayList<>();
        for (IssueTypeSchemeExport scheme : normalized.getSchemes()) {
            if (scheme == null || StringUtils.isBlank(scheme.getTypeCode())) {
                continue;
            }
            results.add(importTypeScheme(projectId, scheme.getTypeCode(), scheme, mode));
        }
        return results;
    }

    /**
     * Accept unified scheme or legacy field-only exports for backward compatibility.
     */
    public IssueTypeSchemeExport normalizePayload(IssueTypeSchemeExport payload) {
        if (payload == null) {
            throw new IllegalArgumentException("导入内容不能为空");
        }
        if (IssueTypeSchemeExport.KIND.equals(payload.getKind())) {
            return payload;
        }
        throw new IllegalArgumentException("不支持的配置包类型: " + payload.getKind());
    }

    public IssueTypeSchemeExport normalizeFromLegacy(TypeFieldSchemeExport legacy) {
        if (legacy == null) {
            throw new IllegalArgumentException("导入内容不能为空");
        }
        IssueTypeSchemeExport scheme = new IssueTypeSchemeExport();
        scheme.setSchemaVersion(IssueTypeSchemeExport.SCHEMA_VERSION);
        scheme.setKind(IssueTypeSchemeExport.KIND);
        scheme.setTypeCode(legacy.getTypeCode());
        scheme.setTypeName(legacy.getTypeName());
        FieldSchemeSection section = new FieldSchemeSection();
        section.setLayout(legacy.getLayout());
        section.setCustomFields(legacy.getCustomFields());
        scheme.setFieldScheme(section);
        return scheme;
    }

    public ProjectIssueTypeSchemeExport normalizeFromLegacy(ProjectFieldSchemeExport legacy) {
        if (legacy == null) {
            throw new IllegalArgumentException("导入内容不能为空");
        }
        ProjectIssueTypeSchemeExport project = new ProjectIssueTypeSchemeExport();
        project.setSchemaVersion(ProjectIssueTypeSchemeExport.SCHEMA_VERSION);
        project.setKind(ProjectIssueTypeSchemeExport.KIND);
        if (legacy.getSchemes() != null) {
            for (TypeFieldSchemeExport item : legacy.getSchemes()) {
                project.getSchemes().add(normalizeFromLegacy(item));
            }
        }
        return project;
    }

    private ProjectIssueTypeSchemeExport normalizeProjectPayload(ProjectIssueTypeSchemeExport payload) {
        if (payload == null) {
            throw new IllegalArgumentException("导入内容不能为空");
        }
        if (ProjectIssueTypeSchemeExport.KIND.equals(payload.getKind())) {
            return payload;
        }
        throw new IllegalArgumentException("不支持的配置包类型: " + payload.getKind());
    }

    private void validateTypePayload(IssueTypeSchemeExport payload) {
        if (payload.getSchemaVersion() != IssueTypeSchemeExport.SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的 schema 版本: " + payload.getSchemaVersion());
        }
        if (StringUtils.isBlank(payload.getTypeCode())) {
            throw new IllegalArgumentException("导入包缺少 typeCode");
        }
        validateTypeCode(payload.getTypeCode());
    }

    private void validateProjectPayload(ProjectIssueTypeSchemeExport payload) {
        if (payload.getSchemaVersion() != ProjectIssueTypeSchemeExport.SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的 schema 版本: " + payload.getSchemaVersion());
        }
        if (payload.getSchemes() == null || payload.getSchemes().isEmpty()) {
            throw new IllegalArgumentException("导入包中没有事项类型配置");
        }
    }

    private void validateTypeCode(String typeCode) {
        if (StringUtils.isBlank(typeCode)) {
            throw new IllegalArgumentException("typeCode 不能为空");
        }
        PmWorkItemType type = workItemTypeMapper.selectOne(
                Wrappers.<PmWorkItemType>lambdaQuery().eq(PmWorkItemType::getCode, typeCode));
        if (type == null || type.getEnabled() == null || type.getEnabled() != 1) {
            throw new IllegalArgumentException("不支持的事项类型: " + typeCode);
        }
    }

    private String resolveTypeName(String typeCode) {
        PmWorkItemType type = workItemTypeMapper.selectOne(
                Wrappers.<PmWorkItemType>lambdaQuery().eq(PmWorkItemType::getCode, typeCode));
        return type != null ? type.getName() : typeCode;
    }
}
