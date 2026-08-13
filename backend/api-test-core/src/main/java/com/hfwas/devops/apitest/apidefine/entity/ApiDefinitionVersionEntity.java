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
 * 接口版本记录实体
 *
 * @author hfwas
 */
@Data
@TableName(value = "api_definition_version", autoResultMap = true)
public class ApiDefinitionVersionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属接口定义ID */
    private Long definitionId;

    /** 版本号 */
    private String version;

    /** 变更说明 */
    private String changeLog;

    /** 快照-接口名称 */
    private String snapshotName;

    /** 快照-请求路径 */
    private String snapshotPath;

    /** 快照-请求方式 */
    private String snapshotMethod;

    /** 快照-参数列表 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object snapshotParams;

    /** 快照-响应定义列表 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object snapshotResponses;

    /** 快照-接口描述 */
    private String snapshotDescription;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}