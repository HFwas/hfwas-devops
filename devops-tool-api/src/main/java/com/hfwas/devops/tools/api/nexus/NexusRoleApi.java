
package com.hfwas.devops.tools.api.nexus;

import com.hfwas.devops.tools.entity.nexus.role.NexusRole;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "NexusRoleApi", url = "${devops.nexus.url}")
public interface NexusRoleApi {

    @GetMapping("/service/rest/v1/security/roles")
    List<NexusRole> roles(@RequestParam String source);

    @GetMapping("/service/rest/v1/security/roles/{id}")
    NexusRole role(@PathVariable("id") Long id);

    @PostMapping("/service/rest/v1/security/roles")
    NexusRole addRole(@RequestBody NexusRole role);

    @PutMapping("/service/rest/v1/security/roles")
    NexusRole updateRole(@RequestBody NexusRole role);

    @DeleteMapping("/service/rest/v1/security/roles/{id}")
    boolean deleteRole(@PathVariable("id") Long id);

}
