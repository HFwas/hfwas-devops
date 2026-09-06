package com.hfwas.devops.user.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.user.model.SwitchTenantRequest;
import com.hfwas.devops.user.model.TenantOptionVO;
import com.hfwas.devops.user.model.UserProfile;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import com.hfwas.devops.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final AuthService authService;

    @GetMapping("/me")
    public BaseResult<UserProfile> me() {
        return BaseResult.ok(authService.me());
    }

    @GetMapping("/my-tenants")
    public BaseResult<List<TenantOptionVO>> myTenants() {
        return BaseResult.ok(authService.listMyTenants());
    }

    @OperLog(module = "user", action = "switch_tenant", bizType = "tenant", summary = "切换租户")
    @PostMapping("/switch-tenant")
    public BaseResult<UserProfile> switchTenant(@RequestBody SwitchTenantRequest request) {
        return BaseResult.ok(authService.switchTenant(request));
    }
}
