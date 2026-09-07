package com.hfwas.devops.pipeline.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pipeline.dto.ToolchainOptionVO;
import com.hfwas.devops.pipeline.toolchain.ToolchainCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pipeline/toolchains")
public class PipelineToolchainController {

    private final ToolchainCatalog catalog = new ToolchainCatalog();

    @GetMapping
    public BaseResult<List<ToolchainOptionVO>> list() {
        List<ToolchainOptionVO> rows = catalog.list().stream().map(item -> {
            ToolchainOptionVO vo = new ToolchainOptionVO();
            vo.setStack(item.stack().name());
            vo.setRuntimeVersion(item.runtimeVersion());
            vo.setToolVersion(item.toolVersion());
            vo.setImage(item.image());
            vo.setBuildCommand(item.buildCommand());
            vo.setTestCommand(item.testCommand());
            return vo;
        }).toList();
        return BaseResult.ok(rows);
    }
}
