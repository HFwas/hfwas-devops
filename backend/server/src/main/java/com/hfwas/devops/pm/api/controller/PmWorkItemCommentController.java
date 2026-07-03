package com.hfwas.devops.pm.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.WorkItemCommentSaveDto;
import com.hfwas.devops.pm.workitem.model.WorkItemCommentVo;
import com.hfwas.devops.pm.workitem.service.WorkItemCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pm/work-items")
@RequiredArgsConstructor
public class PmWorkItemCommentController {

    private final WorkItemCommentService commentService;

    @GetMapping("/{id}/comments")
    public BaseResult<List<WorkItemCommentVo>> list(@PathVariable Long id) {
        return BaseResult.ok(commentService.listByWorkItem(id));
    }

    @GetMapping("/{id}/comments/count")
    public BaseResult<Long> count(@PathVariable Long id) {
        return BaseResult.ok(commentService.countByWorkItem(id));
    }

    @PostMapping("/comments/counts")
    public BaseResult<Map<String, Long>> counts(@RequestBody List<Long> workItemIds) {
        return BaseResult.ok(commentService.countByWorkItems(workItemIds));
    }

    @PostMapping("/comments/save")
    public BaseResult<Long> save(@RequestBody WorkItemCommentSaveDto dto) {
        return BaseResult.ok(commentService.save(
                dto.getWorkItemId(),
                dto.getContent(),
                dto.getParentId()
        ));
    }

    @PostMapping("/comments/delete")
    public BaseResult<Void> delete(@RequestParam("id") Long id) {
        commentService.delete(id);
        return BaseResult.ok();
    }
}
