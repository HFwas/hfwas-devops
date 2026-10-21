package com.hfwas.devops.pm.meta;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("pm_work_item_type")
public class PmWorkItemType {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String code;
    private String name;
    private String icon;
    private String color;
    private Integer sortOrder;
    private Integer enabled;
    private Integer builtIn;
    private String description;
    private String templateCode;

    @TableField(exist = false)
    private Long workItemCount;
    @TableField(exist = false)
    private Long fieldCount;
}
