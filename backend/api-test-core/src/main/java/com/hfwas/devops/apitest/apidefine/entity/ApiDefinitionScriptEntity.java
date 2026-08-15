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
 * 接口前置/后置脚本实体
 *
 * @author hfwas
 */
@Data
@TableName(value = "api_definition_script", autoResultMap = true)
public class ApiDefinitionScriptEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属接口定义ID */
    private Long definitionId;

    /** 脚本类型 PRE_REQUEST / POST_RESPONSE */
    private String scriptType;

    /** 脚本内容（JavaScript） */
    private String content;

    /** 是否启用 0-禁用 1-启用 */
    private Integer enabled;

    /** 脚本说明 */
    private String description;

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