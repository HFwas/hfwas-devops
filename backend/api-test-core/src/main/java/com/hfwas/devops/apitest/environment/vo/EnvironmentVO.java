package com.hfwas.devops.apitest.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 环境 VO
 *
 * @author hfwas
 */
@Data
@Schema(description = "环境VO")
public class EnvironmentVO {

    @Schema(description = "环境ID")
    private Long id;

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "环境名称")
    private String name;

    @Schema(description = "环境描述")
    private String description;

    @Schema(description = "变量数量")
    private Integer variableCount;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}