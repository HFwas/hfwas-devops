package com.hfwas.devops.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author houfei
 * @package com.hfwas.devops.controller
 * @date 2025/3/22
 */
@RestController
@RequestMapping("/users")
public class UserController {

    @GetMapping
    public BaseResult getUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return BaseResult.ok(authentication);
    }

}
