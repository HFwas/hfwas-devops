package com.hfwas.devops.pm.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.WorkItemLinkDto;
import com.hfwas.devops.pm.api.dto.WorkItemTransitionDto;
import com.hfwas.devops.pm.query.model.QuerySpec;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.entity.PmWorkItemLink;
import com.hfwas.devops.pm.workitem.service.WorkItemService;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pm/work-items")
@RequiredArgsConstructor
public class PmWorkItemController {

    private final WorkItemService workItemService;

    @PostMapping("/page")
    public BaseResult<IPage<PmWorkItem>> page(@RequestBody QuerySpec spec) {
        return BaseResult.ok(workItemService.page(spec));
    }

    @OperLog(module = "pm", action = "save", bizType = "work_item", summary = "保存工作项", bizId = "#result.data")
    @PostMapping("/save")
    public BaseResult<Long> save(@RequestBody PmWorkItem item) {
        return BaseResult.ok(workItemService.save(item));
    }

    @GetMapping("/{id}")
    public BaseResult<PmWorkItem> getById(@PathVariable Long id) {
        return BaseResult.ok(workItemService.getById(id));
    }

    @OperLog(module = "pm", action = "delete", bizType = "work_item", summary = "删除工作项", bizId = "#id")
    @PostMapping("/delete")
    public BaseResult<Void> delete(@RequestParam("id") Long id) {
        workItemService.delete(id);
        return BaseResult.ok();
    }

    @OperLog(module = "pm", action = "transition", bizType = "work_item", summary = "工作项状态流转", bizId = "#id")
    @PostMapping("/{id}/transition")
    public BaseResult<Void> transition(@PathVariable Long id, @RequestBody WorkItemTransitionDto dto) {
        workItemService.transition(id, dto.getTransitionId(), dto.getFields());
        return BaseResult.ok();
    }

    @OperLog(module = "pm", action = "save", bizType = "work_item_link", summary = "创建工作项关联", bizId = "#result.data")
    @PostMapping("/links/save")
    public BaseResult<Long> addLink(@RequestBody WorkItemLinkDto dto) {
        return BaseResult.ok(workItemService.addLink(dto.getSourceId(), dto.getTargetId(), dto.getLinkType()));
    }

    @GetMapping("/{id}/links")
    public BaseResult<List<PmWorkItemLink>> listLinks(@PathVariable Long id) {
        return BaseResult.ok(workItemService.listLinks(id));
    }
}
