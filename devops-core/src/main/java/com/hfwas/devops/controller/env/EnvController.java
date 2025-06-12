package com.hfwas.devops.controller.env;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.dto.env.DevopsEnvDto;
import com.hfwas.devops.entity.DevopsEnv;
import com.hfwas.devops.service.env.EnvService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.controller.env
 * @date 2025/6/12
 */
@RestController
@RequestMapping("/envs")
public class EnvController {

    private final EnvService envService;

    public EnvController(EnvService envService) {
        this.envService = envService;
    }

    /**
     * 添加环境
     * @param devopsEnvDto
     * @return
     */
    @PostMapping("/save")
    public BaseResult<Long> save(@RequestBody DevopsEnvDto devopsEnvDto) {
        Long id = envService.saveEnv(devopsEnvDto);
        return BaseResult.ok(id);
    }

    /**
     * 更新环境
     * @param devopsEnvDto
     * @return
     */
    @PostMapping("/update")
    public BaseResult update(@RequestBody DevopsEnvDto devopsEnvDto) {
        envService.updateEnv(devopsEnvDto);
        return BaseResult.ok();
    }

    /**
     * 删除环境
     * @param id
     * @return
     */
    @PostMapping("/delete")
    public BaseResult delete(@RequestParam("id") Long id) {
        envService.deleteEnvById(id);
        return BaseResult.ok();
    }

    /**
     * 获取项目下环境列表
     * @param projectId
     * @return
     */
    @PostMapping("/list")
    public BaseResult<List<DevopsEnv>> list(@RequestParam("projectId") Long projectId) {
        List<DevopsEnv> devopsEnvs = envService.listEnv(projectId);
        return BaseResult.ok(devopsEnvs);
    }

}
