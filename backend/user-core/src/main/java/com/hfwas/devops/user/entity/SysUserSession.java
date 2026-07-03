package com.hfwas.devops.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_session")
public class SysUserSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** JWT jti — unique per login */
    private String jti;
    private String loginIp;
    private String userAgent;
    private LocalDateTime loginTime;
    private LocalDateTime lastActiveTime;
    private LocalDateTime expireTime;
    /** 0=active, 1=revoked (logout / force offline) */
    private Integer revoked;
}
