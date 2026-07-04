package com.hfwas.devops.user.model;

import lombok.Data;

@Data
public class NotifyChannelSaveRequest {
    private String channel;
    private Integer enabled;
    private String configJson;
    private String remark;
}
