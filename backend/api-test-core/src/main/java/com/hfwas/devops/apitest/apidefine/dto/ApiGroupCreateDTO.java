package com.hfwas.devops.apitest.apidefine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建分组请求
 *
 * @author hfwas
 */
@Data
@Schema(description = "创建分组请求")
public class ApiGroupCreateDTO {

    @NotNull(message = "项目ID不能为空")
    @Schema(description = "所属项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    @Schema(description = "父分组ID，null为根级")
    private Long parentId;

    @NotBlank(message = "分组名称不能为空")
    @Schema(description = "分组名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "分组描述")
    private String description;
}