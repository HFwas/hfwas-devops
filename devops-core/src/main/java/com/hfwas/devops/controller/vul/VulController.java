package com.hfwas.devops.controller.vul;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.dto.vul.VulDto;
import com.hfwas.devops.entity.DevopsVul;
import com.hfwas.devops.service.sync.VulSyncApi;
import com.hfwas.devops.service.vul.DevopsVulService;
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

    private final DevopsVulService devopsVulService;
    private final List<VulSyncApi> nvdVulSyncApi;

    public VulController(DevopsVulService devopsVulService,
                         List<VulSyncApi> nvdVulSyncApi) {
        this.devopsVulService = devopsVulService;
        this.nvdVulSyncApi = nvdVulSyncApi;
    }

    @PostMapping("/page")
    public BaseResult<IPage<DevopsVul>> list(@RequestBody VulDto vulDto) {
        IPage<DevopsVul> page = devopsVulService.page(vulDto);
        return BaseResult.ok(page);
    }

    @GetMapping("/getById")
    public BaseResult<DevopsVul> getById(@RequestParam Long id) {
        DevopsVul devopsVul = devopsVulService.getById(id);
        return BaseResult.ok(devopsVul);
    }

    @GetMapping("/codeVul")
    public BaseResult<List<DevopsVul>> codeVul(@RequestParam Long codeId) {
        List<DevopsVul> devopsVuls = devopsVulService.codeVul(codeId);
        return BaseResult.ok(devopsVuls);
    }

    @GetMapping("/sync")
    public BaseResult<Void> sync() {
        devopsVulService.sync();
        return BaseResult.ok();
    }

    @GetMapping("/nvd")
    public BaseResult<Void> nvd() {
        for (VulSyncApi vulSyncApi : nvdVulSyncApi) {
            vulSyncApi.sync();
        }
        return BaseResult.ok();
    }

}
