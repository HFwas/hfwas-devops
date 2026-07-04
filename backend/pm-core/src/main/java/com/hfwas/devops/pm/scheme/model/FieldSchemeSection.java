package com.hfwas.devops.pm.scheme.model;

import com.hfwas.devops.pm.field.model.ExportedFieldDefinition;
import com.hfwas.devops.pm.field.model.TypeFieldLayoutConfig;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Field definitions and layout section within an issue type scheme. */
@Data
public class FieldSchemeSection {
    private TypeFieldLayoutConfig layout = new TypeFieldLayoutConfig();
    private List<ExportedFieldDefinition> customFields = new ArrayList<>();
}
