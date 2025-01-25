package com.hfwas.devops.tools.api.depency;

import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.api.depency
 * @date 2025/1/9
 */
@FeignClient(name = "NpmApi", url = "${devops.depency.npm.url}")
public interface NpmApi {

    @GetMapping("/package/{name}/merge-streams?activeTab=dependencies")
    Response dependents(@PathVariable("name") String name);

    @GetMapping("/{name}")
    Response depenInfo(@PathVariable("name") String name);

}
