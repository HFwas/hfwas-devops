package com.hfwas.devops.apitest.apidefine.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口响应定义实体
 *
 * @author hfwas
 */
@Data
@TableName(value = "api_definition_response", autoResultMap = true)
public class ApiDefinitionResponseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属接口定义ID */
    private Long definitionId;

    /** 响应状态码 */
    private Integer statusCode;

    /** 响应Content-Type */
    private String contentType;

    /** 响应描述 */
    private String description;

    /** 响应体JSON Schema定义 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object bodySchema;

    /** 响应体示例值 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object bodyExample;

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