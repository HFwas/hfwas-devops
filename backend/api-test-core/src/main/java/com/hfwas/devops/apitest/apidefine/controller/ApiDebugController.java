package com.hfwas.devops.apitest.apidefine.controller;

import com.hfwas.devops.apitest.apidefine.dto.ApiDebugExecuteDTO;
import com.hfwas.devops.apitest.apidefine.service.ApiDebugService;
import com.hfwas.devops.apitest.apidefine.vo.ApiDebugResultVO;
import com.hfwas.devops.common.core.base.BaseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接口调试控制器
 *
 * @author hfwas
 */
@Slf4j
@Tag(name = "接口调试")
@RestController
@RequestMapping("/apitest/debug")
@RequiredArgsConstructor
public class ApiDebugController {

    private final ApiDebugService apiDebugService;

    /**
     * 执行调试
     * <p>
     * 完整流程：
     * 1. 获取环境变量
     * 2. 渲染请求参数中的 {{varName}} 占位符
     * 3. 执行前置脚本
     * 4. 发送 HTTP 请求
     * 5. 执行后置脚本
     * 6. 执行断言
     * 7. 提取变量
     * 8. 保存调试历史
     */
    @Operation(summary = "执行接口调试")
    @PostMapping("/execute")
    public BaseResult<ApiDebugResultVO> execute(@Valid @RequestBody ApiDebugExecuteDTO dto,
                                                @RequestParam(required = false) Long userId) {
        log.info("执行调试: url={}, method={}, envId={}, userId={}",
                dto.getUrl(), dto.getMethod(), dto.getEnvironmentId(), userId);
        ApiDebugResultVO result = apiDebugService.execute(dto, userId);
        return BaseResult.ok(result);
    }
}