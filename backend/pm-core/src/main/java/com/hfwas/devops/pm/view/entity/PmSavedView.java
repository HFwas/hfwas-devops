package com.hfwas.devops.pm.view.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hfwas.devops.pm.query.model.QuerySpec;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "pm_saved_view", autoResultMap = true)
public class PmSavedView {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Long userId;
    private String name;
    private String typeCode;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private QuerySpec querySpec;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> columns;
    private Integer isDefault;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
