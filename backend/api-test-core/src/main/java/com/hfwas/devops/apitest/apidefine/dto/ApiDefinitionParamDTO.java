package com.hfwas.devops.apitest.apidefine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 接口参数 DTO
 *
 * @author hfwas
 */
@Data
@Schema(description = "接口参数")
public class ApiDefinitionParamDTO {

    @Schema(description = "参数ID（新增时为空，更新时传入）")
    private Long id;

    @Schema(description = "参数类型 path/query/header/body", requiredMode = Schema.RequiredMode.REQUIRED)
    private String paramType;

    @Schema(description = "参数名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "数据类型 string/integer/number/boolean/array/object/file")
    private String dataType;

    @Schema(description = "是否必填")
    private Boolean required;

    @Schema(description = "默认值")
    private String defaultValue;

    @Schema(description = "参数描述")
    private String description;

    @Schema(description = "父参数ID（嵌套结构时使用）")
    private Long parentId;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "示例值")
    private String example;
}