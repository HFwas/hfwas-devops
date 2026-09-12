package com.hfwas.devops.pipeline.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pipeline.dto.DependencyComponentAggregateVO;
import com.hfwas.devops.pipeline.dto.DependencyComponentVO;
import com.hfwas.devops.pipeline.service.DependencyComponentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dependency/components")
public class DependencyComponentController {

    private final DependencyComponentService componentService;

    public DependencyComponentController(DependencyComponentService componentService) {
        this.componentService = componentService;
    }

    @GetMapping("/page")
    public BaseResult<IPage<DependencyComponentVO>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String language
    ) {
        return BaseResult.ok(componentService.pageComponents(pageNo, pageSize, search, language));
    }

    @GetMapping("/aggregated")
    public BaseResult<IPage<DependencyComponentAggregateVO>> aggregated(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String language
    ) {
        return BaseResult.ok(componentService.pageAggregated(pageNo, pageSize, search, language));
    }
}