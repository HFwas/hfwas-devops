package com.hfwas.devops.user.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.user.model.*;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import com.hfwas.devops.user.service.TenantMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/tenants/{tenantId}/members")
@RequiredArgsConstructor
public class TenantMemberController {

    private final TenantMemberService tenantMemberService;

    @PostMapping("/page")
    public BaseResult<IPage<TenantMemberVO>> page(@PathVariable Long tenantId,
                                                   @RequestBody TenantMemberPageRequest request) {
        return BaseResult.ok(tenantMemberService.page(tenantId, request));
    }

    @GetMapping("/available")
    public BaseResult<List<TenantMemberService.UserProfileLite>> available(@PathVariable Long tenantId,
                                                                           @RequestParam(required = false) String keyword) {
        return BaseResult.ok(tenantMemberService.listAvailable(tenantId, keyword));
    }

    @OperLog(module = "user", action = "save", bizType = "tenant_member", summary = "添加租户成员")
    @PostMapping("/add")
    public BaseResult<Void> add(@PathVariable Long tenantId, @RequestBody TenantMemberAddRequest request) {
        tenantMemberService.addMembers(tenantId, request);
        return BaseResult.ok();
    }

    @OperLog(module = "user", action = "save", bizType = "tenant_member", summary = "更新租户成员")
    @PostMapping("/save")
    public BaseResult<Void> save(@PathVariable Long tenantId, @RequestBody TenantMemberSaveRequest request) {
        tenantMemberService.saveMember(tenantId, request);
        return BaseResult.ok();
    }

    @OperLog(module = "user", action = "delete", bizType = "tenant_member", summary = "移除租户成员", bizId = "#userId")
    @PostMapping("/remove")
    public BaseResult<Void> remove(@PathVariable Long tenantId, @RequestParam Long userId) {
        tenantMemberService.removeMember(tenantId, userId);
        return BaseResult.ok();
    }
}
