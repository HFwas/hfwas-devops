package com.hfwas.devops.controller.vul;

import com.hfwas.devops.entity.DevopsVulDependency;
import com.hfwas.devops.service.vul.DevopsVulService;
import com.hfwas.devops.service.vul.rust.DevopsRustDepenScan;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;

/**
 * @author hfwas
 * @package com.hfwas.devops.controller.vul
 * @date 2025/1/12
 */
@RestController
@RequestMapping("/vul")
@Slf4j
public class VulController {

    @Resource
    DevopsVulService devopsVulService;
    @Resource
    DevopsRustDepenScan devopsPythonDepenScan;

    @GetMapping("/npm")
    public void npm() throws IOException {
        List<DevopsVulDependency> dependencys = devopsPythonDepenScan.dependencys(null);
        log.info("databaseSpecific:{}", dependencys);
    }

    @GetMapping("/getById")
    public void getById() throws IOException {
        log.info("databaseSpecific:{}", "null");
    }

    @GetMapping("/sync")
    public void sync() {
        devopsVulService.sync();
    }

}
