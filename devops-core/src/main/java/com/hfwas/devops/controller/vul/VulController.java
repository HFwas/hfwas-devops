package com.hfwas.devops.controller.vul;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.dto.vul.VulDto;
import com.hfwas.devops.entity.DevopsVul;
import com.hfwas.devops.service.sync.VulSyncApi;
import com.hfwas.devops.service.vul.DevopsVulService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author hfwas
 * @package com.hfwas.devops.controller.vul
 * @date 2025/1/12
 */
@RestController
@RequestMapping("/vul")
public class VulController {

    @Resource
    DevopsVulService devopsVulService;
    @Resource
    List<VulSyncApi> nvdVulSyncApi;

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

    @GetMapping("/codeVul")
    public List<DevopsVul> codeVul(@RequestParam Long codeId) {
        List<DevopsVul> devopsVul = devopsVulService.codeVul(codeId);
        return devopsVul;
    }

    @GetMapping("/sync")
    public void sync() {
        devopsVulService.sync();
    }

    @GetMapping("/nvd")
    public void nvd() {
        for (VulSyncApi vulSyncApi : nvdVulSyncApi) {
            vulSyncApi.sync();
        }
    }

}
