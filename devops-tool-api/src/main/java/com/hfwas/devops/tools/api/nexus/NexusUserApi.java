package com.hfwas.devops.tools.api.nexus;

import com.hfwas.devops.tools.entity.nexus.user.NexusUser;
import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;


@FeignClient(name = "nexusUserApi", url = "${devops.nexus.url}")
public interface NexusUserApi {

    @PostMapping("/service/rest/v1/security/users")
    void users(@RequestBody NexusUser nexusUser);

    @GetMapping("/service/rest/v1/security/users")
    Response users(@RequestParam("source") String source, @RequestParam("userId") String userId);

    @PutMapping("/service/rest/v1/security/users/{userId}/change-password")
    void changePassword(@PathVariable("userId") String userId, @RequestBody NexusUser nexusUser);

    @DeleteMapping("/service/rest/v1/security/users/{userId}")
    void deleteUser(@PathVariable("userId") String userId);

    @PutMapping("/service/rest/v1/security/users/{userId}")
    void updateUser(@PathVariable("userId") String userId, @RequestBody NexusUser nexusUser);

}
