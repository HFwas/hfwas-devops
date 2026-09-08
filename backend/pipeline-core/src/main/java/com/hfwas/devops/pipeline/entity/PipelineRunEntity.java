package com.hfwas.devops.pipeline.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pipeline_run")
public class PipelineRunEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long pipelineId;
    private Long tenantId;
    private String status;
    private String trigger;
    private String gitRef;
    private String commitSha;
    private String triggeredByName;
    private String stack;
    private String runtimeVersion;
    private String toolVersion;
    private String image;
    private String tektonName;
    private Integer segmentIndex;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
