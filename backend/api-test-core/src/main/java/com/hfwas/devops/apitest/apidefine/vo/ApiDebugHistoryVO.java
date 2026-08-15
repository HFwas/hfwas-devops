package com.hfwas.devops.apitest.apidefine.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 调试历史 VO
 *
 * @author hfwas
 */
@Data
@Schema(description = "调试历史VO")
public class ApiDebugHistoryVO {

    @Schema(description = "历史ID")
    private Long id;

    @Schema(description = "接口定义ID")
    private Long definitionId;

    @Schema(description = "环境ID")
    private Long environmentId;

    @Schema(description = "调试名称")
    private String name;

    @Schema(description = "请求URL")
    private String requestUrl;

    @Schema(description = "请求方式")
    private String requestMethod;

    @Schema(description = "响应状态码")
    private Integer responseStatusCode;

    @Schema(description = "响应体大小（字节）")
    private Long responseSize;

    @Schema(description = "请求耗时（毫秒）")
    private Long durationMs;

    @Schema(description = "调试状态 SUCCESS / FAILURE / ERROR")
    private String status;

    @Schema(description = "断言是否全部通过")
    private Boolean allAssertionsPassed;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}