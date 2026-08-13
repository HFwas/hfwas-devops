package com.hfwas.devops.apitest.apidefine.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 接口参数视图
 *
 * @author hfwas
 */
@Data
@Schema(description = "接口参数视图")
public class ApiDefinitionParamVO {

    @Schema(description = "参数ID")
    private Long id;

    @Schema(description = "所属接口定义ID")
    private Long definitionId;

    @Schema(description = "参数类型 path/query/header/body")
    private String paramType;

    @Schema(description = "参数名称")
    private String name;

    @Schema(description = "数据类型")
    private String dataType;

    @Schema(description = "是否必填")
    private Boolean required;

    @Schema(description = "默认值")
    private String defaultValue;

    @Schema(description = "参数描述")
    private String description;

    @Schema(description = "父参数ID")
    private Long parentId;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "示例值")
    private String example;
}