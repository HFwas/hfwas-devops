package com.hfwas.devops.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_notify_channel")
public class SysNotifyChannel {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** site | dingtalk | feishu */
    private String channel;
    private Integer enabled;
    private String configJson;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
