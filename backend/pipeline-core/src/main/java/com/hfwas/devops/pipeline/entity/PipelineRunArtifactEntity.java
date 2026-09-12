package com.hfwas.devops.pipeline.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pipeline_run_artifact")
public class PipelineRunArtifactEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long runId;
    private Long jobId;
    private String artifactType;
    private String fileName;
    private Long fileSize;
    private String storagePath;
    private String contentType;
    private LocalDateTime createTime;
}