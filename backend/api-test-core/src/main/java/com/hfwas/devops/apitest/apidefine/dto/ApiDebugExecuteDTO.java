package com.hfwas.devops.apitest.apidefine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 调试执行请求 DTO
 *
 * @author hfwas
 */
@Data
@Schema(description = "调试执行请求")
public class ApiDebugExecuteDTO {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "接口定义ID（可选，可不关联直接调试）")
    private Long definitionId;

    @Schema(description = "环境ID（可选）")
    private Long environmentId;

    @NotBlank(message = "请求URL不能为空")
    @Schema(description = "请求URL（可含 {{var}} 占位符）")
    private String url;

    @NotBlank(message = "请求方式不能为空")
    @Schema(description = "请求方式")
    private String method;

    @Schema(description = "请求头")
    private Map<String, String> headers;

    @Schema(description = "Query参数")
    private Map<String, String> queryParams;

    @Schema(description = "请求体")
    private String body;

    @Schema(description = "请求Content-Type")
    private String contentType;

    @Schema(description = "超时时间（毫秒），默认30000")
    private Long timeoutMs;

    @Schema(description = "是否跟随重定向，默认true")
    private Boolean followRedirects;

    @Schema(description = "前置脚本内容")
    private String preRequestScript;

    @Schema(description = "后置脚本内容")
    private String postResponseScript;

    @Schema(description = "断言列表")
    private List<ApiDebugAssertionDTO> assertions;

    @Schema(description = "变量提取列表")
    private List<ApiDebugExtractDTO> extracts;
}