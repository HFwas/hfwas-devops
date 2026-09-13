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
@TableName("pipeline_job_param")
public class PipelineJobParamEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long pipelineId;
    private Long jobId;
    private String paramKey;
    private String paramLabel;
    private String paramType;
    /** fixed = 写死注入；runtime = 运行时弹框选择 */
    private String valueMode;
    private String defaultValue;
    private Integer required;
    private Integer sortOrder;

    // param_type = 'select'
    private String optionsJson;

    // param_type = 'api_select'
    private String apiUrl;
    private String apiMethod;
    private String apiHeadersJson;
    private String apiResponsePath;

    // param_type = 'input'
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