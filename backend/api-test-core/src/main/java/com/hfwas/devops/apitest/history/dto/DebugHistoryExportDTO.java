package com.hfwas.devops.apitest.history.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 调试历史导出 DTO
 *
 * @author hfwas
 */
@Data
@Schema(description = "调试历史导出")
public class DebugHistoryExportDTO {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "接口定义ID（可选）")
    private Long definitionId;

    @Schema(description = "导出格式 JSON / MARKDOWN / HAR")
    private String format = "JSON";
}