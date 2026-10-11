package com.hfwas.devops.pipeline.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pipeline_task_kind_version")
public class PipelineTaskKindVersionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String kindValue;
    private String version;
    private String note;
    private String snapshotJson;
    private LocalDateTime createTime;
}
