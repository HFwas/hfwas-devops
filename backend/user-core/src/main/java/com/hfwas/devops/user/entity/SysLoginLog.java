package com.hfwas.devops.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_login_log")
public class SysLoginLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String displayName;
    /** login_success | login_fail | logout */
    private String action;
    private String loginIp;
    private String userAgent;
    private String clientInfo;
    private String failReason;
    private LocalDateTime createTime;
}
