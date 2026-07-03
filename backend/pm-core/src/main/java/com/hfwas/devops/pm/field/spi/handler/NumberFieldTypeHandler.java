package com.hfwas.devops.pm.field.spi.handler;

import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.FieldType;
import org.springframework.stereotype.Component;

@Component
public class NumberFieldTypeHandler extends AbstractFieldTypeHandler {
    @Override
    public FieldType type() {
        return FieldType.NUMBER;
    }

    @Override
    public void validate(FieldDefinition definition, Object value) {
        if (definition.getRequiredFlag() != null && definition.getRequiredFlag() == 1) {
            requireNonNull(value, definition);
        }
    }

    @Override
    public Object normalize(FieldDefinition definition, Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number;
        }
        return Double.valueOf(String.valueOf(raw));
    }
}
