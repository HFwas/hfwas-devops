package cn.hfwas.devops.tools.api.harbor;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "HarborApi", url = "devops.harbor.url:http://localhost:8080")
public interface HarborApi {


}
