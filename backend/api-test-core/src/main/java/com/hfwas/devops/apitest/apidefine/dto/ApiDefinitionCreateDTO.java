package com.hfwas.devops.apitest.apidefine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建接口定义请求
 *
 * @author hfwas
 */
@Data
@Schema(description = "创建接口定义请求")
public class ApiDefinitionCreateDTO {

    @NotNull(message = "项目ID不能为空")
    @Schema(description = "所属项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    @Schema(description = "所属分组ID")
    private Long groupId;

    @NotBlank(message = "接口名称不能为空")
    @Schema(description = "接口名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "请求路径不能为空")
    @Schema(description = "请求路径，如 /api/users/{id}", requiredMode = Schema.RequiredMode.REQUIRED)
    private String path;

    @NotBlank(message = "请求方式不能为空")
    @Schema(description = "请求方式 GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS", requiredMode = Schema.RequiredMode.REQUIRED)
    private String method;

    @Schema(description = "标签列表")
    private List<String> tags;

    @Schema(description = "接口描述")
    private String description;

    @Schema(description = "协议 HTTP/HTTPS")
    private String protocol;

    @Schema(description = "主机地址")
    private String host;

    @Schema(description = "请求Content-Type")
    private String contentType;

    @Schema(description = "请求参数列表")
    private List<ApiDefinitionParamDTO> params;

    @Schema(description = "响应定义列表")
    private List<ApiDefinitionResponseDTO> responses;
}