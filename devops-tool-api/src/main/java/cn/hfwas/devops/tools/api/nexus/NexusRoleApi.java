
package cn.hfwas.devops.tools.api.nexus;

import cn.hfwas.devops.tools.entity.nexus.role.NexusRole;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "NexusUserApi", url = "http://localhost:8080")
public interface NexusRoleApi {

    @GetMapping("/v1/security/roles")
    List<NexusRole> roles(@RequestParam String source);

    @GetMapping("/v1/security/roles/{id}")
    NexusRole role(@PathVariable("id") Long id);

    @PostMapping("/v1/security/roles")
    NexusRole addRole(@RequestBody NexusRole role);

    @PutMapping("/v1/security/roles")
    NexusRole updateRole(@RequestBody NexusRole role);

    @DeleteMapping("/v1/security/roles/{id}")
    boolean deleteRole(@PathVariable("id") Long id);

}
