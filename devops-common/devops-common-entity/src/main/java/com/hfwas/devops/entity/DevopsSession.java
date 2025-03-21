package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/3/21
 */
@TableName("session")
public class DevopsSession {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @TableField("client_id")
    private String clientId;
    @TableField("access_token")
    private String accessToken;
    @TableField("refresh_token")
    private String refreshToken;
    @TableField("user_name")
    private String userName;
    @TableField("principal_name")
    private String principalName;
    @TableField("client_registration_id")
    private String clientRegistrationId;
}
