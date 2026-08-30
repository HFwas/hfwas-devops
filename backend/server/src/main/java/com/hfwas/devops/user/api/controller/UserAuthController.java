package com.hfwas.devops.user.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.user.model.LoginRequest;
import com.hfwas.devops.user.model.LoginResponse;
import com.hfwas.devops.user.model.SwitchTenantRequest;
import com.hfwas.devops.user.model.TenantOptionVO;
import com.hfwas.devops.user.model.UserProfile;
import com.hfwas.devops.user.operlog.annotation.OperLog;

import java.util.List;
import com.hfwas.devops.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final AuthService authService;

    @OperLog(module = "user", action = "login", bizType = "user", summary = "用户登录")
    @PostMapping("/login")
    public BaseResult<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return BaseResult.ok(authService.login(request, httpRequest));
    }

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
    public BaseResult<LoginResponse> switchTenant(@RequestBody SwitchTenantRequest request,
                                                  HttpServletRequest httpRequest) {
        String token = resolveToken(httpRequest);
        return BaseResult.ok(authService.switchTenant(request, token, httpRequest));
    }

    @OperLog(module = "user", action = "logout", bizType = "user", summary = "用户登出")
    @PostMapping("/logout")
    public BaseResult<Void> logout(HttpServletRequest request) {
        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            authService.logout(token, request);
        }
        return BaseResult.ok();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
