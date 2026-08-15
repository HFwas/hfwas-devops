package com.hfwas.devops.apitest.environment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 环境更新 DTO
 *
 * @author hfwas
 */
@Data
@Schema(description = "环境更新请求")
public class EnvironmentUpdateDTO {

    @Schema(description = "环境名称")
    private String name;

    @Schema(description = "环境描述")
    private String description;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "变量列表")
    private java.util.List<EnvironmentVariableDTO> variables;
}