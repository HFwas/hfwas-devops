package com.hfwas.devops.pm.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.BoardQueryDto;
import com.hfwas.devops.pm.meta.PmMetaService;
import com.hfwas.devops.pm.meta.PmWorkItemType;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.service.WorkItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pm")
@RequiredArgsConstructor
public class PmMetaController {

    private final PmMetaService metaService;
    private final WorkItemService workItemService;

    @PostMapping("/meta/types")
    public BaseResult<List<PmWorkItemType>> types() {
        return BaseResult.ok(metaService.listTypes());
    }

    @PostMapping("/board")
    public BaseResult<Map<String, List<PmWorkItem>>> board(@RequestBody BoardQueryDto dto) {
        Map<String, List<PmWorkItem>> board = new LinkedHashMap<>();
        board.put("open", workItemService.listByStatus(dto.getProjectId(), dto.getTypeCode(), "open"));
        board.put("in_progress", workItemService.listByStatus(dto.getProjectId(), dto.getTypeCode(), "in_progress"));
        board.put("done", workItemService.listByStatus(dto.getProjectId(), dto.getTypeCode(), "done"));
        board.put("closed", workItemService.listByStatus(dto.getProjectId(), dto.getTypeCode(), "closed"));
        return BaseResult.ok(board);
    }
}
