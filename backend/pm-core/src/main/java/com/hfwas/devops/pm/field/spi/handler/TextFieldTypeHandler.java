package com.hfwas.devops.pm.field.spi.handler;

import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.FieldType;
import org.springframework.stereotype.Component;

@Component
public class TextFieldTypeHandler extends AbstractFieldTypeHandler {
    @Override
    public FieldType type() {
        return FieldType.TEXT;
    }

    @Override
    public void validate(FieldDefinition definition, Object value) {
        if (definition.getRequiredFlag() != null && definition.getRequiredFlag() == 1) {
            requireNonNull(value, definition);
        }
    }

    @Override
    public Object normalize(FieldDefinition definition, Object raw) {
        return raw == null ? null : String.valueOf(raw).trim();
    }
}
