package cn.hfwas.devops.tools.api.nexus;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "NexusStatAPi", url = "http://localhost:8080")
public interface NexusStatAPi {

    @GetMapping("/v1/status/check")
    void check();

    @GetMapping("/v1/status")
    String status();

    @GetMapping("/v1/status/writable")
    String writable();

}
