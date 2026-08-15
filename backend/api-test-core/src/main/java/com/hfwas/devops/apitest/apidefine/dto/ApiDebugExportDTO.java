package com.hfwas.devops.apitest.apidefine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 调试导出请求 DTO
 *
 * @author hfwas
 */
@Data
@Schema(description = "调试导出请求")
public class ApiDebugExportDTO {

    @Schema(description = "调试历史ID列表（为空时导出全部）")
    private java.util.List<Long> ids;

    @Schema(description = "导出格式 JSON / MARKDOWN / HAR")
    private String format = "JSON";
}