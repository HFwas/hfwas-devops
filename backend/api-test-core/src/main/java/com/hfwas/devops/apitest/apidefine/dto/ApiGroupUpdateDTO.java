package com.hfwas.devops.apitest.apidefine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新分组请求
 *
 * @author hfwas
 */
@Data
@Schema(description = "更新分组请求")
public class ApiGroupUpdateDTO {

    @NotBlank(message = "分组名称不能为空")
    @Schema(description = "分组名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "分组描述")
    private String description;
}