package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TransitionValidatorVO {
    /** @see TransitionValidatorType */
    private String type;
    /** REQUIRED_FIELDS: 必填字段 key 列表 */
    private List<String> fieldKeys = new ArrayList<>();
}
