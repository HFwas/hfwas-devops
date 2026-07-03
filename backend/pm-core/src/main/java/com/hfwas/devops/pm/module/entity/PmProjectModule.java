package com.hfwas.devops.pm.module.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName("pm_project_module")
public class PmProjectModule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long parentId;
    private String name;
    private String description;
    private Integer sortOrder;
    private Integer enabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;

    @TableField(exist = false)
    private List<PmProjectModule> children = new ArrayList<>();

    @TableField(exist = false)
    private String pathLabel;
}
