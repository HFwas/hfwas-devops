package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventVO {
    private String type;            // Normal / Warning
    private String reason;
    private String message;
    private Integer count;
    private LocalDateTime firstTimestamp;
    private LocalDateTime lastTimestamp;
    private String involvedKind;
    private String involvedName;
    private String involvedUid;
    private String source;          // component name
}