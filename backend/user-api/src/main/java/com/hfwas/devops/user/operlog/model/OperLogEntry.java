package com.hfwas.devops.user.operlog.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OperLogEntry {
    private String module;
    private String action;
    private String bizType;
    private String bizId;
    private String summary;
    private String extraJson;
    /** success | fail */
    private String status;
    private String failReason;
}
