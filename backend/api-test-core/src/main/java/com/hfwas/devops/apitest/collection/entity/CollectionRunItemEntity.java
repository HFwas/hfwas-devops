package com.hfwas.devops.apitest.collection.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 集合执行项结果实体
 *
 * @author hfwas
 */
@Data
@TableName(value = "api_collection_run_item", autoResultMap = true)
public class CollectionRunItemEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属执行记录ID */
    private Long runId;

    /** 集合项ID */
    private Long collectionItemId;

    /** 接口定义ID */
    private Long definitionId;

    /** 接口名称（执行时快照） */
    private String name;

    /** 请求URL（已渲染） */
    private String requestUrl;

    /** 请求方式 */
    private String requestMethod;

    /** 请求头（已渲染） */
    private String requestHeaders;

    /** 请求体（已渲染） */
    private String requestBody;

    /** 响应状态码 */
    private Integer responseStatusCode;

    /** 响应头 */
    private String responseHeaders;

    /** 响应体 */
    private String responseBody;

    /** 响应大小（字节） */
    private Long responseSize;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 状态 PENDING / SUCCESS / FAILURE / ERROR / SKIPPED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 断言结果列表 */
    private String assertionResults;

    /** 断言是否全部通过 */
    private Integer allAssertionsPassed;

    /** 提取的变量快照 */
    private String extractedVariables;

    /** 执行顺序 */
    private Integer sortOrder;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}