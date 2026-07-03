package com.hfwas.devops.pm.query.model;

import lombok.Data;

@Data
public class QueryCondition {
    private String field;
    private QueryOperator operator;
    private Object value;
}
