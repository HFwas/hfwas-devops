package com.hfwas.devops.pm.query.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QueryConditionGroup {
    private QueryLogic logic = QueryLogic.AND;
    private List<QueryCondition> conditions = new ArrayList<>();
    private List<QueryConditionGroup> groups = new ArrayList<>();
}
