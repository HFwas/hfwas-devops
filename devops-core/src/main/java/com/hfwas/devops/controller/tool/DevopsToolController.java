package com.hfwas.devops.controller.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.dto.tools.DevopsToolDto;
import com.hfwas.devops.entity.DevopsTool;
import com.hfwas.devops.service.tools.DevopsToolService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author houfei
 * @package com.hfwas.devops.controller.tool
 * @date 2025/3/16
 */
@RestController
@RequestMapping("/tools")
public class DevopsToolController {

    @Resource
    DevopsToolService devopsToolService;

    @PostMapping("/save")
    public BaseResult save(@RequestBody DevopsToolDto devopsTool) {
        devopsToolService.insert(devopsTool);
        return BaseResult.ok();
    }

    @PostMapping("/page")
    public BaseResult<Page<DevopsTool>> page(@RequestBody DevopsToolDto devopsTool) {
        Page<DevopsTool> page = devopsToolService.page(devopsTool);
        return BaseResult.ok(page);
    }


}



