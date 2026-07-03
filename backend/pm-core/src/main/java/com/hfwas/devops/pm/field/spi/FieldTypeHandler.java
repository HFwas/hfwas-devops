package com.hfwas.devops.pm.field.spi;

import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.FieldType;
import com.hfwas.devops.pm.query.model.QueryCondition;

public interface FieldTypeHandler {
    FieldType type();

    void validate(FieldDefinition definition, Object value);

    Object normalize(FieldDefinition definition, Object raw);

    default boolean supportsQuery() {
        return true;
    }

    String buildQuerySql(FieldDefinition definition, QueryCondition condition);
}
