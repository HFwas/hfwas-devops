package com.hfwas.devops.controller.vul;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.dto.vul.VulDto;
import com.hfwas.devops.entity.DevopsVul;
import com.hfwas.devops.service.vul.DevopsVulService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/page")
    public IPage<DevopsVul> list(@RequestBody VulDto vulDto) {
        IPage<DevopsVul> page = devopsVulService.page(vulDto);
        return page;
    }

    @GetMapping("/getById")
    public DevopsVul getById(@RequestParam Long id) {
        DevopsVul devopsVul = devopsVulService.getById(id);
        return devopsVul;
    }

    @GetMapping("/sync")
    public void sync() {
        devopsVulService.sync();
    }

}
