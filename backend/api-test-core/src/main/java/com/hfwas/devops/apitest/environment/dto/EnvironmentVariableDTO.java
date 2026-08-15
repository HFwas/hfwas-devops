package com.hfwas.devops.apitest.environment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 环境变量 DTO
 *
 * @author hfwas
 */
@Data
@Schema(description = "环境变量")
public class EnvironmentVariableDTO {

    @Schema(description = "变量ID（新增时不填，更新时填）")
    private Long id;

    @NotBlank(message = "变量名不能为空")
    @Schema(description = "变量名")
    private String name;

    @Schema(description = "变量值")
    private String value;

    @Schema(description = "变量描述")
    private String description;

    @Schema(description = "是否敏感变量")
    private Boolean isSecret;

    @Schema(description = "排序序号")
    private Integer sortOrder;
}