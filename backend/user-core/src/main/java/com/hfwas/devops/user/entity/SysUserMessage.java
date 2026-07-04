package com.hfwas.devops.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_message")
public class SysUserMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** Tenant context when message was sent; optional. */
    private Long tenantId;
    /** system | operation | announcement */
    private String category;
    private String title;
    private String content;
    /** 0 unread, 1 read */
    private Integer readFlag;
    private Long senderId;
    private String senderName;
    private String bizType;
    private String bizId;
    private String linkUrl;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private LocalDateTime readTime;
    @TableLogic
    private Integer delFlag;
}
