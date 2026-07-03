package com.hfwas.devops.pm.field.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("pm_field_option")
public class FieldOption {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long fieldId;
    private String optionKey;
    private String optionLabel;
    private Integer sortOrder;
}
