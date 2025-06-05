package com.hfwas.devops.controller.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.dto.tools.DevopsToolDto;
import com.hfwas.devops.dto.tools.DevopsToolUpdateDto;
import com.hfwas.devops.entity.DevopsTool;
import com.hfwas.devops.service.tools.DevopsToolService;
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

    DevopsToolService devopsToolService;

    public DevopsToolController(DevopsToolService devopsToolService) {
        this.devopsToolService = devopsToolService;
    }

    /**
     * 新增 devops 工具
     * @param devopsTool
     * @return
     */
    @PostMapping("/save")
    public BaseResult save(@RequestBody DevopsToolDto devopsTool) {
        devopsToolService.insert(devopsTool);
        return BaseResult.ok();
    }

    /**
     * 编辑 devops 工具
     * @param devopsTool
     * @return
     */
    @PostMapping("/edit")
    public BaseResult edit(@RequestBody DevopsToolUpdateDto devopsTool) {
        devopsToolService.edit(devopsTool);
        return BaseResult.ok();
    }

    /**
     * 分页查询 devops 工具
     * @param devopsTool
     * @return
     */
    @PostMapping("/page")
    public BaseResult<Page<DevopsTool>> page(@RequestBody DevopsToolDto devopsTool) {
        Page<DevopsTool> page = devopsToolService.page(devopsTool);
        return BaseResult.ok(page);
    }

    /**
     * 逻辑删除 devops 工具
     * @param id
     * @return
     */
    @PostMapping
    public BaseResult delete(@RequestBody Integer id) {
        devopsToolService.delete(id);
        return BaseResult.ok();
    }


}



