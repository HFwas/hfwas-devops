package com.hfwas.devops.pm.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.workitem.model.WorkItemActivityVo;
import com.hfwas.devops.pm.workitem.service.WorkItemActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pm/work-items")
@RequiredArgsConstructor
public class PmWorkItemActivityController {

    private final WorkItemActivityService activityService;

    @GetMapping("/{id}/activities")
    public BaseResult<List<WorkItemActivityVo>> list(@PathVariable Long id) {
        return BaseResult.ok(activityService.listByWorkItem(id));
    }

    @GetMapping("/{id}/activities/page")
    public BaseResult<IPage<WorkItemActivityVo>> page(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "1") int pageNo,
                                                      @RequestParam(defaultValue = "10") int pageSize) {
        return BaseResult.ok(activityService.pageByWorkItem(id, pageNo, pageSize));
    }
}
