package com.hfwas.devops.pm.field.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hfwas.devops.pm.field.model.TypeFieldLayoutConfig;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "pm_type_field_layout", autoResultMap = true)
public class PmTypeFieldLayout {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private String typeCode;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private TypeFieldLayoutConfig layoutConfig;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
