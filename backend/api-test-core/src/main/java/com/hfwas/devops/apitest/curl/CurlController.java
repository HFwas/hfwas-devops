package com.hfwas.devops.apitest.curl;

import com.hfwas.devops.apitest.curl.dto.CurlParseDTO;
import com.hfwas.devops.apitest.curl.dto.CurlParseResultVO;
import com.hfwas.devops.common.core.base.BaseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * cURL 导入控制器
 * <p>
 * 提供 cURL 命令解析功能，支持将 cURL 命令转换为平台内部请求格式。
 *
 * @author hfwas
 */
@Slf4j
@Tag(name = "cURL 导入")
@RestController
@RequestMapping("/apitest/curl")
@RequiredArgsConstructor
public class CurlController {

    private final CurlParserService curlParserService;

    /**
     * 解析 cURL 命令
     *
     * @param dto 包含原始 cURL 命令
     * @return 解析后的请求参数
     */
    @Operation(summary = "解析 cURL 命令")
    @PostMapping("/parse")
    public BaseResult<CurlParseResultVO> parse(@RequestBody CurlParseDTO dto) {
        log.info("解析 cURL 命令: length={}", dto.getCurl() != null ? dto.getCurl().length() : 0);
        CurlParseResultVO result = curlParserService.parse(dto.getCurl());
        return BaseResult.ok(result);
    }
}