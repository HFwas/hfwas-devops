package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/4/12
 */
@Data
@TableName(value = "users")
public class User {
    @TableId
    private Long id;

    private String uuid;

    private String name;

    private String username;

    private String password;

    private String email;

    private String roles;
}
