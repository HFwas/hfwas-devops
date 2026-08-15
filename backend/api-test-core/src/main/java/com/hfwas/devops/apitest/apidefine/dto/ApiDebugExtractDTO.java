package com.hfwas.devops.apitest.apidefine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 调试变量提取 DTO
 *
 * @author hfwas
 */
@Data
@Schema(description = "调试变量提取")
public class ApiDebugExtractDTO {

    @Schema(description = "提取的变量名")
    private String variableName;

    @Schema(description = "提取表达式（JSONPath 或 Header 名称）")
    private String expression;

    @Schema(description = "提取来源 RESPONSE_BODY / RESPONSE_HEADERS / RESPONSE_STATUS")
    private String source;
}