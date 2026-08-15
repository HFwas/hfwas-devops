package com.hfwas.devops.apitest.environment.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 环境变量实体
 *
 * @author hfwas
 */
@Data
@TableName(value = "api_environment_variable", autoResultMap = true)
public class EnvironmentVariableEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属环境ID */
    private Long environmentId;

    /** 变量名 */
    private String name;

    /** 变量值 */
    private String value;

    /** 变量描述 */
    private String description;

    /** 是否敏感变量 0-否 1-是 */
    private Integer isSecret;

    /** 排序序号 */
    private Integer sortOrder;

    /** 逻辑删除 */
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