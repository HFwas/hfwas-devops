package com.hfwas.devops.pm.query.model;

import com.hfwas.devops.pm.common.PmPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class QuerySpec extends PmPageRequest {
    private Long projectId;
    private String typeCode;
    private QueryLogic logic = QueryLogic.AND;
    private List<QueryCondition> conditions = new ArrayList<>();
    private List<QueryConditionGroup> groups = new ArrayList<>();
    private List<SortSpec> sort = new ArrayList<>();
}
