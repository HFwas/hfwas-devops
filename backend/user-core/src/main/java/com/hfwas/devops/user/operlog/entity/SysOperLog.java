package com.hfwas.devops.user.operlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_oper_log")
public class SysOperLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String displayName;
    private String module;
    private String action;
    private String bizType;
    private String bizId;
    private String summary;
    private String status;
    private String failReason;
    private String requestIp;
    private String userAgent;
    private String clientInfo;
    private String extraJson;
    private LocalDateTime createTime;
}
