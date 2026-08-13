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
 * 接口参数实体
 *
 * @author hfwas
 */
@Data
@TableName("api_definition_param")
public class ApiDefinitionParamEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属接口定义ID */
    private Long definitionId;

    /** 参数类型 path/query/header/body */
    private String paramType;

    /** 参数名称 */
    private String name;

    /** 数据类型 string/integer/number/boolean/array/object/file */
    private String dataType;

    /** 是否必填 0-可选 1-必填 */
    private Integer required;

    /** 默认值 */
    private String defaultValue;

    /** 参数描述 */
    private String description;

    /** 父参数ID（嵌套结构时使用） */
    private Long parentId;

    /** 排序序号 */
    private Integer sortOrder;

    /** 示例值 */
    private String example;

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