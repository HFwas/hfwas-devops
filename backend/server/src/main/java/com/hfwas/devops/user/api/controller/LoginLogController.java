package com.hfwas.devops.user.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.user.model.LoginLogPageRequest;
import com.hfwas.devops.user.model.LoginLogVO;
import com.hfwas.devops.user.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/login-logs")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;

    @PostMapping("/page")
    public BaseResult<IPage<LoginLogVO>> page(@RequestBody LoginLogPageRequest request) {
        return BaseResult.ok(loginLogService.page(request));
    }
}
