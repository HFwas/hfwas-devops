package com.hfwas.devops.pm.field.model;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "pm_field_definition", autoResultMap = true)
public class FieldDefinition {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;
    private String scope;
    private String fieldKey;
    private String fieldName;
    private String fieldType;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> applicableTypes;
    private Integer requiredFlag;
    private Integer sortOrder;
    private Integer systemFlag;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;

    @TableField(exist = false)
    private Boolean showInList;
    @TableField(exist = false)
    private Boolean searchable;
    @TableField(exist = false)
    private Boolean showInCreate;
    @TableField(exist = false)
    private Integer listOrder;
}
