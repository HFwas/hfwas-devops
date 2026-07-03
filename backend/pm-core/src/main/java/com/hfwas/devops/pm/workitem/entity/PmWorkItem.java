package com.hfwas.devops.pm.workitem.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "pm_work_item", autoResultMap = true)
public class PmWorkItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Integer itemNo;
    private String typeCode;
    private String title;
    private String description;
    private String status;
    private String priority;
    private Long assigneeId;
    private Long reporterId;
    private Long parentId;
    private Long moduleId;
    private Long sprintId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> customFields;
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;

    @TableField(exist = false)
    private String itemKey;
}
