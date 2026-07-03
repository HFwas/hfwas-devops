package com.hfwas.devops.pm.api.dto;

import lombok.Data;

@Data
public class WorkItemCommentSaveDto {
    private Long workItemId;
    private String content;
    private Long parentId;
    private String authorName;
}
