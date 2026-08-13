package com.hfwas.devops.apitest.apidefine.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口分组实体
 *
 * @author hfwas
 */
@Data
@TableName("api_group")
public class ApiGroupEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属项目ID */
    private Long projectId;

    /** 父分组ID，null为根级 */
    private Long parentId;

    /** 分组名称 */
    private String name;

    /** 排序序号 */
    private Integer sortOrder;

    /** 分组描述 */
    private String description;

    /** 逻辑删除 0-未删 1-已删 */
    @TableLogic
    private Integer deleted;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}