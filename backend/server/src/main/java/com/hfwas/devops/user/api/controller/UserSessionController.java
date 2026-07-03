package com.hfwas.devops.user.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.user.model.UserSessionPageRequest;
import com.hfwas.devops.user.model.UserSessionStats;
import com.hfwas.devops.user.model.UserSessionVO;
import com.hfwas.devops.user.security.JwtTokenService;
import com.hfwas.devops.user.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/user/sessions")
@RequiredArgsConstructor
public class UserSessionController {

    private final UserSessionService userSessionService;
    private final JwtTokenService jwtTokenService;

    @GetMapping("/stats")
    public BaseResult<UserSessionStats> stats() {
        return BaseResult.ok(userSessionService.stats());
    }

    @PostMapping("/page")
    public BaseResult<IPage<UserSessionVO>> page(@RequestBody UserSessionPageRequest request,
                                                 HttpServletRequest httpRequest) {
        return BaseResult.ok(userSessionService.page(request, currentSessionKey(httpRequest).orElse(null)));
    }

    @PostMapping("/revoke")
    public BaseResult<Void> revoke(@RequestParam("id") Long id) {
        userSessionService.revokeById(id);
        return BaseResult.ok();
    }

    private Optional<String> currentSessionKey(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        try {
            return Optional.of(jwtTokenService.resolveSessionKey(header.substring(7)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
