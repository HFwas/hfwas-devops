package com.hfwas.devops.pm.scheme.contributor;

import com.hfwas.devops.pm.field.model.FieldSchemeImportMode;
import com.hfwas.devops.pm.field.model.FieldSchemeImportPreview;
import com.hfwas.devops.pm.field.model.FieldSchemeImportResult;
import com.hfwas.devops.pm.field.model.TypeFieldSchemeExport;
import com.hfwas.devops.pm.field.service.FieldSchemeService;
import com.hfwas.devops.pm.scheme.model.FieldSchemeSection;
import com.hfwas.devops.pm.scheme.model.IssueTypeSchemeExport;
import com.hfwas.devops.pm.scheme.model.IssueTypeSchemeImportPreview;
import com.hfwas.devops.pm.scheme.model.IssueTypeSchemeImportResult;
import com.hfwas.devops.pm.scheme.spi.IssueTypeSchemeContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FieldSchemeContributor implements IssueTypeSchemeContributor {

    public static final String KEY = "fieldScheme";

    private final FieldSchemeService fieldSchemeService;

    @Override
    public String sectionKey() {
        return KEY;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void exportSection(IssueTypeSchemeExport target, Long projectId, String typeCode) {
        TypeFieldSchemeExport exported = fieldSchemeService.exportTypeScheme(projectId, typeCode);
        FieldSchemeSection section = new FieldSchemeSection();
        section.setLayout(exported.getLayout());
        section.setCustomFields(exported.getCustomFields());
        target.setFieldScheme(section);
    }

    @Override
    public boolean importSection(IssueTypeSchemeExport source, Long projectId, String typeCode,
                                 FieldSchemeImportMode mode, IssueTypeSchemeImportResult result) {
        if (!hasSection(source)) {
            return false;
        }
        FieldSchemeImportResult fieldResult = fieldSchemeService.importTypeScheme(
                projectId, typeCode, toLegacyExport(source), mode);
        result.setFieldsCreated(fieldResult.getFieldsCreated());
        result.setFieldsUpdated(fieldResult.getFieldsUpdated());
        result.setFieldsSkipped(fieldResult.getFieldsSkipped());
        result.setLayoutApplied(fieldResult.isLayoutApplied());
        result.getWarnings().addAll(fieldResult.getWarnings());
        result.getSectionsApplied().add(KEY);
        return true;
    }

    @Override
    public void previewSection(IssueTypeSchemeExport source, Long projectId, String typeCode,
                               IssueTypeSchemeImportPreview preview) {
        if (!hasSection(source)) {
            return;
        }
        FieldSchemeImportPreview fieldPreview = fieldSchemeService.previewTypeImport(
                projectId, typeCode, toLegacyExport(source));
        preview.getSections().add(KEY);
        preview.setCustomFieldCount(fieldPreview.getCustomFieldCount());
        preview.setLayoutFieldCount(fieldPreview.getLayoutFieldCount());
        preview.setFieldsToCreate(fieldPreview.getFieldsToCreate());
        preview.setFieldsToUpdate(fieldPreview.getFieldsToUpdate());
        preview.getWarnings().addAll(fieldPreview.getWarnings());
    }

    @Override
    public boolean hasSection(IssueTypeSchemeExport source) {
        return source != null && source.getFieldScheme() != null;
    }

    private TypeFieldSchemeExport toLegacyExport(IssueTypeSchemeExport source) {
        TypeFieldSchemeExport legacy = new TypeFieldSchemeExport();
        legacy.setTypeCode(source.getTypeCode());
        legacy.setTypeName(source.getTypeName());
        legacy.setLayout(source.getFieldScheme().getLayout());
        legacy.setCustomFields(source.getFieldScheme().getCustomFields());
        return legacy;
    }
}
