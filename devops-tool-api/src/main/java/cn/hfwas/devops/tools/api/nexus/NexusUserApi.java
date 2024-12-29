package cn.hfwas.devops.tools.api.nexus;

import cn.hfwas.devops.tools.entity.nexus.user.NexusUser;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "NexusUserApi", url = "devops.nexus.url:http://localhost:8080")
public interface NexusUserApi {

    @PostMapping("/v1/security/users")
    void users(@RequestBody NexusUser nexusUser);

    @GetMapping("/v1/security/users")
    List<NexusUser> users(@RequestParam("source") String source, @RequestParam String userId);

    @PutMapping("/v1/security/users/{userId}/change-password")
    void changePassword(@PathVariable("userId") String userId, @RequestBody NexusUser nexusUser);

    @DeleteMapping("/v1/security/users/{userId}")
    void deleteUser(@PathVariable("userId") String userId);

    @PutMapping("/v1/security/users/{userId}")
    void updateUser(@PathVariable("userId") String userId, @RequestBody NexusUser nexusUser);

}
