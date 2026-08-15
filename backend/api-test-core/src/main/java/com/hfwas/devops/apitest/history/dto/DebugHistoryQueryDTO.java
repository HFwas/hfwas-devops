package com.hfwas.devops.apitest.history.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 调试历史查询 DTO
 *
 * @author hfwas
 */
@Data
@Schema(description = "调试历史查询")
public class DebugHistoryQueryDTO {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "接口定义ID")
    private Long definitionId;

    @Schema(description = "调试状态 SUCCESS / FAILURE / ERROR")
    private String status;

    @Schema(description = "关键词搜索")
    private String keyword;

    @Schema(description = "开始时间")
    private String startTime;

    @Schema(description = "结束时间")
    private String endTime;

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