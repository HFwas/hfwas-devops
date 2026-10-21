package com.hfwas.devops.pm.workitem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "pm_batch_action_config", autoResultMap = true)
public class PmBatchActionConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String actionCode;
    private Integer enabled;
    private Integer maxItems;
    private Integer requireConfirm;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> allowedRoles;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> fieldWhitelist;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
