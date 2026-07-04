package com.hfwas.devops.pm.workitem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("pm_status_definition")
public class PmStatusDefinition {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private String typeCode;
    private String statusCode;
    private String statusName;
    private Integer sortOrder;
    private Integer isInitial;
    private Integer isFinal;
    /** JSON array of target status codes */
    private String transitions;
}
