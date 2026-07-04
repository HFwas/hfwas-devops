package com.hfwas.devops.user.model;

import lombok.Data;

import java.util.List;

@Data
public class MessageSendRequest {
    /** all | tenant | users */
    private String targetType;
    private Long tenantId;
    private List<Long> userIds;
    /** system | announcement */
    private String category = "announcement";
    private String title;
    private String content;
    private String linkUrl;
}
