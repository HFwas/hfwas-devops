package com.hfwas.devops.pm.workitem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hfwas.devops.pm.query.model.QueryCondition;
import com.hfwas.devops.pm.query.model.QueryConditionGroup;
import com.hfwas.devops.pm.query.model.QueryLogic;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Transition 可见性条件，形状与 QuerySpec 的条件部分一致（无分页/排序）。
 * 空 conditions + 空 groups = 始终可见。
 */
@Data
public class TransitionConditionSpec {
    private QueryLogic logic = QueryLogic.AND;
    private List<QueryCondition> conditions = new ArrayList<>();
    private List<QueryConditionGroup> groups = new ArrayList<>();

    @JsonIgnore
    public boolean isEmpty() {
        return (conditions == null || conditions.isEmpty())
                && (groups == null || groups.isEmpty());
    }
}
