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
@FeignClient(name = "JavaApi", url = "${devops.depency.java.url}")
public interface JavaApi {

    @GetMapping("/maven2/{name}")
    Response maven2(@PathVariable(value = "name", required = false) String name);

}
