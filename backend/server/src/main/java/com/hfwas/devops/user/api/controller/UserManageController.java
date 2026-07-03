package com.hfwas.devops.user.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.user.model.UserPageRequest;
import com.hfwas.devops.user.model.UserProfile;
import com.hfwas.devops.user.model.UserSaveRequest;
import com.hfwas.devops.user.service.AuthService;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/users")
@RequiredArgsConstructor
public class UserManageController {

    private final AuthService authService;

    @GetMapping("/options")
    public BaseResult<List<UserProfile>> options() {
        return BaseResult.ok(authService.listEnabled());
    }

    @PostMapping("/page")
    public BaseResult<IPage<UserProfile>> page(@RequestBody UserPageRequest request) {
        return BaseResult.ok(authService.page(request));
    }

    @OperLog(module = "user", action = "save", bizType = "user", summary = "保存用户账号", bizId = "#result.data")
    @PostMapping("/save")
    public BaseResult<Long> save(@RequestBody UserSaveRequest request) {
        return BaseResult.ok(authService.save(request));
    }

    @OperLog(module = "user", action = "delete", bizType = "user", summary = "删除用户账号", bizId = "#id")
    @PostMapping("/delete")
    public BaseResult<Void> delete(@RequestParam("id") Long id) {
        authService.delete(id);
        return BaseResult.ok();
    }
}
