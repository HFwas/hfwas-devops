package com.hfwas.devops.controller;

import com.hfwas.devops.tools.api.nexus.NexusUserApi;
import com.hfwas.devops.tools.entity.nexus.user.NexusUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.controller
 * @date 2025/1/6
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    @Autowired
    NexusUserApi nexusUserApi;

    @GetMapping("/list")
    public void list() {
        List<NexusUser> users = nexusUserApi.users(null, null);
        System.out.println(users);
    }


}
