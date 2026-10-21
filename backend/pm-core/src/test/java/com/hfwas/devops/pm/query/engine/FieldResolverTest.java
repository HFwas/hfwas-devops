package com.hfwas.devops.pm.query.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.field.engine.FieldTypeRegistry;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.spi.handler.DateFieldTypeHandler;
import com.hfwas.devops.pm.field.spi.handler.DateTimeFieldTypeHandler;
import com.hfwas.devops.pm.field.spi.handler.NumberFieldTypeHandler;
import com.hfwas.devops.pm.field.spi.handler.TextFieldTypeHandler;
import com.hfwas.devops.pm.query.model.QueryCondition;
import com.hfwas.devops.pm.query.model.QueryOperator;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldResolverTest {

    private FieldResolver resolver;

    @BeforeEach
    void setUp() {
        FieldTypeRegistry registry = new FieldTypeRegistry(List.of(
                new TextFieldTypeHandler(),
                new NumberFieldTypeHandler(),
                new DateFieldTypeHandler(),
                new DateTimeFieldTypeHandler()));
        resolver = new FieldResolver(registry);
    }

    @Test
    void numberCustomFieldSupportsComparisons() {
        QueryWrapper<PmWorkItem> wrapper = new QueryWrapper<>();
        resolver.applyCondition(wrapper, condition("custom.score", QueryOperator.GT, 10),
                Map.of("score", definition("score", "NUMBER")));

        assertThat(wrapper.getSqlSegment()).contains("CAST").contains("DECIMAL");
    }

    @Test
    void dateCustomFieldSupportsBetween() {
        QueryWrapper<PmWorkItem> wrapper = new QueryWrapper<>();
        resolver.applyCondition(wrapper, condition("custom.due", QueryOperator.BETWEEN,
                        List.of("2026-10-01", "2026-10-31")),
                Map.of("due", definition("due", "DATE")));

        assertThat(wrapper.getSqlSegment()).contains("CAST").contains("BETWEEN");
    }

    @Test
    void textCustomFieldRejectsComparison() {
        QueryWrapper<PmWorkItem> wrapper = new QueryWrapper<>();
        assertThatThrownBy(() -> resolver.applyCondition(wrapper,
                condition("custom.note", QueryOperator.GT, "x"),
                Map.of("note", definition("note", "TEXT"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidCustomFieldKey() {
        QueryWrapper<PmWorkItem> wrapper = new QueryWrapper<>();
        assertThatThrownBy(() -> resolver.applyCondition(wrapper,
                condition("custom.a'); DROP", QueryOperator.EQ, "x"),
                Map.of()))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.FIELD_UNKNOWN.getCode());
    }

    @Test
    void rejectsUnknownCustomField() {
        QueryWrapper<PmWorkItem> wrapper = new QueryWrapper<>();
        assertThatThrownBy(() -> resolver.applyCondition(wrapper,
                condition("custom.missing", QueryOperator.EQ, "x"), Map.of()))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.FIELD_UNKNOWN.getCode());
    }

    private QueryCondition condition(String field, QueryOperator operator, Object value) {
        QueryCondition condition = new QueryCondition();
        condition.setField(field);
        condition.setOperator(operator);
        condition.setValue(value);
        return condition;
    }

    private FieldDefinition definition(String key, String type) {
        FieldDefinition definition = new FieldDefinition();
        definition.setFieldKey(key);
        definition.setFieldName(key);
        definition.setFieldType(type);
        definition.setSystemFlag(0);
        return definition;
    }
}
