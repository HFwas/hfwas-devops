package com.hfwas.devops.pm.field.spi.handler;

import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.FieldType;
import org.springframework.stereotype.Component;

@Component
public class MarkdownFieldTypeHandler extends TextFieldTypeHandler {
    @Override
    public FieldType type() {
        return FieldType.MARKDOWN;
    }

    @Override
    public Object normalize(FieldDefinition definition, Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw);
        return text.isBlank() ? null : text;
    }
}
