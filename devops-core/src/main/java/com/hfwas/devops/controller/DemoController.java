package com.hfwas.devops.controller;

import com.hfwas.devops.tools.api.nexus.NexusUserApi;
import feign.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author houfei
 * @package com.hfwas.devops.controller
 * @date 2025/1/6
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    @Autowired
    private NexusUserApi nexusUserApi;

    @GetMapping("/list")
    public void list() {
        Response users = nexusUserApi.users(null, null);
        Response.Body body = users.body();
        System.out.println();
    }


}
