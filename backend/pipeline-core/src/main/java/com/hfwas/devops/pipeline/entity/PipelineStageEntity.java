package com.hfwas.devops.pipeline.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("pipeline_stage")
public class PipelineStageEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long pipelineId;
    private String name;
    private Integer sortOrder;
}
