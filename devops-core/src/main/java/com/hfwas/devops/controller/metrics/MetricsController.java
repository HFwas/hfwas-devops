package com.hfwas.devops.controller.metrics;

import com.hfwas.devops.service.metrics.MetricsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author houfei
 * @package com.hfwas.devops.controller.metrics
 * @date 2025/2/17
 */
@RestController
@RequestMapping("/metrics")
public class MetricsController {

    @Resource
    private MetricsService metricsService;

    @GetMapping("/process")
    public ProcessHandle process() {
        ProcessHandle processHandle = metricsService.processHandle();
        return processHandle;
    }

}
