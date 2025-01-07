package com.hfwas.devops.tools.api.nexus;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "NexusStatAPi", url = "${devops.nexus.url}")
public interface NexusStatAPi {

    @GetMapping("/service/rest/v1/status/check")
    void check();

    @GetMapping("/service/rest/v1/status")
    String status();

    @GetMapping("/service/rest/v1/status/writable")
    String writable();

}
