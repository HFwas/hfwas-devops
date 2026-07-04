package com.hfwas.devops.pm.scheme.spi;

import com.hfwas.devops.pm.field.model.FieldSchemeImportMode;
import com.hfwas.devops.pm.scheme.model.IssueTypeSchemeExport;
import com.hfwas.devops.pm.scheme.model.IssueTypeSchemeImportPreview;
import com.hfwas.devops.pm.scheme.model.IssueTypeSchemeImportResult;

/**
 * SPI for extensible issue type scheme sections (fields, workflow, future modules).
 * Register new {@code @Component} implementations to add export/import support.
 */
public interface IssueTypeSchemeContributor {

    /** Section key in {@link IssueTypeSchemeExport}, e.g. fieldScheme, statusWorkflow. */
    String sectionKey();

    /** Lower values export/import first. */
    default int order() {
        return 100;
    }

    void exportSection(IssueTypeSchemeExport target, Long projectId, String typeCode);

    /** Returns true if this section was present and applied. */
    boolean importSection(IssueTypeSchemeExport source, Long projectId, String typeCode,
                          FieldSchemeImportMode mode, IssueTypeSchemeImportResult result);

    void previewSection(IssueTypeSchemeExport source, Long projectId, String typeCode,
                        IssueTypeSchemeImportPreview preview);

    boolean hasSection(IssueTypeSchemeExport source);
}
