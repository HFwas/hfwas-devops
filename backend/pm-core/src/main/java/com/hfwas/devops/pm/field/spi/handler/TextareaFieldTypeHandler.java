package com.hfwas.devops.pm.field.spi.handler;

import com.hfwas.devops.pm.field.model.FieldType;
import org.springframework.stereotype.Component;

@Component
public class TextareaFieldTypeHandler extends TextFieldTypeHandler {
    @Override
    public FieldType type() {
        return FieldType.TEXTAREA;
    }
}
