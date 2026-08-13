package com.hfwas.devops.apitest.apidefine.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 接口响应定义视图
 *
 * @author hfwas
 */
@Data
@Schema(description = "接口响应定义视图")
public class ApiDefinitionResponseVO {

    @Schema(description = "响应ID")
    private Long id;

    @Schema(description = "所属接口定义ID")
    private Long definitionId;

    @Schema(description = "响应状态码")
    private Integer statusCode;

    @Schema(description = "响应Content-Type")
    private String contentType;

    @Schema(description = "响应描述")
    private String description;

    @Schema(description = "响应体JSON Schema定义")
    private Object bodySchema;

    @Schema(description = "响应体示例值")
    private Object bodyExample;

    @Schema(description = "创建时间")
    private String createTime;
}