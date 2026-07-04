package com.hfwas.devops.pm.workitem.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkItemActivityVo {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workItemId;
    private String batchId;
    /** CREATE | FIELD_CHANGE | LINK_ADD */
    private String eventType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long actorId;
    private String actorName;
    private String fieldKey;
    private String fieldName;
    private String fieldType;
    private String oldValue;
    private String newValue;
    private String oldLabel;
    private String newLabel;
    private String extraJson;
    private LocalDateTime createTime;
}
