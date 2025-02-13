package com.hfwas.devops.controller.sync;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.service.syncLog.DevopsSyncLogService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author houfei
 * @package com.hfwas.devops.controller.sync
 * @date 2025/2/13
 */
@RestController
@RequestMapping("/syncLog")
public class DevopsSyncLogController {

    @Resource
    private DevopsSyncLogService devopsSyncLogService;

    @PostMapping("/page")
    public BaseResult<Page> page(@RequestBody Page page) {

        return null;
    }

}
