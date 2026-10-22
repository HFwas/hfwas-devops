package com.hfwas.devops.pm.workitem.service;

import com.hfwas.devops.pm.query.model.QueryCondition;
import com.hfwas.devops.pm.query.model.QueryOperator;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.model.TransitionConditionSpec;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TransitionConditionEvaluatorTest {

    @Mock
    private CurrentUserAccessor currentUserAccessor;

    private TransitionConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new TransitionConditionEvaluator(currentUserAccessor);
    }

    @Test
    void comparesEndTimeByTemporalValue() {
        PmWorkItem item = new PmWorkItem();
        item.setEndTime(LocalDateTime.of(2026, 11, 5, 10, 0));

        assertThat(matches(item, QueryOperator.GT, "2026-11-01 00:00:00")).isTrue();
        assertThat(matches(item, QueryOperator.LT, "2026-11-01 00:00:00")).isFalse();
        assertThat(matches(item, QueryOperator.EQ, "2026-11-05T10:00:00")).isTrue();
    }

    @Test
    void comparesEndTimeBetween() {
        PmWorkItem item = new PmWorkItem();
        item.setEndTime(LocalDateTime.of(2026, 11, 5, 10, 0));

        QueryCondition condition = new QueryCondition();
        condition.setField("end_time");
        condition.setOperator(QueryOperator.BETWEEN);
        condition.setValue(java.util.List.of("2026-11-01 00:00:00", "2026-11-30 23:59:59"));
        TransitionConditionSpec spec = new TransitionConditionSpec();
        spec.setConditions(java.util.List.of(condition));

        assertThat(evaluator.matches(item, spec)).isTrue();
    }

    private boolean matches(PmWorkItem item, QueryOperator operator, Object value) {
        QueryCondition condition = new QueryCondition();
        condition.setField("end_time");
        condition.setOperator(operator);
        condition.setValue(value);
        TransitionConditionSpec spec = new TransitionConditionSpec();
        spec.setConditions(java.util.List.of(condition));
        return evaluator.matches(item, spec);
    }
}
