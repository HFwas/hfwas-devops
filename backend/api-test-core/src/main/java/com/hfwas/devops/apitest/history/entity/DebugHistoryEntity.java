package com.hfwas.devops.apitest.history.entity;

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
import java.util.Map;

/**
 * 调试历史记录实体
 *
 * @author hfwas
 */
@Data
@TableName(value = "api_debug_history", autoResultMap = true)
public class DebugHistoryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属项目ID */
    private Long projectId;

    /** 关联接口定义ID */
    private Long definitionId;

    /** 使用的环境ID */
    private Long environmentId;

    /** 调试名称 */
    private String name;

    /** 完整请求URL（变量已渲染） */
    private String requestUrl;

    /** 请求方式 */
    private String requestMethod;

    /** 请求头（已渲染） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> requestHeaders;

    /** 请求Query参数（已渲染） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> requestQuery;

    /** 请求体（已渲染） */
    private String requestBody;

    /** 请求Content-Type */
    private String requestContentType;

    /** 响应状态码 */
    private Integer responseStatusCode;

    /** 响应头 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> responseHeaders;

    /** 响应体 */
    private String responseBody;

    /** 响应Content-Type */
    private String responseContentType;

    /** 响应体大小（字节） */
    private Long responseSize;

    /** 请求耗时（毫秒） */
    private Long durationMs;

    /** 调试状态 SUCCESS / FAILURE / ERROR */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 断言结果列表 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> assertionResults;

    /** 断言是否全部通过 */
    private Integer allAssertionsPassed;

    /** 提取的变量快照 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> extractedVariables;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    /** 创建人ID */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}