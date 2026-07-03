package com.hfwas.devops.pm.workitem.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("pm_project_sequence")
public class PmProjectSequence {
    @TableId
    private Long projectId;
    private Integer nextItemNo;
}
