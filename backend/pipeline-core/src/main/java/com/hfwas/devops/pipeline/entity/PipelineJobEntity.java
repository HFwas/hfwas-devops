package com.hfwas.devops.pipeline.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("pipeline_job")
public class PipelineJobEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long pipelineId;
    private Long stageId;
    private String name;
    private String kind;
    private String command;
    private String stack;
    private String runtimeVersion;
    private String toolVersion;
    private Integer sortOrder;
    private String paramBindings;
}
