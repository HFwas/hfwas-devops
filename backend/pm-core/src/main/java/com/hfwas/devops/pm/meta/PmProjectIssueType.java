package com.hfwas.devops.pm.meta;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("pm_project_issue_type")
public class PmProjectIssueType {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String typeCode;
    private Integer sortOrder;
}
