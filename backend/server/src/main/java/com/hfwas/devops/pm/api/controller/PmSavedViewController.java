package com.hfwas.devops.pm.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.SavedViewListDto;
import com.hfwas.devops.pm.view.entity.PmSavedView;
import com.hfwas.devops.pm.view.service.SavedViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pm/views")
@RequiredArgsConstructor
public class PmSavedViewController {

    private final SavedViewService savedViewService;

    @PostMapping("/save")
    public BaseResult<Long> save(@RequestBody PmSavedView view) {
        return BaseResult.ok(savedViewService.save(view));
    }

    @PostMapping("/list")
    public BaseResult<List<PmSavedView>> list(@RequestBody SavedViewListDto dto) {
        return BaseResult.ok(savedViewService.list(dto.getProjectId(), dto.getTypeCode()));
    }

    @PostMapping("/delete")
    public BaseResult<Void> delete(@RequestParam Long id) {
        savedViewService.delete(id);
        return BaseResult.ok();
    }
}
