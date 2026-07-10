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
    /** 可视化设计器节点 X 坐标 */
    private Double layoutX;
    /** 可视化设计器节点 Y 坐标 */
    private Double layoutY;
    /** JSON array of TransitionVO */
    private String transitions;
}
