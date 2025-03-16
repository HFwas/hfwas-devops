package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/3/16
 */
@Data
@TableName("devops_tool")
public class DevopsTool {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String  protocol;
    private String  ip;
    private Integer port;
    private String  username;
    private String  password;
    private Integer create_by;
    private Integer update_by;
    private LocalDateTime create_time;
    private LocalDateTime update_time;
}
