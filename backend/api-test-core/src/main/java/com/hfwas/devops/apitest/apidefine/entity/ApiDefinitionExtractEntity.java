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
 * 接口变量提取定义实体
 *
 * @author hfwas
 */
@Data
@TableName(value = "api_definition_extract", autoResultMap = true)
public class ApiDefinitionExtractEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属接口定义ID */
    private Long definitionId;

    /** 提取的变量名 */
    private String variableName;

    /** 提取表达式（JSONPath 或 Header 名称） */
    private String expression;

    /** 提取来源 RESPONSE_BODY / RESPONSE_HEADERS / RESPONSE_STATUS */
    private String source;

    /** 是否启用 0-禁用 1-启用 */
    private Integer enabled;

    /** 提取说明 */
    private String description;

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