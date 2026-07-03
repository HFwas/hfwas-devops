package com.hfwas.devops.user.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.user.model.TenantPageRequest;
import com.hfwas.devops.user.model.TenantSaveRequest;
import com.hfwas.devops.user.model.TenantVO;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import com.hfwas.devops.user.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/tenants")
@RequiredArgsConstructor
public class TenantManageController {

    private final TenantService tenantService;

    @PostMapping("/page")
    public BaseResult<IPage<TenantVO>> page(@RequestBody TenantPageRequest request) {
        return BaseResult.ok(tenantService.page(request));
    }

    @GetMapping("/options")
    public BaseResult<List<TenantVO>> options() {
        return BaseResult.ok(tenantService.options());
    }

    @GetMapping("/{id}")
    public BaseResult<TenantVO> getById(@PathVariable Long id) {
        return BaseResult.ok(tenantService.getById(id));
    }

    @OperLog(module = "user", action = "save", bizType = "tenant", summary = "保存租户", bizId = "#result.data")
    @PostMapping("/save")
    public BaseResult<Long> save(@RequestBody TenantSaveRequest request) {
        return BaseResult.ok(tenantService.save(request));
    }

    @OperLog(module = "user", action = "delete", bizType = "tenant", summary = "删除租户", bizId = "#id")
    @PostMapping("/delete")
    public BaseResult<Void> delete(@RequestParam("id") Long id) {
        tenantService.delete(id);
        return BaseResult.ok();
    }
}
