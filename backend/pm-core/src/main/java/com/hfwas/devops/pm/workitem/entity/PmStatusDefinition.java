package com.hfwas.devops.pm.workitem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

@Data
@TableName(value = "pm_status_definition", autoResultMap = true)
public class PmStatusDefinition {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private String typeCode;
    private String statusCode;
    private String statusName;
    private Integer sortOrder;
    private Integer isInitial;
    private Integer isFinal;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private java.util.List<String> transitions;
}
