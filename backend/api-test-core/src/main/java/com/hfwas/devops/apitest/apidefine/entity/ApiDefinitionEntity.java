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
import java.util.List;

/**
 * 接口定义主表实体
 *
 * @author hfwas
 */
@Data
@TableName(value = "api_definition", autoResultMap = true)
public class ApiDefinitionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属项目ID */
    private Long projectId;

    /** 所属分组ID */
    private Long groupId;

    /** 接口名称 */
    private String name;

    /** 请求路径（含路径参数占位符，如 /api/users/{id}） */
    private String path;

    /** 请求方式 GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS */
    private String method;

    /** 状态 DRAFT/PUBLISHED/DEPRECATED */
    private String status;

    /** 当前版本号 */
    private String version;

    /** 标签列表 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /** 接口描述 */
    private String description;

    /** 协议 HTTP/HTTPS */
    private String protocol;

    /** 主机地址（可选，用于调试） */
    private String host;

    /** 请求Content-Type */
    private String contentType;

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