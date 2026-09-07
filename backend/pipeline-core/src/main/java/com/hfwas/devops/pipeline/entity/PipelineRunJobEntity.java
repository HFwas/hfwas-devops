package com.hfwas.devops.pipeline.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pipeline_run_job")
public class PipelineRunJobEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long runId;
    private Long jobId;
    private String stageName;
    private String jobName;
    private String kind;
    private String command;
    private String status;
    private String logText;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
