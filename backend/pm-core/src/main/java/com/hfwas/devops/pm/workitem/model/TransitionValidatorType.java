package com.hfwas.devops.pm.workitem.model;

import java.util.Set;

public final class TransitionValidatorType {

    /** 流转提交前指定字段必须有值 */
    public static final String REQUIRED_FIELDS = "REQUIRED_FIELDS";

    private static final Set<String> ALL = Set.of(REQUIRED_FIELDS);

    private TransitionValidatorType() {
    }

    public static boolean isKnown(String type) {
        return type != null && ALL.contains(type);
    }
}
