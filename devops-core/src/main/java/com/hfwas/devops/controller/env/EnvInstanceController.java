package com.hfwas.devops.controller.env;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.entity.DevopsEnvInstance;
import com.hfwas.devops.service.env.EnvInstanceService;
import org.springframework.web.bind.annotation.*;

/**
 * @author houfei
 * @package com.hfwas.devops.controller.env
 * @date 2025/6/12
 */
@RestController
@RequestMapping("/envs/instances")
public class EnvInstanceController {

    private final EnvInstanceService envInstanceService;

    public EnvInstanceController(EnvInstanceService envInstanceService) {
        this.envInstanceService = envInstanceService;
    }

    @PostMapping("/save")
    public BaseResult save(@RequestBody DevopsEnvInstance devopsEnvInstance) {
        envInstanceService.saveEnvInstance(devopsEnvInstance);
        return BaseResult.ok();
    }

    @PostMapping("/update")
    public BaseResult update(@RequestBody DevopsEnvInstance devopsEnvInstance) {
        envInstanceService.updateEnvInstance(devopsEnvInstance);
        return BaseResult.ok();
    }

    @PostMapping("/delete")
    public BaseResult delete(@RequestParam("id") Long id) {
        envInstanceService.deleteEnvInstance(id);
        return BaseResult.ok();
    }

    @PostMapping("/connect")
    public BaseResult connect(@RequestParam("id") Long id) {
        envInstanceService.connect(id);
        return BaseResult.ok();
    }

}
