package com.hfwas.devops.apitest.environment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 环境查询 DTO
 *
 * @author hfwas
 */
@Data
@Schema(description = "环境查询请求")
public class EnvironmentQueryDTO {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "关键词搜索（名称）")
    private String keyword;

    @Schema(description = "当前页")
    private Integer pageNo = 1;

    @Schema(description = "每页条数")
    private Integer pageSize = 20;

    public long resolvePageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public long resolvePageSize() {
        return pageSize == null || pageSize < 1 ? 20 : pageSize;
    }
}