package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/1/21
 */
@Data
@TableName(value = "devops_sync_log")
public class DevopsSyncLog {
    private Long id;
    private String name;
    private Integer type;
    private Integer status;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Integer successNum;
    private String successData;
    private Integer errorNum;
    private String errorData;
}
