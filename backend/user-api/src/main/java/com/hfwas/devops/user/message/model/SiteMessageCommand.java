package com.hfwas.devops.user.message.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SiteMessageCommand {
    /** system | operation | announcement */
    private String category;
    private String title;
    private String content;
    private Long tenantId;
    private String bizType;
    private String bizId;
    private String linkUrl;
    private Long senderId;
    private String senderName;
}
