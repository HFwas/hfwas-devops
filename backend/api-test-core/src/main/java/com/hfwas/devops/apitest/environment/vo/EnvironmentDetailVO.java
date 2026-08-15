package com.hfwas.devops.apitest.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 环境详情 VO（含变量列表）
 *
 * @author hfwas
 */
@Data
@Schema(description = "环境详情VO")
public class EnvironmentDetailVO {

    @Schema(description = "环境ID")
    private Long id;

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "环境名称")
    private String name;

    @Schema(description = "环境描述")
    private String description;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "变量列表")
    private List<EnvironmentVariableItemVO> variables;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /**
     * 环境变量项 VO
     */
    @Data
    @Schema(description = "环境变量项")
    public static class EnvironmentVariableItemVO {

        @Schema(description = "变量ID")
        private Long id;

        @Schema(description = "变量名")
        private String name;

        @Schema(description = "变量值（敏感变量时返回掩码）")
        private String value;

        @Schema(description = "变量描述")
        private String description;

        @Schema(description = "是否敏感变量")
        private Boolean isSecret;

        @Schema(description = "排序序号")
        private Integer sortOrder;
    }
}