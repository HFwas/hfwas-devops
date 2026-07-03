package com.hfwas.devops.pm.workitem.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkItemCommentVo {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workItemId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;
    private String content;
    private String authorName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorId;
    private LocalDateTime createTime;
    private boolean deletable;
}
