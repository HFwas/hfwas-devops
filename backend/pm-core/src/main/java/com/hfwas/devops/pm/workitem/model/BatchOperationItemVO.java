package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchOperationItemVO {
    public static final String SUCCESS = "SUCCESS";
    public static final String SKIPPED = "SKIPPED";
    public static final String FAILED = "FAILED";

    private Long id;
    private String outcome;
    private Integer code;
    private String message;
    private Long version;
    private String fromStatus;
    private String toStatus;
    private List<BatchFieldChangeVO> changes = new ArrayList<>();
}
