package com.hfwas.devops.pipeline.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pipeline_task_kind")
public class PipelineTaskKindEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private String kindValue;
    private String label;
    private String taskGroup;

    private String description;
    private String hint;
    private String defaultCommand;

    private Integer requiresCommand;
    private Integer enabled;
    private Integer sortOrder;

    private String toolImage;
    private String defaultImage;
    private String commandTemplate;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}