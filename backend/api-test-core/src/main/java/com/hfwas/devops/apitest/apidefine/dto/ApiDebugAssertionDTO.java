package com.hfwas.devops.apitest.apidefine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 调试断言 DTO
 *
 * @author hfwas
 */
@Data
@Schema(description = "调试断言")
public class ApiDebugAssertionDTO {

    @Schema(description = "断言来源 RESPONSE_STATUS / RESPONSE_HEADERS / RESPONSE_BODY / RESPONSE_TIME")
    private String source;

    @Schema(description = "比较方式 EQUALS / NOT_EQUALS / CONTAINS / NOT_CONTAINS / REGEX / GT / GTE / LT / LTE")
    private String compareType;

    @Schema(description = "表达式（JSONPath 或 Header 名称）")
    private String expression;

    @Schema(description = "期望值")
    private String expectedValue;

    @Schema(description = "断言名称")
    private String name;
}