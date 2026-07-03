package com.hfwas.devops.pm.field.spi.handler;

import com.hfwas.devops.pm.field.model.FieldType;
import org.springframework.stereotype.Component;

@Component
public class UserFieldTypeHandler extends NumberFieldTypeHandler {
    @Override
    public FieldType type() {
        return FieldType.USER;
    }
}
