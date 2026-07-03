package com.hfwas.devops.user.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.user.model.UserPageRequest;
import com.hfwas.devops.user.model.UserProfile;
import com.hfwas.devops.user.model.UserSaveRequest;
import com.hfwas.devops.user.service.AuthService;
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

    @PostMapping("/save")
    public BaseResult<Long> save(@RequestBody UserSaveRequest request) {
        return BaseResult.ok(authService.save(request));
    }

    @PostMapping("/delete")
    public BaseResult<Void> delete(@RequestParam("id") Long id) {
        authService.delete(id);
        return BaseResult.ok();
    }
}
