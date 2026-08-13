package com.hfwas.devops.apitest.apidefine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 接口响应定义 DTO
 *
 * @author hfwas
 */
@Data
@Schema(description = "接口响应定义")
public class ApiDefinitionResponseDTO {

    @Schema(description = "响应ID（新增时为空，更新时传入）")
    private Long id;

    @Schema(description = "响应状态码", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer statusCode;

    @Schema(description = "响应Content-Type")
    private String contentType;

    @Schema(description = "响应描述")
    private String description;

    @Schema(description = "响应体JSON Schema定义")
    private Object bodySchema;

    @Schema(description = "响应体示例值")
    private Object bodyExample;
}