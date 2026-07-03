package com.hfwas.devops.pm.api.dto;

import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.FieldOption;
import lombok.Data;

import java.util.List;

@Data
public class FieldDefinitionSaveDto {
    private FieldDefinition definition;
    private List<FieldOption> options;
}
