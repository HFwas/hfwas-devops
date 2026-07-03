package com.hfwas.devops.pm.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.SavedViewListDto;
import com.hfwas.devops.pm.view.entity.PmSavedView;
import com.hfwas.devops.pm.view.service.SavedViewService;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pm/views")
@RequiredArgsConstructor
public class PmSavedViewController {

    private final SavedViewService savedViewService;

    @OperLog(module = "pm", action = "save", bizType = "saved_view", summary = "保存视图", bizId = "#result.data")
    @PostMapping("/save")
    public BaseResult<Long> save(@RequestBody PmSavedView view) {
        return BaseResult.ok(savedViewService.save(view));
    }

    @PostMapping("/list")
    public BaseResult<List<PmSavedView>> list(@RequestBody SavedViewListDto dto) {
        return BaseResult.ok(savedViewService.list(dto.getProjectId(), dto.getTypeCode()));
    }

    @OperLog(module = "pm", action = "delete", bizType = "saved_view", summary = "删除视图", bizId = "#id")
    @PostMapping("/delete")
    public BaseResult<Void> delete(@RequestParam("id") Long id) {
        savedViewService.delete(id);
        return BaseResult.ok();
    }
}
