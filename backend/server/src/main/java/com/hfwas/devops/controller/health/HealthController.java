package com.hfwas.devops.controller.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author houfei
 * @package com.hfwas.devops.controller.health
 * @date 2025/6/12
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/check")
    public String health() {
        return "UP";
    }

}
