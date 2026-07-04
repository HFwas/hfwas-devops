package com.hfwas.devops.pm.workitem.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pm_work_item_activity")
public class PmWorkItemActivity {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workItemId;
    private String batchId;
    /** CREATE | FIELD_CHANGE | LINK_ADD */
    private String eventType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long actorId;
    private String actorName;
    private String fieldKey;
    private String fieldName;
    private String fieldType;
    private String oldValue;
    private String newValue;
    private String oldLabel;
    private String newLabel;
    private String extraJson;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
