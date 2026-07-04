package com.hfwas.devops.user.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotifyChannelVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String channel;
    private String channelLabel;
    private Integer enabled;
    /** Config with secrets masked. */
    private String configJson;
    private String remark;
    private LocalDateTime updateTime;
}
