package cn.hfwas.devops.tools.api.harbor;

import cn.hfwas.devops.tools.entity.harbor.HarborProject;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "HarborApi", url = "devops.harbor.url:http://localhost:8080")
public interface HarborApi {

    @PostMapping("/projects")
    void project(@RequestBody HarborProject harborProject);

    @PutMapping("/projects/{project_name_or_id}")
    void updateProject(@PathVariable String project_name_or_id, @RequestBody HarborProject harborProject);

    @DeleteMapping("/projects/{project_name_or_id}")
    void project(@PathVariable String project_name_or_id);

    @GetMapping("/projects")
    void projects(@RequestParam(required = false, defaultValue = "1") Integer page, @RequestParam(required = false, defaultValue = "10") Integer pageSize,
                  @RequestParam(required = false) String name, @RequestParam(required = false) String owner,
                  @RequestParam(required = false) String q, @RequestParam(required = false) Boolean with_detail);

}
