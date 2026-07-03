package com.hfwas.devops.user.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.user.operlog.model.OperLogPageRequest;
import com.hfwas.devops.user.operlog.model.OperLogVO;
import com.hfwas.devops.user.operlog.service.OperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/oper-logs")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogService operLogService;

    @PostMapping("/page")
    public BaseResult<IPage<OperLogVO>> page(@RequestBody OperLogPageRequest request) {
        return BaseResult.ok(operLogService.page(request));
    }
}
