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
@TableName("pipeline_task_kind_param")
public class PipelineTaskKindParamEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String kindValue;
    private String paramKey;
    private String paramLabel;
    private String paramType;
    private String defaultValue;
    private Integer required;
    private Integer sortOrder;
    private String optionsJson;
    private String apiUrl;
    private String apiMethod;
    private String apiHeadersJson;
    private String apiResponsePath;
    private String placeholder;

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
